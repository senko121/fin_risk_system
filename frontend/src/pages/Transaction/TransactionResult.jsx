import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

export default function TransactionResult() {
  const { state } = useLocation();
  const navigate = useNavigate();
  
  //   FIX LỖI 1: Gán mặc định formData = {} để nó không bị undefined nữa
  const { result, formData = {}, recipientName } = state || {};

  // Lấy thông tin người dùng hiện tại từ localStorage để làm "Người gửi"
  const storedUser = JSON.parse(localStorage.getItem('currentUser'));

  if (!result) return <div className="p-10 text-center">Không tìm thấy dữ liệu giao dịch!</div>;

  // Lấy trạng thái chốt sổ từ Backend làm tiêu chuẩn vàng
  const isSuccess = result.status === 'SUCCESS';

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-white rounded-[2.5rem] shadow-2xl overflow-hidden">
        
        {/* Header: Hiển thị trạng thái */}
        <div className={`p-8 text-center ${isSuccess ? 'bg-green-500' : 'bg-red-500'}`}>
          <div className="inline-flex items-center justify-center w-20 h-20 bg-white/20 rounded-full mb-4">
            {isSuccess ? (
              <svg className="w-12 h-12 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7"></path></svg>
            ) : (
              <svg className="w-12 h-12 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M6 18L18 6M6 6l12 12"></path></svg>
            )}
          </div>
          <h2 className="text-2xl font-black text-white uppercase tracking-tight">
            {isSuccess ? 'Giao dịch thành công' : 'Giao dịch bị chặn'}
          </h2>
          <p className="text-white/80 text-xs mt-1 font-medium">
            {new Date(result.createdAt || Date.now()).toLocaleString('vi-VN')}
          </p>
        </div>

        <div className="p-8 space-y-6">
          {/* Section 1: Phân tích AI */}
          <div className="bg-gray-50 rounded-2xl p-5 border border-gray-100">
            <h3 className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-3">Hệ thống AI bảo mật</h3>
            <div className="flex justify-between items-center mb-2">
              <span className="text-gray-600 text-sm">Mức độ rủi ro:</span>
              <span className={`text-sm font-black ${result.riskLevel === 'LOW' ? 'text-green-600' : 'text-orange-500'}`}>
                {result.riskLevel} ({Math.min(result.totalRiskScore, 100)}/100)
              </span>
            </div>
            <div className="w-full bg-gray-200 h-1.5 rounded-full overflow-hidden">
              <div 
                className={`h-full transition-all duration-1000 ${result.riskLevel === 'LOW' ? 'bg-green-500' : 'bg-orange-500'}`}
                style={{ width: `${Math.min(result.totalRiskScore, 100)}%` }}
              ></div>
            </div>
            <p className="text-[10px] mt-3 italic leading-tight">
              <span className="text-gray-400">* AI nhận diện cảm xúc: </span>
              {result.emotionSignal ? (
                <strong className="text-blue-600 uppercase">{result.emotionSignal}</strong>
              ) : (
                <span className="text-gray-400 font-medium">Không yêu cầu (Giao dịch an toàn)</span>
              )}
            </p>
          </div>

          {/* Section 2: Chi tiết chuyển tiền */}
          <div className="space-y-4">
            
            {/* THÔNG TIN NGƯỜI GỬI */}
            <div className="flex justify-between items-start">
              <span className="text-gray-400 text-sm font-medium">Từ tài khoản</span>
              <div className="text-right">
                <p className="text-gray-900 font-bold text-sm">{storedUser?.fullName || result.fromAccount?.user?.fullName || 'Tài khoản của tôi'}</p>
                <p className="text-[16px] text-gray-500 font-mono">{storedUser?.accountNumber || result.fromAccount?.accountNumber || 'N/A'}</p>
              </div>
            </div>

            <div className="border-t border-gray-100 my-2"></div>

            {/* THÔNG TIN NGƯỜI NHẬN */}
            <div className="flex justify-between items-start">
              <span className="text-gray-400 text-sm font-medium">Đến người nhận</span>
              <div className="text-right">
                <p className="text-gray-900 font-bold text-sm">{recipientName || 'Người nhận'}</p>
                {/*   FIX LỖI 3: Lấy toAccountNumber từ Backend */}
                <p className="text-[16px] text-gray-500 font-mono">{result.toAccountNumber || formData.toAccount}</p>
              </div>
            </div>

            {/* SỐ TIỀN */}
            <div className="flex justify-between pt-4 border-t border-dashed border-gray-200">
              <span className="text-gray-400 text-sm font-medium">Số tiền</span>
              <span className="text-2xl font-black text-blue-600">
                {/*   FIX LỖI 4: Lấy amount từ Backend */}
                {Number(result.amount || formData.amount || 0).toLocaleString()} <span className="text-sm">VND</span>
              </span>
            </div>

            {/* LỜI NHẮN */}
            <div className="bg-blue-50/50 p-4 rounded-xl border border-blue-100/50">
              <p className="text-[10px] text-blue-400 uppercase font-bold mb-1">Nội dung chuyển khoản</p>
              <p className="text-gray-700 text-sm font-medium italic">
                {/*   FIX LỖI 5: Lấy description từ Backend */}
                "{formData.description || result.description || "Chuyển tiền nhanh FinRisk"}"
              </p>
            </div>
          </div>

          {/* Nút điều hướng */}
          <button 
            onClick={() => navigate('/dashboard')}
            className="w-full py-4 bg-gray-900 hover:bg-black text-white rounded-2xl font-bold transition-all shadow-lg active:scale-95"
          >
            QUAY LẠI TRANG CHỦ
          </button>
        </div>
        
        {/* Footer biên lai */}
        <div className="bg-gray-50 p-4 text-center border-t border-gray-100">
          <p className="text-[10px] text-gray-400 font-medium">Giao dịch được bảo mật bởi FinRisk AI Core</p>
        </div>
      </div>
    </div>
  );
}
// import React from 'react';
// import { useLocation, useNavigate } from 'react-router-dom';

