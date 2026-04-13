import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

export default function TransactionStep1() {
  const navigate = useNavigate();
  const [currentUser, setCurrentUser] = useState(null);
  const [formData, setFormData] = useState({ toAccount: '', amount: '', emotion: 'NORMAL' });
  const [isScanning, setIsScanning] = useState(false);
  const [scanComplete, setScanComplete] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    const userStr = localStorage.getItem('currentUser');
    if (userStr) setCurrentUser(JSON.parse(userStr));
    else navigate('/login');
  }, [navigate]);

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
    if (!formData.toAccount || !formData.amount) return alert("Vui lòng nhập đầy đủ thông tin!");
    
    setIsLoading(true);
    try {
      const response = await axios.post('http://localhost:8081/api/transactions/process', {
        fromAccountId: currentUser.userId,
        toAccount: formData.toAccount,
        amount: formData.amount,
        emotion: formData.emotion
      });
      const result = response.data;
      alert(`Đánh giá rủi ro: ${result.riskLevel}\nĐiểm số: ${result.totalRiskScore}`);
      navigate('/dashboard');
    } catch (error) {
      alert("Lỗi: " + (error.response?.data || error.message));
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

            <form onSubmit={handleSubmit} className="space-y-8">
              <div>
                <label className="block text-xs uppercase tracking-widest font-bold text-gray-400 mb-3">Số tài khoản nhận</label>
                <input 
                  type="text" name="toAccount" value={formData.toAccount} onChange={handleChange} required
                  className="w-full px-6 py-4 rounded-2xl bg-gray-50 border-none focus:ring-2 focus:ring-blue-500 font-mono text-lg"
                  placeholder="Nhập số tài khoản"
                />
              </div>
              
              <div>
                <label className="block text-xs uppercase tracking-widest font-bold text-gray-400 mb-3">Số tiền chuyển (VND)</label>
                <input 
                  type="number" name="amount" value={formData.amount} onChange={handleChange} required
                  className="w-full px-6 py-4 rounded-2xl bg-gray-50 border-none focus:ring-2 focus:ring-blue-500 font-bold text-2xl text-blue-900"
                  placeholder="0"
                />
              </div>

              <button 
                type="submit" disabled={isLoading}
                className={`w-full py-5 rounded-2xl text-white font-bold text-lg shadow-xl shadow-blue-100 transition-all ${isLoading ? 'bg-blue-300' : 'bg-blue-600 hover:bg-blue-700'}`}
              >
                {isLoading ? 'ĐANG TÍNH TOÁN RỦI RO...' : 'XÁC NHẬN GIAO DỊCH'}
              </button>
            </form>
          </div>

          {/* Cột phải: AI Scanner */}
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
            
            {/* Trang trí background cột đen */}
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