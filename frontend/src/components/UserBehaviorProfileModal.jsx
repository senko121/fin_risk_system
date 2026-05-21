// import React, { useEffect, useState } from 'react';
// import { 
//     Radar, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, ResponsiveContainer, Tooltip 
// } from 'recharts';
// import { Activity, Clock, DollarSign, Target, ShieldAlert, CheckCircle, AlertTriangle } from 'lucide-react';
// import axiosClient from '../api/axiosClient'; // Dùng chuẩn axiosClient của bạn

// const UserBehaviorProfileModal = ({ userId, isOpen, onClose }) => {
//     const [profile, setProfile] = useState(null);
//     const [loading, setLoading] = useState(true);

//     useEffect(() => {
//         const fetchBehaviorProfile = async () => {
//             if (isOpen && userId) {
//                 setLoading(true);
//                 try {
//                     // Cú pháp gọi API thẳng bằng axiosClient y hệt code cũ của bạn
//                     // Lưu ý: Nếu báo lỗi 404, bạn thử thêm '/api' vào đầu chuỗi: '/api/admin/analytics/...'
//                     const response = await axiosClient.get(`/admin/analytics/user/${userId}/behavior-profile`);
//                     setProfile(response.data);
//                 } catch (error) {
//                     console.error("Lỗi lấy dữ liệu hành vi", error);
//                 } finally {
//                     setLoading(false);
//                 }
//             }
//         };

//         fetchBehaviorProfile();
//     }, [isOpen, userId]);

//     if (!isOpen) return null;

//     // Chuẩn bị data cho Radar Chart từ DTO
//     const radarData = profile ? [
//         { subject: 'Số tiền', score: profile.amountAnomalyScore, fullMark: 100 },
//         { subject: 'Giờ GD', score: profile.hourAnomalyScore, fullMark: 100 },
//         { subject: 'Tần suất', score: profile.frequencyAnomalyScore, fullMark: 100 },
//         { subject: 'Người nhận mới', score: profile.recipientAnomalyScore, fullMark: 100 },
//     ] : [];

//     // Render màu sắc theo mức độ rủi ro
//     const getRiskColor = (level) => {
//         if (level === 'NORMAL') return 'text-green-500 bg-green-50';
//         if (level === 'SUSPICIOUS') return 'text-orange-500 bg-orange-50';
//         return 'text-red-600 bg-red-50';
//     };

//     return (
//         <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
//             {/* Vùng bấm ra ngoài để đóng (Giống phong cách TransactionDetailModal của bạn) */}
//             <div className="absolute inset-0" onClick={onClose}></div>

//             <div className="relative bg-white w-full max-w-4xl rounded-3xl shadow-2xl p-6 max-h-[90vh] overflow-y-auto animate-fade-in-up">
//                 {/* Header */}
//                 <div className="flex justify-between items-center border-b border-gray-100 pb-4 mb-4">
//                     <h2 className="text-2xl font-bold text-gray-800 flex items-center gap-2">
//                         <Activity className="text-blue-600" />
//                         Hồ sơ Hành vi User #{userId}
//                     </h2>
//                     <button 
//                         onClick={onClose} 
//                         className="text-gray-400 hover:text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-full p-2 transition-colors"
//                     >
//                         <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
//                     </button>
//                 </div>

//                 {loading ? (
//                     <div className="flex justify-center p-10 font-bold text-slate-400 animate-pulse">
//                         Đang phân tích dữ liệu AI...
//                     </div>
//                 ) : !profile ? (
//                     <div className="text-center text-gray-500 p-10 italic">Không tìm thấy dữ liệu hành vi.</div>
//                 ) : (
//                     <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        
//                         {/* Cột Trái: Tóm tắt & Biểu đồ Radar */}
//                         <div className="space-y-6">
//                             {/* Khối Điểm Rủi Ro (Anomaly Score) */}
//                             <div className={`p-6 rounded-2xl border ${getRiskColor(profile.anomalyLevel)} flex flex-col items-center justify-center text-center`}>
//                                 {profile.anomalyLevel === 'NORMAL' && <CheckCircle size={40} className="mb-2" />}
//                                 {profile.anomalyLevel === 'SUSPICIOUS' && <AlertTriangle size={40} className="mb-2" />}
//                                 {profile.anomalyLevel === 'CRITICAL' && <ShieldAlert size={40} className="mb-2" />}
                                
