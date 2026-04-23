import React, { useEffect, useRef, useState } from 'react';
import { Html5Qrcode } from 'html5-qrcode';
import { toast } from 'react-toastify';

export default function QRCodeScanner({ onScanSuccess, onScanCancel }) {
  const html5QrCodeRef = useRef(null);
  const fileInputRef = useRef(null);
  const [isCameraActive, setIsCameraActive] = useState(false);

  useEffect(() => {
    let isMounted = true; // 🔥 CỜ SỐ 1: Bắt lỗi bấm tắt quá nhanh
    const html5QrCode = new Html5Qrcode("finrisk-qr-viewport");
    html5QrCodeRef.current = html5QrCode;

    const startCamera = async () => {
      try {
        await html5QrCode.start(
          { facingMode: "environment" },
          { fps: 10, qrbox: { width: 250, height: 250 }, aspectRatio: 1.0 },
          (decodedText) => {
            // Quét xong -> Tắt máy ảnh cẩn thận rồi mới truyền data ra ngoài
            if (html5QrCodeRef.current?.isScanning) {
              html5QrCodeRef.current.stop().then(() => {
                onScanSuccess(decodedText);
              }).catch(err => console.error(err));
            }
          },
          (errorMessage) => {} // Bỏ qua lỗi focus
        );

        // 🔥 NẾU USER BẤM "HỦY" TRONG LÚC ĐANG KHỞI ĐỘNG CAMERA
        if (!isMounted) {
           await html5QrCode.stop();
           html5QrCode.clear();
        } else {
           setIsCameraActive(true);
        }
      } catch (err) {
        if (isMounted) {
           console.error("Lỗi khởi động camera:", err);
           toast.warning("⚠️ Không thể tự bật Camera. Vui lòng tải ảnh QR lên!");
        }
      }
    };

    startCamera();

    // Hàm Cleanup khi Component bị gỡ khỏi DOM (VD: Chuyển trang đột ngột)
    return () => {
      isMounted = false;
      if (html5QrCodeRef.current?.isScanning) {
        html5QrCodeRef.current.stop()
          .then(() => html5QrCodeRef.current.clear())
          .catch(err => console.error("Lỗi khi tắt QR:", err));
      }
    };
  }, [onScanSuccess]);

  const handleFileUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    try {
      const decodedText = await html5QrCodeRef.current.scanFile(file, true);
      // 🔥 TẮT CAMERA NẾU ĐANG BẬT TRƯỚC KHI TRẢ KẾT QUẢ
      if (html5QrCodeRef.current?.isScanning) {
        await html5QrCodeRef.current.stop();
      }
      onScanSuccess(decodedText);
    } catch (err) {
      toast.error("❌ Không tìm thấy hoặc không thể đọc mã QR trong ảnh này!");
      e.target.value = '';
    }
  };

  // 🔥 CỜ SỐ 2: Ép tắt Camera triệt để TRƯỚC KHI đóng giao diện Modal
  const handleClose = async () => {
    if (html5QrCodeRef.current?.isScanning) {
       try {
         await html5QrCodeRef.current.stop();
         html5QrCodeRef.current.clear();
       } catch(e) { console.error("Lỗi tắt thủ công", e); }
    }
    // Chờ camera ngắt kết nối rồi mới báo React đóng Modal
    onScanCancel();
  };

  return (
    <div className="relative bg-white p-6 rounded-[2.5rem] w-full max-w-sm mx-auto">
      <h3 className="text-center font-black text-slate-800 mb-6 uppercase tracking-wider text-sm">
        Quét QR Nhận Tiền
      </h3>

      <div className="relative rounded-3xl overflow-hidden border-4 border-slate-100 bg-slate-900 aspect-square flex items-center justify-center shadow-inner">
        {!isCameraActive && (
          <div className="absolute z-10 flex flex-col items-center">
             <div className="animate-spin w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full mb-3"></div>
             <p className="text-white text-xs font-bold tracking-widest animate-pulse">ĐANG BẬT CAMERA...</p>
          </div>
        )}
        
        <div id="finrisk-qr-viewport" className="w-full h-full absolute inset-0 [&>video]:object-cover"></div>
        
        {isCameraActive && (
           <div className="absolute inset-0 z-20 pointer-events-none flex items-center justify-center">
              <div className="w-48 h-48 border-2 border-white/50 rounded-2xl shadow-[0_0_0_999px_rgba(0,0,0,0.5)]">
                 <div className="absolute top-0 left-0 w-8 h-8 border-t-4 border-l-4 border-blue-500 rounded-tl-xl -mt-1 -ml-1"></div>
                 <div className="absolute top-0 right-0 w-8 h-8 border-t-4 border-r-4 border-blue-500 rounded-tr-xl -mt-1 -mr-1"></div>
                 <div className="absolute bottom-0 left-0 w-8 h-8 border-b-4 border-l-4 border-blue-500 rounded-bl-xl -mb-1 -ml-1"></div>
                 <div className="absolute bottom-0 right-0 w-8 h-8 border-b-4 border-r-4 border-blue-500 rounded-br-xl -mb-1 -mr-1"></div>
                 <div className="w-full h-0.5 bg-blue-500 shadow-[0_0_10px_#3b82f6] absolute top-1/2 animate-[scan_2s_ease-in-out_infinite]"></div>
              </div>
           </div>
        )}
      </div>

      <div className="mt-8 space-y-3">
        <button 
          onClick={() => fileInputRef.current.click()}
          className="w-full py-4 bg-blue-50 hover:bg-blue-100 text-blue-700 font-black tracking-wide rounded-2xl transition-all flex items-center justify-center"
        >
          <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"></path></svg>
          TẢI ẢNH TỪ THƯ VIỆN
        </button>
        <input 
          type="file" 
          ref={fileInputRef} 
          onChange={handleFileUpload} 
          accept="image/*" 
          className="hidden" 
        />

        {/* Đã gọi handleClose thay vì onScanCancel trực tiếp */}
        <button 
          onClick={handleClose}
          className="w-full py-4 bg-slate-100 hover:bg-slate-200 text-slate-600 font-bold rounded-2xl transition-all"
        >
          Hủy & Đóng
        </button>
      </div>

      <style dangerouslySetInnerHTML={{__html: `
        @keyframes scan {
          0% { transform: translateY(-100px); }
          50% { transform: translateY(100px); }
          100% { transform: translateY(-100px); }
        }
      `}} />
    </div>
  );
}