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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

public final class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    private final ObjectMapper objectMapper;

    public ProblemAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        if (response.isCommitted()) {
            return;
        }

        delegate.commence(request, response, safeException(exception));
        HttpStatus status = HttpStatus.valueOf(response.getStatus());
        String detail = status == HttpStatus.BAD_REQUEST
                ? "The authentication request is invalid."
                : "A valid access token is required.";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        TraceContext.addTo(problem, request);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private AuthenticationException safeException(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            OAuth2Error error = oauthException.getError();
            // Preserve the protocol code/status without publishing decoder diagnostics.
            OAuth2Error safeError = error instanceof BearerTokenError bearerError
                    ? new BearerTokenError(
                            error.getErrorCode(), bearerError.getHttpStatus(), null, null, bearerError.getScope())
                    : new OAuth2Error(error.getErrorCode());
            return new OAuth2AuthenticationException(safeError);
        }
        return exception;
    }
}
