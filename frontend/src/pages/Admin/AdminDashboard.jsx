

// import React, { useState, useEffect } from 'react';
// import axiosClient from '../../api/axiosClient'; 
// import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts';
// import { toast } from 'react-toastify';
// import { Link } from 'react-router-dom'; // 🚀 1. IMPORT LINK ĐỂ ĐIỀU HƯỚNG

// export default function AdminDashboard() {
//   const [stats, setStats] = useState({
//     totalTransactions: 0,
//     highRiskBlocked: 0,
//     totalMoneyTransferred: 0,
//     riskPercentage: 0
//   });
//   const [isLoading, setIsLoading] = useState(true);

//   useEffect(() => {
//     const fetchStats = async () => {
//       try {
//         const response = await axiosClient.get('/admin/dashboard-stats');
//         setStats(response.data);
//       } catch (error) {
//         console.error("Lỗi tải dữ liệu Dashboard:", error);
        
//         if(error.response?.status === 403) {
//             toast.error("🚨 Xâm nhập trái phép! Bạn không có quyền truy cập khu vực này!");
//             setTimeout(() => {
//                 window.location.href = '/dashboard';
//             }, 1500);
//         } else {
//             toast.error("Lỗi kết nối máy chủ quản trị!");
//         }
//       } finally {
//         setIsLoading(false);
//       }
//     };
    
//     fetchStats();
//     const interval = setInterval(fetchStats, 5000);
//     return () => clearInterval(interval);
//   }, []);

//   const safeTx = stats.totalTransactions - stats.highRiskBlocked;
//   const pieData = [
//     { name: 'Giao dịch An toàn', value: safeTx > 0 ? safeTx : 0 },
//     { name: 'Giao dịch Bị chặn (HIGH)', value: stats.highRiskBlocked }
//   ];
//   const COLORS = ['#22c55e', '#ef4444'];

//   if (isLoading) return <div className="min-h-screen flex items-center justify-center text-xl font-bold">Đang tải Dữ liệu AI Core...</div>;

//   return (
//     <div className="min-h-screen bg-slate-50 p-8">
//       <div className="max-w-6xl mx-auto">
        
//         {/* HEADER & NÚT ĐĂNG XUẤT (Tùy chọn) */}
//         <div className="mb-8 flex justify-between items-center">
//           <div>
//             <h1 className="text-3xl font-black text-slate-900 tracking-tight">HỆ THỐNG QUẢN TRỊ RỦI RO</h1>
//             <p className="text-slate-500 font-medium mt-1">FinRisk AI Security System - Realtime Dashboard</p>
//           </div>
//           <Link to="/dashboard" className="px-4 py-2 bg-white border border-slate-200 text-slate-600 font-bold rounded-lg hover:bg-slate-100 transition-colors shadow-sm">
//             Thoát Admin
//           </Link>
//         </div>

//         {/* 3 THẺ SỐ LIỆU TỔNG QUAN (CARDS) */}
//         <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
//           <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100 border-l-4 border-l-blue-500">
//             <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-2">Tổng Giao Dịch</h3>
//             <p className="text-4xl font-black text-slate-800">{stats.totalTransactions}</p>
//           </div>
          
//           <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100 border-l-4 border-l-red-500">
//             <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-2">Gian lận / Bị chặn</h3>
//             <p className="text-4xl font-black text-red-600">{stats.highRiskBlocked}</p>
//           </div>
          
//           <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100 border-l-4 border-l-green-500">
//             <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-2">Luân chuyển an toàn</h3>
//             <p className="text-3xl font-black text-green-600">
//               {Number(stats.totalMoneyTransferred).toLocaleString()} <span className="text-base">VND</span>
//             </p>
//           </div>
//         </div>

//         {/* KHU VỰC BIỂU ĐỒ VÀ PHÂN TÍCH */}
//         <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
//           <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100">
//             <h3 className="text-lg font-bold text-slate-800 mb-4">Tỉ lệ Phân loại Rủi ro (AI)</h3>
//             <div className="h-64 w-full">
//               <ResponsiveContainer width="100%" height="100%">
//                 <PieChart>
//                   <Pie data={pieData} cx="50%" cy="50%" innerRadius={60} outerRadius={80} paddingAngle={5} dataKey="value">
//                     {pieData.map((entry, index) => (
//                       <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
//                     ))}
//                   </Pie>
//                   <Tooltip />
//                   <Legend />
//                 </PieChart>
//               </ResponsiveContainer>
//             </div>
//           </div>

//           <div className="bg-slate-900 rounded-2xl p-6 shadow-xl relative overflow-hidden">
//             <div className="absolute top-0 right-0 w-32 h-32 bg-blue-500 rounded-full blur-3xl opacity-20 -mr-10 -mt-10"></div>
//             <h3 className="text-lg font-bold text-white mb-6 flex items-center">
//               <span className="w-2 h-2 rounded-full bg-green-400 animate-pulse mr-2"></span>
//               Trạng thái Hệ thống AI
//             </h3>
            
