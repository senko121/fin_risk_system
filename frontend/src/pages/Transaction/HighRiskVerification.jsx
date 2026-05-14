 
// import React, { useRef, useState, useEffect, useCallback } from 'react';
// import Webcam from 'react-webcam';
// import { useLocation, useNavigate } from 'react-router-dom';
// import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';
// import { toast } from 'react-toastify'; 

// export default function HighRiskVerification() {
//   const webcamRef = useRef(null);
//   const navigate = useNavigate();
//   const { state } = useLocation();
  
//   // 🚀 Lấy voiceCode từ state (do TransactionController luồng PIN truyền sang)
//   const { transactionId, formData, recipientName, voiceCode: stateVoiceCode } = state || {};

//   const [voiceCode, setVoiceCode] = useState(stateVoiceCode || '------');
//   const [isProcessing, setIsProcessing] = useState(false);
//   const [status, setStatus] = useState("Đang tải AI Model...");

//   const [faceLandmarker, setFaceLandmarker] = useState(null);
//   const [isModelLoaded, setIsModelLoaded] = useState(false);
//   const [isRecording, setIsRecording] = useState(false);

//   // 🚀 WEBSOCKET STATES
//   const wsRef = useRef(null);
//   const [liveEmotion, setLiveEmotion] = useState("NEUTRAL"); 

//   // 🚀 LIVENESS STATES
//   const [isLivenessPassed, setIsLivenessPassed] = useState(false);
//   const globalBlinkFlag = useRef(false);
//   const framesBuffer = useRef([]);
//   const MAX_FRAMES = 30; // 0.5 giây

//   // 1. KHỞI TẠO MEDIAPIPE
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
//           numFaces: 1,
//           outputFacialTransformationMatrixes: true, 
//           outputFaceBlendshapes: true 
//         });
//         setFaceLandmarker(landmarker);
//         setIsModelLoaded(true);
//         setStatus("👀 Hãy nhìn thẳng camera, chớp mắt VÀ lắc nhẹ đầu...");
//       } catch (err) {
//         setStatus("Lỗi khởi tạo AI.");
//         toast.error("❌ Không thể tải AI Model.");
//       }
//     };
//     initAI();
//   }, [transactionId, navigate]);

//   // 2. VÒNG LẶP LIVENESS 
//   useEffect(() => {
//     let animationFrameId;

//     const analyzeLiveness = () => {
//       if (!isLivenessPassed && webcamRef.current && webcamRef.current.video && faceLandmarker) {
//         const video = webcamRef.current.video;
//         if (video.currentTime > 0) {
//           const results = faceLandmarker.detectForVideo(video, performance.now());

//           if (results.faceLandmarks && results.faceLandmarks.length === 1) {
//             const landmarks = results.faceLandmarks[0];
//             const nose = landmarks[1];
//             const leftCheek = landmarks[234];
//             const rightCheek = landmarks[454];
//             const top = landmarks[10];
//             const bottom = landmarks[152];

//             const yaw = Math.atan2(rightCheek.z - leftCheek.z, rightCheek.x - leftCheek.x);
//             const pitch = Math.atan2(bottom.z - top.z, bottom.y - top.y);

//             const distLeft = Math.sqrt(Math.pow(leftCheek.x - nose.x, 2) + Math.pow(leftCheek.y - nose.y, 2));
//             const distRight = Math.sqrt(Math.pow(rightCheek.x - nose.x, 2) + Math.pow(rightCheek.y - nose.y, 2));
//             const totalDist = distLeft + distRight;
//             const ratio = totalDist > 0 ? Math.abs(distLeft - distRight) / totalDist : 0;

//             let currentBlinkScore = 0;
//             if (results.faceBlendshapes && results.faceBlendshapes.length > 0) {
//               const blendshapes = results.faceBlendshapes[0].categories;
//               const eyeBlinkLeft = blendshapes.find(shape => shape.categoryName === "eyeBlinkLeft")?.score || 0;
//               const eyeBlinkRight = blendshapes.find(shape => shape.categoryName === "eyeBlinkRight")?.score || 0;
//               currentBlinkScore = Math.max(eyeBlinkLeft, eyeBlinkRight);
              
