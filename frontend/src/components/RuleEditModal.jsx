import React, { useState, useEffect } from 'react';

export default function RuleEditModal({ isOpen, onClose, onSave, initialData }) {
  // State cục bộ để quản lý Form nhập liệu
  const [formData, setFormData] = useState({
    ruleName: '',
    actionScore: 0,
    field: 'amount',
    operator: '>=',
    value: ''
  });

  // Mỗi khi initialData thay đổi (Admin bấm Sửa một luật khác, hoặc bấm Tạo mới),
  // ta phải nạp dữ liệu đó vào State để hiển thị lên Form.
  useEffect(() => {
    if (initialData) {
      setFormData({
        ruleName: initialData.ruleName || '',
        actionScore: initialData.actionScore || 0,
        field: initialData.field || 'amount',
        operator: initialData.operator || '>=',
        value: initialData.value || ''
      });
    }
  }, [initialData]);

  // Hàm xử lý khi bấm Lưu (Chỉ đóng gói dữ liệu và gọi ngược lên Cha)
  const handleSaveClick = () => {
    onSave(formData);
  };

  // Nếu Modal đang bị ẩn, không render gì cả
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-3xl w-full max-w-lg p-8 shadow-2xl transform transition-all">
        <h2 className="text-2xl font-black text-slate-800 mb-6 flex items-center">
          <span className="text-blue-500 mr-2">⚙️</span> Tinh chỉnh Thuật toán
        </h2>
        
        <div className="space-y-5">
          {/* Ô Nhập Tên Luật */}
          <div>
            <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2">Tên Báo Cáo (Luật)</label>
            <input 
              type="text" 
              value={formData.ruleName} 
              onChange={e => setFormData({...formData, ruleName: e.target.value})}
              className="w-full bg-slate-50 border border-slate-200 p-3.5 rounded-xl font-bold text-slate-800 focus:ring-2 focus:ring-blue-500 outline-none transition-all"
            />
          </div>

          {/* Ô Nhập JSON Bối cảnh */}
          <div className="p-5 bg-blue-50/50 rounded-2xl border border-blue-100">
            <label className="block text-[11px] font-bold text-blue-800 uppercase tracking-wider mb-3">Tham số đầu vào (JSON Builder)</label>
            <div className="grid grid-cols-3 gap-3">
              <select 
                value={formData.field}
                onChange={e => setFormData({...formData, field: e.target.value})}
                className="p-2.5 border border-slate-200 bg-white rounded-xl text-sm font-bold text-slate-700 outline-none focus:border-blue-500"
              >
                {/* Nhóm Điều phối Tiền bạc */}
                <option value="amount">Số tiền / 1 Giao dịch</option>
                <option value="dailyTotal">Tổng tiền GD trong ngày</option>
                <option value="balanceRatio">Tỷ lệ vét ví (Ví dụ: 0.9)</option>
                
                {/* Nhóm Hành vi & Lịch sử */}
                <option value="history">Lịch sử GD (NEW_RECIPIENT)</option>
                <option value="recentTxCount">Số lần GD liên tục (Spam)</option>
                <option value="isNightTime">Giao dịch đêm khuya (true/false)</option>
                
                {/* Nhóm Thiết bị & AI */}
                <option value="deviceTrusted">Thiết bị lạ (true/false)</option>
                <option value="suspiciousSession">Phiên rủi IP (true/false)</option>
                <option value="emotion">Cảm xúc AI (STRESS/FEAR)</option>
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

          {/* Ô Nhập Điểm */}
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
            onClick={onClose}
            className="px-6 py-3.5 font-bold text-slate-500 hover:bg-slate-100 rounded-xl transition-colors"
          >
            Hủy bỏ
          </button>
          <button 
            onClick={handleSaveClick}
            className="px-6 py-3.5 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl transition-colors shadow-lg shadow-blue-600/30"
          >
            Cập nhật lên Server
          </button>
        </div>
      </div>
    </div>
  );
}