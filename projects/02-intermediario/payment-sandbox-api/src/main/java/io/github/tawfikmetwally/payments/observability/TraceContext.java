package io.github.tawfikmetwally.payments.observability;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ProblemDetail;

public final class TraceContext {

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_REQUEST_ATTRIBUTE =
            TraceContext.class.getName() + ".traceId";

    private TraceContext() {
    }

    public static void addTo(ProblemDetail problem, HttpServletRequest request) {
        Object traceId = request.getAttribute(TRACE_ID_REQUEST_ATTRIBUTE);
        if (traceId instanceof String value && !value.isBlank()) {
            problem.setProperty(TRACE_ID, value);
        }
    }
}
