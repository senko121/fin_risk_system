package com.datn.finrisk.core.strategies; // Hoặc package com.datn.finrisk.core.strategies;

import com.datn.finrisk.core.entities.Transaction;

public interface RiskActionStrategy {
    // Hàm này sẽ nhận vào Giao dịch và trả về Giao dịch đã được xử lý (Cập nhật status)
    Transaction execute(Transaction transaction);
}