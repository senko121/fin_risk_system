-- ============================================================
-- Risk Engine v3 — Rule Logic Fix
-- Vấn đề: category caps quá thấp khiến nhiều rules bị nuốt
-- Vấn đề: Rule 16 trùng SpEL với Rule 14
-- Chạy trực tiếp trên MySQL — idempotent (chạy lại không lỗi)
-- ============================================================

-- ── 1. Vô hiệu hóa Rule 16 (trùng hoàn toàn với Rule 14) ──
-- Rule 14 và Rule 16 cùng SpEL:
--   #isNewRecipient == true AND #tx.amount >= 5000000 AND #deviceTrusted == false
-- Cả 2 fire cùng lúc → COMPOSITE bucket nhận 75đ nhưng cap ở 40 → 35đ bị bỏ.
-- Giữ Rule 14 (điểm cao hơn, có min_policy_override MEDIUM_2).
UPDATE rules SET is_active = 0 WHERE id = 16;

-- ── 2. Kiểm tra kết quả rules active ──────────────────────
SELECT
    id,
    rule_name,
    category,
    rule_type,
    action_score,
    min_policy_override,
    is_active
FROM rules
ORDER BY category, id;

-- ── Ghi chú: Category caps được cập nhật trong Java code ──
-- File: RiskEvaluationService.java — CATEGORY_CAPS
--
--   Category     | Cap cũ | Cap mới | Lý do
--   -------------|--------|---------|-----------------------------------------------
--   CONTEXTUAL   |   20   |   35    | R1(20) + R9(25) = 45 → cap 35, cả 2 đóng góp
--   DEVICE       |   30   |   50    | R4(25) + R5(40) = 65 → cap 50, cả 2 đóng góp
--   FINANCIAL    |   40   |   55    | R8(35) + R11(40) = 75 → cap 55, cả 2 đóng góp
--   VELOCITY     |   30   |   40    | R10(35) < cap, không bị cắt bớt
--   COMPOSITE    |   40   |   55    | R14(45) + R15(25) = 70 → cap 55, cả 2 đóng góp
--   BIOMETRIC    |   55   |   55    | Giữ nguyên
--
-- ── Tác động lên scoring sau khi fix ──────────────────────
-- Tình huống                        | Trước | Sau
-- ----------------------------------|-------|-----
-- Thiết bị lạ + phiên đáng ngờ      |  30   |  50
-- Người nhận lạ + đêm khuya         |  20   |  35
-- Vét sạch (90%) + hạn mức ngày 20M |  40   |  55
-- Money Mule + ATO Drain            |  40   |  55
-- ============================================================
