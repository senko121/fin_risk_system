// import React, { useState, useEffect } from 'react';
// import { useNavigate } from 'react-router-dom';
// import axiosClient from '../../api/axiosClient';

// export default function TransferMethod() {
//   const navigate = useNavigate();
//   const [currentUser, setCurrentUser] = useState(null);
//   const [recentRecipients, setRecentRecipients] = useState([]);
//   const [favorites, setFavorites] = useState([]);
//   const [loading, setLoading] = useState(true);
//   const [searchTerm, setSearchTerm] = useState("");

//   useEffect(() => {
//     const userStr = localStorage.getItem('currentUser');
//     if (userStr) {
//       const user = JSON.parse(userStr);
//       setCurrentUser(user);
//       fetchData(user.id || user.userId);
//     } else {
//       navigate('/login');
//     }
//   }, [navigate]);

//   const fetchData = async (userId) => {
//     setLoading(true);
//     try {
//       // Nhớ fix /api dư thừa nếu chưa fix ở lượt chat trước nhé bro
//       const [recentRes, favoriteRes] = await Promise.all([
//         axiosClient.get(`/transactions/recent-recipients/${userId}`),
//         axiosClient.get(`/users/${userId}/contacts`) 
//       ]);
//       setRecentRecipients(recentRes.data);
//       setFavorites(favoriteRes.data);
//     } catch (error) {
//       console.error("Lỗi load dữ liệu:", error);
//     } finally {
//       setLoading(false);
//     }
//   };

//   const handleQuickTransfer = (accountNumber) => {
//     navigate('/transfer/stk', { state: { targetAccount: accountNumber } });
//   };

//     // Lọc danh sách gần đây
//     const filteredRecent = recentRecipients.filter(person => 
//     person.fullName.toLowerCase().includes(searchTerm.toLowerCase()) || 
//     person.accountNumber.includes(searchTerm)
//     );

//     // Lọc danh bạ yêu thích
//     const filteredFavorites = favorites.filter(contact => 
//     contact.contactName.toLowerCase().includes(searchTerm.toLowerCase()) || 
//     contact.contactAccountNumber.includes(searchTerm)
//     );

//   return (
//     <div className="min-h-screen bg-slate-50 py-12 px-4">
//       <div className="max-w-2xl mx-auto">
        
//         {/* Header */}
//         <div className="flex items-center justify-between mb-8">
//           <button onClick={() => navigate('/dashboard')} className="p-3 bg-white rounded-2xl shadow-sm text-slate-600 hover:text-blue-600 transition-all hover:shadow-md">
//             <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 19l-7-7 7-7"></path></svg>
//           </button>
//           <h2 className="text-2xl font-black text-slate-800 tracking-tight">Chuyển tiền</h2>
//           <div className="w-12 h-12"></div>
//         </div>

//         {/* SEARCH BAR */}
// <div className="relative mb-8 group">
//   <div className="absolute inset-y-0 left-5 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-600 transition-colors">
//     <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
//       <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
//     </svg>
//   </div>
//   <input 
//     type="text"
//     placeholder="Tìm tên hoặc số tài khoản..."
//     value={searchTerm}
//     onChange={(e) => setSearchTerm(e.target.value)}
//     className="w-full pl-14 pr-6 py-4 bg-white rounded-[2rem] border-none shadow-sm outline-none text-slate-700 font-bold placeholder:text-slate-300 focus:ring-2 focus:ring-blue-100 transition-all"
//   />
//   {searchTerm && (
//     <button 
//       onClick={() => setSearchTerm("")}
//       className="absolute inset-y-0 right-5 flex items-center text-slate-300 hover:text-slate-500"
//     >
//       <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
//         <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd"></path>
//       </svg>
//     </button>
//   )}
// </div>

