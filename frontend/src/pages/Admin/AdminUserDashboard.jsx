 

// import React, { useState, useEffect } from 'react';
// import axiosClient from '../../api/axiosClient';
// import { toast } from 'react-toastify';
// import { Link } from 'react-router-dom';

// export default function AdminUserDashboard() {
//   const [users, setUsers] = useState([]);
//   const [isLoading, setIsLoading] = useState(true);
//   const [searchTerm, setSearchTerm] = useState('');
  
//   // 🚀 STATE MỚI: QUẢN LÝ TAB SWITCHER
//   const [activeTab, setActiveTab] = useState('general'); // 'general' hoặc 'biometric'

//   // STATE CHO MODAL HỒ SƠ (GIỮ NGUYÊN)
//   const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
//   const [selectedUser, setSelectedUser] = useState(null);
//   const [recentTransactions, setRecentTransactions] = useState([]);
//   const [isTxLoading, setIsTxLoading] = useState(false);

//   // 🚀 STATE MỚI: CHO MODAL CẢNH BÁO XÓA KHUÔN MẶT
//   const [isResetModalOpen, setIsResetModalOpen] = useState(false);
//   const [userToReset, setUserToReset] = useState(null);

//   const fetchUsers = async () => {
//     try {
//       setIsLoading(true);
//       const response = await axiosClient.get('/admin/users');
//       setUsers(response.data);
//     } catch (error) {
//       toast.error("Không thể kết nối máy chủ để lấy danh sách người dùng!");
//     } finally {
//       setIsLoading(false);
//     }
//   };

//   useEffect(() => {
//     fetchUsers();
//   }, []);

//   const handleToggleStatus = async (id) => {
//     try {
//       // Đã xóa bỏ cục cấu hình Header
//       await axiosClient.patch(`/admin/users/${id}/toggle-status`);
//       toast.success("Cập nhật trạng thái tài khoản thành công!");
//       fetchUsers(); 
//     } catch (error) {
//       toast.error("Lỗi khi thực hiện khóa tài khoản!");
//     }
//   };

//   const handleToggleSuspicious = async (id) => {
//     try {
//       // Đã xóa bỏ cục cấu hình Header
//       await axiosClient.patch(`/admin/users/${id}/toggle-suspicious`);
//       toast.info("Đã cập nhật mức độ tin cậy của phiên đăng nhập");
//       fetchUsers();
//     } catch (error) {
//       toast.error("Lỗi hệ thống khi cập nhật cờ rủi ro!");
//     }
//   };

//   // MỞ MODAL VÀ GỌI API LẤY DỮ LIỆU THẬT
//   const openUserProfile = async (user) => {
//     setSelectedUser(user);
//     setIsProfileModalOpen(true);
//     setIsTxLoading(true);

//     try {
//       const response = await axiosClient.get(`/admin/users/${user.id}/recent-transactions`);
//       setRecentTransactions(response.data);
//     } catch (error) {
//       toast.error("Không thể lấy lịch sử giao dịch của người dùng này!");
//       setRecentTransactions([]);
//     } finally {
//       setIsTxLoading(false);
//     }
//   };

//   const handleResetFaceBiometric = async () => {
//     if (!userToReset) return;
//     try {
//       // Đã xóa bỏ cục cấu hình Header
//       await axiosClient.patch(`/admin/users/${userToReset.id}/reset-face`);
//       toast.success(`Đã hủy vĩnh viễn dữ liệu khuôn mặt của ${userToReset.username}!`);
//       setIsResetModalOpen(false); 
//       fetchUsers(); 
//     } catch (error) {
//       toast.error(error.response?.data || "Lỗi khi hủy dữ liệu sinh trắc học!");
//     }
//   };

//   const filteredUsers = users.filter(user => {
//     const searchLower = searchTerm.toLowerCase();
//     return (
//       (user.fullName && user.fullName.toLowerCase().includes(searchLower)) ||
//       (user.username && user.username.toLowerCase().includes(searchLower)) ||
//       (user.phoneNumber && user.phoneNumber.includes(searchLower)) ||
//       (user.email && user.email.toLowerCase().includes(searchLower))
//     );
//   });

