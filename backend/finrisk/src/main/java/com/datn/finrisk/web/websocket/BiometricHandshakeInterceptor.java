package com.datn.finrisk.web.websocket;

import com.datn.finrisk.core.entities.BiometricSession;
import com.datn.finrisk.core.repository.BiometricSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
public class BiometricHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private BiometricSessionRepository biometricSessionRepository;

    @Autowired
    private WsConnectionGuard wsConnectionGuard;

   @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String clientIp = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        
        boolean acquired = false; // Cờ đánh dấu đã chiếm slot thành công chưa

        try {
            String query = request.getURI().getQuery();

            // 1. Require bsToken (đã được đẩy lên trước)
            if (query == null) {
                log.warn("[WS-SECURITY] ip={} — missing query string, rejecting", clientIp);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            String bsToken = extractParam(query, "bsToken");
            if (bsToken == null) {
                log.warn("[WS-SECURITY] ip={} — missing bsToken, rejecting", clientIp);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // 2. Validate bsToken against DB record (Làm trước khi chiếm slot)
                BiometricSession biometricSession = biometricSessionRepository
                        .findBySessionTokenEager(bsToken)
                        .orElse(null);

            if (biometricSession == null) {
                log.warn("[WS-SECURITY] ip={} — bsToken not found", clientIp);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            if (Boolean.TRUE.equals(biometricSession.getUsed())) {
                log.warn("[WS-SECURITY] ip={} user={} — bsToken already used (replay attempt)",
                        clientIp, biometricSession.getUser().getUsername());
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            if (Boolean.TRUE.equals(biometricSession.getFinalized())) {
                log.warn("[WS-SECURITY] ip={} user={} — bsToken already finalized (no retries left)", 
                        clientIp, biometricSession.getUser().getUsername());
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            if (biometricSession.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("[WS-SECURITY] ip={} user={} — bsToken expired",
                        clientIp, biometricSession.getUser().getUsername());
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            // 3. Chỉ chiếm slot SAU KHI token hoàn toàn hợp lệ
            if (!wsConnectionGuard.tryAcquire(clientIp)) {
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return false;
            }
            acquired = true; // Đánh dấu đã acquire thành công!

            // 4. Derive authenticated user từ DB record
            String username = biometricSession.getUser().getUsername();
            attributes.put("authenticatedUser", username);
            attributes.put("biometricSession", biometricSession);
            attributes.put("clientIp", clientIp);

            log.info("[WS-SECURITY] ip={} user={} — handshake accepted", clientIp, username);
            return true;

        } catch (Exception e) {
            log.error("[WS-SECURITY] ip={} — handshake exception: {}", clientIp, e.getMessage());
            
            // CỨU CÁNH: Nếu đã lỡ acquire mà bên trong try block bị crash -> Phải nhả slot ngay
            if (acquired) {
                log.info("[WS-SECURITY] ip={} — Emergency release due to handshake exception", clientIp);
                wsConnectionGuard.release(clientIp);
            }
            
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        if (exception != null) {
            // The WS upgrade itself failed after beforeHandshake returned true.
            // The connection will never open, so afterConnectionClosed will not fire.
            // Release the IP slot so it is not leaked.
            String clientIp = request.getRemoteAddress() != null
                    ? request.getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            log.warn("[WS-SECURITY] ip={} — post-handshake error, releasing slot: {}", clientIp, exception.getMessage());
            wsConnectionGuard.release(clientIp);
        }
    }

    /**
     * Extracts a named query parameter from a raw query string, with URL-decoding.
     * Handles encoded characters (e.g. %2B, %3D) that would break a plain split().
     */
    private String extractParam(String query, String paramName) {
        String prefix = paramName + "=";
        for (String part : query.split("&")) {
            if (part.startsWith(prefix)) {
                String raw = part.substring(prefix.length());
                try {
                    return URLDecoder.decode(raw, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return raw;
                }
            }
        }
        return null;
    }
}
