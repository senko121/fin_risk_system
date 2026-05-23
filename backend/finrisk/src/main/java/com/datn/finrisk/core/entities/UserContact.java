package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString; 
import lombok.EqualsAndHashCode; 
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;  

@Entity
@Table(name = "user_contacts")
@Data
public class UserContact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

 
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;

    private String contactAccountNumber;
    private String contactName;
    private boolean isPinned = false;
    private LocalDateTime createdAt = LocalDateTime.now();
}