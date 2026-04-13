import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Dashboard from './pages/Dashboard/Dashboard';
import TransactionStep1 from './pages/Transaction/TransactionStep1';
import Login from './pages/Auth/Login'; // Import trang Login mới

function App() {
  return (
    <Router>
      <div className="min-h-screen bg-gray-50 flex">
        {/* Nội dung chính */}
        <main className="flex-1">
          <Routes>
            {/* Mặc định vào thẳng trang Login */}
            <Route path="/" element={<Navigate to="/login" />} />
            
            <Route path="/login" element={<Login />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/transfer" element={<TransactionStep1 />} />
          </Routes>
        </main>
      </div>
    </Router>
  );
}

export default App;