import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axiosClient from '../../api/axiosClient';

export default function AccountProfile() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [qrData, setQrData] = useState(null);
  const [loadingQR, setLoadingQR] = useState(true);

  useEffect(() => {
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      const parsedUser = JSON.parse(storedUser);
      setUser(parsedUser);
      fetchMyQR(parsedUser.id || parsedUser.userId);
    } else {
      navigate('/login');
    }
  }, [navigate]);

  const fetchMyQR = async (userId) => {
    try {
      const res = await axiosClient.get(`/users/${userId}/generate-qr`);
      setQrData(res.data.qrCodeBase64);
    } catch (error) {
      console.error("Lỗi lấy mã QR:", error);
    } finally {
      setLoadingQR(false);
    }
  };

  if (!user) return null;

  const formatMoney = (amount) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount || 0);
  };

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Nền Header tối giản */}
      <div className="h-48 bg-slate-900 rounded-b-[3rem] shadow-lg"></div>

      <div className="max-w-md mx-auto px-6 -mt-32 pb-12">
        {/* Nút quay lại */}
        <button 
          onClick={() => navigate(-1)} 
          className="mb-6 p-3 bg-white/10 hover:bg-white/20 rounded-2xl backdrop-blur-md text-white transition-all border border-white/10"
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 19l-7-7 7-7"></path>
          </svg>
        </button>

        {/* 2. KHỐI MÃ QR VÀ SỐ TÀI KHOẢN */}
        <div className="bg-white rounded-[2.5rem] p-8 shadow-xl border border-slate-100 flex flex-col items-center mb-8">
          <div className="text-center mb-6">
            <h3 className="text-lg font-black text-slate-800 uppercase tracking-tight">Mã QR của tôi</h3>
            <p className="text-slate-400 text-xs font-medium mt-1">Dùng để nhận tiền nhanh trong hệ thống</p>
          </div>

          <div className="p-4 bg-white border-2 border-slate-50 rounded-3xl shadow-inner relative group mb-4">
            {loadingQR ? (
              <div className="w-48 h-48 bg-slate-50 flex flex-col items-center justify-center rounded-2xl animate-pulse">
                <div className="w-6 h-6 border-4 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
              </div>
            ) : qrData ? (
              <img src={qrData} alt="My QR Code" className="w-48 h-48 object-contain" />
            ) : (
              <div className="w-48 h-48 flex items-center justify-center text-red-400 text-xs font-bold uppercase tracking-tighter text-center px-4">
                Lỗi tải mã QR
              </div>
            )}
          </div>

          {/* 🔥 SỐ TÀI KHOẢN ĐƯỢC CHÈN VÀO ĐÂY */}
          <div className="bg-blue-50/50 px-6 py-2 rounded-xl border border-blue-100 flex items-center space-x-3">
             <p className="text-[10px] font-black text-blue-400 uppercase tracking-widest">STK</p>
             <p className="font-mono text-lg tracking-widest font-black text-blue-700">
                {user.accountNumber ? String(user.accountNumber).replace(/(.{4})/g, '$1 ').trim() : 'N/A'}
             </p>
          </div>
        </div>

        {/* 3. THÔNG TIN CHỦ TÀI KHOẢN */}
        <div className="bg-white rounded-[2.5rem] p-8 shadow-sm border border-slate-50">
           <div className="space-y-6">
              <div className="flex items-center space-x-4">
                <div className="w-12 h-12 bg-blue-50 text-blue-600 rounded-2xl flex items-center justify-center font-black text-xl shadow-sm">
                  {user.fullName?.charAt(0)}
                </div>
                <div>
                  <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Chủ tài khoản</p>
                  <p className="font-black text-slate-800 text-lg uppercase leading-tight mt-0.5">{user.fullName}</p>
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 pt-4 border-t border-slate-50">
                 <div>
                    <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Số điện thoại</p>
                    <p className="text-sm font-bold text-slate-700 mt-1">{user.phoneNumber}</p>
                 </div>
                 <div>
                    <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Địa chỉ Email</p>
                    <p className="text-sm font-bold text-slate-700 mt-1 truncate">{user.email || 'Chưa cập nhật'}</p>
                 </div>
              </div>
           </div>
        </div>

      </div>
    </div>
  );
}