//         {/* 1. KHỐI HÌNH THỨC CHÍNH (Giữ nguyên) */}
//         <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-10">
//             <button onClick={() => navigate('/transfer/stk')} className="group relative overflow-hidden bg-blue-600 p-6 rounded-[2.5rem] shadow-xl shadow-blue-200 transition-all hover:-translate-y-2 text-left">
//                 <div className="relative z-10 text-white">
//                     <div className="w-12 h-12 bg-white/20 rounded-2xl flex items-center justify-center mb-4 backdrop-blur-md">
//                         <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"></path></svg>
//                     </div>
//                     <p className="font-black text-[10px] uppercase tracking-wider opacity-80">Đến số</p>
//                     <h3 className="text-lg font-bold leading-tight">Tài khoản</h3>
//                 </div>
//             </button>
//             {/* ... các nút Thẻ và QR tương tự ... */}
//             <button className="group relative overflow-hidden bg-indigo-700 p-6 rounded-[2.5rem] shadow-xl shadow-indigo-200 transition-all hover:-translate-y-2 text-left">
//                 <div className="relative z-10 text-white">
//                     <div className="w-12 h-12 bg-white/20 rounded-2xl flex items-center justify-center mb-4 backdrop-blur-md">
//                         <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path></svg>
//                     </div>
//                     <p className="font-black text-[10px] uppercase tracking-wider opacity-80">Đến số</p>
//                     <h3 className="text-lg font-bold leading-tight">Thẻ</h3>
//                 </div>
//             </button>
//             <button className="group relative overflow-hidden bg-slate-900 p-6 rounded-[2.5rem] shadow-xl shadow-slate-200 transition-all hover:-translate-y-2 text-left">
//                 <div className="relative z-10 text-white">
//                     <div className="w-12 h-12 bg-white/20 rounded-2xl flex items-center justify-center mb-4 backdrop-blur-md">
//                         <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z"></path></svg>
//                     </div>
//                     <p className="font-black text-[10px] uppercase tracking-wider opacity-80">Quét mã</p>
//                     <h3 className="text-lg font-bold leading-tight">QR Code</h3>
//                 </div>
//             </button>
//         </div>

//         {/* 2. GIAO DỊCH GẦN ĐÂY (Đổi thành style AVATAR TRÒN LƯỚT NGANG) */}
//         <div className="mb-10">
//           <h3 className="text-sm font-black text-slate-400 uppercase tracking-[0.2em] mb-6">Giao dịch gần đây</h3>
//           <div className="flex space-x-6 overflow-x-auto pb-4 scrollbar-hide">
//             {filteredRecent.length > 0 ? (
//               filteredRecent.map((person, idx) => (
//                 <button 
//                   key={idx} 
//                   onClick={() => handleQuickTransfer(person.accountNumber)}
//                   className="flex flex-col items-center flex-shrink-0 group"
//                 >
//                   <div className="w-16 h-16 bg-white border-2 border-slate-100 rounded-full flex items-center justify-center text-xl font-black text-blue-600 shadow-sm group-hover:bg-blue-600 group-hover:text-white group-hover:border-blue-600 transition-all duration-300">
//                     {person.fullName.charAt(0)}
//                   </div>
//                   <span className="text-[11px] font-bold text-slate-600 mt-3 w-20 truncate text-center">{person.fullName}</span>
//                 </button>
//               ))
//             ) : (
//               <p className="text-slate-400 text-sm italic">Chưa có giao dịch gần đây</p>
//             )}
//           </div>
//         </div>

//         {/* 3. DANH BẠ YÊU THÍCH (Style DANH SÁCH DỌC - Layout dàn hàng ngang) */}
//         <div>
//         <div className="flex justify-between items-center mb-6">
//             <h3 className="text-sm font-black text-slate-400 uppercase tracking-[0.2em]">Danh bạ yêu thích</h3>
//             <button className="text-blue-600 font-bold text-xs hover:underline">+ THÊM MỚI</button>
//         </div>
        
//         <div className="space-y-3">
//             {filteredFavorites.length > 0 ? (
//             filteredFavorites.map((contact) => (
//                 <button 
//                 key={contact.id}
//                 onClick={() => handleQuickTransfer(contact.contactAccountNumber)}
//                 className="w-full bg-white p-5 rounded-[2rem] flex items-center justify-between border border-slate-50 shadow-sm hover:shadow-md hover:border-blue-100 transition-all group"
//                 >
//                 {/* PHẦN 1: AVATAR & THÔNG TIN CƠ BẢN (CHIẾM 50% THẺ) */}
//                 <div className="flex items-center space-x-4 w-1/2">
//                     <div className="relative flex-shrink-0">
//                     <div className="w-12 h-12 bg-blue-50 text-blue-600 rounded-2xl flex items-center justify-center font-black text-lg group-hover:bg-blue-600 group-hover:text-white transition-colors">
//                         {contact.contactName.charAt(0)}
//                     </div>
//                     {contact.pinned && (
//                         <div className="absolute -top-1 -right-1 bg-amber-400 text-white p-1 rounded-lg shadow-sm border border-white">
//                         <svg className="w-2.5 h-2.5" fill="currentColor" viewBox="0 0 20 20"><path d="M10 12h5l-5 5V12z"></path><path fillRule="evenodd" d="M3 5a2 2 0 012-2h10a2 2 0 012 2v8a2 2 0 01-2 2h-2.22l.123.489.804.804A1 1 0 0113 18H7a1 1 0 01-.707-1.707l.804-.804L7.22 15H5a2 2 0 01-2-2V5zm2 0v8h10V5H5z" clipRule="evenodd"></path></svg>
//                         </div>
//                     )}
//                     </div>
//                     <div className="text-left truncate">
//                     <p className="font-black text-slate-800 group-hover:text-blue-700 transition-colors truncate">
//                         {contact.contactName}
//                     </p>
//                     <p className="text-sm font-mono text-slate-500 tracking-tight">
//                         {contact.contactAccountNumber}
//                     </p>
//                     </div>
//                 </div>

