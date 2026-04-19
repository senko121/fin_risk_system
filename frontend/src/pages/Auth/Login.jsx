import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

export default function Login() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [errorMsg, setErrorMsg] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleLogin = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    setIsLoading(true);

    try {
      const response = await axios.post('http://localhost:8081/api/auth/login', {
        username, password
      });
      
      // Bóc tách và cất 2 cái Token + Thông tin User vào ví (LocalStorage)
      localStorage.setItem('accessToken', response.data.accessToken);
      localStorage.setItem('refreshToken', response.data.refreshToken);
      localStorage.setItem('currentUser', JSON.stringify(response.data.user));

      // 🚀 SỬA Ở ĐÂY: Lấy chức vụ ra để phân luồng giao thông
      const userRole = response.data.user.role; 

      if (userRole === 'ADMIN') {
          // Nếu là Quản trị viên -> Đá thẳng vào màn hình Admin
          navigate('/admin');
      } else {
          // Nếu là Khách hàng bình thường -> Đá vào màn hình Chuyển tiền
          navigate('/dashboard');
      }
      
    } catch (error) {
      // Backend của mình trả về lỗi dạng chuỗi ở error.response.data
      setErrorMsg(error.response?.data || 'Lỗi kết nối đến Server Backend!');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-white flex justify-center items-center p-4">
      {/* Container chính bóp hẹp ở giữa */}
      <div className="w-full max-w-md bg-white p-8 rounded-3xl shadow-[0_20px_50px_rgba(0,0,0,0.1)] border border-gray-100">
        <div className="text-center mb-10">
          <h1 className="text-2xl font-black text-gray-900 tracking-tight">FINRISK BANK</h1>
          <p className="text-gray-400 text-sm mt-1">Hệ thống giao dịch bảo mật</p>
        </div>

        {errorMsg && (
          <div className="mb-6 p-4 bg-red-50 border-l-4 border-red-500 text-red-700 rounded-lg text-sm font-medium animate-shake">
            {errorMsg}
          </div>
        )}

        <form onSubmit={handleLogin} className="space-y-6">
          <div>
            <label className="block text-xs uppercase tracking-widest font-bold text-gray-500 mb-2 ml-1">Tên đăng nhập</label>
            <input 
              type="text" required
              className="w-full px-5 py-4 rounded-2xl bg-gray-50 border-none focus:ring-2 focus:ring-blue-500 transition-all font-medium"
              placeholder="Nhập username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>

          <div>
            <label className="block text-xs uppercase tracking-widest font-bold text-gray-500 mb-2 ml-1">Mật khẩu</label>
            <input 
              type="password" required
              className="w-full px-5 py-4 rounded-2xl bg-gray-50 border-none focus:ring-2 focus:ring-blue-500 transition-all font-medium"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <button 
            type="submit" disabled={isLoading}
            className={`w-full py-4 rounded-2xl text-white font-bold text-lg shadow-xl transition-all ${isLoading ? 'bg-blue-300' : 'bg-blue-600 hover:bg-blue-700 active:scale-95'}`}
          >
            {isLoading ? 'ĐANG XỬ LÝ...' : 'ĐĂNG NHẬP'}
          </button>
        </form>
        
        <p className="text-center mt-8 text-gray-400 text-xs">
          © 2026 FinRisk Security System. All rights reserved.
        </p>
      </div>
    </div>
  );
}