//                                 <span className="text-sm font-semibold uppercase tracking-wider">Điểm bất thường (Anomaly Score)</span>
//                                 <span className="text-5xl font-black mt-2">{profile.anomalyScore.toFixed(1)} <span className="text-xl">/ 100</span></span>
//                                 <span className="mt-2 font-medium">Trạng thái: {profile.anomalyLevel}</span>
//                             </div>

//                             {/* Radar Chart Component */}
//                             <div className="bg-slate-50 p-4 rounded-2xl border border-slate-100 h-64">
//                                 <h3 className="text-center font-bold text-slate-700 mb-2">Phân bố rủi ro theo chiều</h3>
//                                 <ResponsiveContainer width="100%" height="100%">
//                                     <RadarChart cx="50%" cy="50%" outerRadius="70%" data={radarData}>
//                                         <PolarGrid stroke="#e2e8f0" />
//                                         <PolarAngleAxis dataKey="subject" textAnchor="middle" tick={{ fill: '#475569', fontSize: 12, fontWeight: 600 }} />
//                                         <PolarRadiusAxis angle={30} domain={[0, 100]} tick={false} axisLine={false} />
//                                         <Radar name="Mức độ rủi ro" dataKey="score" stroke="#4f46e5" fill="#4f46e5" fillOpacity={0.5} />
//                                         <Tooltip contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }} />
//                                     </RadarChart>
//                                 </ResponsiveContainer>
//                             </div>
//                         </div>

//                         {/* Cột Phải: Các chỉ số chi tiết */}
//                         <div className="space-y-4">
//                             {/* Thông số hệ thống học (Machine Learning Stats) */}
//                             <div className="bg-white p-4 rounded-2xl border border-slate-100 shadow-sm">
//                                 <h3 className="font-bold text-slate-700 mb-3 border-b border-slate-100 pb-2 flex items-center gap-2"><Target size={18} className="text-blue-500"/> Trạng thái Học máy</h3>
//                                 <div className="grid grid-cols-2 gap-4 text-sm">
//                                     <div>
//                                         <p className="text-slate-500 mb-1">Giai đoạn</p>
//                                         <p className="font-bold text-blue-600 bg-blue-50 px-2 py-1 rounded w-fit">{profile.profilingPhase}</p>
//                                     </div>
//                                     <div>
//                                         <p className="text-slate-500 mb-1">Thuật toán</p>
//                                         <p className="font-bold text-purple-600 bg-purple-50 px-2 py-1 rounded w-fit">{profile.calculationMethod}</p>
//                                     </div>
//                                     <div>
//                                         <p className="text-slate-500 mb-1">Số mẫu đã học</p>
//                                         <p className="font-bold text-slate-800">{profile.txCount} Giao dịch</p>
//                                     </div>
//                                     <div>
//                                         <p className="text-slate-500 mb-1">Độ tin cậy profile</p>
//                                         <div className="flex items-center gap-2">
//                                             <p className="font-bold text-slate-800">{(profile.profileReliability * 100).toFixed(0)}%</p>
//                                             <progress className="progress progress-primary w-16" value={profile.profileReliability * 100} max="100"></progress>
//                                         </div>
//                                     </div>
//                                 </div>
//                             </div>

