import React, { useState, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify'; // 🚀 ĐÃ IMPORT SÚNG BÁO LỖI

// 🚀 Gọi con bot axiosClient vào thay cho axios thường
import axiosClient from '../../api/axiosClient';

export default function TransactionStep1() {
  const navigate = useNavigate();
  const [currentUser, setCurrentUser] = useState(null);
  const [formData, setFormData] = useState({ toAccount: '', amount: '', emotion: 'NORMAL', description: '' });
  const [isScanning, setIsScanning] = useState(false);
  const [scanComplete, setScanComplete] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  // === 3 STATE MỚI ĐỂ XỬ LÝ TRA CỨU TÊN ===
  const [recipientName, setRecipientName] = useState('');
  const [lookupError, setLookupError] = useState('');
  const [isLookingUp, setIsLookingUp] = useState(false);
  const [recentRecipients, setRecentRecipients] = useState([]);

  useEffect(() => {
    const userStr = localStorage.getItem('currentUser');
    if (userStr) setCurrentUser(JSON.parse(userStr));
    else navigate('/login');
  }, [navigate]);

  // === LOGIC TỰ ĐỘNG TÌM TÊN KHI GÕ SỐ TÀI KHOẢN ===
  useEffect(() => {
    // Đợi người dùng ngừng gõ 0.5 giây rồi mới chạy API (Debounce)
    const timer = setTimeout(async () => {
      if (formData.toAccount.length >= 9) { // Ít nhất 9 số mới tìm
        setIsLookingUp(true);
        setLookupError('');
        setRecipientName('');
        
        try {
          const response = await axiosClient.get(`/transactions/lookup/${formData.toAccount}`);
          setRecipientName(response.data.fullName);
        } catch (error) {
          // Đoạn này không dùng Toast để tránh spam màn hình khi đang gõ
          setLookupError(error.response?.data || "Tài khoản không tồn tại!");
        } finally {
          setIsLookingUp(false);
        }
      } else {
        // Xóa kết quả nếu xóa bớt số đi
        setRecipientName('');
        setLookupError('');
      }
    }, 500);

    return () => clearTimeout(timer);
  }, [formData.toAccount]);

  useEffect(() => {
        const fetchRecent = async () => {
            if (currentUser) {
                try {
                    const res = await axiosClient.get(`/transactions/recent-recipients/${currentUser.id || currentUser.userId}`);
                    setRecentRecipients(res.data);
                } catch (err) { console.error(err); }
            }
        };
        fetchRecent();
    }, [currentUser]);
  // ===============================================

  const handleChange = (e) => setFormData({ ...formData, [e.target.name]: e.target.value });

  const handleScanFace = () => {
    setIsScanning(true);
    setScanComplete(false);
    setTimeout(() => {
      const emotions = ['NORMAL', 'HAPPY', 'STRESS', 'FEAR'];
      const randomEmotion = emotions[Math.floor(Math.random() * emotions.length)];
      setFormData({ ...formData, emotion: randomEmotion });
      setIsScanning(false);
      setScanComplete(true);
    }, 2500);
  };

const handleSubmit = async (e) => {
    e.preventDefault();
    
    // 1. Check rỗng cơ bản
    if (!formData.toAccount || !formData.amount) {
        toast.warning("⚠️ Vui lòng nhập đầy đủ thông tin giao dịch!");
        return;
    }
    
    // 2. Check đã tra cứu ra tên người nhận chưa
    if (!recipientName) {
        toast.error("❌ Vui lòng nhập đúng số tài khoản người nhận!");
        return;
    }

    // 🚀 VÁ LỖ HỔNG 1: Chặn chuyển tiền cho chính mình
    if (String(formData.toAccount).trim() === String(currentUser.accountNumber).trim()) {
        toast.error("🚨 Lỗi: Bạn không thể tự chuyển tiền cho chính mình!");
        return;
    }

    // 🚀 VÁ LỖ HỔNG 2: Chặn số tiền âm, số 0 hoặc chữ cái
    const transferAmount = Number(formData.amount);
    if (isNaN(transferAmount) || transferAmount <= 0) {
        toast.error("🚨 Lỗi: Số tiền giao dịch phải lớn hơn 0!");
        return;
    }

    setIsLoading(true);
    try {
      const response = await axiosClient.post('/transactions/process', {
        fromAccountId: currentUser.userId || currentUser.id,
        toAccount: formData.toAccount,
        amount: formData.amount,
        emotion: formData.emotion,
        description: formData.description
      });
      const result = response.data;
      
      // 🚀 THÔNG BÁO TÙY THEO MỨC ĐỘ RỦI RO
      if (result.riskLevel === 'LOW') {
        toast.success("✅ Giao dịch an toàn! Đang xử lý...");
        navigate('/transaction-result', { 
                state: { 
                    result: result, 
                    formData: formData, 
                    recipientName: recipientName 
                } 
            });
      } 
      else if (result.riskLevel === 'MEDIUM') {
        toast.warning("⚠️ Phát hiện rủi ro! Yêu cầu xác thực OTP.");
        navigate('/verify-otp', { state: { transactionId: result.id } });
      } 
      else {
        toast.error("🚨 CẢNH BÁO: Rủi ro cực cao! Bật khiên Face ID.");
        navigate('/verify-face', { 
            state: { 
                transactionId: result.id,
                formData: formData, 
                recipientName: recipientName,
                result: result 
            } 
        });
      }

    } catch (error) {
      // BẮT LỖI TỪ BACKEND
      toast.error("Lỗi: " + (error.response?.data || error.message));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-white">
      <div className="max-w-5xl mx-auto bg-white min-h-screen shadow-2xl border-x border-gray-100 p-8 sm:p-12">
        <button onClick={() => navigate('/dashboard')} className="flex items-center text-gray-400 hover:text-blue-600 mb-10 transition">
          <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path></svg>
          Quay lại Dashboard
        </button>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-16">
        {/* Cột trái: Form */}
        <div>
        <h2 className="text-3xl font-black text-gray-900 mb-2">Chuyển khoản</h2>
        <p className="text-gray-500 mb-10">Giao dịch sẽ được kiểm soát bởi hệ thống đánh giá rủi ro.</p>

        <form onSubmit={handleSubmit} className="space-y-6">

        <div>
        <label className="block text-xs uppercase tracking-widest font-bold text-gray-400 mb-4">Giao dịch gần đây</label>
        <div className="flex space-x-4 mb-6 overflow-x-auto pb-2">
            {recentRecipients.length > 0 ? recentRecipients.map((person, idx) => (
            <button 
                key={idx}
                type="button"
                onClick={() => setFormData({...formData, toAccount: person.accountNumber})}
                className="flex flex-col items-center flex-shrink-0 group"
            >
                <div className="w-14 h-14 bg-blue-50 text-blue-600 rounded-full flex items-center justify-center font-bold text-lg mb-2 group-hover:bg-blue-600 group-hover:text-white transition-all shadow-sm">
                {person.fullName.charAt(0)}
                </div>
                <span className="text-[10px] font-bold text-gray-500 w-16 truncate text-center">{person.fullName}</span>
            </button>
            )) : (
                <p className="text-xs text-gray-400 italic">Chưa có giao dịch gần đây</p>
            )}
        </div>
        </div>

            {/* Ô NHẬP SỐ TÀI KHOẢN */}
            <div>
            <label className="block text-xs uppercase tracking-widest font-bold text-gray-400 mb-3">Số tài khoản nhận</label>
            <input 
                type="text" 
                name="toAccount" 
                value={formData.toAccount} 
                onChange={handleChange} 
                required
                className={`w-full px-6 py-4 rounded-2xl bg-gray-50 border-2 outline-none font-mono text-lg transition-colors ${recipientName ? 'border-green-200 focus:border-green-500' : lookupError ? 'border-red-200 focus:border-red-500' : 'border-transparent focus:border-blue-500'}`}
                placeholder="Nhập số tài khoản"
            />
            
            {/* Thông báo lỗi nếu không tìm thấy */}
            {!isLookingUp && lookupError && (
                <div className="mt-2 h-6 pl-2">
                <p className="text-sm text-red-500 font-medium flex items-center">
                    <svg className="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                    {lookupError}
                </p>
                </div>
            )}
            </div>

            {/* Ô HIỂN THỊ TÊN NGƯỜI NHẬN (CHỈ HIỆN KHI TÌM THẤY TÊN) */}
            <div className={`transition-all duration-500 ${recipientName ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-4 pointer-events-none h-0 overflow-hidden'}`}>
            <label className="block text-xs uppercase tracking-widest font-bold text-gray-400 mb-3">Tên người nhận</label>
            <div className="relative">
                <input 
                type="text" 
                value={recipientName} 
                readOnly 
                disabled
                className="w-full px-6 py-4 rounded-2xl bg-green-50 border-2 border-green-200 text-green-700 font-bold text-lg cursor-not-allowed select-none"
                />
                <div className="absolute right-4 top-1/2 -translate-y-1/2 text-green-500">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                </div>
            </div>
            </div>
            
            {/* Ô NHẬP SỐ TIỀN */}
            <div>
            <label className="block text-xs uppercase tracking-widest font-bold text-gray-400 mb-3">Số tiền chuyển (VND)</label>
            <input 
                type="number" 
                name="amount" 
                value={formData.amount} 
                onChange={handleChange} 
                required
                className="w-full px-6 py-4 rounded-2xl bg-gray-50 border-none focus:ring-2 focus:ring-blue-500 font-bold text-2xl text-blue-900"
                placeholder="0"
            />
            </div>

            {/* THÊM MÔ TẢ */}
            <div>
            <label className="block text-xs uppercase tracking-widest font-bold text-gray-400 mb-3">Lời nhắn</label>
            <textarea 
                name="description" 
                value={formData.description} 
                onChange={handleChange}
                className="w-full px-6 py-4 rounded-2xl bg-gray-50 border-none focus:ring-2 focus:ring-blue-500 font-medium text-gray-700 resize-none"
                placeholder="Nhập nội dung chuyển tiền..."
                rows="2"
            />
            </div>

            {/* NÚT XÁC NHẬN */}
            <button 
            type="submit" 
            disabled={isLoading || isLookingUp || !recipientName}
            className={`w-full py-5 rounded-2xl text-white font-bold text-lg shadow-xl transition-all ${isLoading || isLookingUp || !recipientName ? 'bg-gray-300 cursor-not-allowed shadow-none' : 'bg-blue-600 hover:bg-blue-700 shadow-blue-100'}`}
            >
            {isLoading ? 'ĐANG TÍNH TOÁN RỦI RO...' : 'XÁC NHẬN GIAO DỊCH'}
            </button>
        </form>
        </div>

          {/* Cột phải: AI Scanner (Giữ nguyên không đụng chạm) */}
          <div className="bg-slate-900 rounded-[2.5rem] p-10 text-white flex flex-col items-center justify-center relative overflow-hidden">
            <h3 className="text-xl font-bold mb-8 relative z-10 uppercase tracking-tighter">AI Emotion Analysis</h3>
            
            <div className={`relative w-56 h-56 rounded-full border-4 flex items-center justify-center mb-10 transition-all duration-500 z-10 ${isScanning ? 'border-blue-500 scale-105 shadow-[0_0_50px_rgba(59,130,246,0.5)]' : scanComplete ? (formData.emotion === 'STRESS' ? 'border-red-500' : 'border-green-500') : 'border-slate-700'}`}>
              {isScanning ? (
                <div className="absolute inset-0 flex items-center justify-center">
                  <div className="w-full h-1 bg-blue-400/50 absolute animate-scan"></div>
                  <span className="text-blue-400 font-mono text-sm animate-pulse">ANALYZING...</span>
                </div>
              ) : scanComplete ? (
                <span className="text-3xl font-black">{formData.emotion}</span>
              ) : (
                <svg className="w-20 h-20 text-slate-700" fill="currentColor" viewBox="0 0 20 20"><path d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z"></path></svg>
              )}
            </div>

            <button 
              type="button" onClick={handleScanFace} disabled={isScanning}
              className="bg-white/10 hover:bg-white/20 px-8 py-3 rounded-xl font-bold transition-all z-10"
            >
              {isScanning ? 'VUI LÒNG ĐỢI...' : 'BẮT ĐẦU QUÉT KHUÔN MẶT'}
            </button>
            
            <div className="absolute top-0 left-0 w-full h-full opacity-10 pointer-events-none">
              <div className="absolute top-10 right-10 w-20 h-20 border border-white rounded-full"></div>
              <div className="absolute bottom-10 left-10 w-40 h-40 border border-white rounded-full"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}