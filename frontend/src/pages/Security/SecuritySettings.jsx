// import React from 'react';
// import { useNavigate } from 'react-router-dom';

// export default function SecuritySettings() {
//   const navigate = useNavigate();

//   return (
//     <div className="min-h-screen bg-slate-50 p-6 flex flex-col items-center">
//       <div className="max-w-md w-full">
        
//         {/* Header có nút Back */}
//         <div className="flex items-center mb-8">
//           <button onClick={() => navigate('/dashboard')} className="p-2 bg-white rounded-xl shadow-sm hover:bg-slate-100 transition mr-4">
//             <svg className="w-6 h-6 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7"></path></svg>
//           </button>
//           <h1 className="text-2xl font-black text-slate-900">Trung tâm Bảo mật</h1>
//         </div>

//         {/* Danh sách các tính năng */}
//         <div className="bg-white rounded-3xl shadow-sm border border-slate-100 overflow-hidden">
          
//           {/* 1. NÚT ĐỔI MẬT KHẨU (Đã làm xong) */}
//           <button 
//             onClick={() => navigate('/change-password')}
//             className="w-full flex items-center justify-between p-6 hover:bg-slate-50 transition-colors border-b border-slate-50 group"
//           >
//             <div className="flex items-center">
//               <div className="w-12 h-12 bg-blue-50 text-blue-600 rounded-full flex items-center justify-center mr-4 group-hover:bg-blue-600 group-hover:text-white transition-colors">
//                 <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"></path></svg>
//               </div>
//               <div className="text-left">
//                 <h3 className="font-bold text-slate-800">Đổi mật khẩu</h3>
//                 <p className="text-xs text-slate-400 mt-1">Cập nhật mật khẩu đăng nhập định kỳ</p>
//               </div>
//             </div>
//             <svg className="w-5 h-5 text-slate-300 group-hover:text-blue-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7"></path></svg>
//           </button>

//           {/* 2. NÚT THIẾT LẬP FACE ID (Chờ làm tiếp theo) */}
//           <button 
//             onClick={() => navigate('/register-face')}
//             className="w-full flex items-center justify-between p-6 hover:bg-slate-50 transition-colors border-b border-slate-50 group"
//           >
//             <div className="flex items-center">
//               <div className="w-12 h-12 bg-emerald-50 text-emerald-600 rounded-full flex items-center justify-center mr-4 group-hover:bg-emerald-600 group-hover:text-white transition-colors">
//                 <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
//               </div>
//               <div className="text-left">
//                 <h3 className="font-bold text-slate-800">Dữ liệu khuôn mặt (FaceID)</h3>
//                 <p className="text-xs text-slate-400 mt-1">Quét khuôn mặt để xác thực GD rủi ro cao</p>
//               </div>
//             </div>
//             <div className="flex items-center space-x-2">
//               <span className="text-[10px] font-bold bg-orange-100 text-orange-600 px-2 py-1 rounded-md uppercase">Chưa cài đặt</span>
//               <svg className="w-5 h-5 text-slate-300 group-hover:text-emerald-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7"></path></svg>
//             </div>
//           </button>

//       {/* 3. NÚT ĐỔI MÃ PIN */}
//           <button 
//             onClick={() => navigate('/setup-pin')}
//             className="w-full flex items-center justify-between p-6 hover:bg-slate-50 transition-colors border-b border-slate-50 group"
//           >
//             <div className="flex items-center">
//               {/* Icon ổ khóa màu tím (Giữ nguyên) */}
//               <div className="w-12 h-12 bg-purple-50 text-purple-600 rounded-full flex items-center justify-center mr-4 group-hover:bg-purple-600 group-hover:text-white transition-colors">
//                 <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8V7a4 4 0 00-8 0v4h8z"></path></svg>
//               </div>
//               <div className="text-left">
//                 <h3 className="font-bold text-slate-800">Mã Smart PIN</h3>
//                 <p className="text-xs text-slate-400 mt-1">Thiết lập & Đổi mã PIN xác thực nhanh</p>
//               </div>
//             </div>
            
//             {/* Mũi tên chỉ sang phải thay cho chữ Sắp ra mắt */}
//             <svg className="w-5 h-5 text-slate-300 group-hover:text-purple-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7"></path></svg>
//           </button>

//         </div>

//         <p className="text-center text-xs text-slate-400 mt-8 font-medium">
//           Mọi thay đổi bảo mật đều được ghi log để đảm bảo an toàn.
//         </p>

//       </div>
//     </div>
//   );
// }


import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify'; // Import thêm toast để thông báo

