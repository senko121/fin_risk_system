from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, List
from deepface import DeepFace
import asyncio
import base64
import concurrent.futures
import cv2
import functools
import logging
import numpy as np
import os
import time

from uniface import MiniFASNet, create_spoofer
from uniface.constants import MiniFASNetWeights

# ── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(message)s",
)
_log = logging.getLogger(__name__)

# ── Inference executor ─────────────────────────────────────────────────────────
_INFERENCE_WORKERS = int(os.environ.get("INFERENCE_WORKERS", "4"))
_inference_executor = concurrent.futures.ThreadPoolExecutor(
    max_workers=_INFERENCE_WORKERS,
    thread_name_prefix="inference-face",
)
_log.info(f"[face-service] Inference executor ready: max_workers={_INFERENCE_WORKERS}")

_MAX_CONCURRENT: int = _INFERENCE_WORKERS * 2
_inference_semaphore: asyncio.Semaphore

# ==============================================================
# ANTI-SPOOF ENGINE
# ==============================================================
_SPOOF_MODELS: list[MiniFASNet] = []


def _load_antispoof_models() -> None:
    global _SPOOF_MODELS
    loaded = []
    for variant in [MiniFASNetWeights.V2, MiniFASNetWeights.V1SE]:
        try:
            m = create_spoofer(variant, providers=["CPUExecutionProvider"])
            loaded.append(m)
            _log.info(f"[liveness] Loaded uniface {variant.name}")
        except Exception as e:
            _log.warning(f"[liveness] Failed to load {variant.name}: {e}")
    _SPOOF_MODELS = loaded
    if not loaded:
        _log.warning("[liveness] No anti-spoof models loaded → Fail-Open active")
    else:
        _log.info(f"[liveness] {len(loaded)} anti-spoof model(s) ready")


# ==============================================================
# CONSTANTS
# ==============================================================
BACKEND         = "mtcnn"
MODEL_NAME      = "ArcFace"
DISTANCE_METRIC = "cosine"
EMBEDDING_DIM   = 512  # ArcFace output dimension

THRESHOLD_STRICT = 0.40
THRESHOLD_SOFT   = 0.58

QUALITY_GATE        = 0.35
TOP_N_FRAMES        = 3
MAX_FRAMES_TO_SCORE = 15

LIVENESS_THRESHOLD       = 0.60
LIVENESS_MIN_FRAMES_PASS = 2
LIVENESS_ENFORCE = os.environ.get("LIVENESS_ENFORCE", "true").lower() == "true"

REPLAY_DIFF_MEAN_THRESHOLD = 0.008
REPLAY_DIFF_STD_THRESHOLD  = 0.003


# ==============================================================
# WARM-UP
# ==============================================================
def _warmup_models() -> None:
    _log.info(f"[face-service] pid={os.getpid()} warm-up: loading ArcFace ...")
    try:
        DeepFace.build_model("ArcFace")
    except Exception as exc:
        _log.warning(f"[face-service] ArcFace warm-up failed (non-fatal): {exc}")

    _log.info(f"[face-service] pid={os.getpid()} warm-up: triggering MTCNN ...")
    try:
        dummy = np.zeros((112, 112, 3), dtype=np.uint8)
        DeepFace.extract_faces(img_path=dummy, detector_backend="mtcnn", enforce_detection=False)
    except Exception as exc:
        _log.warning(f"[face-service] MTCNN warm-up failed (non-fatal): {exc}")

    _log.info(f"[face-service] pid={os.getpid()} warm-up complete")
    _load_antispoof_models()


# ==============================================================
# LIFESPAN
# ==============================================================
@asynccontextmanager
async def lifespan(app: FastAPI):
    global _inference_semaphore
    pid = os.getpid()
    _inference_semaphore = asyncio.Semaphore(_MAX_CONCURRENT)
    _log.info(f"[face-service] Worker boot pid={pid} inference_workers={_INFERENCE_WORKERS}")

    loop = asyncio.get_running_loop()
    try:
        await loop.run_in_executor(_inference_executor, _warmup_models)
    except Exception as exc:
        if LIVENESS_ENFORCE:
            _log.critical(f"[face-service] pid={pid} startup ABORTED — {exc}")
            raise
        _log.warning(f"[face-service] pid={pid} lifespan warm-up error: {exc}")
    yield
    _log.info(f"[face-service] Worker shutdown pid={pid} — draining executor ...")
    _inference_executor.shutdown(wait=True)


