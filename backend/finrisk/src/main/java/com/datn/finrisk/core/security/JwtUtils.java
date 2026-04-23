package com.datn.finrisk.core.security;

import com.datn.finrisk.core.entities.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private int jwtExpirationMs;

    //   BỔ SUNG 1: Khai báo thời gian sống của Refresh Token (đọc từ application.yml)
    @Value("${jwt.refreshExpiration}")
    private int jwtRefreshExpirationMs;

    // Tạo chìa khóa từ chuỗi Secret
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // 1. HÀM TẠO ACCESS TOKEN (Vé vào cửa 5 phút, kẹp full thông tin)
    public String generateJwtToken(User user) {
        // Gói thêm ID và Role vào Token để Frontend đọc cho dễ
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("role", user.getRole().name()); // Lấy chữ "ADMIN" hoặc "USER"
        claims.put("fullName", user.getFullName());

        return Jwts.builder()
                .setClaims(claims) // Nhét dữ liệu phụ vào
                .setSubject(user.getUsername()) // Subject chính là tên đăng nhập
                .setIssuedAt(new Date()) // Thời gian phát hành
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs)) // Thời gian hết hạn
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // Đóng mộc mã hóa
                .compact();
    }

    // =========================================================
    //   BỔ SUNG 2: HÀM MỚI - TẠO REFRESH TOKEN (Vé gia hạn 7 ngày)
    // =========================================================
    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .setSubject(user.getUsername()) // Chỉ cần username để nhận diện là đủ
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtRefreshExpirationMs)) // Dùng hạn 7 ngày
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // 2. HÀM LẤY USERNAME TỪ TOKEN (Để soi xem ai đang request)
    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    // 3. HÀM KIỂM TRA TOKEN CÓ HỢP LỆ KHÔNG (Bị sửa chữa, hết hạn...)
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Token JWT không đúng định dạng: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Token JWT đã hết hạn: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Token JWT không được hỗ trợ: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Chuỗi claims JWT trống: {}", e.getMessage());
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.error("Chữ ký JWT không hợp lệ: {}", e.getMessage());
        }
        return false;
    }
}