 

from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List
from collections import Counter
import base64
import cv2
import numpy as np
import time
import os
import logging
from datetime import datetime
from tensorflow.keras.models import load_model
import asyncio
import concurrent.futures
import functools

# ==========================================
# 1. CẤU HÌNH HỆ THỐNG GHI LOG (HỘP ĐEN)
# ==========================================
# Tạo thư mục 'logs' nếu chưa tồn tại
os.makedirs("logs", exist_ok=True)

# Lấy ngày hiện tại để đặt tên file log (VD: ai_emotion_2026-05-05.log)
log_filename = f"logs/ai_emotion_{datetime.now().strftime('%Y-%m-%d')}.log"

# Cấu hình format của log: [Thời gian] | [Mức độ] | [Nội dung]
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(message)s",
    handlers=[
        logging.FileHandler(log_filename, encoding='utf-8'), # Ghi ra file
        logging.StreamHandler()                              # In ra cả màn hình terminal
    ]
)
logger = logging.getLogger(__name__)

# ==========================================
# 2. KHỞI TẠO MODEL
# ==========================================
logger.info("⏳ Đang nạp AI Model vào bộ nhớ...")
model = load_model("my_emotion_model.h5")
emotion_labels = ['ANGRY', 'DISGUST', 'FEAR', 'HAPPY', 'NEUTRAL', 'SAD', 'SURPRISE']
logger.info("✅ Nạp Model thành công! Server AI đã sẵn sàng.")

# ── Inference executor ────────────────────────────────────────────────────────
# Moves model.predict() off the FastAPI event loop. Keras/TF2 releases the GIL
# during forward-pass computation, so threads achieve real concurrency.
_EMOTION_INFERENCE_WORKERS = int(os.environ.get("EMOTION_INFERENCE_WORKERS", "4"))
_inference_executor = concurrent.futures.ThreadPoolExecutor(
    max_workers=_EMOTION_INFERENCE_WORKERS,
    thread_name_prefix="inference-emotion",
)
logger.info(f"[emotion-service] Inference executor ready: max_workers={_EMOTION_INFERENCE_WORKERS}")

# ── Backpressure semaphore ────────────────────────────────────────────────────
# Higher multiplier than face-service (3×) because emotion inference is faster
# (~500 ms per batch vs ~3 s for face verify), so more queuing is safe.
# STREAM single-frame endpoint uses a 0.2 s acquisition timeout (fast-fail,
# UI shows UNKNOWN for one frame).  FINALIZE batch endpoint uses 1.0 s.
_MAX_CONCURRENT_EMOTION: int = _EMOTION_INFERENCE_WORKERS * 3
_inference_semaphore: asyncio.Semaphore  # assigned in lifespan per worker


def _executor_pending(executor: concurrent.futures.ThreadPoolExecutor) -> int:
    """Best-effort queue depth. Returns -1 when unavailable (Python 3.9+ SimpleQueue lacks qsize)."""
    try:
        return executor._work_queue.qsize()
    except AttributeError:
        return -1


def _infer_sequence(frames_b64: List[str]) -> list:
    """Preprocess + predict for every frame in an inference thread; returns list of prediction arrays."""
    results = []
    for b64 in frames_b64:
        face = preprocess_image(b64)
        pred = model.predict(face, verbose=0)
        results.append(pred)
    return results


# ==============================================================
# WARM-UP
# ==============================================================
def _warmup_models() -> None:
    """
    Run one dummy inference to force Keras/TF to finish JIT compilation
    and allocate memory before the first real request arrives.
    """
    logger.info(f"[emotion-service] pid={os.getpid()} warm-up: running dummy predict ...")
    try:
        dummy = np.zeros((1, 48, 48, 1), dtype=np.float32)
        model.predict(dummy, verbose=0)
        logger.info(f"[emotion-service] pid={os.getpid()} warm-up complete")
    except Exception as exc:
        logger.warning(f"[emotion-service] pid={os.getpid()} warm-up failed (non-fatal): {exc}")


