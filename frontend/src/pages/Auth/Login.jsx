// import React, { useState } from 'react';
// import { useNavigate } from 'react-router-dom';
// import axios from 'axios';

// export default function Login() {
//   const navigate = useNavigate();
//   const [username, setUsername] = useState('');
//   const [password, setPassword] = useState('');
//   const [errorMsg, setErrorMsg] = useState('');
//   const [isLoading, setIsLoading] = useState(false);

  
//  const handleLogin = async (e) => {
//     e.preventDefault();
//     setErrorMsg('');
//     setIsLoading(true);

//     try {
//       const response = await axios.post('http://localhost:8081/api/auth/login', {
//         username, password
//       });
      
//       // 🚀 LOG 1: In toàn bộ dữ liệu gốc từ Backend ra để kiểm tra
//       console.log("=========================================");
//       console.log("📦 DỮ LIỆU BACKEND TRẢ VỀ:", response.data);
//       console.log("=========================================");

//       // Lưu Token
//       localStorage.setItem('accessToken', response.data.accessToken);
//       localStorage.setItem('refreshToken', response.data.refreshToken);

//       // 🚀 BƯỚC 1: Quét sạch mọi ngóc ngách, có "is" hay không có "is" lụm hết!
//       const pinStatus = 
//           response.data.pinSetup ?? 
//           response.data.isPinSetup ?? 
//           response.data.user?.pinSetup ?? 
//           response.data.user?.isPinSetup ?? 
//           false;

//       const faceStatus = 
//           response.data.faceSetup ?? 
//           response.data.isFaceSetup ?? 
//           response.data.user?.faceSetup ?? 
//           response.data.user?.isFaceSetup ?? 
//           false;

//       // 🚀 LOG 2: In kết quả sau khi Frontend đã "chốt" để xem nó có bị ngu nữa không
//       console.log("🎯 TRẠNG THÁI CHỐT LẠI -> PIN đã cài:", pinStatus, "| FACE đã cài:", faceStatus);

//       // 🚀 BƯỚC 2: Nhét thêm 2 cờ này vào đối tượng user trước khi cất vào ví
//       const userToSave = { 
//           ...response.data.user, 
//           isPinSetup: pinStatus,
//           isFaceSetup: faceStatus
//       };
//       localStorage.setItem('currentUser', JSON.stringify(userToSave));

//       const userRole = response.data.user?.role || response.data.role; // Quét thêm role cho chắc chắn

//       // 🚀 BƯỚC 3: Phân luồng giao thông với Radar Onboarding
//       if (userRole === 'ADMIN') {
//           navigate('/admin');
//       } else {
//           // Check PIN trước
//           if (pinStatus === false) {
//               navigate('/setup-pin');
//           } 
//           // Cài PIN xong (hoặc đã có) thì check tiếp FaceID
//           else if (faceStatus === false) {
//               navigate('/register-face');
//           } 
//           // Đủ hết đồ chơi thì cho vào nhà
//           else {
//               navigate('/dashboard');
//           }
//       }
      
//     } catch (error) {
//       setErrorMsg(error.response?.data || 'Lỗi kết nối đến Server Backend!');
//     } finally {
//       setIsLoading(false);
//     }
//   };

//   return (
//     <div className="min-h-screen bg-white flex justify-center items-center p-4">
//       {/* Container chính bóp hẹp ở giữa */}
//       <div className="w-full max-w-md bg-white p-8 rounded-3xl shadow-[0_20px_50px_rgba(0,0,0,0.1)] border border-gray-100">
//         <div className="text-center mb-10">
//           <h1 className="text-2xl font-black text-gray-900 tracking-tight">FINRISK BANK</h1>
//           <p className="text-gray-400 text-sm mt-1">Hệ thống giao dịch bảo mật</p>
//         </div>

//         {errorMsg && (
//           <div className="mb-6 p-4 bg-red-50 border-l-4 border-red-500 text-red-700 rounded-lg text-sm font-medium animate-shake">
//             {errorMsg}
//           </div>
//         )}

//         <form onSubmit={handleLogin} className="space-y-6">
//           <div>
//             <label className="block text-xs uppercase tracking-widest font-bold text-gray-500 mb-2 ml-1">Tên đăng nhập</label>
//             <input 
//               type="text" required
//               className="w-full px-5 py-4 rounded-2xl bg-gray-50 border-none focus:ring-2 focus:ring-blue-500 transition-all font-medium"
//               placeholder="Nhập username"
//               value={username}
//               onChange={(e) => setUsername(e.target.value)}
//             />
//           </div>

//           <div>
//             <label className="block text-xs uppercase tracking-widest font-bold text-gray-500 mb-2 ml-1">Mật khẩu</label>
//             <input 
//               type="password" required
//               className="w-full px-5 py-4 rounded-2xl bg-gray-50 border-none focus:ring-2 focus:ring-blue-500 transition-all font-medium"
//               placeholder="••••••••"
//               value={password}
//               onChange={(e) => setPassword(e.target.value)}
//             />
//           </div>

//           <button 
//             type="submit" disabled={isLoading}
//             className={`w-full py-4 rounded-2xl text-white font-bold text-lg shadow-xl transition-all ${isLoading ? 'bg-blue-300' : 'bg-blue-600 hover:bg-blue-700 active:scale-95'}`}
//           >
//             {isLoading ? 'ĐANG XỬ LÝ...' : 'ĐĂNG NHẬP'}
//           </button>
//         </form>
        
