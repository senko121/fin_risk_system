import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import { Link } from 'react-router-dom';

export default function AdminLogDashboard() {
  const [logs, setLogs] = useState([]);
  const [activeTab, setActiveTab] = useState('audit'); // 'audit' hoặc 'config'
  const [loading, setLoading] = useState(true);

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

  // Hàm render màu sắc cho Action
  const getActionColor = (action) => {
    if (action.includes('REJECT') || action.includes('FAILED')) return 'bg-red-100 text-red-600';
    if (action.includes('SUCCESS') || action.includes('PASS')) return 'bg-emerald-100 text-emerald-600';
    if (action.includes('INITIATED')) return 'bg-blue-100 text-blue-600';
    return 'bg-slate-100 text-slate-600';
  };

  return (
    <div className="min-h-screen bg-slate-50 p-8">
      <div className="max-w-6xl mx-auto">
        
        {/* 🔥 HEADER ĐÃ ĐƯỢC QUY HOẠCH LẠI THEO CHUẨN MỚI */}
        <div className="flex justify-between items-start mb-8">
          
          {/* CỘT TRÁI: TIÊU ĐỀ VÀ TAB SWITCHER */}
          <div className="flex flex-col space-y-5">
            {/* TIÊU ĐỀ */}
            <div>
              <h1 className="text-3xl font-black text-slate-900 tracking-tight uppercase leading-none">Nhật ký hệ thống</h1>
              <p className="text-slate-500 font-medium mt-2 text-sm">FinRisk Audit Trail & Configuration Logs</p>
            </div>
            
            {/* TAB SWITCHER (Đã dời xuống dưới tiêu đề) */}
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

          {/* CỘT PHẢI: NÚT QUAY LẠI (Đã dời sang góc phải) */}
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
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {loading ? (
                   <tr><td colSpan="4" className="text-center py-20 font-bold text-slate-400 animate-pulse uppercase tracking-widest">Đang quét dữ liệu hộp đen...</td></tr>
                ) : logs.map((log) => (
                  <tr key={log.id} className="hover:bg-blue-50/30 transition-colors">
                    <td className="px-6 py-5 text-xs font-mono text-slate-500 text-center border-r border-slate-50">
                      {new Date(log.timestamp || log.createdAt).toLocaleString()}
                    </td>
                    <td className="px-6 py-5">
                      <div className="flex items-center space-x-3">
                        <div className="w-8 h-8 bg-slate-100 rounded-lg flex items-center justify-center font-bold text-slate-600 text-xs">
                          {(log.username || log.adminUsername)?.charAt(0).toUpperCase()}
                        </div>
                        <span className="font-bold text-slate-700">{log.username || log.adminUsername}</span>
                      </div>
                    </td>
                    <td className="px-6 py-5 text-center">
                      <span className={`px-3 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-tight ${getActionColor(log.action || log.actionType)}`}>
                        {log.action || log.actionType}
                      </span>
                    </td>
                    <td className="px-6 py-5">
                      <p className="text-sm text-slate-600 font-medium" title={log.details}>
                        {log.details || `Thay đổi cấu hình bảng ${log.targetTable} (ID: ${log.targetId})`}
                      </p>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}