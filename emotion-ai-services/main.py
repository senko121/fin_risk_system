
# from fastapi import FastAPI, HTTPException
# from pydantic import BaseModel
# import base64
# import cv2
# import numpy as np
# from tensorflow.keras.models import load_model

# app = FastAPI()

# # Load model tự train của bro
# model = load_model("my_emotion_model.h5")
# emotion_labels = ['ANGRY', 'DISGUST', 'FEAR', 'HAPPY', 'NEUTRAL', 'SAD', 'SURPRISE']

# class EmotionRequest(BaseModel):
#     image_base64: str

# def preprocess_image(base64_string):
#     try:
#         # 🔥 BƯỚC QUAN TRỌNG: Cắt bỏ tiền tố rác
#         if "," in base64_string:
#             base64_string = base64_string.split(",")[1]
            
#         # Giải mã
#         img_data = base64.b64decode(base64_string)
#         nparr = np.frombuffer(img_data, np.uint8)
        
#         # Đọc ảnh dạng Xám (Grayscale) vì model cảm xúc train trên ảnh xám
#         img = cv2.imdecode(nparr, cv2.IMREAD_GRAYSCALE) 
        
#         if img is None:
#             raise ValueError("Không thể đọc dữ liệu ảnh")

#         # Resize về đúng kích thước đầu vào của model bro (48x48)
#         img = cv2.resize(img, (48, 48))
        
#         # Chuẩn hóa về khoảng [0, 1]
#         img = img / 255.0
        
#         # Thêm chiều (Reshape) để khớp với input shape (1, 48, 48, 1)
#         img = np.reshape(img, (1, 48, 48, 1))
#         return img
#     except Exception as e:
#         print(f"❌ Lỗi tiền xử lý ảnh cảm xúc: {e}")
#         raise e

# @app.post("/api/ai/detect-emotion")
# async def detect_emotion(request: EmotionRequest):
#     try:
#         face = preprocess_image(request.image_base64)
#         prediction = model.predict(face)
#         label = emotion_labels[np.argmax(prediction)]
#         confidence = float(np.max(prediction))
        
#         return {"emotion": label, "confidence": confidence}
#     except Exception as e:
#         raise HTTPException(status_code=500, detail=str(e))

# if __name__ == "__main__":
#     import uvicorn
#     uvicorn.run(app, host="0.0.0.0", port=5001)


from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import base64
import cv2
import numpy as np
import time
import os
import logging
from datetime import datetime
from tensorflow.keras.models import load_model

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
# 2. KHỞI TẠO APP VÀ MODEL
# ==========================================
app = FastAPI()

logger.info("⏳ Đang nạp AI Model vào bộ nhớ...")
model = load_model("my_emotion_model.h5")
emotion_labels = ['ANGRY', 'DISGUST', 'FEAR', 'HAPPY', 'NEUTRAL', 'SAD', 'SURPRISE']
logger.info("✅ Nạp Model thành công! Server AI đã sẵn sàng.")

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
    start_time = time.time() # Bắt đầu bấm giờ
    request_id = str(int(start_time * 1000))[-6:] # Tạo một mã ID ngắn gọn cho request
    
    logger.info(f"[{request_id}] ------------------------------------------")
    logger.info(f"[{request_id}] 📥 NHẬN YÊU CẦU QUÉT KHUÔN MẶT MỚI")
    
    try:
        # 1. Tiền xử lý
        face = preprocess_image(request.image_base64)
        logger.info(f"[{request_id}] Đã xử lý ảnh thành công. Kích thước tensor: {face.shape}")
        
        # 2. Dự đoán
        prediction = model.predict(face, verbose=0) # verbose=0 để Keras không in rác ra màn hình
        label = emotion_labels[np.argmax(prediction)]
        confidence = float(np.max(prediction))
        
        # 3. Tính thời gian xử lý (Millisecond)
        process_time_ms = round((time.time() - start_time) * 1000, 2)
        
        # 4. In mảng xác suất của tất cả các nhãn (Cực kỳ hữu ích để debug và làm biểu đồ)
        prob_details = {emotion_labels[i]: round(float(prediction[0][i]) * 100, 2) for i in range(len(emotion_labels))}
        
        logger.info(f"[{request_id}] 📊 Chi tiết dự đoán: {prob_details}")
        logger.info(f"[{request_id}] 🎯 KẾT QUẢ CHỐT: {label} ({confidence*100:.2f}%)")
        logger.info(f"[{request_id}] ⏱️ Thời gian phản hồi: {process_time_ms} ms")
        
        return {
            "emotion": label, 
            "confidence": confidence,
            "prob_details": prob_details,
            "process_time_ms": process_time_ms
        }
        
    except Exception as e:
        logger.error(f"[{request_id}] ❌ LỖI NGHIÊM TRỌNG TRONG QUÁ TRÌNH NHẬN DIỆN: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5001)