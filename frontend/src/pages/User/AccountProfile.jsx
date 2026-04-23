import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import axiosClient from '../api/axiosClient';

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

      <div className="max-w-md mx-auto px-6 -mt-32">
        {/* Nút quay lại */}
        <button 
          onClick={() => navigate(-1)} 
          className="mb-6 p-3 bg-white/10 hover:bg-white/20 rounded-2xl backdrop-blur-md text-white transition-all border border-white/10"
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 19l-7-7 7-7"></path>
          </svg>
        </button>

        {/* 1. THẺ NGÂN HÀNG PREMIUM */}
        <div className="bg-gradient-to-br from-slate-800 to-black rounded-[2.5rem] p-8 text-white shadow-2xl relative overflow-hidden mb-8">
          <div className="absolute -right-10 -top-10 w-40 h-40 bg-blue-500/10 rounded-full blur-3xl"></div>
          
          <div className="relative z-10 flex justify-between items-center mb-10">
            <div className="flex items-center space-x-2">
               <div className="w-8 h-6 bg-yellow-500/80 rounded-sm shadow-inner"></div> {/* Giả lập chip */}
               <span className="text-xs font-black tracking-tighter opacity-60 italic">FINRISK PLATINUM</span>
            </div>
            <div className="text-right">
              <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest">Global Class</p>
            </div>
          </div>

          <div className="relative z-10 mb-8">
             <p className="text-white/40 text-[10px] uppercase font-bold tracking-widest mb-1">Số tài khoản</p>
             <p className="font-mono text-2xl tracking-[0.2em] text-blue-100">
                {user.accountNumber ? String(user.accountNumber).replace(/(.{4})/g, '$1 ').trim() : '#### #### ####'}
             </p>
          </div>

          <div className="relative z-10 flex justify-between items-end">
             <div>
               <p className="text-white/40 text-[10px] uppercase font-bold tracking-widest mb-1">Số dư khả dụng</p>
               <p className="text-2xl font-black tracking-tight">{formatMoney(user.balance)}</p>
             </div>
          </div>
        </div>

        {/* 2. KHỐI MÃ QR TÍCH HỢP SẴN TRÊN PAGE */}
        <div className="bg-white rounded-[2.5rem] p-8 shadow-xl border border-slate-100 flex flex-col items-center mb-8">
          <div className="text-center mb-6">
            <h3 className="text-lg font-black text-slate-800 uppercase tracking-tight">Mã QR của tôi</h3>
            <p className="text-slate-400 text-xs font-medium mt-1">Dùng để nhận tiền nhanh trong hệ thống</p>
          </div>

          <div className="p-4 bg-white border-2 border-slate-50 rounded-3xl shadow-inner relative group">
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
        </div>

        {/* 3. THÔNG TIN CHỦ TÀI KHOẢN */}
        <div className="bg-white rounded-[2.5rem] p-8 shadow-sm border border-slate-50">
           <div className="space-y-6">
              <div className="flex items-center space-x-4">
                <div className="w-12 h-12 bg-blue-50 text-blue-600 rounded-2xl flex items-center justify-center font-black text-xl">
                  {user.fullName?.charAt(0)}
                </div>
                <div>
                  <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Chủ tài khoản</p>
                  <p className="font-black text-slate-800 text-lg uppercase">{user.fullName}</p>
                </div>
              </div>

              <div className="grid grid-cols-1 gap-4 pt-4 border-t border-slate-50">
                 <div>
                    <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Số điện thoại</p>
                    <p className="text-sm font-bold text-slate-700 mt-1">{user.phoneNumber}</p>
                 </div>
                 <div>
                    <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Địa chỉ Email</p>
                    <p className="text-sm font-bold text-slate-700 mt-1">{user.email || 'Chưa cập nhật'}</p>
                 </div>
              </div>
           </div>
        </div>
      </div>
    </div>
  );
}