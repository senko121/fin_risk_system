import React, { useEffect, useState } from 'react';
import { toast } from 'react-toastify'; 
import axiosClient from '../api/axiosClient';

export default function MyQRCodeModal({ isOpen, onClose, user }) {
  const [qrData, setQrData] = useState(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (isOpen && user && !qrData) {
      fetchMyQR();
    }
  }, [isOpen, user]);

  const fetchMyQR = async () => {
    setIsLoading(true);
    try {
      const res = await axiosClient.get(`/users/${user.id || user.userId}/generate-qr`);
      setQrData(res.data.qrCodeBase64);
    } catch (error) {
      toast.error("❌ Lỗi khi tạo mã QR: " + (error.response?.data?.error || error.message));
      onClose(); 
    } finally {
      setIsLoading(false);
    }
  };

  // 🔥 Hàm hỗ trợ Tải ảnh QR về máy
  const handleDownloadQR = () => {
    if (!qrData) return;
    const link = document.createElement('a');
    link.href = qrData;
    link.download = `QR_${user.accountNumber}_FinRisk.png`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    toast.success("📸 Đã lưu mã QR về máy!");
  };

  if (!isOpen || !user) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/70 backdrop-blur-sm px-4 animate-fadeIn">
      <div className="bg-white w-full max-w-sm rounded-[2.5rem] p-6 shadow-2xl relative overflow-hidden">
        
        {/* Nền Decor mờ ảo */}
        <div className="absolute top-0 left-0 w-full h-32 bg-gradient-to-b from-blue-50 to-white opacity-50 pointer-events-none"></div>

        {/* Nút đóng */}
        <button 
          onClick={onClose}
          className="absolute top-4 right-4 p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700 rounded-full transition-colors z-10"
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12"></path></svg>
        </button>

        {/* Header */}
        <div className="text-center mb-6 mt-4 relative z-10">
          <div className="flex justify-center mb-2">
             <div className="w-10 h-10 bg-blue-600 rounded-xl flex items-center justify-center text-white font-black text-xl shadow-lg shadow-blue-200">
               F
             </div>
          </div>
          <h3 className="text-xl font-black text-slate-800 tracking-tight">Mã QR Nhận Tiền</h3>
          <p className="text-slate-500 text-sm mt-1 font-medium">FinRisk Pay</p>
        </div>

        {/* Khu vực chứa ảnh QR (Được viền lại siêu đẹp) */}
        <div className="flex justify-center mb-6 relative z-10">
          <div className="bg-white p-4 rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.08)] border border-slate-100 relative group">
             {/* 4 góc viền xanh kiểu Scan */}
             <div className="absolute top-0 left-0 w-6 h-6 border-t-4 border-l-4 border-blue-500 rounded-tl-3xl opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
             <div className="absolute top-0 right-0 w-6 h-6 border-t-4 border-r-4 border-blue-500 rounded-tr-3xl opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
             <div className="absolute bottom-0 left-0 w-6 h-6 border-b-4 border-l-4 border-blue-500 rounded-bl-3xl opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>
             <div className="absolute bottom-0 right-0 w-6 h-6 border-b-4 border-r-4 border-blue-500 rounded-br-3xl opacity-0 group-hover:opacity-100 transition-opacity duration-300"></div>

            {isLoading ? (
              <div className="w-48 h-48 bg-slate-50 flex flex-col items-center justify-center animate-pulse rounded-2xl">
                <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mb-2"></div>
                <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Đang tạo mã...</span>
              </div>
            ) : qrData ? (
              <img src={qrData} alt="My QR Code" className="w-48 h-48 object-contain rounded-xl" />
            ) : (
              <div className="w-48 h-48 bg-red-50 text-red-500 rounded-2xl flex items-center justify-center font-bold text-sm text-center px-4">
                Mã QR bị lỗi
              </div>
            )}
          </div>
        </div>

        {/* Thông tin User (Thiết kế dạng thẻ ngân hàng Mini) */}
        <div className="bg-slate-900 p-5 rounded-2xl relative overflow-hidden shadow-lg z-10">
          <div className="absolute -right-6 -top-6 w-24 h-24 bg-blue-500/20 rounded-full blur-2xl"></div>
          
          <div className="flex items-center justify-between mb-3">
             <p className="text-[10px] font-black uppercase text-slate-400 tracking-widest">Người thụ hưởng</p>
             <svg className="w-5 h-5 text-slate-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path></svg>
          </div>
          
          <p className="font-black text-white text-lg uppercase leading-tight truncate mb-1">
            {user.fullName}
          </p>
          <p className="text-blue-400 font-mono text-xl tracking-widest font-bold">
             {user.accountNumber ? String(user.accountNumber).replace(/(.{4})/g, '$1 ').trim() : 'N/A'}
          </p>
        </div>

        {/* Nút Hành động */}
        <button 
          onClick={handleDownloadQR}
          disabled={!qrData}
          className={`w-full mt-4 py-3.5 rounded-xl font-black text-sm uppercase tracking-wide transition-all flex items-center justify-center ${qrData ? 'bg-blue-50 text-blue-600 hover:bg-blue-600 hover:text-white' : 'bg-slate-100 text-slate-400 cursor-not-allowed'}`}
        >
          <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"></path></svg>
          Lưu ảnh QR
        </button>

      </div>
    </div>
  );
}