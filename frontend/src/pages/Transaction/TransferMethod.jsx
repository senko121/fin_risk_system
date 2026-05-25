 




import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axiosClient from '../../api/axiosClient';
import { toast } from 'react-toastify';
import QRCodeScanner from '../../components/QRCodeScanner';
import { useHeaderOverride } from '../../context/HeaderContext';

export default function TransferMethod() {
  const navigate = useNavigate();
  useHeaderOverride('Chuyển tiền', 'Tạo lệnh giao dịch an toàn', () => navigate('/dashboard'));
  
  // Lấy user đồng bộ ngay lúc khởi tạo
  const [currentUser] = useState(() => {
    const stored = localStorage.getItem('currentUser');
    return stored ? JSON.parse(stored) : null;
  });

  const [recentRecipients, setRecentRecipients] = useState([]);
  const [favorites, setFavorites] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [showScanner, setShowScanner] = useState(false);

  useEffect(() => {
    if (!currentUser) {
      navigate('/login');
      return;
    }
    
    const fetchData = async (userId) => {
      setLoading(true);
      try {
        const [recentRes, favoriteRes] = await Promise.all([
          axiosClient.get(`/transactions/recent-recipients/${userId}`),
          axiosClient.get(`/users/${userId}/contacts`),
        ]);
        setRecentRecipients(recentRes.data);
        setFavorites(favoriteRes.data);
      } catch (error) {
        console.error('Lỗi load dữ liệu:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchData(currentUser.id || currentUser.userId);
  }, [currentUser, navigate]); // FIX: Bổ sung dependencies chuẩn của React

  const handleQuickTransfer = (accountNumber) => {
    navigate('/transfer/stk', { state: { targetAccount: accountNumber } });
  };

  const handleQRSuccess = (decodedText) => {
    if (decodedText && decodedText.startsWith('FINRISK|')) {
      const parts = decodedText.split('|');
      const accountNumber = parts[1];
      setShowScanner(false);
      toast.success('✅ Đã nhận diện mã QR thành công!');
      handleQuickTransfer(accountNumber);
    } else {
      toast.error('❌ Mã QR không hợp lệ hoặc không thuộc hệ thống FinRisk!');
    }
  };

  const filteredRecent = recentRecipients.filter(
    (person) =>
      person.fullName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      person.accountNumber.includes(searchTerm)
  );

  const filteredFavorites = favorites.filter(
    (contact) =>
      contact.contactName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      contact.contactAccountNumber.includes(searchTerm)
  );

  return (
    <div className="w-full relative pb-10">

      {/* ── BODY ── */}
      <div className="max-w-2xl mx-auto px-5 py-6">
        {/* Search Input */}
        <div className="relative mb-6 flex justify-center"> 
          <div className="relative w-full max-w-[450px]"> 
            <div className="absolute inset-y-0 left-4 flex items-center pointer-events-none" style={{ color: '#94a3b8' }}>
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
            <input
              type="text"
              placeholder="Tìm tên hoặc STK..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full outline-none font-bold text-xs transition-all focus:border-blue-400 focus:ring-1 focus:ring-blue-100" 
              style={{
                paddingLeft: 40, paddingRight: searchTerm ? 40 : 16, paddingTop: 12, paddingBottom: 12, 
                background: 'white', border: '1px solid #e2e8f0', borderRadius: 14,
                color: '#1e293b', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.05)'  
              }}
            />
            {searchTerm && (
              <button
                onClick={() => setSearchTerm('')}
                className="absolute inset-y-0 right-4 flex items-center transition-opacity hover:opacity-70"
                style={{ color: '#94a3b8', background: 'none', border: 'none', cursor: 'pointer' }}
              >
                <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                </svg>
              </button>
            )}
          </div>
        </div>

        {/* 1. PHƯƠNG THỨC CHUYỂN */}
        <p className="font-black text-[10px] uppercase mb-3" style={{ color: '#94a3b8', letterSpacing: '0.12em' }}>
          Phương thức chuyển
        </p>
        <div className="grid grid-cols-3 gap-3 mb-7">
          <MethodCard
            onClick={() => navigate('/transfer/stk')}
            bg="#0d1b2a" accentBg="rgba(26,86,219,0.25)" accentColor="#93b4f8"
            tag="Đến số" label="Tài khoản"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" /></svg>
          </MethodCard>
          <MethodCard
            bg="#1a56db" accentBg="rgba(255,255,255,0.15)" accentColor="rgba(255,255,255,0.9)"
            tag="Đến số" label="Thẻ ngân hàng"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" /></svg>
          </MethodCard>
          <MethodCard
            onClick={() => setShowScanner(true)}
            bg="#0f6e56" accentBg="rgba(255,255,255,0.15)" accentColor="rgba(255,255,255,0.9)"
            tag="Quét mã" label="QR Code"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" /></svg>
          </MethodCard>
        </div>

        {/* 2. GIAO DỊCH GẦN ĐÂY */}
        <div className="mb-7">
          <p className="font-black text-[10px] uppercase mb-4" style={{ color: '#94a3b8', letterSpacing: '0.12em' }}>
            Giao dịch gần đây
          </p>
          <div className="flex gap-5 overflow-x-auto pb-2" style={{ scrollbarWidth: 'none' }}>
            {loading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <RecentAvatarSkeleton key={`skeleton-recent-${i}`} />
              ))
            ) : filteredRecent.length > 0 ? (
              filteredRecent.map((person, idx) => (
                <RecentAvatar
                  key={idx}
                  name={person.fullName}
                  onClick={() => handleQuickTransfer(person.accountNumber)}
                />
              ))
            ) : (
              <p className="text-sm italic" style={{ color: '#94a3b8' }}>Chưa có giao dịch gần đây</p>
            )}
          </div>
        </div>

        {/* 3. DANH BẠ YÊU THÍCH */}
        <div>
          <div className="flex items-center justify-between mb-4">
            <p className="font-black text-[10px] uppercase" style={{ color: '#94a3b8', letterSpacing: '0.12em' }}>
              Danh bạ yêu thích
            </p>
            <button className="font-black text-[10px] uppercase transition-colors" style={{ color: '#1a56db', background: 'none', border: 'none', cursor: 'pointer', letterSpacing: '0.08em' }}>
              + Thêm mới
            </button>
          </div>

          <div className="flex flex-col gap-3">
            {loading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <ContactRowSkeleton key={`skeleton-contact-${i}`} />
              ))
            ) : filteredFavorites.length > 0 ? (
              filteredFavorites.map((contact) => (
                <ContactRow
                  key={contact.id}
                  contact={contact}
                  onClick={() => handleQuickTransfer(contact.contactAccountNumber)}
                />
              ))
            ) : (
              <div className="text-center py-8" style={{ background: 'white', borderRadius: 20, border: '2px dashed #e2e8f0' }}>
                <p className="text-sm font-bold" style={{ color: '#94a3b8' }}>Danh bạ trống</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ── QR SCANNER MODAL ── */}
      {showScanner && (
        <div className="fixed inset-0 flex items-center justify-center px-4" style={{ zIndex: 100, background: 'rgba(13,27,42,0.75)', backdropFilter: 'blur(4px)' }}>
          <div className="w-full" style={{ maxWidth: 440, background: 'white', borderRadius: '2rem', padding: 8 }}>
            <QRCodeScanner
              onScanSuccess={handleQRSuccess}
              onScanCancel={() => setShowScanner(false)}
            />
          </div>
        </div>
      )}
    </div>
  );
}