//                             {/* Thống kê thói quen trung bình */}
//                             <div className="bg-white p-4 rounded-2xl border border-slate-100 shadow-sm">
//                                 <h3 className="font-bold text-slate-700 mb-3 border-b border-slate-100 pb-2 flex items-center gap-2"><Activity size={18} className="text-green-500"/> Thói quen Giao dịch</h3>
//                                 <ul className="space-y-4 text-sm">
//                                     <li className="flex justify-between items-center">
//                                         <span className="flex items-center gap-2 text-slate-600 font-medium"><DollarSign size={16} className="text-slate-400"/> Số tiền TB</span>
//                                         <div className="text-right">
//                                             <p className="font-bold text-slate-800">{profile.avgAmount.toLocaleString('vi-VN')} ₫</p>
//                                             <p className="text-xs text-slate-400">± {profile.stdAmount.toLocaleString('vi-VN')} ₫ (Độ lệch)</p>
//                                         </div>
//                                     </li>
//                                     <li className="flex justify-between items-center">
//                                         <span className="flex items-center gap-2 text-slate-600 font-medium"><Clock size={16} className="text-slate-400"/> Giờ GD quen thuộc</span>
//                                         <p className="font-bold text-slate-800">{profile.avgTransactionHour}</p>
//                                     </li>
//                                     <li className="flex justify-between items-center">
//                                         <span className="flex items-center gap-2 text-slate-600 font-medium"><Activity size={16} className="text-slate-400"/> Khoảng cách các GD</span>
//                                         <div className="text-right">
//                                             <p className="font-bold text-slate-800">{profile.avgGapHours.toFixed(1)} giờ</p>
//                                             <p className="text-xs text-slate-400">± {profile.stdGapHours.toFixed(1)} giờ (Độ lệch)</p>
//                                         </div>
//                                     </li>
//                                 </ul>
//                             </div>
//                         </div>
//                     </div>
//                 )}
//             </div>
//         </div>
//     );
// };

// export default UserBehaviorProfileModal;


import React, { useEffect, useState } from 'react';
import { 
    Radar, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, ResponsiveContainer, Tooltip 
} from 'recharts';
import { Activity, Clock, DollarSign, Target, ShieldAlert, CheckCircle, AlertTriangle, Database } from 'lucide-react';
import axiosClient from '../api/axiosClient';

