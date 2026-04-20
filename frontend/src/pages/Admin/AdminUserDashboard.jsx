

// import React, { useState, useEffect } from 'react';
// import axiosClient from '../../api/axiosClient';
// import { toast } from 'react-toastify';
// import { Link } from 'react-router-dom';

// export default function AdminUserDashboard() {
//   const [users, setUsers] = useState([]);
//   const [isLoading, setIsLoading] = useState(true);
  
//   // 🚀 BƯỚC 2: STATE LƯU TRỮ TỪ KHÓA TÌM KIẾM
//   const [searchTerm, setSearchTerm] = useState('');

//   // 1. Lấy danh sách người dùng từ API Backend
//   const fetchUsers = async () => {
//     try {
//       setIsLoading(true);
//       const response = await axiosClient.get('/admin/users');
//       setUsers(response.data);
//     } catch (error) {
//       console.error('Lỗi tải danh sách người dùng:', error);
//       toast.error("Không thể kết nối máy chủ để lấy danh sách người dùng!");
//     } finally {
//       setIsLoading(false);
//     }
//   };

//   useEffect(() => {
//     fetchUsers();
//   }, []);

//   // 2. Xử lý Khóa/Mở khóa tài khoản
//   const handleToggleStatus = async (id) => {
//     try {
//       await axiosClient.patch(`/admin/users/${id}/toggle-status`, null, {
//         headers: { 'X-Admin-Username': 'admin_thach' } 
//       });
//       toast.success("Cập nhật trạng thái tài khoản thành công!");
//       fetchUsers(); // Refresh lại danh sách
//     } catch (error) {
//       toast.error("Lỗi khi thực hiện khóa tài khoản!");
//     }
//   };

//   // 3. Xử lý Đánh dấu/Gỡ nghi ngờ IP
//   const handleToggleSuspicious = async (id) => {
//     try {
//       await axiosClient.patch(`/admin/users/${id}/toggle-suspicious`, null, {
//         headers: { 'X-Admin-Username': 'admin_thach' }
//       });
//       toast.info("Đã cập nhật mức độ tin cậy của phiên đăng nhập");
//       fetchUsers();
//     } catch (error) {
//       toast.error("Lỗi hệ thống khi cập nhật cờ rủi ro!");
//     }
//   };

//   // 🚀 BƯỚC 2: HÀM LỌC DANH SÁCH USER (REAL-TIME)
//   const filteredUsers = users.filter(user => {
//     const searchLower = searchTerm.toLowerCase();
//     // Tìm trong: Tên đầy đủ, Username, Số ĐT, hoặc Email
//     return (
//       (user.fullName && user.fullName.toLowerCase().includes(searchLower)) ||
//       (user.username && user.username.toLowerCase().includes(searchLower)) ||
//       (user.phoneNumber && user.phoneNumber.includes(searchLower)) ||
//       (user.email && user.email.toLowerCase().includes(searchLower))
//     );
//   });

//   return (
//     <div className="min-h-screen bg-slate-50 p-8 pb-16">
//       <div className="max-w-7xl mx-auto">
        
//         {/* HEADER */}
//         <div className="mb-8 flex justify-between items-center">
//           <div>
//             <h1 className="text-3xl font-black text-slate-900 tracking-tight flex items-center">
//                Quản lý Người dùng
//             </h1>
//             <p className="text-slate-500 font-medium mt-1">Giám sát hoạt động và quản lý trạng thái bảo mật của khách hàng</p>
//           </div>
//           <Link to="/admin" className="px-5 py-2.5 bg-slate-900 text-white font-bold rounded-xl hover:bg-black transition-colors shadow-lg flex items-center">
//             <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path></svg>
//             Quay lại Dashboard
//           </Link>
//         </div>

//         {/* 🚀 BƯỚC 2: THANH TÌM KIẾM (SEARCH BAR) */}
//         <div className="mb-6">
//           <div className="relative w-full max-w-md">
//             <div className="absolute inset-y-0 left-0 flex items-center pl-4 pointer-events-none">
//               <svg className="w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path></svg>
//             </div>
//             <input 
//               type="text" 
//               placeholder="Tìm kiếm theo Tên, SĐT, Email..." 
//               value={searchTerm}
//               onChange={(e) => setSearchTerm(e.target.value)}
//               className="w-full pl-12 pr-4 py-3 bg-white border border-slate-200 rounded-2xl font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent shadow-sm transition-all"
//             />
//           </div>
//         </div>

