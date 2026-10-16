package pl.oliwier.adscsv.exception;

import static pl.oliwier.adscsv.exception.ProblemMapper.classify;
import static pl.oliwier.adscsv.exception.ProblemMapper.problem;
import static pl.oliwier.adscsv.exception.ProblemMapper.statusOf;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.DisconnectedClientHelper;
import pl.oliwier.adscsv.exception.auth.InvalidCredentialsException;
import pl.oliwier.adscsv.exception.auth.NotAuthenticatedException;
import pl.oliwier.adscsv.exception.job.JobNotFoundException;
import pl.oliwier.adscsv.exception.job.JobStateConflictException;
import pl.oliwier.adscsv.exception.job.ResultGoneException;
import pl.oliwier.adscsv.exception.upload.InvalidUploadException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler({NotAuthenticatedException.class, InvalidCredentialsException.class})
  ResponseEntity<ProblemDetail> handleUnauthenticated(ApplicationException e) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem(HttpStatus.UNAUTHORIZED, e));
  }

  @ExceptionHandler(JobNotFoundException.class)
  ResponseEntity<ProblemDetail> handleJobNotFound(JobNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, e));
  }

  @ExceptionHandler(JobStateConflictException.class)
  ResponseEntity<ProblemDetail> handleJobStateConflict(JobStateConflictException e) {
    var problem = problem(HttpStatus.CONFLICT, e);
    problem.setProperty("currentStatus", e.currentStatus());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  @ExceptionHandler(ResultGoneException.class)
  ResponseEntity<ProblemDetail> handleResultGone(ResultGoneException e) {
    return ResponseEntity.status(HttpStatus.GONE).body(problem(HttpStatus.GONE, e));
  }

  @ExceptionHandler(InvalidUploadException.class)
  ResponseEntity<ProblemDetail> handleInvalidUpload(InvalidUploadException e) {
    var status = statusOf(e.problem());
    return ResponseEntity.status(status).body(problem(status, e));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest request) {
    if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
      log.debug("Client disconnected during {} {}", request.getMethod(), request.getRequestURI());
      return null;
    }
    log.error("Unhandled error during {} {}", request.getMethod(), request.getRequestURI(), e);
    return ResponseEntity.internalServerError()
        .body(
            problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error"));
  }

  @Override
  protected @NonNull ResponseEntity<Object> createResponseEntity(
      Object body,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode statusCode,
      @NonNull WebRequest request) {
    if (body instanceof ProblemDetail problem) {
      classify(problem, statusCode);
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }
}