const UserBehaviorProfileModal = ({ userId, isOpen, onClose }) => {
    const [profile, setProfile] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchBehaviorProfile = async () => {
            if (isOpen && userId) {
                setLoading(true);
                try {
                    const response = await axiosClient.get(`/admin/analytics/user/${userId}/behavior-profile`);
                    setProfile(response.data);
                } catch (error) {
                    console.error("Lỗi lấy dữ liệu hành vi", error);
                } finally {
                    setLoading(false);
                }
            }
        };

        fetchBehaviorProfile();
    }, [isOpen, userId]);

    if (!isOpen) return null;

    // Chuẩn bị data an toàn cho Radar Chart
    const radarData = profile ? [
        { subject: 'Số tiền', score: Number(profile.amountAnomalyScore || 0), fullMark: 100 },
        { subject: 'Giờ GD', score: Number(profile.hourAnomalyScore || 0), fullMark: 100 },
        { subject: 'Tần suất', score: Number(profile.frequencyAnomalyScore || 0), fullMark: 100 },
        { subject: 'Người nhận mới', score: Number(profile.recipientAnomalyScore || 0), fullMark: 100 },
    ] : [];

    // Khử màu sắc theo mức độ rủi ro dựa trên Anomaly Level
    const getRiskColor = (level) => {
        if (level === 'NORMAL') return 'text-green-500 bg-green-50 border-green-100';
        if (level === 'SUSPICIOUS') return 'text-orange-500 bg-orange-50 border-orange-100';
        return 'text-red-600 bg-red-50 border-red-100';
    };

    // Kiểm tra giai đoạn Khởi động lạnh
    const isColdStart = profile?.profilingPhase === 'COLD_START';

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
            {/* Vùng bấm ra ngoài để đóng */}
            <div className="absolute inset-0" onClick={onClose}></div>

            <div className="relative bg-white w-full max-w-4xl rounded-3xl shadow-2xl p-6 max-h-[90vh] overflow-y-auto animate-fade-in-up">
                {/* Header */}
                <div className="flex justify-between items-center border-b border-gray-100 pb-4 mb-4">
                    <h2 className="text-2xl font-black text-gray-800 flex items-center gap-2">
                        <Activity className="text-blue-600" />
                        Hồ sơ Hành vi User #{userId}
                    </h2>
                    <button 
                        onClick={onClose} 
                        className="text-gray-400 hover:text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-full p-2 transition-colors"
                    >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                    </button>
                </div>

                {loading ? (
                    <div className="flex flex-col items-center justify-center p-16 space-y-4">
                        <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
                        <span className="font-bold text-slate-400 animate-pulse">AI Đang phân tích dữ liệu hành vi...</span>
                    </div>
                ) : !profile ? (
                    <div className="text-center text-gray-500 p-10 italic">Không tìm thấy dữ liệu hành vi.</div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        
                        {/* Cột Trái: Điểm số rủi ro hoặc Màn hình chờ học máy (Cold Start) */}
                        <div className="space-y-6">
                            {isColdStart ? (
                                <div className="h-full flex flex-col items-center justify-center bg-slate-50 rounded-2xl border border-slate-100 p-8 text-center min-h-[350px]">
                                    <div className="w-16 h-16 bg-blue-100 text-blue-500 rounded-2xl flex items-center justify-center mb-4">
                                        <Database size={32} />
                                    </div>
                                    <h3 className="text-lg font-bold text-slate-700 mb-2">Giai đoạn Khởi động lạnh (Cold Start)</h3>
                                    <p className="text-slate-500 text-sm mb-4 max-w-sm">
                                        Hệ thống AI mới chỉ ghi nhận <strong className="text-blue-600">{profile.txCount || 0} giao dịch</strong> từ tài khoản này. 
                                        Risk Engine cần tối thiểu 50 giao dịch thực tế để thiết lập ma trận elip thói quen chuẩn xác.
                                    </p>
                                    <div className="w-full bg-slate-200 rounded-full h-2.5 max-w-xs">
                                        <div 
                                            className="bg-blue-500 h-2.5 rounded-full transition-all duration-500" 
                                            style={{ width: `${Math.min((Number(profile.txCount || 0) / 50) * 100, 100)}%` }}
                                        ></div>
                                    </div>
                                    <span className="text-xs text-slate-400 mt-2">Tiến độ thu thập: {profile.txCount || 0} / 50 mẫu</span>
                                </div>
                            ) : (
                                <>
                                    {/* Khối Điểm Rủi Ro Tổng Quát */}
                                    <div className={`p-6 rounded-2xl border ${getRiskColor(profile.anomalyLevel)} flex flex-col items-center justify-center text-center shadow-sm`}>
                                        {profile.anomalyLevel === 'NORMAL' && <CheckCircle size={40} className="mb-2" />}
                                        {profile.anomalyLevel === 'SUSPICIOUS' && <AlertTriangle size={40} className="mb-2" />}
                                        {profile.anomalyLevel === 'CRITICAL' && <ShieldAlert size={40} className="mb-2" />}
                                        
                                        <span className="text-sm font-bold uppercase tracking-wider opacity-80">Điểm bất thường (Anomaly Score)</span>
                                        <span className="text-5xl font-black mt-2">{Number(profile.anomalyScore || 0).toFixed(1)} <span className="text-xl font-normal opacity-60">/ 100</span></span>
                                        <span className="mt-2 font-bold text-sm tracking-wide bg-white/60 px-3 py-0.5 rounded-full border border-inherit">Trạng thái: {profile.anomalyLevel}</span>
                                    </div>

                                    {/* Biểu đồ mạng nhện phân phối rủi ro 4 chiều */}
                                    <div className="bg-slate-50 p-4 rounded-2xl border border-slate-100 h-64 shadow-inner">
                                        <h3 className="text-center font-bold text-slate-700 mb-2 text-xs uppercase tracking-wider">Bản đồ phân tán rủi ro 4 đặc trưng</h3>
                                        <ResponsiveContainer width="100%" height="100%">
                                            <RadarChart cx="50%" cy="50%" outerRadius="70%" data={radarData}>
                                                <PolarGrid stroke="#e2e8f0" />
                                                <PolarAngleAxis dataKey="subject" textAnchor="middle" tick={{ fill: '#475569', fontSize: 12, fontWeight: 700 }} />
                                                <PolarRadiusAxis angle={30} domain={[0, 100]} tick={false} axisLine={false} />
                                                <Radar name="Mức độ bất thường" dataKey="score" stroke="#4f46e5" fill="#4f46e5" fillOpacity={0.4} />
                                                <Tooltip contentStyle={{ borderRadius: '12px', border: 'none', boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1)' }} />
                                            </RadarChart>
                                        </ResponsiveContainer>
                                    </div>
                                </>
                            )}
                        </div>

                        {/* Cột Phải: Trạng thái máy học & Chỉ số thống kê hành vi */}
                        <div className="space-y-4">
                            {/* Khối Trạng thái ML */}
                            <div className="bg-white p-4 rounded-2xl border border-slate-100 shadow-sm">
                                <h3 className="font-bold text-slate-700 mb-3 border-b border-slate-100 pb-2 flex items-center gap-2"><Target size={18} className="text-blue-500"/> Kiến trúc trạng thái mô hình</h3>
                                <div className="grid grid-cols-2 gap-4 text-sm">
                                    <div>
                                        <p className="text-slate-500 mb-1 text-xs">Giai đoạn huấn luyện</p>
                                        <p className="font-bold text-blue-600 bg-blue-50 px-2 py-0.5 rounded text-xs w-fit">{profile.profilingPhase}</p>
                                    </div>
                                    <div>
                                        <p className="text-slate-500 mb-1 text-xs">Thuật toán đánh giá</p>
                                        <p className="font-bold text-purple-600 bg-purple-50 px-2 py-0.5 rounded text-xs w-fit">{profile.calculationMethod}</p>
                                    </div>
                                    <div>
                                        <p className="text-slate-500 mb-1 text-xs">Số lượng mẫu giao dịch</p>
                                        <p className="font-bold text-slate-800">{profile.txCount || 0} Bản ghi</p>
                                    </div>
                                    <div>
                                        <p className="text-slate-500 mb-1 text-xs">Độ tin cậy hồ sơ</p>
                                        <div className="flex items-center gap-2">
                                            <p className="font-bold text-slate-800">{(Number(profile.profileReliability || 0) * 100).toFixed(0)}%</p>
                                            <progress className="progress progress-primary w-16" value={Number(profile.profileReliability || 0) * 100} max="100"></progress>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {/* Khối Thói quen trung bình đã học */}
                            <div className="bg-white p-4 rounded-2xl border border-slate-100 shadow-sm relative overflow-hidden">
                                {isColdStart && (
                                    <div className="absolute inset-0 bg-white/75 backdrop-blur-[1px] z-10 flex items-center justify-center">
                                        <span className="bg-slate-900 text-white px-3 py-1.5 rounded-full shadow-md text-xs font-bold tracking-wide flex items-center gap-1.5 animate-bounce">
                                            ⚠️ Đang khóa: Chờ tích lũy đủ mẫu
                                        </span>
                                    </div>
                                )}
                                
                                <h3 className="font-bold text-slate-700 mb-3 border-b border-slate-100 pb-2 flex items-center gap-2"><Activity size={18} className="text-green-500"/> Chỉ số thói quen lõi</h3>
                                <ul className="space-y-4 text-sm">
                                    <li className="flex justify-between items-center">
                                        <span className="flex items-center gap-2 text-slate-600 font-medium"><DollarSign size={16} className="text-slate-400"/> Định mức chi tiêu TB</span>
                                        <div className="text-right">
                                            <p className="font-bold text-slate-800">{Number(profile.avgAmount || 0).toLocaleString('vi-VN')} ₫</p>
                                            <p className="text-xs text-slate-400">± {Number(profile.stdAmount || 0).toLocaleString('vi-VN')} ₫ (Phương sai biên)</p>
                                        </div>
                                    </li>
                                    <li className="flex justify-between items-center">
                                        <span className="flex items-center gap-2 text-slate-600 font-medium"><Clock size={16} className="text-slate-400"/> Khung giờ quen thuộc</span>
                                        <p className="font-bold text-slate-800">{profile.avgTransactionHour || 'N/A'}</p>
                                    </li>
                                    <li className="flex justify-between items-center">
                                        <span className="flex items-center gap-2 text-slate-600 font-medium"><Activity size={16} className="text-slate-400"/> Tần suất giữa các GD</span>
                                        <div className="text-right">
                                            <p className="font-bold text-slate-800">{Number(profile.avgGapHours || 0).toFixed(1)} giờ</p>
                                            <p className="text-xs text-slate-400">± {Number(profile.stdGapHours || 0).toFixed(1)} giờ (Độ giãn cách)</p>
                                        </div>
                                    </li>
                                </ul>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default UserBehaviorProfileModal;