app = FastAPI(lifespan=lifespan)


# ==============================================================
# REQUEST / RESPONSE MODELS
# ==============================================================

class FaceEnrollRequest(BaseModel):
    """Nhận ảnh webcam → trả về embedding vector để lưu DB."""
    image_base64: str
    user_id:      Optional[str] = None  # optional, dùng cho logging


class FaceEnrollResponse(BaseModel):
    success:        bool
    embedding:      Optional[List[float]] = None  # 512 floats
    quality_score:  Optional[float]       = None
    face_detected:  bool                  = False
    error:          Optional[str]         = None


class FaceVerificationRequest(BaseModel):
    # ── Live side ──
    live_image_base64:      Optional[str]        = None
    live_image_base64_list: Optional[List[str]]  = None

    # ── Registered side: MỚI dùng embedding, CŨ dùng ảnh (backward compat) ──
    registered_embedding:      Optional[List[float]] = None  # ưu tiên dùng cái này
    registered_image_base64:   Optional[str]         = None  # fallback nếu chưa migrate


# ==============================================================
# HELPERS
# ==============================================================
def decode_base64_image(b64: str) -> np.ndarray:
    """Decode base64 (có hoặc không có prefix data:...) → RGB numpy array."""
    try:
        if "," in b64:
            b64 = b64.split(",")[1]
        img = cv2.imdecode(np.frombuffer(base64.b64decode(b64), np.uint8), cv2.IMREAD_COLOR)
        if img is None:
            raise ValueError("Decode returned NULL")
        return cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    except Exception as e:
        raise ValueError(f"Image decode error: {e}")


def _brightness_score(mean_px: float) -> float:
    if 60.0 <= mean_px <= 200.0:
        return 1.0
    if mean_px < 60.0:
        return float(max(mean_px / 60.0, 0.0))
    return float(max((255.0 - mean_px) / 55.0, 0.0))


def compute_face_quality(img_rgb: np.ndarray) -> dict:
    h_img, w_img = img_rgb.shape[:2]
    default = dict(quality_score=0.0, blur_score=0.0, brightness_score=0.0,
                   face_size_score=0.0, face_detected=False, face_bbox=None)
    try:
        dets = DeepFace.extract_faces(img_path=img_rgb, detector_backend="mtcnn",
                                      enforce_detection=False)
    except Exception:
        return default

    if not dets:
        return default

    best = max(dets, key=lambda d: d.get("confidence", 0.0) if isinstance(d, dict) else 0.0)
    if not isinstance(best, dict) or "facial_area" not in best:
        gray      = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2GRAY)
        blur_norm = float(min(cv2.Laplacian(gray, cv2.CV_64F).var() / 400.0, 1.0))
        b_score   = _brightness_score(float(np.mean(gray)))
        return {**default, "face_detected": True, "blur_score": blur_norm,
                "brightness_score": b_score, "face_size_score": 0.5,
                "quality_score": round(0.40 * blur_norm + 0.40 * 0.5 + 0.20 * b_score, 4)}

    fa   = best["facial_area"]
    x, y = int(fa.get("x", 0)), int(fa.get("y", 0))
    w, h = int(fa.get("w", 0)), int(fa.get("h", 0))
    if w <= 0 or h <= 0:
        return default

    pad  = int(0.10 * max(w, h))
    crop = img_rgb[max(0, y-pad):min(h_img, y+h+pad), max(0, x-pad):min(w_img, x+w+pad)]
    gray = cv2.cvtColor(crop if crop.size > 0 else img_rgb, cv2.COLOR_RGB2GRAY)

    blur_norm      = float(min(cv2.Laplacian(gray, cv2.CV_64F).var() / 400.0, 1.0))
    face_size_norm = float(min((w * h) / max(w_img * h_img, 1) / 0.15, 1.0))
    b_score        = _brightness_score(float(np.mean(gray)))

    return dict(
        quality_score    = round(0.40 * blur_norm + 0.40 * face_size_norm + 0.20 * b_score, 4),
        blur_score       = blur_norm,
        brightness_score = b_score,
        face_size_score  = face_size_norm,
        face_detected    = True,
        face_bbox        = (x, y, w, h),
    )


