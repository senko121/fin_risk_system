 

import React, { useMemo } from 'react';
import { Radar, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, ResponsiveContainer, Tooltip as ChartTooltip, Legend } from 'recharts';

export default function AdminTransactionDetailModal({ isOpen, onClose, transaction, formatMoney, onResolve = null, isResolving = false }) {
  if (!isOpen || !transaction) return null;

  // ── Lấy điểm từ data thật ──────────────
  const finalScore    = transaction.totalRiskScore ?? 0;
  const ruleScore     = transaction.ruleScore      ?? 0;
  const behaviorScore = transaction.behaviorScore  ?? 0;

  // ── Normalize AI Insights từ backend ───────────────────
  const rawInsights = transaction.aiInsights ?? [];
  const insights = rawInsights.length > 0
    ? rawInsights.map(i => ({
        type:        (i.type ?? 'WARNING').toLowerCase(),   
        feature:     i.feature    ?? '',
        text:        i.message    ?? '',
        score:       parseFloat(i.anomalyScore) || 0,
      }))
    : buildFallbackInsights(transaction, behaviorScore);

  // 🚀 TẠO DATA CHO BIỂU ĐỒ 2 MÀNG NHỆN (RADAR CHART)
  const radarData = useMemo(() => {
    // 1. Khởi tạo Baseline mặc định (Z=1.5 -> 50%) nếu Backend chưa gửi Profile
    let bAmt = 50, bTime = 50, bFreq = 50, bRec = 50;
    const pb = transaction.profileBaseline;

    // 2. Tính toán Vùng An Toàn (Baseline) cá nhân hóa từ ewmaVariance
    if (pb) {
      // Hàm ánh xạ: min(CV / maxCV * 60, 60) + 15 (Giới hạn vùng 15% -> 75%)
      bAmt  = Math.min(((pb.amountCV || 0) / 2.0) * 60, 60) + 15;
      bTime = (pb.circularVariance || 0) * 60 + 15;
      bFreq = Math.min(((pb.frequencyCV || 0) / 2.0) * 60, 60) + 15;
      bRec  = (pb.noveltyRate || 0) * 60 + 15;
    }

    // 3. Khởi tạo cấu trúc Data với 2 mốc: Baseline (Gốc) và Current (Hiện tại)
    const defaultData = [
      { subject: 'SỐ TIỀN', Baseline: bAmt, Current: 0, fullMark: 100 },
      { subject: 'THỜI GIAN', Baseline: bTime, Current: 0, fullMark: 100 },
      { subject: 'TẦN SUẤT', Baseline: bFreq, Current: 0, fullMark: 100 },
      { subject: 'NGƯỜI NHẬN', Baseline: bRec, Current: 0, fullMark: 100 },
    ];

    if (rawInsights.length === 0) return defaultData;

    // 4. Ánh xạ Z-Score (0 -> 3.0) thành điểm Hiện tại (0 -> 100)
    insights.forEach(ins => {
      let normalizedScore = Math.min((ins.score / 3.0) * 100, 100);
      
      if (ins.feature === 'AMOUNT') defaultData[0].Current = normalizedScore;
      if (ins.feature === 'TIME') defaultData[1].Current = normalizedScore;
      if (ins.feature === 'FREQUENCY') defaultData[2].Current = normalizedScore;
      if (ins.feature === 'RECIPIENT') defaultData[3].Current = normalizedScore;
    });

    return defaultData;
  }, [insights, rawInsights.length, transaction.profileBaseline]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/80 backdrop-blur-md">
      <style>{`
        .hide-sb::-webkit-scrollbar { display: none; }
        .hide-sb { -ms-overflow-style: none; scrollbar-width: none; }
      `}</style>

      <div className="absolute inset-0" onClick={onClose} />

      <div className="relative bg-white rounded-[2rem] w-full max-w-6xl shadow-2xl flex flex-col max-h-[95vh] overflow-hidden animate-fade-in">

        {/* ── HEADER ─────────────────────────────────────── */}
        <div className="flex-shrink-0 flex justify-between items-center px-6 py-4 border-b border-slate-100 bg-white z-10 shadow-sm">
          <div>
            <h3 className="text-xl font-black text-slate-800 uppercase tracking-tight flex items-center gap-2">
              Hồ sơ Phân tích Rủi ro
              {transaction.status === 'UNDER_REVIEW' && (
                <span className="bg-amber-100 text-amber-700 text-[10px] px-2 py-0.5 rounded-full animate-pulse">CẦN DUYỆT</span>
              )}
            </h3>
            <p className="text-xs font-mono font-bold text-slate-500 mt-1">
              MÃ GIAO DỊCH: <span className="text-blue-600 bg-blue-50 px-1.5 rounded">#{transaction.id}</span>
            </p>
          </div>
          <button onClick={onClose} className="w-10 h-10 flex items-center justify-center rounded-full bg-slate-50 hover:bg-rose-50 text-slate-400 hover:text-rose-500 transition-all border border-transparent hover:border-rose-100">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* 🚀 CHIA ĐÔI MÀN HÌNH */}
        <div className="flex-1 overflow-y-auto hide-sb flex flex-col lg:flex-row bg-slate-50/50">
          
          {/* 🟦 CỘT TRÁI: DỮ LIỆU SỐ & RULES */}
          <div className="lg:w-4/12 p-6 border-r border-slate-100 space-y-6">
            
            <div className="text-center bg-white p-6 rounded-3xl shadow-sm border border-slate-200 relative overflow-hidden">
              <div className={`absolute top-0 left-0 w-full h-1.5 ${finalScore >= 70 ? 'bg-rose-500' : finalScore >= 40 ? 'bg-amber-500' : 'bg-emerald-500'}`} />
              <p className="text-xs font-black text-slate-400 uppercase tracking-widest mb-2">Số tiền giao dịch</p>
              <p className="text-4xl font-black text-slate-900 tracking-tight">{formatMoney(transaction.amount)}</p>
              
              <div className="flex justify-center gap-2 mt-4">
                <span className={`px-3 py-1 text-[11px] font-black uppercase rounded-lg border ${
                  transaction.status === 'SUCCESS' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' :
                  transaction.status === 'PENDING' || transaction.status === 'UNDER_REVIEW' ? 'bg-amber-50 text-amber-700 border-amber-200' :
                  'bg-rose-50 text-rose-700 border-rose-200'
                }`}>
                  {transaction.status}
                </span>
                <span className="px-3 py-1 bg-slate-900 text-white text-[11px] font-black uppercase rounded-lg shadow-md">
                  ĐIỂM RỦI RO: {finalScore}/100
                </span>
              </div>
            </div>

            <div>
              <h5 className="text-xs font-black text-slate-700 uppercase tracking-widest mb-3 flex items-center gap-2">
                <span className="w-1.5 h-1.5 bg-blue-500 rounded-full" /> Thông tin cơ bản
              </h5>
              <div className="bg-white rounded-2xl border border-slate-200 divide-y divide-slate-100 text-sm shadow-sm">
                {[
                  { label: 'Thời gian', value: new Date(transaction.createdAt).toLocaleString('vi-VN') },
                  { label: 'Người gửi', value: `${transaction.senderFullName}`, sub: transaction.senderAccountNumber },
                  { label: 'Người nhận', value: `${transaction.recipientFullName || 'Ngoài hệ thống'}`, sub: transaction.toAccountNumber },
                  { label: 'IP / Thiết bị', value: transaction.locationIp, sub: "Đã xác thực" },
                ].map(({ label, value, sub }) => (
                  <div key={label} className="flex justify-between px-5 py-3.5 gap-4">
                    <span className="text-slate-500 font-bold flex-shrink-0">{label}</span>
                    <div className="text-right">
                      <p className="font-black text-slate-800 truncate">{value}</p>
                      {sub && <p className="text-[10px] font-mono text-slate-400">{sub}</p>}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Category breakdown */}
            <CategoryBreakdown
              breakdown={transaction.categoryBreakdown}
              aiContribution={transaction.aiContribution}
            />

            <div>
              <h5 className="text-xs font-black text-slate-700 uppercase tracking-widest mb-3 flex items-center gap-2">
                <span className="w-1.5 h-1.5 bg-amber-500 rounded-full" />
                Luật tĩnh vi phạm
              </h5>
              {transaction.violatedRules?.length > 0 ? (
                <ul className="space-y-2 max-h-[200px] overflow-y-auto hide-sb pr-1">
                  {transaction.violatedRules.map((rule, idx) => (
                    <li key={idx} className="flex items-start gap-2 text-xs bg-white px-4 py-3 rounded-2xl border border-slate-200 shadow-sm">
                      <span className="mt-0.5">⚠️</span>
                      <span className="font-bold text-slate-700 leading-relaxed">{rule}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="bg-emerald-50 text-emerald-700 px-4 py-4 rounded-2xl border border-emerald-100 text-sm font-bold flex items-center justify-center shadow-sm">
                  <span className="mr-2">✨</span> Giao dịch thỏa mãn các luật tĩnh
                </div>
              )}
            </div>
          </div>

          {/* 🟪 CỘT PHẢI: BIỂU ĐỒ 2 LỚP VÀ AI INSIGHTS */}
          <div className="lg:w-8/12 p-6 space-y-6 bg-white">
            
            <div className="flex justify-between items-end mb-2">
              <h5 className="text-sm font-black text-slate-800 uppercase tracking-widest flex items-center gap-2">
                <span className="w-1.5 h-5 bg-indigo-500 rounded-full" />
                Giao Dịch Hiện Tại vs Thói Quen Gốc
              </h5>
              <span className="text-xs font-bold text-slate-500 bg-slate-100 px-2 py-1 rounded-lg">
                Lệch thói quen: {behaviorScore}%
              </span>
            </div>

            <div className="flex flex-col xl:flex-row gap-6">
              {/* 🚀 BIỂU ĐỒ 2 MÀNG NHỆN */}
              <div className="bg-slate-900 rounded-3xl p-2 flex items-center justify-center h-[320px] xl:w-7/12 shadow-xl relative overflow-hidden">
                <div className="absolute inset-0 opacity-10" style={{ backgroundImage: 'radial-gradient(#fff 1px, transparent 1px)', backgroundSize: '20px 20px' }}></div>
                
                <ResponsiveContainer width="100%" height="100%">
                  <RadarChart cx="50%" cy="50%" outerRadius="65%" data={radarData}>
                    <PolarGrid stroke="#334155" strokeDasharray="3 3" />
                    <PolarAngleAxis dataKey="subject" tick={{ fill: '#e2e8f0', fontSize: 10, fontWeight: '900', letterSpacing: '1px' }} />
                    <PolarRadiusAxis angle={30} domain={[0, 100]} tick={false} axisLine={false} />
                    
                    <ChartTooltip 
                      contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: '12px', color: '#fff', fontSize: '12px' }}
                      formatter={(value, name) => [
                        `${Math.round(value)}% ${name === 'Baseline' ? '(Ngưỡng)' : '(Độ lệch)'}`, 
                        name === 'Baseline' ? 'Vùng An Toàn' : 'Giao Dịch Này'
                      ]} 
                    />
                    
                    <Legend wrapperStyle={{ fontSize: '11px', fontWeight: 'bold', color: '#cbd5e1' }} />
                    
                    {/* LỚP 1: Màng nhện Xanh (Thói quen gốc) */}
                    <Radar name="Vùng An Toàn (Gốc)" dataKey="Baseline" stroke="#10b981" strokeWidth={2} strokeDasharray="5 5" fill="#10b981" fillOpacity={0.2} />
                    
                    {/* LỚP 2: Màng nhện Đỏ (Giao dịch hiện tại) */}
                    <Radar name="Giao Dịch Hiện Tại" dataKey="Current" stroke="#f43f5e" strokeWidth={3} fill="#f43f5e" fillOpacity={0.6} />
                  </RadarChart>
                </ResponsiveContainer>
              </div>

              {/* ── BẢNG THÔNG SỐ GỐC ──────────────── */}
              <div className="xl:w-5/12 bg-slate-50 border border-slate-100 rounded-3xl p-5 shadow-sm">
                <h6 className="text-xs font-black text-emerald-600 uppercase tracking-widest mb-4 flex items-center gap-2">
                  <span className="w-2 h-2 bg-emerald-500 rounded-full animate-pulse" />
                  Hồ sơ thói quen gốc
                </h6>
                
                {transaction.profileBaseline ? (
                  <div className="space-y-4">
                    <div className="grid grid-cols-2 gap-3">
                      <div className="bg-white p-3 rounded-2xl border border-slate-200">
                        <p className="text-[9px] font-bold text-slate-400 uppercase">Mức tiền TB</p>
                        <p className="text-sm font-black text-slate-800">{transaction.profileBaseline.avgAmount}</p>
                        <p className="text-[10px] text-slate-500">{transaction.profileBaseline.stdAmount}</p>
                      </div>
                      <div className="bg-white p-3 rounded-2xl border border-slate-200">
                        <p className="text-[9px] font-bold text-slate-400 uppercase">Khung giờ TB</p>
                        <p className="text-sm font-black text-slate-800">{transaction.profileBaseline.avgHour}</p>
                      </div>
                      <div className="bg-white p-3 rounded-2xl border border-slate-200">
                        <p className="text-[9px] font-bold text-slate-400 uppercase">Tần suất</p>
                        <p className="text-sm font-black text-slate-800">{transaction.profileBaseline.avgGapHours}</p>
                      </div>
                      <div className="bg-white p-3 rounded-2xl border border-slate-200">
                        <p className="text-[9px] font-bold text-slate-400 uppercase">% Người lạ</p>
                        <p className="text-sm font-black text-slate-800">{transaction.profileBaseline.noveltyRate}</p>
                      </div>
                    </div>
                    <div className="text-[10px] font-bold text-slate-400 text-center">
                      Số liệu học từ {transaction.profileBaseline.txCount} giao dịch ({transaction.profileBaseline.phase})
                    </div>
                  </div>
                ) : (
                  <div className="flex h-[150px] items-center justify-center text-center px-4 border border-dashed border-slate-300 rounded-2xl">
                    <p className="text-xs font-bold text-slate-400">Chưa có đủ dữ liệu thói quen (Cold Start) để thiết lập Vùng an toàn.</p>
                  </div>
                )}
              </div>
            </div>

            {/* ── AI INSIGHTS ──────────────── */}
            <div>
              <ul className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {insights.filter(i => i.feature !== 'BEHAVIOR_SCORE').map((insight, idx) => (
                  <InsightRow key={idx} insight={insight} />
                ))}
              </ul>
            </div>
            
          </div>
        </div>

        {/* ── FOOTER ─────────────────────────────────────── */}
        <div className="flex-shrink-0 px-6 py-5 bg-white border-t border-slate-100 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            {transaction.status === 'UNDER_REVIEW' && onResolve && (
              <>
                <button
                  onClick={() => onResolve(transaction.id, 'APPROVE')}
                  disabled={isResolving}
                  className="px-6 py-3 bg-emerald-500 hover:bg-emerald-600 text-white font-black rounded-xl transition-all shadow-lg text-sm uppercase tracking-widest disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {isResolving ? 'Đang xử lý...' : '✓ Duyệt Giao Dịch'}
                </button>
                <button
                  onClick={() => onResolve(transaction.id, 'REJECT_FRAUD')}
                  disabled={isResolving}
                  className="px-6 py-3 bg-rose-500 hover:bg-rose-600 text-white font-black rounded-xl transition-all shadow-lg text-sm uppercase tracking-widest disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {isResolving ? 'Đang xử lý...' : '✗ Từ Chối — Gian Lận'}
                </button>
              </>
            )}
          </div>
          <button onClick={onClose} className="px-10 py-3 bg-slate-900 hover:bg-black text-white font-black rounded-xl transition-all shadow-lg hover:shadow-xl hover:-translate-y-0.5 text-sm uppercase tracking-widest">
            Đóng bảng phân tích
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Sub-component: Một dòng insight ─────────────────────
function InsightRow({ insight }) {
  const isDANGER  = insight.type === 'danger';
  const isWARNING = insight.type === 'warning';

  const bg    = isDANGER ? 'bg-rose-50 border-rose-100'
              : isWARNING ? 'bg-amber-50 border-amber-100'
              : 'bg-emerald-50 border-emerald-100';
  const text  = isDANGER ? 'text-rose-900'
              : isWARNING ? 'text-amber-900'
              : 'text-emerald-900';
  const iconBg = isDANGER ? 'bg-rose-500' : isWARNING ? 'bg-amber-500' : 'bg-emerald-500';

  return (
    <li className={`flex flex-col sm:flex-row sm:items-start gap-3 p-4 rounded-2xl border ${bg} transition-all shadow-sm`}>
      <div className="flex items-center justify-between sm:flex-col sm:items-start gap-2 min-w-[100px]">
        <span className="text-[10px] font-black text-slate-600 uppercase bg-white/80 px-2 py-1 rounded-md border border-slate-200/50 shadow-sm">
          {insight.feature}
        </span>
        <span className={`text-[11px] font-black px-2.5 py-1 rounded-md shadow-sm text-white ${iconBg}`}>
          Z = {insight.score.toFixed(2)}
        </span>
      </div>
      <div className="flex-1 mt-1 sm:mt-0">
        <span className={`font-bold text-sm ${text} leading-relaxed`}>{insight.text}</span>
      </div>
    </li>
  );
}

const CATEGORY_META = {
  DEVICE:     { label: 'Thiết bị',  color: '#3b82f6' },
  FINANCIAL:  { label: 'Tài chính', color: '#f59e0b' },
  BIOMETRIC:  { label: 'Sinh trắc', color: '#10b981' },
  VELOCITY:   { label: 'Tần suất',  color: '#ef4444' },
  CONTEXTUAL: { label: 'Bối cảnh',  color: '#8b5cf6' },
  COMPOSITE:  { label: 'Tổng hợp',  color: '#06b6d4' },
};
const AI_COLOR = '#ec4899';

function CategoryBreakdown({ breakdown, aiContribution }) {
  if (!breakdown || Object.keys(breakdown).length === 0) return null;

  const ai = aiContribution ?? 0;

  // Build segments, sort descending by value — AI always after categories
  const catSegments = Object.entries(breakdown)
    .filter(([, d]) => d.effective > 0)
    .map(([cat, d]) => ({
      key: cat,
      label: CATEGORY_META[cat]?.label ?? cat,
      color: CATEGORY_META[cat]?.color ?? '#94a3b8',
      value: d.effective,
    }))
    .sort((a, b) => b.value - a.value);

  const segments = ai > 0
    ? [...catSegments, { key: 'AI', label: 'AI hành vi', color: AI_COLOR, value: ai }]
    : catSegments;

  const total = segments.reduce((s, seg) => s + seg.value, 0);
  const safe  = Math.max(0, 100 - total);

  return (
    <div>
      <h5 className="text-xs font-black text-slate-700 uppercase tracking-widest mb-3 flex items-center gap-2">
        <span className="w-1.5 h-1.5 bg-indigo-500 rounded-full" />
        Cấu thành điểm rủi ro
      </h5>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4 space-y-3">
        {/* Stacked bar */}
        <div className="flex h-5 rounded-full overflow-hidden gap-px">
          {segments.map(seg => (
            <div
              key={seg.key}
              title={`${seg.label}: ${seg.value}đ`}
              style={{ width: `${seg.value}%`, backgroundColor: seg.color }}
            />
          ))}
          {safe > 0 && (
            <div style={{ width: `${safe}%` }} className="bg-slate-100" />
          )}
        </div>

        {/* Score label below bar */}
        <div className="flex justify-between text-[10px] font-bold text-slate-500">
          <span>0</span>
          <span className="text-slate-800">{total}<span className="text-slate-400">/100đ rủi ro</span></span>
          <span>100</span>
        </div>

        {/* Legend */}
        <div className="flex flex-wrap gap-x-3 gap-y-1.5 pt-1 border-t border-slate-100">
          {segments.map(seg => (
            <div key={seg.key} className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-sm flex-shrink-0" style={{ backgroundColor: seg.color }} />
              <span className="text-[11px] font-bold text-slate-600">
                {seg.label} <span className="font-mono text-slate-400">{seg.value}đ</span>
              </span>
            </div>
          ))}
          {safe > 0 && (
            <div className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-sm bg-slate-100 border border-slate-200 flex-shrink-0" />
              <span className="text-[11px] font-bold text-slate-400">An toàn {safe}đ</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function buildFallbackInsights(transaction, behaviorScore) {
  const insights = [];
  if (behaviorScore >= 70) {
    insights.push({ type: 'danger', feature: 'BEHAVIOR', text: `Điểm hành vi ${behaviorScore}/100 — Giao dịch rất bất thường so với lịch sử.`, score: 3.0 });
  } else if (behaviorScore >= 40) {
    insights.push({ type: 'warning', feature: 'BEHAVIOR', text: `Điểm hành vi ${behaviorScore}/100 — Giao dịch có dấu hiệu chệch khỏi thói quen.`, score: 1.5 });
  } else {
    insights.push({ type: 'safe', feature: 'BEHAVIOR', text: `Điểm hành vi ${behaviorScore}/100 — Giao dịch an toàn, phù hợp thói quen.`, score: 0.5 });
  }
  return insights;
}