import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

export default function TransactionResult() {
  const { state } = useLocation();
  const navigate = useNavigate();
  
  // 🚀 FIX LỖI 1: Gán mặc định formData = {} để nó không bị undefined nữa
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
                {result.riskLevel} ({result.totalRiskScore}/100)
              </span>
            </div>
            <div className="w-full bg-gray-200 h-1.5 rounded-full overflow-hidden">
              <div 
                className={`h-full transition-all duration-1000 ${result.riskLevel === 'LOW' ? 'bg-green-500' : 'bg-orange-500'}`}
                style={{ width: `${Math.min(result.totalRiskScore, 100)}%` }}
              ></div>
            </div>
            {/* 🚀 FIX LỖI 2: Ưu tiên lấy emotionSignal từ Backend */}
            <p className="text-[10px] text-gray-400 mt-3 italic leading-tight">
              * AI nhận diện cảm xúc <strong>{result.emotionSignal || formData.emotion || 'Không xác định'}</strong>. 
            </p>
          </div>

          {/* Section 2: Chi tiết chuyển tiền */}
          <div className="space-y-4">
            
            {/* THÔNG TIN NGƯỜI GỬI */}
            <div className="flex justify-between items-start">
              <span className="text-gray-400 text-sm font-medium">Từ tài khoản</span>
              <div className="text-right">
                <p className="text-gray-900 font-bold text-sm">{storedUser?.fullName || result.fromAccount?.user?.fullName || 'Tài khoản của tôi'}</p>
                <p className="text-[11px] text-gray-400 font-mono">{storedUser?.accountNumber || result.fromAccount?.accountNumber || 'N/A'}</p>
              </div>
            </div>

            <div className="border-t border-gray-100 my-2"></div>

            {/* THÔNG TIN NGƯỜI NHẬN */}
            <div className="flex justify-between items-start">
              <span className="text-gray-400 text-sm font-medium">Đến người nhận</span>
              <div className="text-right">
                <p className="text-gray-900 font-bold text-sm">{recipientName || 'Người nhận'}</p>
                {/* 🚀 FIX LỖI 3: Lấy toAccountNumber từ Backend */}
                <p className="text-[11px] text-gray-400 font-mono">{result.toAccountNumber || formData.toAccount}</p>
              </div>
            </div>

            {/* SỐ TIỀN */}
            <div className="flex justify-between pt-4 border-t border-dashed border-gray-200">
              <span className="text-gray-400 text-sm font-medium">Số tiền</span>
              <span className="text-2xl font-black text-blue-600">
                {/* 🚀 FIX LỖI 4: Lấy amount từ Backend */}
                {Number(result.amount || formData.amount || 0).toLocaleString()} <span className="text-sm">VND</span>
              </span>
            </div>

            {/* LỜI NHẮN */}
            <div className="bg-blue-50/50 p-4 rounded-xl border border-blue-100/50">
              <p className="text-[10px] text-blue-400 uppercase font-bold mb-1">Nội dung chuyển khoản</p>
              <p className="text-gray-700 text-sm font-medium italic">
                {/* 🚀 FIX LỖI 5: Lấy description từ Backend */}
                "{result.description || formData.description || "Chuyển tiền nhanh FinRisk"}"
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