//               if (currentBlinkScore > 0.45) { 
//                 globalBlinkFlag.current = true;
//               }
//             }

//             framesBuffer.current.push({ yaw, pitch, ratio, blinkScore: currentBlinkScore });
            
//             if (framesBuffer.current.length > MAX_FRAMES) {
//               framesBuffer.current.shift(); 
//             }

//             if (framesBuffer.current.length === MAX_FRAMES && !isProcessing) {
//               const yaws = framesBuffer.current.map(f => f.yaw);
//               const pitches = framesBuffer.current.map(f => f.pitch);
//               const ratios = framesBuffer.current.map(f => f.ratio);

//               const yawDelta = Math.abs(Math.max(...yaws) - Math.min(...yaws));
//               const pitchDelta = Math.abs(Math.max(...pitches) - Math.min(...pitches));
//               const ratioDelta = Math.abs(Math.max(...ratios) - Math.min(...ratios));

//               const THRESHOLD_RATIO = 0.065; 
//               const THRESHOLD_SHAKE = 0.05; 

//               const hasBlinked = globalBlinkFlag.current;
//               const is3D = ratioDelta >= THRESHOLD_RATIO; 
//               const isShaking = yawDelta > THRESHOLD_SHAKE || pitchDelta > THRESHOLD_SHAKE;

//               if (hasBlinked && is3D) {
//                 setIsLivenessPassed(true);
//                 setStatus("✅ NGƯỜI THẬT: Sẵn sàng Xác thực Kép!");
//               } else if (isShaking && !is3D) {
//                 setStatus("🚨 PHÁT HIỆN ẢNH GIẢ! Vui lòng dùng khuôn mặt thật.");
//               } else if (hasBlinked && !is3D && !isShaking) {
//                 setStatus("⏳ Đã nhận diện chớp mắt. Vui lòng lắc nhẹ đầu...");
//               } else if (!hasBlinked && is3D) {
//                 setStatus("⏳ Đang lắc đầu 3D... Vui lòng chớp mắt 1 cái!");
//               } else {
//                 setStatus("👀 Hãy nhìn thẳng camera, chớp mắt VÀ lắc nhẹ đầu...");
//               }
//             }
//           } else {
//             framesBuffer.current = [];
//             globalBlinkFlag.current = false;
//             setIsLivenessPassed(false);
//             if (!isProcessing) setStatus("Vui lòng đưa khuôn mặt vào giữa khung hình.");
//           }
//         }
//       }
//       animationFrameId = requestAnimationFrame(analyzeLiveness);
//     };

//     if (isModelLoaded) analyzeLiveness();
//     return () => cancelAnimationFrame(animationFrameId);
//   }, [isModelLoaded, faceLandmarker, isProcessing, isLivenessPassed]);

//   // 3. KHỞI TẠO PURE WEBSOCKET
//   useEffect(() => {
//     const socket = new WebSocket('ws://localhost:8081/ws/emotion-stream');
    
//     socket.onopen = () => console.log('✅ Đã kết nối Pure WebSocket 100%!');

//     socket.onmessage = (event) => {
//       const data = JSON.parse(event.data);
//       console.log("📥 [WS NHẬN]:", data);
      
//       // NHÁNH 1: UI Cảm xúc Live
//       if (data.type === "LIVE_RESULT" && data.status === "SUCCESS") {
//         setLiveEmotion(data.emotion);
//       }
//       // NHÁNH 2: Nhận kết quả chốt sổ ATOMIC
//       else if (data.type === "FINAL_RESULT") {
//          setIsProcessing(false);
//          if (data.status === "SUCCESS") {
//             toast.success("🎉 " + data.message);
//             // Chuyển sang trang kết quả thành công
//             navigate('/transaction-result', { 
//                 state: { result: data.data, formData, recipientName } 
//             });
//          } else {
//             setStatus("❌ TỪ CHỐI: " + data.message);
//             toast.error("🚨 Lỗi: " + data.message); 
//          }
//       }
//     };

