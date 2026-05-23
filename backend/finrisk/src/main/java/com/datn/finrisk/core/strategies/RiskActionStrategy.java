package com.datn.finrisk.core.strategies;  

import com.datn.finrisk.core.entities.Transaction;

public interface RiskActionStrategy {
 
    Transaction execute(Transaction transaction);
}