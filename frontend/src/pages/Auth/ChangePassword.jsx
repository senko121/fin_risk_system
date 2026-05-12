import React, { useState } from 'react';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import { useNavigate } from 'react-router-dom';

export default function ChangePassword() {
  const [formData, setFormData] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();
  
  // Lấy ID user đang đăng nhập
  const userId = localStorage.getItem('userId'); 

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // Kiểm tra khớp mật khẩu
    if (formData.newPassword !== formData.confirmPassword) {
      return toast.error("Mật khẩu xác nhận không khớp!");
    }
    if (formData.newPassword.length < 6) {
      return toast.warning("Mật khẩu mới phải có ít nhất 6 ký tự!");
    }

    try {
      setIsLoading(true);
      await axiosClient.post(`/users/${userId}/change-password`, {
        oldPassword: formData.oldPassword,
        newPassword: formData.newPassword
      });
      
      toast.success("Đổi mật khẩu thành công! Vui lòng đăng nhập lại.");
      
      // Xóa token và đá về Login
      localStorage.removeItem('token');
      localStorage.removeItem('userId');
      setTimeout(() => navigate('/login'), 1500);

    } catch (error) {
      toast.error(error.response?.data || "Lỗi hệ thống khi đổi mật khẩu!");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
      {/* 1. Nền Header tối giản tạo chiều sâu */}
      <div className="h-48 bg-slate-900 rounded-b-[3rem] shadow-lg"></div>

      <div className="max-w-md mx-auto px-6 -mt-32 pb-12">
        {/* 2. Tiêu đề & Nút quay lại kiểu Glassmorphism */}
        <div className="flex items-center mb-8">
          <button 
            onClick={() => navigate(-1)} 
            className="p-3 bg-white/10 hover:bg-white/20 rounded-2xl backdrop-blur-md text-white transition-all border border-white/10 mr-4"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 19l-7-7 7-7"></path>
            </svg>
          </button>
          <div>
            <h1 className="text-2xl font-bold text-white tracking-tight">Bảo mật</h1>
            <p className="text-slate-300 text-sm mt-1 opacity-80">Cập nhật mật khẩu tài khoản</p>
          </div>
        </div>

        {/* 3. Khối Card Nội Dung Xịn Sò */}
        <div className="bg-white rounded-[2.5rem] shadow-sm border border-slate-100 overflow-hidden">
          {/* Section Header Micro-copy */}
          <h4 className="px-8 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest bg-slate-50/50 border-b border-slate-100">
            Thiết lập mật khẩu
          </h4>

          <form onSubmit={handleSubmit} className="p-8 space-y-6">
            
            {/* Input: Mật khẩu hiện tại */}
            <div className="group">
              <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2.5 group-focus-within:text-blue-600 transition-colors">
                Mật khẩu hiện tại
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-500 transition-colors">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path></svg>
                </div>
                <input 
                  type="password" required 
                  className="w-full pl-11 pr-4 py-4 bg-slate-50 border border-slate-100 rounded-2xl text-slate-800 text-sm font-medium focus:bg-white focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10 outline-none transition-all placeholder:text-slate-300"
                  placeholder="Nhập mật khẩu cũ"
                  value={formData.oldPassword} onChange={e => setFormData({...formData, oldPassword: e.target.value})} 
                />
              </div>
            </div>

            {/* Input: Mật khẩu mới */}
            <div className="group">
              <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2.5 group-focus-within:text-blue-600 transition-colors">
                Mật khẩu mới
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-500 transition-colors">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"></path></svg>
                </div>
                <input 
                  type="password" required 
                  className="w-full pl-11 pr-4 py-4 bg-slate-50 border border-slate-100 rounded-2xl text-slate-800 text-sm font-medium focus:bg-white focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10 outline-none transition-all placeholder:text-slate-300"
                  placeholder="Ít nhất 6 ký tự"
                  value={formData.newPassword} onChange={e => setFormData({...formData, newPassword: e.target.value})} 
                />
              </div>
            </div>

            {/* Input: Xác nhận mật khẩu */}
            <div className="group">
              <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2.5 group-focus-within:text-blue-600 transition-colors">
                Xác nhận mật khẩu mới
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-500 transition-colors">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"></path></svg>
                </div>
                <input 
                  type="password" required 
                  className="w-full pl-11 pr-4 py-4 bg-slate-50 border border-slate-100 rounded-2xl text-slate-800 text-sm font-medium focus:bg-white focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10 outline-none transition-all placeholder:text-slate-300"
                  placeholder="Nhập lại mật khẩu mới"
                  value={formData.confirmPassword} onChange={e => setFormData({...formData, confirmPassword: e.target.value})} 
                />
              </div>
            </div>
            
            {/* Nút Submit Premium */}
            <button 
              type="submit" 
              disabled={isLoading} 
              className="w-full mt-2 py-4 bg-slate-900 text-white font-bold rounded-2xl hover:bg-blue-600 transition-all shadow-md hover:shadow-xl hover:shadow-blue-500/20 disabled:bg-slate-300 disabled:shadow-none flex items-center justify-center space-x-2"
            >
              {isLoading ? (
                <>
                  <svg className="animate-spin -ml-1 mr-2 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path></svg>
                  <span>Đang xử lý...</span>
                </>
              ) : (
                <>
                  <span>Cập nhật mật khẩu</span>
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M14 5l7 7m0 0l-7 7m7-7H3"></path></svg>
                </>
              )}
            </button>
            
          </form>
        </div>
      </div>
    </div>
  );
}

