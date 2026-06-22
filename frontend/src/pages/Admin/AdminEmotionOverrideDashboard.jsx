import React, { useState, useEffect, useCallback } from 'react';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import { useNavigate } from 'react-router-dom';

const AI_REAL = 'AI_REAL';

const MODES = [
  { value: AI_REAL,    label: 'AI Thật',  desc: 'Phân tích cảm xúc từ khuôn mặt thật', coercion: null,  wide: true },
  { value: 'FEAR',     label: 'Fear',     desc: null,                                    coercion: true,  wide: false },
  { value: 'STRESS',   label: 'Stress',   desc: null,                                    coercion: true,  wide: false },
  { value: 'ANGRY',    label: 'Angry',    desc: null,                                    coercion: true,  wide: false },
  { value: 'HAPPY',    label: 'Happy',    desc: null,                                    coercion: false, wide: false },
  { value: 'NEUTRAL',  label: 'Neutral',  desc: null,                                    coercion: false, wide: false },
  { value: 'SURPRISE', label: 'Surprise', desc: null,                                    coercion: false, wide: false },
  { value: 'SAD',      label: 'Sad',      desc: null,                                    coercion: false, wide: false },
  { value: 'DISGUST',  label: 'Disgust',  desc: null,                                    coercion: false, wide: false },
  { value: 'CALM',     label: 'Calm',     desc: null,                                    coercion: false, wide: false },
];

function modeStyle(mode, isSelected) {
  if (mode.value === AI_REAL) {
    return isSelected
      ? 'bg-emerald-600 border-emerald-600 text-white'
      : 'bg-white border-emerald-300 text-emerald-700 hover:border-emerald-500';
  }
  if (mode.coercion) {
    return isSelected
      ? 'bg-red-600 border-red-600 text-white'
      : 'bg-white border-gray-200 text-gray-600 hover:border-red-300';
  }
  return isSelected
    ? 'bg-[#1e2d40] border-[#1e2d40] text-white'
    : 'bg-white border-gray-200 text-gray-600 hover:border-gray-400';
}

function coercionWillFire(selectedMode, conf) {
  if (!selectedMode || selectedMode.value === AI_REAL) return false;
  if (!selectedMode.coercion) return false;
  if (selectedMode.value === 'FEAR') return conf >= 0.65;
  return true;
}

