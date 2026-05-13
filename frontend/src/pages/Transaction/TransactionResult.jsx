// import React from 'react';
// import { useLocation, useNavigate } from 'react-router-dom';

// export default function TransactionResult() {
//   const { state } = useLocation();
//   const navigate = useNavigate();
  
//   //   FIX LỖI 1: Gán mặc định formData = {} để nó không bị undefined nữa
//   const { result, formData = {}, recipientName } = state || {};

//   // Lấy thông tin người dùng hiện tại từ localStorage để làm "Người gửi"
//   const storedUser = JSON.parse(localStorage.getItem('currentUser'));

//   if (!result) return <div className="p-10 text-center">Không tìm thấy dữ liệu giao dịch!</div>;

//   // Lấy trạng thái chốt sổ từ Backend làm tiêu chuẩn vàng
//   const isSuccess = result.status === 'SUCCESS';

//   return (
//     <div className="w-full min-h-screen flex items-center justify-center p-4">
//       <div className="max-w-md w-full bg-white rounded-[2.5rem] shadow-2xl overflow-hidden">
        
//         {/* Header: Hiển thị trạng thái */}
//         <div className={`p-8 text-center ${isSuccess ? 'bg-green-500' : 'bg-red-500'}`}>
//           <div className="inline-flex items-center justify-center w-20 h-20 bg-white/20 rounded-full mb-4">
//             {isSuccess ? (
//               <svg className="w-12 h-12 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7"></path></svg>
//             ) : (
//               <svg className="w-12 h-12 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M6 18L18 6M6 6l12 12"></path></svg>
//             )}
//           </div>
//           <h2 className="text-2xl font-black text-white uppercase tracking-tight">
//             {isSuccess ? 'Giao dịch thành công' : 'Giao dịch bị chặn'}
//           </h2>
//           <p className="text-white/80 text-xs mt-1 font-medium">
//             {new Date(result.createdAt || Date.now()).toLocaleString('vi-VN')}
//           </p>
//         </div>

//         <div className="p-8 space-y-6">
//           {/* Section 1: Phân tích AI */}
//           <div className="bg-gray-50 rounded-2xl p-5 border border-gray-100">
//             <h3 className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-3">Hệ thống AI bảo mật</h3>
//             <div className="flex justify-between items-center mb-2">
//               <span className="text-gray-600 text-sm">Mức độ rủi ro:</span>
//               <span className={`text-sm font-black ${result.riskLevel === 'LOW' ? 'text-green-600' : 'text-orange-500'}`}>
//                 {result.riskLevel} ({Math.min(result.totalRiskScore, 100)}/100)
//               </span>
//             </div>
//             <div className="w-full bg-gray-200 h-1.5 rounded-full overflow-hidden">
//               <div 
//                 className={`h-full transition-all duration-1000 ${result.riskLevel === 'LOW' ? 'bg-green-500' : 'bg-orange-500'}`}
//                 style={{ width: `${Math.min(result.totalRiskScore, 100)}%` }}
//               ></div>
//             </div>
//             <p className="text-[10px] mt-3 italic leading-tight">
//               <span className="text-gray-400">* AI nhận diện cảm xúc: </span>
//               {result.emotionSignal ? (
//                 <strong className="text-blue-600 uppercase">{result.emotionSignal}</strong>
//               ) : (
//                 <span className="text-gray-400 font-medium">Không yêu cầu (Giao dịch an toàn)</span>
//               )}
//             </p>
//           </div>

//           {/* Section 2: Chi tiết chuyển tiền */}
//           <div className="space-y-4">
            
//             {/* THÔNG TIN NGƯỜI GỬI */}
//             <div className="flex justify-between items-start">
//               <span className="text-gray-400 text-sm font-medium">Từ tài khoản</span>
//               <div className="text-right">
//                 <p className="text-gray-900 font-bold text-sm">{storedUser?.fullName || result.fromAccount?.user?.fullName || 'Tài khoản của tôi'}</p>
//                 <p className="text-[16px] text-gray-500 font-mono">{storedUser?.accountNumber || result.fromAccount?.accountNumber || 'N/A'}</p>
//               </div>
//             </div>

//             <div className="border-t border-gray-100 my-2"></div>

//             {/* THÔNG TIN NGƯỜI NHẬN */}
//             <div className="flex justify-between items-start">
//               <span className="text-gray-400 text-sm font-medium">Đến người nhận</span>
//               <div className="text-right">
//                 <p className="text-gray-900 font-bold text-sm">{recipientName || 'Người nhận'}</p>
//                 {/*   FIX LỖI 3: Lấy toAccountNumber từ Backend */}
//                 <p className="text-[16px] text-gray-500 font-mono">{result.toAccountNumber || formData.toAccount}</p>
//               </div>
//             </div>