//   return (
//     <div className="min-h-screen bg-slate-50 p-8 pb-16 relative">
//       <div className="max-w-7xl mx-auto">
        
//         {/* HEADER */}
//         <div className="mb-8 flex justify-between items-start">
//           <div className="flex flex-col space-y-5">
//             <div>
//               <h1 className="text-3xl font-black text-slate-900 tracking-tight flex items-center">
//                  Quản lý Người dùng
//               </h1>
//               <p className="text-slate-500 font-medium mt-1">Giám sát hoạt động và quản lý trạng thái bảo mật của khách hàng</p>
//             </div>

//             {/* 🚀 TAB SWITCHER MỚI */}
//             <div className="flex bg-white p-1.5 rounded-2xl shadow-sm border border-slate-200 w-max">
//               <button 
//                 onClick={() => setActiveTab('general')}
//                 className={`px-6 py-2 rounded-xl font-bold transition-all ${activeTab === 'general' ? 'bg-slate-900 text-white shadow-lg' : 'text-slate-500 hover:bg-slate-50'}`}
//               >
//                 Quản trị Chung
//               </button>
//               <button 
//                 onClick={() => setActiveTab('biometric')}
//                 className={`px-6 py-2 rounded-xl font-bold transition-all ${activeTab === 'biometric' ? 'bg-slate-900 text-white shadow-lg' : 'text-slate-500 hover:bg-slate-50'}`}
//               >
//                 Kiểm soát Sinh trắc học
//               </button>
//             </div>
//           </div>
          
//           <Link to="/admin" className="px-5 py-2.5 bg-slate-900 text-white font-bold rounded-xl hover:bg-black transition-colors shadow-lg flex items-center mt-2">
//             <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path></svg>
//             Quay lại Dashboard
//           </Link>
//         </div>

//         {/* THANH TÌM KIẾM */}
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

//         {/* BẢNG DANH SÁCH USER (Render theo Tab) */}
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
                    
//                     {/* CÁC CỘT RẼ NHÁNH THEO TAB */}
//                     {activeTab === 'general' ? (
//                       <>
//                         <th className="px-6 py-4">Liên hệ</th>
//                         <th className="px-6 py-4">Trạng thái</th>
//                         <th className="px-6 py-4">Bảo mật IP</th>
//                         <th className="px-6 py-4 text-right">Hành động</th>
//                       </>
//                     ) : (
//                       <>
//                         <th className="px-6 py-4 text-center">Trạng thái Sinh trắc học</th>
//                         <th className="px-6 py-4 text-right">Kiểm soát Dữ liệu</th>
//                       </>
//                     )}
//                   </tr>
//                 </thead>
//                 <tbody className="divide-y divide-slate-50">
//                   {filteredUsers.map(user => (
//                     <tr key={user.id} className="hover:bg-slate-50/80 transition-colors group">
//                       <td className="px-6 py-5 text-slate-400 font-mono text-xs">#{user.id}</td>
//                       <td className="px-6 py-5">
//                         <p className="font-bold text-slate-800">{user.fullName}</p>
//                         <p className="text-xs text-slate-400">@{user.username}</p>
//                       </td>
                      
//                       {/* DỮ LIỆU RẼ NHÁNH THEO TAB */}
//                       {activeTab === 'general' ? (
//                         <>
//                           <td className="px-6 py-5 text-sm">
//                             <p className="text-slate-600 font-medium">{user.phoneNumber}</p>
//                             <p className="text-xs text-slate-400">{user.email}</p>
//                           </td>
//                           <td className="px-6 py-5">
//                             <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-tighter ${user.status === 'ACTIVE' ? 'bg-green-100 text-green-600' : 'bg-red-100 text-red-600'}`}>
//                               {user.status}
//                             </span>
//                           </td>
//                           <td className="px-6 py-5">
//                             {user.suspiciousSession ? (
//                               <div className="flex items-center text-orange-600 font-bold text-xs bg-orange-50 px-3 py-1 rounded-lg border border-orange-100 w-fit">
//                                 <span className="w-2 h-2 bg-orange-600 rounded-full animate-ping mr-2"></span>
//                                 IP NGHI VẤN
//                               </div>
//                             ) : (
//                               <span className="text-slate-400 text-xs font-medium">An toàn</span>
//                             )}
//                           </td>
//                           <td className="px-6 py-5 text-right space-x-2 flex justify-end items-center">
//                             <button 
//                               onClick={() => openUserProfile(user)}
//                               className="px-3 py-2 bg-cyan-50 text-cyan-600 hover:bg-cyan-100 rounded-lg font-bold text-xs transition-all mr-2 flex items-center"
//                             >
//                               <svg className="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path></svg>
//                               HỒ SƠ
//                             </button>