//         <p className="text-center mt-8 text-gray-400 text-xs">
//           © 2026 FinRisk Security System. All rights reserved.
//         </p>
//       </div>
//     </div>
//   );
// }

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

      console.log("=========================================");
      console.log("📦 DỮ LIỆU BACKEND TRẢ VỀ:", response.data);
      console.log("=========================================");

      localStorage.setItem('accessToken', response.data.accessToken);
      localStorage.setItem('refreshToken', response.data.refreshToken);

      const pinStatus =
        response.data.pinSetup ??
        response.data.isPinSetup ??
        response.data.user?.pinSetup ??
        response.data.user?.isPinSetup ??
        false;

      const faceStatus =
        response.data.faceSetup ??
        response.data.isFaceSetup ??
        response.data.user?.faceSetup ??
        response.data.user?.isFaceSetup ??
        false;

      console.log("🎯 PIN đã cài:", pinStatus, "| FACE đã cài:", faceStatus);

      const userToSave = {
        ...response.data.user,
        isPinSetup: pinStatus,
        isFaceSetup: faceStatus
      };
      localStorage.setItem('currentUser', JSON.stringify(userToSave));

      const userRole = response.data.user?.role || response.data.role;

      if (userRole === 'ADMIN') {
        navigate('/admin');
      } else {
        if (pinStatus === false) navigate('/setup-pin');
        else if (faceStatus === false) navigate('/register-face');
        else navigate('/dashboard');
      }

    } catch (error) {
      setErrorMsg(error.response?.data || 'Lỗi kết nối đến Server Backend!');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#f4f6f9] flex items-center justify-center p-4">
      <div className="w-full max-w-3xl flex rounded-2xl overflow-hidden shadow-lg">

        {/* ── CỘT TRÁI: Navy hero panel ── */}
        <div className="hidden md:flex w-5/12 bg-[#1e2d40] flex-col justify-between p-10">

          {/* Brand */}
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 bg-white/10 border border-white/15 rounded-xl flex items-center justify-center text-white font-bold text-sm">
              F
            </div>
            <span className="text-white font-bold text-sm tracking-wide">FINRISK</span>
          </div>

          {/* Headline */}
          <div>
            {/* Accent line — điểm nhấn duy nhất có màu */}
            <div className="w-8 h-[3px] bg-blue-500 rounded-full mb-5" />
            <h2 className="text-white text-xl font-semibold leading-snug mb-3">
              Hệ thống quản trị<br />bảo mật tài chính
            </h2>
            <p className="text-white/40 text-xs leading-relaxed">
              Giám sát giao dịch thời gian thực với AI phát hiện rủi ro tự động.
            </p>
          </div>

          {/* Trust badges */}
          <div className="space-y-2.5">
            {[
              'DeepFace CNN Verification',
              'Redis OTP Caching',
              'Anti-Spam Detection Rule',
            ].map((module) => (
              <div key={module} className="flex items-center gap-2.5">
                <span className="w-1.5 h-1.5 rounded-full bg-green-400 flex-shrink-0" />
                <span className="text-white/50 text-[11px]">{module}</span>
              </div>
            ))}
          </div>
        </div>

        {/* ── CỘT PHẢI: Form đăng nhập ── */}
        <div className="flex-1 bg-white p-10 flex flex-col justify-center">

          {/* Mobile brand — chỉ hiện khi ẩn cột trái */}
          <div className="flex items-center gap-2 mb-8 md:hidden">
            <div className="w-8 h-8 bg-[#1e2d40] rounded-lg flex items-center justify-center text-white font-bold text-sm">F</div>
            <span className="text-[#1e2d40] font-bold text-sm tracking-wide">FINRISK</span>
          </div>

          <h1 className="text-lg font-semibold text-gray-800 mb-1">Đăng nhập</h1>
          <p className="text-xs text-gray-400 mb-8">Nhập thông tin tài khoản để tiếp tục</p>

          {/* Error */}
          {errorMsg && (
            <div className="mb-6 p-3 bg-red-50 border-l-2 border-red-600 rounded-lg text-xs text-red-700 font-medium">
              {errorMsg}
            </div>
          )}

          <form onSubmit={handleLogin} className="space-y-5">

            <div>
              <label className="block text-[10px] font-semibold uppercase tracking-widest text-gray-400 mb-2">
                Tên đăng nhập
              </label>
              <input
                type="text"
                required
                autoComplete="username"
                placeholder="Nhập username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full px-4 py-3 bg-[#f4f6f9] border border-transparent rounded-xl text-sm text-gray-800 font-medium placeholder-gray-300 focus:outline-none focus:bg-white focus:border-[#1e2d40] transition-all"
              />
            </div>

            <div>
              <label className="block text-[10px] font-semibold uppercase tracking-widest text-gray-400 mb-2">
                Mật khẩu
              </label>
              <input
                type="password"
                required
                autoComplete="current-password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-4 py-3 bg-[#f4f6f9] border border-transparent rounded-xl text-sm text-gray-800 font-medium placeholder-gray-300 focus:outline-none focus:bg-white focus:border-[#1e2d40] transition-all"
              />
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className={`w-full py-3 rounded-xl text-sm font-semibold text-white tracking-wide transition-all
                ${isLoading
                  ? 'bg-[#1e2d40]/40 cursor-not-allowed'
                  : 'bg-[#1e2d40] hover:bg-[#162233] active:scale-[0.98]'
                }`}
            >
              {isLoading ? (
                <span className="flex items-center justify-center gap-2">
                  <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4l3-3-3-3v4a8 8 0 00-8 8h4z" />
                  </svg>
                  Đang xử lý...
                </span>
              ) : 'Đăng nhập'}
            </button>

          </form>

          <p className="text-center mt-8 text-[10px] text-gray-300">
            © 2026 FinRisk Security System
          </p>
        </div>

      </div>
    </div>
  );
}