//         {/* BẢNG DANH SÁCH USER */}
//         <div className="bg-white rounded-3xl shadow-sm border border-slate-100 overflow-hidden">
//           <div className="overflow-x-auto">
//             {isLoading ? (
//               <div className="text-center py-20 font-bold text-slate-400 animate-pulse">Đang truy xuất cơ sở dữ liệu người dùng...</div>
//             ) : (
//               <table className="w-full text-left">
//                 <thead className="bg-slate-50/50">
//                   <tr className="border-b border-slate-100 text-[11px] font-bold text-slate-400 uppercase tracking-widest">
//                     <th className="px-6 py-4">ID</th>
//                     <th className="px-6 py-4">Thông tin cơ bản</th>
//                     <th className="px-6 py-4">Liên hệ</th>
//                     <th className="px-6 py-4">Trạng thái</th>
//                     <th className="px-6 py-4">Bảo mật IP</th>
//                     <th className="px-6 py-4 text-right">Hành động</th>
//                   </tr>
//                 </thead>
//                 <tbody className="divide-y divide-slate-50">
//                   {/* 🚀 BƯỚC 2: ĐỔI MẢNG MAP TỪ users SANG filteredUsers */}
//                   {filteredUsers.map(user => (
//                     <tr key={user.id} className="hover:bg-slate-50/80 transition-colors group">
//                       <td className="px-6 py-5 text-slate-400 font-mono text-xs">#{user.id}</td>
//                       <td className="px-6 py-5">
//                         <p className="font-bold text-slate-800">{user.fullName}</p>
//                         <p className="text-xs text-slate-400">@{user.username}</p>
//                       </td>
//                       <td className="px-6 py-5 text-sm">
//                         <p className="text-slate-600 font-medium">{user.phoneNumber}</p>
//                         <p className="text-xs text-slate-400">{user.email}</p>
//                       </td>
//                       <td className="px-6 py-5">
//                         <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-tighter ${user.status === 'ACTIVE' ? 'bg-green-100 text-green-600' : 'bg-red-100 text-red-600'}`}>
//                           {user.status}
//                         </span>
//                       </td>
//                       <td className="px-6 py-5">
//                         {user.suspiciousSession ? (
//                           <div className="flex items-center text-orange-600 font-bold text-xs bg-orange-50 px-3 py-1 rounded-lg border border-orange-100 w-fit">
//                             <span className="w-2 h-2 bg-orange-600 rounded-full animate-ping mr-2"></span>
//                             IP NGHI VẤN
//                           </div>
//                         ) : (
//                           <span className="text-slate-400 text-xs font-medium">An toàn</span>
//                         )}
//                       </td>
//                       <td className="px-6 py-5 text-right space-x-2">
//                         <button 
//                           onClick={() => handleToggleSuspicious(user.id)}
//                           className={`p-2 rounded-lg transition-all ${user.suspiciousSession ? 'bg-slate-100 text-slate-600' : 'bg-orange-50 text-orange-600 hover:bg-orange-100'}`}
//                           title={user.suspiciousSession ? "Gỡ bỏ cảnh báo IP" : "Đánh dấu IP đáng ngờ"}
//                         >
//                           <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
//                         </button>
                        
//                         <button 
//                           onClick={() => handleToggleStatus(user.id)}
//                           className={`px-4 py-2 rounded-lg font-bold text-xs transition-all ${user.status === 'ACTIVE' ? 'bg-red-50 text-red-600 hover:bg-red-100' : 'bg-green-50 text-green-600 hover:bg-green-100'}`}
//                         >
//                           {user.status === 'ACTIVE' ? 'KHÓA TK' : 'MỞ KHÓA'}
//                         </button>
//                       </td>
//                     </tr>
//                   ))}
                  
