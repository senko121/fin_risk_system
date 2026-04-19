import React, { useRef, useState, useEffect, useCallback } from 'react';
import Webcam from 'react-webcam';
import { useLocation, useNavigate } from 'react-router-dom';
import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';
import { toast } from 'react-toastify'; // 🚀 IMPORT TOAST VÀO ĐÂY

import axiosClient from '../../api/axiosClient';

export default function FaceVerification() {
  const webcamRef = useRef(null);
  const navigate = useNavigate();
  const { state } = useLocation();
  
  // Nhận dữ liệu giao dịch từ trang Step 1 truyền sang
  const { transactionId, formData, recipientName, result } = state || {};

  const [faceLandmarker, setFaceLandmarker] = useState(null);
  const [isModelLoaded, setIsModelLoaded] = useState(false);
  const [isFaceDetected, setIsFaceDetected] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [status, setStatus] = useState("Đang tải AI Model...");

  // 1. KHỞI TẠO MEDIAPIPE AI
  useEffect(() => {
    if (!transactionId) {
      toast.error("🚨 Lỗi: Không tìm thấy thông tin giao dịch!");
      navigate('/dashboard');
      return;
    }

    const initAI = async () => {
      try {
        const filesetResolver = await FilesetResolver.forVisionTasks(
          "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3/wasm"
        );
        const landmarker = await FaceLandmarker.createFromOptions(filesetResolver, {
          baseOptions: {
            modelAssetPath: "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task",
            delegate: "GPU"
          },
          runningMode: "VIDEO",
          numFaces: 1
        });
        setFaceLandmarker(landmarker);
        setIsModelLoaded(true);
        setStatus("Hãy nhìn thẳng vào camera để xác thực!");
      } catch (err) {
        setStatus("Lỗi khởi tạo AI.");
        toast.error("❌ Không thể tải AI Model. Vui lòng kiểm tra lại mạng!");
      }
    };
    initAI();
  }, [transactionId, navigate]);

  // 2. LIÊN TỤC QUÉT XEM CÓ MẶT NGƯỜI KHÔNG
  useEffect(() => {
    let animationFrameId;
    const detectFace = () => {
      if (webcamRef.current && webcamRef.current.video && faceLandmarker) {
        const video = webcamRef.current.video;
        if (video.currentTime > 0) {
          const results = faceLandmarker.detectForVideo(video, performance.now());
          if (results.faceLandmarks && results.faceLandmarks.length === 1) {
            setIsFaceDetected(true);
            if (!isProcessing) setStatus("Đã nhận diện khuôn mặt. Sẵn sàng quét!");
          } else {
            setIsFaceDetected(false);
            if (!isProcessing) setStatus("Vui lòng giữ khuôn mặt trong khung hình.");
          }
        }
      }
      animationFrameId = requestAnimationFrame(detectFace);
    };
    if (isModelLoaded) detectFace();
    return () => cancelAnimationFrame(animationFrameId);
  }, [isModelLoaded, faceLandmarker, isProcessing]);

  // 3. HÀM CHỤP ẢNH VÀ GỬI LÊN BACKEND XÁC THỰC
  const captureAndVerify = useCallback(async () => {
    if (!webcamRef.current) return;
    setIsProcessing(true);
    setStatus("Hệ thống đang đối chiếu sinh trắc học...");

    const base64Image = webcamRef.current.getScreenshot();

    try {
      const response = await axiosClient.post('/transactions/verify', {
        transactionId: transactionId,
        authType: "FACE",
        faceImageBase64: base64Image
      });

      const txData = response.data;

      // NÂNG CẤP ĐA LỚP: Nếu Backend bảo chờ OTP -> Đá sang trang OTP
      if (txData.status === "PENDING_OTP") {
        toast.success("✅ Sinh trắc học hợp lệ. Chuyển sang bước OTP!");
        navigate('/verify-otp', { 
          state: { 
            transactionId: transactionId,
            formData: formData, 
            recipientName: recipientName 
          } 
        });
      } else {
        // Dự phòng: Nếu giao dịch được thông qua luôn (SUCCESS)
        toast.success("🎉 Xác thực thành công! Giao dịch đã được duyệt.");
        navigate('/transaction-result', { 
          state: { 
            result: txData, 
            formData: formData, 
            recipientName: recipientName 
          } 
        });
      }

    } catch (error) {
      const errorMsg = error.response?.data || "Xác thực khuôn mặt thất bại!";
      setStatus("❌ TỪ CHỐI GIAO DỊCH: " + errorMsg);
      toast.error("🚨 Lỗi: " + errorMsg); // 🚀 BẮT LỖI BẰNG TOAST
      setIsProcessing(false);
    }
  }, [webcamRef, transactionId, formData, recipientName, navigate]);

  return (
    <div className="min-h-screen bg-red-900/95 flex flex-col items-center justify-center p-4 relative overflow-hidden">
      {/* Hiệu ứng cảnh báo rủi ro */}
      <div className="absolute top-0 left-0 w-full h-2 bg-red-500 animate-pulse"></div>

      <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl max-w-md w-full text-center relative z-10 border-4 border-red-500/20">
        <div className="mx-auto bg-red-100 text-red-600 w-16 h-16 rounded-full flex items-center justify-center mb-4">
          <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
        </div>
        <h2 className="text-2xl font-black text-gray-900 mb-1 uppercase tracking-tight">Rủi Ro Cao (HIGH RISK)</h2>
        <p className="text-xs font-bold text-gray-400 mb-6 uppercase tracking-widest">Yêu cầu xác thực sinh trắc học</p>
        
        <p className={`text-sm font-bold mb-6 transition-colors ${isFaceDetected ? 'text-green-600' : 'text-red-500'}`}>
          {status}
        </p>

        <div className={`relative w-64 h-64 mx-auto rounded-xl overflow-hidden border-4 mb-8 shadow-2xl transition-all duration-300 ${isFaceDetected ? 'border-green-500 scale-105' : 'border-red-400 scale-100'}`}>
          {isModelLoaded ? (
            <Webcam
              audio={false}
              ref={webcamRef}
              screenshotFormat="image/jpeg"
              videoConstraints={{ facingMode: "user" }}
              className="w-full h-full object-cover transform scale-x-[-1]" // 🚀 LẬT NGƯỢC CAMERA (MIRROR)
            />
          ) : (
            <div className="w-full h-full bg-slate-900 flex items-center justify-center">
              <span className="text-white text-xs tracking-widest animate-pulse">AI SCANNER INIT...</span>
            </div>
          )}
          {isProcessing && <div className="absolute inset-0 bg-blue-500/40 animate-scan"></div>}
          
          {/* Lưới định vị khuôn mặt (Trang trí cho ngầu) */}
          <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
            <div className={`w-32 h-40 border-2 rounded-full transition-colors ${isFaceDetected ? 'border-green-400/50' : 'border-red-400/50 border-dashed'}`}></div>
          </div>
        </div>

        <button 
          onClick={captureAndVerify} 
          disabled={!isModelLoaded || !isFaceDetected || isProcessing}
          className={`w-full py-5 rounded-2xl font-black text-white text-lg transition-all shadow-xl ${(!isModelLoaded || !isFaceDetected || isProcessing) ? 'bg-gray-300 cursor-not-allowed shadow-none text-gray-500' : 'bg-red-600 hover:bg-red-700 active:scale-95 hover:shadow-red-500/50'}`}
        >
          {isProcessing ? 'ĐANG CHỐT SỔ GIAO DỊCH...' : 'QUÉT VÀ XÁC NHẬN CHUYỂN TIỀN'}
        </button>
      </div>
    </div>
  );
}