import React from 'react';
import { Navigate } from 'react-router-dom';

export default function AdminRoute({ children }) {
  // Lấy thẻ căn cước (thông tin user) từ LocalStorage
  // LƯU Ý: Phải đảm bảo lúc Login, ông lưu user dưới tên 'currentUser' nhé!
  const currentUser = JSON.parse(localStorage.getItem('currentUser'));

  // Nếu không có ai đăng nhập, hoặc đăng nhập rồi nhưng role KHÔNG PHẢI là ADMIN
  if (!currentUser || currentUser.role !== 'ADMIN') {
    // Navigate sẽ lập tức "đá" thằng user này về trang /dashboard
    // Thuộc tính replace giúp xóa lịch sử trang /admin, tránh việc user bấm nút Back trên trình duyệt để quay lại.
    return <Navigate to="/dashboard" replace />;
  }

  // Nếu qua được ải trên (đúng là ADMIN) -> Mở cửa cho vào trang bên trong (children)
  return children;
}