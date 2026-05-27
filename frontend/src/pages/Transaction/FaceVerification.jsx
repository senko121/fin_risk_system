 

import React, { useRef, useState, useEffect, useCallback } from 'react';
import Webcam from 'react-webcam';
import { useLocation, useNavigate } from 'react-router-dom';
import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';
import { toast } from 'react-toastify'; 

import axiosClient from '../../api/axiosClient'; 

export default function FaceVerification() {
  const webcamRef = useRef(null);
  const navigate = useNavigate();
  const { state } = useLocation();
  
  const { transactionId, formData, recipientName } = state || {};

  const [faceLandmarker, setFaceLandmarker] = useState(null);
  const [isModelLoaded, setIsModelLoaded] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [status, setStatus] = useState("Đang tải AI Model...");

  // 🚀 THÊM STATE VÀ REF CHO LIVENESS
  const [isLivenessPassed, setIsLivenessPassed] = useState(false);
  const [isFaceDetected, setIsFaceDetected] = useState(false);
  const [isFrozen, setIsFrozen] = useState(false);
  const globalBlinkFlag = useRef(false);
  const framesBuffer = useRef([]);
  const MAX_FRAMES = 30; // Bộ đệm 0.5 giây

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
          numFaces: 1,
          // 🚀 BẮT BUỘC PHẢI THÊM 2 DÒNG NÀY ĐỂ ĐỌC ĐƯỢC CHỚP MẮT VÀ 3D
          outputFacialTransformationMatrixes: true, 
          outputFaceBlendshapes: true 
        });
        setFaceLandmarker(landmarker);
        setIsModelLoaded(true);
        setStatus("👀 Hãy nhìn thẳng camera, chớp mắt VÀ lắc nhẹ đầu...");
      } catch (err) {
        setStatus("Lỗi khởi tạo AI.");
        toast.error("❌ Không thể tải AI Model. Vui lòng kiểm tra lại mạng!");
      }
    };
    initAI();
  }, [transactionId, navigate]); 

  // 🚀 VÒNG LẶP LIVENESS (Thay thế cho hàm detectFace cũ)
  useEffect(() => {
    let animationFrameId;

    const analyzeLiveness = () => {
      if (!isLivenessPassed && webcamRef.current && webcamRef.current.video && faceLandmarker) {
        const video = webcamRef.current.video;
        if (video.currentTime > 0) {
          const results = faceLandmarker.detectForVideo(video, performance.now());

          if (results.faceLandmarks && results.faceLandmarks.length === 1) {
            setIsFaceDetected(true);
            const landmarks = results.faceLandmarks[0];
            const nose = landmarks[1];
            const leftCheek = landmarks[234];
            const rightCheek = landmarks[454];
            const top = landmarks[10];
            const bottom = landmarks[152];

            const yaw = Math.atan2(rightCheek.z - leftCheek.z, rightCheek.x - leftCheek.x);
            const pitch = Math.atan2(bottom.z - top.z, bottom.y - top.y);

            const distLeft = Math.sqrt(Math.pow(leftCheek.x - nose.x, 2) + Math.pow(leftCheek.y - nose.y, 2));
            const distRight = Math.sqrt(Math.pow(rightCheek.x - nose.x, 2) + Math.pow(rightCheek.y - nose.y, 2));
            const totalDist = distLeft + distRight;
            const ratio = totalDist > 0 ? Math.abs(distLeft - distRight) / totalDist : 0;

            let currentBlinkScore = 0;
            if (results.faceBlendshapes && results.faceBlendshapes.length > 0) {
              const blendshapes = results.faceBlendshapes[0].categories;
              const eyeBlinkLeft = blendshapes.find(shape => shape.categoryName === "eyeBlinkLeft")?.score || 0;
              const eyeBlinkRight = blendshapes.find(shape => shape.categoryName === "eyeBlinkRight")?.score || 0;
              currentBlinkScore = Math.max(eyeBlinkLeft, eyeBlinkRight);
              
              if (currentBlinkScore > 0.45) { 
                globalBlinkFlag.current = true;
              }
            }

            framesBuffer.current.push({ yaw, pitch, ratio, blinkScore: currentBlinkScore });
            
            if (framesBuffer.current.length > MAX_FRAMES) {
              framesBuffer.current.shift(); 
            }

            if (framesBuffer.current.length === MAX_FRAMES && !isProcessing) {
              const yaws = framesBuffer.current.map(f => f.yaw);
              const pitches = framesBuffer.current.map(f => f.pitch);
              const ratios = framesBuffer.current.map(f => f.ratio);

              const yawDelta = Math.abs(Math.max(...yaws) - Math.min(...yaws));
              const pitchDelta = Math.abs(Math.max(...pitches) - Math.min(...pitches));
              const ratioDelta = Math.abs(Math.max(...ratios) - Math.min(...ratios));

              const THRESHOLD_RATIO = 0.065; 
              const THRESHOLD_SHAKE = 0.05; 

              const hasBlinked = globalBlinkFlag.current;
              const is3D = ratioDelta >= THRESHOLD_RATIO; 
              const isShaking = yawDelta > THRESHOLD_SHAKE || pitchDelta > THRESHOLD_SHAKE;

              if (hasBlinked && is3D) {
                setIsLivenessPassed(true);
                setStatus("✅ NGƯỜI THẬT: Sẵn sàng quét khuôn mặt!");
              } else if (isShaking && !is3D) {
                setStatus("🚨 PHÁT HIỆN ẢNH GIẢ! Vui lòng dùng khuôn mặt thật.");
              } else if (hasBlinked && !is3D && !isShaking) {
                setStatus("⏳ Đã nhận diện chớp mắt. Vui lòng lắc nhẹ đầu...");
              } else if (!hasBlinked && is3D) {
                setStatus("⏳ Đang lắc đầu 3D... Vui lòng chớp mắt 1 cái!");
              } else {
                setStatus("👀 Hãy nhìn thẳng camera, chớp mắt VÀ lắc nhẹ đầu...");
              }
            }
          } else {
            framesBuffer.current = [];
            globalBlinkFlag.current = false;
            setIsLivenessPassed(false);
            setIsFaceDetected(false);
            if (!isProcessing) setStatus("Vui lòng đưa khuôn mặt vào giữa khung hình.");
          }
        }
      }
      animationFrameId = requestAnimationFrame(analyzeLiveness);
    };

    if (isModelLoaded) analyzeLiveness();
    return () => cancelAnimationFrame(animationFrameId);
  }, [isModelLoaded, faceLandmarker, isProcessing, isLivenessPassed]);