// import React, { useState } from 'react';
// import axiosClient from '../../api/axiosClient';
// import { toast } from 'react-toastify';
// import { useNavigate } from 'react-router-dom';

// export default function ChangePassword() {
//   const [formData, setFormData] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
//   const [isLoading, setIsLoading] = useState(false);
//   const navigate = useNavigate();
  
//   // Lấy ID user đang đăng nhập (kết hợp cả 2 kiểu lưu để chắc chắn không bị lỗi)
//   const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}');
//   const userId = localStorage.getItem('userId') || currentUser.id || currentUser.userId;

//   const handleSubmit = async (e) => {
//     e.preventDefault();
    
//     // Kiểm tra khớp mật khẩu
//     if (formData.newPassword !== formData.confirmPassword) {
//       return toast.error("Mật khẩu xác nhận không khớp!");
//     }
//     if (formData.newPassword.length < 6) {
//       return toast.warning("Mật khẩu mới phải có ít nhất 6 ký tự!");
//     }

//     try {
//       setIsLoading(true);
//       await axiosClient.post(`/users/${userId}/change-password`, {
//         oldPassword: formData.oldPassword,
//         newPassword: formData.newPassword
//       });
      
//       toast.success("Đổi mật khẩu thành công! Vui lòng đăng nhập lại.");
      
//       // Đổi pass xong thì xóa token và đá về trang Login cho bảo mật
//       localStorage.removeItem('token');
//       localStorage.removeItem('accessToken'); // Dọn dẹp thêm để đồng bộ luồng Login.js của bro
//       localStorage.removeItem('userId');
//       localStorage.removeItem('currentUser'); 
//       setTimeout(() => navigate('/login'), 1500);

//     } catch (error) {
//       toast.error(error.response?.data || "Lỗi hệ thống khi đổi mật khẩu!");
//     } finally {
//       setIsLoading(false);
//     }
//   };

//   return (
//     <div className="min-h-screen bg-[#f4f6f9] flex flex-col font-sans">
      
//       {/* ── Topbar: navy shell ── */}
//       <nav className="bg-[#1e2d40] px-6 py-4 flex items-center justify-between shadow-sm">
//         <div className="flex items-center gap-3">
//           <button 
//             onClick={() => navigate('/dashboard')} 
//             className="p-2 text-white/40 hover:text-white hover:bg-white/10 rounded-lg transition-all active:scale-95"
//           >
//             <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
//               <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 19l-7-7 7-7" />
//             </svg>
//           </button>
//           <div>
//             <p className="text-white font-semibold text-sm">Hệ thống an ninh</p>
//             <p className="text-white/40 text-[10px] uppercase tracking-widest">Đổi mật khẩu</p>
//           </div>
//         </div>
//         <div className="w-8 h-8 bg-white/10 border border-white/15 rounded-lg flex items-center justify-center text-white font-bold text-sm">
//           F
//         </div>
//       </nav>