//             <div className="space-y-4">
//               <div className="bg-slate-800 rounded-xl p-4 border border-slate-700">
//                 <p className="text-slate-400 text-xs font-bold uppercase tracking-wider mb-1">Tỉ lệ Rủi ro trung bình</p>
//                 <div className="flex items-end justify-between">
//                   <p className={`text-2xl font-black ${stats.riskPercentage > 20 ? 'text-orange-500' : 'text-green-400'}`}>
//                     {stats.riskPercentage}%
//                   </p>
//                   <span className="text-slate-500 text-sm">/ 100%</span>
//                 </div>
//               </div>

//               <div className="bg-slate-800 rounded-xl p-4 border border-slate-700">
//                 <p className="text-slate-400 text-xs font-bold uppercase tracking-wider mb-1">Modules Đang Hoạt Động</p>
//                 <ul className="text-sm text-slate-300 space-y-2 mt-2">
//                   <li className="flex items-center">✅ DeepFace CNN Verification</li>
//                   <li className="flex items-center">✅ Redis OTP Caching</li>
//                   <li className="flex items-center">✅ Anti-Spam Detection Rule</li>
//                 </ul>
//               </div>
//             </div>
//           </div>
//         </div>
//                 {/* 🚀 2. KHU VỰC MENU ĐIỀU HƯỚNG MỚI (CHUYỂN SANG TRANG RULE) */}
//         <div className="mb-8">
//           <h2 className="text-lg font-bold text-slate-800 mb-4 flex items-center">
//             <span className="mr-2">⚡</span> Bảng Điều Khiển Nhanh
//           </h2>
//           <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            
//             {/* THẺ ĐIỀU HƯỚNG TỚI RULE ENGINE */}
//             <Link to="/admin/rules" className="bg-slate-900 hover:bg-black group rounded-2xl p-6 flex items-center justify-between transition-all transform hover:-translate-y-1 shadow-lg cursor-pointer">
//               <div>
//                 <h3 className="font-black text-white text-lg">Rule Engine AI</h3>
//                 <p className="text-slate-400 text-sm mt-1">Cấu hình điểm số & Thuật toán</p>
//               </div>
//               <div className="w-12 h-12 bg-slate-800 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
//                 <svg className="w-6 h-6 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"></path><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
//               </div>
//             </Link>

//             {/* Các thẻ tương lai (Ví dụ: Quản lý User) */}
//             <div className="bg-white border-2 border-dashed border-slate-200 rounded-2xl p-6 flex items-center justify-center opacity-50 cursor-not-allowed">
//               <span className="font-bold text-slate-400">Quản lý Người dùng (Coming Soon)</span>
//             </div>
//             <div className="bg-white border-2 border-dashed border-slate-200 rounded-2xl p-6 flex items-center justify-center opacity-50 cursor-not-allowed">
//               <span className="font-bold text-slate-400">Log Hệ thống (Coming Soon)</span>
//             </div>
//           </div>
//         </div>
//       </div>
//     </div>
//   );
// }



import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient'; 
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { toast } from 'react-toastify';
import { Link } from 'react-router-dom';