def _effective_soft_threshold(avg_quality: float) -> float:
    if avg_quality >= 0.70: return THRESHOLD_SOFT
    if avg_quality >= 0.50:
        return THRESHOLD_SOFT * (0.96 + 0.04 * (avg_quality - 0.50) / 0.20)
    return THRESHOLD_STRICT


def classify_distance(distance: float, avg_quality: float) -> dict:
    if distance <= THRESHOLD_STRICT:
        return {"is_matched": True,  "confidence_band": "HIGH",   "effective_threshold": THRESHOLD_STRICT}
    eff = _effective_soft_threshold(avg_quality)
    if distance <= eff:
        return {"is_matched": True,  "confidence_band": "MEDIUM", "effective_threshold": round(eff, 4)}
    return     {"is_matched": False, "confidence_band": "LOW",    "effective_threshold": round(eff, 4)}


def select_best_frames(imgs: List[np.ndarray], top_n: int = TOP_N_FRAMES) -> List[tuple]:
    if not imgs: return []
    step    = max(1, len(imgs) // MAX_FRAMES_TO_SCORE)
    sampled = imgs[::step][:MAX_FRAMES_TO_SCORE]
    scored  = [(img, q["quality_score"])
               for img in sampled
               for q in [compute_face_quality(img)]
               if q["face_detected"] and q["quality_score"] >= QUALITY_GATE]
    scored.sort(key=lambda p: p[1], reverse=True)
    return scored[:top_n]


def get_embedding_mtcnn(img_rgb: np.ndarray) -> Optional[List[float]]:
    """Extract ArcFace embedding từ ảnh RGB. Trả về None nếu không detect được mặt."""
    try:
        raw = DeepFace.represent(
            img_path=img_rgb,
            model_name=MODEL_NAME,
            detector_backend=BACKEND,
            enforce_detection=False,
        )
        if isinstance(raw, list) and raw:
            return raw[0]["embedding"]
        if isinstance(raw, dict):
            return raw["embedding"]
    except Exception as exc:
        _log.debug(f"[embedding] Failed: {exc}")
    return None


def normalize_embedding(emb: List[float]) -> np.ndarray:
    """L2-normalize embedding vector."""
    v = np.array(emb, dtype=np.float32)
    n = np.linalg.norm(v)
    return v if n == 0.0 else v / n


def average_embeddings(embeddings: List[List[float]]) -> np.ndarray:
    v = np.mean(np.array(embeddings, dtype=np.float32), axis=0)
    n = np.linalg.norm(v)
    return v if n == 0.0 else v / n


def _cosine_distance(a: np.ndarray, b: np.ndarray) -> float:
    na, nb = np.linalg.norm(a), np.linalg.norm(b)
    return 1.0 if na == 0.0 or nb == 0.0 else float(1.0 - np.dot(a, b) / (na * nb))


# ==============================================================
# LIVENESS CHECK
# ==============================================================
def _bbox_xywh_to_xyxy(bbox: tuple) -> list:
    x, y, w, h = bbox
    return [x, y, x + w, y + h]


def check_liveness(img_rgb: np.ndarray, face_bbox: Optional[tuple]) -> dict:
    if not _SPOOF_MODELS:
        is_live = not LIVENESS_ENFORCE
        return {"live_score": 1.0 if is_live else 0.0, "is_live": is_live,
                "models_used": 0, "skipped": True}
    if face_bbox is None:
        return {"live_score": 0.0, "is_live": False, "models_used": 0, "skipped": False}

    img_bgr   = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2BGR)
    bbox_xyxy = _bbox_xywh_to_xyxy(face_bbox)
    scores    = []

    for model in _SPOOF_MODELS:
        try:
            result    = model.predict(img_bgr, bbox_xyxy)
            live_conf = result.confidence if result.is_real else (1.0 - result.confidence)
            scores.append(live_conf)
        except Exception as exc:
            _log.warning(f"[liveness] Inference error: {exc}")

    if not scores:
        return {"live_score": 0.0, "is_live": False, "models_used": 0, "skipped": False}

    avg = float(np.mean(scores))
    return {"live_score": avg, "is_live": avg >= LIVENESS_THRESHOLD,
            "models_used": len(scores), "skipped": False}


