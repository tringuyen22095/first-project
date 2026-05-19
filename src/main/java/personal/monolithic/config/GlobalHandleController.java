package personal.monolithic.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;
import personal.monolithic.exception.NotFoundException;
import personal.monolithic.exception.UnauthorizeException;

@RestControllerAdvice
@Slf4j
public class GlobalHandleController {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(NotFoundException exception) {
        log.warn("Not found exception: {}", exception.getMessage());
        return buildProblemDetail(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler(UnauthorizeException.class)
    public ProblemDetail handleUnauthorizeException(UnauthorizeException exception) {
        log.warn("Unauthorized exception: {}", exception.getMessage());
        return buildProblemDetail(HttpStatus.UNAUTHORIZED, "Unauthorized", exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        if (status.is5xxServerError()) {
            log.error("Response status exception: {}", exception.getReason(), exception);
        } else {
            log.warn("Response status exception status={} message={}", status.value(), exception.getReason());
        }

        return buildProblemDetail(status, status.getReasonPhrase(), exception.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "Unexpected error");
    }

    private ProblemDetail buildProblemDetail(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return problemDetail;
    }
}
