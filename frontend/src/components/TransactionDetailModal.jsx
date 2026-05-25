 


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