 

import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import { Link } from 'react-router-dom';

// 🚀 IMPORT COMPONENT MODAL CON VÀO ĐÂY
import RuleEditModal from '../../components/RuleEditModal';

export default function AdminRuleDashboard() {
  const [rules, setRules] = useState([]);
  const [isLoading, setIsLoading] = useState(true);

  // State điều khiển Modal
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  
  // Dữ liệu mồi truyền xuống cho Modal Con
  const [initialModalData, setInitialModalData] = useState(null);

  // 1. Gọi API lấy danh sách luật
  const fetchRules = async () => {
    try {
      setIsLoading(true);
      const response = await axiosClient.get('/admin/rules');
      setRules(response.data);
    } catch (error) {
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
      // Đã xóa bỏ đoạn config chứa { headers: ... }
      await axiosClient.patch(`/admin/rules/${id}/toggle`);
      toast.success("Đã thay đổi trạng thái luật!");
      fetchRules(); 
    } catch (error) {
      toast.error('Lỗi khi bật/tắt luật!');
    }
  };

  // 3. Mở Modal cho việc SỬA
  const openEditModal = (rule) => {
    let parsedCondition = {};
    try {
      parsedCondition = JSON.parse(rule.conditions);
    } catch (e) {
      console.error("Lỗi parse JSON");
    }

    // Nạp dữ liệu vào Cục mồi
    setInitialModalData({
      ruleName: rule.ruleName,
      actionScore: rule.actionScore,
      field: parsedCondition.field || 'amount',
      operator: parsedCondition.operator || '>=',
      value: parsedCondition.value || ''
    });
    setEditingId(rule.id);
    setIsModalOpen(true);
  };

  // 3.5. Mở Modal cho việc TẠO MỚI (Reset Cục mồi)
  const openCreateModal = () => {
    setInitialModalData({
      ruleName: '',
      actionScore: 0,
      field: 'amount',
      operator: '>=',
      value: ''
    });
    setEditingId(null);
    setIsModalOpen(true);
  };

 // 4. Nhận dữ liệu từ Modal Con và gọi API Lưu
  const handleSaveFromModal = async (formDataFromChild) => {
    const newConditionsJson = JSON.stringify({
      field: formDataFromChild.field,
      operator: formDataFromChild.operator,
      value: formDataFromChild.value
    });

    const payload = {
      ruleName: formDataFromChild.ruleName,
      actionScore: parseInt(formDataFromChild.actionScore),
      conditions: newConditionsJson
    };

    try {
      if (editingId) {
        // Đã xóa bỏ cục { headers: ... } ở tham số thứ 3
        await axiosClient.put(`/admin/rules/${editingId}`, payload);
        toast.success("Cập nhật luật thành công!");
      } else {
        // Đã xóa bỏ cục { headers: ... } ở tham số thứ 3
        await axiosClient.post(`/admin/rules`, payload);
        toast.success("Tạo luật mới thành công!");
      }
      setIsModalOpen(false);
      fetchRules(); 
    } catch (error) {
      toast.error('Lỗi lưu cấu hình luật!');
    }
  };

  return (
    <div className="w-full p-8 pb-16">
      <div className="max-w-6xl mx-auto">
        
      {/* HEADER */}
        <div className="mb-8 flex justify-between items-start">
          <div>
            <h1 className="text-3xl font-black text-slate-900 tracking-tight">FinRisk Rule Engine</h1>
            <p className="text-slate-500 font-medium mt-1 mb-5">Quản lý thuật toán và trọng số đánh giá rủi ro (AI Core)</p>
            
            <button 
              onClick={openCreateModal}
              className="px-5 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl transition-colors shadow-lg shadow-blue-600/30 flex items-center"
            >
              <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M12 4v16m8-8H4"></path></svg>
              + Thêm Luật Mới
            </button>
          </div>
          
          <Link to="/admin" className="px-5 py-2.5 bg-slate-900 text-white font-bold rounded-xl hover:bg-black transition-colors shadow-lg flex items-center h-fit">
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

      {/* 🚀 GỌI COMPONENT CON VÀ TRUYỀN PROPS XUỐNG */}
      <RuleEditModal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
        onSave={handleSaveFromModal}
        initialData={initialModalData}
      />

    </div>
  );
}