# ==============================================================
# REPLAY DETECTION
# ==============================================================
def detect_replay_attack(imgs: List[np.ndarray]) -> dict:
    if len(imgs) < 3:
        return {"is_replay": False, "reason": "INSUFFICIENT_FRAMES",
                "frame_diff_mean": -1.0, "frame_diff_std": -1.0}

    grays = []
    for img in imgs[:10]:
        try:
            grays.append(cv2.cvtColor(img, cv2.COLOR_RGB2GRAY).astype(np.float32))
        except Exception:
            pass

    if len(grays) < 2:
        return {"is_replay": False, "reason": "DECODE_FAILED",
                "frame_diff_mean": -1.0, "frame_diff_std": -1.0}

    diffs     = [float(np.mean(np.abs(grays[i] - grays[i-1])) / 255.0)
                 for i in range(1, len(grays))]
    mean_diff = float(np.mean(diffs))
    std_diff  = float(np.std(diffs))
    is_replay = (mean_diff < REPLAY_DIFF_MEAN_THRESHOLD and std_diff < REPLAY_DIFF_STD_THRESHOLD)

    return {"is_replay": is_replay,
            "reason": "STATIC_FRAME_SEQUENCE" if is_replay else None,
            "frame_diff_mean": round(mean_diff, 5),
            "frame_diff_std":  round(std_diff,  5)}


def _run_liveness_on_frames(best_frames: list) -> dict:
    if not _SPOOF_MODELS:
        return {"pass": not LIVENESS_ENFORCE, "score": 1.0,
                "reason": "SKIPPED_NO_MODELS", "frames_checked": 0, "frames_passed": 0}

    frames_passed, scores = 0, []
    for img, _quality in best_frames:
        q  = compute_face_quality(img)
        lr = check_liveness(img, q.get("face_bbox"))
        scores.append(lr["live_score"])
        if lr["is_live"]:
            frames_passed += 1

    if not scores:
        return {"pass": not LIVENESS_ENFORCE, "score": 0.0,
                "reason": "NO_LIVENESS_RESULT", "frames_checked": 0, "frames_passed": 0}

    avg    = float(np.mean(scores))
    passed = frames_passed >= LIVENESS_MIN_FRAMES_PASS
    return {"pass": passed, "score": round(avg, 4),
            "reason": None if passed else "SPOOF_DETECTED",
            "frames_checked": len(scores), "frames_passed": frames_passed}


# ==============================================================
# ENROLL LOGIC
# ==============================================================
def _enroll_face_sync(img_rgb: np.ndarray, user_id: Optional[str]) -> dict:
    """
    Chạy trong thread pool.
    1. Kiểm tra quality
    2. Extract embedding
    3. Trả về embedding vector (512 floats, đã normalize)
    """
    quality = compute_face_quality(img_rgb)

    if not quality["face_detected"]:
        return {"success": False, "error": "NO_FACE_DETECTED",
                "face_detected": False, "quality_score": 0.0}

    if quality["quality_score"] < QUALITY_GATE:
        return {"success": False,
                "error": f"QUALITY_TOO_LOW (score={quality['quality_score']:.3f}, min={QUALITY_GATE})",
                "face_detected": True, "quality_score": quality["quality_score"]}

    emb = get_embedding_mtcnn(img_rgb)
    if emb is None:
        return {"success": False, "error": "EMBEDDING_FAILED",
                "face_detected": True, "quality_score": quality["quality_score"]}

    # Normalize trước khi lưu → cosine distance chỉ cần dot product sau này
    emb_normalized = normalize_embedding(emb).tolist()

    _log.info(f"[enroll] user={user_id or '?'} "
              f"quality={quality['quality_score']:.4f} "
              f"embedding_dim={len(emb_normalized)}")

    return {"success": True, "embedding": emb_normalized,
            "face_detected": True, "quality_score": quality["quality_score"]}


