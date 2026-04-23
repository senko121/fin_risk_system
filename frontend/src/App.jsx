import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
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
import AdminRuleDashboard from './pages/Admin/AdminRuleDashboard';
import AdminUserDashboard from './pages/Admin/AdminUserDashboard';

function App() {
  return (
    <Router>
      <div className="min-h-screen bg-gray-50 flex">
        {/* Nội dung chính */}
        <main className="flex-1">
          
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
            {/*   TRONG NÀY BÂY GIỜ CHỈ CÓ ROUTE THÔI */}
            <Route path="/" element={<Navigate to="/login" />} />
            <Route path="/login" element={<Login />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/transfer" element={<TransactionStep1 />} />
            <Route path="/verify-otp" element={<OTPVerification />} />
            <Route path="/transaction-result" element={<TransactionResult />} />
            <Route path="/register-face" element={<FaceRegister />} />
            <Route path="/verify-face" element={<FaceVerification />} />
            <Route path="/history" element={<TransactionHistory />} />
            <Route path="/analytics" element={<ExpenseAnalytics />} />
            <Route path="/security" element={<SecuritySettings />} />
            <Route path="/change-password" element={<ChangePassword />} />

            <Route path="/admin" element={<AdminRoute> <AdminDashboard /> </AdminRoute>} />
            <Route path="/admin/rules" element={<AdminRoute> <AdminRuleDashboard /> </AdminRoute>} />
            <Route path="/admin/users" element={<AdminRoute> <AdminUserDashboard /> </AdminRoute>} />

          </Routes>
          
        </main>
      </div>
    </Router>
  );
}

export default App;