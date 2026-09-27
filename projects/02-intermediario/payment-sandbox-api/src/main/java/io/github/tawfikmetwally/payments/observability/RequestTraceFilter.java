package io.github.tawfikmetwally.payments.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestTraceFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTraceFilter.class);
    private static final String UNMAPPED_ROUTE = "unmapped";

    private final Supplier<String> traceIdSupplier;

    public RequestTraceFilter() {
        this(() -> UUID.randomUUID().toString());
    }

    RequestTraceFilter(Supplier<String> traceIdSupplier) {
        this.traceIdSupplier = traceIdSupplier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = traceIdSupplier.get();
        String previousTraceId = MDC.get(TraceContext.TRACE_ID);
        long startedAt = System.nanoTime();
        Exception failure = null;

        request.setAttribute(TraceContext.TRACE_ID_REQUEST_ATTRIBUTE, traceId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        MDC.put(TraceContext.TRACE_ID, traceId);

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            try {
                long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
                if (failure == null) {
                    LOGGER.info(
                            "HTTP request completed method={} route={} status={} durationMs={}",
                            request.getMethod(),
                            route(request),
                            response.getStatus(),
                            durationMillis);
                } else {
                    LOGGER.error(
                            "HTTP request failed method={} route={} status=500 durationMs={} failureType={}",
                            request.getMethod(),
                            route(request),
                            durationMillis,
                            failure.getClass().getName());
                }
            } finally {
                restoreTraceId(previousTraceId);
            }
        }
    }

    private String route(HttpServletRequest request) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return route instanceof String pattern ? pattern : UNMAPPED_ROUTE;
    }

    private void restoreTraceId(String previousTraceId) {
        if (previousTraceId == null) {
            MDC.remove(TraceContext.TRACE_ID);
        } else {
            MDC.put(TraceContext.TRACE_ID, previousTraceId);
        }
    }
}
