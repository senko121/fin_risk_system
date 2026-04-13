import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function Dashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);

  useEffect(() => {
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) setUser(JSON.parse(storedUser));
    else navigate('/login');
  }, [navigate]);

  if (!user) return null;

  return (
    <div className="min-h-screen bg-white">
      {/* Wrapper căn giữa giao diện làm việc */}
      <div className="max-w-5xl mx-auto bg-slate-50 min-h-screen shadow-[0_0_50px_rgba(0,0,0,0.05)] border-x border-gray-100">
        
        {/* Navbar nội bộ */}
        <nav className="bg-white border-b border-gray-100 px-8 py-4 flex justify-between items-center">
          <div className="flex items-center space-x-2">
            <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center text-white font-bold">F</div>
            <span className="text-lg font-black text-blue-900 tracking-tighter">FINRISK</span>
          </div>
          <div className="flex items-center space-x-4">
            <span className="text-sm font-bold text-gray-700">{user.fullName}</span>
            <button onClick={() => { localStorage.removeItem('currentUser'); navigate('/login'); }} className="p-2 text-gray-400 hover:text-red-500">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path></svg>
            </button>
          </div>
        </nav>

        <div className="p-8">
          <h2 className="text-xl font-bold text-gray-800 mb-6">Tài khoản chính</h2>
          
          {/* Card số dư */}
          <div className="bg-gradient-to-br from-blue-700 to-indigo-800 rounded-[2rem] p-10 text-white shadow-2xl mb-10 relative overflow-hidden">
            <div className="relative z-10">
              <p className="text-blue-200 text-xs uppercase tracking-[0.2em] font-bold mb-2">Số dư hiện tại</p>
              <h3 className="text-5xl font-black mb-10">
                {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(user.balance)}
              </h3>
              <div className="flex justify-between items-end">
                <div>
                  <p className="text-blue-300 text-[10px] uppercase font-bold mb-1">Số tài khoản</p>
                  <p className="font-mono text-xl tracking-widest">{user.accountNumber.replace(/(.{4})/g, '$1 ')}</p>
                </div>
                <div className="bg-white/20 px-4 py-2 rounded-xl backdrop-blur-md text-sm font-bold uppercase">
                  Global Class
                </div>
              </div>
            </div>
            <div className="absolute -bottom-20 -right-20 w-64 h-64 bg-white/10 rounded-full blur-3xl"></div>
          </div>

          {/* Nút chức năng */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 mb-10">
            <button onClick={() => navigate('/transfer')} className="flex items-center p-6 bg-white rounded-2xl shadow-sm border border-gray-50 hover:shadow-xl transition-all group">
              <div className="w-12 h-12 bg-blue-100 text-blue-600 rounded-xl flex items-center justify-center mr-4 group-hover:bg-blue-600 group-hover:text-white transition-all">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"></path></svg>
              </div>
              <div className="text-left">
                <p className="font-bold text-gray-800">Chuyển tiền</p>
                <p className="text-xs text-gray-400">Đánh giá rủi ro thời gian thực</p>
              </div>
            </button>
            
            <button className="flex items-center p-6 bg-white rounded-2xl shadow-sm border border-gray-50 opacity-60">
              <div className="w-12 h-12 bg-gray-100 text-gray-400 rounded-xl flex items-center justify-center mr-4">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"></path></svg>
              </div>
              <div className="text-left">
                <p className="font-bold text-gray-800">Bảo mật</p>
                <p className="text-xs text-gray-400">Cấu hình lớp xác thực</p>
              </div>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}