export default function SecuritySettings() {
  const navigate = useNavigate();
  const [userData, setUserData] = useState(null);

  // 1. Kéo dữ liệu Login từ LocalStorage lên khi mở trang
  useEffect(() => {
    const storedUser = localStorage.getItem('currentUser'); // Sửa lại key này nếu bro lưu tên khác
    if (storedUser) {
      setUserData(JSON.parse(storedUser));
    }
  }, []);

  // 2. Trích xuất cờ FaceID (Check cả 2 vị trí cho chắc ăn theo cục JSON bro gửi)
  const isFaceSetup = userData?.faceSetup || userData?.user?.faceSetup || false;

  // 3. Xử lý click nút FaceID
  const handleFaceIdClick = () => {
    if (isFaceSetup) {
      // Đã cài rồi thì báo thông báo, hoặc sau này bro làm trang "Xóa/Cập nhật khuôn mặt" thì navigate tới đó
      toast.info("✅ Tài khoản của bạn đã được bảo vệ bằng FaceID!");
    } else {
      // Chưa cài thì cho đi đăng ký
      navigate('/register-face');
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 p-6 flex flex-col items-center">
      <div className="max-w-md w-full">
        
        {/* Header có nút Back */}
        <div className="flex items-center mb-8">
          <button onClick={() => navigate('/dashboard')} className="p-2 bg-white rounded-xl shadow-sm hover:bg-slate-100 transition mr-4">
            <svg className="w-6 h-6 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7"></path></svg>
          </button>
          <h1 className="text-2xl font-black text-slate-900">Trung tâm Bảo mật</h1>
        </div>

        {/* Danh sách các tính năng */}
        <div className="bg-white rounded-3xl shadow-sm border border-slate-100 overflow-hidden">
          
          {/* 1. NÚT ĐỔI MẬT KHẨU */}
          <button 
            onClick={() => navigate('/change-password')}
            className="w-full flex items-center justify-between p-6 hover:bg-slate-50 transition-colors border-b border-slate-50 group"
          >
            <div className="flex items-center">
              <div className="w-12 h-12 bg-blue-50 text-blue-600 rounded-full flex items-center justify-center mr-4 group-hover:bg-blue-600 group-hover:text-white transition-colors">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"></path></svg>
              </div>
              <div className="text-left">
                <h3 className="font-bold text-slate-800">Đổi mật khẩu</h3>
                <p className="text-xs text-slate-400 mt-1">Cập nhật mật khẩu đăng nhập định kỳ</p>
              </div>
            </div>
            <svg className="w-5 h-5 text-slate-300 group-hover:text-blue-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7"></path></svg>
          </button>

          {/* 2. NÚT THIẾT LẬP FACE ID (ĐÃ SỬA LOGIC) */}
          <button 
            onClick={handleFaceIdClick}
            className="w-full flex items-center justify-between p-6 hover:bg-slate-50 transition-colors border-b border-slate-50 group"
          >
            <div className="flex items-center">
              <div className={`w-12 h-12 rounded-full flex items-center justify-center mr-4 transition-colors ${isFaceSetup ? 'bg-green-50 text-green-600 group-hover:bg-green-600 group-hover:text-white' : 'bg-emerald-50 text-emerald-600 group-hover:bg-emerald-600 group-hover:text-white'}`}>
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
              </div>
              <div className="text-left">
                <h3 className="font-bold text-slate-800">Dữ liệu khuôn mặt (FaceID)</h3>
                <p className="text-xs text-slate-400 mt-1">Quét khuôn mặt để xác thực GD rủi ro cao</p>
              </div>
            </div>
            <div className="flex items-center space-x-2">
              {/* 🚀 ĐỔI GIAO DIỆN THEO TRẠNG THÁI */}
              {isFaceSetup ? (
                <span className="text-[10px] font-bold bg-green-100 text-green-700 px-2 py-1 rounded-md uppercase">
                  Đã cài đặt
                </span>
              ) : (
                <span className="text-[10px] font-bold bg-orange-100 text-orange-600 px-2 py-1 rounded-md uppercase">
                  Chưa cài đặt
                </span>
              )}
              
              {!isFaceSetup && (
                <svg className="w-5 h-5 text-slate-300 group-hover:text-emerald-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7"></path></svg>
              )}
            </div>
          </button>

          {/* 3. NÚT ĐỔI MÃ PIN */}
          <button 
            onClick={() => navigate('/setup-pin')}
            className="w-full flex items-center justify-between p-6 hover:bg-slate-50 transition-colors border-b border-slate-50 group"
          >
            <div className="flex items-center">
              <div className="w-12 h-12 bg-purple-50 text-purple-600 rounded-full flex items-center justify-center mr-4 group-hover:bg-purple-600 group-hover:text-white transition-colors">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8V7a4 4 0 00-8 0v4h8z"></path></svg>
              </div>
              <div className="text-left">
                <h3 className="font-bold text-slate-800">Mã Smart PIN</h3>
                <p className="text-xs text-slate-400 mt-1">Thiết lập & Đổi mã PIN xác thực nhanh</p>
              </div>
            </div>
            
            <svg className="w-5 h-5 text-slate-300 group-hover:text-purple-500 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7"></path></svg>
          </button>

        </div>

        <p className="text-center text-xs text-slate-400 mt-8 font-medium">
          Mọi thay đổi bảo mật đều được ghi log để đảm bảo an toàn.
        </p>

      </div>
    </div>
  );
}