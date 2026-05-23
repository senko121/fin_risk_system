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

 
    @Value("${jwt.refreshExpiration}")
    private int jwtRefreshExpirationMs;
 
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }
 
    public String generateJwtToken(User user) {
 
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("role", user.getRole().name());  
        claims.put("fullName", user.getFullName());

        return Jwts.builder()
                .setClaims(claims) 
                .setSubject(user.getUsername())  
                .setIssuedAt(new Date())  
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))  
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)  
                .compact();
    }
 
    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .setSubject(user.getUsername())  
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtRefreshExpirationMs)) 
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

 
    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
 
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

    public String getRoleFromJwtToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return (String) claims.get("role");
    }
}