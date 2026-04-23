import React, { useState } from 'react';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import { Link, useNavigate } from 'react-router-dom';

export default function ChangePassword() {
  const [formData, setFormData] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();
  
  // Lấy ID user đang đăng nhập (tùy theo cách ông lưu lúc Login, thường là trong localStorage)
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
      
      // Đổi pass xong thì xóa token và đá về trang Login cho bảo mật
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
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-white p-8 rounded-3xl shadow-xl border border-slate-100 relative overflow-hidden">
        
        {/* Nút quay lại */}
        <Link to="/dashboard" className="absolute top-6 right-6 text-slate-400 hover:text-slate-800 transition-colors">
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
        </Link>

        <h2 className="text-2xl font-black text-slate-900 mb-2">🛡️ Đổi mật khẩu</h2>
        <p className="text-slate-500 text-sm mb-8">Bảo vệ tài khoản của bạn bằng mật khẩu an toàn.</p>
        
        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2">Mật khẩu hiện tại</label>
            <input type="password" required 
                   className="w-full p-3.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 focus:ring-2 focus:ring-blue-500 outline-none transition-all" 
                   value={formData.oldPassword} onChange={e => setFormData({...formData, oldPassword: e.target.value})} />
          </div>
          <div>
            <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2">Mật khẩu mới</label>
            <input type="password" required 
                   className="w-full p-3.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 focus:ring-2 focus:ring-blue-500 outline-none transition-all"
                   value={formData.newPassword} onChange={e => setFormData({...formData, newPassword: e.target.value})} />
          </div>
          <div>
            <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2">Xác nhận mật khẩu mới</label>
            <input type="password" required 
                   className="w-full p-3.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 focus:ring-2 focus:ring-blue-500 outline-none transition-all"
                   value={formData.confirmPassword} onChange={e => setFormData({...formData, confirmPassword: e.target.value})} />
          </div>
          
          <button type="submit" disabled={isLoading} 
                  className="w-full mt-4 py-4 bg-blue-600 text-white font-black rounded-xl hover:bg-blue-700 transition-all shadow-lg shadow-blue-500/30 disabled:bg-slate-400">
            {isLoading ? 'ĐANG CẬP NHẬT...' : 'CẬP NHẬT MẬT KHẨU'}
          </button>
        </form>
      </div>
    </div>
  );
}