//       {/* ── Vùng nội dung trung tâm ── */}
//       <div className="flex-1 flex items-center justify-center p-4">
//         <div className="max-w-md w-full bg-white p-8 rounded-2xl shadow-sm border border-gray-100 relative">
          
//           <div className="text-center mb-8">
//             <div className="w-12 h-12 bg-[#f4f6f9] border border-gray-100 text-[#1e2d40] rounded-xl flex items-center justify-center mx-auto mb-4">
//               {/* Icon ổ khóa */}
//               <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
//                 <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
//               </svg>
//             </div>
//             <h2 className="text-sm font-semibold text-gray-800 uppercase tracking-wide mb-1">
//               Đổi mật khẩu truy cập
//             </h2>
//             <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest px-4">
//               Thiết lập khóa an toàn cho tài khoản của bạn
//             </p>
//           </div>
          
//           <form onSubmit={handleSubmit} className="space-y-5">
//             <div>
//               <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2">
//                 Mật khẩu hiện tại
//               </label>
//               <input 
//                 type="password" 
//                 required 
//                 placeholder="••••••••"
//                 className="w-full px-4 py-3.5 bg-[#f4f6f9] border border-transparent rounded-xl text-gray-800 font-medium focus:outline-none focus:bg-white focus:border-[#1e2d40] transition-all" 
//                 value={formData.oldPassword} 
//                 onChange={e => setFormData({...formData, oldPassword: e.target.value})} 
//               />
//             </div>
            
//             <div>
//               <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2">
//                 Mật khẩu mới (Tối thiểu 6 ký tự)
//               </label>
//               <input 
//                 type="password" 
//                 required 
//                 placeholder="••••••••"
//                 className="w-full px-4 py-3.5 bg-[#f4f6f9] border border-transparent rounded-xl text-gray-800 font-medium focus:outline-none focus:bg-white focus:border-[#1e2d40] transition-all"
//                 value={formData.newPassword} 
//                 onChange={e => setFormData({...formData, newPassword: e.target.value})} 
//               />
//             </div>
            
//             <div>
//               <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2">
//                 Xác nhận mật khẩu mới
//               </label>
//               <input 
//                 type="password" 
//                 required 
//                 placeholder="••••••••"
//                 className={`w-full px-4 py-3.5 rounded-xl border font-medium focus:outline-none transition-all
//                   ${formData.confirmPassword.length > 0 && formData.newPassword !== formData.confirmPassword 
//                     ? 'bg-red-50 border-[#b91c1c] text-[#b91c1c] focus:border-[#b91c1c]' 
//                     : 'bg-[#f4f6f9] border-transparent text-gray-800 focus:bg-white focus:border-[#1e2d40]'
//                   }`}
//                 value={formData.confirmPassword} 
//                 onChange={e => setFormData({...formData, confirmPassword: e.target.value})} 
//               />
//               {/* Cảnh báo lỗi real-time nếu không khớp */}
//               {formData.confirmPassword.length > 0 && formData.newPassword !== formData.confirmPassword && (
//                 <p className="text-[10px] font-semibold text-[#b91c1c] uppercase tracking-widest mt-2">
//                   Mật khẩu xác nhận không khớp
//                 </p>
//               )}
//             </div>
            
//             <div className="pt-2">
//               <button 
//                 type="submit" 
//                 disabled={isLoading} 
//                 className={`w-full py-3.5 rounded-xl text-sm font-semibold tracking-wide transition-all flex items-center justify-center gap-2
//                   ${isLoading 
//                     ? 'bg-[#1e2d40]/40 text-white cursor-not-allowed' 
//                     : 'bg-[#1e2d40] text-white hover:bg-[#162233] active:scale-[0.98]'}`}
//               >
//                 {isLoading ? (
//                   <>
//                     <svg className="w-4 h-4 animate-spin text-white" fill="none" viewBox="0 0 24 24">
//                       <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
//                       <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4l3-3-3-3v4a8 8 0 00-8 8h4z"></path>
//                     </svg>
//                     ĐANG CẬP NHẬT...
//                   </>
//                 ) : 'CẬP NHẬT MẬT KHẨU'}
//               </button>
//             </div>
//           </form>

//         </div>
//       </div>
//     </div>
//   );
// }