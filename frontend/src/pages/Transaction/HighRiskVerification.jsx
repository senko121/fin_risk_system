import React, { useRef, useState, useEffect, useLayoutEffect, useCallback } from 'react';
import Webcam from 'react-webcam';
import { useLocation, useNavigate } from 'react-router-dom';
import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';
import { toast } from 'react-toastify';
import axiosClient from '../../api/axiosClient';

export default function HighRiskVerification() {
  const webcamRef = useRef(null);
  const navigate = useNavigate();
  const { state } = useLocation();
  
  const { transactionId, formData, recipientName, voiceCode: stateVoiceCode, biometricSessionToken } = state || {};

  // Đọc từ state HOẶC sessionStorage (fallback khi StrictMode remount)
  const staticTokenRef = useRef(
    biometricSessionToken || sessionStorage.getItem('bsToken')
  );

useLayoutEffect(() => {
  const token = biometricSessionToken || sessionStorage.getItem('bsToken');
  if (token) {
    staticTokenRef.current = token;
    console.log('✅ [TOKEN SYNC] ref đã nhận token:', token);
  } else {
    console.error('❌ [TOKEN SYNC] Không tìm thấy token!');
  }
}, []); // ← []: chỉ chạy 1 lần

  const [voiceCode, setVoiceCode] = useState(stateVoiceCode || '------');
  const [isProcessing, setIsProcessing] = useState(false);
  const [status, setStatus] = useState("Đang tải AI Model...");

  const [faceLandmarker, setFaceLandmarker] = useState(null);
  const [isModelLoaded, setIsModelLoaded] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [isFrozen, setIsFrozen] = useState(false);

  // 🚀 WEBSOCKET REFS (TÁCH LÀM 2 ỐNG)
  const wsFaceRef = useRef(null);
  const wsVoiceRef = useRef(null);
  const ackTimerRef = useRef(null); // cleared when WS FINAL_RESULT arrives
  const adminPollIntervalRef = useRef(null);
  const [liveEmotion, setLiveEmotion] = useState("NEUTRAL");

  const [isLivenessPassed, setIsLivenessPassed] = useState(false);
  const [isUnderReview, setIsUnderReview] = useState(false);
  const [isFrozenResult, setIsFrozenResult] = useState(false);
  const [isRejectedByAdmin, setIsRejectedByAdmin] = useState(false);
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


  // 2. VÒNG LẶP LIVENESS (Sử dụng setTimeout chống treo WebAssembly)
  useEffect(() => {
    let timeoutId;
    let isComponentMounted = true;

    const analyzeLiveness = () => {
      if (!isComponentMounted) return;

      if (!isLivenessPassed && webcamRef.current && webcamRef.current.video && faceLandmarker) {
        const video = webcamRef.current.video;
        
        if (video.currentTime > 0 && video.readyState >= 3) {
          try {
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
          } catch (wasmErr) {
            console.warn("MediaPipe engine đang bận dọn dẹp RAM...", wasmErr);
          }
        }
      }
      
      if (!isLivenessPassed) {
        timeoutId = setTimeout(analyzeLiveness, 150);
      }
    };

    if (isModelLoaded && !isLivenessPassed) {
      analyzeLiveness();
    }

    return () => {
      isComponentMounted = false;
      if (timeoutId) clearTimeout(timeoutId);
    };
  }, [isModelLoaded, faceLandmarker, isProcessing, isLivenessPassed]);


  // 3a-0. UNDER_REVIEW POLLING — chạy mỗi 5s cho đến khi admin ra quyết định
  const pollUntilAdminDecision = useCallback(() => {
    if (adminPollIntervalRef.current) return;
    adminPollIntervalRef.current = setInterval(async () => {
      try {
        const res = await axiosClient.get(`/transactions/${transactionId}/verification-status`);
        const txStatus = res.data?.status;
        if (txStatus === 'SUCCESS') {
          clearInterval(adminPollIntervalRef.current);
          adminPollIntervalRef.current = null;
          toast.success("🎉 Giao dịch đã được phê duyệt thành công!");
          navigate('/transaction-result', {
            state: { result: res.data?.data, formData, recipientName }
          });
        } else if (txStatus === 'BLOCKED') {
          clearInterval(adminPollIntervalRef.current);
          adminPollIntervalRef.current = null;
          setIsUnderReview(false);
          setIsRejectedByAdmin(true);
          toast.error("🚫 Giao dịch bị từ chối.");
        } else if (txStatus === 'FROZEN') {
          clearInterval(adminPollIntervalRef.current);
          adminPollIntervalRef.current = null;
          setIsUnderReview(false);
          setIsFrozenResult(true);
        }
        // UNDER_REVIEW → giữ nguyên interval, thử lại lần sau
      } catch (_err) {
        // network error → giữ nguyên interval
      }
    }, 5000);
  }, [transactionId, navigate, formData, recipientName]);

  // 3a. POLLING FALLBACK — gọi khi FINAL_RESULT WS không đến sau 20s
  const pollVerificationStatus = useCallback(async () => {
    let attempts = 0;
    const MAX_ATTEMPTS = 6;

    const poll = async () => {
      if (attempts >= MAX_ATTEMPTS) {
        setIsProcessing(false);
        setStatus("⚠️ Không nhận được phản hồi. Kiểm tra lại lịch sử giao dịch.");
        toast.warning("Không thể xác nhận kết quả qua WebSocket. Vui lòng kiểm tra lịch sử giao dịch.");
        return;
      }
      attempts++;
      try {
        const res = await axiosClient.get(`/transactions/${transactionId}/verification-status`);
        const txStatus = res.data?.status;

        if (txStatus === 'SUCCESS') {
          setIsProcessing(false);
          toast.success("🎉 Xác thực thành công!");
          navigate('/transaction-result', {
            state: { result: res.data?.data, formData, recipientName }
          });
        } else if (txStatus === 'BLOCKED') {
          setIsProcessing(false);
          setIsFrozen(true);
          setStatus("🚫 Giao dịch bị khóa do xác thực sai quá 3 lần.");
          toast.error("🚫 Giao dịch bị khóa.");
        } else if (txStatus === 'UNDER_REVIEW') {
          setIsFrozen(true);
          setIsUnderReview(true);
          pollUntilAdminDecision();
        } else {
          setTimeout(poll, 3000);
        }
      } catch (_err) {
        setTimeout(poll, 3000);
      }
    };

    poll();
  }, [transactionId, navigate, formData, recipientName, pollUntilAdminDecision]);

  // 3. KHỞI TẠO 2 ỐNG WEBSOCKET ĐỘC LẬP (Sửa dependency & sử dụng staticTokenRef)
  useEffect(() => {
    const currentToken = staticTokenRef.current;
    console.log('🔌 [WS-INIT] Khởi tạo WebSocket với token:', currentToken);

    if (!currentToken) {
        toast.error("Thiếu mã xác thực phiên sinh trắc học!");
        return;
    }

    const faceSocket = new WebSocket(
      `ws://localhost:8081/ws/emotion-stream?bsToken=${currentToken}`
    );
    faceSocket.onopen = () => console.log('✅ Đã kết nối Socket Face!');
  faceSocket.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (data.type === "LIVE_RESULT" && data.status === "SUCCESS") {
      setLiveEmotion(data.emotion);
    } else if (data.type === "FINAL_RESULT") {
      // WS delivered — cancel the REST polling fallback
      if (ackTimerRef.current) {
        clearTimeout(ackTimerRef.current);
        ackTimerRef.current = null;
      }
      
      // Nếu là SUCCESS hoặc BLOCKED mới đóng, RETRY thì giữ nguyên
      if (data.status === "SUCCESS" || data.status === "BLOCKED") {
          if (wsVoiceRef.current) wsVoiceRef.current.close();
      }

      if (data.status === "SUCCESS") {
          setIsProcessing(false);
          toast.success("🎉 " + data.message);
          navigate('/transaction-result', { 
              state: { result: data.data, formData, recipientName } 
          });
      } 
      else if (data.status === "UNDER_REVIEW") {
          setIsFrozen(true);
          setIsUnderReview(true);
          pollUntilAdminDecision();
      }
      else if (data.status === "BLOCKED") {
          if (wsFaceRef.current) wsFaceRef.current.close();
          setIsProcessing(false);
          setIsFrozen(true);
          setStatus("🚫 " + data.message);
          toast.error("🚫 " + data.message);
          setTimeout(() => navigate('/dashboard'), 4000);
      }
      else if (data.status === "RETRY") {
          // Python closed its WS after each recognition — the Java↔Python bridge is dead.
          // Close the React↔Java voice WS so ensureVoiceSocketOpen creates a fresh bridge on the next attempt.
          if (wsVoiceRef.current && wsVoiceRef.current.readyState !== WebSocket.CLOSED) {
              wsVoiceRef.current.close();
          }
          // RESET UI để thử lại
          setIsProcessing(false);
          setIsRecording(false);
          setIsLivenessPassed(false);       // Bắt làm liveness lại từ đầu
          globalBlinkFlag.current = false;  // Reset cờ chớp mắt
          framesBuffer.current = [];        // Reset bộ đệm khung hình
          setStatus("❌ " + data.message + " — Hãy thực hiện lại!");
          toast.warning("⚠️ " + data.message);
      }
      else {
          setIsProcessing(false);
          setStatus("❌ TỪ CHỐI: " + data.message);
          toast.error("🚨 Lỗi: " + data.message);
      }
    }
  };
    wsFaceRef.current = faceSocket;

    const voiceSocket = new WebSocket(
      `ws://localhost:8081/ws/voice-stream?bsToken=${currentToken}`
    );
    voiceSocket.binaryType = 'arraybuffer'; 
    voiceSocket.onopen = () => console.log('✅ Đã kết nối Socket Voice (Binary)!');
    wsVoiceRef.current = voiceSocket;

    return () => {
      console.log('🧹 [WS-CLEANUP] Đóng các kết nối Socket cũ');
      if (wsFaceRef.current) wsFaceRef.current.close();
      if (wsVoiceRef.current) wsVoiceRef.current.close();
      if (ackTimerRef.current) {
        clearTimeout(ackTimerRef.current);
        ackTimerRef.current = null;
      }
      if (adminPollIntervalRef.current) {
        clearInterval(adminPollIntervalRef.current);
        adminPollIntervalRef.current = null;
      }
      sessionStorage.removeItem('bsToken');
    };
  }, []); // ← [] hoàn toàn, vì đã dùng ref cho token


  // =========================================================================
  // 🚀 BƯỚC SỬA QUAN TRỌNG: ĐƯA ensureVoiceSocketOpen LÊN TRƯỚC VÌ LUẬT HOISTING
  // =========================================================================
  const ensureVoiceSocketOpen = useCallback(() => {
    return new Promise((resolve, reject) => {
      if (wsVoiceRef.current?.readyState === WebSocket.OPEN) {
        console.log('✅ [ENSURE-VOICE] Socket đã mở sẵn, dùng luôn');
        resolve();
        return;
      }

      const activeToken = staticTokenRef.current;
      if (!activeToken) {
        console.error("❌ [ENSURE-VOICE] Token không tồn tại trong ref!");
        reject(new Error("Missing biometric session token"));
        return;
      }

      console.log(`♻️ [ENSURE-VOICE] Tiến hành reconnect ống Voice với token: ${activeToken}`);
      
      const voiceSocket = new WebSocket(
        `ws://localhost:8081/ws/voice-stream?bsToken=${activeToken}`
      );
      voiceSocket.binaryType = 'arraybuffer';

      voiceSocket.onopen = () => {
        console.log('✅ [ENSURE-VOICE] Nối ống Voice thành công!');
        wsVoiceRef.current = voiceSocket;
        resolve();
      };

      voiceSocket.onerror = (error) => {
        console.error('❌ [ENSURE-VOICE] Lỗi nối ống Voice:', error);
        reject(error);
      };
    });
  }, []); // Không cần deps vì chỉ truy cập qua Ref


  // =========================================================================
  // 🚀 verifyAtomicAllInOne KHAI BÁO SAU CÙNG (Có thể thấy ensureVoiceSocketOpen ngon lành)
  // =========================================================================
  const verifyAtomicAllInOne = useCallback(async () => {
    if (!webcamRef.current) return;
    
    setIsProcessing(true);
    setStatus("⏳ Đang thiết lập đường truyền bảo mật...");

    try {
        await ensureVoiceSocketOpen();
    } catch (e) {
        toast.error("Lỗi: Không thể kết nối đến Máy chủ Sinh trắc học!");
        setIsProcessing(false);
        return;
    }

    setIsRecording(true);
    setStatus("🔴 Đang ghi âm & quét mặt... Hãy đọc to mã số bên dưới!");
    
    let frameCount = 0;
    
    const streamInterval = setInterval(() => {
      if (webcamRef.current && webcamRef.current.video && wsFaceRef.current?.readyState === WebSocket.OPEN) {
        const canvas = webcamRef.current.getCanvas(); 

        if (canvas) {
          canvas.toBlob((blob) => {
            if (!blob || blob.size === 0) return;

            blob.arrayBuffer().then((buf) => {
              if (wsFaceRef.current && wsFaceRef.current.readyState === WebSocket.OPEN) {
                wsFaceRef.current.send(buf); 
              }
            });
          }, 'image/jpeg', 0.70); 
        }
      }
      frameCount++;
    }, 250); 

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

        setTimeout(() => {
            clearInterval(streamInterval);
            
            workletNode.disconnect();
            source.disconnect();
            audioContext.close();
            stream.getTracks().forEach(t => t.stop());
            URL.revokeObjectURL(workletUrl); 
            
            setIsRecording(false);
            setStatus("🤖 AI đang tổng hợp Khuôn mặt + Giọng nói...");

            if (wsFaceRef.current?.readyState === WebSocket.OPEN) {
                wsFaceRef.current.send(JSON.stringify({
                    action: "FINALIZE",
                    transactionId: transactionId
                }));
            }

            if (wsVoiceRef.current?.readyState === WebSocket.OPEN) {
                wsVoiceRef.current.send(new Int16Array(0).buffer);
            }

            // Start ACK timer — if WS FINAL_RESULT doesn't arrive within 20s after FINALIZE,
            // fall back to REST polling so a dropped socket never leaves the UI stuck.
            ackTimerRef.current = setTimeout(() => {
                ackTimerRef.current = null;
                pollVerificationStatus();
            }, 20000);

        }, 10000); 

    } catch (err) {
        console.error("Lỗi Mic:", err);
        toast.error("Lỗi truy cập Microphone!");
        setIsProcessing(false);
        setIsRecording(false);
    }
    
  }, [transactionId, ensureVoiceSocketOpen, pollVerificationStatus]);

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-4 relative overflow-hidden">
      <div className={`absolute top-0 left-0 w-full h-2 animate-pulse ${isFrozenResult ? 'bg-blue-500' : isRejectedByAdmin ? 'bg-red-500' : isUnderReview ? 'bg-amber-400' : isLivenessPassed ? 'bg-green-500' : 'bg-orange-500'}`}></div>

      <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl max-w-md w-full text-center relative z-10 border-4 border-slate-800">
        <h2 className="text-2xl font-black text-gray-900 mb-1 uppercase tracking-tight">Khu Vực An Ninh (HIGH RISK)</h2>
        <p className="text-xs font-bold text-gray-400 mb-6 uppercase tracking-widest">
          XÁC THỰC SINH TRẮC HỌC KÉP
        </p>
        
        {!isUnderReview && !isFrozenResult && !isRejectedByAdmin && (
          <p className={`text-sm font-bold mb-6 transition-colors ${isProcessing ? 'text-blue-500 animate-pulse' : isRecording ? 'text-red-500 animate-pulse' : status.includes('🚨') || status.includes('❌') ? 'text-red-600 animate-bounce' : status.includes('✅') ? 'text-green-600' : 'text-slate-600'}`}>
            {status}
          </p>
        )}

        {isRejectedByAdmin ? (
          /* MÀN HÌNH GIAO DỊCH BỊ TỪ CHỐI */
          <div className="flex flex-col items-center py-6">
            <div className="w-20 h-20 rounded-full bg-red-50 border-4 border-red-500 flex items-center justify-center mb-5">
              <svg className="w-10 h-10 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>

            <p className="text-base font-black text-red-700 mb-2 uppercase tracking-tight">Giao dịch bị từ chối</p>
            <p className="text-sm text-slate-500 text-center mb-5 leading-relaxed">
              Chuyên viên bảo mật đã xem xét và từ chối<br />
              giao dịch này. Tiền vẫn an toàn trong tài khoản.
            </p>

            <p className="text-xs text-slate-400 text-center mb-5 leading-relaxed">
              Mã giao dịch: <span className="font-bold text-slate-600">#{transactionId}</span><br />
              Liên hệ <span className="font-bold">1900 xxxx</span> nếu cần hỗ trợ.
            </p>

            <button
              onClick={() => navigate('/dashboard')}
              className="w-full py-3 rounded-2xl font-bold text-sm text-white bg-red-500 hover:bg-red-600 active:scale-95 transition-all"
            >
              ← Về trang chính
            </button>
          </div>
        ) : isFrozenResult ? (
          /* MÀN HÌNH GIAO DỊCH BỊ ĐÓNG BĂNG */
          <div className="flex flex-col items-center py-6">
            <div className="w-20 h-20 rounded-full bg-blue-50 border-4 border-blue-500 flex items-center justify-center mb-5">
              <svg className="w-10 h-10 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </div>

            <p className="text-base font-black text-blue-700 mb-2 uppercase tracking-tight">Giao dịch bị đóng băng</p>
            <p className="text-sm text-slate-500 text-center mb-5 leading-relaxed">
              Hệ thống bảo mật đã tạm giữ giao dịch này.<br />
              Tiền của bạn vẫn an toàn trong tài khoản.
            </p>

            <div className="w-full bg-blue-50 border border-blue-200 rounded-2xl px-4 py-3 mb-5 text-center">
              <p className="text-xs font-bold text-blue-600 uppercase tracking-widest mb-1">Liên hệ hỗ trợ khẩn cấp</p>
              <p className="text-lg font-black text-blue-800">1900 xxxx</p>
              <p className="text-xs text-slate-400 mt-0.5">24/7 — Miễn phí cước gọi</p>
            </div>

            <p className="text-xs text-slate-400 text-center leading-relaxed">
              Mã giao dịch: <span className="font-bold text-slate-600">#{transactionId}</span><br />
              Vui lòng cung cấp mã này khi liên hệ hỗ trợ.
            </p>

            <button
              onClick={() => navigate('/dashboard')}
              className="mt-5 w-full py-3 rounded-2xl font-bold text-sm text-blue-700 bg-blue-50 border border-blue-200 hover:bg-blue-100 active:scale-95 transition-all"
            >
              ← Về trang chính
            </button>
          </div>
        ) : isUnderReview ? (
          /* MÀN HÌNH CHỜ ADMIN DUYỆT */
          <div className="flex flex-col items-center py-6">
            <div className="w-20 h-20 rounded-full bg-amber-50 border-4 border-amber-400 flex items-center justify-center mb-5">
              <svg className="w-10 h-10 text-amber-500 animate-pulse" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
            </div>

            <p className="text-base font-black text-amber-600 mb-2 uppercase tracking-tight">Đang chờ kiểm duyệt bảo mật</p>
            <p className="text-sm text-slate-500 text-center mb-5 leading-relaxed">
              Hệ thống phát hiện dấu hiệu bất thường.<br />
              Giao dịch đang chờ chuyên viên xem xét.
            </p>

            <div className="flex gap-2 mb-5">
              <div className="w-2.5 h-2.5 bg-amber-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }}></div>
              <div className="w-2.5 h-2.5 bg-amber-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }}></div>
              <div className="w-2.5 h-2.5 bg-amber-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }}></div>
            </div>

            <p className="text-xs text-slate-400 text-center leading-relaxed">
              Trang này tự động cập nhật khi có kết quả.<br />
              Vui lòng không đóng ứng dụng.
            </p>
          </div>
        ) : (
          <>
            {/* KHUNG CAMERA */}
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

            {/* LỚP HIỂN THỊ MÃ OTP */}
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
          </>
        )}
      </div>
    </div>
  );
}