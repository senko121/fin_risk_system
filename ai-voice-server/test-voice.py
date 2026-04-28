import json
import os
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from vosk import Model, KaldiRecognizer

app = FastAPI()

# Kiểm tra xem thư mục model có tồn tại không
if not os.path.exists("model"):
    print("🚨 LỖI: Không tìm thấy thư mục 'model'. Vui lòng kiểm tra lại Bước 2!")
    exit(1)

# Nạp model vào RAM
print("Loading Vosk Model...")
model = Model("model")
print("Model Loaded Successfully!")

@app.websocket("/ws/stt")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    
    # Khởi tạo bộ nhận diện với tần số lấy mẫu 16,000 Hz (Chuẩn của âm thanh voice)
    # Ép Vosk chỉ được phép đoán các từ trong danh sách này
    grammar = '["không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín", "[unk]"]'
    rec = KaldiRecognizer(model, 16000, grammar)
    
    try:
        while True:
            # Liên tục hứng cục audio (byte) từ Client gửi lên
            data = await websocket.receive_bytes()
            
            # Quăng vào miệng Vosk để nhai
            if rec.AcceptWaveform(data):
                # Khi user ngắt câu (Có khoảng lặng) -> Chốt kết quả Final
                result = json.loads(rec.Result())
                if result.get("text"):
                    await websocket.send_json({
                        "type": "final",
                        "text": result["text"]
                    })
            else:
                # Khi user đang nói dở -> Trả về kết quả tạm thời để UI nhảy lạch cạch
                partial = json.loads(rec.PartialResult())
                if partial.get("partial"):
                    await websocket.send_json({
                        "type": "partial",
                        "text": partial["partial"]
                    })
                    
    except WebSocketDisconnect:
        print("Client đã ngắt kết nối.")
        
        
        
        