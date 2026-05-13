// import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
// import { ToastContainer } from 'react-toastify';
// import 'react-toastify/dist/ReactToastify.css';
// import Dashboard from './pages/Dashboard/Dashboard';
// import TransactionStep1 from './pages/Transaction/TransactionStep1';
// import Login from './pages/Auth/Login'; 
// import OTPVerification from './pages/Transaction/OTPVerification';
// import TransactionResult from './pages/Transaction/TransactionResult';
// import FaceRegister from './pages/Auth/FaceRegister';
// import FaceVerification from './pages/Transaction/FaceVerification';
// import AdminRoute from './components/AdminRoute';
// import AdminDashboard from './pages/Admin/AdminDashboard';
// import TransactionHistory from './pages/History/TransactionHistory';
// import ExpenseAnalytics from './pages/Analytics/ExpenseAnalytics';
// import ChangePassword from './pages/Auth/ChangePassword';
// import SecuritySettings from './pages/Security/SecuritySettings'; 
// import TransferMethod from './pages/Transaction/TransferMethod';
// import HighRiskVerification from './pages/Transaction/HighRiskVerification';
// import AccountProfile from './pages/User/AccountProfile';
// import UserSettings from './pages/Account/UserSettings';
// import SetupPin from './pages/Security/SetupPin';
// import AdminRuleDashboard from './pages/Admin/AdminRuleDashboard';
// import AdminUserDashboard from './pages/Admin/AdminUserDashboard';
// import AdminLogDashboard from './pages/Admin/AdminLogDashboard';
// import AdminPolicyDashboard from './pages/Admin/AdminPolicyDashboard';
// import AdminAiLogDashboard from './pages/Admin/AdminAiLogDashboard'; 

// import LivenessSandbox from './pages/Security/LivenessSandbox';


// function App() {
//   return (
//     <Router>
//       <div className="min-h-screen bg-gray-50 flex">
//         {/* Nội dung chính */}
//         <main className="flex-1"> 
          
//           <ToastContainer 
//             position="top-right" 
//             autoClose={3000} 
//             hideProgressBar={false}
//             newestOnTop={true}
//             closeOnClick
//             rtl={false}
//             pauseOnFocusLoss
//             draggable
//             pauseOnHover
//             theme="colored"
//           />

//           <Routes>
//             {/*   TRONG NÀY BÂY GIỜ CHỈ CÓ ROUTE THÔI */}
//             <Route path="/" element={<Navigate to="/login" />} />
//             <Route path="/login" element={<Login />} />
//             <Route path="/dashboard" element={<Dashboard />} />
//             <Route path="/account" element={<UserSettings />} />
//             <Route path="/card-details" element={<AccountProfile />} />
//             <Route path="/transfer" element={<TransferMethod />} />
//             <Route path="/transfer/stk" element={<TransactionStep1 />} />
//             <Route path="/verify-otp" element={<OTPVerification />} />
//             <Route path="/transaction-result" element={<TransactionResult />} />
//             <Route path="/register-face" element={<FaceRegister />} />
//             <Route path="/verify-face" element={<FaceVerification />} />
//             <Route path="/verify-high-risk" element={<HighRiskVerification />} />
//             <Route path="/history" element={<TransactionHistory />} />
//             <Route path="/analytics" element={<ExpenseAnalytics />} />
            
//             <Route path="/security" element={<SecuritySettings />} />
//             <Route path="/setup-pin" element={<SetupPin />} />
//             <Route path="/change-password" element={<ChangePassword />} />

//             <Route path="/test-3d" element={<LivenessSandbox />} />

//             <Route path="/admin" element={<AdminRoute> <AdminDashboard /> </AdminRoute>} />
//             <Route path="/admin/rules" element={<AdminRoute> <AdminRuleDashboard /> </AdminRoute>} />
//             <Route path="/admin/users" element={<AdminRoute> <AdminUserDashboard /> </AdminRoute>} />
//             <Route path="/admin/logs" element={<AdminRoute> <AdminLogDashboard /> </AdminRoute>} />
//             <Route path="/admin/policies" element={<AdminRoute> <AdminPolicyDashboard /> </AdminRoute>} />
//             <Route path="/admin/ai-logs" element={<AdminRoute> <AdminAiLogDashboard /> </AdminRoute>} />
//           </Routes>
          
//         </main>
//       </div>
//     </Router>
//   );
// }

// export default App;

import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

