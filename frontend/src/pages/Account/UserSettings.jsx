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


        {/* 🔥 3. DANH SÁCH MENU TIỆN ÍCH MỚI SIÊU XỊN */}
        <div className="bg-white rounded-[2.5rem] shadow-sm border border-slate-100 overflow-hidden">
           <h4 className="px-8 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest bg-slate-50/50 border-b border-slate-100">
             Cài đặt & Tiện ích
           </h4>

         {/* Nút 1: Chi tiết thẻ (Đã gắn Link) */}
           <button 
             onClick={() => navigate('/card-details')} 
             className="w-full px-6 py-5 flex items-center justify-between hover:bg-slate-50 transition-colors border-b border-slate-50 group cursor-pointer"
           >
             <div className="flex items-center">
                <div className="w-10 h-10 bg-slate-100 text-slate-700 rounded-xl flex items-center justify-center mr-4 group-hover:bg-slate-800 group-hover:text-white transition-colors">
                   <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path></svg>
                </div>
                <div className="text-left">
                   <p className="font-bold text-slate-800">Thông tin chi tiết thẻ</p>
                   <p className="text-xs text-slate-500 mt-0.5">Xem thẻ, số dư và mã QR</p>
                </div>
             </div>
             {/* Thay chữ Sắp Có bằng mũi tên */}
             <svg className="w-5 h-5 text-slate-300 group-hover:text-blue-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 5l7 7-7 7"></path></svg>
           </button>

           {/* Nút 2: Cập nhật cá nhân */}
           <button className="w-full px-8 py-5 flex items-center justify-between hover:bg-slate-50 transition-colors border-b border-slate-50 group">
             <div className="flex items-center">
                <div className="w-10 h-10 bg-blue-50 text-blue-600 rounded-xl flex items-center justify-center mr-4 group-hover:bg-blue-600 group-hover:text-white transition-colors">
                   <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path></svg>
                </div>
                <div className="text-left">
                   <p className="font-bold text-slate-800">Cập nhật thông tin cá nhân</p>
                   <p className="text-xs text-slate-500 mt-0.5">Email, số điện thoại, CCCD</p>
                </div>
             </div>
             <svg className="w-5 h-5 text-slate-300 group-hover:text-blue-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 5l7 7-7 7"></path></svg>
           </button>

           {/* Nút 3: Danh bạ thụ hưởng */}
           <button className="w-full px-8 py-5 flex items-center justify-between hover:bg-slate-50 transition-colors border-b border-slate-50 group">
             <div className="flex items-center">
                <div className="w-10 h-10 bg-emerald-50 text-emerald-600 rounded-xl flex items-center justify-center mr-4 group-hover:bg-emerald-600 group-hover:text-white transition-colors">
                   <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"></path></svg>
                </div>
                <div className="text-left">
                   <p className="font-bold text-slate-800">Danh bạ thụ hưởng</p>
                   <p className="text-xs text-slate-500 mt-0.5">Quản lý người nhận chuyển tiền</p>
                </div>
             </div>
             <svg className="w-5 h-5 text-slate-300 group-hover:text-blue-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 5l7 7-7 7"></path></svg>
           </button>

           {/* Nút 4: Biểu phí & Hạn mức */}
           <button className="w-full px-8 py-5 flex items-center justify-between hover:bg-slate-50 transition-colors group border-b border-slate-50">
             <div className="flex items-center">
                <div className="w-10 h-10 bg-orange-50 text-orange-500 rounded-xl flex items-center justify-center mr-4 group-hover:bg-orange-500 group-hover:text-white transition-colors">
                   <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                </div>
                <div className="text-left">
                   <p className="font-bold text-slate-800">Biểu phí & Hạn mức</p>
                   <p className="text-xs text-slate-500 mt-0.5">Quy định và giới hạn giao dịch</p>
                </div>
             </div>
             <svg className="w-5 h-5 text-slate-300 group-hover:text-blue-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 5l7 7-7 7"></path></svg>
           </button>

           {/* Nút 5: Trung tâm hỗ trợ */}
           <button className="w-full px-8 py-5 flex items-center justify-between hover:bg-slate-50 transition-colors group">
             <div className="flex items-center">
                <div className="w-10 h-10 bg-purple-50 text-purple-600 rounded-xl flex items-center justify-center mr-4 group-hover:bg-purple-600 group-hover:text-white transition-colors">
                   <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M18.364 5.636l-3.536 3.536m0 5.656l3.536 3.536M9.172 9.172L5.636 5.636m3.536 9.192l-3.536 3.536M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-5 0a4 4 0 11-8 0 4 4 0 018 0z"></path></svg>
                </div>
                <div className="text-left">
                   <p className="font-bold text-slate-800">Trung tâm hỗ trợ</p>
                   <p className="text-xs text-slate-500 mt-0.5">Liên hệ CSKH 24/7</p>
                </div>
             </div>
             <svg className="w-5 h-5 text-slate-300 group-hover:text-blue-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 5l7 7-7 7"></path></svg>
           </button>
        </div>

      </div>
    </div>
  );
}