import React, { useRef, useState, useEffect, useCallback } from 'react';
import Webcam from 'react-webcam';
import { useNavigate } from 'react-router-dom';
import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';
import { toast } from 'react-toastify';
import axiosClient from '../../api/axiosClient';

const STEPS = [
  { label: 'Nhìn thẳng vào camera', icon: '😐', hint: 'Giữ mặt thẳng, ngay giữa khung hình' },
  { label: 'Quay đầu sang trái',    icon: '👈', hint: 'Quay nhẹ đầu sang phía trái của bạn' },
  { label: 'Quay đầu sang phải',    icon: '👉', hint: 'Quay nhẹ đầu sang phía phải của bạn' },
  { label: 'Ngẩng đầu lên',         icon: '⬆️', hint: 'Ngẩng nhẹ cằm lên trên' },
  { label: 'Cúi đầu xuống',         icon: '⬇️', hint: 'Cúi nhẹ đầu xuống dưới' },
];

export default function FaceRegister() {
  const webcamRef = useRef(null);
  const navigate  = useNavigate();

  const [faceLandmarker, setFaceLandmarker] = useState(null);
  const [isModelLoaded,  setIsModelLoaded]  = useState(false);
  const [isFaceDetected, setIsFaceDetected] = useState(false);
  const [isProcessing,   setIsProcessing]   = useState(false);
  const [isSubmitting,   setIsSubmitting]   = useState(false);

  const [step,           setStep]           = useState(0);   // 0-4
  const [capturedImages, setCapturedImages] = useState([]);  // base64 per angle

  const currentUser = JSON.parse(localStorage.getItem('currentUser'));

  // ── Guard: not logged in ───────────────────────────────────────────────────
  useEffect(() => {
    if (!currentUser) {
      toast.error('Vui lòng đăng nhập trước!');
      navigate('/login');
    }
  }, []);

  // ── 1. Init MediaPipe ──────────────────────────────────────────────────────
  useEffect(() => {
    const initAI = async () => {
      try {
        const filesetResolver = await FilesetResolver.forVisionTasks(
          'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.3/wasm'
        );
        const landmarker = await FaceLandmarker.createFromOptions(filesetResolver, {
          baseOptions: {
            modelAssetPath:
              'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task',
            delegate: 'GPU',
          },
          outputFaceBlendshapes: true,
          runningMode: 'VIDEO',
          numFaces: 1,
        });
        setFaceLandmarker(landmarker);
        setIsModelLoaded(true);
      } catch (err) {
        console.error(err);
        toast.error('Không thể tải AI Model. Vui lòng kiểm tra kết nối mạng!');
      }
    };
    initAI();
  }, []);

  // ── 2. Continuous face detection loop ─────────────────────────────────────
  useEffect(() => {
    let rafId;
    const detectFace = () => {
      if (webcamRef.current?.video && faceLandmarker) {
        const video = webcamRef.current.video;
        if (video.currentTime > 0) {
          const results = faceLandmarker.detectForVideo(video, performance.now());
          setIsFaceDetected(results.faceLandmarks?.length === 1);
        }
      }
      rafId = requestAnimationFrame(detectFace);
    };
    if (isModelLoaded && !isSubmitting) detectFace();
    return () => cancelAnimationFrame(rafId);
  }, [isModelLoaded, faceLandmarker, isSubmitting]);

  // ── 3. Capture frame from webcam (flip to correct mirror) ─────────────────
  const captureFrame = useCallback(() => {
    const video = webcamRef.current?.video;
    if (!video) return null;
    const canvas = document.createElement('canvas');
    canvas.width  = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext('2d');
    ctx.translate(canvas.width, 0);
    ctx.scale(-1, 1);
    ctx.drawImage(video, 0, 0);
    return canvas.toDataURL('image/jpeg', 0.95).split(',')[1];
  }, []);

  // ── 4. Submit batch to Spring Boot ─────────────────────────────────────────
  const submitBatch = useCallback(async (images) => {
    setIsSubmitting(true);
    try {
      await axiosClient.post('/users/register-face-batch', {
        userId:       currentUser.id || currentUser.userId,
        imagesBase64: images,
      });
      const updatedUser = { ...currentUser, isFaceSetup: true };
      localStorage.setItem('currentUser', JSON.stringify(updatedUser));
      toast.success(`🎉 Đăng ký thành công với ${images.length} góc!`);
      setTimeout(() => navigate('/dashboard'), 1000);
    } catch (error) {
      toast.error('Lỗi: ' + (error.response?.data || error.message));
      setIsSubmitting(false);
      setIsProcessing(false);
    }
  }, [currentUser, navigate]);

  // ── 5. Capture one angle, advance step or submit ───────────────────────────
  const captureStep = useCallback(async () => {
    setIsProcessing(true);
    const base64 = captureFrame();
    if (!base64) { setIsProcessing(false); return; }

    const newImages = [...capturedImages, base64];
    setCapturedImages(newImages);

    if (step < STEPS.length - 1) {
      setStep(s => s + 1);
      setIsProcessing(false);
    } else {
      await submitBatch(newImages);
    }
  }, [captureFrame, capturedImages, step, submitBatch]);

  if (!currentUser) return null;

  const allCaptured = capturedImages.length === STEPS.length;
  const canCapture  = isModelLoaded && isFaceDetected && !isProcessing && !isSubmitting && !allCaptured;
  const isLastStep  = step === STEPS.length - 1;
  const current     = STEPS[step] ?? STEPS[STEPS.length - 1];

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center p-4">
      <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl max-w-md w-full text-center relative">

        {/* Title */}
        <h2 className="text-2xl font-black text-gray-800 mb-1">Cài đặt FaceID</h2>
        <p className="text-xs text-gray-400 mb-5">
          {allCaptured
            ? 'Tất cả góc đã chụp — đang lưu...'
            : `Bước ${step + 1} / ${STEPS.length} — cần chụp ${STEPS.length} góc để đăng ký`}
        </p>

        {/* Step progress bar */}
        <div className="flex justify-center gap-2 mb-6">
          {STEPS.map((s, i) => (
            <div
              key={i}
              title={s.label}
              className={`transition-all duration-300 rounded-full h-3 ${
                i < capturedImages.length ? 'w-8 bg-green-500' :
                i === step && !allCaptured  ? 'w-8 bg-blue-500 animate-pulse' :
                'w-3 bg-gray-200'
              }`}
            />
          ))}
        </div>

        {/* Angle instruction */}
        {!allCaptured && (
          <div className="mb-5">
            <p className="text-4xl mb-1">{current.icon}</p>
            <p className="text-lg font-bold text-gray-800">{current.label}</p>
            <p className="text-sm text-gray-500 mt-1">{current.hint}</p>
          </div>
        )}

        {/* Webcam circle */}
        <div className={`relative w-64 h-64 mx-auto rounded-full overflow-hidden border-4 mb-5 shadow-lg transition-colors duration-300 ${
          isSubmitting    ? 'border-purple-500' :
          allCaptured     ? 'border-green-400' :
          isFaceDetected  ? 'border-green-500' :
          'border-slate-300'
        }`}>
          {isModelLoaded ? (
            <Webcam
              audio={false}
              ref={webcamRef}
              screenshotFormat="image/jpeg"
              videoConstraints={{ facingMode: 'user' }}
              className="w-full h-full object-cover transform scale-x-[-1]"
            />
          ) : (
            <div className="w-full h-full bg-slate-800 animate-pulse flex items-center justify-center">
              <span className="text-white text-xs tracking-widest animate-bounce">LOADING AI...</span>
            </div>
          )}

          {(isProcessing || isSubmitting) && (
            <div className="absolute inset-0 bg-blue-500/30 animate-pulse" />
          )}

          {/* Captured count badge */}
          {capturedImages.length > 0 && (
            <div className="absolute top-2 right-2 bg-green-500 text-white text-xs font-bold rounded-full w-7 h-7 flex items-center justify-center shadow">
              {capturedImages.length}/{STEPS.length}
            </div>
          )}
        </div>

        {/* Face detection status text */}
        <p className={`text-sm font-semibold mb-5 transition-colors ${
          isSubmitting   ? 'text-purple-500' :
          isProcessing   ? 'text-blue-500'   :
          allCaptured    ? 'text-green-600'  :
          isFaceDetected ? 'text-green-500'  :
          'text-orange-500'
        }`}>
          {isSubmitting   ? 'Đang lưu dữ liệu sinh trắc học...' :
           isProcessing   ? 'Đang xử lý...' :
           allCaptured    ? 'Tất cả góc đã chụp xong!' :
           isFaceDetected ? 'Đã nhận diện khuôn mặt — sẵn sàng chụp!' :
           'Chưa nhận diện khuôn mặt'}
        </p>

        {/* Angle checklist mini-thumbnails */}
        <div className="flex justify-center gap-2 mb-5">
          {STEPS.map((s, i) => (
            <div
              key={i}
              title={s.label}
              className={`w-9 h-9 rounded-xl border-2 flex flex-col items-center justify-center text-xs transition-all ${
                i < capturedImages.length
                  ? 'border-green-500 bg-green-50 text-green-600'
                  : i === step && !allCaptured
                  ? 'border-blue-400 bg-blue-50 text-blue-600 scale-110'
                  : 'border-gray-200 bg-gray-50 text-gray-400'
              }`}
            >
              {i < capturedImages.length ? (
                <span className="text-base">✓</span>
              ) : (
                <span className="text-base">{s.icon}</span>
              )}
            </div>
          ))}
        </div>

        {/* Primary capture button */}
        {!allCaptured && (
          <button
            onClick={captureStep}
            disabled={!canCapture}
            className={`w-full py-4 rounded-2xl font-bold text-white transition-all shadow-lg ${
              !canCapture
                ? 'bg-gray-300 cursor-not-allowed shadow-none'
                : isLastStep
                ? 'bg-green-600 hover:bg-green-700 active:scale-95 shadow-green-500/50'
                : 'bg-blue-600 hover:bg-blue-700 active:scale-95 shadow-blue-500/50'
            }`}
          >
            {isProcessing
              ? 'ĐANG XỬ LÝ...'
              : isLastStep
              ? `CHỤP & HOÀN TẤT (${step + 1}/${STEPS.length})`
              : `CHỤP GÓC NÀY (${step + 1}/${STEPS.length})`}
          </button>
        )}

        {/* Submitting state button (disabled) */}
        {isSubmitting && (
          <button disabled className="w-full py-4 rounded-2xl font-bold text-white bg-purple-400 cursor-not-allowed">
            ĐANG LƯU...
          </button>
        )}

        {/* Early submit — allow sending with ≥2 captured (backend minimum) */}
        {capturedImages.length >= 2 && !isLastStep && !isSubmitting && !allCaptured && (
          <button
            onClick={() => submitBatch(capturedImages)}
            className="mt-3 w-full py-2 rounded-xl text-sm font-semibold text-gray-400 hover:text-gray-700 hover:bg-gray-100 transition-colors"
          >
            Dùng {capturedImages.length} góc đã chụp →
          </button>
        )}

        {/* Retry on submit failure after all captured */}
        {allCaptured && !isSubmitting && (
          <button
            onClick={() => submitBatch(capturedImages)}
            className="w-full py-4 rounded-2xl font-bold text-white bg-green-600 hover:bg-green-700 active:scale-95 shadow-lg shadow-green-500/50 transition-all"
          >
            THỬ LẠI
          </button>
        )}
      </div>
    </div>
  );
}
