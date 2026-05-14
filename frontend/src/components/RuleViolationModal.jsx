import React from 'react';

export default function RuleViolationModal({ isOpen, onClose, transaction }) {
  if (!isOpen || !transaction) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm transition-opacity">
      {/* Vùng bấm ra ngoài để đóng */}
      <div className="absolute inset-0" onClick={onClose}></div>
      
      {/* Khung Popup siêu gọn, thu nhỏ max-w-sm */}
      <div className="relative bg-white rounded-2xl w-full max-w-sm shadow-2xl overflow-hidden transform transition-all animate-fade-in-up">
        
        {/* Header */}
        <div className="flex justify-between items-center p-4 border-b border-slate-100 bg-slate-50">
          <div>
            <h3 className="text-base font-black text-slate-800">Chi tiết rủi ro</h3>
            <p className="text-[11px] font-mono text-slate-500 mt-0.5">Giao dịch #{transaction.id}</p>
          </div>
          <button 
            onClick={onClose}
            className="text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-full p-2 transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
          </button>
        </div>

        {/* Body - Chỉ focus vào Rủi ro */}
        <div className="p-5">
          <div className="flex justify-between items-center mb-5 pb-4 border-b border-slate-100">
            <span className="text-slate-600 font-bold text-sm">Tổng điểm phạt:</span>
            <span className={`text-3xl font-black ${transaction.totalRiskScore > 0 ? 'text-red-600' : 'text-emerald-600'}`}>
              {transaction.totalRiskScore || 0}đ
            </span>
          </div>

          <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-3">
            Nguyên nhân kích hoạt AI
          </p>

          {/* Render mảng violatedRules */}
          {transaction.violatedRules && transaction.violatedRules.length > 0 ? (
            <ul className="space-y-2.5">
              {transaction.violatedRules.map((rule, idx) => (
                <li key={idx} className="flex items-start bg-red-50/50 border border-red-100 p-3 rounded-xl">
                  <span className="text-red-500 mr-2 font-bold text-sm mt-0.5">🚨</span>
                  <span className="font-semibold text-red-800 text-sm leading-snug">
                    {rule}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <div className="bg-emerald-50 text-emerald-600 p-4 rounded-xl border border-emerald-100 text-sm font-bold flex flex-col items-center justify-center text-center">
              <svg className="w-8 h-8 mb-2 opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
              Giao dịch này an toàn (0đ)
              <span className="text-xs font-medium opacity-80 mt-1">Không vi phạm luật nào</span>
            </div>
          )}
        </div>

      </div>
    </div>
  );
}