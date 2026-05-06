import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import { Link } from 'react-router-dom';

export default function AdminPolicyDashboard() {
  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchPolicies();
  }, []);

  const fetchPolicies = async () => {
    setLoading(true);
    try {
      const res = await axiosClient.get('/admin/policies');
      // Sắp xếp ID để LOW lên đầu, HIGH xuống cuối cho đúng logic
      const sorted = res.data.sort((a, b) => a.id - b.id);
      setPolicies(sorted);
    } catch (error) {
      toast.error("Không thể tải danh sách Ngưỡng rủi ro!");
    } finally {
      setLoading(false);
    }
  };

  // Quản lý việc sửa giá trị Min/Max trên giao diện (chưa gọi API)
  const handleChange = (id, field, value) => {
    setPolicies(policies.map(p => p.id === id ? { ...p, [field]: parseInt(value) || 0 } : p));
  };

  // Bấm nút Lưu -> Gọi API
  const handleSave = async (policy) => {
    if (policy.minScore >= policy.maxScore) {
      toast.error(`[${policy.riskLevel}] Điểm tối thiểu (Min) phải nhỏ hơn điểm tối đa (Max)!`);
      return;
    }

    try {
      // Nhớ đổi 'admin_root' thành username lấy từ LocalStorage sau khi bro làm xong phần Login nhé
      await axiosClient.put(`/admin/policies/${policy.id}`, 
        { minScore: policy.minScore, maxScore: policy.maxScore },
        { headers: { 'X-Admin-Username': 'admin_root' } }
      );
      toast.success(`Cập nhật ngưỡng ${policy.riskLevel} thành công!`);
      fetchPolicies();
    } catch (error) {
      toast.error("Lỗi khi cập nhật cấu hình!");
    }
  };

  // Bộ lọc màu sắc cực xịn cho các thẻ Card
  const getCardStyle = (level) => {
    if (level === 'LOW') return { bg: 'bg-green-50', border: 'border-green-500', text: 'text-green-700', icon: '🟢', shadow: 'shadow-green-500/20' };
    if (level === 'MEDIUM_1') return { bg: 'bg-yellow-50', border: 'border-yellow-400', text: 'text-yellow-700', icon: '🟡', shadow: 'shadow-yellow-400/20' };
    if (level === 'MEDIUM_2') return { bg: 'bg-orange-50', border: 'border-orange-500', text: 'text-orange-700', icon: '🟠', shadow: 'shadow-orange-500/20' };
    if (level === 'HIGH') return { bg: 'bg-red-50', border: 'border-red-500', text: 'text-red-700', icon: '🔴', shadow: 'shadow-red-500/20' };
    return { bg: 'bg-slate-50', border: 'border-slate-500', text: 'text-slate-700', icon: '⚙️', shadow: 'shadow-slate-500/20' };
  };

  return (
    <div className="min-h-screen bg-slate-50 p-8 pb-16">
      <div className="max-w-5xl mx-auto">
        
        {/* HEADER */}
        <div className="mb-10 flex justify-between items-start">
          <div>
            <h1 className="text-3xl font-black text-slate-900 tracking-tight uppercase">Kiểm soát Ngưỡng Rủi Ro</h1>
            <p className="text-slate-500 font-medium mt-2">Điều chỉnh các mốc điểm kích hoạt phương thức xác thực bảo mật (Risk Policies)</p>
          </div>
          <Link to="/admin" className="px-5 py-2.5 bg-slate-900 text-white font-bold rounded-xl hover:bg-black transition-colors shadow-lg flex items-center shrink-0">
            <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path></svg>
            Quay lại Dashboard
          </Link>
        </div>

        {loading ? (
          <div className="text-center py-20 font-bold text-slate-400 animate-pulse text-lg">Đang kết nối tới Trạm Kiểm Soát...</div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {policies.map((policy) => {
              const style = getCardStyle(policy.riskLevel);
              
              return (
                <div key={policy.id} className={`rounded-3xl border-l-8 ${style.border} bg-white shadow-xl ${style.shadow} p-6 relative overflow-hidden transition-all hover:-translate-y-1`}>
                  
                  {/* Băng rôn báo hiệu ở góc phải */}
                  <div className={`absolute top-0 right-0 ${style.bg} px-4 py-2 rounded-bl-2xl font-black text-sm tracking-widest ${style.text}`}>
                    {policy.riskLevel}
                  </div>

                  <div className="flex items-center mb-4 mt-2">
                    <span className="text-2xl mr-3">{style.icon}</span>
                    <h2 className="text-xl font-black text-slate-800">{policy.description || 'Chính sách bảo mật'}</h2>
                  </div>

                  <div className="mb-6 p-3 bg-slate-50 rounded-xl border border-slate-100">
                    <p className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-1">Hành động kích hoạt (System Core)</p>
                    <code className="text-sm font-mono text-blue-600 font-bold">{policy.actionBeanName}</code>
                  </div>

                  <div className="flex space-x-4 mb-6">
                    <div className="flex-1">
                      <label className="block text-[11px] font-black text-slate-400 uppercase tracking-wider mb-2">Điểm Tối Thiểu (Min)</label>
                      <input 
                        type="number" 
                        value={policy.minScore}
                        onChange={(e) => handleChange(policy.id, 'minScore', e.target.value)}
                        className="w-full bg-slate-50 border border-slate-200 p-3 rounded-xl font-black text-lg text-slate-700 focus:ring-2 focus:ring-blue-500 outline-none transition-all text-center"
                      />
                    </div>
                    <div className="flex items-center pt-6 text-slate-300 font-black text-xl">-</div>
                    <div className="flex-1">
                      <label className="block text-[11px] font-black text-slate-400 uppercase tracking-wider mb-2">Điểm Tối Đa (Max)</label>
                      <input 
                        type="number" 
                        value={policy.maxScore}
                        onChange={(e) => handleChange(policy.id, 'maxScore', e.target.value)}
                        className="w-full bg-slate-50 border border-slate-200 p-3 rounded-xl font-black text-lg text-slate-700 focus:ring-2 focus:ring-blue-500 outline-none transition-all text-center"
                      />
                    </div>
                  </div>

                  <button 
                    onClick={() => handleSave(policy)}
                    className={`w-full py-3.5 font-black text-white rounded-xl transition-colors shadow-lg ${
                      policy.riskLevel === 'HIGH' ? 'bg-red-600 hover:bg-red-700 shadow-red-600/30' : 
                      'bg-slate-900 hover:bg-black shadow-slate-900/30'
                    }`}
                  >
                    LƯU THAY ĐỔI
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}