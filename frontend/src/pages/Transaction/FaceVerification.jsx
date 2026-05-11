 

// import React, { useRef, useState, useEffect, useCallback } from 'react';
// import Webcam from 'react-webcam';
// import { useLocation, useNavigate } from 'react-router-dom';
// import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';
// import { toast } from 'react-toastify'; 

// import axiosClient from '../../api/axiosClient'; 

// export default function FaceVerification() {
//   const webcamRef = useRef(null);
//   const navigate = useNavigate();
//   const { state } = useLocation();
  
//   const { transactionId, formData, recipientName } = state || {};

//   const [faceLandmarker, setFaceLandmarker] = useState(null);
//   const [isModelLoaded, setIsModelLoaded] = useState(false);
//   const [isFaceDetected, setIsFaceDetected] = useState(false);
//   const [isProcessing, setIsProcessing] = useState(false);
//   const [status, setStatus] = useState("Đang tải AI Model...");

//   useEffect(() => {
//     if (!transactionId) {
//       toast.error("🚨 Lỗi: Không tìm thấy thông tin giao dịch!");
//       navigate('/dashboard');
//       return;
//     }

//     const initAI = async () => {
//       try {
//         const filesetResolver = await FilesetResolver.forVisionTasks(
//           "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3/wasm"
//         );
//         const landmarker = await FaceLandmarker.createFromOptions(filesetResolver, {
//           baseOptions: {
//             modelAssetPath: "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task",
//             delegate: "GPU"
//           },
//           runningMode: "VIDEO",
//           numFaces: 1
//         });
//         setFaceLandmarker(landmarker);
//         setIsModelLoaded(true);
//         setStatus("Hãy nhìn thẳng vào camera để xác thực!");
//       } catch (err) {
//         setStatus("Lỗi khởi tạo AI.");
//         toast.error("❌ Không thể tải AI Model. Vui lòng kiểm tra lại mạng!");
//       }
//     };
//     initAI();
//   }, [transactionId, navigate]); // 🚀 Đã xóa phụ thuộc hasFaceSetup

//   useEffect(() => {
//     let animationFrameId;
//     const detectFace = () => {
//       if (webcamRef.current && webcamRef.current.video && faceLandmarker) {
//         const video = webcamRef.current.video;
//         if (video.currentTime > 0) {
//           const results = faceLandmarker.detectForVideo(video, performance.now());
//           if (results.faceLandmarks && results.faceLandmarks.length === 1) {
//             setIsFaceDetected(true);
//             if (!isProcessing) setStatus("Đã nhận diện khuôn mặt. Sẵn sàng quét!");
//           } else {
//             setIsFaceDetected(false);
//             if (!isProcessing) setStatus("Vui lòng giữ khuôn mặt trong khung hình.");
//           }
//         }
//       }
//       animationFrameId = requestAnimationFrame(detectFace);
//     };
//     if (isModelLoaded) detectFace();
//     return () => cancelAnimationFrame(animationFrameId);
//   }, [isModelLoaded, faceLandmarker, isProcessing]);

//   const captureAndVerify = useCallback(async () => {
//     if (!webcamRef.current) return;
//     setIsProcessing(true);
//     setStatus("Hệ thống đang đối chiếu sinh trắc học...");

//     const base64Image = webcamRef.current.getScreenshot();

//     try {
//       const response = await axiosClient.post('/transactions/verify', {
//         transactionId: transactionId,
//         authType: "FACE_STATIC",
//         faceImageBase64: base64Image
//       });

//       const resData = response.data;

//       if (resData.status === "SUCCESS") {
//         toast.success("🎉 Xác thực khuôn mặt thành công! Giao dịch đã được duyệt.");
//         navigate('/transaction-result', { 
//           state: { 
//             result: resData.data, 
//             formData: formData, 
//             recipientName: recipientName 
//           } 
//         });
//       }

//     } catch (error) {
//       const errorMsg = error.response?.data || "Xác thực khuôn mặt thất bại!";
//       setStatus("❌ TỪ CHỐI GIAO DỊCH: " + errorMsg);
//       toast.error("🚨 Lỗi: " + errorMsg); 
//       setIsProcessing(false);
//     }
//   }, [webcamRef, transactionId, formData, recipientName, navigate]);

