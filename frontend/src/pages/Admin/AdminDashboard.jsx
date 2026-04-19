import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient'; 
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { toast } from 'react-toastify'; // 🚀 1. IMPORT TOAST VÀO ĐÂY

export default function AdminDashboard() {
  const [stats, setStats] = useState({
    totalTransactions: 0,
    highRiskBlocked: 0,
    totalMoneyTransferred: 0,
    riskPercentage: 0
  });
  const [isLoading, setIsLoading] = useState(true);

  // Gọi API lấy số liệu từ Backend Java
  useEffect(() => {
    const fetchStats = async () => {
      try {
        const response = await axiosClient.get('/admin/dashboard-stats');
        setStats(response.data);
      } catch (error) {
        console.error("Lỗi tải dữ liệu Dashboard:", error);
        
        if(error.response?.status === 403) {
            toast.error("🚨 Xâm nhập trái phép! Bạn không có quyền truy cập khu vực này!");
            
            // Đợi Toast hiện lên 1.5s rồi mới đá về trang chủ để User kịp đọc lỗi
            setTimeout(() => {
                window.location.href = '/dashboard';
            }, 1500);
        } else {
            // Các lỗi khác như mất mạng, sập server...
            toast.error("Lỗi kết nối máy chủ quản trị!");
        }
      } finally {
        setIsLoading(false);
      }
    };
    
    fetchStats();
    
    // Tự động làm mới mỗi 5 giây cho giống Real-time
    const interval = setInterval(fetchStats, 5000);
    return () => clearInterval(interval);
  }, []);

  // Dữ liệu cho Biểu đồ Tròn
  const safeTx = stats.totalTransactions - stats.highRiskBlocked;
  const pieData = [
    { name: 'Giao dịch An toàn', value: safeTx > 0 ? safeTx : 0 },
    { name: 'Giao dịch Bị chặn (HIGH)', value: stats.highRiskBlocked }
  ];
  const COLORS = ['#22c55e', '#ef4444']; // Xanh lá và Đỏ

  if (isLoading) return <div className="min-h-screen flex items-center justify-center text-xl font-bold">Đang tải Dữ liệu AI Core...</div>;

  return (
    <div className="min-h-screen bg-slate-50 p-8">
      <div className="max-w-6xl mx-auto">
        
        {/* HEADER */}
        <div className="mb-8">
          <h1 className="text-3xl font-black text-slate-900 tracking-tight">HỆ THỐNG QUẢN TRỊ RỦI RO</h1>
          <p className="text-slate-500 font-medium mt-1">FinRisk AI Security System - Realtime Dashboard</p>
        </div>

        {/* 3 THẺ SỐ LIỆU TỔNG QUAN (CARDS) */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          {/* Thẻ 1 */}
          <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100 border-l-4 border-l-blue-500">
            <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-2">Tổng Giao Dịch</h3>
            <p className="text-4xl font-black text-slate-800">{stats.totalTransactions}</p>
          </div>
          
          {/* Thẻ 2 */}
          <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100 border-l-4 border-l-red-500">
            <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-2">Gian lận / Bị chặn</h3>
            <p className="text-4xl font-black text-red-600">{stats.highRiskBlocked}</p>
          </div>
          
          {/* Thẻ 3 */}
          <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100 border-l-4 border-l-green-500">
            <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-2">Luân chuyển an toàn</h3>
            <p className="text-3xl font-black text-green-600">
              {Number(stats.totalMoneyTransferred).toLocaleString()} <span className="text-base">VND</span>
            </p>
          </div>
        </div>

        {/* KHU VỰC BIỂU ĐỒ VÀ PHÂN TÍCH */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          
          {/* Biểu đồ Tròn */}
          <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100">
            <h3 className="text-lg font-bold text-slate-800 mb-4">Tỉ lệ Phân loại Rủi ro (AI)</h3>
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={80}
                    paddingAngle={5}
                    dataKey="value"
                  >
                    {pieData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Cảnh báo Hệ thống */}
          <div className="bg-slate-900 rounded-2xl p-6 shadow-xl relative overflow-hidden">
            <div className="absolute top-0 right-0 w-32 h-32 bg-blue-500 rounded-full blur-3xl opacity-20 -mr-10 -mt-10"></div>
            <h3 className="text-lg font-bold text-white mb-6 flex items-center">
              <span className="w-2 h-2 rounded-full bg-green-400 animate-pulse mr-2"></span>
              Trạng thái Hệ thống AI
            </h3>
            
            <div className="space-y-4">
              <div className="bg-slate-800 rounded-xl p-4 border border-slate-700">
                <p className="text-slate-400 text-xs font-bold uppercase tracking-wider mb-1">Tỉ lệ Rủi ro trung bình</p>
                <div className="flex items-end justify-between">
                  <p className={`text-2xl font-black ${stats.riskPercentage > 20 ? 'text-orange-500' : 'text-green-400'}`}>
                    {stats.riskPercentage}%
                  </p>
                  <span className="text-slate-500 text-sm">/ 100%</span>
                </div>
              </div>

              <div className="bg-slate-800 rounded-xl p-4 border border-slate-700">
                <p className="text-slate-400 text-xs font-bold uppercase tracking-wider mb-1">Modules Đang Hoạt Động</p>
                <ul className="text-sm text-slate-300 space-y-2 mt-2">
                  <li className="flex items-center">✅ DeepFace CNN Verification</li>
                  <li className="flex items-center">✅ Redis OTP Caching</li>
                  <li className="flex items-center">✅ Anti-Spam Detection Rule</li>
                </ul>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}