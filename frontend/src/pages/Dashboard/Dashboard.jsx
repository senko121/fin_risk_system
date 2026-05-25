 




import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import MyQRCodeModal from '../../components/MyQRCodeModal';

export default function Dashboard() {
  const navigate = useNavigate();
  
  // FIX: Lazy Initializer - Đọc đồng bộ ngay khi khởi tạo
  const [user, setUser] = useState(() => {
    const storedUser = localStorage.getItem('currentUser');
    return storedUser ? JSON.parse(storedUser) : null;
  });
  
  const [isQRModalOpen, setIsQRModalOpen] = useState(false);

  // FIX: Chỉ dùng useEffect để đá văng nếu không có user
  useEffect(() => {
    if (!user) {
      toast.error('🚨 Bạn chưa đăng nhập!');
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      navigate('/login');
    }
  }, [user, navigate]);

  if (!user) return null;

  const formatMoney = (amount) => {
    if (amount == null || isNaN(amount)) return '0 ₫';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
  };

  const formatAccountNumber = (acc) => {
    if (!acc) return 'Đang cập nhật';
    return String(acc).replace(/(.{4})/g, '$1 ').trim();
  };

  return (
    <div className="w-full pb-10">
      {/* ── NAVBAR ĐÃ ĐƯỢC CẮT SANG MAINLAYOUT ── */}

      {/* ── BODY ── */}
      <div className="max-w-2xl mx-auto px-6 py-8">

        {/* Greeting */}
        <p className="font-black text-xs uppercase tracking-widest mb-1" style={{ color: '#94a3b8', letterSpacing: '0.12em' }}>
          Xin chào trở lại
        </p>
        <h1 className="font-black text-xl mb-6" style={{ color: '#0d1b2a' }}>
          {user.fullName}
        </h1>

        {/* ── BALANCE CARD ── */}
        <div
          className="relative overflow-hidden mb-5"
          style={{ background: '#0d1b2a', borderRadius: '2rem', padding: '28px 28px 24px' }}
        >
          {/* Decorative circles */}
          <div style={{
            position: 'absolute', width: 240, height: 240, borderRadius: '50%',
            background: 'rgba(26,86,219,0.18)', bottom: -90, right: -70, zIndex: 0,
          }} />
          <div style={{
            position: 'absolute', width: 120, height: 120, borderRadius: '50%',
            background: 'rgba(255,255,255,0.04)', top: -40, right: 90, zIndex: 0,
          }} />

          {/* Card content */}
          <div style={{ position: 'relative', zIndex: 1 }}>
            {/* Top row */}
            <div className="flex items-start justify-between mb-2">
              <span
                className="font-black text-xs uppercase tracking-widest"
                style={{ color: 'rgba(255,255,255,0.38)', letterSpacing: '0.18em' }}
              >
                Số dư hiện tại
              </span>
              <button
                onClick={() => setIsQRModalOpen(true)}
                className="flex items-center justify-center transition-all"
                style={{
                  width: 36, height: 36,
                  background: 'rgba(255,255,255,0.09)',
                  border: '1px solid rgba(255,255,255,0.12)',
                  borderRadius: 10,
                  cursor: 'pointer',
                }}
                onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.16)'}
                onMouseLeave={e => e.currentTarget.style.background = 'rgba(255,255,255,0.09)'}
                title="Hiển thị mã QR nhận tiền"
              >
                <svg className="w-[18px] h-[18px]" fill="none" stroke="rgba(255,255,255,0.7)" strokeWidth="2" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
                </svg>
              </button>
            </div>

            {/* Amount */}
            <h2
              className="font-black mb-8"
              style={{ fontSize: 36, color: 'white', letterSpacing: '-0.5px', lineHeight: 1.15 }}
            >
              {formatMoney(user.balance)}
            </h2>

            {/* Footer row */}
            <div className="flex items-end justify-between">
              <div>
                <p
                  className="font-black text-[9px] uppercase mb-1"
                  style={{ color: 'rgba(255,255,255,0.3)', letterSpacing: '0.12em' }}
                >
                  Số tài khoản
                </p>
                <p
                  className="font-mono font-semibold"
                  style={{ fontSize: 16, letterSpacing: '0.18em', color: 'rgba(255,255,255,0.85)' }}
                >
                  {formatAccountNumber(user.accountNumber)}
                </p>
              </div>
              <div
                className="font-black text-[10px] uppercase"
                style={{
                  background: '#1a56db',
                  color: 'rgba(255,255,255,0.9)',
                  padding: '6px 14px',
                  borderRadius: 8,
                  letterSpacing: '0.06em',
                }}
              >
                Global Class
              </div>
            </div>
          </div>
        </div>

        {/* ── ACTION BUTTONS ── */}
        <p className="font-black text-[10px] uppercase tracking-widest mb-3" style={{ color: '#94a3b8', letterSpacing: '0.12em' }}>
          Chức năng chính
        </p>
        <div className="grid grid-cols-4 gap-3 mb-5">

          {/* Chuyển tiền */}
          <ActionBtn
            onClick={() => navigate('/transfer')}
            iconBg="#e8f0fd" iconColor="#1a56db"
            label="Chuyển tiền"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
            </svg>
          </ActionBtn>

          {/* Lịch sử */}
          <ActionBtn
            onClick={() => navigate('/history')}
            iconBg="#e8f7f0" iconColor="#059669"
            label="Lịch sử GD"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" />
            </svg>
          </ActionBtn>

          {/* Chi tiêu */}
          <ActionBtn
            onClick={() => navigate('/analytics')}
            iconBg="#f0eeff" iconColor="#7c3aed"
            label="Chi tiêu"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
          </ActionBtn>

          {/* Bảo mật */}
          <ActionBtn
            onClick={() => navigate('/security')}
            iconBg="#f1f4f8" iconColor="#475569"
            label="Bảo mật"
          >
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
            </svg>
          </ActionBtn>
        </div>

        {/* ── COMING SOON ROW ── */}
        <div
          className="flex items-center gap-3 p-4 mb-5"
          style={{
            background: 'white',
            borderRadius: 16,
            border: '1px solid #e8ecf2',
            opacity: 0.5,
          }}
        >
          <div
            className="flex items-center justify-center"
            style={{ width: 40, height: 40, borderRadius: 12, background: '#f5f5f5', color: '#9ca3af' }}
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
            </svg>
          </div>
          <div>
            <p className="font-bold text-sm" style={{ color: '#64748b' }}>Tính năng sắp ra mắt</p>
            <p className="text-xs" style={{ color: '#94a3b8' }}>Coming soon — đang phát triển</p>
          </div>
        </div>

        {/* ── SECURITY STATUS BAR ── */}
        <div
          className="flex items-center justify-between px-5 py-4"
          style={{ background: '#0d1b2a', borderRadius: 16 }}
        >
          <div className="flex items-center gap-3">
            <div
              className="flex items-center justify-center"
              style={{
                width: 38, height: 38, borderRadius: 10,
                background: 'rgba(26,86,219,0.25)',
                color: '#93b4f8',
              }}
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18" />
              </svg>
            </div>
            <div>
              <p className="font-bold text-xs" style={{ color: 'white' }}>AI Risk Monitor đang hoạt động</p>
              <p className="text-[10px]" style={{ color: 'rgba(255,255,255,0.38)' }}>Không phát hiện giao dịch bất thường</p>
            </div>
          </div>
          <div className="flex gap-2">
            <span
              className="font-black text-[9px] uppercase"
              style={{ background: 'rgba(74,222,128,0.15)', color: '#4ade80', padding: '4px 9px', borderRadius: 6, letterSpacing: '0.06em' }}
            >
              FaceID
            </span>
            <span
              className="font-black text-[9px] uppercase"
              style={{ background: 'rgba(147,180,248,0.15)', color: '#93b4f8', padding: '4px 9px', borderRadius: 6, letterSpacing: '0.06em' }}
            >
              PIN
            </span>
          </div>
        </div>

      </div>

      {/* ── QR MODAL ── */}
      <MyQRCodeModal
        isOpen={isQRModalOpen}
        onClose={() => setIsQRModalOpen(false)}
        user={user}
      />
    </div>
  );
}

/* ── ActionBtn helper component ── */
function ActionBtn({ onClick, iconBg, iconColor, label, children }) {
  const [hovered, setHovered] = React.useState(false);

  return (
    <button
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      className="flex flex-col items-center gap-3 py-5 px-2 transition-all cursor-pointer"
      style={{
        background: 'white',
        borderRadius: 18,
        border: hovered ? '1px solid #0d1b2a' : '1px solid #e8ecf2',
      }}
    >
      <div
        className="flex items-center justify-center transition-all"
        style={{
          width: 46, height: 46,
          borderRadius: 13,
          background: hovered ? '#0d1b2a' : iconBg,
          color: hovered ? 'white' : iconColor,
        }}
      >
        {children}
      </div>
      <span
        className="font-bold text-xs text-center leading-tight"
        style={{ color: hovered ? '#0d1b2a' : '#334155' }}
      >
        {label}
      </span>
    </button>
  );
}







 