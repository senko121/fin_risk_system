import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { Link } from 'react-router-dom'; 

export default function AdminAiLogDashboard() {
  const [logs, setLogs] = useState([]);
  const [isLoading, setIsLoading] = useState(false); // Sửa lại thành false ban đầu
  const [isLoadingMore, setIsLoadingMore] = useState(false); // Trạng thái loading riêng cho nút Tải thêm

  // 🚀 CÁC STATE MỚI ĐỂ QUẢN LÝ PHÂN TRANG
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);

  // Chạy lần đầu tiên khi vào trang
  useEffect(() => {
    fetchLogs(0); // Lấy trang 0
  }, []);

  const fetchLogs = async (pageNumber) => {
    try {
      if (pageNumber === 0) {
        setIsLoading(true);
      } else {
        setIsLoadingMore(true);
      }

      // 🚀 Gọi API có truyền tham số page và size
      const response = await axiosClient.get(`/admin/ai-logs?page=${pageNumber}&size=10`);
      
      // Response trả về là object phân trang, ta chui vào .content để lấy mảng
      const newData = response.data.content || response.data || [];

      if (pageNumber === 0) {
        setLogs(newData); // Lần đầu hoặc làm mới -> Ghi đè
      } else {
        setLogs(prevLogs => [...prevLogs, ...newData]); // Tải thêm -> Nối mảng
      }

      // Kiểm tra xem backend báo đã là trang cuối chưa (last: true)
      setHasMore(!response.data.last);
      setPage(pageNumber);

    } catch (error) {
      console.error("Lỗi khi lấy dữ liệu AI Log:", error);
    } finally {
      setIsLoading(false);
      setIsLoadingMore(false);
    }
  };

  // Hàm xử lý khi bấm nút "Làm mới"
  const handleRefresh = () => {
    setPage(0);
    setHasMore(true);
    fetchLogs(0);
  };

  // Hàm xử lý khi bấm nút "Tải thêm"
  const handleLoadMore = () => {
    if (!isLoadingMore && hasMore) {
      fetchLogs(page + 1);
    }
  };

  const getResultBadge = (label) => {
    const badges = {
      'HAPPY': 'bg-green-100 text-green-700 border-green-200',
      'FEAR': 'bg-red-100 text-red-700 border-red-200 animate-pulse',
      'STRESS': 'bg-orange-100 text-orange-700 border-orange-200',
      'ANGRY': 'bg-rose-100 text-rose-700 border-rose-200',
      'SAD': 'bg-blue-100 text-blue-700 border-blue-200',
      'NEUTRAL': 'bg-gray-100 text-gray-700 border-gray-200',
    };
    const style = badges[label?.toUpperCase()] || 'bg-slate-100 text-slate-700 border-slate-200';
    return <span className={`px-3 py-1 rounded-full text-xs font-bold border ${style}`}>{label}</span>;
  };

  const renderEmotionDetails = (jsonStr) => {
    if (!jsonStr) return <span className="text-gray-400 italic">Không có dữ liệu</span>;
    try {
      const details = JSON.parse(jsonStr);
      const sortedEmotions = Object.entries(details)
        .filter(([_, value]) => value >= 1.0)
        .sort((a, b) => b[1] - a[1]);

      return (
        <div className="flex flex-wrap gap-2">
          {sortedEmotions.map(([emotion, value]) => (
            <div key={emotion} className="flex items-center text-[11px] font-medium bg-slate-50 border border-slate-200 rounded-md px-2 py-1 shadow-sm">
              <span className="text-slate-500 mr-1">{emotion}:</span>
              <span className={`font-black ${value > 40 ? 'text-blue-600' : 'text-slate-700'}`}>
                {value.toFixed(1)}%
              </span>
            </div>
          ))}
        </div>
      );
    } catch (e) {
      return <span className="text-red-500 text-xs">Lỗi parse dữ liệu</span>;
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex justify-between items-start mb-8">
          <div className="flex flex-col space-y-4">
            <div>
              <h1 className="text-3xl font-black text-slate-900 tracking-tight">AI Audit Trail</h1>
              <p className="text-slate-500 font-medium mt-1">Hệ thống giám sát sinh trắc học & Cảm xúc (Dành cho nội bộ)</p>
            </div>
            
            <button 
              onClick={handleRefresh} 
              className="w-max px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm font-bold text-slate-700 hover:bg-slate-100 transition shadow-sm flex items-center"
            >
              <span className={`mr-2 ${isLoading ? 'animate-spin' : ''}`}>🔄</span> Làm mới dữ liệu
            </button>
          </div>

          <Link to="/admin" className="px-5 py-2.5 bg-slate-900 text-white font-bold rounded-xl hover:bg-black transition-colors shadow-lg flex items-center shrink-0">
            <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path>
            </svg>
            Quay lại Dashboard
          </Link>
        </div>

        <div className="bg-white rounded-3xl shadow-xl border border-slate-100 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-900 text-white text-xs uppercase tracking-widest">
                  <th className="p-4 font-bold rounded-tl-3xl">Mã GD (Tx ID)</th>
                  <th className="p-4 font-bold">User ID</th>
                  <th className="p-4 font-bold">Loại Quét</th>
                  <th className="p-4 font-bold text-center">Kết Quả (AI Chốt)</th>
                  <th className="p-4 font-bold">Chi tiết Cảm Xúc (Đã lọc &gt; 1%)</th>
                  <th className="p-4 font-bold">Tốc độ AI</th>
                  <th className="p-4 font-bold rounded-tr-3xl">Thời gian</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {isLoading ? (
                  <tr><td colSpan="7" className="p-8 text-center text-slate-400 font-bold animate-pulse">Đang nạp dữ liệu AI...</td></tr>
                ) : logs.length === 0 ? (
                  <tr><td colSpan="7" className="p-8 text-center text-slate-400 italic">Chưa có lịch sử quét nào.</td></tr>
                ) : (
                  logs.map((log) => (
                    // Dùng log.id làm key, nếu có nguy cơ trùng lặp khi nối mảng thì nối thêm chuỗi
                    <tr key={`ai_log_${log.id}`} className="hover:bg-slate-50 transition-colors">
                      <td className="p-4 font-mono text-sm font-bold text-slate-700">#{log.transactionId}</td>
                      <td className="p-4 font-medium text-slate-600">User {log.userId}</td>
                      <td className="p-4">
                        <span className="text-[10px] font-black uppercase tracking-wider bg-purple-100 text-purple-700 px-2 py-1 rounded">
                          {log.scanType}
                        </span>
                      </td>
                      <td className="p-4 text-center">
                        {getResultBadge(log.resultLabel)}
                        <div className="text-[10px] text-slate-400 font-bold mt-1">Độ tin cậy: {(log.confidenceScore * 100).toFixed(1)}%</div>
                      </td>
                      <td className="p-4 max-w-md">
                        {renderEmotionDetails(log.emotionDetails)}
                      </td>
                      <td className="p-4 text-sm font-mono text-slate-500">{log.processTimeMs} ms</td>
                      <td className="p-4 text-xs font-medium text-slate-500">
                        {new Date(log.createdAt).toLocaleString('vi-VN')}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* 🚀 KHU VỰC NÚT TẢI THÊM NẰM Ở ĐÁY BẢNG */}
          {!isLoading && logs.length > 0 && (
            <div className="p-4 bg-slate-50 border-t border-slate-100 flex justify-center">
              {hasMore ? (
                <button
                  onClick={handleLoadMore}
                  disabled={isLoadingMore}
                  className={`px-6 py-2.5 rounded-xl font-bold text-sm transition-all shadow-sm
                    ${isLoadingMore 
                      ? 'bg-slate-200 text-slate-500 cursor-not-allowed' 
                      : 'bg-white border border-slate-200 text-blue-600 hover:bg-blue-50 hover:border-blue-200 hover:shadow-md'
                    }`}
                >
                  {isLoadingMore ? 'Đang tải thêm...' : '⬇️ Tải thêm các bản ghi cũ hơn'}
                </button>
              ) : (
                <span className="text-sm font-medium text-slate-400 italic">
                  Đã hiển thị toàn bộ lịch sử quét AI.
                </span>
              )}
            </div>
          )}

        </div>
      </div>
    </div>
  );
}