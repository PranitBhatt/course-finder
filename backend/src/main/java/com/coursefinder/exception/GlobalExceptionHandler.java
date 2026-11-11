package com.coursefinder.exception;

import com.coursefinder.connector.ConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into RFC 7807 {@link ProblemDetail} responses.
 * <p>
 * The parent class already handles Spring MVC's own errors (unknown route, wrong HTTP method, ...).
 * Validation errors get an extra {@code errors} map: {@code {"size": "must be at most 100"}}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------------------------------------------------------------------
    // Application exceptions
    // ---------------------------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(SyncInProgressException.class)
    public ProblemDetail handleSyncInProgress(SyncInProgressException e) {
        return problem(HttpStatus.CONFLICT, "Sync already running", e.getMessage());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException e) {
        return problem(HttpStatus.CONFLICT, "Email already registered", e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", e.getMessage());
    }

    @ExceptionHandler(UnsupportedSyncException.class)
    public ProblemDetail handleUnsupportedSync(UnsupportedSyncException e) {
        return problem(HttpStatus.BAD_REQUEST, "Sync not supported", e.getMessage());
    }

    @ExceptionHandler(ConnectorException.class)
    public ProblemDetail handleConnector(ConnectorException e) {
        log.warn("Upstream platform error from {}: {}", e.getSource(), e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "Upstream platform error",
                "%s is currently unavailable".formatted(e.getSource().getDisplayName()));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred");
    }

    // ---------------------------------------------------------------------
    // Validation (overrides of ResponseEntityExceptionHandler)
    // ---------------------------------------------------------------------

    /** Invalid {@code @Valid} request object, including unparseable values like {@code page=abc}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            String message = fieldError.isBindingFailure()
                    ? "invalid value '%s'".formatted(fieldError.getRejectedValue())
                    : fieldError.getDefaultMessage();
            errors.putIfAbsent(fieldError.getField(), message);
        }
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    /** Constraint on a plain parameter, e.g. {@code @Positive Long id}. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage)
                    .findFirst()
                    .ifPresent(message -> errors.putIfAbsent(name, message));
        });
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    /** Unparseable path variable / request param, e.g. {@code /api/courses/abc}. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e,
                                                        HttpHeaders headers,
                                                        HttpStatusCode status,
                                                        WebRequest request) {
        String name = e.getPropertyName() == null ? "parameter" : e.getPropertyName();
        Map<String, String> errors = Map.of(name, "invalid value '%s'".formatted(e.getValue()));
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static ProblemDetail validationProblem(Map<String, String> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more request parameters are invalid");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
