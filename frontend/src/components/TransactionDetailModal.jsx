// import React from 'react';

// export default function TransactionDetailModal({ isOpen, onClose, transaction, formatMoney, formatDate }) {
//   // Nếu không mở hoặc không có dữ liệu giao dịch thì không render gì cả
//   if (!isOpen || !transaction) return null;

//   return (
//     <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-gray-900/60 backdrop-blur-sm transition-opacity">
//       {/* Vùng bấm ra ngoài để đóng */}
//       <div className="absolute inset-0" onClick={onClose}></div>
      
//       {/* Nội dung tấm thẻ Popup */}
//       <div className="relative bg-white rounded-3xl w-full max-w-md shadow-2xl overflow-hidden transform transition-all animate-fade-in-up">
        
//         {/* Header Popup */}
//         <div className="flex justify-between items-center p-6 border-b border-gray-100 bg-gray-50/50">
//           <h3 className="text-lg font-bold text-gray-800">Chi tiết giao dịch</h3>
//           <button 
//             onClick={onClose}
//             className="text-gray-400 hover:text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-full p-2 transition-colors"
//           >
//             <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
//           </button>
//         </div>

//         {/* Body Popup */}
//         <div className="p-6">
//           <div className="text-center mb-8">
//             <p className="text-sm font-medium text-gray-500 mb-1">Số tiền giao dịch</p>
//             <h4 className={`text-4xl font-black ${transaction.type === 'CREDIT' ? 'text-green-600' : 'text-gray-900'}`}>
//               {transaction.type === 'CREDIT' ? '+' : '-'}{formatMoney(transaction.amount)}
//             </h4>
//             <span className="inline-block mt-3 px-3 py-1 bg-green-100 text-green-700 text-xs font-bold rounded-full">
//               ✓ Thành công
//             </span>
//           </div>

//           <div className="space-y-4 text-sm">
//             <div className="flex justify-between border-b border-gray-50 pb-3">
//               <span className="text-gray-500">Mã giao dịch</span>
//               <span className="font-mono font-bold text-gray-800">#{transaction.id * 10203}</span>
//             </div>
//             {/* THÊM TÀI KHOẢN NGƯỜI NHẬN */}
//             {/* 🚀 HIỂN THỊ TÊN NGƯỜI GỬI / NHẬN (Sử dụng relatedName từ Backend) */}
//             <div className="flex justify-between items-center border-b border-gray-50 pb-3">
//               <span className="text-gray-500 whitespace-nowrap">
//                 {transaction.type === 'CREDIT' ? 'Nhận từ' : 'Chuyển đến'}
//               </span>
//               <span className="font-bold text-gray-800 text-right max-w-[60%] truncate" title={transaction.relatedName}>
//                 {transaction.relatedName || 'Người nhận'}
//               </span>
//             </div>

//             {/* 🚀 NẾU LÀ CHUYỂN ĐI (DEBIT), HIỆN THÊM SỐ TÀI KHOẢN ĐỂ DỄ ĐỐI CHIẾU */}
//             {transaction.type === 'DEBIT' && transaction.toAccountNumber && transaction.toAccountNumber !== "N/A" && (
//               <div className="flex justify-between items-center border-b border-gray-50 pb-3">
//                 <span className="text-gray-500">STK thụ hưởng</span>
//                 <span className="font-mono font-bold text-gray-800">{transaction.toAccountNumber}</span>
//               </div>
//             )}
//             <div className="flex justify-between border-b border-gray-50 pb-3">
//               <span className="text-gray-500">Thời gian</span>
//               <span className="font-medium text-gray-800">{formatDate(transaction.date)}</span>
//             </div>
//             <div className="flex justify-between border-b border-gray-50 pb-3">
//               <span className="text-gray-500">Nội dung</span>
//               <span className="font-medium text-gray-800 text-right max-w-[60%]">{transaction.description}</span>
//             </div>
//             <div className="flex justify-between pb-3">
//               <span className="text-gray-500">Số dư sau GD</span>
//               <span className="font-bold text-blue-600">{formatMoney(transaction.balanceAfter)}</span>
//             </div>
//           </div>

//           {/*   KHU VỰC AI SECURITY (Đã lắp đạn thật) */}
//           <div className="mt-6 p-4 rounded-2xl border border-orange-200 bg-orange-50/50 relative overflow-hidden">
//             <div className="absolute top-0 right-0 p-2 opacity-10">
//               <svg className="w-16 h-16" fill="currentColor" viewBox="0 0 20 20"><path fillRule="evenodd" d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5c.11.65.166 1.32.166 2.001 0 5.225-3.34 9.67-8 11.317C5.34 16.67 2 12.225 2 7c0-.682.057-1.35.166-2.001zm11.541 3.708a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd"></path></svg>
//             </div>
//             <p className="text-xs font-bold text-orange-600 uppercase tracking-wider mb-3 flex items-center">
//               <span className="w-2 h-2 rounded-full bg-orange-500 mr-2 animate-pulse"></span>
//               Bảo mật FinRisk AI
//             </p>
            
