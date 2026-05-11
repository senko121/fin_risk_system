import { useState, useCallback } from 'react';

// Bro có thể thử đổi maxFrames = 10, intervalMs = 500 (chụp 10 tấm, mỗi tấm cách 0.5s) để xem AI nhảy mượt hơn nhé!
export default function useFrameCapture(webcamRef, maxFrames = 5, intervalMs = 1000) {
  const [isCapturing, setIsCapturing] = useState(false);

  // 🚀 NÂNG CẤP: Nhận thêm 1 hàm onFrameCaptured từ bên ngoài truyền vào
  const captureFrames = useCallback((onFrameCaptured) => {
    return new Promise((resolve) => {
      setIsCapturing(true);
      const captured = [];
      let count = 0;

      const timer = setInterval(() => {
        if (webcamRef.current) {
          const frame = webcamRef.current.getScreenshot();
          if (frame) {
            const base64Data = frame.split(',')[1];
            captured.push(base64Data);
            
            // 🚀 BƠM TRỰC TIẾP RA NGOÀI: Hễ có ảnh là ném ra ngay lập tức
            if (onFrameCaptured) {
                onFrameCaptured(base64Data);
            }
          }
        }
        
        count++;
        if (count >= maxFrames) {
          clearInterval(timer);
          setIsCapturing(false);
          resolve(captured);
        }
      }, intervalMs);
    });
  }, [webcamRef, maxFrames, intervalMs]);

  return { captureFrames, isCapturing };
}