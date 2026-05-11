 


// import React, { useState, useRef, useEffect } from 'react';
// import { useNavigate } from 'react-router-dom';
// import { toast } from 'react-toastify';
// import axiosClient from '../api/axiosClient';

// export default function PinModal({ isOpen, onClose, transactionId, formData, recipientName }) {
//   const navigate = useNavigate();
//   const inputRef = useRef(null);
//   const [pin, setPin] = useState('');
//   const [isLoading, setIsLoading] = useState(false);
//   // 🚀 BƯỚC 1: THÊM STATE ĐỂ LƯU CÂU BÁO LỖI (VÍ DỤ: "Bạn còn 3 lần thử")
//   const [errorText, setErrorText] = useState('');

//   useEffect(() => {
//     if (isOpen && inputRef.current) {
//       setTimeout(() => inputRef.current.focus(), 100);
//       setErrorText(''); // 🚀 BƯỚC 2: Xóa lỗi cũ mỗi khi mở lại Modal
//     }
//   }, [isOpen]);

//   if (!isOpen) return null;

//   const handleVerifyPin = async (e) => {
//     e.preventDefault();
//     e.stopPropagation();
//     if (pin.length !== 6) {
//       setErrorText("Vui lòng nhập đủ 6 số Mã PIN"); // 🚀 BƯỚC 3: Thay Toast bằng thông báo lỗi tại chỗ
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

//       // ... (Các KỊCH BẢN 1 và 2 giữ nguyên) ...
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
//         } else if (resData.nextAuthType === 'FACE_AI') { 
//           // NHÁNH MỚI CHO LUỒNG HIGH RISK
//           navigate('/verify-high-risk', { state: { transactionId, formData, recipientName } });
//         }
//       }
//     } catch (error) {
//       // 🚀 BƯỚC 4: Bắt chính xác câu chửi đếm lùi từ Backend (nhớ có ?.message vì Backend đã trả về JSON ApiErrorResponse)
//       const errorMsg = error.response?.data?.message || "Sai Mã PIN, vui lòng thử lại!";
      
//       // Bắn Toast cho những lỗi nghiêm trọng (như Khóa tài khoản)
//       if (typeof errorMsg === 'string' && errorMsg.toLowerCase().includes('khóa')) {
//           toast.error("❌ " + errorMsg);
//           setTimeout(() => {
//             onClose();
//             navigate('/dashboard');
//           }, 2000);
//       } else {
//           // 🚀 BƯỚC 5: Nếu chỉ là đếm lùi (còn 4, 3, 2 lần), thì in chữ đỏ dưới ô nhập, không cần bắn Toast rác màn hình
//           setErrorText(errorMsg);
//           setPin(''); // Xóa trắng ô PIN cho khách nhập lại
//           if (inputRef.current) inputRef.current.focus();
//       }
//     } finally {
//       setIsLoading(false);
//     }
//   };

//   return (
//     <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4">
//       <div className="bg-white rounded-[2rem] shadow-2xl w-full max-w-sm overflow-hidden relative animate-fade-in-up">
        
//         {/* Nút X tắt Modal (Giữ nguyên) */}
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
//               // 🚀 BƯỚC 6: Xóa dòng báo lỗi màu đỏ khi khách hàng bắt đầu gõ lại
//               onChange={(e) => {
//                   setPin(e.target.value.replace(/[^0-9]/g, ''));
//                   if (errorText) setErrorText('');
//               }}
//               disabled={isLoading}
//               // 🚀 BƯỚC 7: Đổi màu viền input thành ĐỎ nếu có lỗi
//               className={`w-full text-center text-4xl tracking-[0.5em] font-black py-4 border-b-4 focus:outline-none mb-2 transition-colors bg-transparent ${errorText ? 'border-red-500 text-red-600' : 'border-slate-200 focus:border-emerald-500 text-slate-800'}`}
//               placeholder="••••••"
//             />
            
//             {/* 🚀 BƯỚC 8: Vùng hiển thị câu thông báo "Bạn còn 3 lần thử" */}
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4">
      <div className="bg-white rounded-[2rem] shadow-2xl w-full max-w-sm overflow-hidden relative animate-fade-in-up">
        
        <button 
          onClick={() => {
            if(window.confirm("Bạn có chắc chắn muốn hủy phiên nhập mã PIN này không?")) {
              onClose();
            }
          }} 
          disabled={isLoading} 
          className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 p-2"
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
        </button>

        <div className="p-8 text-center mt-4">
          <div className="w-16 h-16 bg-emerald-50 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg className="w-8 h-8 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path></svg>
          </div>
          <h3 className="text-xl font-black text-slate-800 mb-1">Mã PIN bảo mật</h3>
          <p className="text-xs text-slate-500 font-medium mb-6">Vui lòng nhập 6 số Mã PIN để xác thực</p>

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
              className={`w-full text-center text-4xl tracking-[0.5em] font-black py-4 border-b-4 focus:outline-none mb-2 transition-colors bg-transparent ${errorText ? 'border-red-500 text-red-600' : 'border-slate-200 focus:border-emerald-500 text-slate-800'}`}
              placeholder="••••••"
            />
            
            <div className="h-6 mb-6">
                {errorText && (
                    <p className="text-sm font-bold text-red-500 animate-pulse">
                        {errorText}
                    </p>
                )}
            </div>

            <button 
              type="button" 
              onClick={handleVerifyPin}
              disabled={isLoading || pin.length !== 6}
              className={`w-full py-4 rounded-xl text-white font-bold text-lg shadow-lg transition-all ${(isLoading || pin.length !== 6) ? 'bg-slate-200 shadow-none cursor-not-allowed' : 'bg-emerald-500 hover:bg-emerald-600 active:scale-95 shadow-emerald-200'}`}
            >
              {isLoading ? 'ĐANG KIỂM TRA...' : 'XÁC NHẬN'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}