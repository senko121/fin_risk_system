

// import React, { useRef, useState, useEffect, useCallback } from 'react';
// import Webcam from 'react-webcam';
// import { useLocation, useNavigate } from 'react-router-dom';
// import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';
// import { toast } from 'react-toastify'; 
// import axiosClient from '../../api/axiosClient';

// export default function HighRiskVerification() {
//   const webcamRef = useRef(null);
//   const navigate = useNavigate();
//   const { state } = useLocation();
//   const { transactionId, formData, recipientName } = state || {};

//   // Điều hướng nội bộ trong Gateway
//   const [step, setStep] = useState(1); // 1 = Face AI, 2 = Voice OTP
//   const [voiceCode, setVoiceCode] = useState('');

//   // State chung
//   const [isProcessing, setIsProcessing] = useState(false);
//   const [status, setStatus] = useState("Đang tải AI Model...");

//   // State Face AI
//   const [faceLandmarker, setFaceLandmarker] = useState(null);
//   const [isModelLoaded, setIsModelLoaded] = useState(false);
//   const [isFaceDetected, setIsFaceDetected] = useState(false);
//   const [isRecording, setIsRecording] = useState(false);

//   // 🚀 BƯỚC 1: LÔI USER TỪ KÉT SẮT RA KIỂM TRA
//   const currentUser = JSON.parse(localStorage.getItem('currentUser')) || {};
//   const hasFaceSetup = currentUser.isFaceSetup === true;

//   // ==========================================
//   // KHỞI TẠO CAMERA & MEDIAPIPE (Chạy 1 lần)
//   // ==========================================
//   useEffect(() => {
//     if (!transactionId) {
//       toast.error("🚨 Lỗi: Không tìm thấy thông tin giao dịch!");
//       navigate('/dashboard');
//       return;
//     }

//     // 🚀 CHẶN AI NGAY LẬP TỨC NẾU CHƯA CÓ KHUÔN MẶT ĐỂ TIẾT KIỆM RAM
//     if (!hasFaceSetup) return;

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
//         toast.error("❌ Không thể tải AI Model.");
//       }
//     };
//     initAI();
//   }, [transactionId, navigate, hasFaceSetup]);

//   // Liên tục check Face (Chỉ dùng ở Step 1)
//   useEffect(() => {
//     let animationFrameId;
//     const detectFace = () => {
//       if (step === 1 && webcamRef.current && webcamRef.current.video && faceLandmarker) {
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
//   }, [isModelLoaded, faceLandmarker, isProcessing, step]);

//   // ==========================================
//   // STEP 1: XÁC THỰC FACE AI + CẢM XÚC
//   // ==========================================
//   const verifyFaceAI = useCallback(async () => {
//     if (!webcamRef.current) return;
//     setIsProcessing(true);
//     setStatus("AI đang phân tích danh tính và cảm xúc...");

//     const base64Image = webcamRef.current.getScreenshot();

//     try {
//       const response = await axiosClient.post('/transactions/verify', {
//         transactionId: transactionId,
//         authType: "FACE_AI",
//         faceImageBase64: base64Image
//       });

//       const resData = response.data;
//       if (resData.status === "NEXT_STEP" && resData.nextAuthType === "VOICE_OTP") {
//         toast.success("✅ " + resData.message);
//         setVoiceCode(resData.voiceCode);
//         setStep(2); // CHUYỂN SANG MÀN 2
//         setStatus("Vui lòng đọc to dãy số bên dưới");
//       }
//     } catch (error) {
//       const errorMsg = error.response?.data || "Phát hiện rủi ro sinh trắc!";
//       setStatus("❌ TỪ CHỐI GIAO DỊCH: " + errorMsg);
//       toast.error("🚨 Cảnh báo: " + errorMsg); 
//     } finally {
//       setIsProcessing(false);
//     }
//   }, [transactionId, webcamRef]);

