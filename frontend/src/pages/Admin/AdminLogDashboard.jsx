// import React, { useState, useEffect } from 'react';
// import axiosClient from '../../api/axiosClient';
// import { toast } from 'react-toastify';
// import { Link } from 'react-router-dom';

// export default function AdminLogDashboard() {
//   const [logs, setLogs] = useState([]);
//   const [activeTab, setActiveTab] = useState('audit'); // 'audit' hoặc 'config'
//   const [loading, setLoading] = useState(true);

//   useEffect(() => {
//     fetchLogs();
//   }, [activeTab]);

//   const fetchLogs = async () => {
//     setLoading(true);
//     try {
//       const endpoint = activeTab === 'audit' ? '/admin/logs/audit' : '/admin/logs/config';
//       const res = await axiosClient.get(endpoint);
//       setLogs(res.data);
//     } catch (error) {
//       toast.error("Không thể tải nhật ký hệ thống");
//     } finally {
//       setLoading(false);
//     }
//   };

//   // Hàm render màu sắc cho Action
//   const getActionColor = (action) => {
//     if (action.includes('REJECT') || action.includes('FAILED')) return 'bg-red-100 text-red-600';
//     if (action.includes('SUCCESS') || action.includes('PASS')) return 'bg-emerald-100 text-emerald-600';
//     if (action.includes('INITIATED')) return 'bg-blue-100 text-blue-600';
//     return 'bg-slate-100 text-slate-600';
//   };

//   return (
//     <div className="min-h-screen bg-slate-50 p-8">
//       <div className="max-w-6xl mx-auto">
        
//         {/* 🔥 HEADER ĐÃ ĐƯỢC QUY HOẠCH LẠI THEO CHUẨN MỚI */}
//         <div className="flex justify-between items-start mb-8">
          
//           {/* CỘT TRÁI: TIÊU ĐỀ VÀ TAB SWITCHER */}
//           <div className="flex flex-col space-y-5">
//             {/* TIÊU ĐỀ */}
//             <div>
//               <h1 className="text-3xl font-black text-slate-900 tracking-tight uppercase leading-none">Nhật ký hệ thống</h1>
//               <p className="text-slate-500 font-medium mt-2 text-sm">FinRisk Audit Trail & Configuration Logs</p>
//             </div>
            
//             {/* TAB SWITCHER (Đã dời xuống dưới tiêu đề) */}
//             <div className="flex bg-white p-1.5 rounded-2xl shadow-sm border border-slate-200 w-max">
//               <button 
//                 onClick={() => setActiveTab('audit')}
//                 className={`px-6 py-2 rounded-xl font-bold transition-all ${activeTab === 'audit' ? 'bg-slate-900 text-white shadow-lg' : 'text-slate-500 hover:bg-slate-50'}`}
//               >
//                 Audit Logs
//               </button>
//               <button 
//                 onClick={() => setActiveTab('config')}
//                 className={`px-6 py-2 rounded-xl font-bold transition-all ${activeTab === 'config' ? 'bg-slate-900 text-white shadow-lg' : 'text-slate-500 hover:bg-slate-50'}`}
//               >
//                 Config Logs
//               </button>
//             </div>
//           </div>

//           {/* CỘT PHẢI: NÚT QUAY LẠI (Đã dời sang góc phải) */}
//           <Link to="/admin" className="px-5 py-2.5 bg-slate-900 text-white font-bold rounded-xl hover:bg-black transition-colors shadow-lg flex items-center shrink-0">
//             <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
//               <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path>
//             </svg>
//             Quay lại Dashboard
//           </Link>
          
//         </div>

