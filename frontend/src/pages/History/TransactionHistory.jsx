 
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

  // 🚀 TÍNH NĂNG MỚI: QUẢN LÝ PHÂN TRANG VÀ LỌC TỪ BACKEND
  const [filter, setFilter] = useState('ALL');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  // Khởi tạo User
  useEffect(() => {
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      const parsedUser = JSON.parse(storedUser);
      setUser(parsedUser);
      // Gọi lần đầu luôn ở useEffect lắng nghe filter bên dưới
    } else {
      toast.error("🚨 Bạn chưa đăng nhập!");
      navigate('/login');
    }
  }, [navigate]);

  // 🚀 Lắng nghe sự thay đổi của Tab (Filter)
  useEffect(() => {
    if (user) {
      // Đổi tab thì reset về trang 0, xóa list cũ, bật loading chính
      setPage(0);
      setHistory([]);
      setIsHistoryLoading(true);
      fetchHistory(user.id || user.userId, 0, filter); 
    }
  }, [filter, user]);

  // 🚀 Hàm fetch dữ liệu đã nâng cấp
  const fetchHistory = async (accountId, currentPage, currentFilter) => {
    try {
      const response = await axiosClient.get(`/transactions/history/${accountId}?page=${currentPage}&size=10&filter=${currentFilter}`);
      
      const { content, totalPages: newTotalPages } = response.data;
      
      setTotalPages(newTotalPages);

      // Nếu đang ở trang 0 thì đè list mới, nếu đang cuộn thì nối mảng
      if (currentPage === 0) {
        setHistory(content);
      } else {
        setHistory(prev => [...prev, ...content]);
      }

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
        setIsLoadingMore(false); // Tắt cờ cuộn trang (nếu có)
    }
  };

  // Hàm xử lý khi bấm nút Xem thêm
  const handleLoadMore = () => {
    if (page < totalPages - 1 && user) {
      setIsLoadingMore(true);
      const nextPage = page + 1;
      setPage(nextPage);
      fetchHistory(user.id || user.userId, nextPage, filter);
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

  return (
    <div className="min-h-screen bg-slate-50 py-8 px-4 sm:px-6 lg:px-8">
      <div className="max-w-3xl mx-auto">
        
        {/* Header */}
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

        {/* Tab Filters */}
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
            // Skeleton Loader
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
          ) : history.length === 0 ? (
            // Empty State
            <div className="flex flex-col items-center justify-center p-12 opacity-50">
              <svg className="w-16 h-16 text-gray-300 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
              <p className="text-gray-500 font-medium">Chưa có giao dịch nào phù hợp.</p>
            </div>
          ) : (
            // Danh sách thật
            <>
              {history.map((item, index) => (
                <div 
                  key={item.id} 
                  onClick={() => setSelectedTx(item)} 
                  className={`flex items-center justify-between p-4 cursor-pointer hover:bg-gray-50 transition-colors rounded-2xl ${index !== history.length - 1 ? 'border-b border-gray-50' : ''}`}
                >
                  <div className="flex items-center flex-1 min-w-0">
                    <div className={`w-10 h-10 flex-shrink-0 rounded-full flex items-center justify-center mr-4 ${item.type === 'CREDIT' ? 'bg-green-100 text-green-600' : 'bg-red-100 text-red-600'}`}>
                      {item.type === 'CREDIT' ? (
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 14l-7 7m0 0l-7-7m7 7V3"></path></svg>
                      ) : (
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 10l7-7m0 0l7 7m-7-7v18"></path></svg>
                      )}
                    </div>
                    
                    <div className="flex-1 min-w-0 pr-4">
                      <p className="font-bold text-gray-800 text-sm truncate">
                        {item.type === 'CREDIT' ? 'Nhận từ: ' : 'Chuyển đến: '}
                        {item.relatedName || 'Hệ thống'}
                      </p>
                      <p className="text-xs text-gray-500 mt-0.5 truncate">{item.description}</p>
                      <p className="text-[10px] text-gray-400 mt-0.5">{formatDate(item.date)}</p>
                    </div>
                  </div>
                  
                  <div className="text-right flex-shrink-0">
                    <p className={`font-black ${item.type === 'CREDIT' ? 'text-green-600' : 'text-gray-900'}`}>
                      {item.type === 'CREDIT' ? '+' : '-'}{formatMoney(item.amount)}
                    </p>
                    <p className="text-xs text-gray-400 mt-0.5">SD: {formatMoney(item.balanceAfter)}</p>
                  </div>
                </div>
              ))}

              {/* 🚀 NÚT XEM THÊM (Chỉ hiện khi chưa hết trang) */}
              {page < totalPages - 1 && (
                <div className="flex justify-center mt-4 mb-2">
                  <button 
                    onClick={handleLoadMore} 
                    disabled={isLoadingMore}
                    className="flex items-center px-6 py-2 bg-blue-50 text-blue-600 font-bold text-sm rounded-xl hover:bg-blue-100 transition-colors"
                  >
                    {isLoadingMore ? (
                      <>
                        <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-blue-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                        </svg>
                        Đang tải...
                      </>
                    ) : (
                      "Xem thêm giao dịch"
                    )}
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>

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
