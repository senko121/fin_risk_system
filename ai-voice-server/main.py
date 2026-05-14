# import os
# import json
# import wave
# from io import BytesIO
# from fastapi import FastAPI, UploadFile, File, HTTPException
# from vosk import Model, KaldiRecognizer

# app = FastAPI()

# # 1. Kiểm tra và Nạp Model
# if not os.path.exists("model"):
#     print("🚨 LỖI: Không tìm thấy thư mục 'model'. Vui lòng kiểm tra lại!")
#     exit(1)

# print("Loading Vosk Model...")
# model = Model("model")
# print("Model Loaded Successfully!")

# # 2. Bộ từ điển dịch "Chữ" sang "Số"
# WORD_TO_NUM = {
#     "không": "0",
#     "một": "1",
#     "hai": "2",
#     "ba": "3",
#     "bốn": "4",
#     "năm": "5",
#     "sáu": "6",
#     "bảy": "7",
#     "tám": "8",
#     "chín": "9"
# }

# @app.post("/api/ai/verify-voice")
# async def verify_voice(audio_file: UploadFile = File(...)):
#     """
#     API nhận file audio (.wav), bóc băng bằng Vosk, và trả về mã số (authCode).
#     Yêu cầu File: Định dạng WAV, 1 Channel (Mono), Sample Rate 16000Hz, 16-bit PCM.
#     """
#     try:
#         # Đọc toàn bộ file upload vào RAM
#         audio_bytes = await audio_file.read()
        
#         # Đọc định dạng WAV
#         try:
#             wf = wave.open(BytesIO(audio_bytes), "rb")
#         except wave.Error:
#             raise HTTPException(status_code=400, detail="Định dạng Audio không hợp lệ. Yêu cầu file .wav")

#         # Kiểm tra tiêu chuẩn âm thanh cho Vosk (Rất quan trọng)
#         if wf.getnchannels() != 1 or wf.getsampwidth() != 2 or wf.getframerate() != 16000:
#             raise HTTPException(
#                 status_code=400, 
#                 detail=f"Sai chuẩn Audio! Hiện tại: {wf.getnchannels()}ch, {wf.getsampwidth()}bytes, {wf.getframerate()}Hz. Yêu cầu: 1ch (Mono), 2bytes (16-bit), 16000Hz."
#             )

#         # Khởi tạo bộ nhận diện với Grammar ép buộc (Chỉ nghe từ 0 đến 9)
#         grammar = '["không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín", "[unk]"]'
#         rec = KaldiRecognizer(model, wf.getframerate(), grammar)

#         # Đọc từng chunk và đưa vào Vosk để phân tích
#         while True:
#             data = wf.readframes(4000)
#             if len(data) == 0:
#                 break
#             rec.AcceptWaveform(data)

#         # Lấy kết quả cuối cùng
#         result = json.loads(rec.FinalResult())
#         text_output = result.get("text", "")

#         # 3. Dịch Chữ thành Số
#         # Ví dụ: "một năm sáu hai" -> ["một", "năm", "sáu", "hai"] -> "1562"
#         words = text_output.split()
#         auth_code = "".join([WORD_TO_NUM.get(w, "") for w in words])

#         print(f"🎤 [VOICE AI] Văn bản gốc: '{text_output}' -> Mã trích xuất: '{auth_code}'")

#         # Trả về cho Java Spring Boot
#         return {
#             "text_recognized": text_output,
#             "authCode": auth_code
#         }

#     except HTTPException as he:
#         raise he
#     except Exception as e:
#         print(f"❌ Lỗi xử lý hệ thống Voice AI: {e}")
#         raise HTTPException(status_code=500, detail="Lỗi nội bộ AI Server.")

# if __name__ == "__main__":
#     import uvicorn
#     # Khởi chạy trên cổng 5003 như bro yêu cầu
#     uvicorn.run(app, host="0.0.0.0", port=5003)


import os
import json
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from vosk import Model, KaldiRecognizer

app = FastAPI()

# ==========================================
# 1. KIỂM TRA VÀ NẠP MODEL VOSK
# ==========================================
if not os.path.exists("model"):
    print("🚨 LỖI: Không tìm thấy thư mục 'model'. Vui lòng kiểm tra lại!")
    exit(1)

print("Loading Vosk Model...")
model = Model("model")
print("Model Loaded Successfully!")

# ==========================================
# 2. BỘ TỪ ĐIỂN DỊCH CHỮ SANG SỐ
# ==========================================
WORD_TO_NUM = {
    "không": "0", "một": "1", "hai": "2", "ba": "3",
    "bốn": "4", "năm": "5", "sáu": "6", "bảy": "7",
    "tám": "8", "chín": "9"
}

def translate_to_code(text: str) -> str:
    """Dịch chuỗi văn bản (VD: 'một hai ba') thành chuỗi số ('123')"""
    words = text.split()
    return "".join([WORD_TO_NUM.get(w, "") for w in words])

