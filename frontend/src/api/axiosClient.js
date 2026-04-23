import axios from 'axios';

// Tạo một thằng lính đánh thuê cấu hình sẵn
const axiosClient = axios.create({
    baseURL: 'http://localhost:8081/api', // Địa chỉ gốc của Backend
    headers: {
        'Content-Type': 'application/json',
    },
});

// NHIỆM VỤ 1: KHI GỬI ĐƠN ĐI (Tự động gắn Access Token)
axiosClient.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('accessToken');
        if (token) {
            config.headers['Authorization'] = 'Bearer ' + token;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// NHIỆM VỤ 2: KHI NHẬN HÀNG VỀ (Xử lý khi Access Token bị hết hạn 5 phút)
axiosClient.interceptors.response.use(
    (response) => response, 
    
    async (error) => {
        const originalRequest = error.config;
        
        // Nếu Backend chửi 401 (Hết hạn) VÀ chưa thử xin lại lần nào
        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true; 
            
            try {
                // 1. Lấy CMND gốc (Refresh Token) ra
                const refreshToken = localStorage.getItem('refreshToken');
                if (!refreshToken) throw new Error("No refresh token"); // Bắt lỗi nếu không có thẻ luôn
                
                // 2. Chạy qua API xin thẻ mới
                const res = await axios.post('http://localhost:8081/api/auth/refresh-token', { refreshToken });
                
                // 3. Cất thẻ mới vào ví
                const newAccessToken = res.data.accessToken;
                const newRefreshToken = res.data.refreshToken; // Cập nhật luôn thẻ phụ nếu có
                localStorage.setItem('accessToken', newAccessToken);
                if (newRefreshToken) localStorage.setItem('refreshToken', newRefreshToken);
                
                // 4. Dán thẻ mới vào cái Request đang bị lỗi lúc nãy, và bắt nó chạy lại
                originalRequest.headers['Authorization'] = 'Bearer ' + newAccessToken;
                return axiosClient(originalRequest);
                
            } catch (refreshError) {
                //   NÂNG CẤP Ở ĐÂY: Gắn một cái "Nhãn dán" báo hiệu cái chết hoàn toàn
                console.error("Hết hạn toàn bộ Token, bắt buộc đăng nhập lại!");
                // Trả về lỗi có đánh dấu để thằng Dashboard biết đường xử lý
                return Promise.reject({ ...refreshError, isSessionExpired: true }); 
            }
        }
        
        // Bắt luôn lỗi 403 (Forbidden) lỡ dính
        if (error.response?.status === 403) {
             return Promise.reject({ ...error, isSessionExpired: true });
        }

        return Promise.reject(error);
    }
);

export default axiosClient;