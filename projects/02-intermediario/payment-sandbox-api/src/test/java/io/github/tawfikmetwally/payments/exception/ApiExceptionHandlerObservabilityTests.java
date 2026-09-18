package io.github.tawfikmetwally.payments.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import io.github.tawfikmetwally.payments.observability.TraceContext;

class ApiExceptionHandlerObservabilityTests {

    private Logger logger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void captureHandlerLogs() {
        logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void stopCapturingHandlerLogs() {
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void returnsCorrelatedSafeProblemAndDoesNotLogExceptionMessage() {
        String privateMessage = "private-database-diagnostic";
        var servletRequest = new MockHttpServletRequest("POST", "/api/v1/payments");
        servletRequest.setAttribute(TraceContext.TRACE_ID_REQUEST_ATTRIBUTE,
                "trace-handler-test");
        var request = new ServletWebRequest(servletRequest);
        var exception = new IllegalStateException(privateMessage);

        var response = new ApiExceptionHandler().handleUnexpectedFailure(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getDetail()).isEqualTo("An internal server error occurred.");
        assertThat(problem.getInstance()).hasToString("/api/v1/payments");
        assertThat(problem.getProperties())
                .containsEntry(TraceContext.TRACE_ID, "trace-handler-test");
        assertThat(problem.toString()).doesNotContain(privateMessage);

        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent log = logAppender.list.get(0);
        assertThat(log.getLevel()).isEqualTo(Level.ERROR);
        assertThat(log.getFormattedMessage())
                .contains("failureType=java.lang.IllegalStateException", "origin=")
                .doesNotContain(privateMessage);
    }
}
