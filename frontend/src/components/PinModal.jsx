 

// import React, { useState, useRef, useEffect } from 'react';
// import { useNavigate } from 'react-router-dom';
// import { toast } from 'react-toastify';
// import axiosClient from '../api/axiosClient';

// export default function PinModal({ isOpen, onClose, transactionId, formData, recipientName }) {
//   const navigate = useNavigate();
//   const inputRef = useRef(null);
//   const [pin, setPin] = useState('');
//   const [isLoading, setIsLoading] = useState(false);
//   const [errorText, setErrorText] = useState('');

//   useEffect(() => {
//     if (isOpen && inputRef.current) {
//       setTimeout(() => inputRef.current.focus(), 100);
//       setErrorText(''); 
//     }
//   }, [isOpen]);

//   if (!isOpen) return null;

//   const handleVerifyPin = async (e) => {
//     e.preventDefault();
//     e.stopPropagation();
//     if (pin.length !== 6) {
//       setErrorText("Vui lòng nhập đủ 6 số Mã PIN"); 
//       return;
//     }

//     setIsLoading(true);
//     try {
//       const response = await axiosClient.post('/transactions/verify', {
//         transactionId: transactionId,
//         authType: 'PIN',
//         authCode: pin
//       });

//       const resData = response.data;

//       if (resData.status === 'SUCCESS') {
//         toast.success("🎉 " + (resData.message || "Giao dịch thành công!"));
//         onClose();
//         navigate('/transaction-result', { 
//           state: { result: resData.data, formData, recipientName } 
//         });
//       } 
//       else if (resData.status === 'NEXT_STEP') {
//         toast.info("✅ " + resData.message);
//         onClose(); 
        
//         if (resData.nextAuthType === 'OTP') {
//           navigate('/verify-otp', { state: { transactionId, formData, recipientName } });
//         } else if (resData.nextAuthType === 'FACE_STATIC') {
//           navigate('/verify-face', { state: { transactionId, formData, recipientName } });
//         } 
//         // 🚀 ĐÓN LUỒNG ATOMIC VERIFICATION TỪ BACKEND
//         else if (resData.nextAuthType === 'ALL_IN_ONE_BIOMETRIC' || resData.nextAuthType === 'FACE_AI') { 
//           navigate('/verify-high-risk', { 
//             state: { 
//               transactionId, 
//               formData, 
//               recipientName,
//               voiceCode: resData.voiceCode // 🚀 CHỞ ĐẠN QUA MÀN HÌNH HIGHRISK
//             } 
//           });
//         }
//       }
//     } catch (error) {
//       const errorMsg = error.response?.data?.message || "Sai Mã PIN, vui lòng thử lại!";
      
//       if (typeof errorMsg === 'string' && errorMsg.toLowerCase().includes('khóa')) {
//           toast.error("❌ " + errorMsg);
//           setTimeout(() => {
//             onClose();
//             navigate('/dashboard');
//           }, 2000);
//       } else {
//           setErrorText(errorMsg);
//           setPin(''); 
//           if (inputRef.current) inputRef.current.focus();
//       }
//     } finally {
//       setIsLoading(false);
//     }
//   };

//   return (
//     <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4">
//       <div className="bg-white rounded-[2rem] shadow-2xl w-full max-w-sm overflow-hidden relative animate-fade-in-up">
        
//         <button 
//           onClick={() => {
//             if(window.confirm("Bạn có chắc chắn muốn hủy phiên nhập mã PIN này không?")) {
//               onClose();
//             }
//           }} 
//           disabled={isLoading} 
//           className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 p-2"
//         >
//           <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
//         </button>

//         <div className="p-8 text-center mt-4">
//           <div className="w-16 h-16 bg-emerald-50 rounded-full flex items-center justify-center mx-auto mb-4">
//             <svg className="w-8 h-8 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path></svg>
//           </div>
//           <h3 className="text-xl font-black text-slate-800 mb-1">Mã PIN bảo mật</h3>
//           <p className="text-xs text-slate-500 font-medium mb-6">Vui lòng nhập 6 số Mã PIN để xác thực</p>

//           <form onSubmit={handleVerifyPin}>
//             <input 
//               ref={inputRef}
//               type="password" 
//               maxLength="6"
//               value={pin}
//               onChange={(e) => {
//                   setPin(e.target.value.replace(/[^0-9]/g, ''));
//                   if (errorText) setErrorText('');
//               }}
//               disabled={isLoading}
//               className={`w-full text-center text-4xl tracking-[0.5em] font-black py-4 border-b-4 focus:outline-none mb-2 transition-colors bg-transparent ${errorText ? 'border-red-500 text-red-600' : 'border-slate-200 focus:border-emerald-500 text-slate-800'}`}
//               placeholder="••••••"
//             />
            
//             <div className="h-6 mb-6">
//                 {errorText && (
//                     <p className="text-sm font-bold text-red-500 animate-pulse">
//                         {errorText}
//                     </p>
//                 )}
//             </div>

//             <button 
//               type="button" 
//               onClick={handleVerifyPin}
//               disabled={isLoading || pin.length !== 6}
//               className={`w-full py-4 rounded-xl text-white font-bold text-lg shadow-lg transition-all ${(isLoading || pin.length !== 6) ? 'bg-slate-200 shadow-none cursor-not-allowed' : 'bg-emerald-500 hover:bg-emerald-600 active:scale-95 shadow-emerald-200'}`}
//             >
//               {isLoading ? 'ĐANG KIỂM TRA...' : 'XÁC NHẬN'}
//             </button>
//           </form>
//         </div>
//       </div>
//     </div>
//   );
// }


