// import React, { useState } from 'react';
// import axiosClient from '../../api/axiosClient';
// import { toast } from 'react-toastify';
// import { Link, useNavigate } from 'react-router-dom';

// export default function ChangePassword() {
//   const [formData, setFormData] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
//   const [isLoading, setIsLoading] = useState(false);
//   const navigate = useNavigate();
  
//   // Lấy ID user đang đăng nhập (tùy theo cách ông lưu lúc Login, thường là trong localStorage)
//   const userId = localStorage.getItem('userId'); 

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
//       localStorage.removeItem('userId');
//       setTimeout(() => navigate('/login'), 1500);

//     } catch (error) {
//       toast.error(error.response?.data || "Lỗi hệ thống khi đổi mật khẩu!");
//     } finally {
//       setIsLoading(false);
//     }
//   };

//   return (
//     <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
//       <div className="max-w-md w-full bg-white p-8 rounded-3xl shadow-xl border border-slate-100 relative overflow-hidden">
        
//         {/* Nút quay lại */}
//         <Link to="/dashboard" className="absolute top-6 right-6 text-slate-400 hover:text-slate-800 transition-colors">
//           <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
//         </Link>

//         <h2 className="text-2xl font-black text-slate-900 mb-2">🛡️ Đổi mật khẩu</h2>
//         <p className="text-slate-500 text-sm mb-8">Bảo vệ tài khoản của bạn bằng mật khẩu an toàn.</p>
        
//         <form onSubmit={handleSubmit} className="space-y-5">
//           <div>
//             <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2">Mật khẩu hiện tại</label>
//             <input type="password" required 
//                    className="w-full p-3.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 focus:ring-2 focus:ring-blue-500 outline-none transition-all" 
//                    value={formData.oldPassword} onChange={e => setFormData({...formData, oldPassword: e.target.value})} />
//           </div>
//           <div>
//             <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2">Mật khẩu mới</label>
//             <input type="password" required 
//                    className="w-full p-3.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 focus:ring-2 focus:ring-blue-500 outline-none transition-all"
//                    value={formData.newPassword} onChange={e => setFormData({...formData, newPassword: e.target.value})} />
//           </div>
//           <div>
//             <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2">Xác nhận mật khẩu mới</label>
//             <input type="password" required 
//                    className="w-full p-3.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 focus:ring-2 focus:ring-blue-500 outline-none transition-all"
//                    value={formData.confirmPassword} onChange={e => setFormData({...formData, confirmPassword: e.target.value})} />
//           </div>
          
//           <button type="submit" disabled={isLoading} 
//                   className="w-full mt-4 py-4 bg-blue-600 text-white font-black rounded-xl hover:bg-blue-700 transition-all shadow-lg shadow-blue-500/30 disabled:bg-slate-400">
//             {isLoading ? 'ĐANG CẬP NHẬT...' : 'CẬP NHẬT MẬT KHẨU'}
//           </button>
//         </form>
//       </div>
//     </div>
//   );
// }

import React, { useState } from 'react';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import { useNavigate } from 'react-router-dom';

