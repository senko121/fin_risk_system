import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import axiosClient from '../../api/axiosClient';

export default function SetupPin() {
  const navigate = useNavigate();
  const [currentUser, setCurrentUser] = useState(null);
  
  const [isPinSetup, setIsPinSetup] = useState(false);
  const [isLoadingStatus, setIsLoadingStatus] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [formData, setFormData] = useState({
    oldPin: '',
    newPin: '',
    confirmPin: ''
  });

  // 1. Lấy User từ LocalStorage và Gọi API check status
  useEffect(() => {
    const userStr = localStorage.getItem('currentUser');
    if (userStr) {
      const user = JSON.parse(userStr);
      setCurrentUser(user);
      checkSecurityStatus(user.id || user.userId);
    } else {
      navigate('/login');
    }
  }, [navigate]);

  const checkSecurityStatus = async (userId) => {
    try {
      const response = await axiosClient.get(`/users/${userId}/security-status`);
      setIsPinSetup(response.data.isPinSetup);
    } catch (error) {
      toast.error("Lỗi lấy thông tin bảo mật!");
    } finally {
      setIsLoadingStatus(false);
    }
  };

  const handleChange = (e) => {
    const value = e.target.value.replace(/[^0-9]/g, '');
    setFormData({ ...formData, [e.target.name]: value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (isPinSetup && formData.oldPin.length !== 6) {
      toast.warning("Vui lòng nhập đủ 6 số PIN cũ!");
      return;
    }
    if (formData.newPin.length !== 6) {
      toast.warning("Mã PIN mới phải đủ 6 số!");
      return;
    }
    if (formData.newPin !== formData.confirmPin) {
      toast.error("Mã PIN xác nhận không khớp!");
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await axiosClient.post('/users/security/pin', {
        userId: currentUser.id || currentUser.userId,
        oldPin: isPinSetup ? formData.oldPin : null,
        newPin: formData.newPin
      });

      toast.success("🎉 " + response.data.message);

      const updatedUser = { ...currentUser, isPinSetup: true };
      localStorage.setItem('currentUser', JSON.stringify(updatedUser));
      setCurrentUser(updatedUser); 

      const hasFace = updatedUser.isFaceSetup ?? updatedUser.faceSetup ?? false;

      if (hasFace === false) {
          toast.info("📸 Tuyệt vời! Vui lòng tiếp tục đăng ký FaceID để hoàn tất bảo mật.");
          navigate('/register-face');
      } else {
          navigate('/security'); 
      }

    } catch (error) {
      const errorMsg = error.response?.data?.message || "Lỗi cập nhật Mã PIN!";
      toast.error("❌ " + errorMsg);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoadingStatus) {
    return (
      <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center">
        <div className="animate-spin h-8 w-8 border-4 border-blue-600 border-t-transparent rounded-full mb-4"></div>
        <span className="text-sm font-bold text-slate-400 uppercase tracking-widest">Đang tải cấu hình...</span>
      </div>
    );
  }

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
            <h1 className="text-2xl font-bold text-white tracking-tight">Smart PIN</h1>
            <p className="text-slate-300 text-sm mt-1 opacity-80">Mã bảo mật giao dịch nhanh</p>
          </div>
        </div>

        {/* 3. Khối Card Nội Dung Xịn Sò */}
        <div className="bg-white rounded-[2.5rem] shadow-sm border border-slate-100 overflow-hidden">
          {/* Section Header Micro-copy */}
          <h4 className="px-8 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest bg-slate-50/50 border-b border-slate-100">
            {isPinSetup ? 'Thiết lập mã PIN' : 'Kích hoạt mã PIN'}
          </h4>

          <form onSubmit={handleSubmit} className="p-8 space-y-6">
            
            {/* Input: Mã PIN hiện tại (chỉ hiện khi đã setup) */}
            {isPinSetup && (
              <div className="group">
                <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2.5 group-focus-within:text-blue-600 transition-colors">
                  Mã PIN hiện tại
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-500 transition-colors">
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path></svg>
                  </div>
                  <input 
                    type="password" name="oldPin" maxLength="6" required={isPinSetup}
                    className="w-full pl-11 pr-4 py-4 bg-slate-50 border border-slate-100 rounded-2xl text-slate-800 text-sm font-medium focus:bg-white focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10 outline-none transition-all placeholder:text-slate-300"
                    placeholder="Nhập 6 số PIN cũ"
                    value={formData.oldPin} onChange={handleChange} 
                  />
                </div>
              </div>
            )}

            {/* Input: Mã PIN mới */}
            <div className="group">
              <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2.5 group-focus-within:text-blue-600 transition-colors">
                Mã PIN mới
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-500 transition-colors">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"></path></svg>
                </div>
                <input 
                  type="password" name="newPin" maxLength="6" required
                  className="w-full pl-11 pr-4 py-4 bg-slate-50 border border-slate-100 rounded-2xl text-slate-800 text-sm font-medium focus:bg-white focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10 outline-none transition-all placeholder:text-slate-300"
                  placeholder="Nhập 6 số PIN mới"
                  value={formData.newPin} onChange={handleChange} 
                />
              </div>
            </div>

            {/* Input: Xác nhận mã PIN */}
            <div className="group">
              <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2.5 group-focus-within:text-blue-600 transition-colors">
                Xác nhận mã PIN
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-500 transition-colors">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"></path></svg>
                </div>
                <input 
                  type="password" name="confirmPin" maxLength="6" required
                  className={`w-full pl-11 pr-4 py-4 bg-slate-50 border rounded-2xl text-slate-800 text-sm font-medium outline-none transition-all placeholder:text-slate-300 ${
                    formData.confirmPin.length > 0 && formData.newPin !== formData.confirmPin 
                    ? 'border-red-200 bg-red-50 text-red-600 focus:ring-4 focus:ring-red-500/10' 
                    : 'border-slate-100 focus:bg-white focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10'
                  }`}
                  placeholder="Nhập lại mã PIN mới"
                  value={formData.confirmPin} onChange={handleChange} 
                />
              </div>
            </div>
            
            {/* Nút Submit Premium */}
            <button 
              type="submit" 
              disabled={isSubmitting} 
              className="w-full mt-2 py-4 bg-slate-900 text-white font-bold rounded-2xl hover:bg-blue-600 transition-all shadow-md hover:shadow-xl hover:shadow-blue-500/20 disabled:bg-slate-300 disabled:shadow-none flex items-center justify-center space-x-2"
            >
              {isSubmitting ? (
                <>
                  <svg className="animate-spin -ml-1 mr-2 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path></svg>
                  <span>Đang xử lý...</span>
                </>
              ) : (
                <>
                  <span>{isPinSetup ? 'Cập nhật Smart PIN' : 'Kích hoạt Smart PIN'}</span>
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

// import React, { useState, useEffect } from 'react';
// import { useNavigate } from 'react-router-dom';
// import { toast } from 'react-toastify';
// import axiosClient from '../../api/axiosClient';

// export default function SetupPin() {
//   const navigate = useNavigate();
//   const [currentUser, setCurrentUser] = useState(null);
  
//   const [isPinSetup, setIsPinSetup] = useState(false); // Cờ kiểm tra từ Backend
//   const [isLoadingStatus, setIsLoadingStatus] = useState(true);
//   const [isSubmitting, setIsSubmitting] = useState(false);

//   const [formData, setFormData] = useState({
//     oldPin: '',
//     newPin: '',
//     confirmPin: ''
//   });

//   // 1. Lấy User từ LocalStorage và Gọi API check status
//   useEffect(() => {
//     const userStr = localStorage.getItem('currentUser');
//     if (userStr) {
//       const user = JSON.parse(userStr);
//       setCurrentUser(user);
//       checkSecurityStatus(user.id || user.userId);
//     } else {
//       navigate('/login');
//     }
//   }, [navigate]);

//   const checkSecurityStatus = async (userId) => {
//     try {
//       // 🚀 GỌI API VỪA VIẾT Ở BƯỚC 1
//       const response = await axiosClient.get(`/users/${userId}/security-status`);
//       setIsPinSetup(response.data.isPinSetup);
//     } catch (error) {
//       toast.error("Lỗi lấy thông tin bảo mật!");
//     } finally {
//       setIsLoadingStatus(false);
//     }
//   };

//   const handleChange = (e) => {
//     // Chỉ cho nhập số
//     const value = e.target.value.replace(/[^0-9]/g, '');
//     setFormData({ ...formData, [e.target.name]: value });
//   };

//   const handleSubmit = async (e) => {
//     e.preventDefault();
    
//     // Validate cơ bản
//     if (isPinSetup && formData.oldPin.length !== 6) {
//       toast.warning("Vui lòng nhập đủ 6 số PIN cũ!");
//       return;
//     }
//     if (formData.newPin.length !== 6) {
//       toast.warning("Mã PIN mới phải đủ 6 số!");
//       return;
//     }
//     if (formData.newPin !== formData.confirmPin) {
//       toast.error("Mã PIN xác nhận không khớp!");
//       return;
//     }

//     setIsSubmitting(true);
//     try {
//       const response = await axiosClient.post('/users/security/pin', {
//         userId: currentUser.id || currentUser.userId,
//         oldPin: isPinSetup ? formData.oldPin : null, // Nếu chưa có PIN thì gửi null
//         newPin: formData.newPin
//       });

//       toast.success("🎉 " + response.data.message);

//       // 🚀 BƯỚC 1: CẬP NHẬT LẠI "VÍ" LOCALSTORAGE NGAY LẬP TỨC
//       // Ép cờ isPinSetup thành true để app biết là mình đã có PIN rồi
//       const updatedUser = { ...currentUser, isPinSetup: true };
//       localStorage.setItem('currentUser', JSON.stringify(updatedUser));
//       setCurrentUser(updatedUser); 

//       // 🚀 BƯỚC 2: TRẠM KIỂM SOÁT NGÃ BA ĐƯỜNG (ONBOARDING CHUỖI)
//       // Check xem user có FaceID chưa (quét cả 2 tên biến cho chắc ăn)
//       const hasFace = updatedUser.isFaceSetup ?? updatedUser.faceSetup ?? false;

//       if (hasFace === false) {
//           // Nếu chưa có FaceID -> Chở thẳng khách sang trạm FaceID
//           toast.info("📸 Tuyệt vời! Vui lòng tiếp tục đăng ký FaceID để hoàn tất bảo mật.");
//           navigate('/register-face');
//       } else {
//           // Nếu đổi PIN định kỳ (đã có đủ đồ chơi) -> Trả khách về Trung tâm bảo mật
//           navigate('/security'); 
//       }

//     } catch (error) {
//       const errorMsg = error.response?.data?.message || "Lỗi cập nhật Mã PIN!";
//       toast.error("❌ " + errorMsg);
//     } finally {
//       setIsSubmitting(false);
//     }
//   };

//   if (isLoadingStatus) {
//     return (
//       <div className="min-h-screen bg-[#f4f6f9] flex flex-col items-center justify-center font-sans">
//         <svg className="w-6 h-6 animate-spin text-[#1e2d40] mb-3" fill="none" viewBox="0 0 24 24">
//           <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
//           <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4l3-3-3-3v4a8 8 0 00-8 8h4z"></path>
//         </svg>
//         <span className="text-[10px] font-semibold text-gray-500 uppercase tracking-widest">Đang tải cấu hình...</span>
//       </div>
//     );
//   }

//   return (
//     <div className="min-h-screen bg-[#f4f6f9] flex flex-col font-sans">
      
//       {/* ── Topbar: navy shell ── */}
//       <nav className="bg-[#1e2d40] px-6 py-4 flex items-center justify-between shadow-sm">
//         <div className="flex items-center gap-3">
//           {/* Nút Back đưa lên Topbar */}
//           <button 
//             onClick={() => navigate(-1)} 
//             className="p-2 text-white/40 hover:text-white hover:bg-white/10 rounded-lg transition-all active:scale-95"
//           >
//             <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
//               <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 19l-7-7 7-7" />
//             </svg>
//           </button>
//           <div>
//             <p className="text-white font-semibold text-sm">Hệ thống an ninh</p>
//             <p className="text-white/40 text-[10px] uppercase tracking-widest">Thiết lập bảo mật</p>
//           </div>
//         </div>
//         <div className="w-8 h-8 bg-white/10 border border-white/15 rounded-lg flex items-center justify-center text-white font-bold text-sm">
//           F
//         </div>
//       </nav>

//       {/* ── Vùng nội dung trung tâm ── */}
//       <div className="flex-1 flex justify-center items-center p-4">
//         <div className="w-full max-w-md bg-white p-8 rounded-2xl shadow-sm border border-gray-100">
          
//           <div className="text-center mb-8">
//             <div className="w-12 h-12 bg-[#f4f6f9] border border-gray-100 text-[#1e2d40] rounded-xl flex items-center justify-center mx-auto mb-4">
//               <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
//                 <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
//               </svg>
//             </div>
//             <h2 className="text-sm font-semibold text-gray-800 uppercase tracking-wide mb-1">
//               {isPinSetup ? 'Đổi mã Smart PIN' : 'Tạo mã Smart PIN'}
//             </h2>
//             <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest px-4">
//               Mã PIN gồm 6 số dùng để xác thực giao dịch nhanh.
//             </p>
//           </div>

//           <form onSubmit={handleSubmit} className="space-y-5">
            
//             {/* NẾU ĐÃ CÓ PIN THÌ HIỆN Ô NÀY */}
//             {isPinSetup && (
//               <div>
//                 <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2">Mã PIN hiện tại</label>
//                 <input 
//                   type="password" 
//                   name="oldPin" 
//                   maxLength="6" 
//                   placeholder="••••••"
//                   value={formData.oldPin} 
//                   onChange={handleChange} 
//                   required={isPinSetup}
//                   className="w-full text-center text-3xl tracking-[0.4em] font-bold py-4 rounded-xl bg-[#f4f6f9] border border-transparent focus:border-[#1e2d40] focus:bg-white text-gray-800 outline-none transition-all"
//                 />
//               </div>
//             )}

//             <div>
//               <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2">Mã PIN mới</label>
//               <input 
//                 type="password" 
//                 name="newPin" 
//                 maxLength="6" 
//                 placeholder="••••••"
//                 value={formData.newPin} 
//                 onChange={handleChange} 
//                 required
//                 className="w-full text-center text-3xl tracking-[0.4em] font-bold py-4 rounded-xl bg-[#f4f6f9] border border-transparent focus:border-[#1e2d40] focus:bg-white text-gray-800 outline-none transition-all"
//               />
//             </div>

//             <div>
//               <label className="block text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2">Nhập lại PIN mới</label>
//               <input 
//                 type="password" 
//                 name="confirmPin" 
//                 maxLength="6" 
//                 placeholder="••••••"
//                 value={formData.confirmPin} 
//                 onChange={handleChange} 
//                 required
//                 className={`w-full text-center text-3xl tracking-[0.4em] font-bold py-4 rounded-xl border outline-none transition-all ${
//                   formData.confirmPin.length > 0 && formData.newPin !== formData.confirmPin 
//                   ? 'bg-red-50 border-[#b91c1c] text-[#b91c1c] focus:border-[#b91c1c]' 
//                   : 'bg-[#f4f6f9] border-transparent focus:border-[#1e2d40] focus:bg-white text-gray-800'
//                 }`}
//               />
//               {/* Cảnh báo lỗi real-time nếu không khớp */}
//               {formData.confirmPin.length > 0 && formData.newPin !== formData.confirmPin && (
//                 <p className="text-[10px] font-semibold text-[#b91c1c] uppercase tracking-widest mt-2 text-center">
//                   Mã xác nhận không khớp
//                 </p>
//               )}
//             </div>

//             <div className="pt-2">
//               <button 
//                 type="submit" 
//                 disabled={isSubmitting}
//                 className={`w-full py-3.5 rounded-xl text-sm font-semibold tracking-wide transition-all flex items-center justify-center gap-2
//                   ${isSubmitting 
//                     ? 'bg-[#1e2d40]/40 text-white cursor-not-allowed' 
//                     : 'bg-[#1e2d40] text-white hover:bg-[#162233] active:scale-[0.98]'}`}
//               >
//                 {isSubmitting ? (
//                   <>
//                     <svg className="w-4 h-4 animate-spin text-white" fill="none" viewBox="0 0 24 24">
//                       <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
//                       <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4l3-3-3-3v4a8 8 0 00-8 8h4z"></path>
//                     </svg>
//                     ĐANG LƯU HỆ THỐNG...
//                   </>
//                 ) : (isPinSetup ? 'CẬP NHẬT MÃ PIN' : 'TẠO MÃ PIN')}
//               </button>
//             </div>
//           </form>

//         </div>
//       </div>
//     </div>
//   );
// }