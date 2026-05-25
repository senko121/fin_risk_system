 
package com.datn.finrisk.application.dtos;

import com.datn.finrisk.core.entities.User;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore; 
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;  
@Data
@NoArgsConstructor   
@AllArgsConstructor  
public class UserDTO {
    private Long id;
    private String username;
    private String fullName;
    private String phoneNumber;
    private String email;
    private String role;
    private boolean isFaceSetup; 
    private String accountNumber;
    private BigDecimal balance;

 
    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.fullName = user.getFullName();
        this.phoneNumber = user.getPhoneNumber();
        this.email = user.getEmail();
        this.role = user.getRole().name();
         this.isFaceSetup = user.hasFaceEmbedding() || user.hasLegacyFaceImage();
    }
}