from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from deepface import DeepFace
import base64
import cv2
import numpy as np

app = FastAPI()

class FaceVerificationRequest(BaseModel):
    live_image_base64: str
    registered_image_base64: str

def decode_base64_image(base64_string):
    try:
        if "," in base64_string:
            base64_string = base64_string.split(",")[1]
        
        img_data = base64.b64decode(base64_string)
        nparr = np.frombuffer(img_data, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if img is None:
            raise ValueError("Ảnh decode ra NULL")

        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        return img
    except Exception as e:
        raise ValueError(f"Lỗi giải mã ảnh: {str(e)}")


def verify_single(img1, img2, backend):
    """Thử verify với 1 backend, trả về distance hoặc None nếu lỗi"""
    try:
        result = DeepFace.verify(
            img1_path=img1,
            img2_path=img2,
            model_name="ArcFace",
            enforce_detection=True,
            detector_backend=backend,
            distance_metric="cosine"
        )
        return float(result["distance"])
    except Exception as e:
        print(f"⚠️ Backend [{backend}] thất bại: {e}")
        return None


@app.post("/api/ai/verify-face")
async def verify_face(request: FaceVerificationRequest):
    try:
        print("========== AI REQUEST ==========")
        print("LIVE IMAGE LENGTH:", len(request.live_image_base64))
        print("REGISTERED IMAGE LENGTH:", len(request.registered_image_base64))

        img1 = decode_base64_image(request.live_image_base64)
        img2 = decode_base64_image(request.registered_image_base64)

        if img1 is None or img2 is None:
            raise ValueError("Ảnh decode lỗi hoặc NULL")

        print("IMG1 SHAPE:", img1.shape)
        print("IMG2 SHAPE:", img2.shape)

        # ── THAY ĐỔI 1: Thử lần lượt nhiều backend, lấy kết quả đầu tiên thành công ──
        # retinaface chính xác nhất nhưng chậm hơn
        # mtcnn tốt với góc nghiêng
        # opencv fallback cuối cùng
        BACKENDS = ["mtcnn", "opencv"]
        
        distance = None
        used_backend = None
        for backend in BACKENDS:
            print(f"🔍 Thử backend: {backend}")
            distance = verify_single(img1, img2, backend)
            if distance is not None:
                used_backend = backend
                break

        if distance is None:
            raise ValueError("Tất cả backend đều thất bại khi detect khuôn mặt")

        print(f"✅ Backend thành công: {used_backend}")
        print(f"🔥 DISTANCE: {distance:.6f}")

        # ── THAY ĐỔI 2: Threshold nới lên 0.55 (chuẩn ArcFace cosine là 0.68) ──
        # 0.55 vẫn chặt hơn chuẩn nhưng chấp nhận được góc nghiêng nhẹ
        THRESHOLD = 0.45

        is_match = distance <= THRESHOLD

        # ── THAY ĐỔI 3: Log chi tiết hơn để debug ──
        print(f"🔥 THRESHOLD: {THRESHOLD}")
        print(f"🔥 MARGIN: {THRESHOLD - distance:+.4f} ({'PASS' if is_match else 'FAIL'})")
        print(f"🔥 RESULT: {'MATCH ✅' if is_match else 'NOT MATCH ❌'}")

        return {
            "is_matched": is_match,
            "similarity_distance": distance,
            "threshold": THRESHOLD,
            "backend_used": used_backend
        }

    except ValueError as ve:
        print("❌ VALUE ERROR:", ve)
        raise HTTPException(status_code=400, detail=str(ve))

    except Exception as e:
        print("❌ SYSTEM ERROR:", str(e))
        raise HTTPException(status_code=500, detail=f"Lỗi hệ thống AI: {str(e)}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5000)