// export default function TransactionResult() {
//   const { state } = useLocation();
//   const navigate = useNavigate();
  
//   const { result, formData = {}, recipientName } = state || {};
//   const storedUser = JSON.parse(localStorage.getItem('currentUser'));

//   if (!result) return (
//     <div className="min-h-screen bg-[#f4f6f9] flex items-center justify-center font-sans">
//       <div className="text-sm font-semibold text-gray-500 uppercase tracking-widest">Không tìm thấy dữ liệu giao dịch!</div>
//     </div>
//   );

//   const isSuccess = result.status === 'SUCCESS';
//   const isLowRisk = result.riskLevel === 'LOW';

//   return (
//     <div className="min-h-screen bg-[#f4f6f9] flex flex-col font-sans">
      
//       {/* ── Khung xương FinRisk (Giữ nguyên Topbar Navy) ── */}
//       <nav className="bg-[#1e2d40] px-6 py-4 flex items-center justify-between shadow-sm">
//         <div className="flex items-center gap-3">
//           <div className="w-8 h-8 bg-white/10 border border-white/15 rounded-lg flex items-center justify-center text-white font-bold text-sm">
//             F
//           </div>
//           <div>
//             <p className="text-white font-semibold text-sm">Hệ thống an ninh</p>
//             <p className="text-white/40 text-[10px] uppercase tracking-widest">Biên lai giao dịch</p>
//           </div>
//         </div>
//       </nav>

//       {/* ── Khu vực Biên lai Full-page (Vibe Neo-Banking) ── */}
//       <div className="flex-1 max-w-2xl mx-auto w-full p-4 sm:p-6 flex flex-col">
        
//         {/* Khối giấy biên lai trắng */}
//         <div className="bg-white rounded-3xl shadow-sm border border-gray-100 overflow-hidden flex-1 flex flex-col">
          
//           {/* 1. Điểm neo thị giác: Số tiền khổng lồ (Cốt lõi của Neo-banking) */}
//           <div className="px-8 pt-12 pb-8 text-center border-b border-dashed border-gray-200">
//             <div className={`mx-auto flex items-center justify-center w-16 h-16 rounded-full mb-6 ${isSuccess ? 'bg-[#dcfce7] text-[#15803d]' : 'bg-red-50 text-[#b91c1c]'}`}>
//               {isSuccess ? (
//                 <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7"></path></svg>
//               ) : (
//                 <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M6 18L18 6M6 6l12 12"></path></svg>
//               )}
//             </div>
            
//             <p className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-2">
//               {isSuccess ? 'Chuyển tiền thành công' : 'Giao dịch thất bại'}
//             </p>
            
//             {/* Chữ font-black siêu to, VND text xám nhỏ hơn */}
//             <h1 className={`text-[2.75rem] leading-none font-black tracking-tight ${isSuccess ? 'text-gray-900' : 'text-[#b91c1c]'}`}>
//               {isSuccess ? '-' : ''}{Number(result.amount || formData.amount || 0).toLocaleString()} 
//               <span className="text-xl text-gray-400 ml-1">VND</span>
//             </h1>
//           </div>