const captureAndVerify = useCallback(async () => {
  if (!webcamRef.current) return;
  setIsProcessing(true);

  // Flip giống enroll để embedding khớp nhau
  const video = webcamRef.current.video;
  const canvas = document.createElement('canvas');
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  const ctx = canvas.getContext('2d');
  ctx.translate(canvas.width, 0);
  ctx.scale(-1, 1);
  ctx.drawImage(video, 0, 0);
  const base64Image = canvas.toDataURL('image/jpeg', 0.95).split(',')[1];

  try {
    const response = await axiosClient.post('/transactions/verify', {
      transactionId: transactionId,
      authType: "FACE_STATIC",
      faceImageBase64: base64Image   // ← clean, không có prefix
    });

      const resData = response.data;
      if (resData.status === "SUCCESS") {
        toast.success("🎉 Xác thực khuôn mặt thành công!");
        navigate('/transaction-result', { 
          state: { result: resData.data, formData, recipientName } 
        });
      }
    } catch (error) {
      const resData = error.response?.data;
      const resStatus = resData?.status;
      const errorMsg = resData?.message
          || (typeof resData === 'string' ? resData : null)
          || "Xác thực khuôn mặt thất bại!";

      if (resStatus === 'BLOCKED') {
          setIsFrozen(true);
          setIsProcessing(false);
          setStatus("🚫 " + errorMsg);
          toast.error("🚫 " + errorMsg);
          setTimeout(() => navigate('/dashboard'), 3000);
      } else {
          setIsProcessing(false);
          setIsLivenessPassed(false);
          globalBlinkFlag.current = false;
          framesBuffer.current = [];
          setStatus("❌ TỪ CHỐI: " + errorMsg);
          toast.error("🚨 Lỗi: " + errorMsg);
      }
    }
  }, [webcamRef, transactionId, formData, recipientName, navigate]);

