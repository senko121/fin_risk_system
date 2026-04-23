import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient'; //   DÙNG AXIOS CHUẨN CỦA DỰ ÁN
import { toast } from 'react-toastify';
import { Link } from 'react-router-dom';

export default function AdminRuleDashboard() {
  const [rules, setRules] = useState([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  // State quản lý Form sửa luật
  const [formData, setFormData] = useState({
    ruleName: '',
    actionScore: 0,
    field: 'amount',
    operator: '>=',
    value: ''
  });

  // 1. Gọi API lấy danh sách luật (Dùng axiosClient)
  const fetchRules = async () => {
    try {
      setIsLoading(true);
      const response = await axiosClient.get('/admin/rules');
      setRules(response.data);
    } catch (error) {
      console.error('Lỗi lấy danh sách luật:', error);
      toast.error("Không thể tải danh sách luật từ Server!");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchRules();
  }, []);

  // 2. Xử lý Bật/Tắt luật
  const handleToggle = async (id) => {
    try {
      await axiosClient.patch(`/admin/rules/${id}/toggle`, null, {
        headers: { 'X-Admin-Username': 'admin_thach' } 
      });
      toast.success("Đã thay đổi trạng thái luật!");
      fetchRules(); // Load lại bảng
    } catch (error) {
      toast.error('Lỗi khi bật/tắt luật!');
    }
  };

  // 3. Mở Modal và băm JSON ra Form
  const openEditModal = (rule) => {
    let parsedCondition = {};
    try {
      parsedCondition = JSON.parse(rule.conditions);
    } catch (e) {
      console.error("Lỗi parse JSON");
    }

    setFormData({
      ruleName: rule.ruleName,
      actionScore: rule.actionScore,
      field: parsedCondition.field || 'amount',
      operator: parsedCondition.operator || '>=',
      value: parsedCondition.value || ''
    });
    setEditingId(rule.id);
    setIsModalOpen(true);
  };

  // 4. Lưu luật
  const handleSaveRule = async () => {
    const newConditionsJson = JSON.stringify({
      field: formData.field,
      operator: formData.operator,
      value: formData.value
    });

    const payload = {
      ruleName: formData.ruleName,
      actionScore: parseInt(formData.actionScore),
      conditions: newConditionsJson
    };

    try {
      await axiosClient.put(`/admin/rules/${editingId}`, payload, {
        headers: { 
          'X-Admin-Username': 'admin_thach' 
        }
      });
      setIsModalOpen(false);
      toast.success("Cập nhật luật thành công!");
      fetchRules(); 
    } catch (error) {
      toast.error('Lỗi lưu cấu hình luật!');
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 p-8 pb-16">
      <div className="max-w-6xl mx-auto">
        
        {/*   HEADER MỚI CÓ NÚT QUAY LẠI */}
        <div className="mb-8 flex justify-between items-center">
          <div>
            <h1 className="text-3xl font-black text-slate-900 tracking-tight">FinRisk Rule Engine</h1>
            <p className="text-slate-500 font-medium mt-1">Quản lý thuật toán và trọng số đánh giá rủi ro (AI Core)</p>
          </div>
          <Link to="/admin" className="px-5 py-2.5 bg-slate-900 text-white font-bold rounded-xl hover:bg-black transition-colors shadow-lg flex items-center">
            <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path></svg>
            Quay lại Dashboard
          </Link>
        </div>

        {/* Bảng Danh sách Luật */}
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
          <div className="overflow-x-auto p-6">
            
            {isLoading ? (
              <div className="text-center py-10 font-bold text-slate-500 animate-pulse">Đang nạp dữ liệu Rule Engine...</div>
            ) : (
              <table className="w-full text-left">
                <thead>
                  <tr className="border-b border-gray-200 text-sm text-gray-500">
                    <th className="pb-3 font-semibold">Tên Luật</th>
                    <th className="pb-3 font-semibold">Điều kiện kích hoạt (If...)</th>
                    <th className="pb-3 font-semibold">Điểm Phạt (+/100)</th>
                    <th className="pb-3 font-semibold">Trạng thái</th>
                    <th className="pb-3 font-semibold text-right">Hành động</th>
                  </tr>
                </thead>
                <tbody className="text-sm">
                  {rules.map(rule => (
                    <tr key={rule.id} className="border-b border-gray-50 hover:bg-gray-50 transition-colors">
                      <td className="py-5 font-bold text-slate-800">{rule.ruleName}</td>
                      <td className="py-5 font-mono text-xs text-blue-600">
                        <span className="bg-blue-50 px-3 py-1.5 rounded-lg border border-blue-100">
                          {rule.conditions}
                        </span>
                      </td>
                      <td className="py-5 font-black text-red-500 text-base">+{rule.actionScore}đ</td>
                      <td className="py-5">
                        <span className={`px-3 py-1.5 rounded-full text-[11px] uppercase tracking-wider font-bold ${rule.isActive ? 'bg-green-100 text-green-700' : 'bg-slate-200 text-slate-500'}`}>
                          {rule.isActive ? 'ĐANG BẬT' : 'ĐÃ TẮT'}
                        </span>
                      </td>
                      <td className="py-5 text-right space-x-3">
                        <button 
                          onClick={() => openEditModal(rule)}
                          className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded-lg transition-colors"
                        >
                          SỬA
                        </button>
                        <button 
                          onClick={() => handleToggle(rule.id)}
                          className={`px-4 py-2 font-bold rounded-lg transition-colors ${rule.isActive ? 'bg-red-50 hover:bg-red-100 text-red-600' : 'bg-green-50 hover:bg-green-100 text-green-600'}`}
                        >
                          {rule.isActive ? 'TẮT' : 'BẬT'}
                        </button>
                      </td>
                    </tr>
                  ))}
                  {rules.length === 0 && (
                    <tr>
                      <td colSpan="5" className="py-8 text-center text-slate-400 font-medium">Chưa có dữ liệu luật nào.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            )}

          </div>
        </div>
      </div>

      {/* ======================================================== */}
      {/* MODAL SỬA LUẬT */}
      {/* ======================================================== */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-3xl w-full max-w-lg p-8 shadow-2xl transform transition-all">
            <h2 className="text-2xl font-black text-slate-800 mb-6 flex items-center">
              <span className="text-blue-500 mr-2">⚙️</span> Tinh chỉnh Thuật toán
            </h2>
            
            <div className="space-y-5">
              <div>
                <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2">Tên Báo Cáo (Luật)</label>
                <input 
                  type="text" 
                  value={formData.ruleName} 
                  onChange={e => setFormData({...formData, ruleName: e.target.value})}
                  className="w-full bg-slate-50 border border-slate-200 p-3.5 rounded-xl font-bold text-slate-800 focus:ring-2 focus:ring-blue-500 outline-none transition-all"
                />
              </div>

              {/* BĂM JSON RA 3 Ô */}
              <div className="p-5 bg-blue-50/50 rounded-2xl border border-blue-100">
                <label className="block text-[11px] font-bold text-blue-800 uppercase tracking-wider mb-3">Tham số đầu vào (JSON Builder)</label>
                <div className="grid grid-cols-3 gap-3">
                  <select 
                    value={formData.field}
                    onChange={e => setFormData({...formData, field: e.target.value})}
                    className="p-2.5 border border-slate-200 bg-white rounded-xl text-sm font-bold text-slate-700 outline-none focus:border-blue-500"
                  >
                    <option value="amount">Số tiền</option>
                    <option value="emotion">Cảm xúc AI</option>
                    <option value="history">Lịch sử GD</option>
                  </select>

                  <select 
                    value={formData.operator}
                    onChange={e => setFormData({...formData, operator: e.target.value})}
                    className="p-2.5 border border-slate-200 bg-white rounded-xl text-sm font-bold text-slate-700 outline-none text-center focus:border-blue-500"
                  >
                    <option value=">=">{'>='}</option>
                    <option value=">">{'>'}</option>
                    <option value="==">Bằng (==)</option>
                  </select>

                  <input 
                    type="text" 
                    value={formData.value}
                    onChange={e => setFormData({...formData, value: e.target.value})}
                    placeholder="Giá trị..."
                    className="p-2.5 border border-slate-200 bg-white rounded-xl text-sm font-bold text-slate-700 outline-none focus:border-blue-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2">Mức độ Nghiêm trọng (Điểm Phạt)</label>
                <input 
                  type="number" 
                  value={formData.actionScore} 
                  onChange={e => setFormData({...formData, actionScore: e.target.value})}
                  className="w-full bg-slate-50 border border-slate-200 p-3.5 rounded-xl font-black text-2xl text-red-500 focus:ring-2 focus:ring-red-500 outline-none transition-all"
                />
              </div>
            </div>

            {/* Nút Save/Cancel */}
            <div className="flex justify-end space-x-3 mt-8">
              <button 
                onClick={() => setIsModalOpen(false)}
                className="px-6 py-3.5 font-bold text-slate-500 hover:bg-slate-100 rounded-xl transition-colors"
              >
                Hủy bỏ
              </button>
              <button 
                onClick={handleSaveRule}
                className="px-6 py-3.5 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl transition-colors shadow-lg shadow-blue-600/30"
              >
                Cập nhật lên Server
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}