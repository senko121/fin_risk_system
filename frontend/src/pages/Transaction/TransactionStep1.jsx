import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { toast } from 'react-toastify'; 
import axiosClient from '../../api/axiosClient';

export default function TransactionStep1() {
  const navigate = useNavigate();
  const location = useLocation();
  const [currentUser, setCurrentUser] = useState(null);
  const [formData, setFormData] = useState({ toAccount: '', amount: '', description: '' });
  const [isLoading, setIsLoading] = useState(false);

  const [recipientName, setRecipientName] = useState('');
  const [lookupError, setLookupError] = useState('');
  const [isLookingUp, setIsLookingUp] = useState(false);

  const formatMoney = (amount) => {
    if (amount == null || isNaN(amount)) return "0 ₫"; 
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
  };

  useEffect(() => {
    const userStr = localStorage.getItem('currentUser');
    if (userStr) setCurrentUser(JSON.parse(userStr));
    else navigate('/login');
  }, [navigate]);

  useEffect(() => {
    if (location.state?.targetAccount) {
      setFormData(prev => ({ 
        ...prev, 
        toAccount: location.state.targetAccount 
      }));
    }
  }, [location]);

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

  const handleChange = (e) => setFormData({ ...formData, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.toAccount || !formData.amount) {
        toast.warning("⚠️ Vui lòng nhập đầy đủ thông tin!");
        return;
    }
    if (!recipientName) {
        toast.error("❌ Người nhận không hợp lệ!");
        return;
    }
    setIsLoading(true);
    try {
      const response = await axiosClient.post('/transactions/process', {
        fromAccountId: currentUser.userId || currentUser.id,
        toAccount: formData.toAccount,
        amount: formData.amount,
        description: formData.description
      });
      const result = response.data;
      
      if (result.riskLevel === 'LOW') {
        toast.success("✅ Giao dịch an toàn!");
        navigate('/transaction-result', { state: { result, formData, recipientName } });
      } else if (result.riskLevel === 'MEDIUM') {
        toast.warning("⚠️ Cần xác minh OTP.");
        navigate('/verify-otp', { state: { transactionId: result.id, formData, recipientName } });
      } else {
        toast.error("🚨 Yêu cầu quét mặt!");
        navigate('/verify-face', { state: { transactionId: result.id, formData, recipientName, result } });
      }
    } catch (error) {
      const msg = error.response?.data?.message || "Lỗi giao dịch!";
      toast.error("🚨 " + msg);
    } finally {
      setIsLoading(false);
    }
  };

  if (!currentUser) return null;

  return (
    <div className="min-h-screen bg-slate-50 py-12 px-4">
      <div className="max-w-2xl mx-auto bg-white rounded-[2.5rem] shadow-xl border border-slate-100 p-8 sm:p-12 relative overflow-hidden">
        
        <button onClick={() => navigate('/transfer')} className="flex items-center text-slate-400 hover:text-blue-600 mb-8 transition-colors font-bold text-sm group">
          <div className="p-2 bg-slate-100 rounded-xl mr-3 group-hover:bg-blue-50 transition-colors">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M15 19l-7-7 7-7"></path></svg>
          </div>
          Quay lại
        </button>

        <div className="text-center mb-10">
            <h2 className="text-3xl font-black text-slate-900 tracking-tight">Chi tiết chuyển tiền</h2>
        </div>

        <form onSubmit={handleSubmit} className="space-y-8">

          {/* 🔥 KHỐI NGUỒN TIỀN - TỐI GIẢN THEO Ý BRO */}
          <div className="flex items-center justify-between px-2 py-4 border-b border-slate-100 mb-6">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 bg-blue-50 text-blue-600 rounded-full flex items-center justify-center">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path>
                </svg>
              </div>
              <div>
                <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Từ tài khoản chính</p>
                <p className="text-sm font-bold text-slate-500 italic">Thanh toán mặc định</p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-0.5">Số dư khả dụng</p>
              <p className="text-xl font-black text-blue-600">{formatMoney(currentUser.balance)}</p>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-6">
            {/* SỐ TÀI KHOẢN NHẬN */}
            <div className="space-y-2">
                <label className="text-[11px] font-black text-slate-400 uppercase tracking-widest ml-1">SỐ TÀI KHOẢN</label>
                <div className="relative">
                    <input 
                        type="text" name="toAccount" value={formData.toAccount} onChange={handleChange} required
                        className={`w-full px-6 py-5 rounded-2xl bg-slate-50 border-2 outline-none font-mono text-xl transition-all ${recipientName ? 'border-emerald-200 focus:border-emerald-500 bg-emerald-50/30' : lookupError ? 'border-red-200 focus:border-red-500' : 'border-transparent focus:border-blue-500'}`}
                        placeholder="Nhập số tài khoản"
                    />
                    {isLookingUp && (
                        <div className="absolute right-5 top-1/2 -translate-y-1/2">
                            <div className="animate-spin h-5 w-5 border-2 border-blue-600 border-t-transparent rounded-full"></div>
                        </div>
                    )}
                </div>
            </div>

            {/* 🔥 TÊN NGƯỜI NHẬN - GIỮ NGUYÊN CODE GỐC CỦA BRO KHÔNG THAY ĐỔI */}
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

            {lookupError && <p className="text-xs text-red-500 font-bold ml-1">{lookupError}</p>}

            {/* SỐ TIỀN */}
            <div className="space-y-2">
                <label className="text-[11px] font-black text-slate-400 uppercase tracking-widest ml-1">Số tiền chuyển (VND)</label>
                <div className="relative group">
                    <input 
                        type="text" name="amount" value={formData.amount} required placeholder="0"
                        className="w-full px-6 py-5 rounded-2xl bg-slate-50 border-2 border-transparent focus:border-blue-500 focus:bg-white outline-none font-black text-3xl text-slate-800 transition-all"
                        onChange={(e) => setFormData({ ...formData, amount: e.target.value.replace(/[^0-9]/g, '') })}
                    />
                    <span className="absolute right-6 top-1/2 -translate-y-1/2 text-slate-300 font-black text-lg">VND</span>
                </div>
            </div>

            {/* LỜI NHẮN */}
            <div className="space-y-2">
                <label className="text-[11px] font-black text-slate-400 uppercase tracking-widest ml-1">Nội dung</label>
                <textarea 
                    name="description" value={formData.description} onChange={handleChange}
                    className="w-full px-6 py-4 rounded-2xl bg-slate-50 border-2 border-transparent focus:border-blue-500 focus:bg-white outline-none font-medium text-slate-700 resize-none transition-all"
                    placeholder="Nội dung chuyển khoản..." rows="2"
                />
            </div>
          </div>

          <button 
            type="submit" disabled={isLoading || !recipientName}
            className={`w-full py-5 rounded-2xl text-white font-black text-lg transition-all shadow-lg ${isLoading || !recipientName ? 'bg-slate-200 cursor-not-allowed text-slate-400 shadow-none' : 'bg-blue-600 hover:bg-blue-700 shadow-blue-200 hover:-translate-y-1 active:scale-95'}`}
          >
            {isLoading ? 'ĐANG THẨM ĐỊNH RỦI RO...' : 'XÁC NHẬN GIAO DỊCH'}
          </button>
        </form>
      </div>
    </div>
  );
}