//   // ==========================================
//   // STEP 2: THU ÂM & XÁC THỰC VOICE OTP
//   // ==========================================
//   const recordWavAudio = () => {
//     return new Promise((resolve, reject) => {
//       navigator.mediaDevices.getUserMedia({ audio: true }).then(stream => {
//         const audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
//         const source = audioContext.createMediaStreamSource(stream);
//         const processor = audioContext.createScriptProcessor(4096, 1, 1);
//         const audioChunks = [];

//         processor.onaudioprocess = (e) => {
//           audioChunks.push(new Float32Array(e.inputBuffer.getChannelData(0)));
//         };

//         source.connect(processor);
//         processor.connect(audioContext.destination);

//         // Thu âm chính xác trong 4 giây rồi tự ngắt
//         setTimeout(() => {
//           processor.disconnect();
//           source.disconnect();
//           audioContext.close();
//           stream.getTracks().forEach(t => t.stop());

//           let length = 0;
//           audioChunks.forEach(c => length += c.length);
//           let flattened = new Float32Array(length);
//           let offset = 0;
//           audioChunks.forEach(c => { flattened.set(c, offset); offset += c.length; });

//           let pcmData = new Int16Array(flattened.length);
//           for (let i = 0; i < flattened.length; i++) {
//             let s = Math.max(-1, Math.min(1, flattened[i]));
//             pcmData[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
//           }

//           const buffer = new ArrayBuffer(44 + pcmData.length * 2);
//           const view = new DataView(buffer);
//           const writeString = (v, o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
          
//           writeString(view, 0, 'RIFF');
//           view.setUint32(4, 36 + pcmData.length * 2, true);
//           writeString(view, 8, 'WAVE');
//           writeString(view, 12, 'fmt ');
//           view.setUint32(16, 16, true);
//           view.setUint16(20, 1, true);
//           view.setUint16(22, 1, true); 
//           view.setUint32(24, 16000, true); 
//           view.setUint32(28, 16000 * 2, true);
//           view.setUint16(32, 2, true);
//           view.setUint16(34, 16, true);
//           writeString(view, 36, 'data');
//           view.setUint32(40, pcmData.length * 2, true);
//           let pcmOffset = 44;
//           for (let i = 0; i < pcmData.length; i++, pcmOffset += 2) view.setInt16(pcmOffset, pcmData[i], true);

//           resolve(new Blob([view], { type: 'audio/wav' }));
//         }, 4000);
//       }).catch(reject);
//     });
//   };

//   const verifyVoiceOTP = async () => {
//     setIsRecording(true);
//     setStatus("🔴 Đang ghi âm (4 giây). Hãy đọc to mã số!");
    
//     try {
//       const wavBlob = await recordWavAudio();
//       setIsRecording(false);
//       setIsProcessing(true);
//       setStatus("Vosk AI đang phân tích giọng nói...");

//       const formDataToSend = new FormData();
//       formDataToSend.append('transactionId', transactionId);
//       formDataToSend.append('audioFile', wavBlob, 'voice.wav');

//       const response = await axiosClient.post('/transactions/verify-voice', formDataToSend, {
//         headers: { 'Content-Type': 'multipart/form-data' }
//       });

//       if (response.data.status === "SUCCESS") {
//         toast.success("🎉 Xác thực Đa Lớp thành công!");
//         navigate('/transaction-result', { 
//           state: { result: response.data.data, formData, recipientName } 
//         });
//       }
//     } catch (error) {
//       const errorMsg = error.response?.data || "Nhận diện giọng nói thất bại!";
//       setStatus("❌ TỪ CHỐI GIAO DỊCH: " + errorMsg);
//       toast.error("🚨 Lỗi: " + errorMsg); 
//     } finally {
//       setIsRecording(false);
//       setIsProcessing(false);
//     }
//   };

