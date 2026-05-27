package com.datn.finrisk.application.dtos;

import lombok.Data;
import java.util.List;

@Data
public class FaceRegisterBatchRequest {
    private Long userId;
    /** Danh sách ảnh base64 — thứ tự khuyến nghị: front, left, right, up, down. */
    private List<String> imagesBase64;
}