//                             <button 
//                               onClick={() => handleToggleSuspicious(user.id)}
//                               className={`p-2 rounded-lg transition-all ${user.suspiciousSession ? 'bg-slate-100 text-slate-600' : 'bg-orange-50 text-orange-600 hover:bg-orange-100'}`}
//                               title="Cảnh báo IP"
//                             >
//                               <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path></svg>
//                             </button>
                            
//                             <button 
//                               onClick={() => handleToggleStatus(user.id)}
//                               className={`px-4 py-2 rounded-lg font-bold text-xs transition-all ${user.status === 'ACTIVE' ? 'bg-red-50 text-red-600 hover:bg-red-100' : 'bg-green-50 text-green-600 hover:bg-green-100'}`}
//                             >
//                               {user.status === 'ACTIVE' ? 'KHÓA TK' : 'MỞ KHÓA'}
//                             </button>
//                           </td>
//                         </>
//                       ) : (
//                         // 🚀 TAB BIOMETRIC: BẢNG KIỂM SOÁT KHUÔN MẶT
//                         <>
//                           <td className="px-6 py-5 text-center">
//                             {user.hasFaceData ? (
//                               <span className="px-3 py-1.5 rounded-lg text-[11px] font-black uppercase tracking-tight bg-green-100 text-green-700">
//                                 🟢 Đã đăng ký
//                               </span>
//                             ) : (
//                               <span className="px-3 py-1.5 rounded-lg text-[11px] font-black uppercase tracking-tight bg-slate-100 text-slate-500">
//                                 ⚪ Chưa đăng ký
//                               </span>
//                             )}
//                           </td>
//                           <td className="px-6 py-5 text-right">
//                             <button 
//                               disabled={!user.hasFaceData}
//                               onClick={() => { setUserToReset(user); setIsResetModalOpen(true); }}
//                               className={`px-4 py-2 rounded-lg font-bold text-xs transition-all flex items-center justify-end ml-auto ${
//                                 user.hasFaceData 
//                                   ? 'bg-red-50 text-red-600 hover:bg-red-600 hover:text-white shadow-sm' 
//                                   : 'bg-slate-50 text-slate-300 cursor-not-allowed'
//                               }`}
//                             >
//                               <span className="mr-2">📸</span> HỦY KHUÔN MẶT
//                             </button>
//                           </td>
//                         </>
//                       )}
//                     </tr>
//                   ))}
//                 </tbody>
//               </table>
//             )}
//           </div>
//         </div>
//       </div>

//       {/* ========================================== */}
//       {/*   MODAL HỒ SƠ RỦI RO CHI TIẾT (GIỮ NGUYÊN)  */}
//       {/* ========================================== */}
//       {isProfileModalOpen && selectedUser && (
//         <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
//           <div className="bg-white rounded-3xl w-full max-w-2xl overflow-hidden shadow-2xl animate-in fade-in zoom-in duration-200">
//             {/* Modal Header */}
//             <div className="bg-slate-900 p-6 flex justify-between items-start">
//               <div>
//                 <h2 className="text-xl font-black text-white flex items-center">
//                   Hồ sơ Kiểm sát: {selectedUser.fullName}
//                 </h2>
//                 <p className="text-slate-400 text-sm mt-1">ID Hệ thống: #{selectedUser.id} | Username: {selectedUser.username}</p>
//               </div>
//               <button onClick={() => setIsProfileModalOpen(false)} className="text-slate-400 hover:text-white bg-slate-800 p-2 rounded-full">
//                 <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
//               </button>
//             </div>