//   return (
//     <div className="min-h-screen bg-red-900/95 flex flex-col items-center justify-center p-4 relative overflow-hidden">
//       <div className="absolute top-0 left-0 w-full h-2 bg-red-500 animate-pulse"></div>

//       <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl max-w-md w-full text-center relative z-10 border-4 border-red-500/20">
//         <div className="mx-auto bg-red-100 text-red-600 w-16 h-16 rounded-full flex items-center justify-center mb-4">
//           <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
//         </div>
//         <h2 className="text-2xl font-black text-gray-900 mb-1 uppercase tracking-tight">Rủi Ro Cao (HIGH RISK)</h2>
//         <p className="text-xs font-bold text-gray-400 mb-6 uppercase tracking-widest">Yêu cầu xác thực sinh trắc học</p>
        
//         <p className={`text-sm font-bold mb-6 transition-colors ${isFaceDetected ? 'text-green-600' : 'text-red-500'}`}>
//           {status}
//         </p>

//         <div className={`relative w-64 h-64 mx-auto rounded-xl overflow-hidden border-4 mb-8 shadow-2xl transition-all duration-300 ${isFaceDetected ? 'border-green-500 scale-105' : 'border-red-400 scale-100'}`}>
//           {isModelLoaded ? (
//             <Webcam
//               audio={false}
//               ref={webcamRef}
//               screenshotFormat="image/jpeg"
//               videoConstraints={{ facingMode: "user" }}
//               className="w-full h-full object-cover transform scale-x-[-1]"
//             />
//           ) : (
//             <div className="w-full h-full bg-slate-900 flex items-center justify-center">
//               <span className="text-white text-xs tracking-widest animate-pulse">AI SCANNER INIT...</span>
//             </div>
//           )}
//           {isProcessing && <div className="absolute inset-0 bg-blue-500/40 animate-scan"></div>}
          
//           <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
//             <div className={`w-32 h-40 border-2 rounded-full transition-colors ${isFaceDetected ? 'border-green-400/50' : 'border-red-400/50 border-dashed'}`}></div>
//           </div>
//         </div>

