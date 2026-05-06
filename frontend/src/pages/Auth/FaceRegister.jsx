import React, { useRef, useState, useEffect, useCallback } from 'react';
import Webcam from 'react-webcam';
import { useNavigate } from 'react-router-dom';
import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';
import { toast } from 'react-toastify'; //   IMPORT SÚNG BÁO LỖI VÀO ĐÂY

import axiosClient from '../../api/axiosClient'; 

export default function FaceRegister() {
  const webcamRef = useRef(null);
  const navigate = useNavigate();
  
  const [faceLandmarker, setFaceLandmarker] = useState(null);
  const [isModelLoaded, setIsModelLoaded] = useState(false);
  const [isFaceDetected, setIsFaceDetected] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [status, setStatus] = useState("Đang tải AI Model của Google...");

  const currentUser = JSON.parse(localStorage.getItem('currentUser'));

  // 1. KHỞI TẠO MEDIAPIPE AI
  useEffect(() => {
    const initAI = async () => {
      try {
        const filesetResolver = await FilesetResolver.forVisionTasks(
          "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3/wasm"
        );
        const landmarker = await FaceLandmarker.createFromOptions(filesetResolver, {
          baseOptions: {
            modelAssetPath: "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task",
            delegate: "GPU" // Ép dùng Card đồ họa cho mượt
          },
          outputFaceBlendshapes: true,
          runningMode: "VIDEO",
          numFaces: 1 // Chỉ quét 1 mặt
        });
        setFaceLandmarker(landmarker);
        setIsModelLoaded(true);
        setStatus("Sẵn sàng! Hãy đưa mặt vào khung hình.");
      } catch (err) {
        console.error(err);
        setStatus("Lỗi khởi tạo AI. Vui lòng tải lại trang.");
        toast.error("❌ Không thể tải AI Model của Google. Vui lòng kiểm tra kết nối mạng!");
      }
    };
    initAI();
  }, []);

  // 2. LIÊN TỤC QUÉT XEM CÓ MẶT NGƯỜI KHÔNG (LIVENESS BASIC)
  useEffect(() => {
    let animationFrameId;
    
    const detectFace = () => {
      if (webcamRef.current && webcamRef.current.video && faceLandmarker) {
        const video = webcamRef.current.video;
        if (video.currentTime > 0) {
          // AI quét khung hình hiện tại
          const results = faceLandmarker.detectForVideo(video, performance.now());
          
          // Nếu phát hiện đúng 1 khuôn mặt -> Bật nút chụp
          if (results.faceLandmarks && results.faceLandmarks.length === 1) {
            setIsFaceDetected(true);
            if (!isProcessing) setStatus("Đã nhận diện được khuôn mặt. Vui lòng bấm chụp!");
          } else {
            setIsFaceDetected(false);
            if (!isProcessing) setStatus("Không thấy khuôn mặt hoặc có quá nhiều người!");
          }
        }
      }
      animationFrameId = requestAnimationFrame(detectFace);
    };

    if (isModelLoaded) {
      detectFace();
    }

    return () => cancelAnimationFrame(animationFrameId);
  }, [isModelLoaded, faceLandmarker, isProcessing]);

  // 3. HÀM CHỤP VÀ GỬI LÊN SPRING BOOT
  const captureAndRegister = useCallback(async () => {
    if (!webcamRef.current) return;
    setIsProcessing(true);
    setStatus("Đang lưu trữ dữ liệu sinh trắc học...");

    // Chụp ảnh định dạng Base64
    const base64Image = webcamRef.current.getScreenshot();

    try {
      await axiosClient.post('/users/register-face', {
        userId: currentUser.id || currentUser.userId,
        base64FaceImage: base64Image
      });

      // 🚀 ĐIỂM NÂNG CẤP TỐI THƯỢNG Ở ĐÂY:
      // Cập nhật lại cờ nhận thức trong LocalStorage ngay lập tức
      const updatedUser = { ...currentUser, isFaceSetup: true };
      localStorage.setItem('currentUser', JSON.stringify(updatedUser));

      toast.success("🎉 Đăng ký Sinh trắc học thành công!");
      setTimeout(() => {
        navigate('/dashboard'); // Hoặc navigate lùi lại trang giao dịch tùy bro
      }, 1000); 
      
    } catch (error) {
      setStatus("❌ Lỗi lưu DB. Vui lòng thử lại!");
      toast.error("Lỗi: " + (error.response?.data || error.message)); 
    } finally {
      setIsProcessing(false);
    }
  }, [webcamRef, currentUser, navigate]);
  
  useEffect(() => {
    if (!currentUser) {
      toast.error("Vui lòng đăng nhập trước khi sử dụng tính năng này!");
      navigate('/login');
    }
  }, [currentUser, navigate]);

  // Nếu chưa đăng nhập thì trả về null để không vẽ UI, nhường quyền cho useEffect đá văng ra ngoài
  if (!currentUser) {
    return null;
  }

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-4">
      <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl max-w-md w-full text-center relative overflow-hidden">
        <h2 className="text-2xl font-black text-gray-800 mb-2">Cài đặt FaceID</h2>
        <p className={`text-sm font-bold mb-6 transition-colors ${isFaceDetected ? 'text-green-500' : 'text-orange-500'}`}>
          {status}
        </p>

        <div className={`relative w-64 h-64 mx-auto rounded-full overflow-hidden border-4 mb-8 shadow-lg transition-colors duration-300 ${isFaceDetected ? 'border-green-500' : 'border-slate-300'}`}>
          {isModelLoaded ? (
            <Webcam
              audio={false}
              ref={webcamRef}
              screenshotFormat="image/jpeg"
              videoConstraints={{ facingMode: "user" }}
              className="w-full h-full object-cover transform scale-x-[-1]" // Lật ngược cam lại cho giống gương
            />
          ) : (
            <div className="w-full h-full bg-slate-800 animate-pulse flex items-center justify-center">
              <span className="text-white text-xs tracking-widest animate-bounce">LOADING AI...</span>
            </div>
          )}
          
          {/* Hiệu ứng quét khi đang xử lý lưu */}
          {isProcessing && <div className="absolute inset-0 bg-blue-500/30 animate-scan"></div>}
        </div>

        <button 
          onClick={captureAndRegister} 
          disabled={!isModelLoaded || !isFaceDetected || isProcessing}
          className={`w-full py-4 rounded-2xl font-bold text-white transition-all shadow-lg ${(!isModelLoaded || !isFaceDetected || isProcessing) ? 'bg-gray-300 cursor-not-allowed shadow-none' : 'bg-blue-600 hover:bg-blue-700 active:scale-95 shadow-blue-500/50'}`}
        >
          {isProcessing ? 'ĐANG LƯU DỮ LIỆU...' : 'XÁC NHẬN CHỤP'}
        </button>
      </div>
    </div>
  );
}