# ==============================================================
# VERIFY LOGIC
# ==============================================================
def verify_single_mtcnn(img1: np.ndarray, img2: np.ndarray) -> Optional[float]:
    try:
        result = DeepFace.verify(img1_path=img1, img2_path=img2, model_name=MODEL_NAME,
                                 enforce_detection=True, detector_backend=BACKEND,
                                 distance_metric=DISTANCE_METRIC)
        return float(result["distance"])
    except Exception as exc:
        _log.debug(f"[verify] verify_single failed: {exc}")
        return None


def verify_multi_frame(live_frames_b64: List[str],
                       reg_embedding: Optional[np.ndarray],
                       reg_img: Optional[np.ndarray]) -> dict:
    """
    reg_embedding: đã normalize, dùng trực tiếp nếu có  ← ĐƯỜNG MỚI
    reg_img: fallback nếu chưa migrate                   ← ĐƯỜNG CŨ
    """
    decoded = []
    for idx, b64 in enumerate(live_frames_b64):
        try:
            decoded.append(decode_base64_image(b64))
        except Exception as exc:
            _log.warning(f"[multi] frame {idx} decode error: {exc}")

    if not decoded:
        return {"error": "ALL_FRAMES_DECODE_FAILED", "frames_evaluated": 0, "frames_used": 0}

    best_frames = select_best_frames(decoded, top_n=TOP_N_FRAMES)
    if not best_frames:
        return {"error": "NO_QUALITY_FRAMES", "frames_evaluated": len(decoded), "frames_used": 0}

    # ── Replay ──
    t0     = time.perf_counter()
    replay = detect_replay_attack(decoded)
    _log.info(f"[replay] elapsed={(time.perf_counter()-t0)*1000:.1f}ms "
              f"is_replay={replay['is_replay']} "
              f"mean={replay['frame_diff_mean']:.5f} std={replay['frame_diff_std']:.5f}")
    if replay["is_replay"]:
        return {"error": "REPLAY_ATTACK_DETECTED", "frames_evaluated": len(decoded),
                "frames_used": 0, "frame_diff_mean": replay["frame_diff_mean"]}

    # ── Liveness ──
    t0       = time.perf_counter()
    liveness = _run_liveness_on_frames(best_frames)
    _log.info(f"[liveness] elapsed={(time.perf_counter()-t0)*1000:.1f}ms "
              f"pass={liveness['pass']} score={liveness['score']:.4f} "
              f"frames={liveness['frames_passed']}/{liveness['frames_checked']}")
    if not liveness["pass"]:
        return {"error": "LIVENESS_FAILED", "frames_evaluated": len(decoded),
                "frames_used": 0, "liveness_score": liveness["score"],
                "frames_liveness_passed": liveness["frames_passed"]}

    # ── Live embeddings ──
    live_embeddings, qualities = [], []
    for img, quality in best_frames:
        emb = get_embedding_mtcnn(img)
        if emb is not None:
            live_embeddings.append(emb)
            qualities.append(quality)

    if not live_embeddings:
        return {"error": "NO_FACE_DETECTED", "frames_evaluated": len(decoded),
                "frames_used": 0, "avg_quality": 0.0}

    avg_quality = float(np.mean(qualities))
    avg_live    = average_embeddings(live_embeddings)

    # ── Registered embedding: ưu tiên vector, fallback ảnh ──
    if reg_embedding is not None:
        # ĐƯỜNG MỚI: dùng thẳng embedding từ DB, không cần decode ảnh
        reg_vec = reg_embedding
        _log.info(f"[face-id] using pre-computed registered_embedding dim={len(reg_vec)}")
    else:
        # ĐƯỜNG CŨ: extract từ ảnh (backward compat)
        if reg_img is None:
            return {"error": "NO_REGISTERED_DATA",
                    "frames_evaluated": len(decoded), "frames_used": len(live_embeddings)}
        reg_emb_raw = get_embedding_mtcnn(reg_img)
        if reg_emb_raw is None:
            return {"error": "REGISTERED_FACE_NOT_DETECTED",
                    "frames_evaluated": len(decoded), "frames_used": len(live_embeddings)}
        reg_quality = compute_face_quality(reg_img)
        _log.info(f"[face-id] registered_quality={reg_quality.get('quality_score', 0.0):.4f} "
                  f"face_detected={reg_quality.get('face_detected', False)}")
        reg_vec = normalize_embedding(reg_emb_raw)

    distance = _cosine_distance(avg_live, reg_vec)
    return {
        "error":            None,
        "distance":         distance,
        "frames_evaluated": len(decoded),
        "frames_used":      len(live_embeddings),
        "avg_quality":      avg_quality,
        "liveness_score":   liveness["score"],
        "liveness_pass":    True,
    }