//             {/* Modal Body */}
//             <div className="p-6">
//               {selectedUser.suspiciousSession && (
//                 <div className="mb-6 bg-red-50 border border-red-100 p-4 rounded-xl flex items-start">
//                   <span className="text-2xl mr-3">🚨</span>
//                   <div>
//                     <h4 className="text-red-800 font-bold text-sm">Cảnh báo An ninh mức độ cao!</h4>
//                     <p className="text-red-600 text-xs mt-1">Tài khoản này đang bị hệ thống đánh dấu IP đáng ngờ. Mọi giao dịch sẽ bị cộng thêm điểm rủi ro mặc định.</p>
//                   </div>
//                 </div>
//               )}

//               <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4">Hoạt động giao dịch gần đây</h3>
              
//               {isTxLoading ? (
//                 <div className="py-10 text-center text-slate-400 font-medium animate-pulse">Đang trích xuất dữ liệu từ Core Banking...</div>
//               ) : (
//                 <div className="space-y-3">
//                   {recentTransactions.map((tx, index) => (
//                     <div key={index} className="flex items-center justify-between p-4 bg-slate-50 border border-slate-100 rounded-2xl hover:border-blue-200 transition-colors">
//                       <div className="flex items-center space-x-4">
//                         <div className={`w-10 h-10 rounded-full flex items-center justify-center font-black ${tx.status === 'BLOCKED' ? 'bg-red-100 text-red-600' : tx.status === 'SUCCESS' ? 'bg-green-100 text-green-600' : 'bg-orange-100 text-orange-600'}`}>
//                           {tx.status === 'SUCCESS' ? '✓' : tx.status === 'BLOCKED' ? '✕' : '!'}
//                         </div>
//                         <div>
//                           <p className="font-black text-slate-800">{Number(tx.amount).toLocaleString()} VND</p>
//                           <p className="text-xs text-slate-400 font-medium">{tx.date} • Mã GD: {tx.id}</p>
//                         </div>
//                       </div>
//                       <div className="text-right">
//                         <p className={`text-xs font-bold uppercase tracking-wider ${tx.status === 'BLOCKED' ? 'text-red-500' : tx.status === 'SUCCESS' ? 'text-green-500' : 'text-orange-500'}`}>
//                           {tx.status}
//                         </p>
//                         <p className="text-[11px] font-bold text-slate-400 mt-1">
//                           Điểm rủi ro AI: <span className={tx.riskScore > 50 ? 'text-red-500' : 'text-slate-600'}>{tx.riskScore}</span>
//                         </p>
//                       </div>
//                     </div>
//                   ))}
//                   {recentTransactions.length === 0 && (
//                     <p className="text-center text-slate-400 text-sm py-4">Chưa có giao dịch nào được ghi nhận.</p>
//                   )}
//                 </div>
//               )}
//             </div>
            
//             {/* Modal Footer */}
//             <div className="bg-slate-50 p-4 border-t border-slate-100 flex justify-end">
//               <button onClick={() => setIsProfileModalOpen(false)} className="px-5 py-2.5 bg-white border border-slate-200 text-slate-600 font-bold rounded-xl hover:bg-slate-100 transition-colors">
//                 Đóng hồ sơ
//               </button>
//             </div>
//           </div>
//         </div>
//       )}

//       {/* ========================================== */}
//       {/* 🚀 MODAL MỚI: CẢNH BÁO XÓA KHUÔN MẶT */}
//       {/* ========================================== */}
//       {isResetModalOpen && userToReset && (
//         <div className="fixed inset-0 bg-slate-900/70 backdrop-blur-sm flex items-center justify-center z-50 p-4">
//           <div className="bg-white rounded-3xl w-full max-w-md p-8 shadow-2xl transform transition-all text-center">
//             <div className="w-20 h-20 bg-red-50 border-[6px] border-red-100 rounded-full flex items-center justify-center mx-auto mb-6">
//               <span className="text-4xl">📸</span>
//             </div>
//             <h2 className="text-2xl font-black text-slate-800 mb-2">Cảnh báo nguy hiểm!</h2>
//             <p className="text-slate-500 font-medium mb-8 leading-relaxed">
//               Bạn đang chuẩn bị hủy vĩnh viễn mẫu khuôn mặt của tài khoản <strong className="text-slate-800">@{userToReset.username}</strong>. 
//               Hành động này không thể hoàn tác. Khách hàng sẽ phải quét lại khuôn mặt ở lần giao dịch bảo mật tiếp theo.
//             </p>
//             <div className="flex space-x-3">
//               <button 
//                 onClick={() => setIsResetModalOpen(false)}
//                 className="flex-1 py-3.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-black rounded-xl transition-colors"
//               >
//                 HỦY BỎ
//               </button>
//               <button 
//                 onClick={handleResetFaceBiometric}
//                 className="flex-1 py-3.5 bg-red-600 hover:bg-red-700 text-white font-black rounded-xl shadow-lg shadow-red-600/30 transition-colors"
//               >
//                 XÁC NHẬN XÓA
//               </button>
//             </div>
//           </div>
//         </div>
//       )}