//         {/* BẢNG DỮ LIỆU */}
//         <div className="bg-white rounded-[2rem] shadow-xl border border-slate-100 overflow-hidden">
//           <div className="overflow-x-auto">
//             <table className="w-full text-left border-collapse">
//               <thead>
//                 <tr className="bg-slate-50 border-b border-slate-100">
//                   <th className="px-6 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest text-center">Thời gian</th>
//                   <th className="px-6 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest">Người thực hiện</th>
//                   <th className="px-6 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest text-center">Hành động</th>
//                   <th className="px-6 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest">Chi tiết nội dung</th>
//                 </tr>
//               </thead>
//               <tbody className="divide-y divide-slate-50">
//                 {loading ? (
//                    <tr><td colSpan="4" className="text-center py-20 font-bold text-slate-400 animate-pulse uppercase tracking-widest">Đang quét dữ liệu hộp đen...</td></tr>
//                 ) : logs.map((log) => (
//                   <tr key={log.id} className="hover:bg-blue-50/30 transition-colors">
//                     <td className="px-6 py-5 text-xs font-mono text-slate-500 text-center border-r border-slate-50">
//                       {new Date(log.timestamp || log.createdAt).toLocaleString()}
//                     </td>
//                     <td className="px-6 py-5">
//                       <div className="flex items-center space-x-3">
//                         <div className="w-8 h-8 bg-slate-100 rounded-lg flex items-center justify-center font-bold text-slate-600 text-xs">
//                           {(log.username || log.adminUsername)?.charAt(0).toUpperCase()}
//                         </div>
//                         <span className="font-bold text-slate-700">{log.username || log.adminUsername}</span>
//                       </div>
//                     </td>
//                     <td className="px-6 py-5 text-center">
//                       <span className={`px-3 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-tight ${getActionColor(log.action || log.actionType)}`}>
//                         {log.action || log.actionType}
//                       </span>
//                     </td>
//                     <td className="px-6 py-5">
//                       <p className="text-sm text-slate-600 font-medium" title={log.details}>
//                         {log.details || `Thay đổi cấu hình bảng ${log.targetTable} (ID: ${log.targetId})`}
//                       </p>
//                     </td>
//                   </tr>
//                 ))}
//               </tbody>
//             </table>
//           </div>
//         </div>
//       </div>
//     </div>
//   );
// }

import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import { Link } from 'react-router-dom';