//                   {/* Hiển thị khi tìm kiếm không ra kết quả */}
//                   {filteredUsers.length === 0 && !isLoading && (
//                     <tr>
//                       <td colSpan="6" className="py-12 text-center">
//                         <p className="text-slate-400 font-bold text-lg">Không tìm thấy người dùng nào 🕵️‍♂️</p>
//                         <p className="text-slate-400 text-sm mt-1">Thử đổi từ khóa tìm kiếm khác xem sao.</p>
//                       </td>
//                     </tr>
//                   )}
//                 </tbody>
//               </table>
//             )}
//           </div>
//         </div>

//         {/* FOOTER INFO */}
//         <div className="mt-6 flex justify-between items-center px-4">
//            <p className="text-slate-400 text-xs font-medium">Đang hiển thị: {filteredUsers.length} / {users.length} người dùng</p>
//            <div className="bg-blue-50 text-blue-700 px-4 py-2 rounded-xl text-[11px] font-bold border border-blue-100">
//              TIP: Khóa tài khoản sẽ thu hồi Token, ép người dùng đăng xuất ngay lập tức.
//            </div>
//         </div>
//       </div>
//     </div>
//   );
// }

import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import { Link } from 'react-router-dom';

export default function AdminUserDashboard() {
  const [users, setUsers] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  // 🚀 STATE CHO MODAL HỒ SƠ
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);
  const [recentTransactions, setRecentTransactions] = useState([]);
  const [isTxLoading, setIsTxLoading] = useState(false);

  const fetchUsers = async () => {
    try {
      setIsLoading(true);
      const response = await axiosClient.get('/admin/users');
      setUsers(response.data);
    } catch (error) {
      toast.error("Không thể kết nối máy chủ để lấy danh sách người dùng!");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleToggleStatus = async (id) => {
    try {
      await axiosClient.patch(`/admin/users/${id}/toggle-status`, null, {
        headers: { 'X-Admin-Username': 'admin_thach' } 
      });
      toast.success("Cập nhật trạng thái tài khoản thành công!");
      fetchUsers(); 
    } catch (error) {
      toast.error("Lỗi khi thực hiện khóa tài khoản!");
    }
  };

  const handleToggleSuspicious = async (id) => {
    try {
      await axiosClient.patch(`/admin/users/${id}/toggle-suspicious`, null, {
        headers: { 'X-Admin-Username': 'admin_thach' }
      });
      toast.info("Đã cập nhật mức độ tin cậy của phiên đăng nhập");
      fetchUsers();
    } catch (error) {
      toast.error("Lỗi hệ thống khi cập nhật cờ rủi ro!");
    }
  };

  // 🚀 MỞ MODAL VÀ (TẠM THỜI) LOAD DỮ LIỆU GIẢ
  const openUserProfile = async (user) => {
    setSelectedUser(user);
    setIsProfileModalOpen(true);
    setIsTxLoading(true);

    // Chỗ này lát nữa sẽ gọi API thật: await axiosClient.get(`/admin/users/${user.id}/recent-transactions`);
    // Tạm thời dùng setTimeout để giả lập độ trễ mạng 0.5s
    setTimeout(() => {
      setRecentTransactions([
        { id: 'TX9981', amount: 50000000, status: 'BLOCKED', riskScore: 85, date: 'Vừa xong' },
        { id: 'TX9980', amount: 200000, status: 'SUCCESS', riskScore: 10, date: '2 giờ trước' },
        { id: 'TX9975', amount: 1500000, status: 'PENDING_FACE', riskScore: 45, date: 'Hôm qua' },
      ]);
      setIsTxLoading(false);
    }, 500);
  };

  const filteredUsers = users.filter(user => {
    const searchLower = searchTerm.toLowerCase();
    return (
      (user.fullName && user.fullName.toLowerCase().includes(searchLower)) ||
      (user.username && user.username.toLowerCase().includes(searchLower)) ||
      (user.phoneNumber && user.phoneNumber.includes(searchLower)) ||
      (user.email && user.email.toLowerCase().includes(searchLower))
    );
  });

  return (
    <div className="min-h-screen bg-slate-50 p-8 pb-16 relative">
      <div className="max-w-7xl mx-auto">
        
        {/* HEADER */}
        <div className="mb-8 flex justify-between items-center">
          <div>
            <h1 className="text-3xl font-black text-slate-900 tracking-tight flex items-center">
               Quản lý Người dùng
            </h1>
            <p className="text-slate-500 font-medium mt-1">Giám sát hoạt động và quản lý trạng thái bảo mật của khách hàng</p>
          </div>
          <Link to="/admin" className="px-5 py-2.5 bg-slate-900 text-white font-bold rounded-xl hover:bg-black transition-colors shadow-lg flex items-center">
            <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path></svg>
            Quay lại Dashboard
          </Link>
        </div>

        {/* THANH TÌM KIẾM */}
        <div className="mb-6">
          <div className="relative w-full max-w-md">
            <div className="absolute inset-y-0 left-0 flex items-center pl-4 pointer-events-none">
              <svg className="w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path></svg>
            </div>
            <input 
              type="text" 
              placeholder="Tìm kiếm theo Tên, SĐT, Email..." 
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-12 pr-4 py-3 bg-white border border-slate-200 rounded-2xl font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent shadow-sm transition-all"
            />
          </div>
        </div>

        {/* BẢNG DANH SÁCH USER */}
        <div className="bg-white rounded-3xl shadow-sm border border-slate-100 overflow-hidden">
          <div className="overflow-x-auto">
            {isLoading ? (
              <div className="text-center py-20 font-bold text-slate-400 animate-pulse">Đang truy xuất cơ sở dữ liệu người dùng...</div>
            ) : (
              <table className="w-full text-left">
                <thead className="bg-slate-50/50">
                  <tr className="border-b border-slate-100 text-[11px] font-bold text-slate-400 uppercase tracking-widest">
                    <th className="px-6 py-4">ID</th>
                    <th className="px-6 py-4">Thông tin cơ bản</th>
                    <th className="px-6 py-4">Liên hệ</th>
                    <th className="px-6 py-4">Trạng thái</th>
                    <th className="px-6 py-4">Bảo mật IP</th>
                    <th className="px-6 py-4 text-right">Hành động</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {filteredUsers.map(user => (
                    <tr key={user.id} className="hover:bg-slate-50/80 transition-colors group">
                      <td className="px-6 py-5 text-slate-400 font-mono text-xs">#{user.id}</td>
                      <td className="px-6 py-5">
                        <p className="font-bold text-slate-800">{user.fullName}</p>
                        <p className="text-xs text-slate-400">@{user.username}</p>
                      </td>
                      <td className="px-6 py-5 text-sm">
                        <p className="text-slate-600 font-medium">{user.phoneNumber}</p>
                        <p className="text-xs text-slate-400">{user.email}</p>
                      </td>
                      <td className="px-6 py-5">
                        <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-tighter ${user.status === 'ACTIVE' ? 'bg-green-100 text-green-600' : 'bg-red-100 text-red-600'}`}>
                          {user.status}
                        </span>
                      </td>
                      <td className="px-6 py-5">
                        {user.suspiciousSession ? (
                          <div className="flex items-center text-orange-600 font-bold text-xs bg-orange-50 px-3 py-1 rounded-lg border border-orange-100 w-fit">
                            <span className="w-2 h-2 bg-orange-600 rounded-full animate-ping mr-2"></span>
                            IP NGHI VẤN
                          </div>
                        ) : (
                          <span className="text-slate-400 text-xs font-medium">An toàn</span>
                        )}
                      </td>
                      <td className="px-6 py-5 text-right space-x-2 flex justify-end items-center">
                        {/* 🚀 NÚT XEM HỒ SƠ MỚI */}
                        <button 
                          onClick={() => openUserProfile(user)}
                          className="px-3 py-2 bg-cyan-50 text-cyan-600 hover:bg-cyan-100 rounded-lg font-bold text-xs transition-all mr-2 flex items-center"
                        >
                          <svg className="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path></svg>
                          HỒ SƠ
                        </button>

                        <button 
                          onClick={() => handleToggleSuspicious(user.id)}
                          className={`p-2 rounded-lg transition-all ${user.suspiciousSession ? 'bg-slate-100 text-slate-600' : 'bg-orange-50 text-orange-600 hover:bg-orange-100'}`}
                          title="Cảnh báo IP"
                        >
                          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
                        </button>
                        
                        <button 
                          onClick={() => handleToggleStatus(user.id)}
                          className={`px-4 py-2 rounded-lg font-bold text-xs transition-all ${user.status === 'ACTIVE' ? 'bg-red-50 text-red-600 hover:bg-red-100' : 'bg-green-50 text-green-600 hover:bg-green-100'}`}
                        >
                          {user.status === 'ACTIVE' ? 'KHÓA TK' : 'MỞ KHÓA'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>

      </div>

      {/* ========================================== */}
      {/* 🚀 MODAL HỒ SƠ RỦI RO CHI TIẾT */}
      {/* ========================================== */}
      {isProfileModalOpen && selectedUser && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-3xl w-full max-w-2xl overflow-hidden shadow-2xl animate-in fade-in zoom-in duration-200">
            
            {/* Modal Header */}
            <div className="bg-slate-900 p-6 flex justify-between items-start">
              <div>
                <h2 className="text-xl font-black text-white flex items-center">
                  Hồ sơ Kiểm sát: {selectedUser.fullName}
                </h2>
                <p className="text-slate-400 text-sm mt-1">ID Hệ thống: #{selectedUser.id} | Username: {selectedUser.username}</p>
              </div>
              <button onClick={() => setIsProfileModalOpen(false)} className="text-slate-400 hover:text-white bg-slate-800 p-2 rounded-full">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6">
              
              {/* Thẻ Cảnh báo nếu có */}
              {selectedUser.suspiciousSession && (
                <div className="mb-6 bg-red-50 border border-red-100 p-4 rounded-xl flex items-start">
                  <span className="text-2xl mr-3">🚨</span>
                  <div>
                    <h4 className="text-red-800 font-bold text-sm">Cảnh báo An ninh mức độ cao!</h4>
                    <p className="text-red-600 text-xs mt-1">Tài khoản này đang bị hệ thống đánh dấu IP đáng ngờ. Mọi giao dịch sẽ bị cộng thêm điểm rủi ro mặc định.</p>
                  </div>
                </div>
              )}

              <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4">Hoạt động giao dịch gần đây</h3>
              
              {isTxLoading ? (
                <div className="py-10 text-center text-slate-400 font-medium animate-pulse">Đang trích xuất dữ liệu từ Core Banking...</div>
              ) : (
                <div className="space-y-3">
                  {recentTransactions.map((tx, index) => (
                    <div key={index} className="flex items-center justify-between p-4 bg-slate-50 border border-slate-100 rounded-2xl hover:border-blue-200 transition-colors">
                      <div className="flex items-center space-x-4">
                        <div className={`w-10 h-10 rounded-full flex items-center justify-center font-black ${tx.status === 'BLOCKED' ? 'bg-red-100 text-red-600' : tx.status === 'SUCCESS' ? 'bg-green-100 text-green-600' : 'bg-orange-100 text-orange-600'}`}>
                          {tx.status === 'SUCCESS' ? '✓' : tx.status === 'BLOCKED' ? '✕' : '!'}
                        </div>
                        <div>
                          <p className="font-black text-slate-800">{Number(tx.amount).toLocaleString()} VND</p>
                          <p className="text-xs text-slate-400 font-medium">{tx.date} • Mã GD: {tx.id}</p>
                        </div>
                      </div>
                      <div className="text-right">
                        <p className={`text-xs font-bold uppercase tracking-wider ${tx.status === 'BLOCKED' ? 'text-red-500' : tx.status === 'SUCCESS' ? 'text-green-500' : 'text-orange-500'}`}>
                          {tx.status}
                        </p>
                        <p className="text-[11px] font-bold text-slate-400 mt-1">
                          Điểm rủi ro AI: <span className={tx.riskScore > 50 ? 'text-red-500' : 'text-slate-600'}>{tx.riskScore}</span>
                        </p>
                      </div>
                    </div>
                  ))}
                  {recentTransactions.length === 0 && (
                    <p className="text-center text-slate-400 text-sm py-4">Chưa có giao dịch nào được ghi nhận.</p>
                  )}
                </div>
              )}
            </div>
            
            {/* Modal Footer */}
            <div className="bg-slate-50 p-4 border-t border-slate-100 flex justify-end">
              <button onClick={() => setIsProfileModalOpen(false)} className="px-5 py-2.5 bg-white border border-slate-200 text-slate-600 font-bold rounded-xl hover:bg-slate-100 transition-colors">
                Đóng hồ sơ
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}