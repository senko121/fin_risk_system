from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, List
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

from face_engine import get_engine

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
_log.info(f"[face-service] Inference executor: max_workers={_INFERENCE_WORKERS}")

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
EMBEDDING_DIM = 512  # InsightFace buffalo_l ArcFace output dimension

# buffalo_l same-person cosine: typically 0.10–0.30
# Different person:              typically 0.55–0.85
THRESHOLD_STRICT = 0.35   # HIGH band  — accept immediately
THRESHOLD_SOFT   = 0.50   # MEDIUM band — accept with quality gate

# Quality scoring: sharpness weighted highest (main webcam failure mode),
# then InsightFace detection confidence, then face size, then brightness.
QUALITY_GATE        = 0.30   # minimum to pass frame selection
TOP_N_FRAMES        = 4      # best frames fed to embedding + liveness
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
    pid = os.getpid()
    _log.info(f"[face-service] pid={pid} warm-up: initialising InsightFace buffalo_l …")
    try:
        engine = get_engine()
        dummy  = np.zeros((112, 112, 3), dtype=np.uint8)
        engine.get_faces(dummy)   # compiles ONNX sessions
        _log.info(f"[face-service] pid={pid} buffalo_l ready")
    except Exception as exc:
        _log.warning(f"[face-service] buffalo_l warm-up failed (non-fatal): {exc}")
    _load_antispoof_models()
    _log.info(f"[face-service] pid={pid} warm-up complete")


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
    _log.info(f"[face-service] Worker shutdown pid={pid} — draining executor …")
    _inference_executor.shutdown(wait=True)


app = FastAPI(lifespan=lifespan)


# ==============================================================
# REQUEST / RESPONSE MODELS
# ==============================================================

class FaceEnrollRequest(BaseModel):
    """Single-image enroll (backward compatible)."""
    image_base64: str
    user_id:      Optional[str] = None


class FaceEnrollResponse(BaseModel):
    success:       bool
    embedding:     Optional[List[float]] = None  # 512 floats, L2-normalised
    quality_score: Optional[float]       = None
    face_detected: bool                  = False
    error:         Optional[str]         = None


class FaceEnrollBatchRequest(BaseModel):
    """Multi-angle enroll: 2–5 images (front, left, right, up, down).
    Java stores all returned embeddings in faceEmbeddings[].
    """
    images_base64: List[str]
    user_id:       Optional[str] = None


class FaceEnrollBatchResponse(BaseModel):
    success:          bool
    embeddings:       Optional[List[List[float]]] = None  # one per accepted image
    embeddings_count: int                         = 0
    errors:           List[str]                   = []


class FaceVerificationRequest(BaseModel):
    # ── Live side ──
    live_image_base64:      Optional[str]       = None
    live_image_base64_list: Optional[List[str]] = None

    # ── Registered side (priority: embeddings > embedding > image) ──
    registered_embeddings:   Optional[List[List[float]]] = None  # multi-angle NEW
    registered_embedding:    Optional[List[float]]       = None  # single    LEGACY
    registered_image_base64: Optional[str]               = None  # raw image OLDEST


# ==============================================================
# IMAGE HELPERS
# ==============================================================

def decode_base64_image(b64: str) -> np.ndarray:
    """Decode base64 (with or without data: prefix) → RGB numpy array."""
    try:
        if "," in b64:
            b64 = b64.split(",")[1]
        img = cv2.imdecode(
            np.frombuffer(base64.b64decode(b64), np.uint8),
            cv2.IMREAD_COLOR,
        )
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


# ==============================================================
# FACE QUALITY  (InsightFace-aware)
# ==============================================================