//   // ========================================================
//   // 🚀 LUỒNG BỊ CHẶN: Khi chưa có FaceID
//   // ========================================================
//   if (!hasFaceSetup) {
//     return (
//       <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-4">
//         <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl max-w-md w-full text-center border-4 border-orange-500/30 relative overflow-hidden">
//           <div className="mx-auto bg-orange-100 text-orange-600 w-20 h-20 rounded-full flex items-center justify-center mb-6">
//             <svg className="w-10 h-10" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
//           </div>
//           <h2 className="text-2xl font-black text-gray-900 mb-2 tracking-tight">CHƯA CÓ FACEID</h2>
//           <p className="text-gray-500 font-medium mb-8 leading-relaxed">
//             Hệ thống an ninh từ chối giao dịch do tài khoản của bạn chưa được thiết lập dữ liệu sinh trắc học. Vui lòng cài đặt trước khi tiếp tục.
//           </p>
//           <button 
//             onClick={() => navigate('/register-face')} 
//             className="w-full py-4 bg-gradient-to-r from-orange-500 to-red-500 hover:from-orange-600 hover:to-red-600 text-white rounded-2xl font-black shadow-lg shadow-orange-500/30 transition-all active:scale-95 uppercase tracking-wider"
//           >
//             ĐI CÀI ĐẶT NGAY
//           </button>
//           <button 
//             onClick={() => navigate('/dashboard')} 
//             className="w-full py-3 mt-3 text-slate-500 font-bold hover:text-slate-800 transition-colors"
//           >
//             Hủy giao dịch
//           </button>
//         </div>
//       </div>
//     );
//   }

//   // ========================================================
//   // 🚀 LUỒNG BÌNH THƯỜNG: Xác Thực High Risk
//   // ========================================================
//   return (
//     <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-4 relative overflow-hidden">
//       {/* Vòng sáng cảnh báo */}
//       <div className={`absolute top-0 left-0 w-full h-2 animate-pulse ${step === 1 ? 'bg-orange-500' : 'bg-red-500'}`}></div>

//       <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl max-w-md w-full text-center relative z-10 border-4 border-slate-800">
//         <h2 className="text-2xl font-black text-gray-900 mb-1 uppercase tracking-tight">Khu Vực An Ninh (HIGH RISK)</h2>
//         <p className="text-xs font-bold text-gray-400 mb-6 uppercase tracking-widest">
//           {step === 1 ? "Bước 1: Quét Khuôn Mặt" : "Bước 2: Xác Thực Giọng Nói"}
//         </p>
        
//         <p className={`text-sm font-bold mb-6 transition-colors ${isProcessing ? 'text-blue-500 animate-pulse' : isRecording ? 'text-red-500 animate-pulse' : 'text-slate-600'}`}>
//           {status}
//         </p>

//         {/* ================= KHUNG CAMERA DÙNG CHUNG ================= */}
//         <div className={`relative w-64 h-64 mx-auto rounded-xl overflow-hidden border-4 mb-8 shadow-2xl transition-all duration-300 ${step === 1 && isFaceDetected ? 'border-green-500 scale-105' : step === 2 ? 'border-blue-500' : 'border-slate-300'}`}>
//           {isModelLoaded ? (
//             <Webcam
//               audio={false}
//               ref={webcamRef}
//               screenshotFormat="image/jpeg"
//               videoConstraints={{ facingMode: "user" }}
//               className={`w-full h-full object-cover transform scale-x-[-1] ${step === 2 ? 'opacity-30 blur-sm' : ''}`}
//             />
//           ) : (
//             <div className="w-full h-full bg-slate-900 flex items-center justify-center">
//               <span className="text-white text-xs tracking-widest animate-pulse">INIT CAMERA...</span>
//             </div>
//           )}
          
//           {/* Lớp phủ UI cho Step 1 (Mặt) */}
//           {step === 1 && (
//             <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
//               <div className={`w-32 h-40 border-2 rounded-full transition-colors ${isFaceDetected ? 'border-green-400/50' : 'border-slate-400/50 border-dashed'}`}></div>
//             </div>
//           )}