//             {/* SỐ TIỀN */}
//             <div className="flex justify-between pt-4 border-t border-dashed border-gray-200">
//               <span className="text-gray-400 text-sm font-medium">Số tiền</span>
//               <span className="text-2xl font-black text-blue-600">
//                 {/*   FIX LỖI 4: Lấy amount từ Backend */}
//                 {Number(result.amount || formData.amount || 0).toLocaleString()} <span className="text-sm">VND</span>
//               </span>
//             </div>

//             {/* LỜI NHẮN */}
//             <div className="bg-blue-50/50 p-4 rounded-xl border border-blue-100/50">
//               <p className="text-[10px] text-blue-400 uppercase font-bold mb-1">Nội dung chuyển khoản</p>
//               <p className="text-gray-700 text-sm font-medium italic">
//                 {/*   FIX LỖI 5: Lấy description từ Backend */}
//                 "{formData.description || result.description || "Chuyển tiền nhanh FinRisk"}"
//               </p>
//             </div>
//           </div>

//           {/* Nút điều hướng */}
//           <button 
//             onClick={() => navigate('/dashboard')}
//             className="w-full py-4 bg-gray-900 hover:bg-black text-white rounded-2xl font-bold transition-all shadow-lg active:scale-95"
//           >
//             QUAY LẠI TRANG CHỦ
//           </button>
//         </div>
        
//         {/* Footer biên lai */}
//         <div className="bg-gray-50 p-4 text-center border-t border-gray-100">
//           <p className="text-[10px] text-gray-400 font-medium">Giao dịch được bảo mật bởi FinRisk AI Core</p>
//         </div>
//       </div>
//     </div>
//   );
// }















// import React from 'react';
// import { useLocation, useNavigate } from 'react-router-dom';

// export default function TransactionResult() {
//   const { state } = useLocation();
//   const navigate = useNavigate();
  
//   // FIX LỖI 1: Gán mặc định formData = {} để nó không bị undefined nữa
//   const { result, formData = {}, recipientName } = state || {};

//   // Lấy thông tin người dùng hiện tại từ localStorage để làm "Người gửi"
//   const storedUser = JSON.parse(localStorage.getItem('currentUser'));

//   if (!result) return <div className="p-10 text-center">Không tìm thấy dữ liệu giao dịch!</div>;

//   // Lấy trạng thái chốt sổ từ Backend làm tiêu chuẩn vàng
//   const isSuccess = result.status === 'SUCCESS';

//   return (
//     <div className="w-full min-h-screen flex items-center justify-center p-4">
//       <div className="max-w-md w-full bg-white rounded-[2.5rem] shadow-2xl overflow-hidden">
        
//         {/* Header: Hiển thị trạng thái */}
//         <div className={`p-8 text-center ${isSuccess ? 'bg-green-500' : 'bg-red-500'}`}>
//           <div className="inline-flex items-center justify-center w-20 h-20 bg-white/20 rounded-full mb-4">
//             {isSuccess ? (
//               <svg className="w-12 h-12 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7"></path></svg>
//             ) : (
//               <svg className="w-12 h-12 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M6 18L18 6M6 6l12 12"></path></svg>
//             )}
//           </div>
//           <h2 className="text-2xl font-black text-white uppercase tracking-tight">
//             {isSuccess ? 'Giao dịch thành công' : 'Giao dịch bị chặn'}
//           </h2>
//           <p className="text-white/80 text-xs mt-1 font-medium">
//             {new Date(result.createdAt || Date.now()).toLocaleString('vi-VN')}
//           </p>
//         </div>

//         <div className="p-8 space-y-6">
//           {/* Chi tiết chuyển tiền */}
//           <div className="space-y-4">
            
//             {/* THÔNG TIN NGƯỜI GỬI */}
//             <div className="flex justify-between items-start">
//               <span className="text-gray-400 text-sm font-medium">Từ tài khoản</span>
//               <div className="text-right">
//                 <p className="text-gray-900 font-bold text-sm">{storedUser?.fullName || result.fromAccount?.user?.fullName || 'Tài khoản của tôi'}</p>
//                 <p className="text-[16px] text-gray-500 font-mono">{storedUser?.accountNumber || result.fromAccount?.accountNumber || 'N/A'}</p>
//               </div>
//             </div>

//             <div className="border-t border-gray-100 my-2"></div>

//             {/* THÔNG TIN NGƯỜI NHẬN */}
//             <div className="flex justify-between items-start">
//               <span className="text-gray-400 text-sm font-medium">Đến người nhận</span>
//               <div className="text-right">
//                 <p className="text-gray-900 font-bold text-sm">{recipientName || 'Người nhận'}</p>
//                 <p className="text-[16px] text-gray-500 font-mono">{result.toAccountNumber || formData.toAccount}</p>
//               </div>
//             </div>