def compute_face_quality(img_rgb: np.ndarray) -> dict:
    """Detect best face with InsightFace, return quality metrics.

    face_bbox is stored as (x1, y1, x2, y2) xyxy — InsightFace native —
    so no conversion is required before passing to the liveness model.
    """
    engine  = get_engine()
    img_bgr = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2BGR)
    h_img, w_img = img_bgr.shape[:2]

    default = dict(quality_score=0.0, blur_score=0.0, brightness_score=0.0,
                   face_size_score=0.0, det_confidence=0.0,
                   face_detected=False, face_bbox=None)

    face = engine.best_face(img_bgr)
    if face is None:
        return default

    x1 = max(0,     int(face.bbox[0]))
    y1 = max(0,     int(face.bbox[1]))
    x2 = min(w_img, int(face.bbox[2]))
    y2 = min(h_img, int(face.bbox[3]))
    if (x2 - x1) <= 0 or (y2 - y1) <= 0:
        return default

    crop = img_bgr[y1:y2, x1:x2]
    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)

    blur_norm      = float(min(cv2.Laplacian(gray, cv2.CV_64F).var() / 400.0, 1.0))
    face_size_norm = float(min((x2 - x1) * (y2 - y1) / max(w_img * h_img, 1) / 0.15, 1.0))
    b_score        = _brightness_score(float(np.mean(gray)))
    det_conf       = float(face.det_score)

    # Sharpness weighted 45% — primary failure mode on laptop webcams
    quality = (0.45 * blur_norm
               + 0.25 * face_size_norm
               + 0.15 * b_score
               + 0.15 * det_conf)

    return dict(
        quality_score    = round(quality, 4),
        blur_score       = blur_norm,
        brightness_score = b_score,
        face_size_score  = face_size_norm,
        det_confidence   = det_conf,
        face_detected    = True,
        face_bbox        = (x1, y1, x2, y2),   # xyxy — passed directly to liveness
    )


def _effective_soft_threshold(avg_quality: float) -> float:
    if avg_quality >= 0.70:
        return THRESHOLD_SOFT
    if avg_quality >= 0.50:
        return THRESHOLD_SOFT * (0.96 + 0.04 * (avg_quality - 0.50) / 0.20)
    return THRESHOLD_STRICT


def classify_distance(distance: float, avg_quality: float) -> dict:
    if distance <= THRESHOLD_STRICT:
        return {"is_matched": True, "confidence_band": "HIGH",
                "effective_threshold": THRESHOLD_STRICT}
    eff = _effective_soft_threshold(avg_quality)
    if distance <= eff:
        return {"is_matched": True, "confidence_band": "MEDIUM",
                "effective_threshold": round(eff, 4)}
    return {"is_matched": False, "confidence_band": "LOW",
            "effective_threshold": round(eff, 4)}


def select_best_frames(
    imgs: List[np.ndarray],
    top_n: int = TOP_N_FRAMES,
) -> List[tuple]:
    """Score frames; return top-N as (img, quality_score, quality_dict) tuples.

    Passing quality_dict through avoids re-detecting the face in liveness.
    """
    if not imgs:
        return []
    step    = max(1, len(imgs) // MAX_FRAMES_TO_SCORE)
    sampled = imgs[::step][:MAX_FRAMES_TO_SCORE]

    scored = []
    for img in sampled:
        q = compute_face_quality(img)
        if q["face_detected"] and q["quality_score"] >= QUALITY_GATE:
            scored.append((img, q["quality_score"], q))

    scored.sort(key=lambda t: t[1], reverse=True)
    return scored[:top_n]


# ==============================================================
# EMBEDDING  (InsightFace native)
# ==============================================================

def extract_embedding(img_rgb: np.ndarray) -> Optional[np.ndarray]:
    """Extract L2-normalised ArcFace embedding. Returns None if no face found."""
    engine  = get_engine()
    img_bgr = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2BGR)
    return engine.embed(img_bgr)


# ==============================================================
# LIVENESS CHECK
# ==============================================================

def check_liveness(img_rgb: np.ndarray, face_bbox_xyxy: Optional[tuple]) -> dict:
    """face_bbox_xyxy: (x1, y1, x2, y2) — InsightFace native xyxy format."""
    if not _SPOOF_MODELS:
        is_live = not LIVENESS_ENFORCE
        return {"live_score": 1.0 if is_live else 0.0, "is_live": is_live,
                "models_used": 0, "skipped": True}
    if face_bbox_xyxy is None:
        return {"live_score": 0.0, "is_live": False, "models_used": 0, "skipped": False}

    img_bgr = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2BGR)
    bbox    = list(face_bbox_xyxy)   # already xyxy — no conversion needed
    scores  = []

    for model in _SPOOF_MODELS:
        try:
            result    = model.predict(img_bgr, bbox)
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

    diffs     = [float(np.mean(np.abs(grays[i] - grays[i - 1])) / 255.0)
                 for i in range(1, len(grays))]
    mean_diff = float(np.mean(diffs))
    std_diff  = float(np.std(diffs))
    is_replay = (mean_diff < REPLAY_DIFF_MEAN_THRESHOLD
                 and std_diff < REPLAY_DIFF_STD_THRESHOLD)

    return {"is_replay": is_replay,
            "reason": "STATIC_FRAME_SEQUENCE" if is_replay else None,
            "frame_diff_mean": round(mean_diff, 5),
            "frame_diff_std":  round(std_diff, 5)}