# ==============================================================
# LIFESPAN  (startup / shutdown hooks for every worker process)
# ==============================================================
@asynccontextmanager
async def lifespan(app: FastAPI):
    global _inference_semaphore
    pid = os.getpid()
    _inference_semaphore = asyncio.Semaphore(_MAX_CONCURRENT_EMOTION)
    logger.info(
        f"[emotion-service] Worker boot  pid={pid} "
        f"inference_workers={_EMOTION_INFERENCE_WORKERS} "
        f"max_concurrent={_MAX_CONCURRENT_EMOTION}"
    )
    loop = asyncio.get_running_loop()
    try:
        await loop.run_in_executor(_inference_executor, _warmup_models)
    except Exception as exc:
        logger.warning(f"[emotion-service] pid={pid} lifespan warm-up error (non-fatal): {exc}")

    yield  # ── worker is alive and serving requests ──

    logger.info(f"[emotion-service] Worker shutdown pid={pid} — draining inference executor ...")
    _inference_executor.shutdown(wait=True)
    logger.info(f"[emotion-service] Worker shutdown pid={pid} complete")


# ── App must be created AFTER lifespan is defined ────────────────────────────
app = FastAPI(lifespan=lifespan)


class EmotionRequest(BaseModel):
    image_base64: str

def preprocess_image(base64_string):
    try:
        # Cắt bỏ tiền tố rác
        if "," in base64_string:
            base64_string = base64_string.split(",")[1]
            
        # Giải mã
        img_data = base64.b64decode(base64_string)
        nparr = np.frombuffer(img_data, np.uint8)
        
        # Đọc ảnh dạng Xám (Grayscale)
        img = cv2.imdecode(nparr, cv2.IMREAD_GRAYSCALE) 
        
        if img is None:
            raise ValueError("Không thể trích xuất dữ liệu ảnh từ chuỗi Base64")

        # Resize về đúng kích thước đầu vào của model (48x48)
        img = cv2.resize(img, (48, 48))
        
        # Chuẩn hóa về khoảng [0, 1]
        img = img / 255.0
        
        # Thêm chiều (Reshape) để khớp với input shape (1, 48, 48, 1)
        img = np.reshape(img, (1, 48, 48, 1))
        return img
    except Exception as e:
        logger.error(f"Lỗi tiền xử lý ảnh (Pre-processing): {e}")
        raise e

# ==========================================
# 3. API NHẬN DIỆN CẢM XÚC
# ==========================================
@app.post("/api/ai/detect-emotion")
async def detect_emotion(request: EmotionRequest):
    start_time = time.time()
    request_id = str(int(start_time * 1000))[-6:]

    logger.info(f"[{request_id}] ------------------------------------------")
    logger.info(f"[{request_id}] 📥 NHẬN YÊU CẦU QUÉT KHUÔN MẶT MỚI")

    # Backpressure: STREAM single-frame calls are expendable — fail fast (0.2 s)
    # so Java's realtimeEmotionExecutor gets a quick null back and moves on.
    try:
        await asyncio.wait_for(_inference_semaphore.acquire(), timeout=0.2)
    except asyncio.TimeoutError:
        logger.warning(
            f"[{request_id}] [detect-emotion] OVERLOADED — semaphore full "
            f"(max={_MAX_CONCURRENT_EMOTION}) — returning 503"
        )
        raise HTTPException(
            status_code=503,
            detail={"error_code": "INFERENCE_OVERLOADED", "emotion": "UNKNOWN"},
        )

    try:
        # 1. Tiền xử lý
        face = preprocess_image(request.image_base64)
        logger.info(f"[{request_id}] Đã xử lý ảnh thành công. Kích thước tensor: {face.shape}")

        # 2. Dự đoán
        _t_infer = time.time()
        prediction = await asyncio.get_running_loop().run_in_executor(
            _inference_executor,
            functools.partial(model.predict, face, verbose=0),
        )
        logger.info(f"[{request_id}] ⏱️ Model inference: {round((time.time() - _t_infer) * 1000, 2)} ms")
        label = emotion_labels[np.argmax(prediction)]
        confidence = float(np.max(prediction))

        process_time_ms = round((time.time() - start_time) * 1000, 2)
        prob_details = {emotion_labels[i]: round(float(prediction[0][i]) * 100, 2) for i in range(len(emotion_labels))}

        logger.info(f"[{request_id}] 📊 Chi tiết dự đoán: {prob_details}")
        logger.info(f"[{request_id}] 🎯 KẾT QUẢ CHỐT: {label} ({confidence*100:.2f}%)")
        logger.info(f"[{request_id}] ⏱️ Thời gian phản hồi: {process_time_ms} ms")

        return {
            "emotion": label,
            "confidence": confidence,
            "prob_details": prob_details,
            "process_time_ms": process_time_ms,
        }

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[{request_id}] ❌ LỖI NGHIÊM TRỌNG TRONG QUÁ TRÌNH NHẬN DIỆN: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        _inference_semaphore.release()
    # Thêm DTO mới để nhận mảng ảnh
