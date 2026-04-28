import os
import json
import wave
from io import BytesIO
from fastapi import FastAPI, UploadFile, File, HTTPException
from vosk import Model, KaldiRecognizer

app = FastAPI()

# 1. Kiểm tra và Nạp Model
if not os.path.exists("model"):
    print("🚨 LỖI: Không tìm thấy thư mục 'model'. Vui lòng kiểm tra lại!")
    exit(1)

print("Loading Vosk Model...")
model = Model("model")
print("Model Loaded Successfully!")

# 2. Bộ từ điển dịch "Chữ" sang "Số"
WORD_TO_NUM = {
    "không": "0",
    "một": "1",
    "hai": "2",
    "ba": "3",
    "bốn": "4",
    "năm": "5",
    "sáu": "6",
    "bảy": "7",
    "tám": "8",
    "chín": "9"
}

@app.post("/api/ai/verify-voice")
async def verify_voice(audio_file: UploadFile = File(...)):
    """
    API nhận file audio (.wav), bóc băng bằng Vosk, và trả về mã số (authCode).
    Yêu cầu File: Định dạng WAV, 1 Channel (Mono), Sample Rate 16000Hz, 16-bit PCM.
    """
    try:
        # Đọc toàn bộ file upload vào RAM
        audio_bytes = await audio_file.read()
        
        # Đọc định dạng WAV
        try:
            wf = wave.open(BytesIO(audio_bytes), "rb")
        except wave.Error:
            raise HTTPException(status_code=400, detail="Định dạng Audio không hợp lệ. Yêu cầu file .wav")

        # Kiểm tra tiêu chuẩn âm thanh cho Vosk (Rất quan trọng)
        if wf.getnchannels() != 1 or wf.getsampwidth() != 2 or wf.getframerate() != 16000:
            raise HTTPException(
                status_code=400, 
                detail=f"Sai chuẩn Audio! Hiện tại: {wf.getnchannels()}ch, {wf.getsampwidth()}bytes, {wf.getframerate()}Hz. Yêu cầu: 1ch (Mono), 2bytes (16-bit), 16000Hz."
            )

        # Khởi tạo bộ nhận diện với Grammar ép buộc (Chỉ nghe từ 0 đến 9)
        grammar = '["không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín", "[unk]"]'
        rec = KaldiRecognizer(model, wf.getframerate(), grammar)

        # Đọc từng chunk và đưa vào Vosk để phân tích
        while True:
            data = wf.readframes(4000)
            if len(data) == 0:
                break
            rec.AcceptWaveform(data)

        # Lấy kết quả cuối cùng
        result = json.loads(rec.FinalResult())
        text_output = result.get("text", "")

        # 3. Dịch Chữ thành Số
        # Ví dụ: "một năm sáu hai" -> ["một", "năm", "sáu", "hai"] -> "1562"
        words = text_output.split()
        auth_code = "".join([WORD_TO_NUM.get(w, "") for w in words])

        print(f"🎤 [VOICE AI] Văn bản gốc: '{text_output}' -> Mã trích xuất: '{auth_code}'")

        # Trả về cho Java Spring Boot
        return {
            "text_recognized": text_output,
            "authCode": auth_code
        }

    except HTTPException as he:
        raise he
    except Exception as e:
        print(f"❌ Lỗi xử lý hệ thống Voice AI: {e}")
        raise HTTPException(status_code=500, detail="Lỗi nội bộ AI Server.")

if __name__ == "__main__":
    import uvicorn
    # Khởi chạy trên cổng 5003 như bro yêu cầu
    uvicorn.run(app, host="0.0.0.0", port=5003)