def _run_liveness_on_frames(best_frames: list) -> dict:
    if not _SPOOF_MODELS:
        return {"pass": not LIVENESS_ENFORCE, "score": 1.0,
                "reason": "SKIPPED_NO_MODELS", "frames_checked": 0, "frames_passed": 0}

    frames_passed, scores = 0, []
    for img, _quality, quality_dict in best_frames:   # 3-tuple from select_best_frames
        lr = check_liveness(img, quality_dict.get("face_bbox"))
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
# REGISTERED EMBEDDING HELPERS
# ==============================================================

def _parse_registered_embeddings(request: FaceVerificationRequest) -> Optional[List[np.ndarray]]:
    """Parse the embedding fields that don't require inference (embeddings/embedding).
    Returns None when only registered_image_base64 is present (needs inference later).
    """
    if request.registered_embeddings:
        return [np.array(e, dtype=np.float32) for e in request.registered_embeddings]
    if request.registered_embedding:
        if len(request.registered_embedding) != EMBEDDING_DIM:
            raise ValueError(
                f"registered_embedding must be {EMBEDDING_DIM}-dim, "
                f"got {len(request.registered_embedding)}"
            )
        return [np.array(request.registered_embedding, dtype=np.float32)]
    return None   # caller must handle registered_image_base64 path


# ==============================================================
# ENROLL LOGIC
# ==============================================================

def _enroll_single_sync(img_rgb: np.ndarray, user_id: Optional[str]) -> dict:
    quality = compute_face_quality(img_rgb)

    if not quality["face_detected"]:
        return {"success": False, "error": "NO_FACE_DETECTED",
                "face_detected": False, "quality_score": 0.0}

    if quality["quality_score"] < QUALITY_GATE:
        return {"success": False,
                "error": f"QUALITY_TOO_LOW (score={quality['quality_score']:.3f}, "
                         f"min={QUALITY_GATE})",
                "face_detected": True, "quality_score": quality["quality_score"]}

    emb = extract_embedding(img_rgb)
    if emb is None:
        return {"success": False, "error": "EMBEDDING_FAILED",
                "face_detected": True, "quality_score": quality["quality_score"]}

    _log.info(f"[enroll] user={user_id or '?'} quality={quality['quality_score']:.4f}")
    return {"success": True, "embedding": emb.tolist(),
            "face_detected": True, "quality_score": quality["quality_score"]}


# ==============================================================
# VERIFY LOGIC
# ==============================================================