//     socket.onerror = (error) => console.error('❌ Lỗi WebSocket:', error);
//     wsRef.current = socket;

//     return () => { if (wsRef.current) wsRef.current.close(); };
//   }, [navigate, formData, recipientName]);

//   // =========================================================================
//   // 🚀 MODULE GHI ÂM (Nâng lên 5 giây cho khớp với thời gian Video)
//   // =========================================================================
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

//         // THỜI GIAN GHI ÂM ĐÃ NÂNG LÊN 5000ms (5 giây)
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
//         }, 5000); 
//       }).catch(reject);
//     });
//   };

//   // =========================================================================
//   // 🚀 4. API ATOMIC (HỢP THỂ VIDEO & AUDIO CHẠY SONG SONG)
//   // =========================================================================
//   const verifyAtomicAllInOne = useCallback(async () => {
//     if (!webcamRef.current) return;
    
//     setIsProcessing(true);
//     setIsRecording(true);
//     setStatus("🔴 Đang ghi âm & quét mặt... Hãy đọc to mã số bên dưới!");
    
//     // 🚀 A. KÍCH HOẠT MICROPHONE (Chạy ngầm Promise)
//     const audioPromise = recordWavAudio();

//     // 🚀 B. KÍCH HOẠT CAMERA STREAMING (Chạy đè lên)
//     let frameCount = 0;
//     const TARGET_FRAMES = 50; 
//     const INTERVAL_MS = 100;  
    
//     const streamInterval = setInterval(() => {
//       if (webcamRef.current && wsRef.current?.readyState === WebSocket.OPEN) {
//         const frame = webcamRef.current.getScreenshot();
//         if (frame) {
//           const base64Data = frame.split(',')[1];
//           wsRef.current.send(JSON.stringify({ 
//               action: "STREAM",
//               transactionId: transactionId, 
//               frame: base64Data 
//           }));
//         }
//       }
//       frameCount++;
//       if (frameCount >= TARGET_FRAMES) {
//         clearInterval(streamInterval);
//       }
//     }, INTERVAL_MS); 

//     // 🚀 C. CHỜ AUDIO THU XONG 5 GIÂY LÀ CHỐT SỔ
//     try {
//         const wavBlob = await audioPromise; 
//         setIsRecording(false);
//         setStatus("🤖 AI đang tổng hợp Khuôn mặt + Giọng nói...");

//         // Nén Audio thành Base64
//         const reader = new FileReader();
//         reader.readAsDataURL(wavBlob);
//         reader.onloadend = () => {
//             const audioBase64 = reader.result.split(',')[1]; 

//             if (wsRef.current?.readyState === WebSocket.OPEN) {
//                  console.log("📤 [WS GỬI]: Gửi lệnh FINALIZE KÈM AUDIO Base64!");
//                  wsRef.current.send(JSON.stringify({ 
//                      action: "FINALIZE", 
//                      transactionId: transactionId,
//                      audioBase64: audioBase64 
//                  }));
//             }
//         };
//     } catch (err) {
//         toast.error("Lỗi Microphone!");
//         setIsProcessing(false);
//     }
    
//   }, [transactionId]);

//   return (
//     <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-4 relative overflow-hidden">
//       <div className={`absolute top-0 left-0 w-full h-2 animate-pulse ${isLivenessPassed ? 'bg-green-500' : 'bg-orange-500'}`}></div>

//       <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl max-w-md w-full text-center relative z-10 border-4 border-slate-800">
//         <h2 className="text-2xl font-black text-gray-900 mb-1 uppercase tracking-tight">Khu Vực An Ninh (HIGH RISK)</h2>
//         <p className="text-xs font-bold text-gray-400 mb-6 uppercase tracking-widest">
//           XÁC THỰC SINH TRẮC HỌC KÉP
//         </p>
        
