import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function UserSettings() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);

  useEffect(() => {
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      setUser(JSON.parse(storedUser));
    } else {
      navigate('/login');
    }
  }, [navigate]);

  if (!user) return null;

  return (
    <div className="min-h-screen bg-slate-50 relative pb-10">
      {/* Background Header */}
      <div className="absolute top-0 left-0 w-full h-56 bg-slate-900 rounded-b-[2.5rem]"></div>

      <div className="relative max-w-lg mx-auto px-4 pt-8">
        
        {/* Nút quay lại */}
        <div className="flex items-center text-white mb-6">
          <button onClick={() => navigate('/dashboard')} className="p-3 bg-white/10 hover:bg-white/20 rounded-2xl backdrop-blur-md transition-all">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 19l-7-7 7-7"></path></svg>
          </button>
          <h2 className="ml-4 text-lg font-black uppercase tracking-widest">Tài khoản của tôi</h2>
        </div>

        {/* Thẻ Profile Khách hàng */}
        <div className="bg-white rounded-[2rem] p-6 shadow-xl mb-6">
          <div className="flex items-center space-x-4 mb-6">
            <div className="w-16 h-16 bg-gradient-to-tr from-blue-600 to-indigo-500 text-white rounded-2xl flex items-center justify-center font-black text-2xl shadow-md">
              {user.fullName?.charAt(0)}
            </div>
            <div>
              <h3 className="text-xl font-black text-slate-800 uppercase">{user.fullName}</h3>
              <p className="text-sm font-mono text-slate-500 tracking-wider font-bold">{user.phoneNumber}</p>
            </div>
          </div>
          <div className="pt-4 border-t border-slate-100 flex justify-between items-center">
             <span className="text-xs font-bold text-slate-400 uppercase tracking-widest">Hạng Thành Viên</span>
             <span className="text-xs font-black text-blue-600 bg-blue-50 px-3 py-1 rounded-full uppercase tracking-widest">Premium</span>
          </div>
        </div>

        {/* Danh sách Menu Chức Năng */}
        <div className="bg-white rounded-[2rem] shadow-sm border border-slate-100 overflow-hidden mb-6">
          <h4 className="px-6 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest bg-slate-50 border-b border-slate-100">Bảo mật & Đăng nhập</h4>
          
          <button onClick={() => navigate('/change-password')} className="w-full px-6 py-5 flex items-center justify-between hover:bg-slate-50 transition-colors border-b border-slate-50 group">
             <div className="flex items-center">
                <div className="w-10 h-10 bg-orange-50 text-orange-500 rounded-xl flex items-center justify-center mr-4">
                   <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"></path></svg>
                </div>
                <div className="text-left">
                   <p className="font-bold text-slate-800">Đổi mật khẩu</p>
                   <p className="text-xs text-slate-500 mt-0.5">Thay đổi mật khẩu đăng nhập</p>
                </div>
             </div>
             <svg className="w-5 h-5 text-slate-300 group-hover:text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 5l7 7-7 7"></path></svg>
          </button>

          <button onClick={() => navigate('/register-face')} className="w-full px-6 py-5 flex items-center justify-between hover:bg-slate-50 transition-colors border-b border-slate-50 group">
             <div className="flex items-center">
                <div className="w-10 h-10 bg-indigo-50 text-indigo-500 rounded-xl flex items-center justify-center mr-4">
                   <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path></svg>
                </div>
                <div className="text-left">
                   <p className="font-bold text-slate-800">Cập nhật FaceID KYC</p>
                   <p className="text-xs text-slate-500 mt-0.5">Đăng ký lại khuôn mặt xác thực</p>
                </div>
             </div>
             <svg className="w-5 h-5 text-slate-300 group-hover:text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 5l7 7-7 7"></path></svg>
          </button>

          <button className="w-full px-6 py-5 flex items-center justify-between hover:bg-slate-50 transition-colors group">
             <div className="flex items-center">
                <div className="w-10 h-10 bg-emerald-50 text-emerald-500 rounded-xl flex items-center justify-center mr-4">
                   <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z"></path></svg>
                </div>
                <div className="text-left">
                   <p className="font-bold text-slate-800">Quản lý thiết bị</p>
                   <p className="text-xs text-slate-500 mt-0.5">Xem các thiết bị đã đăng nhập</p>
                </div>
             </div>
             <span className="text-[9px] font-black uppercase bg-slate-100 text-slate-400 px-2 py-1 rounded">Sắp có</span>
          </button>
        </div>

        {/* Menu Khác */}
        <div className="bg-white rounded-[2rem] shadow-sm border border-slate-100 overflow-hidden">
           <h4 className="px-6 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest bg-slate-50 border-b border-slate-100">Thông tin chung</h4>
           <button className="w-full px-6 py-5 flex items-center justify-between hover:bg-slate-50 transition-colors border-b border-slate-50 group">
             <div className="flex items-center">
                <div className="text-left">
                   <p className="font-bold text-slate-800">Cập nhật thông tin cá nhân</p>
                   <p className="text-xs text-slate-500 mt-0.5">Email, CCCD, Địa chỉ</p>
                </div>
             </div>
             <span className="text-[9px] font-black uppercase bg-slate-100 text-slate-400 px-2 py-1 rounded">Sắp có</span>
          </button>
        </div>

      </div>
    </div>
  );
}