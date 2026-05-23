package com.datn.finrisk.application.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface IContactLastTransaction {
    Long getId();
    String getContactName();
    String getContactAccountNumber();
    Boolean getIsPinned();
    BigDecimal getLastAmount();  
    LocalDateTime getLastDate(); 
}