//         <p className={`text-sm font-bold mb-6 transition-colors ${isProcessing ? 'text-blue-500 animate-pulse' : isRecording ? 'text-red-500 animate-pulse' : status.includes('🚨') || status.includes('❌') ? 'text-red-600 animate-bounce' : status.includes('✅') ? 'text-green-600' : 'text-slate-600'}`}>
//           {status}
//         </p>

//         {/* 🚀 KHUNG CAMERA SẠCH SẼ (Chỉ chứa liveness và cục báo cảm xúc nhỏ góc phải) */}
//         <div className={`relative w-64 h-64 mx-auto rounded-xl overflow-hidden border-4 mb-4 shadow-2xl transition-all duration-300 ${isLivenessPassed ? 'border-green-500 scale-105' : 'border-slate-300'}`}>
          
//           {isProcessing && (
//              <div className="absolute top-2 right-2 z-50 bg-black/60 text-white px-3 py-1 rounded-full text-xs font-bold uppercase backdrop-blur-sm animate-pulse">
//                 Tâm lý: <span className={liveEmotion === 'FEAR' || liveEmotion === 'ANGRY' ? 'text-red-400' : 'text-green-400'}>{liveEmotion}</span>
//              </div>
//           )}

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
//               <span className="text-white text-xs tracking-widest animate-pulse">INIT CAMERA...</span>
//             </div>
//           )}
          
//           {/* Lớp mờ bắt liveness */}
//           {!isLivenessPassed && (
//             <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
//               <div className="w-32 h-40 border-2 rounded-full transition-colors border-slate-400/50 border-dashed"></div>
//             </div>
//           )}
//         </div>

//         {/* 🚀 LỚP HIỂN THỊ MÃ OTP (Đã được chuyển xuống ĐƯỚI camera) */}
//         <div className="h-28 mb-4 flex flex-col items-center justify-center transition-all duration-500">
//             {isLivenessPassed ? (
//                 <div className="animate-fade-in-up flex flex-col items-center">
//                     <span className="text-slate-500 text-xs uppercase font-bold tracking-widest mb-1">
//                         Hãy đọc to mã này:
//                     </span>
//                     <span className="text-[2.75rem] leading-none font-black text-indigo-600 tracking-[0.2em] drop-shadow-sm">
//                         {voiceCode}
//                     </span>
                    
//                     {/* Hiệu ứng chớp nháy khi đang ghi âm */}
//                     {isRecording ? (
//                        <div className="mt-3 flex items-center bg-red-100 border border-red-200 px-4 py-1.5 rounded-full shadow-sm animate-pulse">
//                            <div className="w-2 h-2 bg-red-600 rounded-full mr-2 animate-ping"></div>
//                            <span className="text-red-600 text-[10px] uppercase font-black tracking-widest">Đang ghi âm...</span>
//                        </div>
//                     ) : (
//                        <div className="mt-3 flex items-center px-4 py-1.5 opacity-0">
//                            {/* Element ẩn để giữ nguyên Layout không bị giật khung hình */}
//                            <div className="w-2 h-2 mr-2"></div>
//                            <span className="text-[10px]">placeholder</span>
//                        </div>
//                     )}
//                 </div>
//             ) : (
//                 <div className="text-slate-400 text-sm font-medium animate-pulse flex flex-col items-center justify-center h-full">
//                     <svg className="w-6 h-6 mb-2 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path></svg>
//                     Chờ nhận diện khuôn mặt...
//                 </div>
//             )}
//         </div>