//     </div>
//   );
// }




import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import { Link } from 'react-router-dom';

import UserBehaviorProfileModal from '../../components/UserBehaviorProfileModal';  

export default function AdminUserDashboard() {
  const [users, setUsers] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeTab, setActiveTab] = useState('general');

  // Các state Loading
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  // 🚀 STATE PHÂN TRANG
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);

  // State Modal (Giữ nguyên)
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);
  const [recentTransactions, setRecentTransactions] = useState([]);
  const [isTxLoading, setIsTxLoading] = useState(false);

  const [isResetModalOpen, setIsResetModalOpen] = useState(false);
  const [userToReset, setUserToReset] = useState(null);

  // State cho Modal AI Hành vi
  const [isBehaviorModalOpen, setIsBehaviorModalOpen] = useState(false);
  const [behaviorUserId, setBehaviorUserId] = useState(null);

  // 🚀 HÀM LẤY DỮ LIỆU ĐÃ TÍCH HỢP TÌM KIẾM VÀ PHÂN TRANG
  const fetchUsers = async (pageNumber = 0, isNewSearch = false) => {
    try {
      if (isNewSearch) setIsLoading(true);
      else setIsLoadingMore(true);

      // Gọi API truyền cả page, size và từ khóa tìm kiếm
      const response = await axiosClient.get('/admin/users', {
        params: {
          page: pageNumber,
          size: 10,
          search: searchTerm
        }
      });
      
      const newData = response.data.content || response.data || [];

      if (isNewSearch) {
        setUsers(newData); // Reset mảng nếu là tìm kiếm mới
      } else {
        setUsers(prev => [...prev, ...newData]); // Nối mảng nếu là tải thêm
      }

      setHasMore(response.data.last !== undefined ? !response.data.last : false);
      setPage(pageNumber);
    } catch (error) {
      toast.error("Không thể kết nối máy chủ để lấy danh sách người dùng!");
    } finally {
      setIsLoading(false);
      setIsLoadingMore(false);
    }
  };

  // 🚀 Tự động gọi API khi vào trang hoặc khi gõ tìm kiếm (có độ trễ 500ms)
  useEffect(() => {
    const delayDebounceFn = setTimeout(() => {
      fetchUsers(0, true);
    }, 500); // Đợi user gõ xong 0.5s rồi mới gọi API

    return () => clearTimeout(delayDebounceFn);
  }, [searchTerm]); // Theo dõi sự thay đổi của thanh tìm kiếm


  // Các hàm xử lý hành động (Giữ nguyên)
  const handleToggleStatus = async (id) => {
    try {
      await axiosClient.patch(`/admin/users/${id}/toggle-status`);
      toast.success("Cập nhật trạng thái tài khoản thành công!");
      fetchUsers(0, true); // Làm mới lại từ đầu
    } catch (error) {
      toast.error("Lỗi khi thực hiện khóa tài khoản!");
    }
  };

  const handleToggleSuspicious = async (id) => {
    try {
      await axiosClient.patch(`/admin/users/${id}/toggle-suspicious`);
      toast.info("Đã cập nhật mức độ tin cậy của phiên đăng nhập");
      fetchUsers(0, true); 
    } catch (error) {
      toast.error("Lỗi hệ thống khi cập nhật cờ rủi ro!");
    }
  };

  const openUserProfile = async (user) => {
    setSelectedUser(user);
    setIsProfileModalOpen(true);
    setIsTxLoading(true);

    try {
      const response = await axiosClient.get(`/admin/users/${user.id}/recent-transactions`);
      setRecentTransactions(response.data);
    } catch (error) {
      toast.error("Không thể lấy lịch sử giao dịch của người dùng này!");
      setRecentTransactions([]);
    } finally {
      setIsTxLoading(false);
    }
  };

  const openBehaviorProfile = (userId) => {
    setBehaviorUserId(userId);
    setIsBehaviorModalOpen(true);
  };

  const handleResetFaceBiometric = async () => {
    if (!userToReset) return;
    try {
      await axiosClient.patch(`/admin/users/${userToReset.id}/reset-face`);
      toast.success(`Đã hủy vĩnh viễn dữ liệu khuôn mặt của ${userToReset.username}!`);
      setIsResetModalOpen(false); 
      fetchUsers(0, true); 
    } catch (error) {
      toast.error(error.response?.data || "Lỗi khi hủy dữ liệu sinh trắc học!");
    }
  };

  // Nút Tải thêm
  const handleLoadMore = () => {
    if (!isLoadingMore && hasMore) {
      fetchUsers(page + 1, false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 p-8 pb-16 relative">
      <div className="max-w-7xl mx-auto">
        
        {/* HEADER */}
        <div className="mb-8 flex justify-between items-start">
          <div className="flex flex-col space-y-5">
            <div>
              <h1 className="text-3xl font-black text-slate-900 tracking-tight flex items-center">
                 Quản lý Người dùng
              </h1>
              <p className="text-slate-500 font-medium mt-1">Giám sát hoạt động và quản lý trạng thái bảo mật của khách hàng</p>
            </div>

            <div className="flex bg-white p-1.5 rounded-2xl shadow-sm border border-slate-200 w-max">
              <button 
                onClick={() => setActiveTab('general')}
                className={`px-6 py-2 rounded-xl font-bold transition-all ${activeTab === 'general' ? 'bg-slate-900 text-white shadow-lg' : 'text-slate-500 hover:bg-slate-50'}`}
              >
                Quản trị Chung
              </button>
              <button 
                onClick={() => setActiveTab('biometric')}
                className={`px-6 py-2 rounded-xl font-bold transition-all ${activeTab === 'biometric' ? 'bg-slate-900 text-white shadow-lg' : 'text-slate-500 hover:bg-slate-50'}`}
              >
                Kiểm soát Sinh trắc học
              </button>
            </div>
          </div>
          
          <Link to="/admin" className="px-5 py-2.5 bg-slate-900 text-white font-bold rounded-xl hover:bg-black transition-colors shadow-lg flex items-center mt-2">
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
                    
                    {activeTab === 'general' ? (
                      <>
                        <th className="px-6 py-4">Liên hệ</th>
                        <th className="px-6 py-4">Trạng thái</th>
                        <th className="px-6 py-4">Bảo mật IP</th>
                        <th className="px-6 py-4 text-right">Hành động</th>
                      </>
                    ) : (
                      <>
                        <th className="px-6 py-4 text-center">Trạng thái Sinh trắc học</th>
                        <th className="px-6 py-4 text-right">Kiểm soát Dữ liệu</th>
                      </>
                    )}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {users.length === 0 ? (
                    <tr><td colSpan="6" className="text-center py-10 text-slate-400 italic">Không tìm thấy người dùng nào.</td></tr>
                  ) : users.map(user => (
                    <tr key={`user_${user.id}`} className="hover:bg-slate-50/80 transition-colors group">
                      <td className="px-6 py-5 text-slate-400 font-mono text-xs">#{user.id}</td>
                      <td className="px-6 py-5">
                        <p className="font-bold text-slate-800">{user.fullName}</p>
                        <p className="text-xs text-slate-400">@{user.username}</p>
                      </td>
                      
                      {activeTab === 'general' ? (
                        <>
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

                            <button 
                            onClick={() => openBehaviorProfile(user.id)}
                            className="px-3 py-2 bg-indigo-50 text-indigo-600 hover:bg-indigo-100 rounded-lg font-bold text-xs transition-all mr-2 flex items-center"
                            title="Phân tích hành vi bằng AI"
                          >
                            <svg className="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"></path></svg>
                            AI HÀNH VI
                          </button>
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
                        </>
                      ) : (
                        <>
                          <td className="px-6 py-5 text-center">
                            {user.hasFaceData ? (
                              <span className="px-3 py-1.5 rounded-lg text-[11px] font-black uppercase tracking-tight bg-green-100 text-green-700">
                                🟢 Đã đăng ký
                              </span>
                            ) : (
                              <span className="px-3 py-1.5 rounded-lg text-[11px] font-black uppercase tracking-tight bg-slate-100 text-slate-500">
                                ⚪ Chưa đăng ký
                              </span>
                            )}
                          </td>
                          <td className="px-6 py-5 text-right">
                            <button 
                              disabled={!user.hasFaceData}
                              onClick={() => { setUserToReset(user); setIsResetModalOpen(true); }}
                              className={`px-4 py-2 rounded-lg font-bold text-xs transition-all flex items-center justify-end ml-auto ${
                                user.hasFaceData 
                                  ? 'bg-red-50 text-red-600 hover:bg-red-600 hover:text-white shadow-sm' 
                                  : 'bg-slate-50 text-slate-300 cursor-not-allowed'
                              }`}
                            >
                              <span className="mr-2">📸</span> HỦY KHUÔN MẶT
                            </button>
                          </td>
                        </>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
          
          {/* 🚀 NÚT TẢI THÊM NẰM Ở CUỐI BẢNG */}
          {!isLoading && users.length > 0 && (
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
                  {isLoadingMore ? 'Đang tải thêm...' : '⬇️ Tải thêm người dùng'}
                </button>
              ) : (
                <span className="text-sm font-medium text-slate-400 italic">
                  Đã tải toàn bộ danh sách.
                </span>
              )}
            </div>
          )}
        </div>
      </div>

      {/* GIỮ NGUYÊN CÁC MODAL CỦA BRO Ở DƯỚI NÀY (Hồ sơ rủi ro và Cảnh báo xóa) */}
      {isProfileModalOpen && selectedUser && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          {/* ... Modal Profile Content ... */}
        </div>
      )}

      {isResetModalOpen && userToReset && (
        <div className="fixed inset-0 bg-slate-900/70 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          {/* ... Modal Cảnh báo Content ... */}
        </div>
      )}

      <UserBehaviorProfileModal 
        userId={behaviorUserId} 
        isOpen={isBehaviorModalOpen} 
        onClose={() => setIsBehaviorModalOpen(false)} 
      />

       {/* ========================================== */}
      {/*   MODAL HỒ SƠ RỦI RO CHI TIẾT (GIỮ NGUYÊN)  */}
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

      {/* ========================================== */}
      {/* 🚀 MODAL MỚI: CẢNH BÁO XÓA KHUÔN MẶT */}
      {/* ========================================== */}
      {isResetModalOpen && userToReset && (
        <div className="fixed inset-0 bg-slate-900/70 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-3xl w-full max-w-md p-8 shadow-2xl transform transition-all text-center">
            <div className="w-20 h-20 bg-red-50 border-[6px] border-red-100 rounded-full flex items-center justify-center mx-auto mb-6">
              <span className="text-4xl">📸</span>
            </div>
            <h2 className="text-2xl font-black text-slate-800 mb-2">Cảnh báo nguy hiểm!</h2>
            <p className="text-slate-500 font-medium mb-8 leading-relaxed">
              Bạn đang chuẩn bị hủy vĩnh viễn mẫu khuôn mặt của tài khoản <strong className="text-slate-800">@{userToReset.username}</strong>. 
              Hành động này không thể hoàn tác. Khách hàng sẽ phải quét lại khuôn mặt ở lần giao dịch bảo mật tiếp theo.
            </p>
            <div className="flex space-x-3">
              <button 
                onClick={() => setIsResetModalOpen(false)}
                className="flex-1 py-3.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-black rounded-xl transition-colors"
              >
                HỦY BỎ
              </button>
              <button 
                onClick={handleResetFaceBiometric}
                className="flex-1 py-3.5 bg-red-600 hover:bg-red-700 text-white font-black rounded-xl shadow-lg shadow-red-600/30 transition-colors"
              >
                XÁC NHẬN XÓA
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}