export default function AdminEmotionOverrideDashboard() {
  const navigate = useNavigate();

  const [liveStatus, setLiveStatus] = useState({ enabled: false, emotion: 'FEAR', confidence: 0.9 });
  const [selectedValue, setSelectedValue] = useState(AI_REAL);
  const [confidence, setConfidence]       = useState(0.9);
  const [loading, setLoading]             = useState(true);
  const [applying, setApplying]           = useState(false);

  const fetchStatus = useCallback(async () => {
    try {
      const res = await axiosClient.get('/admin/emotion-override/status');
      setLiveStatus(res.data);
      setSelectedValue(res.data.enabled ? res.data.emotion : AI_REAL);
      setConfidence(res.data.confidence);
    } catch {
      toast.error('Không thể tải trạng thái override.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchStatus(); }, [fetchStatus]);

  const handleApply = async () => {
    setApplying(true);
    try {
      if (selectedValue === AI_REAL) {
        await axiosClient.post('/admin/emotion-override/disable');
        toast.success('Đã trả về AI thật — cảm xúc được phát hiện từ khuôn mặt.');
      } else {
        await axiosClient.post('/admin/emotion-override/enable', null, {
          params: { emotion: selectedValue, confidence },
        });
        toast.success(`Override bật: ${selectedValue} (conf ${confidence.toFixed(2)})`);
      }
      await fetchStatus();
    } catch {
      toast.error('Áp dụng thất bại.');
    } finally {
      setApplying(false);
    }
  };

  const selectedMode    = MODES.find(m => m.value === selectedValue);
  const isAiReal        = selectedValue === AI_REAL;
  const willFire        = coercionWillFire(selectedMode, confidence);
  const isDirty         = isAiReal
    ? liveStatus.enabled
    : (!liveStatus.enabled || liveStatus.emotion !== selectedValue || Math.abs(liveStatus.confidence - confidence) > 0.001);

  if (loading) {
    return (
      <div className="min-h-screen bg-[#f4f6f9] flex items-center justify-center">
        <p className="text-sm font-medium text-gray-400 tracking-widest uppercase">Đang tải...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#f4f6f9]">

      {/* Topbar */}
      <header className="bg-[#1e2d40] px-8 py-4 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/admin')}
            className="flex items-center gap-2 px-3 py-1.5 bg-white/10 hover:bg-white/20 border border-white/15 text-white/70 hover:text-white text-xs font-medium rounded-lg transition-all"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
            </svg>
            Quay lại
          </button>
          <div>
            <p className="text-white font-semibold text-sm tracking-wide">Điều khiển cảm xúc AI</p>
            <p className="text-white/40 text-xs mt-0.5">Emotion Override · Dev / QA Tool</p>
          </div>
        </div>

        {/* Live status pill */}
        <div className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold border ${
          liveStatus.enabled
            ? 'bg-red-500/20 border-red-400/30 text-red-300'
            : 'bg-emerald-500/10 border-emerald-400/20 text-emerald-400'
        }`}>
          <span className={`w-1.5 h-1.5 rounded-full ${liveStatus.enabled ? 'bg-red-400 animate-pulse' : 'bg-emerald-400'}`} />
          {liveStatus.enabled ? `OVERRIDE · ${liveStatus.emotion} (${liveStatus.confidence.toFixed(2)})` : 'AI THẬT · ĐANG HOẠT ĐỘNG'}
        </div>
      </header>

      {/* Banner khi override đang bật */}
      {liveStatus.enabled && (
        <div className="bg-red-600 px-8 py-3 flex items-center gap-3">
          <svg className="w-4 h-4 text-white flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
          </svg>
          <p className="text-white text-xs font-medium">
            Override đang hoạt động — mọi giao dịch nhận cảm xúc&nbsp;
            <strong>{liveStatus.emotion}</strong> (conf {liveStatus.confidence.toFixed(2)}).
            Chọn <strong>AI Thật</strong> rồi bấm Áp dụng để trả về bình thường.
          </p>
        </div>
      )}

      <div className="max-w-5xl mx-auto p-8 pb-16 space-y-5">

        {/* Main config card */}
        <div className="bg-white rounded-xl border border-gray-100 p-6 space-y-6">
          <div className="flex items-center justify-between">
            <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest">Chọn chế độ cảm xúc</p>
            <span className="text-[10px] text-gray-400">
              Hiện tại:&nbsp;
              <span className={`font-semibold ${liveStatus.enabled ? 'text-red-600' : 'text-emerald-600'}`}>
                {liveStatus.enabled ? liveStatus.emotion : 'AI Thật'}
              </span>
            </span>
          </div>

          {/* Mode selector grid */}
          <div className="grid grid-cols-3 gap-2">

            {/* AI Thật — full width row */}
            {(() => {
              const m = MODES[0];
              const selected = selectedValue === m.value;
              return (
                <button
                  key={m.value}
                  onClick={() => setSelectedValue(m.value)}
                  className={`col-span-3 flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-semibold border-2 transition-all ${modeStyle(m, selected)}`}
                >
                  <svg className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2"
                      d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17H4a2 2 0 01-2-2V5a2 2 0 012-2h16a2 2 0 012 2v10a2 2 0 01-2 2h-1" />
                  </svg>
                  <span>{m.label}</span>
                  <span className={`text-xs font-normal ml-1 ${selected ? 'text-white/70' : 'text-emerald-500'}`}>
                    {m.desc}
                  </span>
                  {selected && (
                    <span className="ml-auto text-[10px] bg-white/20 px-2 py-0.5 rounded-full">✓ đã chọn</span>
                  )}
                </button>
              );
            })()}

            {/* Divider label */}
            <div className="col-span-3 flex items-center gap-3 py-1">
              <div className="flex-1 h-px bg-gray-100" />
              <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest">hoặc ghi đè thành</span>
              <div className="flex-1 h-px bg-gray-100" />
            </div>

            {/* Emotion buttons */}
            {MODES.slice(1).map(m => {
              const selected = selectedValue === m.value;
              return (
                <button
                  key={m.value}
                  onClick={() => setSelectedValue(m.value)}
                  className={`px-3 py-2.5 rounded-lg text-xs font-semibold border transition-all flex items-center justify-center gap-1.5 ${modeStyle(m, selected)}`}
                >
                  {m.label}
                  {m.coercion && (
                    <span className={`text-[9px] ${selected ? 'text-red-200' : 'text-red-400'}`}>⚠</span>
                  )}
                </button>
              );
            })}
          </div>

          {/* Confidence slider — chỉ hiện khi chọn emotion cụ thể */}
          {!isAiReal && (
            <div className="space-y-2 pt-1">
              <div className="flex items-center justify-between">
                <p className="text-xs font-medium text-gray-600">Confidence</p>
                <span className={`text-xs font-mono font-bold px-2 py-0.5 rounded ${
                  selectedValue === 'FEAR' && confidence < 0.65
                    ? 'bg-amber-100 text-amber-700'
                    : 'bg-gray-100 text-gray-700'
                }`}>
                  {confidence.toFixed(2)}
                </span>
              </div>

              {/* Track với marker 0.65 cho FEAR */}
              <div className="relative">
                <input
                  type="range"
                  min="0" max="1" step="0.01"
                  value={confidence}
                  onChange={e => setConfidence(parseFloat(e.target.value))}
                  className="w-full h-1.5 bg-gray-200 rounded-full appearance-none cursor-pointer accent-[#1e2d40]"
                />
                {selectedValue === 'FEAR' && (
                  <div
                    className="absolute top-0 bottom-0 w-px bg-amber-400 pointer-events-none"
                    style={{ left: '65%' }}
                  />
                )}
              </div>

              <div className="flex justify-between text-[10px] text-gray-400">
                <span>0.00</span>
                {selectedValue === 'FEAR' && (
                  <span className="text-amber-500 font-semibold">↑ 0.65 (threshold coercion)</span>
                )}
                <span>1.00</span>
              </div>

              {selectedValue === 'FEAR' && confidence < 0.65 && (
                <p className="text-[10px] text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 leading-relaxed">
                  FEAR với confidence &lt; 0.65 sẽ bị hạ thành UNKNOWN — giao dịch vẫn đi qua bình thường.
                  Kéo lên ≥ 0.65 để test luồng UNDER_REVIEW.
                </p>
              )}
            </div>
          )}

          {/* Preview bar + Apply button */}
          <div className="flex items-stretch gap-3 pt-1">
            <div className={`flex-1 flex items-center gap-2.5 px-4 py-3 rounded-xl text-xs font-medium border ${
              isAiReal
                ? 'bg-emerald-50 border-emerald-200 text-emerald-700'
                : willFire
                  ? 'bg-red-50 border-red-200 text-red-700'
                  : 'bg-blue-50 border-blue-200 text-blue-700'
            }`}>
              {isAiReal && (
                <>
                  <svg className="w-3.5 h-3.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  Hệ thống sẽ phân tích cảm xúc thật từ khuôn mặt người dùng
                </>
              )}
              {!isAiReal && willFire && (
                <>
                  <svg className="w-3.5 h-3.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
                  </svg>
                  Giao dịch sẽ bị đẩy vào UNDER_REVIEW (coercion detected)
                </>
              )}
              {!isAiReal && !willFire && (
                <>
                  <svg className="w-3.5 h-3.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  Giao dịch sẽ đi qua bình thường (cảm xúc an toàn)
                </>
              )}
            </div>

            <button
              onClick={handleApply}
              disabled={applying || !isDirty}
              className={`flex-shrink-0 px-6 py-3 rounded-xl text-xs font-bold text-white transition-all disabled:opacity-40 disabled:cursor-not-allowed ${
                isAiReal
                  ? 'bg-emerald-600 hover:bg-emerald-700'
                  : willFire
                    ? 'bg-red-600 hover:bg-red-700'
                    : 'bg-[#1e2d40] hover:bg-[#162233]'
              }`}
            >
              {applying
                ? 'Đang áp dụng...'
                : !isDirty
                  ? '✓ Đang áp dụng'
                  : 'Áp dụng'}
            </button>
          </div>
        </div>

        {/* Reference table */}
        <div className="bg-[#1e2d40] rounded-xl p-6 space-y-4">
          <p className="text-[10px] font-semibold text-white/40 uppercase tracking-widest flex items-center gap-2">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-400 inline-block" />
            Bảng tham chiếu — logic phản ứng hệ thống
          </p>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div className="bg-emerald-500/10 border border-emerald-400/20 rounded-xl p-4 space-y-2">
              <p className="text-xs font-semibold text-emerald-300">AI Thật (mặc định)</p>
              <p className="text-[11px] text-white/60 leading-relaxed">
                Gọi Python AI service để nhận diện cảm xúc từ sequence khuôn mặt. Kết quả thực tế.
              </p>
            </div>

            <div className="bg-red-500/10 border border-red-400/20 rounded-xl p-4 space-y-2">
              <p className="text-xs font-semibold text-red-300">Kích hoạt UNDER_REVIEW</p>
              <div className="space-y-1.5">
                <div className="flex justify-between text-[11px]">
                  <span className="font-mono text-white/70">FEAR</span>
                  <span className="text-red-300">conf ≥ 0.65</span>
                </div>
                <div className="flex justify-between text-[11px]">
                  <span className="font-mono text-white/70">STRESS</span>
                  <span className="text-red-300">luôn luôn</span>
                </div>
                <div className="flex justify-between text-[11px]">
                  <span className="font-mono text-white/70">ANGRY</span>
                  <span className="text-red-300">luôn luôn</span>
                </div>
                <div className="flex justify-between text-[11px]">
                  <span className="font-mono text-white/70">FEAR</span>
                  <span className="text-amber-300">conf &lt; 0.65 → UNKNOWN</span>
                </div>
              </div>
            </div>

            <div className="bg-blue-500/10 border border-blue-400/20 rounded-xl p-4 space-y-2">
              <p className="text-xs font-semibold text-blue-300">Đi qua bình thường</p>
              <div className="space-y-1">
                {['HAPPY', 'NEUTRAL', 'SURPRISE', 'SAD', 'DISGUST', 'CALM', 'UNKNOWN'].map(em => (
                  <div key={em} className="flex justify-between text-[11px]">
                    <span className="font-mono text-white/70">{em}</span>
                    <span className="text-blue-300">→ Voice OTP</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>
  );
}
