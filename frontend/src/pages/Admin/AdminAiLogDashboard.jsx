import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { Link } from 'react-router-dom'; // 🚀 IMPORT THÊM LINK ĐỂ ĐIỀU HƯỚNG

export default function AdminAiLogDashboard() {
  const [logs, setLogs] = useState([]);
  const [isLoading, setIsLoading] = useState(true);

  // 1. Kéo dữ liệu từ Backend
  useEffect(() => {
    fetchLogs();
  }, []);

  const fetchLogs = async () => {
    try {
      setIsLoading(true);
      // Gọi API GET anh em mình vừa tạo ở Bước 1
      const response = await axiosClient.get('/admin/ai-logs');
      setLogs(response.data || response); // Tùy cấu hình interceptor của axiosClient
    } catch (error) {
      console.error("Lỗi khi lấy dữ liệu AI Log:", error);
    } finally {
      setIsLoading(false);
    }
  };

  // 2. Logic tô màu Badge cho Kết quả chốt (Result Label)
  const getResultBadge = (label) => {
    const badges = {
      'HAPPY': 'bg-green-100 text-green-700 border-green-200',
      'FEAR': 'bg-red-100 text-red-700 border-red-200 animate-pulse', // Sợ hãi -> Báo động đỏ nhấp nháy
      'STRESS': 'bg-orange-100 text-orange-700 border-orange-200',
      'ANGRY': 'bg-rose-100 text-rose-700 border-rose-200',
      'SAD': 'bg-blue-100 text-blue-700 border-blue-200',
      'NEUTRAL': 'bg-gray-100 text-gray-700 border-gray-200',
    };
    const style = badges[label?.toUpperCase()] || 'bg-slate-100 text-slate-700 border-slate-200';
    return <span className={`px-3 py-1 rounded-full text-xs font-bold border ${style}`}>{label}</span>;
  };

  // 3. Logic Parse JSON và vẽ Tags phần trăm Cảm xúc
  const renderEmotionDetails = (jsonStr) => {
    if (!jsonStr) return <span className="text-gray-400 italic">Không có dữ liệu</span>;
    try {
      const details = JSON.parse(jsonStr);
      // Chuyển Object thành mảng, lọc bỏ các cảm xúc quá nhỏ (< 1%), và sắp xếp % từ cao xuống thấp
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
        
        {/* ========================================= */}
        {/* HEADER ĐÃ ĐƯỢC CHỈNH SỬA BỐ CỤC */}
        {/* ========================================= */}
        <div className="flex justify-between items-start mb-8">
          <div className="flex flex-col space-y-4">
            <div>
              <h1 className="text-3xl font-black text-slate-900 tracking-tight">AI Audit Trail</h1>
              <p className="text-slate-500 font-medium mt-1">Hệ thống giám sát sinh trắc học & Cảm xúc (Dành cho nội bộ)</p>
            </div>
            
            {/* Nút Làm mới được dời xuống đây */}
            <button 
              onClick={fetchLogs} 
              className="w-max px-4 py-2 bg-white border border-slate-200 rounded-xl text-sm font-bold text-slate-700 hover:bg-slate-100 transition shadow-sm flex items-center"
            >
              <span className="mr-2">🔄</span> Làm mới dữ liệu
            </button>
          </div>

          {/* Nút Quay lại Dashboard */}
          <Link to="/admin" className="px-5 py-2.5 bg-slate-900 text-white font-bold rounded-xl hover:bg-black transition-colors shadow-lg flex items-center shrink-0">
            <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path>
            </svg>
            Quay lại Dashboard
          </Link>
        </div>

        {/* BẢNG DỮ LIỆU */}
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
                    <tr key={log.id} className="hover:bg-slate-50 transition-colors">
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
        </div>
      </div>
    </div>
  );
}
