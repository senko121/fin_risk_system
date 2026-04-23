package com.datn.finrisk.core.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Tắt CSRF vì mình dùng Token (Cú pháp của Spring Boot 3)
        http.csrf(csrf -> csrf.disable())
            
            // Cho phép CORS đi qua (để React gọi được API)
            .cors(cors -> cors.configure(http)) 
            
            // Báo cho Spring biết là hệ thống không dùng Session (Stateless)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            //   ĐÂY LÀ CHỖ CHIA ĐƯỜNG KẺ VẠCH ĐÂY
            .authorizeHttpRequests(auth -> auth
                // 1. Cho phép thả cửa khu vực Đăng nhập / Làm mới Token / Đăng ký mặt
                .requestMatchers("/api/auth/**").permitAll()
                
                // 2. Khu vực Admin Dashboard -> CHỈ AI CÓ ROLE ADMIN MỚI ĐƯỢC VÀO
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                
                // 3. Tất cả các API còn lại (Chuyển tiền, OTP...) -> Phải có Token hợp lệ
                .anyRequest().authenticated()
            );

        // Chèn cái Trạm Gác (JwtAuthFilter) của mình lên đứng trước để soát vé
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    //   MUA MÁY BĂM MẬT KHẨU (KHAI BÁO BEAN)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}