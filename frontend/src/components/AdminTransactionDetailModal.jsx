import React from 'react';

export default function AdminTransactionDetailModal({ isOpen, onClose, transaction, formatMoney }) {
  if (!isOpen || !transaction) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm transition-opacity">
      
      {/* 🚀 VŨ KHÍ BÍ MẬT: Ép ẩn thanh cuộn toàn cục cho Modal này */}
      <style>{`
        .hide-scrollbar::-webkit-scrollbar { display: none !important; }
        .hide-scrollbar { -ms-overflow-style: none !important; scrollbar-width: none !important; }
      `}</style>

      {/* Vùng bấm ra ngoài để đóng */}
      <div className="absolute inset-0" onClick={onClose}></div>
      
      {/* Nội dung tấm thẻ Popup - Thêm max-h để không bị tràn màn hình */}
      <div className="relative bg-white rounded-[2.5rem] w-full max-w-lg shadow-2xl overflow-hidden transform transition-all flex flex-col max-h-[90vh]">
        
        {/* Header Popup - flex-shrink-0 để cố định */}
        <div className="flex-shrink-0 flex justify-between items-center p-6 border-b border-slate-100 bg-slate-50/50">
          <div>
            <h3 className="text-lg font-black text-slate-800 uppercase tracking-tight">Hồ sơ giao dịch & Rủi ro</h3>
            <p className="text-xs font-mono text-slate-500 mt-1">Mã GD: #{transaction.id}</p>
          </div>
          <button 
            onClick={onClose}
            className="text-slate-400 hover:text-slate-700 bg-white hover:bg-slate-200 rounded-full p-2 shadow-sm border border-slate-200 transition-colors"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
          </button>
        </div>

        {/* Body Popup - Thêm hide-scrollbar ở đây */}
        <div className="p-6 overflow-y-auto hide-scrollbar flex-1">
          
          {/* Thông tin luân chuyển */}
          <div className="text-center mb-6">
            <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">Số tiền giao dịch</p>
            <h4 className="text-4xl font-black text-slate-800">
              {formatMoney(transaction.amount)}
            </h4>
            <div className="mt-3 flex justify-center gap-2">
              <span className={`px-3 py-1 text-[11px] font-black uppercase tracking-widest rounded-full ${
                transaction.status === 'SUCCESS' ? 'bg-emerald-100 text-emerald-700' :
                transaction.status === 'PENDING' ? 'bg-amber-100 text-amber-700' : 'bg-red-100 text-red-700'
              }`}>
                {transaction.status}
              </span>
            </div>
          </div>

          <div className="space-y-4 text-sm bg-slate-50 p-4 rounded-2xl border border-slate-100 mb-6">
            {/* ... Giữ nguyên các dòng Thời gian, Người gửi, Người nhận ... */}
            <div className="flex justify-between border-b border-slate-200 pb-3">
              <span className="text-slate-500 font-medium">Thời gian</span>
              <span className="font-bold text-slate-800">{new Date(transaction.createdAt).toLocaleString()}</span>
            </div>
            <div className="flex justify-between border-b border-slate-200 pb-3">
              <span className="text-slate-500 font-medium">Người gửi</span>
              <div className="text-right">
                <span className="font-bold text-slate-800 block">{transaction.senderFullName}</span>
                <span className="font-mono text-xs text-slate-500">{transaction.senderAccountNumber}</span>
              </div>
            </div>
            <div className="flex justify-between border-b border-slate-200 pb-3">
              <span className="text-slate-500 font-medium">Người nhận</span>
              <div className="text-right">
                <span className="font-bold text-slate-800 block">{transaction.toBankCode}</span>
                <span className="font-mono text-xs text-slate-500">{transaction.toAccountNumber}</span>
              </div>
            </div>
            <div className="flex justify-between pb-1">
              <span className="text-slate-500 font-medium">Nội dung</span>
              <span className="font-medium text-slate-800 text-right max-w-[60%]">{transaction.description || 'Không có nội dung'}</span>
            </div>
          </div>

          {/* 🚀 KHU VỰC CHỨNG CỨ VI PHẠM RỦI RO (Fix lỗi tràn và scrollbar) */}
          <div className="p-5 rounded-2xl border border-red-200 bg-red-50/50 relative overflow-hidden">
            <p className="text-xs font-black text-red-600 uppercase tracking-wider mb-4 flex items-center">
              <span className="w-2 h-2 rounded-full bg-red-500 mr-2 animate-pulse"></span>
              Phân tích Rủi ro AI
            </p>
            
            <div className="space-y-3 relative z-10">
              <div className="flex justify-between items-center border-b border-red-100 pb-3">
                <span className="text-red-900/70 font-bold text-sm">Tổng điểm rủi ro:</span>
                <span className="font-black text-red-600 text-lg">
                  {transaction.totalRiskScore || 0} / 100đ
                </span>
              </div>
              
              <div className="pt-2">
                <span className="text-red-900/70 font-bold text-sm block mb-2">Các điều luật bị vi phạm:</span>
                
                {transaction.violatedRules && transaction.violatedRules.length > 0 ? (
                  /* 🚀 GIỚI HẠN CHIỀU CAO DANH SÁCH + ẨN SCROLLBAR TẠI ĐÂY */
                  <ul className="space-y-2 max-h-[180px] overflow-y-auto hide-scrollbar pr-1">
                    {transaction.violatedRules.map((rule, idx) => (
                      <li key={idx} className="flex items-start text-sm">
                        <span className="text-red-500 mr-2 font-bold">↳</span>
                        <span className="font-medium text-red-800 bg-white px-2 py-1.5 rounded-xl shadow-sm border border-red-100 flex-1">
                          {rule}
                        </span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <div className="bg-emerald-50 text-emerald-600 px-3 py-2 rounded-lg border border-emerald-100 text-sm font-bold flex items-center">
                    Giao dịch hợp lệ, không vi phạm.
                  </div>
                )}
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}