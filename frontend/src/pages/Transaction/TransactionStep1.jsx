 
import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { toast } from 'react-toastify';
import axiosClient from '../../api/axiosClient';
import PinModal from '../../components/PinModal';
import { useHeaderOverride } from '../../context/HeaderContext';

export default function TransactionStep1() {
  const navigate = useNavigate();
  const location = useLocation();
  const [currentUser, setCurrentUser] = useState(null);
  const [formData, setFormData] = useState({ toAccount: '', amount: '', description: '' });
  const [isLoading, setIsLoading] = useState(false);

  const [recipientName, setRecipientName] = useState('');
  const [lookupError, setLookupError] = useState('');
  const [isLookingUp, setIsLookingUp] = useState(false);

  const [showPinModal, setShowPinModal] = useState(false);
  const [currentTxId, setCurrentTxId] = useState(null);

  const formatMoney = (amount) => {
    if (amount == null || isNaN(amount)) return '0 ₫';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
  };

  const formatAmountDisplay = (val) => {
    if (!val) return '';
    return new Intl.NumberFormat('vi-VN').format(val);
  };

  useHeaderOverride('Chuyển tiền', 'Tạo lệnh giao dịch an toàn', () => navigate('/transfer'));

  useEffect(() => {
    const userStr = localStorage.getItem('currentUser');
    if (userStr) setCurrentUser(JSON.parse(userStr));
    else navigate('/login');
  }, [navigate]);

  useEffect(() => {
    if (location.state?.targetAccount) {
      setFormData(prev => ({ ...prev, toAccount: location.state.targetAccount }));
    }
  }, [location]);

  useEffect(() => {
    const timer = setTimeout(async () => {
      if (formData.toAccount.length >= 9) {
        setIsLookingUp(true);
        setLookupError('');
        setRecipientName('');
        try {
          const response = await axiosClient.get(`/transactions/lookup/${formData.toAccount}`);
          setRecipientName(response.data.fullName);
        } catch (error) {
          setLookupError(error.response?.data || 'Tài khoản không tồn tại!');
        } finally {
          setIsLookingUp(false);
        }
      } else {
        setRecipientName('');
        setLookupError('');
      }
    }, 500);
    return () => clearTimeout(timer);
  }, [formData.toAccount]);

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
    if (currentTxId) setCurrentTxId(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (currentTxId) {
      setShowPinModal(true);
      toast.info('Vui lòng hoàn tất giao dịch đang thực hiện!');
      return;
    }
    if (!formData.toAccount || !formData.amount) {
      toast.warning('⚠️ Vui lòng nhập đầy đủ thông tin!');
      return;
    }
    if (!recipientName) {
      toast.error('❌ Người nhận không hợp lệ!');
      return;
    }

    setIsLoading(true);
    try {
      const response = await axiosClient.post('/transactions/process', {
        fromAccountId: currentUser.userId || currentUser.id,
        toAccount: formData.toAccount,
        amount: formData.amount,
        description: formData.description,
      });

      const result = response.data;

      if (result.status.startsWith('PENDING_PIN')) {
        setCurrentTxId(result.id);
        setShowPinModal(true);
      } else if (result.status === 'PENDING_FACE_AI') {
        toast.error('🚨 Phát hiện rủi ro cao! Yêu cầu quét mặt an ninh!');
        navigate('/verify-face', { state: { transactionId: result.id, formData, recipientName, result } });
      } else {
        toast.error('🚨 Lỗi luồng xử lý: Trạng thái không xác định.');
      }
    } catch (error) {
      const msg =
        error.response?.data?.message ||
        (typeof error.response?.data === 'string' ? error.response?.data : 'Lỗi giao dịch!');
      const errorCode = error.response?.data?.code || error.response?.data?.errorCode;

      toast.error('🚨 ' + msg);

      if (errorCode === 'ERR_NO_PIN_SETUP') {
        setTimeout(() => navigate('/setup-pin'), 2000);
        return;
      }
      if (errorCode === 'ERR_NO_FACE_SETUP' || msg.includes('FaceID') || msg.includes('khuôn mặt')) {
        setTimeout(() => navigate('/register-face'), 2000);
        return;
      }
      if (typeof msg === 'string' && msg.toLowerCase().includes('khóa')) {
        setTimeout(() => navigate('/dashboard'), 2500);
      }
    } finally {
      setIsLoading(false);
    }
  };

  if (!currentUser) return null;

  const canSubmit = !isLoading && !!recipientName && !showPinModal;

return (
    <div className="w-full relative pb-10">

      {/* ── FORM BODY ── */}
      <div className="max-w-2xl mx-auto px-5 py-6" style={{ marginTop: '-5px' }}> {/* Kéo lên một chút để gối lên header */}
      

        <form onSubmit={handleSubmit}>
          <div className="flex flex-col gap-4">

            {/* ── 1. TÀI KHOẢN NGUỒN (Đã đưa vào trong list gap-4) ── */}
          <div
            className="flex items-center justify-between"
            style={{
              background: 'white',
              border: '1px solid rgb(232, 236, 242)',
              borderRadius: '20px',
              padding: '16px 20px',
              boxShadow: '0 4px 12px rgba(0, 0, 0, 0.03)',
            }}
          >
            <div className="flex items-center gap-3">
              <div
                className="flex items-center justify-center flex-shrink-0"
                style={{
                  width: '40px',
                  height: '40px',
                  background: 'rgb(241, 245, 249)',
                  borderRadius: '12px',
                  color: 'rgb(30, 45, 64)',
                }}
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path>
                </svg>
              </div>
              <div>
                <p className="font-black text-[10px] uppercase mb-0.5" style={{ color: 'rgb(148, 163, 184)', letterSpacing: '0.1em' }}>
                  Tài khoản nguồn
                </p>
                <p className="font-black text-sm" style={{ color: 'rgb(13, 27, 42)' }}>
                  Tài khoản chính
                </p>
              </div>
            </div>
            <div className="text-right">
              <p className="font-black text-[10px] uppercase mb-0.5" style={{ color: 'rgb(148, 163, 184)', letterSpacing: '0.1em' }}>
                Số dư khả dụng
              </p>
              <p className="font-black text-sm" style={{ color: 'rgb(16, 185, 129)' }}>
                {formatMoney(currentUser.balance)}
              </p>
            </div>
          </div>

            {/* ── SỐ TÀI KHOẢN NHẬN ── */}
            <div
              style={{
                background: 'white',
                borderRadius: 20,
                border: '1px solid #e8ecf2',
                padding: '18px 20px',
              }}
            >
              <p
                className="font-black text-[10px] uppercase mb-3"
                style={{ color: '#94a3b8', letterSpacing: '0.12em' }}
              >
                Số tài khoản nhận
              </p>
              <div style={{ position: 'relative' }}>
                <input
                  type="text"
                  name="toAccount"
                  value={formData.toAccount}
                  onChange={handleChange}
                  required
                  placeholder="Nhập STK..."
                  className="w-full outline-none font-mono font-black transition-all"
                  style={{
                    fontSize: 24,
                    color: recipientName ? '#065f46' : lookupError ? '#991b1b' : '#0d1b2a',
                    background: 'transparent',
                    border: 'none',
                    paddingRight: 36,
                  }}
                />
                {/* Status indicator */}
                {isLookingUp && (
                  <div
                    style={{
                      position: 'absolute', right: 0, top: '50%', transform: 'translateY(-50%)',
                      width: 22, height: 22,
                      border: '3px solid #e2e8f0',
                      borderTopColor: '#1a56db',
                      borderRadius: '50%',
                      animation: 'spin 0.7s linear infinite',
                    }}
                  />
                )}
                {recipientName && !isLookingUp && (
                  <div
                    style={{
                      position: 'absolute', right: 0, top: '50%', transform: 'translateY(-50%)',
                      width: 22, height: 22,
                      background: '#10b981',
                      borderRadius: '50%',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                    }}
                  >
                    <svg className="w-3 h-3" fill="none" stroke="white" strokeWidth="3" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                  </div>
                )}
                {lookupError && !isLookingUp && (
                  <div
                    style={{
                      position: 'absolute', right: 0, top: '50%', transform: 'translateY(-50%)',
                      width: 22, height: 22,
                      background: '#ef4444',
                      borderRadius: '50%',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                    }}
                  >
                    <svg className="w-3 h-3" fill="none" stroke="white" strokeWidth="3" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </div>
                )}
              </div>
              {lookupError && (
                <p className="text-xs font-bold mt-2" style={{ color: '#ef4444' }}>{lookupError}</p>
              )}
            </div>

            {/* ── RECIPIENT NAME (animated reveal) ── */}
            <div
              style={{
                overflow: 'hidden',
                maxHeight: recipientName ? 90 : 0,
                opacity: recipientName ? 1 : 0,
                transition: 'max-height 0.3s ease, opacity 0.3s ease',
              }}
            >
              <div
                className="flex items-center justify-between"
                style={{
                  background: '#ecfdf5',
                  border: '1px solid #a7f3d0',
                  borderRadius: 16,
                  padding: '14px 18px',
                }}
              >
                <div>
                  <p
                    className="font-black text-[9px] uppercase mb-1"
                    style={{ color: '#6ee7b7', letterSpacing: '0.1em' }}
                  >
                    Chủ tài khoản
                  </p>
                  <p className="font-black text-lg" style={{ color: '#065f46' }}>
                    {recipientName}
                  </p>
                </div>
                <div
                  className="flex items-center justify-center flex-shrink-0"
                  style={{
                    width: 38, height: 38,
                    background: '#d1fae5',
                    borderRadius: '50%',
                    color: '#10b981',
                  }}
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                </div>
              </div>
            </div>

            {/* ── SỐ TIỀN ── */}
            <div
              style={{
                background: 'white',
                borderRadius: 20,
                border: '1px solid #e8ecf2',
                padding: '18px 20px',
              }}
            >
              <p
                className="font-black text-[10px] uppercase mb-3"
                style={{ color: '#94a3b8', letterSpacing: '0.12em' }}
              >
                Số tiền chuyển
              </p>
              <div className="flex items-baseline gap-3">
                <input
                  type="text"
                  name="amount"
                  value={formData.amount}
                  required
                  placeholder="0"
                  onChange={(e) =>
                    setFormData({ ...formData, amount: e.target.value.replace(/[^0-9]/g, '') })
                  }
                  className="outline-none font-black flex-1 min-w-0 transition-all"
                  style={{
                    fontSize: 36,
                    color: '#0d1b2a',
                    background: 'transparent',
                    border: 'none',
                  }}
                />
                <span
                  className="font-black flex-shrink-0"
                  style={{ fontSize: 18, color: '#cbd5e1' }}
                >
                  VND
                </span>
              </div>
              {formData.amount && (
                <p className="text-xs font-bold mt-1" style={{ color: '#94a3b8' }}>
                  ≈ {formatMoney(Number(formData.amount))}
                </p>
              )}

              {/* Quick amount chips */}
              <div className="flex gap-2 mt-4 flex-wrap">
                {[100000, 500000, 1000000, 5000000].map((amt) => (
                  <button
                    key={amt}
                    type="button"
                    onClick={() => setFormData(prev => ({ ...prev, amount: String(amt) }))}
                    className="font-black text-xs transition-all"
                    style={{
                      padding: '6px 14px',
                      borderRadius: 20,
                      border: 'none',
                      background: formData.amount === String(amt) ? '#0d1b2a' : '#f1f4f8',
                      color: formData.amount === String(amt) ? 'white' : '#64748b',
                      cursor: 'pointer',
                    }}
                  >
                    {amt >= 1000000 ? `${amt / 1000000}M` : `${amt / 1000}K`}
                  </button>
                ))}
              </div>
            </div>

            {/* ── NỘI DUNG ── */}
            <div
              style={{
                background: 'white',
                borderRadius: 20,
                border: '1px solid #e8ecf2',
                padding: '18px 20px',
              }}
            >
              <p
                className="font-black text-[10px] uppercase mb-3"
                style={{ color: '#94a3b8', letterSpacing: '0.12em' }}
              >
                Nội dung chuyển khoản
              </p>
              <textarea
                name="description"
                value={formData.description}
                onChange={handleChange}
                placeholder="Ví dụ: Trả tiền cà phê"
                rows={2}
                className="w-full outline-none font-bold text-sm resize-none"
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: '#334155',
                }}
              />
            </div>

            {/* ── SUBMIT BUTTON ── */}
            <button
              type="submit"
              disabled={!canSubmit}
              className="w-full font-black text-sm uppercase tracking-widest transition-all"
              style={{
                padding: '18px',
                borderRadius: 16,
                border: 'none',
                background: canSubmit ? '#0d1b2a' : '#e2e8f0',
                color: canSubmit ? 'white' : '#94a3b8',
                cursor: canSubmit ? 'pointer' : 'not-allowed',
                letterSpacing: '0.1em',
              }}
              onMouseEnter={e => { if (canSubmit) e.currentTarget.style.background = '#1a56db'; }}
              onMouseLeave={e => { if (canSubmit) e.currentTarget.style.background = '#0d1b2a'; }}
            >
              {isLoading ? (
                <span className="flex items-center justify-center gap-2">
                  <span
                    style={{
                      width: 16, height: 16,
                      border: '2.5px solid rgba(255,255,255,0.3)',
                      borderTopColor: 'white',
                      borderRadius: '50%',
                      display: 'inline-block',
                      animation: 'spin 0.7s linear infinite',
                    }}
                  />
                  Đang thẩm định rủi ro...
                </span>
              ) : (
                'Xác nhận chuyển tiền'
              )}
            </button>

            {/* Security note */}
            <p
              className="text-center font-bold text-xs"
              style={{ color: '#94a3b8' }}
            >
              Giao dịch được bảo vệ bởi AI Risk Monitor & mã hóa SSL 256-bit
            </p>

          </div>
        </form>
      </div>

      {/* ── PIN MODAL ── */}
      <PinModal
        isOpen={showPinModal}
        onClose={() => setShowPinModal(false)}
        transactionId={currentTxId}
        formData={formData}
        recipientName={recipientName}
      />

      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
      `}</style>
    </div>
  );
}