import React, { useRef, useState, useEffect } from 'react';
import Webcam from 'react-webcam';
import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';

export default function LivenessSandbox() {
  const webcamRef = useRef(null);
  const [isModelLoaded, setIsModelLoaded] = useState(false);
  const [faceLandmarker, setFaceLandmarker] = useState(null);
  
  const [status, setStatus] = useState("Đang khởi tạo AI Model...");
  const [isLive, setIsLive] = useState(null); 
  
  const [metrics, setMetrics] = useState({ yawDelta: 0, pitchDelta: 0, blinkScore: 0, ratioDelta: 0 });
  const [blinkDetected, setBlinkDetected] = useState(false);
  
  const globalBlinkFlag = useRef(false); 
  const sessionMax = useRef({ yaw: 0, pitch: 0, ratio: 0, blink: 0 }); 
  
  const [testLogs, setTestLogs] = useState([]);

  const framesBuffer = useRef([]);
  const MAX_FRAMES = 30; 

  useEffect(() => {
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
        setStatus("Lỗi tải AI Model.");
        console.error(err);
      }
    };
    initAI();
  }, []);

  useEffect(() => {
    let animationFrameId;

    const analyzeLiveness = () => {
      if (webcamRef.current && webcamRef.current.video && faceLandmarker) {
        const video = webcamRef.current.video;
        if (video.currentTime > 0) {
          const results = faceLandmarker.detectForVideo(video, performance.now());

          if (results.faceLandmarks && results.faceLandmarks.length > 0) {
            const landmarks = results.faceLandmarks[0];
            const nose = landmarks[1];
            const leftCheek = landmarks[234];
            const rightCheek = landmarks[454];
            const top = landmarks[10];
            const bottom = landmarks[152];

            const yaw = Math.atan2(rightCheek.z - leftCheek.z, rightCheek.x - leftCheek.x);
            const pitch = Math.atan2(bottom.z - top.z, bottom.y - top.y);

            // 🚀 CÔNG THỨC NORMALIZED MỚI: Bóp nghẹt lỗi chia cho 0
            const distLeft = Math.sqrt(Math.pow(leftCheek.x - nose.x, 2) + Math.pow(leftCheek.y - nose.y, 2));
            const distRight = Math.sqrt(Math.pow(rightCheek.x - nose.x, 2) + Math.pow(rightCheek.y - nose.y, 2));
            const totalDist = distLeft + distRight;
            
            // Ratio chuẩn hóa sẽ chỉ nằm trong [0, 1]
            const ratio = totalDist > 0 ? Math.abs(distLeft - distRight) / totalDist : 0;

            let currentBlinkScore = 0;
            if (results.faceBlendshapes && results.faceBlendshapes.length > 0) {
              const blendshapes = results.faceBlendshapes[0].categories;
              const eyeBlinkLeft = blendshapes.find(shape => shape.categoryName === "eyeBlinkLeft")?.score || 0;
              const eyeBlinkRight = blendshapes.find(shape => shape.categoryName === "eyeBlinkRight")?.score || 0;
              currentBlinkScore = Math.max(eyeBlinkLeft, eyeBlinkRight);
              
              if (currentBlinkScore > 0.45) { // Nâng nhẹ ngưỡng chớp mắt để lọc nhiễu ảnh rung
                globalBlinkFlag.current = true;
              }
            }

            framesBuffer.current.push({ yaw, pitch, ratio, blinkScore: currentBlinkScore });
            
            if (framesBuffer.current.length > MAX_FRAMES) {
              framesBuffer.current.shift(); 
            }

            if (framesBuffer.current.length === MAX_FRAMES) {
              const yaws = framesBuffer.current.map(f => f.yaw);
              const pitches = framesBuffer.current.map(f => f.pitch);
              const ratios = framesBuffer.current.map(f => f.ratio);

              const yawDelta = Math.abs(Math.max(...yaws) - Math.min(...yaws));
              const pitchDelta = Math.abs(Math.max(...pitches) - Math.min(...pitches));
              const ratioDelta = Math.abs(Math.max(...ratios) - Math.min(...ratios));

              sessionMax.current.yaw = Math.max(sessionMax.current.yaw, yawDelta);
              sessionMax.current.pitch = Math.max(sessionMax.current.pitch, pitchDelta);
              sessionMax.current.ratio = Math.max(sessionMax.current.ratio, ratioDelta);
              sessionMax.current.blink = Math.max(sessionMax.current.blink, currentBlinkScore);

              setMetrics({ 
                yawDelta: yawDelta.toFixed(4), 
                pitchDelta: pitchDelta.toFixed(4),
                blinkScore: currentBlinkScore.toFixed(2), 
                ratioDelta: ratioDelta.toFixed(4)
              });

              setBlinkDetected(currentBlinkScore > 0.45); 

              // ==========================================
              // ⚖️ LUẬT THÉP V2 (ĐÃ ĐIỀU CHỈNH TỪ DATA LOG)
              // ==========================================
              const THRESHOLD_RATIO = 0.08; // Ngưỡng an toàn cho công thức chuẩn hóa mới
              const THRESHOLD_SHAKE = 0.04; // Nới lỏng để mặt thật nhúc nhích không bị oan sai

              const hasBlinked = globalBlinkFlag.current;
              const is3D = ratioDelta >= THRESHOLD_RATIO; 
              const isShaking = yawDelta > THRESHOLD_SHAKE || pitchDelta > THRESHOLD_SHAKE;

              if (hasBlinked && is3D) {
                setIsLive(true);
                setStatus("✅ XÁC THỰC THÀNH CÔNG: 3D chuẩn + Đã chớp mắt!");
              } else if (isShaking && !is3D) {
                setIsLive(false);
                setStatus("🚨 BỊ CHẶN: Phát hiện cầm mặt phẳng (Ảnh/iPad) rung lắc!");
              } else if (hasBlinked && !is3D && !isShaking) {
                setIsLive(null);
                setStatus("⏳ Đã nhận diện chớp mắt. Vui lòng lắc nhẹ đầu sang trái/phải...");
              } else if (!hasBlinked && is3D) {
                setIsLive(null);
                setStatus("⏳ Đang lắc đầu 3D... Vui lòng chớp mắt 1 cái!");
              } else {
                setIsLive(null);
                setStatus("👀 Hãy nhìn thẳng camera, chớp mắt VÀ lắc nhẹ đầu...");
              }
            }
          } else {
            framesBuffer.current = [];
            setBlinkDetected(false);
          }
        }
      }
      animationFrameId = requestAnimationFrame(analyzeLiveness);
    };

    if (isModelLoaded) analyzeLiveness();
    return () => cancelAnimationFrame(animationFrameId);
  }, [isModelLoaded, faceLandmarker]);

  const handleSaveAndReset = (expectedType) => {
    const newRecord = {
      id: testLogs.length + 1,
      type: expectedType, 
      result: isLive === true ? "✅ PASS" : isLive === false ? "🚨 FAIL" : "⚠️ PENDING",
      maxRatio: sessionMax.current.ratio.toFixed(4),
      maxZDepth: Math.max(sessionMax.current.yaw, sessionMax.current.pitch).toFixed(4),
      maxBlink: sessionMax.current.blink.toFixed(2),
      time: new Date().toLocaleTimeString()
    };

    setTestLogs([newRecord, ...testLogs]);

    framesBuffer.current = [];
    globalBlinkFlag.current = false;
    sessionMax.current = { yaw: 0, pitch: 0, ratio: 0, blink: 0 };
    setIsLive(null);
    setStatus("🔄 Đã Reset. Bắt đầu test phiên mới...");
    setMetrics({ yawDelta: 0, pitchDelta: 0, blinkScore: 0, ratioDelta: 0 });
  };

  const handleHardReset = () => {
    framesBuffer.current = [];
    globalBlinkFlag.current = false;
    sessionMax.current = { yaw: 0, pitch: 0, ratio: 0, blink: 0 };
    setIsLive(null);
    setStatus("🔄 Đã xóa bộ nhớ. Làm lại từ đầu!");
    setMetrics({ yawDelta: 0, pitchDelta: 0, blinkScore: 0, ratioDelta: 0 });
  };

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col items-center py-10 px-4 font-sans">
      <div className="flex flex-col lg:flex-row gap-6 w-full max-w-5xl">
        
        {/* KHỐI CAMERA & METRICS */}
        <div className="bg-white rounded-3xl p-6 shadow-2xl flex-1 flex flex-col items-center">
          <h2 className="text-2xl font-black text-slate-800 mb-2">Data Collector V2</h2>
          <p className="text-xs text-slate-500 mb-4 font-bold uppercase tracking-widest">Normalized Math Edition</p>
          
          <div className={`relative w-72 h-72 mx-auto rounded-2xl overflow-hidden border-4 transition-colors duration-200 ${isLive === true ? 'border-green-500' : isLive === false ? 'border-red-500' : 'border-slate-300'}`}>
            {isModelLoaded ? (
              <Webcam
                audio={false}
                ref={webcamRef}
                screenshotFormat="image/jpeg"
                videoConstraints={{ facingMode: "user" }}
                className="w-full h-full object-cover transform scale-x-[-1]"
              />
            ) : (
              <div className="w-full h-full bg-slate-800 flex items-center justify-center">
                <span className="text-white text-sm animate-pulse">Loading AI...</span>
              </div>
            )}
            <div className={`absolute top-3 right-3 w-4 h-4 rounded-full transition-all duration-100 ${blinkDetected ? 'bg-blue-500 shadow-[0_0_15px_rgba(59,130,246,1)] scale-125' : 'bg-slate-400/50'}`}></div>
            {globalBlinkFlag.current && (
              <div className="absolute bottom-3 right-3 bg-black/60 text-white text-[10px] font-bold px-2 py-1 rounded-md flex items-center gap-1">
                <span>👁️</span> Đã chớp mắt
              </div>
            )}
          </div>

          <p className={`mt-4 text-sm font-bold min-h-[40px] flex items-center text-center px-4 ${isLive === true ? 'text-green-600' : isLive === false ? 'text-red-600' : 'text-slate-500'}`}>
            {status}
          </p>

          <div className="w-full mt-2 bg-slate-50 p-4 rounded-xl text-left font-mono text-sm text-slate-700 border border-slate-200">
            <p className="font-bold mb-2 uppercase text-xs text-slate-500 border-b pb-2">Ma Trận Chuẩn Hóa (0.5s):</p>
            <div className="flex justify-between items-center mb-1">
              <span>Ratio X (Ngưỡng 0.08):</span>
              <span className={metrics.ratioDelta >= 0.08 ? 'text-green-600 font-bold' : 'text-red-500 font-bold'}>{metrics.ratioDelta}</span>
            </div>
            <div className="flex justify-between items-center mb-1">
              <span>Z-Depth (Ngưỡng 0.04):</span>
              <span className={Math.max(metrics.yawDelta, metrics.pitchDelta) > 0.04 ? 'text-orange-500 font-bold' : 'text-slate-500'}>
                {Math.max(metrics.yawDelta, metrics.pitchDelta).toFixed(4)}
              </span>
            </div>
            <div className="flex justify-between items-center">
              <span>Blink (Ngưỡng 0.45):</span>
              <span className={metrics.blinkScore > 0.45 ? 'text-blue-600 font-bold' : 'text-slate-500'}>{metrics.blinkScore}</span>
            </div>
          </div>

          <div className="w-full mt-4 flex flex-col gap-2">
            <div className="flex gap-2">
              <button 
                onClick={() => handleSaveAndReset("Mặt Thật 🧑")}
                className="flex-1 bg-green-100 hover:bg-green-200 text-green-700 py-2 rounded-lg font-bold text-sm transition-colors border border-green-300"
              >
                💾 Lưu: Mặt Thật & Reset
              </button>
              <button 
                onClick={() => handleSaveAndReset("Ảnh Giả 🖼️")}
                className="flex-1 bg-red-100 hover:bg-red-200 text-red-700 py-2 rounded-lg font-bold text-sm transition-colors border border-red-300"
              >
                💾 Lưu: Ảnh Giả & Reset
              </button>
            </div>
            <button 
              onClick={handleHardReset}
              className="w-full bg-slate-200 hover:bg-slate-300 text-slate-700 py-2 rounded-lg font-bold text-sm transition-colors"
            >
              🔄 Xóa Bộ Nhớ (Bỏ qua Log)
            </button>
          </div>
        </div>

        {/* KHỐI BẢNG THỐNG KÊ LOGS */}
        <div className="bg-white rounded-3xl p-6 shadow-2xl flex-1 flex flex-col max-h-[600px]">
          <div className="flex justify-between items-end mb-4 border-b pb-2">
            <h2 className="text-xl font-black text-slate-800">📊 Data Log V2</h2>
            <span className="text-xs text-slate-500 font-bold bg-slate-100 px-2 py-1 rounded-md">Total: {testLogs.length} tests</span>
          </div>
          
          <div className="overflow-y-auto pr-2 custom-scrollbar">
            {testLogs.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-40 text-slate-400">
                <span className="text-3xl mb-2">📭</span>
                <p className="text-sm">Đã cập nhật công thức toán học. Test lại thôi bro!</p>
              </div>
            ) : (
              <div className="flex flex-col gap-3">
                {testLogs.map((log, idx) => (
                  <div key={idx} className="bg-slate-50 border border-slate-200 p-3 rounded-xl flex flex-col text-sm">
                    <div className="flex justify-between font-bold mb-2">
                      <span className="text-slate-700">#{log.id} - {log.type}</span>
                      <span className={log.result.includes("PASS") ? "text-green-600" : log.result.includes("FAIL") ? "text-red-600" : "text-yellow-600"}>
                        {log.result}
                      </span>
                    </div>
                    <div className="grid grid-cols-3 gap-2 text-xs font-mono">
                      <div className="bg-white p-2 rounded border">
                        <p className="text-slate-400">Max Ratio X</p>
                        <p className={parseFloat(log.maxRatio) >= 0.08 ? 'text-green-600 font-bold' : 'text-slate-700'}>{log.maxRatio}</p>
                      </div>
                      <div className="bg-white p-2 rounded border">
                        <p className="text-slate-400">Max Z-Depth</p>
                        <p className={parseFloat(log.maxZDepth) > 0.04 ? 'text-orange-500 font-bold' : 'text-slate-700'}>{log.maxZDepth}</p>
                      </div>
                      <div className="bg-white p-2 rounded border">
                        <p className="text-slate-400">Max Blink</p>
                        <p className={parseFloat(log.maxBlink) > 0.45 ? 'text-blue-600 font-bold' : 'text-slate-700'}>{log.maxBlink}</p>
                      </div>
                    </div>
                    <div className="text-right mt-1 text-[10px] text-slate-400 font-medium">Lúc: {log.time}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
          {testLogs.length > 0 && (
            <button 
              onClick={() => setTestLogs([])}
              className="mt-4 w-full bg-slate-100 hover:bg-slate-200 text-slate-500 py-2 rounded-lg font-bold text-xs transition-colors"
            >
              🗑️ Xóa toàn bộ lịch sử Log
            </button>
          )}
        </div>

      </div>
    </div>
  );
}