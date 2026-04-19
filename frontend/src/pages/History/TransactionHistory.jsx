
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axiosClient from '../../api/axiosClient'; 
import { toast } from 'react-toastify'; 
import TransactionDetailModal from '../../components/TransactionDetailModal';

export default function TransactionHistory() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [history, setHistory] = useState([]); 
  const [isHistoryLoading, setIsHistoryLoading] = useState(true); 
  const [selectedTx, setSelectedTx] = useState(null);

  // 🚀 TÍNH NĂNG 1: STATE CHO BỘ LỌC (Tất cả, Tiền vào, Tiền ra)
  const [filter, setFilter] = useState('ALL');

  useEffect(() => {
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      const parsedUser = JSON.parse(storedUser);
      setUser(parsedUser);
      fetchHistory(parsedUser.id || parsedUser.userId); 
    } else {
      toast.error("🚨 Bạn chưa đăng nhập!");
      navigate('/login');
    }
  }, [navigate]);

  const fetchHistory = async (accountId) => {
    try {
      const response = await axiosClient.get(`/transactions/history/${accountId}`);
      setHistory(response.data);
    } catch (error) {
      if (error.isSessionExpired) {
          toast.error("🚨 Phiên làm việc đã hết hạn!");
          localStorage.clear();
          navigate('/login');
      } else {
          toast.warning("Lỗi đồng bộ dữ liệu lịch sử.");
      }
    } finally {
        setIsHistoryLoading(false);
    }
  };

  if (!user) return null;

  const formatMoney = (amount) => {
    if (amount == null || isNaN(amount)) return "0 ₫"; 
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
  };

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    return date.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  };

  // 🚀 TÍNH NĂNG 1: LOGIC LỌC DỮ LIỆU
  const filteredHistory = history.filter(item => {
    if (filter === 'IN') return item.type === 'CREDIT';
    if (filter === 'OUT') return item.type === 'DEBIT';
    return true; // Nếu là 'ALL' thì trả về hết
  });

  return (
    <div className="min-h-screen bg-slate-50 py-8 px-4 sm:px-6 lg:px-8">
      <div className="max-w-3xl mx-auto">
        
        {/* Header có nút Quay lại */}
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center">
            <button 
              onClick={() => navigate('/dashboard')}
              className="flex items-center text-gray-500 hover:text-blue-600 transition-colors mr-4 bg-white p-2 rounded-full shadow-sm"
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10 19l-7-7m0 0l7-7m-7 7V5"></path></svg>
            </button>
            <div>
              <h1 className="text-2xl font-black text-gray-900">Lịch sử giao dịch</h1>
              <p className="text-sm text-gray-500">Toàn bộ biến động số dư của bạn</p>
            </div>
          </div>
        </div>

        {/* 🚀 TÍNH NĂNG 1: GIAO DIỆN BỘ LỌC TABS */}
        <div className="flex space-x-2 mb-6 bg-white p-1.5 rounded-2xl shadow-sm border border-gray-100 w-fit">
          <button 
            onClick={() => setFilter('ALL')}
            className={`px-5 py-2 rounded-xl text-sm font-bold transition-all ${filter === 'ALL' ? 'bg-gray-900 text-white shadow-md' : 'text-gray-500 hover:bg-gray-100'}`}
          >
            Tất cả
          </button>
          <button 
            onClick={() => setFilter('IN')}
            className={`px-5 py-2 rounded-xl text-sm font-bold transition-all flex items-center ${filter === 'IN' ? 'bg-green-100 text-green-700 shadow-sm' : 'text-gray-500 hover:bg-gray-100'}`}
          >
            <span className="w-2 h-2 rounded-full bg-green-500 mr-2"></span> Tiền vào
          </button>
          <button 
            onClick={() => setFilter('OUT')}
            className={`px-5 py-2 rounded-xl text-sm font-bold transition-all flex items-center ${filter === 'OUT' ? 'bg-red-100 text-red-700 shadow-sm' : 'text-gray-500 hover:bg-gray-100'}`}
          >
            <span className="w-2 h-2 rounded-full bg-red-500 mr-2"></span> Tiền ra
          </button>
        </div>

        {/* Danh sách giao dịch */}
        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 p-2 min-h-[400px]">
          {isHistoryLoading ? (
            
            // 🚀 TÍNH NĂNG 2: HIỆU ỨNG SKELETON LOADER ĐỈNH CAO
            <div className="space-y-2 p-2">
              {[1, 2, 3, 4, 5].map((skeleton) => (
                <div key={skeleton} className="flex items-center justify-between p-4 animate-pulse">
                  <div className="flex items-center">
                    <div className="w-10 h-10 bg-gray-200 rounded-full mr-4"></div>
                    <div className="space-y-2">
                      <div className="h-4 bg-gray-200 rounded-md w-28"></div>
                      <div className="h-3 bg-gray-100 rounded-md w-20"></div>
                    </div>
                  </div>
                  <div className="space-y-2 flex flex-col items-end">
                    <div className="h-4 bg-gray-200 rounded-md w-24"></div>
                    <div className="h-3 bg-gray-100 rounded-md w-32"></div>
                  </div>
                </div>
              ))}
            </div>

          ) : filteredHistory.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-12 opacity-50">
              <svg className="w-16 h-16 text-gray-300 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
              <p className="text-gray-500 font-medium">Chưa có giao dịch nào phù hợp.</p>
            </div>
          ) : (
            filteredHistory.map((item, index) => (
              <div 
                key={item.id} 
                onClick={() => setSelectedTx(item)} 
                className={`flex items-center justify-between p-4 cursor-pointer hover:bg-gray-50 transition-colors rounded-2xl ${index !== filteredHistory.length - 1 ? 'border-b border-gray-50' : ''}`}
              >
                <div className="flex items-center">
                  <div className={`w-10 h-10 rounded-full flex items-center justify-center mr-4 ${item.type === 'CREDIT' ? 'bg-green-100 text-green-600' : 'bg-red-100 text-red-600'}`}>
                    {item.type === 'CREDIT' ? (
                      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 14l-7 7m0 0l-7-7m7 7V3"></path></svg>
                    ) : (
                      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 10l7-7m0 0l7 7m-7-7v18"></path></svg>
                    )}
                  </div>
                  <div>
                    <p className="font-bold text-gray-800 text-sm">{item.description}</p>
                    <p className="text-xs text-gray-400">{formatDate(item.date)}</p>
                  </div>
                </div>
                <div className="text-right">
                  <p className={`font-black ${item.type === 'CREDIT' ? 'text-green-600' : 'text-gray-900'}`}>
                    {item.type === 'CREDIT' ? '+' : '-'}{formatMoney(item.amount)}
                  </p>
                  <p className="text-xs text-gray-400">SD: {formatMoney(item.balanceAfter)}</p>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* Gọi Component Modal */}
      <TransactionDetailModal 
        isOpen={!!selectedTx} 
        onClose={() => setSelectedTx(null)} 
        transaction={selectedTx} 
        formatMoney={formatMoney} 
        formatDate={formatDate} 
      />
    </div>
  );
}
