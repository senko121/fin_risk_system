
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import base64
import cv2
import numpy as np
from tensorflow.keras.models import load_model

app = FastAPI()

# Load model tự train của bro
model = load_model("my_emotion_model.h5")
emotion_labels = ['ANGRY', 'DISGUST', 'FEAR', 'HAPPY', 'NEUTRAL', 'SAD', 'SURPRISE']

class EmotionRequest(BaseModel):
    image_base64: str

def preprocess_image(base64_string):
    try:
        # 🔥 BƯỚC QUAN TRỌNG: Cắt bỏ tiền tố rác
        if "," in base64_string:
            base64_string = base64_string.split(",")[1]
            
        # Giải mã
        img_data = base64.b64decode(base64_string)
        nparr = np.frombuffer(img_data, np.uint8)
        
        # Đọc ảnh dạng Xám (Grayscale) vì model cảm xúc train trên ảnh xám
        img = cv2.imdecode(nparr, cv2.IMREAD_GRAYSCALE) 
        
        if img is None:
            raise ValueError("Không thể đọc dữ liệu ảnh")

        # Resize về đúng kích thước đầu vào của model bro (48x48)
        img = cv2.resize(img, (48, 48))
        
        # Chuẩn hóa về khoảng [0, 1]
        img = img / 255.0
        
        # Thêm chiều (Reshape) để khớp với input shape (1, 48, 48, 1)
        img = np.reshape(img, (1, 48, 48, 1))
        return img
    except Exception as e:
        print(f"❌ Lỗi tiền xử lý ảnh cảm xúc: {e}")
        raise e

@app.post("/api/ai/detect-emotion")
async def detect_emotion(request: EmotionRequest):
    try:
        face = preprocess_image(request.image_base64)
        prediction = model.predict(face)
        label = emotion_labels[np.argmax(prediction)]
        confidence = float(np.max(prediction))
        
        return {"emotion": label, "confidence": confidence}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5001)