# ==============================================================
# ENDPOINTS
# ==============================================================

@app.post("/api/ai/enroll-face", response_model=FaceEnrollResponse)
async def enroll_face(request: FaceEnrollRequest):
    """
    Đăng ký khuôn mặt mới.
    Nhận ảnh webcam → kiểm tra quality → extract embedding → trả về vector.
    Java lưu vector này vào DB thay vì lưu ảnh raw.
    """
    try:
        if not (request.image_base64 or "").strip():
            raise ValueError("image_base64 must not be empty")

        img_rgb = decode_base64_image(request.image_base64)

        try:
            await asyncio.wait_for(_inference_semaphore.acquire(), timeout=2.0)
        except asyncio.TimeoutError:
            raise HTTPException(status_code=503, detail="Inference at capacity, retry later")

        try:
            loop   = asyncio.get_running_loop()
            result = await loop.run_in_executor(
                _inference_executor,
                functools.partial(_enroll_face_sync, img_rgb, request.user_id))
        finally:
            _inference_semaphore.release()

        if not result["success"]:
            return FaceEnrollResponse(
                success       = False,
                face_detected = result.get("face_detected", False),
                quality_score = result.get("quality_score"),
                error         = result.get("error"),
            )

        return FaceEnrollResponse(
            success       = True,
            embedding     = result["embedding"],
            quality_score = result["quality_score"],
            face_detected = True,
        )

    except HTTPException:
        raise
    except ValueError as ve:
        raise HTTPException(status_code=400, detail=str(ve))
    except Exception as exc:
        _log.exception("[enroll-face] Unexpected error")
        raise HTTPException(status_code=500, detail=f"AI system error: {exc}")


