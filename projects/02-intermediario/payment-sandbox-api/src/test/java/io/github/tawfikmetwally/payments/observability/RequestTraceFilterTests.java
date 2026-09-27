package io.github.tawfikmetwally.payments.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestTraceFilterTests {

    private Logger logger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void captureRequestLogs() {
        logger = (Logger) LoggerFactory.getLogger(RequestTraceFilter.class);
        logAppender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void cleanLoggingContext() {
        logger.detachAppender(logAppender);
        logAppender.stop();
        MDC.clear();
    }

    @Test
    void addsTraceContextAndLogsOnlyControlledRequestMetadata() throws Exception {
        String traceId = "trace-test-001";
        String privateToken = "private-bearer-token";
        String privateIdempotencyKey = "private-idempotency-key";
        String privatePaymentToken = "private-payment-method-token";
        var filter = new RequestTraceFilter(() -> traceId);
        var request = new MockHttpServletRequest("POST", "/api/v1/payments");
        var response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer " + privateToken);
        request.addHeader("Idempotency-Key", privateIdempotencyKey);
        request.setQueryString("secret=private-query-value");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(
                ("{\"paymentMethodToken\":\"" + privatePaymentToken + "\"}").getBytes(StandardCharsets.UTF_8));

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            assertThat(MDC.get(TraceContext.TRACE_ID)).isEqualTo(traceId);
            assertThat(httpRequest.getAttribute(TraceContext.TRACE_ID_REQUEST_ATTRIBUTE))
                    .isEqualTo(traceId);
            assertThat(httpResponse.getHeader(TraceContext.TRACE_ID_HEADER)).isEqualTo(traceId);
            httpRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/payments");
            httpResponse.setStatus(HttpStatus.CREATED.value());
        });

        assertThat(MDC.get(TraceContext.TRACE_ID)).isNull();
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent log = logAppender.list.get(0);
        assertThat(log.getLevel()).isEqualTo(Level.INFO);
        assertThat(log.getMDCPropertyMap()).containsEntry(TraceContext.TRACE_ID, traceId);
        assertThat(log.getFormattedMessage())
                .contains("method=POST", "route=/api/v1/payments", "status=201", "durationMs=")
                .doesNotContain(privateToken, privateIdempotencyKey, privatePaymentToken, "private-query-value");
    }

    @Test
    void generatesAUniqueUuidForEachRequest() throws Exception {
        var filter = new RequestTraceFilter();
        var firstRequest = new MockHttpServletRequest("GET", "/api/v1/payments");
        var firstResponse = new MockHttpServletResponse();
        var secondRequest = new MockHttpServletRequest("GET", "/api/v1/payments");
        var secondResponse = new MockHttpServletResponse();

        filter.doFilter(firstRequest, firstResponse, (request, response) -> {});
        filter.doFilter(secondRequest, secondResponse, (request, response) -> {});

        String firstTraceId = firstResponse.getHeader(TraceContext.TRACE_ID_HEADER);
        String secondTraceId = secondResponse.getHeader(TraceContext.TRACE_ID_HEADER);
        assertThat(UUID.fromString(firstTraceId)).isNotNull();
        assertThat(UUID.fromString(secondTraceId)).isNotNull();
        assertThat(firstTraceId).isNotEqualTo(secondTraceId);
    }

    @Test
    void logsFailureTypeWithoutLeakingExceptionMessageAndRestoresPreviousContext() {
        String privateMessage = "private-database-diagnostic";
        var filter = new RequestTraceFilter(() -> "trace-failure-001");
        var request = new MockHttpServletRequest("GET", "/api/v1/payments");
        var response = new MockHttpServletResponse();
        MDC.put(TraceContext.TRACE_ID, "outer-trace");

        assertThatThrownBy(() -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                    throw new ServletException(privateMessage);
                }))
                .isInstanceOf(ServletException.class)
                .hasMessage(privateMessage);

        assertThat(MDC.get(TraceContext.TRACE_ID)).isEqualTo("outer-trace");
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent log = logAppender.list.get(0);
        assertThat(log.getLevel()).isEqualTo(Level.ERROR);
        assertThat(log.getMDCPropertyMap()).containsEntry(TraceContext.TRACE_ID, "trace-failure-001");
        assertThat(log.getFormattedMessage())
                .contains("method=GET", "route=unmapped", "status=500", "failureType=jakarta.servlet.ServletException")
                .doesNotContain(privateMessage);
    }
}
