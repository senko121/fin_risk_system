import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify'; 
import MyQRCodeModal from '../../components/MyQRCodeModal';

export default function Dashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [isQRModalOpen, setIsQRModalOpen] = useState(false);

  useEffect(() => {
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      setUser(JSON.parse(storedUser));
    } else {
      handleForceLogout("Bạn chưa đăng nhập!");
    }
  }, [navigate]);

  const handleForceLogout = (message) => {
      toast.error("🚨 " + message);
      localStorage.removeItem('currentUser'); 
      localStorage.removeItem('accessToken'); 
      localStorage.removeItem('refreshToken'); 
      navigate('/login');
  };

  if (!user) return null;

  const formatMoney = (amount) => {
    if (amount == null || isNaN(amount)) return "0 ₫"; 
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
  };

  return (
    <div className="min-h-screen bg-white relative">
      <div className="max-w-5xl mx-auto bg-slate-50 min-h-screen shadow-[0_0_50px_rgba(0,0,0,0.05)] border-x border-gray-100">
        
        {/* Navbar */}
        <nav className="bg-white border-b border-gray-100 px-8 py-4 flex justify-between items-center">
          <div className="flex items-center space-x-2">
            <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center text-white font-bold">F</div>
            <span className="text-lg font-black text-blue-900 tracking-tighter">FINRISK</span>
          </div>
{/* CẬP NHẬT NAVBAR TRONG DASHBOARD.JSX */}
          <div className="flex items-center space-x-3">
            
            {/* NÚT AVATAR TRÒN MỚI */}
            <button 
              onClick={() => navigate('/account')} 
              className="flex items-center space-x-2 px-2 py-1.5 hover:bg-slate-100 rounded-[1.25rem] transition-colors group cursor-pointer"
            >
              {/* Avatar lấy chữ cái đầu */}
              <div className="w-8 h-8 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center font-black text-sm border-2 border-transparent group-hover:border-blue-200 transition-all">
                {user.fullName?.charAt(0)}
              </div>
              <span className="text-sm font-bold text-gray-700 hidden sm:block">{user.fullName}</span>
            </button>

            {/* Nút đăng xuất (Giữ nguyên) */}
            <button onClick={() => handleForceLogout("Đã đăng xuất an toàn!")} className="p-2 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-full transition-all">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path></svg>
            </button>
          </div>
        </nav>

        <div className="p-8">
          <h2 className="text-xl font-bold text-gray-800 mb-6">Tài khoản chính</h2>
          
          {/* Card số dư */}
          <div className="bg-gradient-to-br from-blue-700 to-indigo-800 rounded-[2rem] p-10 text-white shadow-2xl mb-10 relative overflow-hidden group">
            <div className="relative z-10">
              
              {/* 🔥 BÍ QUYẾT NẰM Ở ĐÂY NÈ BRO: Bọc chữ "Số dư" và "Nút QR" vào chung 1 cái Flex nằm ngang */}
              <div className="flex justify-between items-start mb-2">
                <p className="text-blue-200 text-xs uppercase tracking-[0.2em] font-bold">Số dư hiện tại</p>
                
                {/* Đây là cái nút bật QR */}
                <button 
                  onClick={() => setIsQRModalOpen(true)}
                  className="bg-white/20 hover:bg-white/30 backdrop-blur-md p-2 rounded-xl transition-all transform hover:scale-105 active:scale-95"
                  title="Hiển thị mã QR nhận tiền"
                >
                  <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z"></path>
                  </svg>
                </button>
              </div>
              {/* 🔥 KẾT THÚC BÍ QUYẾT */}

              <h3 className="text-5xl font-black mb-10">
                {formatMoney(user.balance)} 
              </h3>
              <div className="flex justify-between items-end">
                <div>
                  <p className="text-blue-300 text-[10px] uppercase font-bold mb-1">Số tài khoản</p>
                  <p className="font-mono text-xl tracking-widest">{user.accountNumber ? String(user.accountNumber).replace(/(.{4})/g, '$1 ').trim() : 'Đang cập nhật'}</p>
                </div>
                <div className="bg-white/20 px-4 py-2 rounded-xl backdrop-blur-md text-sm font-bold uppercase">
                  Global Class
                </div>
              </div>
            </div>
            <div className="absolute -bottom-20 -right-20 w-64 h-64 bg-white/10 rounded-full blur-3xl group-hover:bg-white/20 transition-colors duration-500"></div>
          </div>

          {/* CÁC NÚT ĐIỀU HƯỚNG CHÍNH */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 mb-10">
            <button onClick={() => navigate('/transfer')} className="flex flex-col items-center p-6 bg-white rounded-2xl shadow-sm border border-gray-50 hover:shadow-xl transition-all group">
              <div className="w-14 h-14 bg-blue-50 text-blue-600 rounded-2xl flex items-center justify-center mb-4 group-hover:bg-blue-600 group-hover:text-white transition-all">
                <svg className="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"></path></svg>
              </div>
              <p className="font-bold text-gray-800">Chuyển tiền</p>
            </button>

            <button onClick={() => navigate('/history')} className="flex flex-col items-center p-6 bg-white rounded-2xl shadow-sm border border-gray-50 hover:shadow-xl transition-all group">
              <div className="w-14 h-14 bg-emerald-50 text-emerald-600 rounded-2xl flex items-center justify-center mb-4 group-hover:bg-emerald-600 group-hover:text-white transition-all">
                <svg className="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01"></path></svg>
              </div>
              <p className="font-bold text-gray-800">Lịch sử GD</p>
            </button>

            <button onClick={() => navigate('/analytics')} className="flex flex-col items-center p-6 bg-white rounded-2xl shadow-sm border border-gray-50 hover:shadow-xl transition-all group">
              <div className="w-14 h-14 bg-purple-50 text-purple-600 rounded-2xl flex items-center justify-center mb-4 group-hover:bg-purple-600 group-hover:text-white transition-all">
                <svg className="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"></path></svg>
              </div>
              <p className="font-bold text-gray-800 text-sm">Chi tiêu</p>
            </button>

            <button onClick={() => navigate('/security')} className="flex flex-col items-center p-6 bg-white rounded-2xl shadow-sm border border-gray-50 hover:shadow-xl hover:-translate-y-1 transition-all group">
              <div className="w-14 h-14 bg-slate-50 text-slate-600 rounded-2xl flex items-center justify-center mb-4 group-hover:bg-slate-800 group-hover:text-white transition-all">
                <svg className="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"></path></svg>
              </div>
              <p className="font-bold text-gray-800 text-sm">Bảo mật</p>
            </button>
            
            <button className="flex flex-col items-center p-6 bg-white rounded-2xl shadow-sm border border-gray-50 opacity-60">
              <div className="w-14 h-14 bg-gray-50 text-gray-500 rounded-2xl flex items-center justify-center mb-4">
                <svg className="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"></path></svg>
              </div>
              <p className="font-bold text-gray-800">Comming soon</p>
            </button>
          </div>

        </div>
      </div>

      {/* Component Modal */}
      <MyQRCodeModal 
        isOpen={isQRModalOpen} 
        onClose={() => setIsQRModalOpen(false)} 
        user={user} 
      />
    </div>
  );
}