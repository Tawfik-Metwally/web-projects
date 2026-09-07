package io.github.tawfikmetwally.payments.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class DemoMerchantAuthenticationFilter extends OncePerRequestFilter {

    private static final String MERCHANT_HEADER = "X-Demo-Merchant-Id";
    private static final int MAX_MERCHANT_ID_LENGTH = 100;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String apiPrefix = request.getContextPath() + "/api/";
        return !request.getRequestURI().startsWith(apiPrefix);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String merchantId = request.getHeader(MERCHANT_HEADER);
        if (merchantId != null) {
            merchantId = merchantId.trim();
        }

        if (isValid(merchantId)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    merchantId,
                    null,
                    List.of());
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValid(String merchantId) {
        return merchantId != null
                && !merchantId.isBlank()
                && merchantId.length() <= MAX_MERCHANT_ID_LENGTH;
    }
}