//                 {/* PHẦN 2: THÔNG TIN GIAO DỊCH GẦN NHẤT (TẬN DỤNG PHẦN GIỮA TRỐNG) */}
//                 <div className="flex-1 flex justify-center px-4">
//                     {contact.lastAmount ? (
//                     <div className="bg-emerald-50 px-4 py-2 rounded-2xl border border-emerald-100 flex items-center space-x-2">
//                         <div className="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-pulse"></div>
//                         <div className="text-left">
//                         <p className="text-[9px] font-bold text-emerald-400 uppercase leading-none mb-0.5">Giao dịch cuối</p>
//                         <p className="text-xs font-black text-emerald-700 leading-none">
//                             {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(contact.lastAmount)}
//                         </p>
//                         </div>
//                     </div>
//                     ) : (
//                     <span className="text-[10px] font-bold text-slate-300 uppercase tracking-tighter italic">Chưa có lịch sử</span>
//                     )}
//                 </div>

//                 {/* PHẦN 3: ICON MŨI TÊN (BÊN PHẢI) */}
//                 <div className="text-slate-300 group-hover:text-blue-600 transition-colors flex-shrink-0 ml-4">
//                     <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
//                     <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M9 5l7 7-7 7"></path>
//                     </svg>
//                 </div>
//                 </button>
//             ))
//             ) : (
//             <div className="text-center py-8 bg-slate-100/50 rounded-[2rem] border-2 border-dashed border-slate-200">
//                 <p className="text-slate-400 text-sm font-bold">Danh bạ trống</p>
//             </div>
//             )}
//         </div>
//         </div>

//       </div>
//     </div>
//   );
// }


import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify'; // 🔥 Thêm thư viện thông báo
import QRCodeScanner from '../../components/QRCodeScanner'; // 🔥 Import Component quét QR