/* ── CÁC COMPONENT CON GIỮ NGUYÊN HOÀN TOÀN TỪ CODE CỦA BRO ── */
/* ── (Tôi không can thiệp vì bro đã tách ra ngoài làm quá chuẩn rồi) ── */

function MethodCard({ onClick, bg, accentBg, accentColor, tag, label, children }) {
  const [hovered, setHovered] = useState(false);
  return (
    <button onClick={onClick} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} className="text-left transition-all" style={{ background: bg, borderRadius: '1.5rem', padding: '20px 16px', border: 'none', cursor: onClick ? 'pointer' : 'default', transform: hovered && onClick ? 'translateY(-4px)' : 'none', opacity: onClick ? 1 : 0.7 }}>
      <div className="flex items-center justify-center mb-4" style={{ width: 42, height: 42, background: accentBg, borderRadius: 12, color: accentColor }}>{children}</div>
      <p className="font-black text-[9px] uppercase mb-1" style={{ color: 'rgba(255,255,255,0.5)', letterSpacing: '0.1em' }}>{tag}</p>
      <p className="font-black text-sm leading-tight" style={{ color: 'white' }}>{label}</p>
    </button>
  );
}

function RecentAvatar({ name, onClick }) {
  const [hovered, setHovered] = useState(false);
  return (
    <button onClick={onClick} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} className="flex flex-col items-center flex-shrink-0 transition-all" style={{ background: 'none', border: 'none', cursor: 'pointer', gap: 10 }}>
      <div className="flex items-center justify-center font-black text-lg transition-all" style={{ width: 56, height: 56, borderRadius: '50%', background: hovered ? '#0d1b2a' : 'white', color: hovered ? 'white' : '#1a56db', border: hovered ? '2px solid #0d1b2a' : '2px solid #e2e8f0' }}>
        {name.charAt(0)}
      </div>
      <span className="font-bold text-center" style={{ fontSize: 11, color: '#64748b', width: 68, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{name}</span>
    </button>
  );
}

function ContactRow({ contact, onClick }) {
  const [hovered, setHovered] = useState(false);
  return (
    <button onClick={onClick} onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} className="w-full flex items-center justify-between transition-all text-left" style={{ background: 'white', borderRadius: 20, padding: '16px 18px', border: hovered ? '1px solid #0d1b2a' : '1px solid #e8ecf2', cursor: 'pointer' }}>
      <div className="flex items-center gap-3" style={{ minWidth: 0, flex: 1 }}>
        <div style={{ position: 'relative', flexShrink: 0 }}>
          <div className="flex items-center justify-center font-black text-base transition-all" style={{ width: 44, height: 44, borderRadius: 13, background: hovered ? '#0d1b2a' : '#e8f0fd', color: hovered ? 'white' : '#1a56db' }}>
            {contact.contactName.charAt(0)}
          </div>
          {contact.pinned && (
            <div className="absolute flex items-center justify-center" style={{ width: 16, height: 16, background: '#f59e0b', borderRadius: 5, top: -4, right: -4, border: '1.5px solid white' }}>
              <svg className="w-2 h-2" fill="white" viewBox="0 0 20 20"><path d="M10 12h5l-5 5V12z" /><path fillRule="evenodd" d="M3 5a2 2 0 012-2h10a2 2 0 012 2v8a2 2 0 01-2 2h-2.22l.123.489.804.804A1 1 0 0113 18H7a1 1 0 01-.707-1.707l.804-.804L7.22 15H5a2 2 0 01-2-2V5zm2 0v8h10V5H5z" clipRule="evenodd" /></svg>
            </div>
          )}
        </div>
        <div style={{ minWidth: 0 }}>
          <p className="font-black text-sm truncate transition-colors" style={{ color: hovered ? '#0d1b2a' : '#1e293b' }}>{contact.contactName}</p>
          <p className="font-mono text-xs mt-0.5" style={{ color: '#94a3b8', letterSpacing: '0.04em' }}>{contact.contactAccountNumber}</p>
        </div>
      </div>
      <div className="flex items-center gap-3 flex-shrink-0 ml-3">
        {contact.lastAmount ? (
          <div className="flex items-center gap-2 px-3 py-2" style={{ background: '#ecfdf5', borderRadius: 10, border: '1px solid #d1fae5' }}>
            <div style={{ width: 6, height: 6, borderRadius: '50%', background: '#10b981' }} />
            <div>
              <p className="font-black text-[8px] uppercase leading-none mb-0.5" style={{ color: '#6ee7b7', letterSpacing: '0.08em' }}>GD cuối</p>
              <p className="font-black text-xs leading-none" style={{ color: '#065f46' }}>
                {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(contact.lastAmount)}
              </p>
            </div>
          </div>
        ) : (
          <span className="font-bold text-[10px] italic" style={{ color: '#cbd5e1' }}>Chưa có lịch sử</span>
        )}
        <svg className="w-4 h-4 transition-colors" fill="none" stroke={hovered ? '#1a56db' : '#cbd5e1'} strokeWidth="2.5" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" /></svg>
      </div>
    </button>
  );
}

