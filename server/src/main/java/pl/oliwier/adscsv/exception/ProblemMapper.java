package pl.oliwier.adscsv.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import pl.oliwier.adscsv.exception.upload.UploadProblem;

public final class ProblemMapper {

  private static final String CODE = "code";

  private ProblemMapper() {}

  public static ProblemDetail problem(HttpStatus status, String code, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty(CODE, code);
    return problem;
  }

  public static ProblemDetail problem(HttpStatus status, ApplicationException e) {
    return problem(status, e.code(), e.getMessage());
  }

  public static void classify(ProblemDetail problem, HttpStatusCode status) {
    problem.setProperty(CODE, codeOf(status));
  }

  public static HttpStatus statusOf(UploadProblem problem) {
    return switch (problem) {
      case EMPTY, INTERRUPTED -> HttpStatus.BAD_REQUEST;
      case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
      case INVALID_HEADER -> HttpStatus.UNPROCESSABLE_CONTENT;
      case INSUFFICIENT_STORAGE -> HttpStatus.INSUFFICIENT_STORAGE;
    };
  }

  private static String codeOf(HttpStatusCode status) {
    return switch (status.value()) {
      case 404 -> "NOT_FOUND";
      case 405 -> "METHOD_NOT_ALLOWED";
      case 415 -> "UNSUPPORTED_MEDIA_TYPE";
      default -> status.is4xxClientError() ? "REQUEST_INVALID" : "INTERNAL_ERROR";
    };
  }
}
