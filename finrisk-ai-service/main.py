from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from deepface import DeepFace
import base64
import cv2
import numpy as np

app = FastAPI()

# Định nghĩa dữ liệu đầu vào từ Spring Boot
class FaceVerificationRequest(BaseModel):
    live_image_base64: str  # Ảnh quét từ Webcam lúc chuyển tiền
    registered_image_base64: str  # Ảnh gốc trong CSDL

# Hàm giải mã Base64 thành mảng hình ảnh cho OpenCV
def decode_base64_image(base64_string):
    try:
        if "," in base64_string:
            base64_string = base64_string.split(",")[1]
        
        img_data = base64.b64decode(base64_string)
        nparr = np.frombuffer(img_data, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if img is None:
            raise ValueError("Ảnh decode ra NULL")

        # 🔥 FIX: BGR → RGB
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

        return img
    except Exception as e:
        raise ValueError("Lỗi giải mã ảnh Base64: " + str(e))

@app.post("/api/ai/verify-face")
async def verify_face(request: FaceVerificationRequest):
    try:
        print("========== AI REQUEST ==========")
        print("LIVE IMAGE LENGTH:", len(request.live_image_base64))
        print("REGISTERED IMAGE LENGTH:", len(request.registered_image_base64))

        # 1. Decode ảnh
        img1 = decode_base64_image(request.live_image_base64)
        img2 = decode_base64_image(request.registered_image_base64)

        # 🔥 Check ảnh hợp lệ
        if img1 is None or img2 is None:
            raise ValueError("Ảnh decode lỗi hoặc NULL")

        print("IMG1 SHAPE:", img1.shape)
        print("IMG2 SHAPE:", img2.shape)

        # 2. Gọi AI
        result = DeepFace.verify(
            img1_path=img1,
            img2_path=img2,
            model_name="ArcFace",              # 🔥 nâng cấp model
            enforce_detection=False,           # 🔥 tránh fail detect
            detector_backend="opencv"          # 🔥 ổn định
        )

        # 3. Lấy distance
        distance = float(result["distance"])

        print("🔥 DISTANCE:", distance)

        # 🔥 RULE CHUẨN
        custom_threshold = 0.4

        is_match = distance <= custom_threshold

        print("🔥 RESULT:", "MATCH ✅" if is_match else "NOT MATCH ❌")

        # 4. Trả về
        return {
            "is_matched": is_match,
            "similarity_distance": distance,
            "threshold": custom_threshold
        }

    except ValueError as ve:
        print("❌ VALUE ERROR:", ve)
        raise HTTPException(status_code=400, detail=str(ve))

    except Exception as e:
        print("❌ SYSTEM ERROR:", str(e))
        raise HTTPException(status_code=500, detail=f"Lỗi hệ thống AI: {str(e)}")
    
# Chạy server ở port 5000
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5000)