function RecentAvatarSkeleton() {
  return (
    <div className="flex flex-col items-center flex-shrink-0" style={{ gap: 10 }}>
      <div className="skeleton-shimmer" style={{ width: 56, height: 56, borderRadius: '50%' }}></div>
      <div className="skeleton-shimmer" style={{ height: 10, width: 48, borderRadius: 4, marginTop: 2 }}></div>
    </div>
  );
}

function ContactRowSkeleton() {
  return (
    <div className="w-full flex items-center justify-between text-left" style={{ background: 'white', borderRadius: 20, padding: '16px 18px', border: '1px solid #e8ecf2' }}>
      <div className="flex items-center gap-3" style={{ flex: 1 }}>
        <div className="skeleton-shimmer flex-shrink-0" style={{ width: 44, height: 44, borderRadius: 13 }}></div>
        <div className="w-full max-w-[120px] flex flex-col gap-2.5">
          <div className="skeleton-shimmer" style={{ height: 12, width: '100%', borderRadius: 4 }}></div>
          <div className="skeleton-shimmer" style={{ height: 10, width: '60%', borderRadius: 4 }}></div>
        </div>
      </div>
      <div className="skeleton-shimmer flex-shrink-0 ml-3" style={{ width: 80, height: 34, borderRadius: 10 }}></div>
    </div>
  );
}





 