package com.gumbatali.marketplace.security;

import com.gumbatali.marketplace.common.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtService = jwtService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            AuthenticatedUser user = jwtService.parseAccessToken(token);
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
            var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user,
                token,
                authorities
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException expired) {
            SecurityContextHolder.clearContext();
            request.setAttribute(RestAuthenticationEntryPoint.AUTH_ERROR_CODE_ATTR, ErrorCode.TOKEN_EXPIRED);
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("Token expired", expired));
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            request.setAttribute(RestAuthenticationEntryPoint.AUTH_ERROR_CODE_ATTR, ErrorCode.TOKEN_INVALID);
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("Token invalid", ex));
        }
    }
}