//             {/* SỐ TIỀN */}
//             <div className="flex justify-between pt-4 border-t border-dashed border-gray-200">
//               <span className="text-gray-400 text-sm font-medium">Số tiền</span>
//               <span className="text-2xl font-black text-blue-600">
//                 {Number(result.amount || formData.amount || 0).toLocaleString()} <span className="text-sm">VND</span>
//               </span>
//             </div>

//             {/* LỜI NHẮN */}
//             <div className="bg-blue-50/50 p-4 rounded-xl border border-blue-100/50">
//               <p className="text-[10px] text-blue-400 uppercase font-bold mb-1">Nội dung chuyển khoản</p>
//               <p className="text-gray-700 text-sm font-medium italic">
//                 "{formData.description || result.description || "Chuyển tiền nhanh FinRisk"}"
//               </p>
//             </div>
//           </div>

//           {/* Nút điều hướng */}
//           <button 
//             onClick={() => navigate('/dashboard')}
//             className="w-full py-4 bg-gray-900 hover:bg-black text-white rounded-2xl font-bold transition-all shadow-lg active:scale-95"
//           >
//             QUAY LẠI TRANG CHỦ
//           </button>
//         </div>
        
//         {/* Footer biên lai */}
//         <div className="bg-gray-50 p-4 text-center border-t border-gray-100">
//           <p className="text-[10px] text-gray-400 font-medium">Giao dịch được bảo mật bởi FinRisk AI Core</p>
//         </div>
//       </div>
//     </div>
//   );
// }

import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useHeaderOverride } from '../../context/HeaderContext';