//           {/* 2. Typography Tương phản (Label xám nhạt, Value đen đậm) */}
//           <div className="p-8 space-y-5 flex-1">
//             <div className="flex justify-between items-center">
//               <span className="text-sm font-medium text-gray-500">Mã giao dịch</span>
//               <span className="text-sm font-bold text-gray-900 font-mono">#{result.id || "N/A"}</span>
//             </div>
            
//             <div className="flex justify-between items-start">
//               <span className="text-sm font-medium text-gray-500">Thời gian</span>
//               <span className="text-sm font-bold text-gray-900 text-right">
//                 {new Date(result.createdAt || Date.now()).toLocaleString('vi-VN')}
//               </span>
//             </div>

//             <div className="flex justify-between items-start">
//               <span className="text-sm font-medium text-gray-500">Từ tài khoản</span>
//               <div className="text-right">
//                 <p className="text-sm font-bold text-gray-900">{storedUser?.fullName || result.fromAccount?.user?.fullName || 'Tài khoản của tôi'}</p>
//                 <p className="text-xs font-mono text-gray-500 mt-0.5">{storedUser?.accountNumber || result.fromAccount?.accountNumber || 'N/A'}</p>
//               </div>
//             </div>

//             <div className="flex justify-between items-start">
//               <span className="text-sm font-medium text-gray-500">Đến người nhận</span>
//               <div className="text-right">
//                 <p className="text-sm font-bold text-gray-900 truncate max-w-[180px]">{recipientName || 'Người nhận'}</p>
//                 <p className="text-xs font-mono text-gray-500 mt-0.5">{result.toAccountNumber || formData.toAccount}</p>
//               </div>
//             </div>

//             <div className="flex justify-between items-start pt-2">
//               <span className="text-sm font-medium text-gray-500">Nội dung</span>
//               <span className="text-sm font-bold text-gray-900 text-right max-w-[60%]">
//                 {formData.description || result.description || "Chuyển tiền nhanh FinRisk"}
//               </span>
//             </div>

//             {/* 3. Thẻ AI Security (Bọc nền màu nhạt - Tinted Background) */}
//             <div className={`mt-8 p-5 rounded-2xl border relative overflow-hidden ${isLowRisk ? 'bg-[#dcfce7]/50 border-green-200' : 'bg-orange-50/80 border-orange-200'}`}>
              
//               {/* Watermark */}
//               <div className={`absolute -right-4 -bottom-4 opacity-[0.05] ${isLowRisk ? 'text-[#15803d]' : 'text-[#b45309]'}`}>
//                 <svg className="w-28 h-28" fill="currentColor" viewBox="0 0 20 20"><path fillRule="evenodd" d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5c.11.65.166 1.32.166 2.001 0 5.225-3.34 9.67-8 11.317C5.34 16.67 2 12.225 2 7c0-.682.057-1.35.166-2.001zm11.541 3.708a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd"></path></svg>
//               </div>

//               <p className={`text-[10px] font-bold uppercase tracking-widest mb-3 flex items-center relative z-10 ${isLowRisk ? 'text-[#15803d]' : 'text-[#b45309]'}`}>
//                 <span className={`w-2 h-2 rounded-full mr-2 ${isLowRisk ? 'bg-[#15803d]' : 'bg-[#b45309] animate-pulse'}`}></span>
//                 Bảo mật FinRisk AI
//               </p>
              
//               <div className="space-y-2 relative z-10">
//                 <div className="flex justify-between items-center">
//                   <span className="text-sm font-medium text-gray-700">Mức độ rủi ro</span>
//                   <span className={`text-sm font-black ${isLowRisk ? 'text-[#15803d]' : 'text-[#b45309]'}`}>
//                     {result.riskLevel || 'N/A'} <span className="text-xs font-bold opacity-70">({Math.min(result.totalRiskScore, 100)}/100)</span>
//                   </span>
//                 </div>
//                 <div className="flex justify-between items-center pt-1">
//                   <span className="text-sm font-medium text-gray-700">Tín hiệu tâm lý</span>
//                   <span className="text-[10px] font-bold text-gray-800 uppercase tracking-widest bg-white px-2 py-1 rounded shadow-sm border border-gray-100">
//                     {result.emotionSignal || 'N/A'}
//                   </span>
//                 </div>
//               </div>
//             </div>
//           </div>

//         </div>

//         {/* Nút điều hướng - Trả lại dáng vẻ nút Đen to bản của Neo-Bank */}
//         <button 
//           onClick={() => navigate('/dashboard')}
//           className="w-full mt-6 py-4 bg-gray-900 hover:bg-black text-white rounded-2xl text-sm font-bold tracking-wide transition-all shadow-lg active:scale-[0.98]"
//         >
//           VỀ TRANG CHỦ QUẢN TRỊ
//         </button>
//       </div>

//     </div>
//   );
// }