import React, { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import axiosClient from '../api/axiosClient';

export default function PinModal({ isOpen, onClose, transactionId, formData, recipientName }) {
  const navigate = useNavigate();
  const inputRef = useRef(null);
  const [pin, setPin] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [errorText, setErrorText] = useState('');

  useEffect(() => {
    if (isOpen && inputRef.current) {
      setTimeout(() => inputRef.current.focus(), 100);
      setErrorText(''); 
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleVerifyPin = async (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (pin.length !== 6) {
      setErrorText("Vui lòng nhập đủ 6 số Mã PIN"); 
      return;
    }

    setIsLoading(true);
    try {
      const response = await axiosClient.post('/transactions/verify', {
        transactionId: transactionId,
        authType: 'PIN',
        authCode: pin
      });

      const resData = response.data;

      if (resData.status === 'SUCCESS') {
        toast.success("🎉 " + (resData.message || "Giao dịch thành công!"));
        onClose();
        navigate('/transaction-result', { 
          state: { result: resData.data, formData, recipientName } 
        });
      } 
      else if (resData.status === 'NEXT_STEP') {
        toast.info("✅ " + resData.message);
        onClose(); 
        
        if (resData.nextAuthType === 'OTP') {
          navigate('/verify-otp', { state: { transactionId, formData, recipientName } });
        } else if (resData.nextAuthType === 'FACE_STATIC') {
          navigate('/verify-face', { state: { transactionId, formData, recipientName } });
        } 
        // 🚀 ĐÓN LUỒNG ATOMIC VERIFICATION TỪ BACKEND
        else if (resData.nextAuthType === 'ALL_IN_ONE_BIOMETRIC' || resData.nextAuthType === 'FACE_AI') { 
          navigate('/verify-high-risk', { 
            state: { 
              transactionId, 
              formData, 
              recipientName,
              voiceCode: resData.voiceCode // 🚀 CHỞ ĐẠN QUA MÀN HÌNH HIGHRISK
            } 
          });
        }
      }
    } catch (error) {
      const errorMsg = error.response?.data?.message || "Sai Mã PIN, vui lòng thử lại!";
      
      if (typeof errorMsg === 'string' && errorMsg.toLowerCase().includes('khóa')) {
          toast.error("❌ " + errorMsg);
          setTimeout(() => {
            onClose();
            navigate('/dashboard');
          }, 2000);
      } else {
          setErrorText(errorMsg);
          setPin(''); 
          if (inputRef.current) inputRef.current.focus();
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-[#1e2d40]/80 backdrop-blur-sm p-4 font-sans">
      <div className="bg-white rounded-2xl shadow-xl border border-gray-100 w-full max-w-sm overflow-hidden relative animate-fade-in-up">
        
        <button 
          onClick={() => {
            if(window.confirm("Bạn có chắc chắn muốn hủy phiên nhập mã PIN này không?")) {
              onClose();
            }
          }} 
          disabled={isLoading} 
          className="absolute top-4 right-4 text-gray-400 hover:text-[#1e2d40] p-2 transition-colors"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path>
          </svg>
        </button>

        <div className="p-8 text-center mt-2">
          {/* Icon bảo mật: Form xám nhẹ, icon Navy */}
          <div className="w-12 h-12 bg-[#f4f6f9] text-[#1e2d40] rounded-xl flex items-center justify-center mx-auto mb-4 border border-gray-100">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
            </svg>
          </div>
          
          <h3 className="text-sm font-semibold text-gray-800 mb-1">XÁC THỰC GIAO DỊCH</h3>
          <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-400 mb-6">Mã PIN bảo mật</p>

          <form onSubmit={handleVerifyPin}>
            <input 
              ref={inputRef}
              type="password" 
              maxLength="6"
              value={pin}
              onChange={(e) => {
                  setPin(e.target.value.replace(/[^0-9]/g, ''));
                  if (errorText) setErrorText('');
              }}
              disabled={isLoading}
              className={`w-full text-center text-3xl tracking-[0.4em] font-bold py-4 rounded-xl border focus:outline-none transition-all 
                ${errorText 
                  ? 'bg-red-50 border-[#b91c1c] text-[#b91c1c] focus:border-[#b91c1c]' 
                  : 'bg-[#f4f6f9] border-transparent text-gray-800 focus:bg-white focus:border-[#1e2d40]'}`}
              placeholder="••••••"
            />
            
            <div className="h-6 mt-2 mb-4">
                {errorText && (
                    <p className="text-[10px] font-semibold text-[#b91c1c] uppercase tracking-widest">
                        {errorText}
                    </p>
                )}
            </div>

            <button 
              type="button" 
              onClick={handleVerifyPin}
              disabled={isLoading || pin.length !== 6}
              className={`w-full py-3.5 rounded-xl text-sm font-semibold tracking-wide transition-all flex items-center justify-center gap-2
                ${(isLoading || pin.length !== 6) 
                  ? 'bg-[#1e2d40]/40 text-white cursor-not-allowed' 
                  : 'bg-[#1e2d40] text-white hover:bg-[#162233] active:scale-[0.98]'}`}
            >
              {isLoading ? (
                <>
                  <svg className="w-4 h-4 animate-spin text-white" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4l3-3-3-3v4a8 8 0 00-8 8h4z"></path>
                  </svg>
                  ĐANG XỬ LÝ...
                </>
              ) : 'XÁC NHẬN MÃ PIN'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}