
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify'; 
import axiosClient from '../../api/axiosClient';

export default function TransactionStep1() {
  const navigate = useNavigate();
  const [currentUser, setCurrentUser] = useState(null);
  
  // 🚀 ĐÃ XÓA 'emotion' KHỎI FORM DATA
  const [formData, setFormData] = useState({ toAccount: '', amount: '', description: '' });
  const [isLoading, setIsLoading] = useState(false);

  const [recipientName, setRecipientName] = useState('');
  const [lookupError, setLookupError] = useState('');
  const [isLookingUp, setIsLookingUp] = useState(false);
  const [recentRecipients, setRecentRecipients] = useState([]);

  useEffect(() => {
    const userStr = localStorage.getItem('currentUser');
    if (userStr) setCurrentUser(JSON.parse(userStr));
    else navigate('/login');
  }, [navigate]);

  useEffect(() => {
    const timer = setTimeout(async () => {
      if (formData.toAccount.length >= 9) { 
        setIsLookingUp(true);
        setLookupError('');
        setRecipientName('');
        
        try {
          const response = await axiosClient.get(`/transactions/lookup/${formData.toAccount}`);
          setRecipientName(response.data.fullName);
        } catch (error) {
          setLookupError(error.response?.data || "Tài khoản không tồn tại!");
        } finally {
          setIsLookingUp(false);
        }
      } else {
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

  const handleChange = (e) => setFormData({ ...formData, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!formData.toAccount || !formData.amount) {
        toast.warning("⚠️ Vui lòng nhập đầy đủ thông tin giao dịch!");
        return;
    }
    
    if (!recipientName) {
        toast.error("❌ Vui lòng nhập đúng số tài khoản người nhận!");
        return;
    }

    if (String(formData.toAccount).trim() === String(currentUser.accountNumber).trim()) {
        toast.error("🚨 Lỗi: Bạn không thể tự chuyển tiền cho chính mình!");
        return;
    }

    const transferAmount = Number(formData.amount);
    if (isNaN(transferAmount) || transferAmount <= 0) {
        toast.error("🚨 Lỗi: Số tiền giao dịch phải lớn hơn 0!");
        return;
    }

    setIsLoading(true);
    try {
      // 🚀 GỬI DATA VÒNG 1 SẠCH SẼ (KHÔNG CÓ EMOTION)
      const response = await axiosClient.post('/transactions/process', {
        fromAccountId: currentUser.userId || currentUser.id,
        toAccount: formData.toAccount,
        amount: formData.amount,
        description: formData.description
      });
      const result = response.data;
      
    if (result.riskLevel === 'LOW') {
        toast.success("✅ Giao dịch an toàn! Đang xử lý...");
        navigate('/transaction-result', { 
            state: { 
                result: result, 
                formData: formData, // Chuyền nguyên form sang để Backup
                recipientName: recipientName 
            } 
        });
      } 
      else if (result.riskLevel === 'MEDIUM') {
        toast.warning("⚠️ Cần xác minh danh tính. Vui lòng nhập mã OTP.");
        navigate('/verify-otp', { 
              state: { 
                  transactionId: result.id,
                  formData: formData, // Chuyền nguyên form sang để Backup
                  recipientName: recipientName 
              } 
          });
      } 
      else {
        toast.error("🚨 CẢNH BÁO AN NINH: Kích hoạt Camera nhận diện khuôn mặt!");
        navigate('/verify-face', { 
            state: { 
                transactionId: result.id,
                formData: formData, // Chuyền nguyên form sang để Backup
                recipientName: recipientName,
                result: result 
            } 
        });
      }

} catch (error) {
      console.error("Lỗi Giao Dịch:", error);

      if (error.response && error.response.data) {
        // Lấy dữ liệu chuẩn từ ApiErrorResponse của Backend
        const { errorCode, message, details } = error.response.data;

        // Phân tích và báo lỗi dựa trên errorCode từ GlobalExceptionHandler
        if (errorCode === 'ERR_VALIDATION_FAILED' && details) {
            // Lấy câu lỗi Validation đầu tiên (VD: "LỖI BẢO MẬT: Số tiền...")
            const firstErrorMsg = Object.values(details)[0];
            toast.error("🚨 " + firstErrorMsg);
        } 
        else if (errorCode === 'ERR_BAD_REQUEST') {
            // Bắt lỗi logic từ IllegalArgumentException (VD: Thiếu tiền, Sai tài khoản)
            toast.warning("⚠️ " + message);
        }
        else if (errorCode === 'ERR_CONCURRENT_TRANSACTION') {
            // Bắt lỗi Database bị khóa
            toast.info("⏳ " + message); 
        }
        else if (errorCode === 'ERR_SYSTEM_UNKNOWN') {
             // Bắt lỗi văng Exception chưa biết (500)
             toast.error("🛠️ " + message);
        }
        else if (message) {
             // Đề phòng có lỗi có thông báo mà mã lạ
             toast.error("🚨 " + message);
        } else {
             toast.error("🚨 Đã có lỗi xảy ra. Vui lòng kiểm tra lại!");
        }

      } else {
        // Lỗi không có response (Mất mạng, Backend sập chưa kịp báo)
        toast.error("🔌 Mất kết nối đến máy chủ FinRisk!");
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 py-12 px-4">
      <div className="max-w-2xl mx-auto bg-white rounded-3xl shadow-xl border border-slate-100 p-8 sm:p-12 relative overflow-hidden">
        
        {/* Nút quay lại */}
        <button onClick={() => navigate('/dashboard')} className="flex items-center text-slate-400 hover:text-blue-600 mb-8 transition-colors font-bold text-sm">
          <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path></svg>
          Quay lại Dashboard
        </button>

        {/* Header Form */}
        <div className="text-center mb-10">
            <div className="w-16 h-16 bg-blue-50 text-blue-600 rounded-2xl flex items-center justify-center mx-auto mb-4 shadow-sm">
                <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"></path></svg>
            </div>
            <h2 className="text-3xl font-black text-slate-900 tracking-tight">Chuyển tiền</h2>
            <p className="text-slate-500 mt-2 font-medium">Hệ thống AI sẽ đánh giá rủi ro giao dịch của bạn.</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">

          {/* Giao dịch gần đây */}
          {recentRecipients.length > 0 && (
            <div>
                <label className="block text-[11px] uppercase tracking-widest font-black text-slate-400 mb-3">Giao dịch gần đây</label>
                <div className="flex space-x-4 overflow-x-auto pb-4 scrollbar-hide">
                    {recentRecipients.map((person, idx) => (
                    <button 
                        key={idx}
                        type="button"
                        onClick={() => setFormData({...formData, toAccount: person.accountNumber})}
                        className="flex flex-col items-center flex-shrink-0 group"
                    >
                        <div className="w-14 h-14 bg-slate-50 border border-slate-100 text-slate-600 rounded-full flex items-center justify-center font-black text-lg mb-2 group-hover:bg-blue-600 group-hover:text-white group-hover:border-blue-600 transition-all shadow-sm">
                            {person.fullName.charAt(0)}
                        </div>
                        <span className="text-[10px] font-bold text-slate-500 w-16 truncate text-center">{person.fullName}</span>
                    </button>
                    ))}
                </div>
            </div>
          )}

          {/* SỐ TÀI KHOẢN */}
          <div>
            <label className="block text-[11px] uppercase tracking-widest font-black text-slate-400 mb-2">Số tài khoản nhận</label>
            <input 
                type="text" name="toAccount" value={formData.toAccount} onChange={handleChange} required
                className={`w-full px-5 py-4 rounded-xl bg-slate-50 border-2 outline-none font-mono text-lg transition-colors ${recipientName ? 'border-green-200 focus:border-green-500' : lookupError ? 'border-red-200 focus:border-red-500' : 'border-transparent focus:border-blue-500'}`}
                placeholder="Nhập số tài khoản"
            />
            {!isLookingUp && lookupError && (
                <p className="text-xs text-red-500 font-bold mt-2 flex items-center">
                    <svg className="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                    {lookupError}
                </p>
            )}
          </div>

          {/* TÊN NGƯỜI NHẬN */}
          <div className={`transition-all duration-300 ${recipientName ? 'opacity-100 block' : 'opacity-0 hidden'}`}>
            <label className="block text-[11px] uppercase tracking-widest font-black text-slate-400 mb-2">Tên người nhận</label>
            <div className="relative">
                <input 
                    type="text" value={recipientName} readOnly disabled
                    className="w-full px-5 py-4 rounded-xl bg-green-50/50 border-2 border-green-100 text-green-700 font-black text-lg cursor-not-allowed"
                />
                <div className="absolute right-4 top-1/2 -translate-y-1/2 text-green-500 bg-white p-1 rounded-full shadow-sm">
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7"></path></svg>
                </div>
            </div>
          </div>
          
          {/* SỐ TIỀN */}
          <div>
            <label className="block text-[11px] uppercase tracking-widest font-black text-slate-400 mb-2">Số tiền chuyển (VND)</label>
            <input 
                type="text" // 🚀 BƯỚC 1: Đổi thành 'text' để dẹp bỏ mũi tên tăng giảm (spinner) của HTML5
                name="amount" 
                value={formData.amount} 
                required
                placeholder="0"
                className="w-full px-5 py-4 rounded-xl bg-slate-50 border-2 border-transparent focus:border-blue-500 focus:bg-white outline-none font-black text-2xl text-slate-800 transition-all"
                
                // 🚀 BƯỚC 2: Chỉ cho phép nhập các ký tự số (0-9)
                onChange={(e) => {
                    const value = e.target.value.replace(/[^0-9]/g, '');
                    setFormData({ ...formData, amount: value });
                }}

                // 🚀 BƯỚC 3: Chặn thêm các phím đặc biệt (-, e, E, +, .) để chắc ăn 100%
                onKeyDown={(e) => {
                    if (['-', 'e', 'E', '+', '.'].includes(e.key)) {
                        e.preventDefault();
                    }
                }}

                // 🚀 BƯỚC 4: Chặn sự kiện cuộn chuột (scroll wheel) làm nhảy số
                onWheel={(e) => e.target.blur()}
            />
          </div>

          {/* LỜI NHẮN */}
          <div>
            <label className="block text-[11px] uppercase tracking-widest font-black text-slate-400 mb-2">Lời nhắn</label>
            <textarea 
                name="description" value={formData.description} onChange={handleChange}
                className="w-full px-5 py-4 rounded-xl bg-slate-50 border-2 border-transparent focus:border-blue-500 focus:bg-white outline-none font-medium text-slate-700 resize-none transition-all"
                placeholder="Nhập nội dung chuyển khoản..." rows="2"
            />
          </div>

          {/* NÚT XÁC NHẬN */}
          <button 
            type="submit" disabled={isLoading || isLookingUp || !recipientName}
            className={`w-full mt-8 py-5 rounded-xl text-white font-black text-lg transition-all flex items-center justify-center ${isLoading || isLookingUp || !recipientName ? 'bg-slate-300 cursor-not-allowed' : 'bg-slate-900 hover:bg-black shadow-lg shadow-slate-900/20 hover:-translate-y-1'}`}
          >
            {isLoading ? (
                <span className="flex items-center">
                    <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path></svg>
                    ĐANG THẨM ĐỊNH RỦI RO...
                </span>
            ) : 'TIẾP TỤC'}
          </button>
        </form>
      </div>
    </div>
  );
}