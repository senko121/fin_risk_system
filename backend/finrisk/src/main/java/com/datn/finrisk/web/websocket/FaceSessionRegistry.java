package com.datn.finrisk.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for which WebSocketSession belongs to the face channel
 * for a given biometric session token. VerificationSyncManager always sends
 * FINAL_RESULT here, regardless of which AI stream completes last.
 */
@Component
public class FaceSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(FaceSessionRegistry.class);

    private final ConcurrentHashMap<String, WebSocketSession> registry = new ConcurrentHashMap<>();

    public void register(String bsToken, WebSocketSession session) {
        registry.put(bsToken, session);
        log.debug("[FACE-REGISTRY] registered bsToken={} session={}", bsToken, session.getId());
    }

    /** Only removes the entry if the caller's session is still the current owner. */
    public void unregisterIfOwner(String bsToken, WebSocketSession session) {
        boolean removed = registry.remove(bsToken, session);
        if (removed) {
            log.debug("[FACE-REGISTRY] unregistered bsToken={} session={}", bsToken, session.getId());
        }
    }

    public Optional<WebSocketSession> get(String bsToken) {
        return Optional.ofNullable(registry.get(bsToken));
    }
}
