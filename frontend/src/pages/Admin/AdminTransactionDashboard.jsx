 

import React, { useState, useEffect } from 'react';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import { Link } from 'react-router-dom';

import AdminTransactionDetailModal from '../../components/AdminTransactionDetailModal';

export default function AdminTransactionDashboard() {
  const [transactions, setTransactions] = useState([]);
  
  // State quản lý Phân trang (Lazy Load danh sách)
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  // State quản lý Bộ lọc (Filters)
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [riskLevel, setRiskLevel] = useState('');
  
  // State quản lý Giao dịch được chọn và hiệu ứng chờ tải dữ liệu AI chi tiết
  const [selectedTransaction, setSelectedTransaction] = useState(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);

  // State quản lý FEAR Alert Panel
  const [fearTransactions, setFearTransactions] = useState([]);
  const [fearLoading, setFearLoading] = useState(false);
  const [fearActionLoading, setFearActionLoading] = useState(new Set());

  // Hàm gọi API lấy danh sách giao dịch
  const fetchTransactions = async (pageNumber, isReset = false) => {
    if (pageNumber === 0) setLoading(true);
    else setIsLoadingMore(true);

    try {
      // ✅ ĐÃ SỬA: Bỏ tiền tố /api thừa (vì axiosClient đã có sẵn baseURL)
      const res = await axiosClient.get('/admin/transactions', {
        params: {
          page: pageNumber,
          size: 10, 
          search: search,
          status: status || undefined,       
          riskLevel: riskLevel || undefined  
        }
      });
      
      const newData = res.data.content || res.data || [];

      if (isReset || pageNumber === 0) {
        setTransactions(newData);
      } else {
        setTransactions(prev => [...prev, ...newData]);
      }

      setHasMore(!res.data.last); 
      setPage(pageNumber);

    } catch (error) {
      toast.error("Không thể tải danh sách giao dịch!");
      console.error(error);
    } finally {
      setLoading(false);
      setIsLoadingMore(false);
    }
  };

  // Lấy danh sách giao dịch đang bị đóng băng do cảm xúc bất thường
  const fetchFearTransactions = async (silent = false) => {
    if (!silent) setFearLoading(true);
    try {
      const res = await axiosClient.get('/admin/transactions', {
        params: { status: 'UNDER_REVIEW', page: 0, size: 50 }
      });
      setFearTransactions(res.data.content || res.data || []);
    } catch {
      // panel phụ — không cần toast khi lỗi
    } finally {
      if (!silent) setFearLoading(false);
    }
  };

  // Xử lý Admin duyệt hoặc từ chối giao dịch UNDER_REVIEW
  const handleResolveReview = async (txId, action) => {
    setFearActionLoading(prev => new Set([...prev, txId]));
    try {
      await axiosClient.post(`/admin/transactions/${txId}/resolve-review`, null, {
        params: {
          action,
          adminNotes: action === 'APPROVE'
            ? 'Admin duyệt — đã xem xét tín hiệu cảm xúc bất thường'
            : action === 'FREEZE'
            ? 'Admin đóng băng — nghi ngờ cưỡng bức hoặc gian lận nghiêm trọng'
            : 'Admin từ chối — nghi ngờ gian lận hoặc ép buộc'
        }
      });
      toast.success(action === 'APPROVE'
        ? `Đã duyệt giao dịch #${txId}`
        : action === 'FREEZE'
        ? `Đã đóng băng giao dịch #${txId}`
        : `Đã từ chối giao dịch #${txId}`
      );
      setFearTransactions(prev => prev.filter(t => t.id !== txId));
      if (selectedTransaction?.id === txId) setSelectedTransaction(null);
      fetchTransactions(0, true);
    } catch (error) {
      if (error.response?.status === 409) {
        toast.warn(`Giao dịch #${txId} vừa được xử lý đồng thời bởi admin khác.`);
        setFearTransactions(prev => prev.filter(t => t.id !== txId));
      } else {
        toast.error('Không thể xử lý giao dịch. Vui lòng thử lại!');
      }
    } finally {
      setFearActionLoading(prev => { const s = new Set(prev); s.delete(txId); return s; });
    }
  };

  // 🚀 LUỒNG XỬ LÝ LAZY LOAD: Kéo dữ liệu rủi ro từ bảng phụ khi click nút chi tiết
  const handleFetchRiskDetails = async (summaryTx) => {
    setIsDetailLoading(true);
    try {
      // ✅ ĐÃ SỬA: Bỏ tiền tố /api thừa tại đây để tránh lỗi lặp URL
      const resDetail = await axiosClient.get(`/admin/transactions/${summaryTx.id}/risk-details`);
      const riskData = resDetail.data;

      // 🦾 MA THUẬT SPREAD OPERATOR: Trộn bối cảnh nền ở Table với dữ liệu chuyên sâu từ AI
      setSelectedTransaction({ 
        ...summaryTx, 
        ...riskData 
      });

    } catch (error) {
      toast.error("Không thể truy xuất hồ sơ rủi ro chuyên sâu từ AI!");
      console.error(error);
    } finally {
      setIsDetailLoading(false);
    }
  };

  // Gọi lại API mỗi khi thay đổi Trạng thái hoặc Mức rủi ro
  useEffect(() => {
    setPage(0);
    setHasMore(true);
    fetchTransactions(0, true);
  }, [status, riskLevel]);

  // Load FEAR panel + tự động làm mới mỗi 10s
  useEffect(() => {
    fetchFearTransactions();
    const id = setInterval(() => fetchFearTransactions(true), 10000);
    return () => clearInterval(id);
  }, []);

  // Xử lý khi bấm nút "Lọc" cho ô tìm kiếm
  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setPage(0);
    setHasMore(true);
    fetchTransactions(0, true);
  };

  // Nút tải thêm
  const handleLoadMore = () => {
    if (!isLoadingMore && hasMore) {
      fetchTransactions(page + 1);
    }
  };

  // Tiện ích format tiền VNĐ
  const formatMoney = (amount) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
  };

  // Badge Color cho Trạng thái
  const getStatusColor = (stt) => {
    if (stt === 'SUCCESS') return 'bg-emerald-100 text-emerald-700 border-emerald-200';
    if (stt === 'PENDING') return 'bg-amber-100 text-amber-700 border-amber-200';
    if (stt === 'FAILED' || stt === 'REJECTED' || stt === 'BLOCKED') return 'bg-red-100 text-red-700 border-red-200';
    return 'bg-slate-100 text-slate-700 border-slate-200';
  };

  // Badge Color cho Rủi ro
  const getRiskColor = (risk) => {
    if (risk === 'HIGH') return 'bg-red-500 text-white shadow-red-500/30';
    if (risk?.includes('MEDIUM')) return 'bg-orange-500 text-white shadow-orange-500/30';
    return 'bg-green-500 text-white shadow-green-500/30';
  };

  return (
    <div className="min-h-screen bg-slate-50 p-8 pb-20 relative">
      
      {/* 🔮 OVERLAY SPINNER: Kích hoạt khi đang tải dữ liệu rủi ro động từ AI */}
      {isDetailLoading && (
        <div className="fixed inset-0 z-[100] flex flex-col items-center justify-center bg-slate-950/40 backdrop-blur-sm animate-fade-in">
          <div className="w-12 h-12 border-4 border-slate-800 border-t-blue-500 rounded-full animate-spin mb-3" />
          <p className="text-white text-xs font-black uppercase tracking-widest bg-slate-900 px-4 py-2 rounded-xl shadow-2xl border border-slate-800 animate-pulse">
            🧠 AI đang bóc tách hồ sơ thói quen...
          </p>
        </div>
      )}

      <div className="max-w-7xl mx-auto">
        
        {/* HEADER */}
        <div className="flex justify-between items-start mb-8">
          <div>
            <h1 className="text-3xl font-black text-slate-900 tracking-tight uppercase">Quản lý Giao dịch</h1>
            <p className="text-slate-500 font-medium mt-2">Giám sát dòng tiền và rủi ro toàn hệ thống</p>
          </div>
          <Link to="/admin" className="px-5 py-2.5 bg-slate-900 text-white font-bold rounded-xl hover:bg-black transition-colors shadow-lg flex items-center">
            <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path></svg>
            Quay lại Dashboard
          </Link>
        </div>

        {/* FEAR ALERT PANEL */}
        {(fearLoading || fearTransactions.length > 0) && (
          <div className="bg-rose-50 border border-rose-200 rounded-3xl p-5 mb-6">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-9 h-9 bg-rose-500 rounded-2xl flex items-center justify-center shadow-lg shadow-rose-500/30">
                <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
              </div>
              <div className="flex-1">
                <h2 className="font-black text-rose-900 text-sm uppercase tracking-tight flex items-center gap-2">
                  Cảnh báo cảm xúc bất thường
                  {!fearLoading && fearTransactions.length > 0 && (
                    <span className="bg-rose-500 text-white text-[10px] px-2 py-0.5 rounded-full font-black animate-pulse">
                      {fearTransactions.length} cần duyệt
                    </span>
                  )}
                  <span className="flex items-center gap-1 text-[10px] font-bold text-emerald-600 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-full">
                    <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-pulse inline-block"></span>
                    LIVE
                  </span>
                </h2>
                <p className="text-xs text-rose-600 font-medium mt-0.5">
                  AI phát hiện FEAR / STRESS / ANGRY — giao dịch đang bị đóng băng chờ xét duyệt thủ công
                </p>
              </div>
              <button
                onClick={fetchFearTransactions}
                className="p-2 rounded-xl bg-rose-100 hover:bg-rose-200 transition-colors"
                title="Làm mới danh sách"
              >
                <svg className="w-4 h-4 text-rose-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
              </button>
            </div>

            {fearLoading ? (
              <p className="text-center text-rose-400 font-bold text-sm py-4 animate-pulse">
                Đang quét giao dịch bất thường...
              </p>
            ) : (
              <div className="space-y-2">
                {fearTransactions.map(t => (
                  <div
                    key={t.id}
                    className="bg-white rounded-2xl border border-rose-100 px-5 py-4 flex flex-wrap items-center justify-between gap-3 shadow-sm hover:shadow-md transition-shadow"
                  >
                    <div className="flex flex-wrap items-center gap-3 min-w-0">
                      <span className="text-sm font-mono font-black text-slate-500">#{t.id}</span>
                      <span className="font-black text-slate-900 text-sm">{formatMoney(t.amount)}</span>
                      <div className="flex flex-col">
                        <span className="font-bold text-slate-700 text-xs">{t.senderFullName}</span>
                        <span className="font-mono text-[10px] text-slate-400">{t.senderAccountNumber}</span>
                      </div>
                      <span className={`px-2.5 py-1 rounded-lg text-[10px] font-black uppercase tracking-wider border ${
                        t.emotionSignal === 'FEAR'   ? 'bg-red-100 text-red-700 border-red-200' :
                        t.emotionSignal === 'STRESS' ? 'bg-orange-100 text-orange-700 border-orange-200' :
                                                       'bg-amber-100 text-amber-700 border-amber-200'
                      }`}>
                        {t.emotionSignal || 'UNDER_REVIEW'}
                      </span>
                      <span className="text-[10px] font-mono text-slate-400">
                        {new Date(t.createdAt).toLocaleString('vi-VN')}
                      </span>
                    </div>

                    <div className="flex items-center gap-2 flex-shrink-0">
                      <button
                        onClick={() => handleFetchRiskDetails(t)}
                        className="px-3 py-2 rounded-xl text-xs font-black text-slate-600 bg-slate-100 hover:bg-slate-200 transition-colors border border-slate-200"
                      >
                        Chi tiết
                      </button>
                      <button
                        onClick={() => handleResolveReview(t.id, 'APPROVE')}
                        disabled={fearActionLoading.has(t.id)}
                        className="px-4 py-2 rounded-xl text-xs font-black text-white bg-emerald-500 hover:bg-emerald-600 transition-colors shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {fearActionLoading.has(t.id) ? '...' : '✓ Duyệt'}
                      </button>
                      <button
                        onClick={() => handleResolveReview(t.id, 'REJECT_FRAUD')}
                        disabled={fearActionLoading.has(t.id)}
                        className="px-4 py-2 rounded-xl text-xs font-black text-white bg-rose-500 hover:bg-rose-600 transition-colors shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {fearActionLoading.has(t.id) ? '...' : '✗ Từ chối'}
                      </button>
                      <button
                        onClick={() => {
                          if (window.confirm(`Đóng băng giao dịch #${t.id}? Giao dịch sẽ bị tạm giữ và không thể thực hiện.`)) {
                            handleResolveReview(t.id, 'FREEZE');
                          }
                        }}
                        disabled={fearActionLoading.has(t.id)}
                        className="px-4 py-2 rounded-xl text-xs font-black text-white bg-blue-600 hover:bg-blue-700 transition-colors shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {fearActionLoading.has(t.id) ? '...' : '❄ Đóng băng'}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* BỘ LỌC (FILTERS) */}
        <div className="bg-white p-5 rounded-3xl shadow-sm border border-slate-200 mb-6 flex flex-wrap gap-4 items-end">
          <form onSubmit={handleSearchSubmit} className="flex-1 min-w-[250px]">
            <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">Tìm kiếm (STK / IP)</label>
            <div className="flex gap-2">
              <input 
                type="text" 
                placeholder="Nhập từ khóa..." 
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full bg-slate-50 border border-slate-200 px-4 py-2.5 rounded-xl text-sm font-bold focus:ring-2 focus:ring-blue-500 outline-none"
              />
              <button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white px-5 py-2.5 rounded-xl font-bold transition-colors">
                Lọc
              </button>
            </div>
          </form>

          <div className="w-48">
            <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">Trạng thái</label>
            <select 
              value={status} 
              onChange={(e) => setStatus(e.target.value)}
              className="w-full bg-slate-50 border border-slate-200 px-4 py-2.5 rounded-xl text-sm font-bold outline-none cursor-pointer"
            >
              <option value="">Tất cả</option>
              <option value="SUCCESS">Thành công (SUCCESS)</option>
              <option value="PENDING">Chờ xử lý (PENDING)</option>
              <option value="FAILED">Thất bại (FAILED)</option>
            </select>
          </div>

          <div className="w-48">
            <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">Mức độ rủi ro</label>
            <select 
              value={riskLevel} 
              onChange={(e) => setRiskLevel(e.target.value)}
              className="w-full bg-slate-50 border border-slate-200 px-4 py-2.5 rounded-xl text-sm font-bold outline-none cursor-pointer"
            >
              <option value="">Tất cả</option>
              <option value="HIGH">Rủi ro CAO (HIGH)</option>
              <option value="MEDIUM_2">Cảnh báo (MEDIUM 2)</option>
              <option value="MEDIUM_1">Cảnh báo (MEDIUM 1)</option>
              <option value="LOW">An toàn (LOW)</option>
            </select>
          </div>
        </div>

        {/* BẢNG DỮ LIỆU */}
        <div className="bg-white rounded-[2rem] shadow-xl border border-slate-100 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse whitespace-nowrap">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-100">
                  <th className="px-5 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest">Mã GD</th>
                  <th className="px-5 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest">Thời gian</th>
                  <th className="px-5 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest">Người Gửi</th>
                  <th className="px-5 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest">Người Nhận</th>
                  <th className="px-5 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest text-right">Số tiền</th>
                  <th className="px-5 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest text-center">Trạng thái</th>
                  <th className="px-5 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest text-center">Cảm xúc (AI)</th>
                  <th className="px-5 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest text-center">Rủi ro (AI)</th>
                  <th className="px-5 py-4 text-[11px] font-black text-slate-400 uppercase tracking-widest text-center">Chi tiết</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {loading ? (
                  <tr><td colSpan="9" className="text-center py-20 font-bold text-slate-400 animate-pulse">Đang nạp dữ liệu giao dịch...</td></tr>
                ) : transactions.length === 0 ? (
                  <tr><td colSpan="9" className="text-center py-10 font-bold text-slate-400 italic">Không tìm thấy giao dịch nào phù hợp.</td></tr>
                ) : transactions.map((t) => (
                  <tr key={t.id} className="hover:bg-blue-50/30 transition-colors">
                    
                    <td className="px-5 py-4 text-sm font-mono font-bold text-slate-700">#{t.id}</td>

                    <td className="px-5 py-4">
                      <div className="flex flex-col gap-0.5">
                        <span className="font-bold text-slate-700 text-sm">
                          {new Date(t.createdAt).toLocaleDateString('vi-VN')}
                        </span>
                        <span className="text-xs font-mono text-slate-500">
                          {new Date(t.createdAt).toLocaleTimeString('vi-VN')}
                        </span>
                      </div>
                    </td>
                    
                    <td className="px-5 py-4">
                      <div className="flex flex-col">
                        <span className="font-bold text-slate-800 text-sm flex items-center gap-2">
                          {t.senderFullName} 
                          {t.senderSuspicious && <span className="bg-red-100 text-red-600 text-[10px] px-1.5 py-0.5 rounded uppercase font-black" title="Phiên khả nghi">Cờ Đỏ</span>}
                        </span>
                        <span className="text-xs font-mono text-slate-500">{t.senderAccountNumber}</span>
                      </div>
                    </td>

                    <td className="px-5 py-4">
                      <div className="flex flex-col">
                        <span className="font-bold text-slate-700 text-sm">
                          {t.recipientFullName || 'Người nhận ngoài hệ thống'}
                        </span>
                        <span className="text-xs font-mono text-slate-500">{t.toAccountNumber}</span>
                      </div>
                    </td>

                    <td className="px-5 py-4 text-right font-black text-slate-800 text-base">
                      {formatMoney(t.amount)}
                    </td>

                    <td className="px-5 py-4 text-center">
                      <span className={`px-3 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-tight border ${getStatusColor(t.status)}`}>
                        {t.status}
                      </span>
                    </td>

                    <td className="px-5 py-4 text-center">
                      {t.emotionSignal ? (
                        <span className={`px-2.5 py-1 rounded-md text-[10px] font-bold uppercase tracking-wider ${t.emotionSignal === 'FEAR' || t.emotionSignal === 'STRESS' ? 'bg-red-50 text-red-600 border border-red-100' : 'bg-blue-50 text-blue-600 border border-blue-100'}`}>
                          {t.emotionSignal}
                        </span>
                      ) : (
                        <span className="text-slate-400 font-bold text-[11px]">N/A</span>
                      )}
                    </td>

                    <td className="px-5 py-4 text-center">
                      <span className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-widest shadow-sm ${getRiskColor(t.riskLevel)}`}>
                        {t.riskLevel || 'LOW'} ({t.totalRiskScore}đ)
                      </span>
                    </td>

                    <td className="px-5 py-4 text-center">
                      <button 
                        onClick={() => handleFetchRiskDetails(t)}
                        className="p-1.5 bg-slate-100 text-slate-500 hover:bg-blue-100 hover:text-blue-600 rounded-lg transition-all shadow-sm border border-slate-200 hover:border-blue-200"
                        title="Xem chi tiết luật vi phạm"
                      >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                        </svg>
                      </button>
                    </td>
                    
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* NÚT TẢI THÊM (LAZY LOAD DANH SÁCH) */}
          {!loading && transactions.length > 0 && (
            <div className="p-4 bg-slate-50 border-t border-slate-100 flex justify-center">
              {hasMore ? (
                <button
                  onClick={handleLoadMore}
                  disabled={isLoadingMore}
                  className={`px-8 py-3 rounded-xl font-black text-sm transition-all shadow-sm
                    ${isLoadingMore 
                      ? 'bg-slate-200 text-slate-500 cursor-not-allowed' 
                      : 'bg-white border border-slate-200 text-blue-600 hover:bg-blue-50 hover:shadow-md hover:border-blue-200'
                    }`}
                >
                  {isLoadingMore ? 'Đang tải thêm...' : '⬇️ Xem thêm 10 giao dịch cũ hơn'}
                </button>
              ) : (
                <span className="text-sm font-medium text-slate-400 italic">
                  Đã hiển thị toàn bộ giao dịch phù hợp.
                </span>
              )}
            </div>
          )}

        </div>
      </div>
      
      {/* ── MODAL CHI TIẾT GIAO DỊCH ──────────────────────── */}
      <AdminTransactionDetailModal
        isOpen={!!selectedTransaction}
        onClose={() => setSelectedTransaction(null)}
        transaction={selectedTransaction}
        formatMoney={formatMoney}
        onResolve={handleResolveReview}
        isResolving={selectedTransaction ? fearActionLoading.has(selectedTransaction.id) : false}
      />
    </div>
  );
}