//           {/* Lớp phủ UI cho Step 2 (Voice) */}
//           {step === 2 && (
//             <div className="absolute inset-0 flex flex-col items-center justify-center z-20">
//                <span className="text-white text-xs uppercase font-bold tracking-widest mb-2 shadow-black drop-shadow-md">Mã cần đọc:</span>
//                <span className="text-5xl font-black text-white tracking-[0.2em] drop-shadow-xl">{voiceCode}</span>
//                {isRecording && (
//                    <div className="mt-4 flex items-center bg-red-500 px-3 py-1 rounded-full animate-bounce">
//                        <div className="w-2 h-2 bg-white rounded-full mr-2 animate-ping"></div>
//                        <span className="text-white text-xs font-bold">ĐANG GHI ÂM</span>
//                    </div>
//                )}
//             </div>
//           )}
//         </div>

//         {/* NÚT BẤM (Đổi theo Step) */}
//         {step === 1 ? (
//           <button 
//             onClick={verifyFaceAI} 
//             disabled={!isModelLoaded || !isFaceDetected || isProcessing}
//             className={`w-full py-5 rounded-2xl font-black text-white text-lg transition-all shadow-xl ${(!isModelLoaded || !isFaceDetected || isProcessing) ? 'bg-gray-300 cursor-not-allowed shadow-none text-gray-500' : 'bg-orange-600 hover:bg-orange-700 active:scale-95'}`}
//           >
//             {isProcessing ? 'ĐANG QUÉT AI...' : 'XÁC THỰC DANH TÍNH'}
//           </button>
//         ) : (
//           <button 
//             onClick={verifyVoiceOTP} 
//             disabled={isRecording || isProcessing}
//             className={`w-full py-5 rounded-2xl font-black text-white text-lg transition-all shadow-xl ${(isRecording || isProcessing) ? 'bg-gray-300 cursor-not-allowed shadow-none' : 'bg-blue-600 hover:bg-blue-700 active:scale-95'}`}
//           >
//             {isProcessing ? 'ĐANG PHÂN TÍCH...' : 'NHẤN ĐỂ GHI ÂM (4 GIÂY)'}
//           </button>
//         )}
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

