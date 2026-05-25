import React, { useState, useEffect, useRef } from 'react'; //   Nhớ thêm useRef
import { useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify'; //   IMPORT SÚNG BÁO LỖI

import axiosClient from '../../api/axiosClient';

export default function OTPVerification() {
  const navigate = useNavigate();
  const location = useLocation();
  const inputRef = useRef(null); //   Dùng để Auto-focus
  
  // Lấy ID giao dịch và các dữ liệu liên quan từ trang trước truyền sang
  const { transactionId, formData, recipientName } = location.state || {};

  const [otp, setOtp] = useState('');
  const [timeLeft, setTimeLeft] = useState(180); // Đếm ngược 180s (3 phút)
  const [isLoading, setIsLoading] = useState(false);

  // Auto-focus vào ô input khi vừa mở trang
  useEffect(() => {
    if (inputRef.current) {
      inputRef.current.focus();
    }
  }, []);

  // Xử lý đếm ngược thời gian
  useEffect(() => {
    // Nếu ai đó gõ url /verify-otp thẳng trên trình duyệt mà không đi qua form chuyển tiền thì đá về trang chủ
    if (!transactionId) {
      toast.error("🚨 Phiên giao dịch không hợp lệ!");
      navigate('/dashboard'); 
      return;
    }

    if (timeLeft <= 0) return;
    const timer = setInterval(() => setTimeLeft(prev => prev - 1), 1000);
    return () => clearInterval(timer);
  }, [timeLeft, transactionId, navigate]);

  // Báo Toast khi sắp hết giờ (còn 30s)
  useEffect(() => {
      if(timeLeft === 30) {
          toast.warning("⏰ Mã OTP của bạn sắp hết hạn!");
      }
      if(timeLeft === 0) {
          toast.error("❌ Giao dịch đã hết hạn do quá thời gian nhập OTP.");
      }
  }, [timeLeft]);

  // Format giây thành dạng Phút:Giây (VD: 02:45)
  const formatTime = (seconds) => {
    const m = Math.floor(seconds / 60).toString().padStart(2, '0');
    const s = (seconds % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  const handleVerify = async (e) => {
    e.preventDefault();
    if (otp.length !== 6) {
      toast.warning("⚠️ Vui lòng nhập đủ 6 số OTP"); //   BÁO TOAST
      return;
    }

    setIsLoading(true);

    try {
      // GỌI API CHỐT ĐƠN CHO BACKEND
      const response = await axiosClient.post('/transactions/verify', {
        transactionId: transactionId,
        authType: 'OTP',
        authCode: otp
      });

      //   CHỐT SỔ THÀNH CÔNG
      toast.success("🎉 Xác thực thành công! Giao dịch đã được duyệt.");
      navigate('/transaction-result', {
        state: {
          result: response.data.data, 
          formData: formData,
          recipientName: recipientName
        }
      });
      
    } catch (error) {
      //   BẮT LỖI TỪ BACKEND
      const errorMsg = error.response?.data || "Lỗi xác thực, vui lòng thử lại!";
      toast.error("❌ " + errorMsg);
      setOtp(''); // Nhập sai thì xóa trắng ô input cho người ta nhập lại
      if (inputRef.current) inputRef.current.focus();
    } finally {
      setIsLoading(false);
    }
  };

return (
    <div className="w-full min-h-screen flex justify-center items-center p-4">
      <div className="w-full max-w-md bg-white p-10 rounded-[2rem] shadow-[0_20px_50px_rgba(0,0,0,0.1)] border border-gray-100 text-center relative overflow-hidden">
        
        {/* Thanh loading mỏng chạy ở trên cùng nếu đang xử lý */}
        {isLoading && <div className="absolute top-0 left-0 w-full h-1 bg-blue-500 animate-scan"></div>}

        <div className="w-20 h-20 bg-blue-50 rounded-full flex items-center justify-center mx-auto mb-6 relative">
          <svg className="w-10 h-10 text-blue-600 z-10" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path></svg>
          {/* Vòng tròn hiệu ứng */}
          <div className="absolute inset-0 border-4 border-blue-100 rounded-full animate-ping opacity-50"></div>
        </div>

        <h2 className="text-2xl font-black text-gray-900 mb-2">Xác nhận giao dịch</h2>
        <p className="text-gray-500 text-sm mb-8">
          Mã OTP gồm 6 chữ số đã được gửi đến số điện thoại và Email của bạn.
        </p>

        <form onSubmit={handleVerify}>
          <input 
            ref={inputRef} //   GẮN REF ĐỂ AUTO-FOCUS
            type="text" 
            maxLength="6"
            value={otp}
            onChange={(e) => setOtp(e.target.value.replace(/[^0-9]/g, ''))} // Chỉ cho gõ số
            disabled={timeLeft === 0}
            className={`w-full text-center text-4xl tracking-[0.5em] font-black py-4 border-b-4 focus:outline-none transition-colors mb-8 ${timeLeft === 0 ? 'bg-gray-100 border-gray-300 text-gray-400 cursor-not-allowed' : 'border-gray-200 focus:border-blue-600 text-gray-800'}`}
            placeholder="------"
          />

          <button 
            type="submit" 
            disabled={isLoading || timeLeft === 0 || otp.length !== 6}
            className={`w-full py-4 rounded-2xl text-white font-bold text-lg shadow-xl transition-all ${(isLoading || timeLeft === 0 || otp.length !== 6) ? 'bg-gray-300 cursor-not-allowed shadow-none' : 'bg-blue-600 hover:bg-blue-700 active:scale-95 shadow-blue-500/30'}`}
          >
            {isLoading ? 'ĐANG KIỂM TRA...' : 'XÁC NHẬN CHUYỂN TIỀN'}
          </button>
        </form>

        <div className="mt-8">
          <p className="text-sm text-gray-500 font-medium flex items-center justify-center">
            <svg className={`w-4 h-4 mr-1 ${timeLeft <= 30 ? 'text-red-500 animate-pulse' : 'text-blue-500'}`} fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
            Thời gian hiệu lực: 
            <span className={`ml-1 font-bold ${timeLeft <= 30 ? 'text-red-500 animate-pulse' : 'text-blue-600'}`}>
              {formatTime(timeLeft)}
            </span>
          </p>
          
          {/* Nút điều hướng khi hết giờ */}
          {timeLeft === 0 && (
            <button 
                onClick={() => navigate('/dashboard')}
                className="mt-4 w-full py-3 border-2 border-gray-200 text-gray-600 font-bold rounded-xl hover:bg-gray-50 hover:text-gray-900 transition-colors"
            >
              Quay lại trang chủ tạo lệnh mới
            </button>
          )}
        </div>

      </div>
    </div>
  );
}

 