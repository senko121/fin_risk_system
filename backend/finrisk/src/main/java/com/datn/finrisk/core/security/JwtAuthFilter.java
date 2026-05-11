// package com.datn.finrisk.core.security;

// import com.datn.finrisk.core.entities.User;
// import com.datn.finrisk.core.repository.UserRepository;
// import jakarta.servlet.FilterChain;
// import jakarta.servlet.ServletException;
// import jakarta.servlet.http.HttpServletRequest;
// import jakarta.servlet.http.HttpServletResponse;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// import org.springframework.security.core.GrantedAuthority;
// import org.springframework.security.core.authority.SimpleGrantedAuthority;
// import org.springframework.security.core.context.SecurityContextHolder;
// import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
// import org.springframework.stereotype.Component;
// import org.springframework.util.StringUtils;
// import org.springframework.web.filter.OncePerRequestFilter;

// import java.io.IOException;
// import java.util.Collections;
// import java.util.List;

// @Component
// public class JwtAuthFilter extends OncePerRequestFilter {

//     @Autowired
//     private JwtUtils jwtUtils;

//     @Autowired
//     private UserRepository userRepository;

//     @Override
//     protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
//             throws ServletException, IOException {
//         try {
//             // 1. Lấy JWT từ Header của request
//             String jwt = parseJwt(request);

//             // 2. Nếu có JWT và nó hợp lệ
//             if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                
//                 // Lấy username từ token
//                 String username = jwtUtils.getUserNameFromJwtToken(jwt);

//                 // Móc User từ Database lên
//                 User user = userRepository.findByUsername(username).orElse(null);

//                 if (user != null) {
//                     // 3. Cấp quyền cho User (Spring Security bắt buộc phải có chữ ROLE_ đứng trước)
//                     List<GrantedAuthority> authorities = Collections.singletonList(
//                             new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
//                     );

//                     // 4. Báo cáo với Spring Security là "Thằng này đã được xác thực an toàn"
//                     UsernamePasswordAuthenticationToken authentication =
//                             new UsernamePasswordAuthenticationToken(user, null, authorities);
//                     authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

//                     SecurityContextHolder.getContext().setAuthentication(authentication);
//                 }
//             }
//         } catch (Exception e) {
//             System.err.println("Không thể xác thực người dùng: " + e.getMessage());
//         }

//         // Cho phép request đi tiếp vào Controller
//         filterChain.doFilter(request, response);
//     }

//     // Hàm phụ: Bóc tách chữ "Bearer " để lấy đúng cái mã Token
//     private String parseJwt(HttpServletRequest request) {
//         String headerAuth = request.getHeader("Authorization");
//         if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
//             return headerAuth.substring(7); // Cắt bỏ 7 ký tự "Bearer "
//         }
//         return null;
//     }
// }

package com.datn.finrisk.core.security;

import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 1. Lấy JWT từ Header của request
            String jwt = parseJwt(request);

            // 2. Nếu có JWT và nó hợp lệ
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                String role = jwtUtils.getRoleFromJwtToken(jwt); // thêm hàm này bên dưới
                List<GrantedAuthority> authorities = Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + role)
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            System.err.println("Không thể xác thực người dùng: " + e.getMessage());
        }

        // Cho phép request đi tiếp vào Controller
        filterChain.doFilter(request, response);
    }

    // Hàm phụ: Bóc tách chữ "Bearer " để lấy đúng cái mã Token
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7); // Cắt bỏ 7 ký tự "Bearer "
        }
        return null;
    }
}