export default function TransactionResult() {
  const { state } = useLocation();
  const navigate = useNavigate();

  const { result, formData = {}, recipientName } = state || {};
  const storedUser = JSON.parse(localStorage.getItem('currentUser'));

  useHeaderOverride('Biên Nhận', '', () => navigate('/dashboard'));

  if (!result) return <div className="p-10 text-center">Không tìm thấy dữ liệu giao dịch!</div>;

  const isSuccess = result.status === 'SUCCESS';
  const txId = result.id || result.transactionId || '—';

  return (
    <div className="w-full min-h-screen flex flex-col items-center p-4 pt-6" style={{ background: '#f0f2f6' }}>

      <div className="w-full max-w-md">

        {/* ── BRAND + MÃ GD ── */}
        <div className="flex items-center justify-between mb-3 px-1">
          <div className="flex items-center gap-2">
            <div className="flex items-center justify-center font-black text-white text-sm"
              style={{ width: 30, height: 30, background: '#1a56db', borderRadius: 9, letterSpacing: '-0.5px' }}>
              FR
            </div>
            {/* Tăng từ text-sm lên text-base */}
            <span className="font-black text-base tracking-widest" style={{ color: '#0d1b2a' }}>FINRISK</span>
          </div>
          <div className="flex items-center gap-1.5">
            {/* Tăng từ text-xs lên text-sm, làm đậm màu xám */}
            <span className="text-sm font-bold" style={{ color: '#64748b' }}>Mã GD: {txId}</span>
            <button
              onClick={() => navigator.clipboard?.writeText(String(txId))}
              style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 2, color: '#64748b' }}
              title="Sao chép"
            >
              <svg width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <rect x="9" y="9" width="13" height="13" rx="2" />
                <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" />
              </svg>
            </button>
          </div>
        </div>

        {/* ── MAIN CARD ── */}
        <div className="w-full overflow-hidden" style={{ background: 'white', borderRadius: 28, boxShadow: '0 4px 24px rgba(13,27,42,0.08)' }}>

          {/* Status header */}
          <div className={`p-8 text-center ${isSuccess ? 'bg-green-500' : 'bg-red-500'}`}>
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-full mb-4"
              style={{ background: 'rgba(255,255,255,0.22)', border: '2px solid rgba(255,255,255,0.5)' }}>
              {isSuccess ? (
                <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7" />
                </svg>
              ) : (
                <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M6 18L18 6M6 6l12 12" />
                </svg>
              )}
            </div>
            {/* Tăng size chữ trạng thái từ text-xl lên text-2xl */}
            <h2 className="text-2xl font-black text-white" style={{ letterSpacing: '-0.3px' }}>
              {isSuccess ? 'Giao dịch thành công' : 'Giao dịch bị chặn'}
            </h2>
            {/* Tăng text-xs lên text-sm, tăng độ rõ (opacity) */}
            <p className="text-sm font-semibold mt-1" style={{ color: 'rgba(255,255,255,0.95)' }}>
              {new Date(result.createdAt || Date.now()).toLocaleString('vi-VN')}
            </p>
          </div>

          <ScallopDivider />

          {/* Số tiền */}
          <div className="text-center pt-5 pb-2 px-6">
            {/* Tăng text-xs lên text-sm, đổi sang xám đậm hơn */}
            <p className="text-sm font-black uppercase mb-2" style={{ color: '#64748b', letterSpacing: '0.12em' }}>
              Chuyển Tiền
            </p>
            {/* Tăng kích cỡ chữ số tiền từ 36 lên 44 */}
            <p className="font-black" style={{ fontSize: 44, color: '#0d1b2a', letterSpacing: '-1px', lineHeight: 1.1 }}>
              {Number(result.amount || formData.amount || 0).toLocaleString()}
              {/* Tăng text-sm lên text-base */}
              <span className="text-base font-bold ml-2" style={{ color: '#64748b' }}>VND</span>
            </p>
          </div>

          <div className="mx-6 my-4" style={{ borderTop: '1.5px dashed #e2e8f0' }} />

          {/* ── INFO ROWS ── */}
          <div className="px-6 pb-2">

            <InfoRow label="TỪ">
              {/* text-sm -> text-base */}
              <p className="font-black text-base" style={{ color: '#0d1b2a' }}>
                {storedUser?.fullName || result.fromAccount?.user?.fullName || 'Tài khoản của tôi'}
              </p>
              {/* text-xs -> text-sm, xám nhạt -> xám đậm */}
              <p className="text-sm font-bold mt-0.5" style={{ color: '#64748b' }}>FinRisk</p>
              <p className="font-mono text-sm mt-0.5" style={{ color: '#475569' }}>
                {storedUser?.accountNumber || result.fromAccount?.accountNumber || 'N/A'}
              </p>
            </InfoRow>

            <div style={{ borderTop: '1px solid #f1f5f9', margin: '14px 0' }} />

            <InfoRow label="ĐẾN">
              <p className="font-black text-base" style={{ color: '#0d1b2a' }}>
                {recipientName || 'Người nhận'}
              </p>
              <p className="font-mono text-sm mt-0.5" style={{ color: '#475569' }}>
                {result.toAccountNumber || formData.toAccount}
              </p>
            </InfoRow>

            <div style={{ borderTop: '1px solid #f1f5f9', margin: '14px 0' }} />

            <InfoRow label="MÔ TẢ">
              {/* Tăng từ text-sm lên text-base, màu đậm hơn */}
              <p className="text-base font-semibold italic" style={{ color: '#334155', lineHeight: 1.5 }}>
                "{formData.description || result.description || 'Chuyển tiền nhanh FinRisk'}"
              </p>
            </InfoRow>

          </div>

          <div style={{ height: 24 }} />
        </div>

        {/* ── BUTTONS ── */}
        <div className="flex gap-3 mt-4">
          <button
            onClick={() => navigate('/dashboard')}
            className="flex-1 py-4 font-black transition-all active:scale-95"
            // Tăng từ fontSize: 14 lên 16
            style={{ background: '#0d1b2a', color: 'white', borderRadius: 18, border: 'none', cursor: 'pointer', fontSize: 16 }}
          >
            Về trang chủ
          </button>
          <button
            onClick={() => navigate('/transfer')}
            className="flex-1 py-4 font-black transition-all active:scale-95"
            // Tăng từ fontSize: 14 lên 16
            style={{ background: 'white', color: '#0d1b2a', borderRadius: 18, border: '1.5px solid #e2e8f0', cursor: 'pointer', fontSize: 16 }}
          >
            Chuyển tiếp
          </button>
        </div>

        {/* Tăng từ text-xs lên text-sm, làm đậm màu xám */}
        <p className="text-center text-sm font-bold mt-4" style={{ color: '#94a3b8' }}>
          Giao dịch được bảo mật bởi FinRisk AI Core
        </p>

      </div>
    </div>
  );
}

function ScallopDivider() {
  return (
    <div style={{ position: 'relative', height: 20, background: 'white', overflow: 'hidden' }}>
      <div style={{ position: 'absolute', top: 0, left: 0, right: 0, display: 'flex' }}>
        {Array.from({ length: 20 }).map((_, i) => (
          <div key={i} style={{
            flex: 1, height: 20,
            borderRadius: '0 0 50% 50%',
            background: '#f0f2f6',
            marginTop: -10,
          }} />
        ))}
      </div>
    </div>
  );
}

function InfoRow({ label, children }) {
  return (
    <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', padding: '4px 0' }}>
      <span style={{
        // Tăng từ 10 lên 13, màu đậm hơn (#94a3b8 -> #64748b)
        fontSize: 13, fontWeight: 800, color: '#64748b',
        letterSpacing: '0.1em', textTransform: 'uppercase',
        minWidth: 48, paddingTop: 2,
      }}>
        {label}
      </span>
      <div style={{ flex: 1 }}>{children}</div>
    </div>
  );
}