//         {/* NÚT BẤM */}
//         <button 
//             onClick={verifyAtomicAllInOne} 
//             disabled={!isModelLoaded || !isLivenessPassed || isProcessing}
//             className={`w-full py-5 rounded-2xl font-black text-white text-lg transition-all shadow-xl ${
//               (!isModelLoaded || !isLivenessPassed || isProcessing) 
//                 ? 'bg-slate-300 cursor-not-allowed shadow-none text-slate-500' 
//                 : 'bg-indigo-600 hover:bg-indigo-700 active:scale-95 hover:shadow-indigo-500/30'
//             }`}
//         >
//             {isProcessing ? '🤖 ĐANG XỬ LÝ DỮ LIỆU...' : 'BẮT ĐẦU XÁC THỰC (5 GIÂY)'}
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

export default function HighRiskVerification() {
  const webcamRef = useRef(null);
  const navigate = useNavigate();
  const { state } = useLocation();
  
  const { transactionId, formData, recipientName, voiceCode: stateVoiceCode } = state || {};

  const [voiceCode, setVoiceCode] = useState(stateVoiceCode || '------');
  const [isProcessing, setIsProcessing] = useState(false);
  const [status, setStatus] = useState("Đang tải AI Model...");

  const [faceLandmarker, setFaceLandmarker] = useState(null);
  const [isModelLoaded, setIsModelLoaded] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [isFrozen, setIsFrozen] = useState(false);

  // 🚀 WEBSOCKET STATES (TÁCH LÀM 2 ỐNG)
  const wsFaceRef = useRef(null);
  const wsVoiceRef = useRef(null);
  const [liveEmotion, setLiveEmotion] = useState("NEUTRAL"); 

  const [isLivenessPassed, setIsLivenessPassed] = useState(false);
  const globalBlinkFlag = useRef(false);
  const framesBuffer = useRef([]);
  const MAX_FRAMES = 30; 

  // 1. KHỞI TẠO MEDIAPIPE (Giữ nguyên)
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



  // 2. VÒNG LẶP LIVENESS (Giữ nguyên)
  useEffect(() => {
    let animationFrameId;
    const analyzeLiveness = () => {
      if (!isLivenessPassed && webcamRef.current && webcamRef.current.video && faceLandmarker) {
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
              
              if (currentBlinkScore > 0.45) globalBlinkFlag.current = true;
            }

            framesBuffer.current.push({ yaw, pitch, ratio, blinkScore: currentBlinkScore });
            if (framesBuffer.current.length > MAX_FRAMES) framesBuffer.current.shift(); 

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
                setStatus("✅ NGƯỜI THẬT: Sẵn sàng Xác thực Kép!");
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
            if (!isProcessing) setStatus("Vui lòng đưa khuôn mặt vào giữa khung hình.");
          }
        }
      }
      animationFrameId = requestAnimationFrame(analyzeLiveness);
    };

    if (isModelLoaded) analyzeLiveness();
    return () => cancelAnimationFrame(animationFrameId);
  }, [isModelLoaded, faceLandmarker, isProcessing, isLivenessPassed]);

  // 3. KHỞI TẠO 2 ỐNG WEBSOCKET ĐỘC LẬP
  useEffect(() => {
    // Ống 1: Nhận diện khuôn mặt (Dùng JSON)
    const faceSocket = new WebSocket('ws://localhost:8081/ws/emotion-stream');
    faceSocket.onopen = () => console.log('✅ Đã kết nối Socket Face!');
faceSocket.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.type === "LIVE_RESULT" && data.status === "SUCCESS") {
        setLiveEmotion(data.emotion);
      } else if (data.type === "FINAL_RESULT") {
         
         // 🚀 FIX ZOMBIE SOCKET: Chặt đứt ống Voice cũ
         if (wsVoiceRef.current) {
             wsVoiceRef.current.close();
         }

         if (data.status === "SUCCESS") {
            setIsProcessing(false);
            toast.success("🎉 " + data.message);
            navigate('/transaction-result', { 
                state: { result: data.data, formData, recipientName } 
            });
         } 
         // =======================================================
         // 🚀 KỊCH BẢN CƯỠNG ÉP (FEAR/ANGRY): GIAM LỎNG MÀN HÌNH
         // =======================================================
         else if (data.status === "UNDER_REVIEW") {
            setIsFrozen(true); // Khóa cứng màn hình
            // Không set isProcessing(false) để cái viền camera vẫn nhấp nháy tạo cảm giác "đang load"
            setStatus("⏳ " + data.message);
            toast.info("Hệ thống đang xử lý, vui lòng giữ màn hình sáng...", { 
                autoClose: false, // Bắt nó hiện mãi mãi không tắt
                theme: "colored"
            });
         } 
         // =======================================================
         else {
            // Lỗi bình thường (đọc sai số, trượt mặt)
            setIsProcessing(false);
            setStatus("❌ TỪ CHỐI: " + data.message);
            toast.error("🚨 Lỗi: " + data.message); 
         }
      }
    };
    wsFaceRef.current = faceSocket;

    // Ống 2: Thu âm giọng nói (Dùng mảng byte nhị phân)
    const voiceSocket = new WebSocket('ws://localhost:8081/ws/voice-stream?txId=' + transactionId);
    voiceSocket.binaryType = 'arraybuffer'; // Cực kỳ quan trọng để nhận gửi binary
    voiceSocket.onopen = () => console.log('✅ Đã kết nối Socket Voice (Binary)!');
    wsVoiceRef.current = voiceSocket;

    return () => { 
      if (wsFaceRef.current) wsFaceRef.current.close(); 
      if (wsVoiceRef.current) wsVoiceRef.current.close(); 
    };
  }, [navigate, formData, recipientName]);



  // =========================================================================
  //   4. API ATOMIC STREAMING (BĂM NHỎ DỮ LIỆU LIÊN TỤC)
  // =========================================================================
  const verifyAtomicAllInOne = useCallback(async () => {
    if (!webcamRef.current) return;
    
    setIsProcessing(true);
    setStatus("⏳ Đang thiết lập đường truyền bảo mật...");

    // 🚀 BƯỚC QUAN TRỌNG: Đảm bảo ống Voice được nối lại nếu user đang thử nghiệm lần 2, lần 3
    try {
        await ensureVoiceSocketOpen();
    } catch (e) {
        toast.error("Lỗi: Không thể kết nối đến Máy chủ Sinh trắc học!");
        setIsProcessing(false);
        return;
    }

    setIsRecording(true);
    setStatus("🔴 Đang ghi âm & quét mặt... Hãy đọc to mã số bên dưới!");
    
    // 🚀 A. KÍCH HOẠT CAMERA STREAMING (Mỗi 100ms gửi 1 ảnh qua Face Socket)
    let frameCount = 0;
    const TARGET_FRAMES = 100; // 10 giây
    
    const streamInterval = setInterval(() => {
      // Dù Face socket không bị ngắt, nhưng vẫn phải check an toàn
      if (webcamRef.current && wsFaceRef.current?.readyState === WebSocket.OPEN) {
        const frame = webcamRef.current.getScreenshot();
        if (frame) {
          wsFaceRef.current.send(JSON.stringify({ 
              action: "STREAM",
              transactionId: transactionId, 
              frame: frame.split(',')[1] 
          }));
        }
      }
      frameCount++;
    }, 100); 

    // 🚀 B. KÍCH HOẠT MICROPHONE STREAMING BINARY (Chạy song song)
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        const audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
        const source = audioContext.createMediaStreamSource(stream);

        const workletCode = `
            class PCMProcessor extends AudioWorkletProcessor {
                process(inputs, outputs, parameters) {
                    const input = inputs[0];
                    if (input && input.length > 0) {
                        const channelData = input[0];
                        const pcmData = new Int16Array(channelData.length);
                        for (let i = 0; i < channelData.length; i++) {
                            let s = Math.max(-1, Math.min(1, channelData[i]));
                            pcmData[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
                        }
                        this.port.postMessage(pcmData.buffer);
                    }
                    return true;
                }
            }
            registerProcessor('pcm-processor', PCMProcessor);
        `;

        const blob = new Blob([workletCode], { type: 'application/javascript' });
        const workletUrl = URL.createObjectURL(blob);

        await audioContext.audioWorklet.addModule(workletUrl);
        const workletNode = new AudioWorkletNode(audioContext, 'pcm-processor');

        workletNode.port.onmessage = (e) => {
            if (wsVoiceRef.current?.readyState === WebSocket.OPEN) {
                wsVoiceRef.current.send(e.data);
            }
        };

        source.connect(workletNode);
        workletNode.connect(audioContext.destination);

        // 🚀 C. SAU 10 GIÂY -> CHỐT SỔ
        setTimeout(() => {
            clearInterval(streamInterval);
            
            workletNode.disconnect();
            source.disconnect();
            audioContext.close();
            stream.getTracks().forEach(t => t.stop());
            URL.revokeObjectURL(workletUrl); 
            
            setIsRecording(false);
            setStatus("🤖 AI đang tổng hợp Khuôn mặt + Giọng nói...");

            // Báo chốt sổ Face
            if (wsFaceRef.current?.readyState === WebSocket.OPEN) {
                wsFaceRef.current.send(JSON.stringify({ 
                    action: "FINALIZE", 
                    transactionId: transactionId
                }));
            }
            
            // Báo chốt sổ Voice (Gửi EOF - Python sẽ nhận và ngắt socket)
            if (wsVoiceRef.current?.readyState === WebSocket.OPEN) {
                wsVoiceRef.current.send(new Int16Array(0).buffer);
            }

        }, 10000); 

    } catch (err) {
        console.error("Lỗi Mic:", err);
        toast.error("Lỗi truy cập Microphone!");
        setIsProcessing(false);
        setIsRecording(false);
    }
    
  }, [transactionId]);

  // =========================================================================
  //   HÀM ĐẢM BẢO KẾT NỐI VOICE LUÔN SỐNG TRƯỚC KHI THU ÂM
  // =========================================================================
  const ensureVoiceSocketOpen = () => {
    return new Promise((resolve, reject) => {
      // Nếu ống vẫn còn sống (OPEN) thì đi tiếp luôn, không cần mở mới
      if (wsVoiceRef.current && wsVoiceRef.current.readyState === WebSocket.OPEN) {
        resolve();
        return;
      }

      console.log("♻️ [RECONNECT] Luồng Voice đã chết. Đang nối lại ống mới...");
      const voiceSocket = new WebSocket('ws://localhost:8081/ws/voice-stream?txId=' + transactionId);
      voiceSocket.binaryType = 'arraybuffer';
      
      voiceSocket.onopen = () => {
        console.log('✅ [RECONNECT] Nối ống Voice thành công!');
        wsVoiceRef.current = voiceSocket;
        resolve();
      };

      voiceSocket.onerror = (error) => {
        console.error('❌ [RECONNECT] Lỗi nối ống Voice:', error);
        reject(error);
      };
    });
  };

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-4 relative overflow-hidden">
      <div className={`absolute top-0 left-0 w-full h-2 animate-pulse ${isLivenessPassed ? 'bg-green-500' : 'bg-orange-500'}`}></div>

      <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl max-w-md w-full text-center relative z-10 border-4 border-slate-800">
        <h2 className="text-2xl font-black text-gray-900 mb-1 uppercase tracking-tight">Khu Vực An Ninh (HIGH RISK)</h2>
        <p className="text-xs font-bold text-gray-400 mb-6 uppercase tracking-widest">
          XÁC THỰC SINH TRẮC HỌC KÉP
        </p>
        
        <p className={`text-sm font-bold mb-6 transition-colors ${isProcessing ? 'text-blue-500 animate-pulse' : isRecording ? 'text-red-500 animate-pulse' : status.includes('🚨') || status.includes('❌') ? 'text-red-600 animate-bounce' : status.includes('✅') ? 'text-green-600' : 'text-slate-600'}`}>
          {status}
        </p>

        {/* 🚀 KHUNG CAMERA */}
        <div className={`relative w-64 h-64 mx-auto rounded-xl overflow-hidden border-4 mb-4 shadow-2xl transition-all duration-300 ${isLivenessPassed ? 'border-green-500 scale-105' : 'border-slate-300'}`}>
          
          {isProcessing && (
             <div className="absolute top-2 right-2 z-50 bg-black/60 text-white px-3 py-1 rounded-full text-xs font-bold uppercase backdrop-blur-sm animate-pulse">
                Tâm lý: <span className={liveEmotion === 'FEAR' || liveEmotion === 'ANGRY' ? 'text-red-400' : 'text-green-400'}>{liveEmotion}</span>
             </div>
          )}

          {isModelLoaded ? (
            <Webcam
              audio={false}
              ref={webcamRef}
              screenshotFormat="image/jpeg"
              videoConstraints={{ facingMode: "user" }}
              className="w-full h-full object-cover transform scale-x-[-1]"
            />
          ) : (
            <div className="w-full h-full bg-slate-900 flex items-center justify-center">
              <span className="text-white text-xs tracking-widest animate-pulse">INIT CAMERA...</span>
            </div>
          )}
          
          {!isLivenessPassed && (
            <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
              <div className="w-32 h-40 border-2 rounded-full transition-colors border-slate-400/50 border-dashed"></div>
            </div>
          )}
        </div>

        {/* 🚀 LỚP HIỂN THỊ MÃ OTP */}
        <div className="h-28 mb-4 flex flex-col items-center justify-center transition-all duration-500">
            {isLivenessPassed ? (
                <div className="animate-fade-in-up flex flex-col items-center">
                    <span className="text-slate-500 text-xs uppercase font-bold tracking-widest mb-1">
                        Hãy đọc to mã này:
                    </span>
                    <span className="text-[2.75rem] leading-none font-black text-indigo-600 tracking-[0.2em] drop-shadow-sm">
                        {voiceCode}
                    </span>
                    
                    {isRecording ? (
                       <div className="mt-3 flex items-center bg-red-100 border border-red-200 px-4 py-1.5 rounded-full shadow-sm animate-pulse">
                           <div className="w-2 h-2 bg-red-600 rounded-full mr-2 animate-ping"></div>
                           <span className="text-red-600 text-[10px] uppercase font-black tracking-widest">Đang ghi âm...</span>
                       </div>
                    ) : (
                       <div className="mt-3 flex items-center px-4 py-1.5 opacity-0">
                           <div className="w-2 h-2 mr-2"></div>
                           <span className="text-[10px]">placeholder</span>
                       </div>
                    )}
                </div>
            ) : (
                <div className="text-slate-400 text-sm font-medium animate-pulse flex flex-col items-center justify-center h-full">
                    <svg className="w-6 h-6 mb-2 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path></svg>
                    Chờ nhận diện khuôn mặt...
                </div>
            )}
        </div>

        {/* NÚT BẤM */}
        <button 
            onClick={verifyAtomicAllInOne} 
            disabled={!isModelLoaded || !isLivenessPassed || isProcessing || isFrozen}
            className={`w-full py-5 rounded-2xl font-black text-white text-lg transition-all shadow-xl ${
              (!isModelLoaded || !isLivenessPassed || isProcessing || isFrozen) 
                ? 'bg-slate-300 cursor-not-allowed shadow-none text-slate-500' 
                : 'bg-indigo-600 hover:bg-indigo-700 active:scale-95 hover:shadow-indigo-500/30'
            }`}
        >
            {isFrozen ? '⏳ ĐANG XỬ LÝ BẢO MẬT...' : isProcessing ? '🤖 ĐANG XỬ LÝ DỮ LIỆU...' : 'BẮT ĐẦU XÁC THỰC (10 GIÂY)'}
        </button>
      </div>
    </div>
  );
}