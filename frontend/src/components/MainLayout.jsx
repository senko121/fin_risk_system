 

import React, { useContext } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { toast } from 'react-toastify';

import { HeaderContext } from '../context/HeaderContext';

export default function MainLayout() {
  const navigate = useNavigate();
  const user = JSON.parse(localStorage.getItem('currentUser') || '{}');
  
  // 1. Máy thu tín hiệu bộ đàm
  const { headerOverride } = useContext(HeaderContext);

  const handleForceLogout = (message) => {
    toast.success('✅ ' + message);
    localStorage.clear();
    navigate('/login');
  };

  const getInitial = (name) => name?.charAt(0)?.toUpperCase() || '?';

  return (
    <div className="flex flex-col w-full h-full">
      
      {/* ── QUYẾT ĐỊNH RENDER NAVBAR NÀO ── */}
{/* ── QUYẾT ĐỊNH RENDER NAVBAR NÀO ── */}
      {headerOverride ? (
        
        /* 🎨 1. NAVBAR GHI ĐÈ (Dùng cho Chuyển tiền, Lịch sử...) */
        <nav 
          className="flex items-center justify-center px-8 z-50 flex-shrink-0 shadow-sm relative transition-all duration-300" 
          style={{ background: '#0d1b2a', height: '80px', borderRadius: '0 0 2rem 2rem' }}
        >
          {/* 🚀 KHUNG ĐỆM AN TOÀN: Giới hạn độ rộng để nút Back tự động kéo vào trong */}
          {/* Bro có thể chỉnh max-w-5xl thành max-w-4xl (vào sâu nữa) hoặc max-w-6xl (ra ngoài chút) */}
          <div className="w-full max-w-2xl mx-auto flex items-center justify-center relative">
            
            {headerOverride.onBack && (
              <button
                onClick={headerOverride.onBack}
                /* 🎯 ĐỔI TỪ left-6 THÀNH left-0: Bây giờ nút bám vào lề của khung max-w-5xl chứ không bám lề màn hình nữa */
                className="absolute left-0 flex items-center justify-center transition-all hover:opacity-80"
                style={{ width: 38, height: 38, background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 10, cursor: 'pointer' }}
              >
                <svg className="w-5 h-5" fill="none" stroke="rgba(255,255,255,0.8)" strokeWidth="2.5" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
                </svg>
              </button>
            )}

            <div className="text-center px-12">
              <h1 className="font-black leading-none" style={{ fontSize: '22px', color: 'white', letterSpacing: '-0.3px' }}>
                {headerOverride.title}
              </h1>
              {headerOverride.subtitle && (
                <p className="font-bold text-xs mt-1.5" style={{ color: 'rgba(255,255,255,0.38)' }}>
                  {headerOverride.subtitle}
                </p>
              )}
            </div>

          </div>
        </nav>

      ) : (

        /* 🎨 2. NAVBAR ZIN (Dùng cho Dashboard mặc định) */
        <nav
          className="flex items-center justify-center gap-80 px-8 z-50 flex-shrink-0 shadow-sm transition-all duration-300"
          style={{ background: '#0d1b2a', height: '60px' }}
        >
          <div className="flex items-center gap-3">
            <div className="flex items-center justify-center font-black text-white text-sm" style={{ width: 34, height: 34, background: '#1a56db', borderRadius: 10, letterSpacing: '-0.5px' }}>
              FR
            </div>
            <span className="font-black text-white text-sm tracking-widest">FINRISK</span>
          </div>

          <div className="flex items-center gap-2">
            <button onClick={() => navigate('/account')} className="flex items-center gap-2 cursor-pointer transition-all" style={{ padding: '5px 12px 5px 5px', borderRadius: 99, background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.1)' }}>
              <div className="flex items-center justify-center font-black text-white text-xs" style={{ width: 28, height: 28, borderRadius: '50%', background: '#1a56db' }}>
                {getInitial(user.fullName)}
              </div>
              <span className="text-xs font-bold hidden sm:block" style={{ color: 'rgba(255,255,255,0.8)' }}>
                {user.fullName}
              </span>
            </button>
            <button onClick={() => handleForceLogout('Đã đăng xuất an toàn!')} className="flex items-center justify-center transition-all" style={{ width: 34, height: 34, borderRadius: '50%', background: 'none', border: 'none', color: 'rgba(255,255,255,0.35)', cursor: 'pointer' }}>
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" /></svg>
            </button>
          </div>
        </nav>

      )}

      {/* ── NỘI DUNG TRANG (Outlet) ── */}
      <div className="flex-1 w-full relative overflow-y-auto hide-scrollbar">
        <AnimatePresence mode="wait">
          <Outlet />
        </AnimatePresence>
      </div>
    </div>
  );
}