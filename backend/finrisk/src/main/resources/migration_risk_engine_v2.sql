-- ============================================================
-- Risk Engine v2 — Data Migration
-- Chạy file này trực tiếp trên MySQL (không cần restart app trước).
-- IF NOT EXISTS đảm bảo idempotent — chạy lại nhiều lần không lỗi.
-- ============================================================

-- ── Tạo cột mới nếu chưa có ──────────────────────────────────
ALTER TABLE rules ADD COLUMN IF NOT EXISTS category  VARCHAR(20)  NULL;
ALTER TABLE rules ADD COLUMN IF NOT EXISTS rule_type VARCHAR(20)  NOT NULL DEFAULT 'ADDITIVE';

-- ── Gắn category + rule_type cho rules hiện tại ──────────────

-- CONTEXTUAL: tín hiệu yếu, cần kết hợp mới có ý nghĩa
UPDATE rules SET category = 'CONTEXTUAL', rule_type = 'ADDITIVE'
WHERE id IN (1, 9);   -- Tài khoản nhận lạ, Giao dịch đêm khuya

-- BIOMETRIC: FEAR là emergency → VETO bypass scoring hoàn toàn
UPDATE rules SET category = 'BIOMETRIC', rule_type = 'VETO'
WHERE id = 3;         -- Phát hiện Sợ hãi (FEAR)

-- BIOMETRIC: STRESS vẫn additive (không phải emergency)
UPDATE rules SET category = 'BIOMETRIC', rule_type = 'ADDITIVE'
WHERE id = 2;         -- Phát hiện Căng thẳng (STRESS)

-- DEVICE: device + session cùng domain → cap 30 ngăn correlated inflation
UPDATE rules SET category = 'DEVICE', rule_type = 'ADDITIVE'
WHERE id IN (4, 5);   -- Thiết bị lạ, Phiên rủi ro vị trí/IP

-- FINANCIAL: ngưỡng tài chính + regulatory
UPDATE rules SET category = 'FINANCIAL', rule_type = 'ADDITIVE'
WHERE id IN (6, 7, 8, 11);  -- Micro tx, QĐ2345, Vét sạch, Daily limit

-- VELOCITY: tần suất + pattern bất thường
UPDATE rules SET category = 'VELOCITY', rule_type = 'ADDITIVE'
WHERE id = 10;        -- Tần suất giao dịch cao

-- COMPOSITE: compound rule đã có (Rule 14)
UPDATE rules SET category = 'COMPOSITE', rule_type = 'ADDITIVE'
WHERE id = 14;        -- Cảnh báo Cold Start nghiêm trọng

-- ── Fix bugs data ────────────────────────────────────────────

-- Rule 14: bỏ điều kiện recentTxCount <= 2 (sai semantic — đây là count/phút,
-- không phải tổng GD của tài khoản. Điều kiện này luôn true ~99% GD bình thường)
UPDATE rules
SET spel_expression = '#isNewRecipient == true AND #tx.amount >= 5000000 AND #deviceTrusted == false',
    description     = 'Thiết bị lạ + người lạ + số tiền >= 5tr — tín hiệu Money Mule'
WHERE id = 14;

-- Rule 7: sửa description sai (nó không cộng điểm, chỉ override policy)
UPDATE rules
SET description = 'Ép mức MEDIUM_2 theo QĐ 2345/NHNN — không cộng điểm rủi ro (action_score=0)'
WHERE id = 7;

-- Rule 5: suspicious session một mình nên tối thiểu MEDIUM_1
UPDATE rules
SET min_policy_override = 'MEDIUM_1'
WHERE id = 5 AND (min_policy_override IS NULL OR min_policy_override = '');

-- ── Thêm composite synergy rules ────────────────────────────
-- Các rule này capture COMBINATION nguy hiểm, đóng góp vào COMPOSITE bucket.
-- SpEL multi-condition → fire chỉ khi cả hai/ba signals cùng xuất hiện.
-- Đây là nguồn non-linearity: base rules bị cap, composite rules thêm bonus.

INSERT INTO rules (rule_name, description, spel_expression, action_score,
                   category, rule_type, is_active, min_policy_override)
VALUES
(
    'ATO Drain Pattern',
    'Thiết bị lạ + chuyển tiền >= 5tr — dấu hiệu Account Takeover drain',
    '#deviceTrusted == false AND #tx.amount >= 5000000',
    25, 'COMPOSITE', 'ADDITIVE', true, NULL
),
(
    'Money Mule Triple Signal',
    'Thiết bị lạ + người lạ + số tiền >= 5tr — dấu hiệu Money Mule',
    '#isNewRecipient == true AND #tx.amount >= 5000000 AND #deviceTrusted == false',
    30, 'COMPOSITE', 'ADDITIVE', true, 'MEDIUM_2'
);

-- ── Kiểm tra kết quả ─────────────────────────────────────────
SELECT id, rule_name, category, rule_type, action_score, min_policy_override
FROM rules
ORDER BY category, id;
