
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

//   const [step, setStep] = useState(1); 
//   const [voiceCode, setVoiceCode] = useState('');

//   const [isProcessing, setIsProcessing] = useState(false);
//   const [status, setStatus] = useState("Đang tải AI Model...");

//   const [faceLandmarker, setFaceLandmarker] = useState(null);
//   const [isModelLoaded, setIsModelLoaded] = useState(false);
//   const [isFaceDetected, setIsFaceDetected] = useState(false);
//   const [isRecording, setIsRecording] = useState(false);

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
//         toast.error("❌ Không thể tải AI Model.");
//       }
//     };
//     initAI();
//   }, [transactionId, navigate]); // 🚀 Đã xóa phụ thuộc hasFaceSetup

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
//         setStep(2); 
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

//   return (
//     <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-4 relative overflow-hidden">
//       <div className={`absolute top-0 left-0 w-full h-2 animate-pulse ${step === 1 ? 'bg-orange-500' : 'bg-red-500'}`}></div>

//       <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl max-w-md w-full text-center relative z-10 border-4 border-slate-800">
//         <h2 className="text-2xl font-black text-gray-900 mb-1 uppercase tracking-tight">Khu Vực An Ninh (HIGH RISK)</h2>
//         <p className="text-xs font-bold text-gray-400 mb-6 uppercase tracking-widest">
//           {step === 1 ? "Bước 1: Quét Khuôn Mặt" : "Bước 2: Xác Thực Giọng Nói"}
//         </p>
        
//         <p className={`text-sm font-bold mb-6 transition-colors ${isProcessing ? 'text-blue-500 animate-pulse' : isRecording ? 'text-red-500 animate-pulse' : 'text-slate-600'}`}>
//           {status}
//         </p>

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
          
//           {step === 1 && (
//             <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
//               <div className={`w-32 h-40 border-2 rounded-full transition-colors ${isFaceDetected ? 'border-green-400/50' : 'border-slate-400/50 border-dashed'}`}></div>
//             </div>
//           )}

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
  const [isRecording, setIsRecording] = useState(false);

  // 🚀 LIVENESS STATES
  const [isLivenessPassed, setIsLivenessPassed] = useState(false);
  const globalBlinkFlag = useRef(false);
  const framesBuffer = useRef([]);
  const MAX_FRAMES = 30; // 0.5 giây

  // 1. KHỞI TẠO MEDIAPIPE (Đã bật nhận diện Cảm biến sâu & Chớp mắt)
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
          outputFacialTransformationMatrixes: true, 
          outputFaceBlendshapes: true 
        });
        setFaceLandmarker(landmarker);
        setIsModelLoaded(true);
        setStatus("👀 Hãy nhìn thẳng camera, chớp mắt VÀ lắc nhẹ đầu...");
      } catch (err) {
        setStatus("Lỗi khởi tạo AI.");
        toast.error("❌ Không thể tải AI Model.");
      }
    };
    initAI();
  }, [transactionId, navigate]);

  // 2. VÒNG LẶP LIVENESS (Thay thế hàm detectFace cũ)
  useEffect(() => {
    let animationFrameId;

    const analyzeLiveness = () => {
      // Chỉ phân tích ở Bước 1 và khi chưa qua ải Liveness
      if (step === 1 && !isLivenessPassed && webcamRef.current && webcamRef.current.video && faceLandmarker) {
        const video = webcamRef.current.video;
        if (video.currentTime > 0) {
          const results = faceLandmarker.detectForVideo(video, performance.now());

          if (results.faceLandmarks && results.faceLandmarks.length === 1) {
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

              // ⚖️ LUẬT THÉP V2 (ĐÃ CHỐT HỆ SỐ)
              const THRESHOLD_RATIO = 0.065; 
              const THRESHOLD_SHAKE = 0.05; 

              const hasBlinked = globalBlinkFlag.current;
              const is3D = ratioDelta >= THRESHOLD_RATIO; 
              const isShaking = yawDelta > THRESHOLD_SHAKE || pitchDelta > THRESHOLD_SHAKE;

              if (hasBlinked && is3D) {
                setIsLivenessPassed(true);
                setStatus("✅ XÁC THỰC THÀNH CÔNG: Sẵn sàng quét danh tính!");
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
            // Mất mặt ra khỏi khung hình
            framesBuffer.current = [];
            globalBlinkFlag.current = false;
            setIsLivenessPassed(false);
            if (!isProcessing) setStatus("Vui lòng đưa khuôn mặt vào giữa khung hình.");
          }
        }
      }
      animationFrameId = requestAnimationFrame(analyzeLiveness);
    };

    if (isModelLoaded) analyzeLiveness();
    return () => cancelAnimationFrame(animationFrameId);
  }, [isModelLoaded, faceLandmarker, isProcessing, step, isLivenessPassed]);

  // 3. API KIỂM TRA DANH TÍNH (Giữ nguyên)
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
      setIsLivenessPassed(false); // Reset lại Liveness nếu API báo lỗi
      framesBuffer.current = [];
      globalBlinkFlag.current = false;
    } finally {
      setIsProcessing(false);
    }
  }, [transactionId, webcamRef]);

  // 4. API GHI ÂM & VOICE OTP (Giữ nguyên)
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
        
        <p className={`text-sm font-bold mb-6 transition-colors ${isProcessing ? 'text-blue-500 animate-pulse' : isRecording ? 'text-red-500 animate-pulse' : status.includes('🚨') || status.includes('❌') ? 'text-red-600 animate-bounce' : status.includes('✅') ? 'text-green-600' : 'text-slate-600'}`}>
          {status}
        </p>

        <div className={`relative w-64 h-64 mx-auto rounded-xl overflow-hidden border-4 mb-8 shadow-2xl transition-all duration-300 ${step === 1 && isLivenessPassed ? 'border-green-500 scale-105' : step === 2 ? 'border-blue-500' : 'border-slate-300'}`}>
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
              <div className={`w-32 h-40 border-2 rounded-full transition-colors ${isLivenessPassed ? 'border-green-400/50' : 'border-slate-400/50 border-dashed'}`}></div>
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
            disabled={!isModelLoaded || !isLivenessPassed || isProcessing}
            className={`w-full py-5 rounded-2xl font-black text-white text-lg transition-all shadow-xl ${(!isModelLoaded || !isLivenessPassed || isProcessing) ? 'bg-gray-300 cursor-not-allowed shadow-none text-gray-500' : 'bg-orange-600 hover:bg-orange-700 active:scale-95'}`}
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