
// import React from 'react';
// import ReactDOM from 'react-dom/client';
// import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
// import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
// import App from './App.jsx'; 
// import './index.css';

// // 1. Khởi tạo QueryClient (Bộ não quản lý Cache)
// const queryClient = new QueryClient({
//   defaultOptions: {
//     queries: {
//       refetchOnWindowFocus: false, // Tắt tính năng tự động gọi lại API khi chuyển tab trình duyệt (tùy chọn)
//       retry: 1, // Nếu lỗi mạng, thử gọi lại 1 lần trước khi báo lỗi
//       staleTime: 5 * 60 * 1000, // Data sống trong 5 phút mới bị coi là cũ và cần fetch lại ngầm
//     },
//   },
// });

// ReactDOM.createRoot(document.getElementById('root')).render(
//   <React.StrictMode>
//     {/* 2. Bọc toàn bộ App bằng Provider */}
//     <QueryClientProvider client={queryClient}>
//       <App />
      
//       {/* Tool hỗ trợ debug xịn xò (chỉ hiện ở môi trường dev, icon bông hoa góc dưới màn hình) */}
//       <ReactQueryDevtools initialIsOpen={false} />
//     </QueryClientProvider>
//   </React.StrictMode>
// );


import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import App from './App.jsx'; 
import './index.css';

import { HeaderProvider } from './context/HeaderContext.jsx'; 

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false, 
      retry: 1, 
      staleTime: 5 * 60 * 1000, 
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <HeaderProvider>
        <App />
      </HeaderProvider>
      
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </React.StrictMode>
);