export default function ChangePassword() {
  const [formData, setFormData] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();
  
  // Lấy ID user đang đăng nhập (kết hợp cả 2 kiểu lưu để chắc chắn không bị lỗi)
  const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}');
  const userId = localStorage.getItem('userId') || currentUser.id || currentUser.userId;

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
      
      // Đổi pass xong thì xóa token và đá về trang Login cho bảo mật
      localStorage.removeItem('token');
      localStorage.removeItem('accessToken'); // Dọn dẹp thêm để đồng bộ luồng Login.js của bro
      localStorage.removeItem('userId');
      localStorage.removeItem('currentUser'); 
      setTimeout(() => navigate('/login'), 1500);

    } catch (error) {
      toast.error(error.response?.data || "Lỗi hệ thống khi đổi mật khẩu!");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#f4f6f9] flex flex-col font-sans">
      
      {/* ── Topbar: navy shell ── */}
      <nav className="bg-[#1e2d40] px-6 py-4 flex items-center justify-between shadow-sm">
        <div className="flex items-center gap-3">
          <button 
            onClick={() => navigate('/dashboard')} 
            className="p-2 text-white/40 hover:text-white hover:bg-white/10 rounded-lg transition-all active:scale-95"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <div>
            <p className="text-white font-semibold text-sm">Hệ thống an ninh</p>
            <p className="text-white/40 text-[10px] uppercase tracking-widest">Đổi mật khẩu</p>
          </div>
        </div>
        <div className="w-8 h-8 bg-white/10 border border-white/15 rounded-lg flex items-center justify-center text-white font-bold text-sm">
          F
        </div>
      </nav>

      {/* ── Vùng nội dung trung tâm ── */}
      <div className="flex-1 flex items-center justify-center p-4">
        <div className="max-w-md w-full bg-white p-8 rounded-2xl shadow-sm border border-gray-100 relative">
          
          <div className="text-center mb-8">
            <div className="w-12 h-12 bg-[#f4f6f9] border border-gray-100 text-[#1e2d40] rounded-xl flex items-center justify-center mx-auto mb-4">
              {/* Icon ổ khóa */}
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
              </svg>
            </div>
            <h2 className="text-sm font-semibold text-gray-800 uppercase tracking-wide mb-1">
              Đổi mật khẩu truy cập
            </h2>
            <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest px-4">
              Thiết lập khóa an toàn cho tài khoản của bạn
            </p>
          </div>
          
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2">
                Mật khẩu hiện tại
              </label>
              <input 
                type="password" 
                required 
                placeholder="••••••••"
                className="w-full px-4 py-3.5 bg-[#f4f6f9] border border-transparent rounded-xl text-gray-800 font-medium focus:outline-none focus:bg-white focus:border-[#1e2d40] transition-all" 
                value={formData.oldPassword} 
                onChange={e => setFormData({...formData, oldPassword: e.target.value})} 
              />
            </div>
            
            <div>
              <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2">
                Mật khẩu mới (Tối thiểu 6 ký tự)
              </label>
              <input 
                type="password" 
                required 
                placeholder="••••••••"
                className="w-full px-4 py-3.5 bg-[#f4f6f9] border border-transparent rounded-xl text-gray-800 font-medium focus:outline-none focus:bg-white focus:border-[#1e2d40] transition-all"
                value={formData.newPassword} 
                onChange={e => setFormData({...formData, newPassword: e.target.value})} 
              />
            </div>
            
            <div>
              <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2">
                Xác nhận mật khẩu mới
              </label>
              <input 
                type="password" 
                required 
                placeholder="••••••••"
                className={`w-full px-4 py-3.5 rounded-xl border font-medium focus:outline-none transition-all
                  ${formData.confirmPassword.length > 0 && formData.newPassword !== formData.confirmPassword 
                    ? 'bg-red-50 border-[#b91c1c] text-[#b91c1c] focus:border-[#b91c1c]' 
                    : 'bg-[#f4f6f9] border-transparent text-gray-800 focus:bg-white focus:border-[#1e2d40]'
                  }`}
                value={formData.confirmPassword} 
                onChange={e => setFormData({...formData, confirmPassword: e.target.value})} 
              />
              {/* Cảnh báo lỗi real-time nếu không khớp */}
              {formData.confirmPassword.length > 0 && formData.newPassword !== formData.confirmPassword && (
                <p className="text-[10px] font-semibold text-[#b91c1c] uppercase tracking-widest mt-2">
                  Mật khẩu xác nhận không khớp
                </p>
              )}
            </div>
            
            <div className="pt-2">
              <button 
                type="submit" 
                disabled={isLoading} 
                className={`w-full py-3.5 rounded-xl text-sm font-semibold tracking-wide transition-all flex items-center justify-center gap-2
                  ${isLoading 
                    ? 'bg-[#1e2d40]/40 text-white cursor-not-allowed' 
                    : 'bg-[#1e2d40] text-white hover:bg-[#162233] active:scale-[0.98]'}`}
              >
                {isLoading ? (
                  <>
                    <svg className="w-4 h-4 animate-spin text-white" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4l3-3-3-3v4a8 8 0 00-8 8h4z"></path>
                    </svg>
                    ĐANG CẬP NHẬT...
                  </>
                ) : 'CẬP NHẬT MẬT KHẨU'}
              </button>
            </div>
          </form>

        </div>
      </div>
    </div>
  );
}