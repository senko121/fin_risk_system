package com.datn.finrisk.web.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WebSocketMetricsLogger {

    @Autowired
    private LiveEmotionWebSocketHandler liveEmotionWebSocketHandler;

    @Scheduled(fixedRate = 300000) // Định kỳ 30 giây chạy 1 lần (30000ms)
    public void logWebSocketMetrics() {
        try {
            // Sử dụng Reflection để đọc an toàn bộ đệm từ LiveEmotionWebSocketHandler
            Field bufferField = LiveEmotionWebSocketHandler.class.getDeclaredField("sessionFrameBuffer");
            bufferField.setAccessible(true);
            Map<?, ?> sessionFrameBuffer = (Map<?, ?>) bufferField.get(liveEmotionWebSocketHandler);

            int activeSessions = sessionFrameBuffer.size();
            int totalBufferedFrames = 0;
            long totalBytes = 0;

            for (Object listObj : sessionFrameBuffer.values()) {
                if (listObj instanceof List<?>) {
                    List<?> list = (List<?>) listObj;
                    totalBufferedFrames += list.size();
                    
                    // Tính toán dung lượng byte thực tế của các khung hình trong RAM
                    for (Object frameObj : list) {
                        Field dataField = frameObj.getClass().getDeclaredField("data");
                        dataField.setAccessible(true);
                        byte[] data = (byte[]) dataField.get(frameObj);
                        if (data != null) {
                            totalBytes += data.length;
                        }
                    }
                }
            }

            double bufferMB = (double) totalBytes / (1024 * 1024);

            // In báo cáo cấu trúc chuẩn lên hệ thống log giám sát
            log.info("[WS-METRICS] active_sessions={} total_buffered_frames={} total_buffer_size={ Milan}MB", 
                    activeSessions, totalBufferedFrames, String.format("%.2f", bufferMB));

        } catch (Exception e) {
            log.warn("[WS-METRICS] Không thể trích xuất chỉ số đo lường hệ thống: {}", e.getMessage());
        }
    }
}