//             <div className="space-y-2 text-sm relative z-10">
//               <div className="flex justify-between items-center border-b border-orange-100/50 pb-2">
//                 <span className="text-gray-700 font-medium">Mức độ rủi ro:</span>
//                 <span className={`font-black ${
//                   transaction.riskLevel === 'HIGH' ? 'text-red-600' : 
//                   transaction.riskLevel === 'MEDIUM' ? 'text-orange-500' : 'text-green-600'
//                 }`}>
//                   {transaction.riskLevel || 'N/A'} 
//                   <span className="text-xs ml-1 font-bold opacity-70">
//                     ({transaction.totalRiskScore || 0}/100)
//                   </span>
//                 </span>
//               </div>
//               <div className="flex justify-between items-center pt-1">
//                 <span className="text-gray-700 font-medium">Phân tích Cảm xúc:</span>
//                 <span className="font-bold text-gray-800 uppercase bg-white px-2 py-1 rounded-md shadow-sm border border-gray-100">
//                   {transaction.emotionSignal || 'N/A'}
//                 </span>
//               </div>
//             </div>
//           </div>

//         </div>
//       </div>
//     </div>
//   );
// }



import React from 'react';

export default function TransactionDetailModal({ isOpen, onClose, transaction, formatMoney, formatDate }) {
  // Nếu không mở hoặc không có dữ liệu giao dịch thì không render gì cả
  if (!isOpen || !transaction) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-gray-900/60 backdrop-blur-sm transition-opacity">
      {/* Vùng bấm ra ngoài để đóng */}
      <div className="absolute inset-0" onClick={onClose}></div>
      
      {/* Nội dung tấm thẻ Popup */}
      <div className="relative bg-white rounded-3xl w-full max-w-md shadow-2xl overflow-hidden transform transition-all animate-fade-in-up">
        
        {/* Header Popup */}
        <div className="flex justify-between items-center p-6 border-b border-gray-100 bg-gray-50/50">
          <h3 className="text-lg font-bold text-gray-800">Chi tiết giao dịch</h3>
          <button 
            onClick={onClose}
            className="text-gray-400 hover:text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-full p-2 transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
          </button>
        </div>

        {/* Body Popup */}
        <div className="p-6">
          <div className="text-center mb-8">
            <p className="text-sm font-medium text-gray-500 mb-1">Số tiền giao dịch</p>
            <h4 className={`text-4xl font-black ${transaction.type === 'CREDIT' ? 'text-green-600' : 'text-gray-900'}`}>
              {transaction.type === 'CREDIT' ? '+' : '-'}{formatMoney(transaction.amount)}
            </h4>
            <span className="inline-block mt-3 px-3 py-1 bg-green-100 text-green-700 text-xs font-bold rounded-full">
              ✓ Thành công
            </span>
          </div>

          <div className="space-y-4 text-sm">
            <div className="flex justify-between border-b border-gray-50 pb-3">
              <span className="text-gray-500">Mã giao dịch</span>
              <span className="font-mono font-bold text-gray-800">#{transaction.id * 10203}</span>
            </div>
            {/* THÊM TÀI KHOẢN NGƯỜI NHẬN */}
            {/* 🚀 HIỂN THỊ TÊN NGƯỜI GỬI / NHẬN (Sử dụng relatedName từ Backend) */}
            <div className="flex justify-between items-center border-b border-gray-50 pb-3">
              <span className="text-gray-500 whitespace-nowrap">
                {transaction.type === 'CREDIT' ? 'Nhận từ' : 'Chuyển đến'}
              </span>
              <span className="font-bold text-gray-800 text-right max-w-[60%] truncate" title={transaction.relatedName}>
                {transaction.relatedName || 'Người nhận'}
              </span>
            </div>

            {/* 🚀 NẾU LÀ CHUYỂN ĐI (DEBIT), HIỆN THÊM SỐ TÀI KHOẢN ĐỂ DỄ ĐỐI CHIẾU */}
            {transaction.type === 'DEBIT' && transaction.toAccountNumber && transaction.toAccountNumber !== "N/A" && (
              <div className="flex justify-between items-center border-b border-gray-50 pb-3">
                <span className="text-gray-500">STK thụ hưởng</span>
                <span className="font-mono font-bold text-gray-800">{transaction.toAccountNumber}</span>
              </div>
            )}
            <div className="flex justify-between border-b border-gray-50 pb-3">
              <span className="text-gray-500">Thời gian</span>
              <span className="font-medium text-gray-800">{formatDate(transaction.date)}</span>
            </div>
            <div className="flex justify-between border-b border-gray-50 pb-3">
              <span className="text-gray-500">Nội dung</span>
              <span className="font-medium text-gray-800 text-right max-w-[60%]">{transaction.description}</span>
            </div>
            <div className="flex justify-between pb-3">
              <span className="text-gray-500">Số dư sau GD</span>
              <span className="font-bold text-blue-600">{formatMoney(transaction.balanceAfter)}</span>
            </div>
          </div>

 

        </div>
      </div>
    </div>
  );
}