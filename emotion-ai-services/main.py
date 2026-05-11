 

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

    emotions_detected = []
    total_confidence = 0.0
    aggregate_probs = {label: 0.0 for label in emotion_labels}
    
    try:
        # 1. Duyệt qua từng khung hình để dự đoán
        for idx, base64_img in enumerate(frames):
            face = preprocess_image(base64_img)
            prediction = model.predict(face, verbose=0)
            
            label = emotion_labels[np.argmax(prediction)]
            confidence = float(np.max(prediction))
            
            emotions_detected.append(label)
            total_confidence += confidence
            
            # Cộng dồn xác suất để tính trung bình
            for i, emo in enumerate(emotion_labels):
                aggregate_probs[emo] += float(prediction[0][i])
                
            logger.info(f"[{request_id}] ↳ Frame {idx+1}/{total_frames}: {label} ({confidence*100:.1f}%)")

        # 2. THUẬT TOÁN TỔNG HỢP & NỘI SUY (AGGREGATION LOGIC)
        # Tính xác suất trung bình của cả chuỗi
        avg_probs = {emo: round((val / total_frames) * 100, 2) for emo, val in aggregate_probs.items()}
        avg_confidence = total_confidence / total_frames
        
        # Thống kê tần suất xuất hiện
        emotion_counts = Counter(emotions_detected)
        
        # LUẬT BẢO MẬT (COERCION DETECTION): 
        # Nếu FEAR, ANGRY hoặc DISGUST xuất hiện >= 30% số khung hình -> BÁO ĐỘNG NGAY
        negative_emotions = emotion_counts.get('FEAR', 0) + emotion_counts.get('ANGRY', 0) + emotion_counts.get('DISGUST', 0)
        negative_ratio = negative_emotions / total_frames
        
        final_emotion = "NEUTRAL" # Mặc định
        
        if negative_ratio >= 0.3:
            final_emotion = "FEAR" # Ép hệ thống báo cờ đỏ
            logger.warning(f"[{request_id}] 🚨 BÁO ĐỘNG: Phát hiện cảm xúc tiêu cực chiếm {negative_ratio*100:.1f}% thời lượng!")
        else:
            # Nếu an toàn, lấy cảm xúc xuất hiện nhiều nhất (Most common)
            final_emotion = emotion_counts.most_common(1)[0][0]

        process_time_ms = round((time.time() - start_time) * 1000, 2)
        
        logger.info(f"[{request_id}] 🎯 KẾT QUẢ CHUỖI: {final_emotion} (Tần suất: {dict(emotion_counts)})")
        logger.info(f"[{request_id}] ⏱️ Tổng thời gian xử lý: {process_time_ms} ms")
        
        # 3. Trả về đúng cấu trúc EmotionAIResponse.java mong đợi
        return {
            "emotion": final_emotion, 
            "confidence": avg_confidence,
            "prob_details": avg_probs,
            "process_time_ms": process_time_ms
        }
        
    except Exception as e:
        logger.error(f"[{request_id}] ❌ LỖI BATCH: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5001)
    