# ==========================================
# 3. WEBSOCKET ENDPOINT: NHẬN DIỆN REAL-TIME
# ==========================================
@app.websocket("/ws/recognize")
async def recognize_voice(websocket: WebSocket):
    """
    API WebSocket nhận luồng Binary thô (PCM 16-bit 16000Hz).
    Không cần đóng gói WAV header, không nén Base64.
    """
    await websocket.accept()
    print("\n" + "="*50)
    print("🎤 [VOICE AI] ĐÃ KẾT NỐI! BẮT ĐẦU NGHE...")
    print("="*50)

    # Khởi tạo bộ nhận diện với Grammar ép buộc cho Session này
    grammar = '["không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín", "[unk]"]'
    rec = KaldiRecognizer(model, 16000, grammar)

    auth_code_final = ""
    last_partial = "" # Lưu lại để tránh in trùng lặp liên tục

    try:
        while True:
            # Hứng cục byte nhị phân được truyền tới liên tục
            data = await websocket.receive_bytes()

            # 🚀 CATCH EOF (End Of File): Tín hiệu kết thúc từ Frontend
            if len(data) == 0:
                print("\n🛑 [VOICE AI] Nhận tín hiệu ngắt (EOF). Đang tổng hợp kết quả...")
                break

            # Đẩy byte thô vào mồm Vosk
            if rec.AcceptWaveform(data):
                # Khi người dùng ngừng lại một chút (Dứt một cụm từ)
                result = json.loads(rec.Result())
                text_output = result.get("text", "")
                code_chunk = translate_to_code(text_output)
                auth_code_final += code_chunk
                
                if code_chunk:
                    print(f"\n✅ [CHỐT TỪNG PHẦN]: Nghe được '{text_output}' -> Ghi nhận số: {code_chunk}")
                    print(f"👉 [OTP HIỆN TẠI]: {auth_code_final}")
                
                # Trả kết quả từng phần về Java
                await websocket.send_json({
                    "type": "FINAL_CHUNK",
                    "text": text_output,
                    "code": code_chunk
                })
                last_partial = "" # Reset partial
            else:
                # Trạng thái đang nói dở (Partial - Realtime)
                partial = json.loads(rec.PartialResult())
                partial_text = partial.get("partial", "")
                
                if partial_text and partial_text != last_partial:
                    partial_code = translate_to_code(partial_text)
                    # In ra terminal xem AI đang đoán chữ gì
                    print(f"   ⏳ [Đang nghe...]: {partial_text} -> (Số: {partial_code})", end="\r", flush=True)
                    last_partial = partial_text
                
                # Trả kết quả Real-time để UI nhấp nháy
                await websocket.send_json({
                    "type": "PARTIAL",
                    "text": partial_text,
                    "code": translate_to_code(partial_text)
                })

        # 🚀 CHỐT SỔ TOÀN BỘ KHI NHẬN LỆNH EOF
        print(f"\n🛑 [VOICE AI] Nhận tín hiệu ngắt (EOF). Đang tổng hợp kết quả...")
        
        # 🚑 CỨU HỘ PHÚT CHÓT: Tránh rớt chữ đang nói dở khi bị ngắt đột ngột
        if last_partial:
            rescued_code = translate_to_code(last_partial)
            auth_code_final += rescued_code
            print(f"🚑 [CỨU HỘ PHÚT CHÓT]: Vớt được chữ đang nói dở '{last_partial}' -> {rescued_code}")

        # Vét nốt buffer cuối cùng của Vosk
        raw_final_result = rec.FinalResult()
        print(f"🔍 [DEBUG RAW JSON]: {raw_final_result}")
        
        final_res = json.loads(raw_final_result)
        final_text = final_res.get("text", "")
        final_code_chunk = translate_to_code(final_text)
        auth_code_final += final_code_chunk
        
        if final_code_chunk:
            print(f"✅ [CHỐT CUỐI CÙNG]: Nghe được '{final_text}' -> Ghi nhận số: {final_code_chunk}")

        print("\n" + "="*50)
        print(f"🎉 [VOICE AI] HOÀN TẤT! MÃ OTP TỔNG HỢP: '{auth_code_final}'")
        print("="*50 + "\n")
        
        # Gửi kết quả cuối cùng về cho Spring Boot để kiểm tra
        await websocket.send_json({
            "type": "VERIFICATION_COMPLETE",
            "authCode": auth_code_final
        })

        # Đóng kết nối
        await websocket.close()

    except WebSocketDisconnect:
        print("\n⚠️ [VOICE AI] Client (Spring Boot) ngắt kết nối đột ngột.")
    except Exception as e:
        print(f"\n❌ [VOICE AI] Lỗi hệ thống: {e}")
        try:
            await websocket.close(code=1011)
        except:
            pass

if __name__ == "__main__":
    import uvicorn
    # Khởi chạy trên cổng 5003
    uvicorn.run(app, host="0.0.0.0", port=5003)