@app.post("/api/ai/verify-face")
async def verify_face(request: FaceVerificationRequest):
    """
    Xác thực khuôn mặt.

    Registered side (chọn 1 trong 2):
      - registered_embedding: List[float] (512 floats) ← KHUYẾN NGHỊ, dùng sau khi migrate
      - registered_image_base64: str                   ← backward compat, sẽ deprecated

    Live side (chọn 1 trong 2):
      - live_image_base64_list: List[str]  ← multi-frame (HIGH risk, dùng với WebSocket)
      - live_image_base64: str             ← single frame (MEDIUM_2 risk)
    """
    try:
        # ── Validate registered side ──
        has_reg_embedding = bool(request.registered_embedding)
        has_reg_image     = bool((request.registered_image_base64 or "").strip())

        if not has_reg_embedding and not has_reg_image:
            raise ValueError("Provide either registered_embedding or registered_image_base64")

        # Parse registered embedding nếu có
        reg_embedding: Optional[np.ndarray] = None
        reg_img:       Optional[np.ndarray] = None

        if has_reg_embedding:
            if len(request.registered_embedding) != EMBEDDING_DIM:
                raise ValueError(...)
            reg_embedding = np.array(request.registered_embedding, dtype=np.float32)  # ← chỉ convert sang numpy
        else:
            # Fallback: decode ảnh cũ từ DB
            reg_img = decode_base64_image(request.registered_image_base64)

        try:
            await asyncio.wait_for(_inference_semaphore.acquire(), timeout=1.0)
        except asyncio.TimeoutError:
            raise HTTPException(status_code=503, detail={
                "error_code": "INFERENCE_OVERLOADED",
                "is_matched": False,
                "message":    "AI inference at capacity",
            })

        try:
            distance         = None
            frames_evaluated = 1
            frames_used      = 1
            avg_quality      = 0.0
            liveness_score   = 1.0
            liveness_pass    = True
            loop             = asyncio.get_running_loop()

            # ── Multi-frame (HIGH risk) ──
            if request.live_image_base64_list:
                result = await loop.run_in_executor(
                    _inference_executor,
                    functools.partial(verify_multi_frame,
                                      request.live_image_base64_list,
                                      reg_embedding,
                                      reg_img))
                if result.get("error"):
                    raise HTTPException(status_code=400, detail={
                        "error_code":       result["error"],
                        "is_matched":       False,
                        "frames_evaluated": result.get("frames_evaluated", 0),
                        "frames_used":      result.get("frames_used", 0),
                    })
                distance         = result["distance"]
                frames_evaluated = result["frames_evaluated"]
                frames_used      = result["frames_used"]
                avg_quality      = result.get("avg_quality", 0.0)
                liveness_score   = result.get("liveness_score", 1.0)
                liveness_pass    = result.get("liveness_pass", True)

            # ── Single frame (MEDIUM_2) ──
            elif request.live_image_base64:
                img_live = decode_base64_image(request.live_image_base64)

                quality_result = await loop.run_in_executor(
                    _inference_executor,
                    functools.partial(compute_face_quality, img_live))
                avg_quality = quality_result["quality_score"]

                lr             = check_liveness(img_live, quality_result.get("face_bbox"))
                liveness_score = lr["live_score"]
                liveness_pass  = lr["is_live"]

                if not lr["is_live"] and LIVENESS_ENFORCE and not lr["skipped"]:
                    raise HTTPException(status_code=400, detail={
                        "error_code":     "LIVENESS_FAILED",
                        "is_matched":     False,
                        "liveness_score": round(lr["live_score"], 4),
                    })

                if reg_embedding is not None:
                    # ĐƯỜNG MỚI: extract live embedding → so cosine với registered vector
                    live_emb_raw = await loop.run_in_executor(
                        _inference_executor,
                        functools.partial(get_embedding_mtcnn, img_live))
                    if live_emb_raw is None:
                        raise HTTPException(status_code=400, detail={
                            "error_code": "NO_FACE_DETECTED", "is_matched": False})
                    live_vec = normalize_embedding(live_emb_raw)
                    distance = _cosine_distance(live_vec, reg_embedding)
                else:
                    # ĐƯỜNG CŨ: DeepFace.verify với 2 ảnh
                    distance = await loop.run_in_executor(
                        _inference_executor,
                        functools.partial(verify_single_mtcnn, img_live, reg_img))
                    if distance is None:
                        raise HTTPException(status_code=400, detail={
                            "error_code": "NO_FACE_DETECTED", "is_matched": False})
            else:
                raise ValueError("Provide either live_image_base64 or live_image_base64_list")

            classification = classify_distance(distance, avg_quality)
            _log.info(f"[verify] distance={distance:.4f} matched={classification['is_matched']} "
                      f"band={classification['confidence_band']} "
                      f"reg_mode={'embedding' if reg_embedding is not None else 'image'}")

            return {
                "is_matched":          classification["is_matched"],
                "similarity_distance": round(distance, 6),
                "threshold":           classification["effective_threshold"],
                "backend_used":        BACKEND,
                "confidence_band":     classification["confidence_band"],
                "frames_evaluated":    frames_evaluated,
                "frames_used":         frames_used,
                "avg_frame_quality":   round(avg_quality, 4),
                "liveness_pass":       liveness_pass,
                "liveness_score":      round(liveness_score, 4),
                "spoof_detected":      not liveness_pass,
            }

        finally:
            _inference_semaphore.release()

    except HTTPException:
        raise
    except ValueError as ve:
        raise HTTPException(status_code=400, detail=str(ve))
    except Exception as exc:
        _log.exception("[verify-face] Unexpected error")
        raise HTTPException(status_code=500, detail=f"AI system error: {exc}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5000)