//         <button 
//           onClick={captureAndVerify} 
//           disabled={!isModelLoaded || !isFaceDetected || isProcessing}
//           className={`w-full py-5 rounded-2xl font-black text-white text-lg transition-all shadow-xl ${(!isModelLoaded || !isFaceDetected || isProcessing) ? 'bg-gray-300 cursor-not-allowed shadow-none text-gray-500' : 'bg-red-600 hover:bg-red-700 active:scale-95 hover:shadow-red-500/50'}`}
//         >
//           {isProcessing ? 'ĐANG CHỐT SỔ GIAO DỊCH...' : 'QUÉT VÀ XÁC NHẬN CHUYỂN TIỀN'}
//         </button>
//       </div>
//     </div>
//   );
// }







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
  const [isFaceDetected, setIsFaceDetected] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [status, setStatus] = useState("Đang tải AI Model...");

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
  }, [transactionId, navigate]); // 🚀 Đã xóa phụ thuộc hasFaceSetup

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

  const captureAndVerify = useCallback(async () => {
    if (!webcamRef.current) return;
    setIsProcessing(true);
    setStatus("Hệ thống đang đối chiếu sinh trắc học...");

    const base64Image = webcamRef.current.getScreenshot();

    try {
      const response = await axiosClient.post('/transactions/verify', {
        transactionId: transactionId,
        authType: "FACE_STATIC",
        faceImageBase64: base64Image
      });

      const resData = response.data;

      if (resData.status === "SUCCESS") {
        toast.success("🎉 Xác thực khuôn mặt thành công! Giao dịch đã được duyệt.");
        navigate('/transaction-result', { 
          state: { 
            result: resData.data, 
            formData: formData, 
            recipientName: recipientName 
          } 
        });
      }

    } catch (error) {
      const errorMsg = error.response?.data || "Xác thực khuôn mặt thất bại!";
      setStatus("TỪ CHỐI GIAO DỊCH: " + errorMsg);
      toast.error("🚨 Lỗi: " + errorMsg); 
      setIsProcessing(false);
    }
  }, [webcamRef, transactionId, formData, recipientName, navigate]);

  return (
    <div className="min-h-screen bg-[#f4f6f9] flex flex-col font-sans">
      
      {/* ── Topbar: navy shell ── */}
      <nav className="bg-[#1e2d40] px-6 py-4 flex items-center justify-between shadow-sm relative overflow-hidden">
        <div className="absolute top-0 left-0 w-full h-1 bg-[#b91c1c] animate-pulse"></div>
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-white/10 border border-white/15 rounded-lg flex items-center justify-center text-white font-bold text-sm">
            F
          </div>
          <div>
            <p className="text-white font-semibold text-sm">Hệ thống an ninh</p>
            <p className="text-white/40 text-[10px] uppercase tracking-widest">Kiểm soát rủi ro</p>
          </div>
        </div>
      </nav>

      {/* ── Vùng nội dung trung tâm ── */}
      <div className="flex-1 flex items-center justify-center p-4">
        <div className="w-full max-w-md bg-white p-8 rounded-2xl shadow-sm border border-gray-100 text-center relative">
          
          {/* Cảnh báo High Risk */}
          <div className="w-12 h-12 bg-red-50 border border-red-100 rounded-xl flex items-center justify-center mx-auto mb-4 text-[#b91c1c]">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
            </svg>
          </div>
          
          <h2 className="text-sm font-bold text-[#b91c1c] mb-1.5 uppercase tracking-wide">Rủi ro cao (High Risk)</h2>
          <p className="text-[10px] font-semibold text-gray-400 mb-6 uppercase tracking-widest">Yêu cầu xác thực sinh trắc học</p>
          
          <p className={`text-[11px] font-bold uppercase tracking-widest mb-6 transition-colors 
            ${isFaceDetected ? 'text-[#15803d]' : 'text-[#b91c1c]'}`}>
            {status}
          </p>

          {/* ── Khung Camera ── */}
          <div className={`relative w-64 h-64 mx-auto rounded-xl overflow-hidden border-2 mb-8 bg-[#1e2d40] transition-colors duration-300 
            ${isFaceDetected ? 'border-[#15803d]' : 'border-[#b91c1c]'}`}>
            
            {isModelLoaded ? (
              <Webcam
                audio={false}
                ref={webcamRef}
                screenshotFormat="image/jpeg"
                videoConstraints={{ facingMode: "user" }}
                className={`w-full h-full object-cover transform scale-x-[-1] transition-all duration-300 ${isFaceDetected ? 'opacity-100' : 'opacity-70 grayscale-[30%]'}`}
              />
            ) : (
              <div className="w-full h-full flex items-center justify-center">
                <span className="text-white/50 text-[10px] uppercase tracking-widest animate-pulse font-semibold">AI Scanner Init...</span>
              </div>
            )}

            {/* Hiệu ứng quét khi đang xử lý API */}
            {isProcessing && <div className="absolute inset-0 bg-[#2563eb]/20 animate-pulse"></div>}
            
            {/* Khung định hướng khuôn mặt (Oval) */}
            <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
              <div className={`w-32 h-44 border-2 rounded-[50%] transition-colors duration-300 
                ${isFaceDetected ? 'border-[#15803d]/70' : 'border-[#b91c1c]/50 border-dashed'}`}>
              </div>
            </div>
          </div>

          {/* ── Nút Xác Nhận ── */}
          <button 
            onClick={captureAndVerify} 
            disabled={!isModelLoaded || !isFaceDetected || isProcessing}
            className={`w-full py-3.5 rounded-xl text-sm font-semibold tracking-wide transition-all flex items-center justify-center gap-2
              ${(!isModelLoaded || !isFaceDetected || isProcessing) 
                ? 'bg-[#1e2d40]/40 text-white cursor-not-allowed' 
                : 'bg-[#1e2d40] text-white hover:bg-[#162233] active:scale-[0.98]'}`}
          >
            {isProcessing ? (
              <>
                <svg className="w-4 h-4 animate-spin text-white" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4l3-3-3-3v4a8 8 0 00-8 8h4z"></path>
                </svg>
                ĐANG ĐỐI CHIẾU AI...
              </>
            ) : 'QUÉT VÀ XÁC NHẬN'}
          </button>
          
        </div>
      </div>
    </div>
  );
}