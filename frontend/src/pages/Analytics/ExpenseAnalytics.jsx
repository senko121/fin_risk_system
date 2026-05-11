
import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';

//   1. IMPORT CÁI MODAL THẦN THÁNH VÀO
import TransactionDetailModal from '../../components/TransactionDetailModal';

export default function ExpenseAnalytics() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [history, setHistory] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  
  //   2. THÊM STATE ĐỂ QUẢN LÝ VIỆC BẬT/TẮT POPUP
  const [selectedTx, setSelectedTx] = useState(null);

  useEffect(() => {
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      const parsedUser = JSON.parse(storedUser);
      setUser(parsedUser);
      fetchHistory(parsedUser.id || parsedUser.userId);
    } else {
      toast.error("🚨 Bạn chưa đăng nhập!");
      navigate('/login');
    }
  }, [navigate]);

const fetchHistory = async (accountId) => {
    try {
      // 💡 ĐÃ ĐỔI ĐƯỜNG DẪN TỪ /history/ SANG /analytics/
      const response = await axiosClient.get(`/transactions/analytics/${accountId}`);
      
      // 💡 KHÔNG CÒN .content NỮA, VÌ API BÂY GIỜ TRẢ THẲNG VỀ MẢNG RỒI
      setHistory(response.data || []); 
      
    } catch (error) {
      toast.warning("Không thể tải dữ liệu phân tích.");
    } finally {
      setIsLoading(false);
    }
  };

  const formatMoney = (amount) => {
    if (amount == null || isNaN(amount)) return "0 ₫";
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
  };

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    return date.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  };

  // =================================================================
  // LOGIC TÍNH TOÁN DỮ LIỆU BÁO CÁO 
  // =================================================================