return (
  <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-4 relative overflow-hidden">
    
    {/* TOP STATUS BAR */}
    <div
      className={`absolute top-0 left-0 w-full h-2 animate-pulse ${
        isLivenessPassed ? "bg-green-500" : "bg-orange-500"
      }`}
    />

 

    <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl max-w-md w-full text-center relative z-10 border-4 border-slate-800">
      
      {/* HEADER */}
      <div className="mb-8">
        <h2 className="text-2xl font-black text-gray-900 mb-1 uppercase tracking-tight">
          Xác thực khuôn mặt
        </h2>

        <p className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.3em]">
          AI Liveness Detection
        </p>
      </div>

      {/* STATUS */}
      <p
        className={`text-sm font-bold mb-6 transition-all duration-300 ${
          isProcessing
            ? "text-blue-500 animate-pulse"
            : isLivenessPassed
            ? "text-green-600"
            : "text-slate-500"
        }`}
      >
        {status}
      </p>

      {/* CAMERA */}
      <div
        className={`relative w-72 h-72 mx-auto rounded-2xl overflow-hidden border-4 mb-8 shadow-2xl transition-all duration-500 ${
          isLivenessPassed
            ? "border-green-500 scale-[1.02]"
            : "border-slate-300"
        }`}
      >
        {isModelLoaded ? (
          <Webcam
            audio={false}
            ref={webcamRef}
            screenshotFormat="image/jpeg"
            videoConstraints={{ facingMode: "user" }}
            className="w-full h-full object-cover transform scale-x-[-1]"
          />
        ) : (
          <div className="w-full h-full bg-slate-900 flex flex-col items-center justify-center">
            <div className="w-8 h-8 border-2 border-white/20 border-t-white rounded-full animate-spin mb-3"></div>

            <span className="text-white text-[10px] tracking-[0.3em] font-black animate-pulse">
              LOADING AI...
            </span>
          </div>
        )}

        {/* SCAN EFFECT */}
        {isProcessing && (
          <div className="absolute inset-0 bg-gradient-to-b from-transparent via-blue-500/20 to-transparent animate-scan border-t border-blue-400"></div>
        )}

        {/* FACE GUIDE */}
        {!isLivenessPassed && isModelLoaded && (
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <div className="w-40 h-52 border-2 border-dashed border-slate-400/60 rounded-full animate-pulse"></div>
          </div>
        )}

        {/* SUCCESS ICON */}
        {isLivenessPassed && (
          <div className="absolute top-3 right-3 bg-green-500 text-white p-2 rounded-full shadow-lg animate-bounce">
            <svg
              className="w-4 h-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="3"
                d="M5 13l4 4L19 7"
              />
            </svg>
          </div>
        )}
      </div>

      {/* BUTTON */}
      <button
        onClick={captureAndVerify}
        disabled={!isModelLoaded || !isLivenessPassed || isProcessing || isFrozen}
        className={`w-full py-5 rounded-2xl font-black text-white text-lg transition-all shadow-xl ${
          !isModelLoaded || !isLivenessPassed || isProcessing
            ? "bg-slate-300 cursor-not-allowed shadow-none text-slate-500"
            : "bg-indigo-600 hover:bg-indigo-700 active:scale-95 hover:shadow-indigo-500/30"
        }`}
      >
        {isProcessing ? (
          <span className="flex items-center justify-center gap-3">
            <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
            ĐANG XÁC THỰC...
          </span>
        ) : (
          "XÁC NHẬN GIAO DỊCH"
        )}
      </button>

      {/* FOOTER */}
      <div className="mt-8 flex items-center justify-center gap-2 opacity-40">
        <svg
          className="w-3 h-3 text-slate-500"
          fill="currentColor"
          viewBox="0 0 24 24"
        >
          <path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z" />
        </svg>

        <span className="text-[9px] font-black uppercase tracking-[0.3em] text-slate-500">
          End-to-End Encryption
        </span>
      </div>
    </div>

    <style>{`
      @keyframes scan {
        0% {
          transform: translateY(-100%);
        }
        100% {
          transform: translateY(100%);
        }
      }

      .animate-scan {
        animation: scan 2s linear infinite;
      }
    `}</style>
  </div>
);
}