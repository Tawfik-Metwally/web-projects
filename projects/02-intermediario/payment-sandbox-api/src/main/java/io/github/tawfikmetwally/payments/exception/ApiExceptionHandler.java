package io.github.tawfikmetwally.payments.exception;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.tawfikmetwally.payments.observability.TraceContext;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedFailure(
            Exception exception, WebRequest request) {
        logUnexpectedFailure(exception);
        return businessProblem(exception, HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal server error occurred.", request);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Object> handlePaymentNotFound(
            PaymentNotFoundException exception, WebRequest request) {
        return businessProblem(exception, HttpStatus.NOT_FOUND,
                "Payment was not found.", request);
    }

    @ExceptionHandler(PaymentNotRefundableException.class)
    public ResponseEntity<Object> handlePaymentNotRefundable(
            PaymentNotRefundableException exception, WebRequest request) {
        return businessProblem(exception, HttpStatus.CONFLICT,
                "Payment is not eligible for a refund.", request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Object> handleIdempotencyConflict(
            IdempotencyConflictException exception, WebRequest request) {
        return businessProblem(exception, HttpStatus.CONFLICT,
                "Idempotency key was already used with different request data.", request);
    }

    @ExceptionHandler(UnsupportedPaymentMethodTokenException.class)
    public ResponseEntity<Object> handleUnsupportedPaymentMethodToken(
            UnsupportedPaymentMethodTokenException exception, WebRequest request) {
        return businessProblem(exception, HttpStatus.BAD_REQUEST,
                "Unsupported payment method token.", request);
    }

    private ResponseEntity<Object> businessProblem(
            Exception exception, HttpStatus status, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return finalizeProblem(exception, problem, new HttpHeaders(), status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // Use controlled messages rather than exception messages or rejected values.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                safeDetail(exception, status));
        if (!status.is5xxServerError()) {
            List<FieldViolation> errors = validationErrors(exception);
            if (!errors.isEmpty()) {
                problem.setProperty("errors", errors);
            }
        }
        return finalizeProblem(exception, problem, headers, status, request);
    }

    private ResponseEntity<Object> finalizeProblem(
            Exception exception, ProblemDetail problem, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
            TraceContext.addTo(problem, servletRequest.getRequest());
        }
        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }

    private void logUnexpectedFailure(Exception exception) {
        StackTraceElement[] stackTrace = exception.getStackTrace();
        if (stackTrace.length == 0) {
            LOGGER.error("Unhandled request failure failureType={}",
                    exception.getClass().getName());
            return;
        }
        StackTraceElement origin = stackTrace[0];
        LOGGER.error("Unhandled request failure failureType={} origin={}.{}:{}",
                exception.getClass().getName(), origin.getClassName(),
                origin.getMethodName(), origin.getLineNumber());
    }

    private String safeDetail(Exception exception, HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return "An internal server error occurred.";
        }
        if (exception instanceof MethodArgumentNotValidException
                || exception instanceof HandlerMethodValidationException) {
            return "Request validation failed.";
        }
        if (exception instanceof ResponseStatusException responseStatus
                && status.value() == 400) {
            // These are the explicit, non-sensitive currency errors in PaymentController.
            if ("Invalid currency".equals(responseStatus.getReason())) {
                return "Invalid currency.";
            }
            if ("Only BRL is supported".equals(responseStatus.getReason())) {
                return "Only BRL is supported.";
            }
        }
        return switch (status.value()) {
            case 400 -> "Request content or parameters are invalid.";
            case 401 -> "Authentication is required.";
            case 403 -> "Access is denied.";
            case 404 -> "Resource was not found.";
            case 405 -> "HTTP method is not supported for this resource.";
            case 406 -> "Requested response media type is not supported.";
            case 415 -> "Request media type is not supported.";
            default -> "The request could not be processed.";
        };
    }

    private List<FieldViolation> validationErrors(Exception exception) {
        List<FieldViolation> errors = new ArrayList<>();
        if (exception instanceof MethodArgumentNotValidException validation) {
            for (FieldError error : validation.getBindingResult().getFieldErrors()) {
                errors.add(new FieldViolation(error.getField(), validationMessage(error)));
            }
        } else if (exception instanceof HandlerMethodValidationException validation) {
            for (var result : validation.getParameterValidationResults()) {
                if (result instanceof ParameterErrors parameterErrors) {
                    for (FieldError error : parameterErrors.getFieldErrors()) {
                        errors.add(new FieldViolation(error.getField(), validationMessage(error)));
                    }
                } else {
                    String field = parameterName(result.getMethodParameter());
                    for (var error : result.getResolvableErrors()) {
                        errors.add(new FieldViolation(field, validationMessage(error)));
                    }
                }
            }
        }
        return errors.stream().distinct()
                .sorted(Comparator.comparing(FieldViolation::field)
                        .thenComparing(FieldViolation::message))
                .toList();
    }

    private String parameterName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null) {
            return header.name().isEmpty() ? header.value() : header.name();
        }
        RequestParam query = parameter.getParameterAnnotation(RequestParam.class);
        if (query != null) {
            String name = query.name().isEmpty() ? query.value() : query.name();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return parameter.getParameterName() == null ? "parameter" : parameter.getParameterName();
    }

    private String validationMessage(MessageSourceResolvable error) {
        if (error.getCodes() != null) {
            for (String code : error.getCodes()) {
                String message = switch (code) {
                    case "NotNull" -> "Must not be null.";
                    case "NotBlank" -> "Must not be blank.";
                    case "Positive" -> "Must be greater than zero.";
                    case "Size" -> "Length is outside the allowed range.";
                    case "Pattern" -> "Format is invalid.";
                    case "Min" -> "Must be at least the minimum allowed value.";
                    case "Max" -> "Must not exceed the maximum allowed value.";
                    default -> null;
                };
                if (message != null) {
                    return message;
                }
            }
        }
        return "Value is invalid.";
    }

    public record FieldViolation(String field, String message) {
    }
}