const analyticsData = useMemo(() => {
    try {
      if (!history || !Array.isArray(history) || history.length === 0) {
        return { chartData: [], totalIn: 0, totalOut: 0, topExpenses: [] };
      }

      let totalIn = 0;
      let totalOut = 0;
      const groupedData = {};
      const expenseList = [];

      history.forEach(curr => {
        // 💡 QUAY LẠI SỬ DỤNG .date VÀ .type THEO ĐÚNG JSON RESPONSE
        const rawDate = curr.date; 
        if (!rawDate) return;

        const type = curr.type; 
        const amount = curr.amount || 0;
        const description = curr.description || "Giao dịch";

        if (type === 'CREDIT') {
          totalIn += amount;
        } else {
          totalOut += amount;
          expenseList.push({ ...curr, displayDescription: description }); 
        }

        const dateObj = new Date(rawDate);
        if (isNaN(dateObj.getTime())) return;
        
        const dateStr = `${dateObj.getDate().toString().padStart(2, '0')}/${(dateObj.getMonth() + 1).toString().padStart(2, '0')}`;
        if (!groupedData[dateStr]) {
          groupedData[dateStr] = { name: dateStr, in: 0, out: 0 };
        }

        if (type === 'CREDIT') {
          groupedData[dateStr].in += amount;
        } else {
          groupedData[dateStr].out += amount;
        }
      });

      const chartData = Object.values(groupedData).slice(-7);
      const topExpenses = expenseList.sort((a, b) => b.amount - a.amount).slice(0, 3);

      return { chartData, totalIn, totalOut, topExpenses };
    } catch (error) {
      console.error("Lỗi tính toán Analytics:", error);
      return { chartData: [], totalIn: 0, totalOut: 0, topExpenses: [] };
    }
  }, [history]);

  const { chartData, totalIn, totalOut, topExpenses } = analyticsData;
  const netBalance = totalIn - totalOut;

  if (!user) return null;

  return (
    <div className="min-h-screen bg-slate-50 py-8 px-4 sm:px-6 lg:px-8">
      <div className="max-w-4xl mx-auto animate-fade-in-up">
        
        {/* HEADER */}
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center">
            <button 
              onClick={() => navigate('/dashboard')}
              className="flex items-center text-gray-500 hover:text-blue-600 transition-colors mr-4 bg-white p-2 rounded-full shadow-sm"
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10 19l-7-7m0 0l7-7m-7 7V5"></path></svg>
            </button>
            <div>
              <h1 className="text-2xl font-black text-gray-900">Quản lý chi tiêu</h1>
              <p className="text-sm text-gray-500">Phân tích dòng tiền 7 ngày gần nhất</p>
            </div>
          </div>
        </div>

        {/* KHU VỰC 1: 3 THẺ TỔNG QUAN */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
          <div className="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 relative overflow-hidden">
            <div className="absolute top-0 right-0 p-4 opacity-10 text-green-500">
              <svg className="w-12 h-12" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"></path></svg>
            </div>
            <p className="text-sm font-bold text-gray-400 mb-1">Tổng tiền vào</p>
            <h4 className="text-2xl font-black text-green-600">{isLoading ? "..." : formatMoney(totalIn)}</h4>
          </div>
          
          <div className="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 relative overflow-hidden">
            <div className="absolute top-0 right-0 p-4 opacity-10 text-red-500">
              <svg className="w-12 h-12" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 17h8m0 0V9m0 8l-8-8-4 4-6-6"></path></svg>
            </div>
            <p className="text-sm font-bold text-gray-400 mb-1">Tổng tiền ra</p>
            <h4 className="text-2xl font-black text-red-600">{isLoading ? "..." : formatMoney(totalOut)}</h4>
          </div>

          <div className={`p-6 rounded-3xl shadow-sm relative overflow-hidden ${netBalance >= 0 ? 'bg-blue-600 text-white' : 'bg-orange-500 text-white'}`}>
            <p className="text-sm font-bold opacity-80 mb-1">Biến động thuần (Net)</p>
            <h4 className="text-2xl font-black">{isLoading ? "..." : `${netBalance >= 0 ? '+' : ''}${formatMoney(netBalance)}`}</h4>
          </div>
        </div>

       {/*   KHU VỰC 2: BIỂU ĐỒ BAR CHART (Đã fix lỗi Mobile) */}
        <div className="bg-white p-4 sm:p-6 rounded-3xl shadow-sm border border-gray-100 mb-8 w-full overflow-hidden">
          <h3 className="text-lg font-bold text-gray-800 mb-6 flex items-center">
            <svg className="w-5 h-5 text-blue-500 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"></path></svg>
            Biểu đồ thu chi
          </h3>
          
          {isLoading ? (
            <div className="h-72 w-full flex items-center justify-center text-gray-400 animate-pulse">Đang tải biểu đồ...</div>
          ) : chartData.length === 0 ? (
            <div className="h-72 w-full flex items-center justify-center text-gray-400 text-center px-4">Chưa có dữ liệu giao dịch để vẽ biểu đồ.</div>
          ) : (
            // 💡 BÍ KÍP TRỊ LỖI WIDTH/HEIGHT = -1 TRÊN MOBILE Ở ĐÂY:
            // 1. Thêm style={{ minWidth: 0, minHeight: 288 }} cho thẻ bọc
            // 2. Đổi width của ResponsiveContainer thành 99%
            <div className="h-72 w-full" style={{ minWidth: 0, minHeight: 288 }}>
              <ResponsiveContainer width="99%" height="100%" minHeight={288} minWidth={0}>
                <BarChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f3f4f6" />
                  <XAxis 
                    dataKey="name" 
                    axisLine={false} 
                    tickLine={false} 
                    tick={{ fontSize: 11, fill: '#6b7280' }} // Thu nhỏ font chữ ngày tháng một chút trên mobile
                    dy={10} 
                  />
                  <YAxis 
                    axisLine={false} 
                    tickLine={false} 
                    tick={{ fontSize: 11, fill: '#6b7280' }}
                    tickFormatter={(value) => value >= 1000000 ? `${(value / 1000000).toFixed(0)}Tr` : value} 
                    width={45} // Cố định độ rộng trục Y để không bị lẹm biểu đồ
                  />
                  <Tooltip 
                    cursor={{ fill: '#f9fafb' }}
                    formatter={(value) => formatMoney(value)}
                    contentStyle={{ borderRadius: '16px', border: 'none', boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1)' }}
                  />
                  <Legend iconType="circle" wrapperStyle={{ paddingTop: '20px', fontSize: '12px', fontWeight: 'bold' }}/>
                  <Bar dataKey="in" name="Tiền vào" fill="#10b981" radius={[4, 4, 0, 0]} maxBarSize={32} />
                  <Bar dataKey="out" name="Tiền ra" fill="#ef4444" radius={[4, 4, 0, 0]} maxBarSize={32} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>

        {/*   KHU VỰC 3: TOP GIAO DỊCH LỚN NHẤT (Đã chống tràn viền Mobile) */}
        {!isLoading && topExpenses.length > 0 && (
          <div className="bg-white rounded-3xl shadow-sm border border-gray-100 p-4 sm:p-6 mb-8">
            <h3 className="text-lg font-bold text-gray-800 mb-4 flex items-center">
              <svg className="w-5 h-5 text-red-500 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
              Top 3 khoản chi lớn nhất
            </h3>
            <div className="space-y-3">
              {topExpenses.map((item, index) => (
                <div 
                  key={item.id} 
                  onClick={() => setSelectedTx(item)}
                  className="flex items-center justify-between p-3 sm:p-4 bg-red-50/50 rounded-2xl border border-red-50 cursor-pointer hover:bg-red-100 transition-colors"
                >
                  <div className="flex items-center overflow-hidden mr-2">
                    <div className="min-w-[40px] h-10 bg-white text-red-500 font-black rounded-full flex items-center justify-center mr-3 sm:mr-4 shadow-sm border border-red-100">
                      #{index + 1}
                    </div>
                    {/* 💡 BÍ KÍP CHỐNG RỚT DÒNG MOBILE: Dùng truncate và max-w */}
                    <div className="overflow-hidden">
                      <p className="font-bold text-gray-800 text-sm truncate">{item.displayDescription}</p>
                      <p className="text-xs text-gray-500 mt-0.5">{formatDate(item.date)}</p>
                    </div>
                  </div>
                  <div className="text-right flex-shrink-0">
                    <p className="font-black text-red-600 text-sm sm:text-base">-{formatMoney(item.amount)}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

      </div>

      {/*   4. GỌI COMPONENT MODAL RA CUỐI TRANG */}
      <TransactionDetailModal 
        isOpen={!!selectedTx} 
        onClose={() => setSelectedTx(null)} 
        transaction={selectedTx} 
        formatMoney={formatMoney} 
        formatDate={formatDate} 
      />

    </div>
  );
}