class EmotionSequenceRequest(BaseModel):
    image_base64_list: List[str]

# ==========================================
# 4. API NHẬN DIỆN CHUỖI CẢM XÚC (BATCH EVALUATION)
# ==========================================
@app.post("/api/ai/detect-emotion-sequence")
async def detect_emotion_sequence(request: EmotionSequenceRequest):
    start_time = time.time()
    request_id = f"BATCH-{str(int(start_time * 1000))[-6:]}"

    frames = request.image_base64_list
    total_frames = len(frames)

    logger.info(f"[{request_id}] ==========================================")
    logger.info(f"[{request_id}] 🎞️ NHẬN CHUỖI {total_frames} KHUNG HÌNH (BATCH)")

    if total_frames == 0:
        raise HTTPException(status_code=400, detail="Mảng ảnh trống!")

    # Backpressure: FINALIZE batch calls matter — allow 1.0 s queue wait before
    # rejecting.  Java treats 503 → null emotion → UNKNOWN (no coercion flag),
    # so the FINALIZE can still pass if identity check succeeds.
    try:
        await asyncio.wait_for(_inference_semaphore.acquire(), timeout=1.0)
    except asyncio.TimeoutError:
        logger.warning(
            f"[{request_id}] [detect-emotion-sequence] OVERLOADED — semaphore full "
            f"(max={_MAX_CONCURRENT_EMOTION}) — returning 503"
        )
        raise HTTPException(
            status_code=503,
            detail={"error_code": "INFERENCE_OVERLOADED", "emotion": "UNKNOWN"},
        )

    emotions_detected = []
    total_confidence = 0.0
    aggregate_probs = {label: 0.0 for label in emotion_labels}

    try:
        _t_infer = time.time()
        predictions = await asyncio.get_running_loop().run_in_executor(
            _inference_executor,
            functools.partial(_infer_sequence, frames),
        )
        logger.info(
            f"[{request_id}] ⏱️ Batch inference elapsed: "
            f"{round((time.time() - _t_infer) * 1000, 2)} ms ({total_frames} frames)"
        )

        for idx, prediction in enumerate(predictions):
            label = emotion_labels[np.argmax(prediction)]
            confidence = float(np.max(prediction))

            emotions_detected.append(label)
            total_confidence += confidence

            for i, emo in enumerate(emotion_labels):
                aggregate_probs[emo] += float(prediction[0][i])

            logger.info(f"[{request_id}] ↳ Frame {idx+1}/{total_frames}: {label} ({confidence*100:.1f}%)")

        avg_probs = {emo: round((val / total_frames) * 100, 2) for emo, val in aggregate_probs.items()}
        avg_confidence = total_confidence / total_frames
        emotion_counts = Counter(emotions_detected)

        # Coercion detection: FEAR/ANGRY/DISGUST >= 30% of frames → flag
        negative_emotions = emotion_counts.get('FEAR', 0) + emotion_counts.get('ANGRY', 0) + emotion_counts.get('DISGUST', 0)
        negative_ratio = negative_emotions / total_frames

        if negative_ratio >= 0.3:
            final_emotion = "FEAR"
            logger.warning(f"[{request_id}] 🚨 BÁO ĐỘNG: Phát hiện cảm xúc tiêu cực chiếm {negative_ratio*100:.1f}% thời lượng!")
        else:
            final_emotion = emotion_counts.most_common(1)[0][0]

        process_time_ms = round((time.time() - start_time) * 1000, 2)

        logger.info(f"[{request_id}] 🎯 KẾT QUẢ CHUỖI: {final_emotion} (Tần suất: {dict(emotion_counts)})")
        logger.info(f"[{request_id}] ⏱️ Tổng thời gian xử lý: {process_time_ms} ms")

        return {
            "emotion": final_emotion,
            "confidence": avg_confidence,
            "prob_details": avg_probs,
            "process_time_ms": process_time_ms,
        }

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"[{request_id}] ❌ LỖI BATCH: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        _inference_semaphore.release()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5001)
    