// Import Pages
import Dashboard from './pages/Dashboard/Dashboard';
import TransactionStep1 from './pages/Transaction/TransactionStep1';
import Login from './pages/Auth/Login'; 
import OTPVerification from './pages/Transaction/OTPVerification';
import TransactionResult from './pages/Transaction/TransactionResult';
import FaceRegister from './pages/Auth/FaceRegister';
import FaceVerification from './pages/Transaction/FaceVerification';
import AdminRoute from './components/AdminRoute';
import AdminDashboard from './pages/Admin/AdminDashboard';
import TransactionHistory from './pages/History/TransactionHistory';
import ExpenseAnalytics from './pages/Analytics/ExpenseAnalytics';
import ChangePassword from './pages/Auth/ChangePassword';
import SecuritySettings from './pages/Security/SecuritySettings'; 
import TransferMethod from './pages/Transaction/TransferMethod';
import HighRiskVerification from './pages/Transaction/HighRiskVerification';
import AccountProfile from './pages/User/AccountProfile';
import UserSettings from './pages/Account/UserSettings';
import SetupPin from './pages/Security/SetupPin';
import AdminRuleDashboard from './pages/Admin/AdminRuleDashboard';
import AdminUserDashboard from './pages/Admin/AdminUserDashboard';
import AdminLogDashboard from './pages/Admin/AdminLogDashboard';
import AdminPolicyDashboard from './pages/Admin/AdminPolicyDashboard';
import AdminAiLogDashboard from './pages/Admin/AdminAiLogDashboard'; 
import LivenessSandbox from './pages/Security/LivenessSandbox';

// Import Wrapper & Layouts
import PageWrapper from './components/PageWrapper';
import MainLayout from './components/MainLayout';
import BlankLayout from './components/BlankLayout';

function App() {
  return (
    <Router>
      <div className="h-screen bg-[#f0f2f6] flex relative overflow-hidden">
        {/* Thêm flex-col để Layout phân bổ Navbar ở trên, nội dung cuộn ở dưới */}
        <main className="flex-1 w-full h-full relative flex flex-col">
          
          <ToastContainer 
            position="top-right" 
            autoClose={3000} 
            hideProgressBar={false}
            newestOnTop={true}
            closeOnClick
            rtl={false}
            pauseOnFocusLoss
            draggable
            pauseOnHover
            theme="colored"
          />

          <Routes>
            <Route path="/" element={<Navigate to="/login" />} />
            
            {/* --- NHÓM 1: KHÔNG CÓ NAVBAR (Login, Verify, Auth...) --- */}
            <Route element={<BlankLayout />}>
              <Route path="/login" element={<PageWrapper key="login"><Login /></PageWrapper>} />
              <Route path="/setup-pin" element={<PageWrapper key="pin"><SetupPin /></PageWrapper>} />
              <Route path="/change-password" element={<PageWrapper key="pass"><ChangePassword /></PageWrapper>} />
              <Route path="/verify-otp" element={<PageWrapper key="otp"><OTPVerification /></PageWrapper>} />
              <Route path="/register-face" element={<PageWrapper key="reg-face"><FaceRegister /></PageWrapper>} />
              <Route path="/verify-face" element={<PageWrapper key="ver-face"><FaceVerification /></PageWrapper>} />
              <Route path="/verify-high-risk" element={<PageWrapper key="high-risk"><HighRiskVerification /></PageWrapper>} />
              <Route path="/test-3d" element={<PageWrapper key="3d"><LivenessSandbox /></PageWrapper>} />
              
              {/* ADMIN (Đưa vào BlankLayout vì Admin thường có giao diện/navbar riêng) */}
              <Route path="/admin" element={<AdminRoute><PageWrapper key="ad-dash"><AdminDashboard /></PageWrapper></AdminRoute>} />
              <Route path="/admin/rules" element={<AdminRoute><PageWrapper key="ad-rule"><AdminRuleDashboard /></PageWrapper></AdminRoute>} />
              <Route path="/admin/users" element={<AdminRoute><PageWrapper key="ad-user"><AdminUserDashboard /></PageWrapper></AdminRoute>} />
              <Route path="/admin/logs" element={<AdminRoute><PageWrapper key="ad-log"><AdminLogDashboard /></PageWrapper></AdminRoute>} />
              <Route path="/admin/policies" element={<AdminRoute><PageWrapper key="ad-pol"><AdminPolicyDashboard /></PageWrapper></AdminRoute>} />
              <Route path="/admin/ai-logs" element={<AdminRoute><PageWrapper key="ad-ai"><AdminAiLogDashboard /></PageWrapper></AdminRoute>} />
            </Route>

            {/* --- NHÓM 2: CÓ NAVBAR BẤT TỬ (Dashboard, Chuyển tiền, Lịch sử...) --- */}
            <Route element={<MainLayout />}>
              <Route path="/dashboard" element={<PageWrapper key="dash"><Dashboard /></PageWrapper>} />
              <Route path="/transfer" element={<PageWrapper key="trans"><TransferMethod /></PageWrapper>} />
              <Route path="/transfer/stk" element={<PageWrapper key="stk"><TransactionStep1 /></PageWrapper>} />
              <Route path="/history" element={<PageWrapper key="hist"><TransactionHistory /></PageWrapper>} />
              <Route path="/analytics" element={<PageWrapper key="analy"><ExpenseAnalytics /></PageWrapper>} />
              <Route path="/security" element={<PageWrapper key="sec"><SecuritySettings /></PageWrapper>} />
              <Route path="/account" element={<PageWrapper key="acc"><UserSettings /></PageWrapper>} />
              <Route path="/card-details" element={<PageWrapper key="card"><AccountProfile /></PageWrapper>} />
              <Route path="/transaction-result" element={<PageWrapper key="result"><TransactionResult /></PageWrapper>} />
            </Route>

          </Routes>
        </main>
      </div>
    </Router>
  );
}

export default App;