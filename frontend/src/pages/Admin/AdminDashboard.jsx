 
import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { toast } from 'react-toastify';
import { Link, useNavigate } from 'react-router-dom';

export default function AdminDashboard() {
  const navigate = useNavigate();
  const [stats, setStats] = useState({
    totalTransactions: 0,
    highRiskBlocked: 0,
    totalMoneyTransferred: 0,
    riskPercentage: 0
  });
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;

    const fetchStats = async () => {
      try {
        const response = await axiosClient.get('/admin/dashboard-stats');
        if (isMounted) setStats(response.data);
      } catch (error) {
        console.error("Lỗi tải dữ liệu Dashboard:", error);
        if (error.response?.status === 403) {
          toast.error("🚨 Phiên làm việc hết hạn hoặc không có quyền!");
          handleLogout();
        }
      } finally {
        if (isMounted) setIsLoading(false);
      }
    };

    fetchStats();
    const interval = setInterval(fetchStats, 180000);
    return () => { isMounted = false; clearInterval(interval); };
  }, []);

  const handleLogout = () => {
    const loadingToast = toast.loading("Đang thoát hệ thống quản trị...");
    localStorage.removeItem('currentUser');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    setTimeout(() => {
      toast.update(loadingToast, {
        render: "🏦 Đã đăng xuất an toàn!",
        type: "success",
        isLoading: false,
        autoClose: 2000
      });
      navigate('/login', { replace: true });
    }, 500);
  };

  const safeTx = stats.totalTransactions - stats.highRiskBlocked;
  const pieData = [
    { name: 'Giao dịch an toàn', value: safeTx > 0 ? safeTx : 0 },
    { name: 'Giao dịch bị chặn', value: stats.highRiskBlocked }
  ];
  const COLORS = ['#15803d', '#b91c1c'];

  if (isLoading) return (
    <div className="min-h-screen bg-[#f4f6f9] flex items-center justify-center">
      <p className="text-sm font-medium text-gray-400 tracking-widest uppercase">Đang tải dữ liệu...</p>
    </div>
  );

  return (
    // ── Nền trang: cool gray nhạt ──
    <div className="min-h-screen bg-[#f4f6f9]">

      {/* ── Topbar: navy đậm ── */}
      <header className="bg-[#1e2d40] px-8 py-4 flex justify-center items-center gap-x-80">
        <div>
          <p className="text-white font-semibold text-sm tracking-wide">Hệ thống quản trị rủi ro</p>
          <p className="text-white/40 text-xs mt-0.5">FinRisk AI Security System · Realtime Dashboard</p>
        </div>
        <button
          onClick={handleLogout}
          className="flex items-center gap-2 px-4 py-2 bg-white/10 hover:bg-red-500/20 border border-white/15 hover:border-red-400/30 text-white/70 hover:text-red-300 text-xs font-medium rounded-lg transition-all"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
          Thoát admin
        </button>
      </header>

      <div className="max-w-6xl mx-auto p-8 pb-16">

        {/* ── 3 stat cards ── */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">

          {/* Blue accent = neutral/info */}
          <div className="bg-white rounded-xl p-5 border-l-2 border-l-blue-600 border border-gray-100">
            <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2">Tổng giao dịch</p>
            <p className="text-3xl font-bold text-gray-800">{stats.totalTransactions}</p>
          </div>

          {/* Red accent = danger/blocked */}
          <div className="bg-white rounded-xl p-5 border-l-2 border-l-red-600 border border-gray-100">
            <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2">Gian lận / bị chặn</p>
            <p className="text-3xl font-bold text-red-600">{stats.highRiskBlocked}</p>
          </div>

          {/* Green accent = safe/success */}
          <div className="bg-white rounded-xl p-5 border-l-2 border-l-green-600 border border-gray-100">
            <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-2">Luân chuyển an toàn</p>
            <p className="text-xl font-bold text-green-700">
              {Number(stats.totalMoneyTransferred).toLocaleString('vi-VN')} <span className="text-sm font-medium text-gray-400">VND</span>
            </p>
          </div>
        </div>

        {/* ── Charts row ── */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">

          {/* Pie chart — white card */}
          <div className="bg-white rounded-xl p-6 border border-gray-100">
            <p className="text-xs font-semibold text-gray-400 uppercase tracking-widest mb-4">Tỉ lệ phân loại rủi ro (AI)</p>
            <div className="h-56 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%" cy="50%"
                    innerRadius={55} outerRadius={75}
                    paddingAngle={4}
                    dataKey="value"
                  >
                    {pieData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{ fontSize: 12, borderRadius: 8, border: '0.5px solid #e5e7eb', boxShadow: 'none' }}
                  />
                  <Legend iconSize={8} iconType="circle" wrapperStyle={{ fontSize: 12 }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* AI status — navy card, nhất quán với topbar */}
          <div className="bg-[#1e2d40] rounded-xl p-6">
            <p className="text-xs font-semibold text-white/40 uppercase tracking-widest mb-5 flex items-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse inline-block" />
              Trạng thái hệ thống AI
            </p>

            <div className="space-y-3">
              {/* Risk % */}
              <div className="bg-white/5 border border-white/10 rounded-lg p-4">
                <p className="text-[10px] font-semibold text-white/40 uppercase tracking-widest mb-2">Tỉ lệ rủi ro trung bình</p>
                <div className="flex items-end justify-between mb-2">
                  <p className={`text-2xl font-bold ${stats.riskPercentage > 20 ? 'text-amber-400' : 'text-green-400'}`}>
                    {stats.riskPercentage}%
                  </p>
                  <span className="text-white/30 text-xs">/ 100%</span>
                </div>
                {/* Progress bar */}
                <div className="h-1 bg-white/10 rounded-full">
                  <div
                    className={`h-1 rounded-full transition-all ${stats.riskPercentage > 20 ? 'bg-amber-400' : 'bg-green-400'}`}
                    style={{ width: `${Math.min(stats.riskPercentage, 100)}%` }}
                  />
                </div>
              </div>

              {/* Modules */}
              <div className="bg-white/5 border border-white/10 rounded-lg p-4">
                <p className="text-[10px] font-semibold text-white/40 uppercase tracking-widest mb-3">Modules đang hoạt động</p>
                <ul className="space-y-2">
                  {['DeepFace CNN Verification', 'Redis OTP Caching', 'Anti-Spam Detection Rule'].map(m => (
                    <li key={m} className="flex items-center gap-2 text-xs text-white/60">
                      <span className="w-1.5 h-1.5 rounded-full bg-green-400 flex-shrink-0" />
                      {m}
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        </div>

        {/* ── Quick nav ── */}
        <div className="border-t border-gray-200 pt-6">
          <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest mb-4">Bảng điều khiển nhanh</p>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">

            {/* Tất cả nav card đều white, chỉ icon có màu accent nhẹ */}
            {[
              {
                to: '/admin/rules',
                label: 'Rule Engine AI',
                desc: 'Cấu hình điểm số & thuật toán',
                iconBg: 'bg-gray-100',
                iconColor: 'text-gray-600',
                hoverBg: 'hover:bg-[#1e2d40]',
                icon: (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065zM15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                )
              },
              {
                to: '/admin/users',
                label: 'Quản lý người dùng',
                desc: 'Giám sát tài khoản & IP độc hại',
                iconBg: 'bg-blue-50',
                iconColor: 'text-blue-600',
                hoverBg: 'hover:bg-blue-600',
                icon: (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
                )
              },
              {
                to: '/admin/logs',
                label: 'Hộp đen hệ thống',
                desc: 'Audit log & lịch sử cấu hình',
                iconBg: 'bg-blue-50',
                iconColor: 'text-blue-600',
                hoverBg: 'hover:bg-blue-600',
                icon: (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                )
              },
              {
                to: '/admin/policies',
                label: 'Ngưỡng rủi ro',
                desc: 'Điều chỉnh mức điểm bảo mật',
                // Amber = cảnh báo/threshold — có nghĩa, không phải decoration
                iconBg: 'bg-amber-50',
                iconColor: 'text-amber-600',
                hoverBg: 'hover:bg-amber-500',
                icon: (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
                )
              },
              {
                to: '/admin/ai-logs',
                label: 'AI Audit Trail',
                desc: 'Lịch sử sinh trắc & cảm xúc',
                iconBg: 'bg-blue-50',
                iconColor: 'text-blue-600',
                hoverBg: 'hover:bg-blue-600',
                icon: (
                  <>
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                  </>
                )
              },
              {
                to: '/admin/all-transactions',
                label: 'Quản lý Giao dịch',
                desc: 'Giám sát dòng tiền & rủi ro',
                iconBg: 'bg-emerald-50',
                iconColor: 'text-emerald-600',
                hoverBg: 'hover:bg-emerald-600',
                icon: (
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                )
              }
            ].map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className={`bg-white border border-gray-100 rounded-xl p-5 flex items-center justify-between group transition-all hover:border-transparent ${item.hoverBg} hover:shadow-md`}
              >
                <div>
                  <p className="text-sm font-semibold text-gray-800 group-hover:text-white transition-colors">{item.label}</p>
                  <p className="text-xs text-gray-400 group-hover:text-white/60 mt-0.5 transition-colors">{item.desc}</p>
                </div>
                <div className={`w-10 h-10 ${item.iconBg} ${item.iconColor} rounded-lg flex items-center justify-center flex-shrink-0 group-hover:bg-white/20 group-hover:text-white transition-all`}>
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    {item.icon}
                  </svg>
                </div>
              </Link>
            ))}

            {/* Coming soon */}
            <div className="bg-white border border-dashed border-gray-200 rounded-xl p-5 flex items-center justify-center opacity-40 cursor-not-allowed">
              <span className="text-xs font-medium text-gray-400 flex items-center gap-2">
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                </svg>
                Coming soon
              </span>
            </div>

          </div>
        </div>

      </div>
    </div>
  );
}