def verify_multi_frame(
    live_frames_b64: List[str],
    reg_embs: List[np.ndarray],
) -> dict:
    """
    Multi-frame verification against a list of registered embeddings.

    For each good live frame, find its best (min) cosine distance against all
    registered embeddings.  Return the overall minimum — the live frame that
    best matches any registered angle wins.  This avoids averaging which can
    dilute a good embedding with bad ones from adjacent frames.
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
        return {"error": "NO_QUALITY_FRAMES",
                "frames_evaluated": len(decoded), "frames_used": 0}

    # ── Replay ──
    t0     = time.perf_counter()
    replay = detect_replay_attack(decoded)
    _log.info(f"[replay] {(time.perf_counter()-t0)*1000:.1f}ms "
              f"is_replay={replay['is_replay']} "
              f"mean={replay['frame_diff_mean']:.5f} std={replay['frame_diff_std']:.5f}")
    if replay["is_replay"]:
        return {"error": "REPLAY_ATTACK_DETECTED", "frames_evaluated": len(decoded),
                "frames_used": 0, "frame_diff_mean": replay["frame_diff_mean"]}

    # ── Liveness (reuses pre-computed quality_dict — no double detection) ──
    t0       = time.perf_counter()
    liveness = _run_liveness_on_frames(best_frames)
    _log.info(f"[liveness] {(time.perf_counter()-t0)*1000:.1f}ms "
              f"pass={liveness['pass']} score={liveness['score']:.4f} "
              f"frames={liveness['frames_passed']}/{liveness['frames_checked']}")
    if not liveness["pass"]:
        return {"error": "LIVENESS_FAILED", "frames_evaluated": len(decoded),
                "frames_used": 0, "liveness_score": liveness["score"],
                "frames_liveness_passed": liveness["frames_passed"]}

    # ── Embeddings: per-frame best match, take overall min ──
    engine        = get_engine()
    best_distance = 1.0
    qualities     = []
    frames_used   = 0

    for img, quality_score, _ in best_frames:
        emb = extract_embedding(img)
        if emb is None:
            continue
        dist = engine.best_match(emb, reg_embs)
        if dist < best_distance:
            best_distance = dist
        qualities.append(quality_score)
        frames_used += 1

    if frames_used == 0:
        return {"error": "NO_FACE_DETECTED",
                "frames_evaluated": len(decoded), "frames_used": 0, "avg_quality": 0.0}

    return {
        "error":            None,
        "distance":         best_distance,
        "frames_evaluated": len(decoded),
        "frames_used":      frames_used,
        "avg_quality":      float(np.mean(qualities)),
        "liveness_score":   liveness["score"],
        "liveness_pass":    True,
    }


# ==============================================================
# ENDPOINTS
# ==============================================================

@app.post("/api/ai/enroll-face", response_model=FaceEnrollResponse)
async def enroll_face(request: FaceEnrollRequest):
    """Single-image enroll. Returns one 512-dim embedding (backward compatible)."""
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
                functools.partial(_enroll_single_sync, img_rgb, request.user_id),
            )
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


@app.post("/api/ai/enroll-face-batch", response_model=FaceEnrollBatchResponse)
async def enroll_face_batch(request: FaceEnrollBatchRequest):
    """Multi-angle enroll. Submit 2–5 images (front/left/right/up/down).
    Returns one embedding per accepted frame; Java stores all in faceEmbeddings[].
    """
    try:
        if not request.images_base64:
            raise ValueError("images_base64 must not be empty")
        if len(request.images_base64) > 10:
            raise ValueError("Maximum 10 images per batch")

        try:
            await asyncio.wait_for(_inference_semaphore.acquire(), timeout=2.0)
        except asyncio.TimeoutError:
            raise HTTPException(status_code=503, detail="Inference at capacity, retry later")

        embeddings: List[List[float]] = []
        errors:     List[str]         = []

        try:
            loop = asyncio.get_running_loop()
            for idx, b64 in enumerate(request.images_base64):
                try:
                    img_rgb = decode_base64_image(b64)
                    result  = await loop.run_in_executor(
                        _inference_executor,
                        functools.partial(_enroll_single_sync, img_rgb, request.user_id),
                    )
                    if result["success"]:
                        embeddings.append(result["embedding"])
                    else:
                        errors.append(f"frame_{idx}: {result.get('error', 'UNKNOWN')}")
                except Exception as exc:
                    errors.append(f"frame_{idx}: {exc}")
        finally:
            _inference_semaphore.release()

        if not embeddings:
            return FaceEnrollBatchResponse(success=False, embeddings_count=0, errors=errors)

        _log.info(f"[enroll-batch] user={request.user_id or '?'} "
                  f"accepted={len(embeddings)}/{len(request.images_base64)}")
        return FaceEnrollBatchResponse(
            success          = True,
            embeddings       = embeddings,
            embeddings_count = len(embeddings),
            errors           = errors,
        )

    except HTTPException:
        raise
    except ValueError as ve:
        raise HTTPException(status_code=400, detail=str(ve))
    except Exception as exc:
        _log.exception("[enroll-face-batch] Unexpected error")
        raise HTTPException(status_code=500, detail=f"AI system error: {exc}")


@app.post("/api/ai/verify-face")
async def verify_face(request: FaceVerificationRequest):
    """Verify live face against registered embeddings.

    Registered side (pick one, priority order):
      registered_embeddings:   List[List[float]]  ← multi-angle (recommended)
      registered_embedding:    List[float]         ← single embedding (legacy)
      registered_image_base64: str                 ← raw image (deprecated)

    Live side (pick one):
      live_image_base64_list:  List[str]           ← multi-frame (high-risk flows)
      live_image_base64:       str                 ← single frame (medium-risk flows)
    """
    try:
        # ── Validate registered side (no inference yet) ──────────────────────
        reg_embs_parsed = _parse_registered_embeddings(request)
        has_reg_image   = bool((request.registered_image_base64 or "").strip())

        if reg_embs_parsed is None and not has_reg_image:
            raise ValueError(
                "Provide registered_embeddings, registered_embedding, "
                "or registered_image_base64"
            )

        # ── Acquire semaphore ─────────────────────────────────────────────────
        try:
            await asyncio.wait_for(_inference_semaphore.acquire(), timeout=1.0)
        except asyncio.TimeoutError:
            raise HTTPException(status_code=503, detail={
                "error_code": "INFERENCE_OVERLOADED",
                "is_matched": False,
                "message":    "AI inference at capacity",
            })

        try:
            loop = asyncio.get_running_loop()

            # ── Resolve registered embeddings (inference only for image path) ──
            if reg_embs_parsed is not None:
                reg_embs = reg_embs_parsed
            else:
                reg_img = decode_base64_image(request.registered_image_base64)
                reg_emb = await loop.run_in_executor(
                    _inference_executor,
                    functools.partial(extract_embedding, reg_img),
                )
                if reg_emb is None:
                    raise HTTPException(status_code=400, detail={
                        "error_code": "REGISTERED_FACE_NOT_DETECTED",
                        "is_matched": False,
                    })
                reg_embs = [reg_emb]

            # ── Live inference ────────────────────────────────────────────────
            distance         = None
            frames_evaluated = 1
            frames_used      = 1
            avg_quality      = 0.0
            liveness_score   = 1.0
            liveness_pass    = True

            # Multi-frame (HIGH risk)
            if request.live_image_base64_list:
                result = await loop.run_in_executor(
                    _inference_executor,
                    functools.partial(
                        verify_multi_frame,
                        request.live_image_base64_list,
                        reg_embs,
                    ),
                )
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

            # Single frame (MEDIUM risk)
            elif request.live_image_base64:
                img_live = decode_base64_image(request.live_image_base64)

                quality_result = await loop.run_in_executor(
                    _inference_executor,
                    functools.partial(compute_face_quality, img_live),
                )
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

                live_emb = await loop.run_in_executor(
                    _inference_executor,
                    functools.partial(extract_embedding, img_live),
                )
                if live_emb is None:
                    raise HTTPException(status_code=400, detail={
                        "error_code": "NO_FACE_DETECTED", "is_matched": False,
                    })

                distance = get_engine().best_match(live_emb, reg_embs)

            else:
                raise ValueError(
                    "Provide either live_image_base64 or live_image_base64_list"
                )

            classification = classify_distance(distance, avg_quality)
            reg_mode = (
                "multi_embedding"  if request.registered_embeddings else
                "single_embedding" if request.registered_embedding  else
                "image"
            )
            _log.info(
                f"[verify] distance={distance:.4f} matched={classification['is_matched']} "
                f"band={classification['confidence_band']} "
                f"reg_mode={reg_mode} reg_count={len(reg_embs)}"
            )

            return {
                "is_matched":          classification["is_matched"],
                "similarity_distance": round(distance, 6),
                "threshold":           classification["effective_threshold"],
                "backend_used":        "insightface/buffalo_l",
                "confidence_band":     classification["confidence_band"],
                "frames_evaluated":    frames_evaluated,
                "frames_used":         frames_used,
                "avg_frame_quality":   round(avg_quality, 4),
                "liveness_pass":       liveness_pass,
                "liveness_score":      round(liveness_score, 4),
                "spoof_detected":      not liveness_pass,
                "registered_count":    len(reg_embs),
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