export default function AdminLogDashboard() {
  const [logs, setLogs] = useState([]);
  const [activeTab, setActiveTab] = useState('audit'); // 'audit' hoặc 'config'
  const [loading, setLoading] = useState(true);

  // 🚀 STATE MỚI: QUẢN LÝ MODAL SOI CHI TIẾT
  const [selectedLog, setSelectedLog] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  useEffect(() => {
    fetchLogs();
  }, [activeTab]);

  const fetchLogs = async () => {
    setLoading(true);
    try {
      const endpoint = activeTab === 'audit' ? '/admin/logs/audit' : '/admin/logs/config';
      const res = await axiosClient.get(endpoint);
      setLogs(res.data);
    } catch (error) {
      toast.error("Không thể tải nhật ký hệ thống");
    } finally {
      setLoading(false);
    }
  };

  // 🔥 ĐÃ NÂNG CẤP: Render màu sắc cho cả Admin Action
  const getActionColor = (action) => {
    if (!action) return 'bg-slate-100 text-slate-600';
    
    // Nhóm màu cho Admin Config (Hộp đen)
    if (action.includes('CREATE')) return 'bg-green-100 text-green-700'; // Xanh lá
    if (action.includes('UPDATE')) return 'bg-orange-100 text-orange-700'; // Cam cảnh báo
    if (action.includes('TOGGLE')) return 'bg-purple-100 text-purple-700'; // Tím thay đổi trạng thái
    
    // Nhóm màu cho User Audit
    if (action.includes('REJECT') || action.includes('FAILED')) return 'bg-red-100 text-red-600';
    if (action.includes('SUCCESS') || action.includes('PASS')) return 'bg-emerald-100 text-emerald-600';
    if (action.includes('INITIATED')) return 'bg-blue-100 text-blue-600';
    
    return 'bg-slate-100 text-slate-600';
  };

  // 🚀 HÀM MỚI: Mở modal và nạp dữ liệu
  const openDetailModal = (log) => {
    setSelectedLog(log);
    setIsModalOpen(true);
  };

  // 🚀 HÀM MỚI: Định dạng JSON cho đẹp, dễ nhìn
  const formatJSON = (jsonStr) => {
    if (!jsonStr || jsonStr === 'null' || jsonStr === '{}') return 'Không có dữ liệu / Dữ liệu trống';
    try {
      // Parse ra Object rồi stringify lại với khoảng trắng (2 spaces) để căn lề
      return JSON.stringify(JSON.parse(jsonStr), null, 2);
    } catch (e) {
      return jsonStr; // Nếu không phải chuẩn JSON thì in ra text thường
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 p-8">
      <div className="max-w-6xl mx-auto">
        
        {/* HEADER */}
        <div className="flex justify-between items-start mb-8">
          <div className="flex flex-col space-y-5">
            <div>
              <h1 className="text-3xl font-black text-slate-900 tracking-tight uppercase leading-none">Nhật ký hệ thống</h1>
              <p className="text-slate-500 font-medium mt-2 text-sm">FinRisk Audit Trail & Configuration Logs</p>
            </div>
            
            <div className="flex bg-white p-1.5 rounded-2xl shadow-sm border border-slate-200 w-max">
              <button 
                onClick={() => setActiveTab('audit')}
                className={`px-6 py-2 rounded-xl font-bold transition-all ${activeTab === 'audit' ? 'bg-slate-900 text-white shadow-lg' : 'text-slate-500 hover:bg-slate-50'}`}
              >
                Audit Logs
              </button>
              <button 
                onClick={() => setActiveTab('config')}
                className={`px-6 py-2 rounded-xl font-bold transition-all ${activeTab === 'config' ? 'bg-slate-900 text-white shadow-lg' : 'text-slate-500 hover:bg-slate-50'}`}
              >
                Config Logs
              </button>
            </div>
          </div>

          <Link to="/admin" className="px-5 py-2.5 bg-slate-900 text-white font-bold rounded-xl hover:bg-black transition-colors shadow-lg flex items-center shrink-0">
            <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path>
            </svg>
            Quay lại Dashboard
          </Link>
        </div>

        {/* BẢNG DỮ LIỆU */}
        <div className="bg-white rounded-[2rem] shadow-xl border border-slate-100 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-100">
                  <th className="px-6 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest text-center">Thời gian</th>
                  <th className="px-6 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest">Người thực hiện</th>
                  <th className="px-6 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest text-center">Hành động</th>
                  <th className="px-6 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest">Chi tiết nội dung</th>
                  {/* Cột Action thêm (Chỉ hiện ở Config Logs) */}
                  {activeTab === 'config' && <th className="px-6 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest text-right">Tra cứu</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {loading ? (
                   <tr><td colSpan={activeTab === 'config' ? "5" : "4"} className="text-center py-20 font-bold text-slate-400 animate-pulse uppercase tracking-widest">Đang quét dữ liệu hộp đen...</td></tr>
                ) : logs.map((log) => (
                  <tr key={log.id} className="hover:bg-blue-50/30 transition-colors">
                    <td className="px-6 py-5 text-xs font-mono text-slate-500 text-center border-r border-slate-50 w-48">
                      {new Date(log.timestamp || log.createdAt).toLocaleString()}
                    </td>
                    <td className="px-6 py-5 w-56">
                      <div className="flex items-center space-x-3">
                        <div className="w-8 h-8 bg-slate-100 rounded-lg flex items-center justify-center font-bold text-slate-600 text-xs shrink-0">
                          {(log.username || log.adminUsername)?.charAt(0).toUpperCase()}
                        </div>
                        <span className="font-bold text-slate-700 truncate">{log.username || log.adminUsername}</span>
                      </div>
                    </td>
                    <td className="px-6 py-5 text-center w-48">
                      <span className={`px-3 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-tight ${getActionColor(log.action || log.actionType)}`}>
                        {log.action || log.actionType}
                      </span>
                    </td>
                    <td className="px-6 py-5">
                      <p className="text-sm text-slate-600 font-medium truncate max-w-md" title={log.details}>
                        {log.details || `Thay đổi cấu hình bảng [${log.targetTable}] (ID: ${log.targetId})`}
                      </p>
                    </td>
                    
                    {/* NÚT SOI CHI TIẾT (Chỉ xuất hiện bên tab Config) */}
                    {activeTab === 'config' && (
                      <td className="px-6 py-5 text-right w-32">
                        <button 
                          onClick={() => openDetailModal(log)}
                          className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold text-xs rounded-lg transition-colors"
                        >
                          🔍 Soi
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
                {logs.length === 0 && !loading && (
                   <tr><td colSpan={activeTab === 'config' ? "5" : "4"} className="text-center py-10 font-medium text-slate-400">Không có dữ liệu.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* ======================================================== */}
      {/* 🚀 MODAL XEM CHI TIẾT SỰ THAY ĐỔI (JSON DIFF VIEWER) */}
      {/* ======================================================== */}
      {isModalOpen && selectedLog && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-3xl w-full max-w-4xl p-8 shadow-2xl transform transition-all flex flex-col max-h-[90vh]">
            
            {/* Header Modal */}
            <div className="flex justify-between items-center mb-6">
              <div>
                <h2 className="text-2xl font-black text-slate-800 flex items-center">
                  <span className="text-blue-500 mr-2">🛡️</span> Hồ Sơ Lưu Vết (Audit Trail)
                </h2>
                <p className="text-slate-500 text-sm mt-1 font-medium">
                  ID Bản ghi: #{selectedLog.targetId} | Bảng: {selectedLog.targetTable} | Thời gian: {new Date(selectedLog.createdAt).toLocaleString()}
                </p>
              </div>
              <button onClick={() => setIsModalOpen(false)} className="p-2 bg-slate-100 hover:bg-slate-200 rounded-full text-slate-500 transition-colors">
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
              </button>
            </div>

            {/* Content: 2 Cột Đối Chiếu */}
            <div className="flex-1 overflow-hidden flex space-x-4">
              
              {/* Cột Trái: Dữ liệu cũ */}
              <div className="flex-1 flex flex-col bg-red-50/50 border border-red-100 rounded-2xl overflow-hidden">
                <div className="bg-red-100/50 px-4 py-3 border-b border-red-100 flex items-center justify-between">
                  <span className="font-bold text-red-800 text-sm">Dữ Liệu Trước Khi Sửa (Old)</span>
                  <span className="text-red-500 text-xs font-black px-2 py-1 bg-red-100 rounded uppercase">- Removed</span>
                </div>
                <div className="p-4 flex-1 overflow-auto bg-white/50">
                  <pre className="text-[13px] font-mono text-slate-700 whitespace-pre-wrap">
                    {formatJSON(selectedLog.oldValue)}
                  </pre>
                </div>
              </div>

              {/* Cột Phải: Dữ liệu mới */}
              <div className="flex-1 flex flex-col bg-emerald-50/50 border border-emerald-100 rounded-2xl overflow-hidden">
                <div className="bg-emerald-100/50 px-4 py-3 border-b border-emerald-100 flex items-center justify-between">
                  <span className="font-bold text-emerald-800 text-sm">Dữ Liệu Mới (New)</span>
                  <span className="text-emerald-600 text-xs font-black px-2 py-1 bg-emerald-100 rounded uppercase">+ Added</span>
                </div>
                <div className="p-4 flex-1 overflow-auto bg-white/50">
                  <pre className="text-[13px] font-mono text-slate-700 whitespace-pre-wrap">
                    {formatJSON(selectedLog.newValue)}
                  </pre>
                </div>
              </div>

            </div>
            
            <div className="mt-6 flex justify-end">
              <button 
                onClick={() => setIsModalOpen(false)}
                className="px-6 py-2.5 bg-slate-900 hover:bg-black text-white font-bold rounded-xl transition-colors"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}