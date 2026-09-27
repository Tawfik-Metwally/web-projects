package io.github.tawfikmetwally.payments.security;

import io.github.tawfikmetwally.payments.observability.TraceContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

public final class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final BearerTokenAccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper objectMapper;

    public ProblemAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException, ServletException {
        if (response.isCommitted()) {
            return;
        }

        delegate.handle(request, response, exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "You do not have permission to perform this operation.");
        problem.setInstance(URI.create(request.getRequestURI()));
        TraceContext.addTo(problem, request);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