export default function TransferMethod() {
  const navigate = useNavigate();
  const [currentUser, setCurrentUser] = useState(null);
  const [recentRecipients, setRecentRecipients] = useState([]);
  const [favorites, setFavorites] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState("");
  
  // 🔥 State quản lý việc bật/tắt Camera
  const [showScanner, setShowScanner] = useState(false);

  useEffect(() => {
    const userStr = localStorage.getItem('currentUser');
    if (userStr) {
      const user = JSON.parse(userStr);
      setCurrentUser(user);
      fetchData(user.id || user.userId);
    } else {
      navigate('/login');
    }
  }, [navigate]);

  const fetchData = async (userId) => {
    setLoading(true);
    try {
      const [recentRes, favoriteRes] = await Promise.all([
        axiosClient.get(`/transactions/recent-recipients/${userId}`),
        axiosClient.get(`/users/${userId}/contacts`) 
      ]);
      setRecentRecipients(recentRes.data);
      setFavorites(favoriteRes.data);
    } catch (error) {
      console.error("Lỗi load dữ liệu:", error);
    } finally {
      setLoading(false);
    }
  };

  const handleQuickTransfer = (accountNumber) => {
    navigate('/transfer/stk', { state: { targetAccount: accountNumber } });
  };

  // 🔥 Hàm xử lý khi Quét QR Thành Công
  const handleQRSuccess = (decodedText) => {
    if (decodedText && decodedText.startsWith("FINRISK|")) {
      const parts = decodedText.split("|");
      const accountNumber = parts[1];
      
      setShowScanner(false); // Đóng camera
      toast.success("✅ Đã nhận diện mã QR thành công!");
      
      // Chuyển thẳng sang trang nhập tiền giống như bấm Quick Transfer
      handleQuickTransfer(accountNumber);
    } else {
      toast.error("❌ Mã QR không hợp lệ hoặc không thuộc hệ thống FinRisk!");
    }
  };

  // Lọc danh sách gần đây
  const filteredRecent = recentRecipients.filter(person => 
    person.fullName.toLowerCase().includes(searchTerm.toLowerCase()) || 
    person.accountNumber.includes(searchTerm)
  );

  // Lọc danh bạ yêu thích
  const filteredFavorites = favorites.filter(contact => 
    contact.contactName.toLowerCase().includes(searchTerm.toLowerCase()) || 
    contact.contactAccountNumber.includes(searchTerm)
  );

  return (
    <div className="min-h-screen bg-slate-50 py-12 px-4 relative">
      <div className="max-w-2xl mx-auto">
        
        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <button onClick={() => navigate('/dashboard')} className="p-3 bg-white rounded-2xl shadow-sm text-slate-600 hover:text-blue-600 transition-all hover:shadow-md">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 19l-7-7 7-7"></path></svg>
          </button>
          <h2 className="text-2xl font-black text-slate-800 tracking-tight">Chuyển tiền</h2>
          <div className="w-12 h-12"></div>
        </div>

        {/* SEARCH BAR */}
        <div className="relative mb-8 group">
          <div className="absolute inset-y-0 left-5 flex items-center pointer-events-none text-slate-400 group-focus-within:text-blue-600 transition-colors">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
            </svg>
          </div>
          <input 
            type="text"
            placeholder="Tìm tên hoặc số tài khoản..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-14 pr-6 py-4 bg-white rounded-[2rem] border-none shadow-sm outline-none text-slate-700 font-bold placeholder:text-slate-300 focus:ring-2 focus:ring-blue-100 transition-all"
          />
          {searchTerm && (
            <button 
              onClick={() => setSearchTerm("")}
              className="absolute inset-y-0 right-5 flex items-center text-slate-300 hover:text-slate-500"
            >
              <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd"></path>
              </svg>
            </button>
          )}
        </div>

        {/* 1. KHỐI HÌNH THỨC CHÍNH */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-10">
            <button onClick={() => navigate('/transfer/stk')} className="group relative overflow-hidden bg-blue-600 p-6 rounded-[2.5rem] shadow-xl shadow-blue-200 transition-all hover:-translate-y-2 text-left">
                <div className="relative z-10 text-white">
                    <div className="w-12 h-12 bg-white/20 rounded-2xl flex items-center justify-center mb-4 backdrop-blur-md">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"></path></svg>
                    </div>
                    <p className="font-black text-[10px] uppercase tracking-wider opacity-80">Đến số</p>
                    <h3 className="text-lg font-bold leading-tight">Tài khoản</h3>
                </div>
            </button>
            <button className="group relative overflow-hidden bg-indigo-700 p-6 rounded-[2.5rem] shadow-xl shadow-indigo-200 transition-all hover:-translate-y-2 text-left">
                <div className="relative z-10 text-white">
                    <div className="w-12 h-12 bg-white/20 rounded-2xl flex items-center justify-center mb-4 backdrop-blur-md">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path></svg>
                    </div>
                    <p className="font-black text-[10px] uppercase tracking-wider opacity-80">Đến số</p>
                    <h3 className="text-lg font-bold leading-tight">Thẻ</h3>
                </div>
            </button>
            
            {/* 🔥 NÚT BẬT CAMERA QR (Đã gắn sự kiện onClick) */}
            <button 
              onClick={() => setShowScanner(true)} 
              className="group relative overflow-hidden bg-slate-900 p-6 rounded-[2.5rem] shadow-xl shadow-slate-200 transition-all hover:-translate-y-2 text-left"
            >
                <div className="relative z-10 text-white">
                    <div className="w-12 h-12 bg-white/20 rounded-2xl flex items-center justify-center mb-4 backdrop-blur-md">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z"></path></svg>
                    </div>
                    <p className="font-black text-[10px] uppercase tracking-wider opacity-80">Quét mã</p>
                    <h3 className="text-lg font-bold leading-tight">QR Code</h3>
                </div>
            </button>
        </div>

        {/* 2. GIAO DỊCH GẦN ĐÂY */}
        <div className="mb-10">
          <h3 className="text-sm font-black text-slate-400 uppercase tracking-[0.2em] mb-6">Giao dịch gần đây</h3>
          <div className="flex space-x-6 overflow-x-auto pb-4 scrollbar-hide">
            {filteredRecent.length > 0 ? (
              filteredRecent.map((person, idx) => (
                <button 
                  key={idx} 
                  onClick={() => handleQuickTransfer(person.accountNumber)}
                  className="flex flex-col items-center flex-shrink-0 group"
                >
                  <div className="w-16 h-16 bg-white border-2 border-slate-100 rounded-full flex items-center justify-center text-xl font-black text-blue-600 shadow-sm group-hover:bg-blue-600 group-hover:text-white group-hover:border-blue-600 transition-all duration-300">
                    {person.fullName.charAt(0)}
                  </div>
                  <span className="text-[11px] font-bold text-slate-600 mt-3 w-20 truncate text-center">{person.fullName}</span>
                </button>
              ))
            ) : (
              <p className="text-slate-400 text-sm italic">Chưa có giao dịch gần đây</p>
            )}
          </div>
        </div>

        {/* 3. DANH BẠ YÊU THÍCH */}
        <div>
        <div className="flex justify-between items-center mb-6">
            <h3 className="text-sm font-black text-slate-400 uppercase tracking-[0.2em]">Danh bạ yêu thích</h3>
            <button className="text-blue-600 font-bold text-xs hover:underline">+ THÊM MỚI</button>
        </div>
        
        <div className="space-y-3">
            {filteredFavorites.length > 0 ? (
            filteredFavorites.map((contact) => (
                <button 
                key={contact.id}
                onClick={() => handleQuickTransfer(contact.contactAccountNumber)}
                className="w-full bg-white p-5 rounded-[2rem] flex items-center justify-between border border-slate-50 shadow-sm hover:shadow-md hover:border-blue-100 transition-all group"
                >
                <div className="flex items-center space-x-4 w-1/2">
                    <div className="relative flex-shrink-0">
                    <div className="w-12 h-12 bg-blue-50 text-blue-600 rounded-2xl flex items-center justify-center font-black text-lg group-hover:bg-blue-600 group-hover:text-white transition-colors">
                        {contact.contactName.charAt(0)}
                    </div>
                    {contact.pinned && (
                        <div className="absolute -top-1 -right-1 bg-amber-400 text-white p-1 rounded-lg shadow-sm border border-white">
                        <svg className="w-2.5 h-2.5" fill="currentColor" viewBox="0 0 20 20"><path d="M10 12h5l-5 5V12z"></path><path fillRule="evenodd" d="M3 5a2 2 0 012-2h10a2 2 0 012 2v8a2 2 0 01-2 2h-2.22l.123.489.804.804A1 1 0 0113 18H7a1 1 0 01-.707-1.707l.804-.804L7.22 15H5a2 2 0 01-2-2V5zm2 0v8h10V5H5z" clipRule="evenodd"></path></svg>
                        </div>
                    )}
                    </div>
                    <div className="text-left truncate">
                    <p className="font-black text-slate-800 group-hover:text-blue-700 transition-colors truncate">
                        {contact.contactName}
                    </p>
                    <p className="text-sm font-mono text-slate-500 tracking-tight">
                        {contact.contactAccountNumber}
                    </p>
                    </div>
                </div>

                <div className="flex-1 flex justify-center px-4">
                    {contact.lastAmount ? (
                    <div className="bg-emerald-50 px-4 py-2 rounded-2xl border border-emerald-100 flex items-center space-x-2">
                        <div className="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-pulse"></div>
                        <div className="text-left">
                        <p className="text-[9px] font-bold text-emerald-400 uppercase leading-none mb-0.5">Giao dịch cuối</p>
                        <p className="text-xs font-black text-emerald-700 leading-none">
                            {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(contact.lastAmount)}
                        </p>
                        </div>
                    </div>
                    ) : (
                    <span className="text-[10px] font-bold text-slate-300 uppercase tracking-tighter italic">Chưa có lịch sử</span>
                    )}
                </div>

                <div className="text-slate-300 group-hover:text-blue-600 transition-colors flex-shrink-0 ml-4">
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M9 5l7 7-7 7"></path>
                    </svg>
                </div>
                </button>
            ))
            ) : (
            <div className="text-center py-8 bg-slate-100/50 rounded-[2rem] border-2 border-dashed border-slate-200">
                <p className="text-slate-400 text-sm font-bold">Danh bạ trống</p>
            </div>
            )}
        </div>
        </div>

      </div>

      {/* 🔥 MODAL CAMERA QUÉT MÃ QR NỔI LÊN TRÊN CÙNG */}
      {showScanner && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/70 backdrop-blur-sm px-4">
          <div className="bg-white w-full max-w-md rounded-[2.5rem] p-2 shadow-2xl animate-fadeIn">
            <QRCodeScanner 
              onScanSuccess={handleQRSuccess} 
              onScanCancel={() => setShowScanner(false)} 
            />
          </div>
        </div>
      )}

    </div>
  );
}