export default function HighRiskVerification() {
  const webcamRef = useRef(null);
  const navigate = useNavigate();
  const { state } = useLocation();
  const { transactionId, formData, recipientName } = state || {};

  const [step, setStep] = useState(1); 
  const [voiceCode, setVoiceCode] = useState('');

  const [isProcessing, setIsProcessing] = useState(false);
  const [status, setStatus] = useState("Đang tải AI Model...");

  const [faceLandmarker, setFaceLandmarker] = useState(null);
  const [isModelLoaded, setIsModelLoaded] = useState(false);
  const [isFaceDetected, setIsFaceDetected] = useState(false);
  const [isRecording, setIsRecording] = useState(false);

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
        toast.error("❌ Không thể tải AI Model.");
      }
    };
    initAI();
  }, [transactionId, navigate]); // 🚀 Đã xóa phụ thuộc hasFaceSetup

  useEffect(() => {
    let animationFrameId;
    const detectFace = () => {
      if (step === 1 && webcamRef.current && webcamRef.current.video && faceLandmarker) {
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
  }, [isModelLoaded, faceLandmarker, isProcessing, step]);

  const verifyFaceAI = useCallback(async () => {
    if (!webcamRef.current) return;
    setIsProcessing(true);
    setStatus("AI đang phân tích danh tính và cảm xúc...");

    const base64Image = webcamRef.current.getScreenshot();

    try {
      const response = await axiosClient.post('/transactions/verify', {
        transactionId: transactionId,
        authType: "FACE_AI",
        faceImageBase64: base64Image
      });

      const resData = response.data;
      if (resData.status === "NEXT_STEP" && resData.nextAuthType === "VOICE_OTP") {
        toast.success("✅ " + resData.message);
        setVoiceCode(resData.voiceCode);
        setStep(2); 
        setStatus("Vui lòng đọc to dãy số bên dưới");
      }
    } catch (error) {
      const errorMsg = error.response?.data || "Phát hiện rủi ro sinh trắc!";
      setStatus("❌ TỪ CHỐI GIAO DỊCH: " + errorMsg);
      toast.error("🚨 Cảnh báo: " + errorMsg); 
    } finally {
      setIsProcessing(false);
    }
  }, [transactionId, webcamRef]);

  const recordWavAudio = () => {
    return new Promise((resolve, reject) => {
      navigator.mediaDevices.getUserMedia({ audio: true }).then(stream => {
        const audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
        const source = audioContext.createMediaStreamSource(stream);
        const processor = audioContext.createScriptProcessor(4096, 1, 1);
        const audioChunks = [];

        processor.onaudioprocess = (e) => {
          audioChunks.push(new Float32Array(e.inputBuffer.getChannelData(0)));
        };

        source.connect(processor);
        processor.connect(audioContext.destination);

        setTimeout(() => {
          processor.disconnect();
          source.disconnect();
          audioContext.close();
          stream.getTracks().forEach(t => t.stop());

          let length = 0;
          audioChunks.forEach(c => length += c.length);
          let flattened = new Float32Array(length);
          let offset = 0;
          audioChunks.forEach(c => { flattened.set(c, offset); offset += c.length; });

          let pcmData = new Int16Array(flattened.length);
          for (let i = 0; i < flattened.length; i++) {
            let s = Math.max(-1, Math.min(1, flattened[i]));
            pcmData[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
          }

          const buffer = new ArrayBuffer(44 + pcmData.length * 2);
          const view = new DataView(buffer);
          const writeString = (v, o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
          
          writeString(view, 0, 'RIFF');
          view.setUint32(4, 36 + pcmData.length * 2, true);
          writeString(view, 8, 'WAVE');
          writeString(view, 12, 'fmt ');
          view.setUint32(16, 16, true);
          view.setUint16(20, 1, true);
          view.setUint16(22, 1, true); 
          view.setUint32(24, 16000, true); 
          view.setUint32(28, 16000 * 2, true);
          view.setUint16(32, 2, true);
          view.setUint16(34, 16, true);
          writeString(view, 36, 'data');
          view.setUint32(40, pcmData.length * 2, true);
          let pcmOffset = 44;
          for (let i = 0; i < pcmData.length; i++, pcmOffset += 2) view.setInt16(pcmOffset, pcmData[i], true);

          resolve(new Blob([view], { type: 'audio/wav' }));
        }, 4000);
      }).catch(reject);
    });
  };

  const verifyVoiceOTP = async () => {
    setIsRecording(true);
    setStatus("🔴 Đang ghi âm (4 giây). Hãy đọc to mã số!");
    
    try {
      const wavBlob = await recordWavAudio();
      setIsRecording(false);
      setIsProcessing(true);
      setStatus("Vosk AI đang phân tích giọng nói...");

      const formDataToSend = new FormData();
      formDataToSend.append('transactionId', transactionId);
      formDataToSend.append('audioFile', wavBlob, 'voice.wav');

      const response = await axiosClient.post('/transactions/verify-voice', formDataToSend, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });

      if (response.data.status === "SUCCESS") {
        toast.success("🎉 Xác thực Đa Lớp thành công!");
        navigate('/transaction-result', { 
          state: { result: response.data.data, formData, recipientName } 
        });
      }
    } catch (error) {
      const errorMsg = error.response?.data || "Nhận diện giọng nói thất bại!";
      setStatus("❌ TỪ CHỐI GIAO DỊCH: " + errorMsg);
      toast.error("🚨 Lỗi: " + errorMsg); 
    } finally {
      setIsRecording(false);
      setIsProcessing(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-4 relative overflow-hidden">
      <div className={`absolute top-0 left-0 w-full h-2 animate-pulse ${step === 1 ? 'bg-orange-500' : 'bg-red-500'}`}></div>

      <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl max-w-md w-full text-center relative z-10 border-4 border-slate-800">
        <h2 className="text-2xl font-black text-gray-900 mb-1 uppercase tracking-tight">Khu Vực An Ninh (HIGH RISK)</h2>
        <p className="text-xs font-bold text-gray-400 mb-6 uppercase tracking-widest">
          {step === 1 ? "Bước 1: Quét Khuôn Mặt" : "Bước 2: Xác Thực Giọng Nói"}
        </p>
        
        <p className={`text-sm font-bold mb-6 transition-colors ${isProcessing ? 'text-blue-500 animate-pulse' : isRecording ? 'text-red-500 animate-pulse' : 'text-slate-600'}`}>
          {status}
        </p>

        <div className={`relative w-64 h-64 mx-auto rounded-xl overflow-hidden border-4 mb-8 shadow-2xl transition-all duration-300 ${step === 1 && isFaceDetected ? 'border-green-500 scale-105' : step === 2 ? 'border-blue-500' : 'border-slate-300'}`}>
          {isModelLoaded ? (
            <Webcam
              audio={false}
              ref={webcamRef}
              screenshotFormat="image/jpeg"
              videoConstraints={{ facingMode: "user" }}
              className={`w-full h-full object-cover transform scale-x-[-1] ${step === 2 ? 'opacity-30 blur-sm' : ''}`}
            />
          ) : (
            <div className="w-full h-full bg-slate-900 flex items-center justify-center">
              <span className="text-white text-xs tracking-widest animate-pulse">INIT CAMERA...</span>
            </div>
          )}
          
          {step === 1 && (
            <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
              <div className={`w-32 h-40 border-2 rounded-full transition-colors ${isFaceDetected ? 'border-green-400/50' : 'border-slate-400/50 border-dashed'}`}></div>
            </div>
          )}

          {step === 2 && (
            <div className="absolute inset-0 flex flex-col items-center justify-center z-20">
               <span className="text-white text-xs uppercase font-bold tracking-widest mb-2 shadow-black drop-shadow-md">Mã cần đọc:</span>
               <span className="text-5xl font-black text-white tracking-[0.2em] drop-shadow-xl">{voiceCode}</span>
               {isRecording && (
                   <div className="mt-4 flex items-center bg-red-500 px-3 py-1 rounded-full animate-bounce">
                       <div className="w-2 h-2 bg-white rounded-full mr-2 animate-ping"></div>
                       <span className="text-white text-xs font-bold">ĐANG GHI ÂM</span>
                   </div>
               )}
            </div>
          )}
        </div>

        {step === 1 ? (
          <button 
            onClick={verifyFaceAI} 
            disabled={!isModelLoaded || !isFaceDetected || isProcessing}
            className={`w-full py-5 rounded-2xl font-black text-white text-lg transition-all shadow-xl ${(!isModelLoaded || !isFaceDetected || isProcessing) ? 'bg-gray-300 cursor-not-allowed shadow-none text-gray-500' : 'bg-orange-600 hover:bg-orange-700 active:scale-95'}`}
          >
            {isProcessing ? 'ĐANG QUÉT AI...' : 'XÁC THỰC DANH TÍNH'}
          </button>
        ) : (
          <button 
            onClick={verifyVoiceOTP} 
            disabled={isRecording || isProcessing}
            className={`w-full py-5 rounded-2xl font-black text-white text-lg transition-all shadow-xl ${(isRecording || isProcessing) ? 'bg-gray-300 cursor-not-allowed shadow-none' : 'bg-blue-600 hover:bg-blue-700 active:scale-95'}`}
          >
            {isProcessing ? 'ĐANG PHÂN TÍCH...' : 'NHẤN ĐỂ GHI ÂM (4 GIÂY)'}
          </button>
        )}
      </div>
    </div>
  );
}