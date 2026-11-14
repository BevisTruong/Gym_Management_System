package com.gym.gym_backend.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import com.gym.gym_backend.auth.AuthService;
import com.gym.gym_backend.infrastructure.supabase.SupabaseDataClient;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final SupabaseDataClient db;
    private final AuthService authService;

    public JwtAuthenticationFilter(JwtService jwt, SupabaseDataClient db, AuthService authService) {
        this.jwt = jwt;
        this.db = db;
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            CurrentUser user = jwt.verify(token);
            if (user != null) {
                var accounts = db.select("account", Map.of("account_id", "eq." + user.accountId()), null, 1);
                if (accounts.isEmpty() || !"Active".equals(accounts.getFirst().get("status"))
                        || accounts.getFirst().get("password_hash") == null
                        || !jwt.matchesCredential(token, String.valueOf(accounts.getFirst().get("password_hash")))) {
                    user = null;
                } else {
                    user = authService.currentUser(accounts.getFirst());
                }
            }
            if (user != null) {
                var authentication = new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name().toUpperCase())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                String path = request.getRequestURI();
                if (user.mustChangePassword() && !path.endsWith("/api/auth/change-password")
                        && !path.endsWith("/api/auth/me") && !path.endsWith("/api/auth/logout")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"success\":false,\"message\":\"Bạn phải đổi mật khẩu tạm thời trước khi tiếp tục\"}");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

}