export default function AdminDashboard() {
  const [stats, setStats] = useState({
    totalTransactions: 0,
    highRiskBlocked: 0,
    totalMoneyTransferred: 0,
    riskPercentage: 0
  });
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const response = await axiosClient.get('/admin/dashboard-stats');
        setStats(response.data);
      } catch (error) {
        console.error("Lỗi tải dữ liệu Dashboard:", error);
        
        if(error.response?.status === 403) {
            toast.error("🚨 Xâm nhập trái phép! Bạn không có quyền truy cập khu vực này!");
            setTimeout(() => {
                window.location.href = '/dashboard';
            }, 1500);
        } else {
            toast.error("Lỗi kết nối máy chủ quản trị!");
        }
      } finally {
        setIsLoading(false);
      }
    };
    
    fetchStats();
    const interval = setInterval(fetchStats, 5000);
    return () => clearInterval(interval);
  }, []);

  const safeTx = stats.totalTransactions - stats.highRiskBlocked;
  const pieData = [
    { name: 'Giao dịch An toàn', value: safeTx > 0 ? safeTx : 0 },
    { name: 'Giao dịch Bị chặn (HIGH)', value: stats.highRiskBlocked }
  ];
  const COLORS = ['#22c55e', '#ef4444'];

  if (isLoading) return <div className="min-h-screen flex items-center justify-center text-xl font-bold">Đang tải Dữ liệu AI Core...</div>;

  return (
    <div className="min-h-screen bg-slate-50 p-8 pb-16">
      <div className="max-w-6xl mx-auto">
        
        {/* HEADER & NÚT ĐĂNG XUẤT */}
        <div className="mb-8 flex justify-between items-center">
          <div>
            <h1 className="text-3xl font-black text-slate-900 tracking-tight">HỆ THỐNG QUẢN TRỊ RỦI RO</h1>
            <p className="text-slate-500 font-medium mt-1">FinRisk AI Security System - Realtime Dashboard</p>
          </div>
          <Link to="/dashboard" className="px-5 py-2.5 bg-white border border-slate-200 text-slate-700 font-bold rounded-xl hover:bg-slate-100 transition-colors shadow-sm flex items-center">
            <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path></svg>
            Thoát Admin
          </Link>
        </div>

        {/* 3 THẺ SỐ LIỆU TỔNG QUAN */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100 border-l-4 border-l-blue-500 hover:shadow-md transition-shadow">
            <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-2">Tổng Giao Dịch</h3>
            <p className="text-4xl font-black text-slate-800">{stats.totalTransactions}</p>
          </div>
          
          <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100 border-l-4 border-l-red-500 hover:shadow-md transition-shadow">
            <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-2">Gian lận / Bị chặn</h3>
            <p className="text-4xl font-black text-red-600">{stats.highRiskBlocked}</p>
          </div>
          
          <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100 border-l-4 border-l-green-500 hover:shadow-md transition-shadow">
            <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-2">Luân chuyển an toàn</h3>
            <p className="text-3xl font-black text-green-600">
              {Number(stats.totalMoneyTransferred).toLocaleString()} <span className="text-base">VND</span>
            </p>
          </div>
        </div>

        {/* KHU VỰC BIỂU ĐỒ VÀ PHÂN TÍCH */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Biểu đồ */}
          <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100">
            <h3 className="text-lg font-bold text-slate-800 mb-4">Tỉ lệ Phân loại Rủi ro (AI)</h3>
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={pieData} cx="50%" cy="50%" innerRadius={60} outerRadius={80} paddingAngle={5} dataKey="value">
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

          {/* Module hệ thống */}
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

        {/* ĐƯỜNG CHIA CÁCH PHẦN BÁO CÁO VÀ HÀNH ĐỘNG */}
        <hr className="my-10 border-slate-200" />

        {/* KHU VỰC MENU ĐIỀU HƯỚNG MỚI (CHUYỂN SANG TRANG RULE) */}
        {/* KHU VỰC MENU ĐIỀU HƯỚNG MỚI */}
        <div>
          <h2 className="text-xl font-bold text-slate-800 mb-5 flex items-center">
            <span className="mr-2 text-blue-500">⚡</span> Bảng Điều Khiển Nhanh
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            
            {/* THẺ ĐIỀU HƯỚNG TỚI RULE ENGINE */}
            <Link to="/admin/rules" className="bg-slate-900 hover:bg-black group rounded-2xl p-6 flex items-center justify-between transition-all transform hover:-translate-y-1 hover:shadow-2xl shadow-lg cursor-pointer border border-slate-800">
              <div>
                <h3 className="font-black text-white text-lg">Rule Engine AI</h3>
                <p className="text-slate-400 text-sm mt-1">Cấu hình điểm số & Thuật toán</p>
              </div>
              <div className="w-12 h-12 bg-slate-800 rounded-full flex items-center justify-center group-hover:scale-110 group-hover:bg-blue-600 transition-all duration-300">
                <svg className="w-6 h-6 text-blue-400 group-hover:text-white transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"></path><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path></svg>
              </div>
            </Link>

            {/* 🚀 THẺ ĐIỀU HƯỚNG TỚI QUẢN LÝ USER */}
            <Link to="/admin/users" className="bg-indigo-600 hover:bg-indigo-700 group rounded-2xl p-6 flex items-center justify-between transition-all transform hover:-translate-y-1 hover:shadow-2xl shadow-lg shadow-indigo-600/30 cursor-pointer">
              <div>
                <h3 className="font-black text-white text-lg">Quản lý Người dùng</h3>
                <p className="text-indigo-200 text-sm mt-1">Giám sát tài khoản & IP độc hại</p>
              </div>
              <div className="w-12 h-12 bg-indigo-500 rounded-full flex items-center justify-center group-hover:scale-110 group-hover:bg-white transition-all duration-300">
                <svg className="w-6 h-6 text-white group-hover:text-indigo-600 transition-colors" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"></path></svg>
              </div>
            </Link>

            {/* Thẻ Log hệ thống (Vẫn để Coming Soon chờ làm sau) */}
            <div className="bg-white border-2 border-dashed border-slate-200 rounded-2xl p-6 flex items-center justify-center opacity-60 cursor-not-allowed hover:bg-slate-50 transition-colors">
              <span className="font-bold text-slate-400 flex items-center">
                <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
                Log Hệ thống <span className="ml-2 text-xs bg-slate-200 text-slate-500 px-2 py-0.5 rounded-md">Soon</span>
              </span>
            </div>
            
          </div>
        </div>

      </div>
    </div>
  );
}