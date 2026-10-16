package pl.oliwier.adscsv.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.oliwier.adscsv.api.ApiRoutes;
import pl.oliwier.adscsv.config.properties.HistoryProperties;
import pl.oliwier.adscsv.dto.JobResponse;
import pl.oliwier.adscsv.dto.PageResponse;
import pl.oliwier.adscsv.model.JobStatus;
import pl.oliwier.adscsv.service.job.JobService;
import pl.oliwier.adscsv.service.upload.JobUploadService;

@RestController
@RequestMapping(ApiRoutes.JOBS)
@RequiredArgsConstructor
public class JobController {

  private static final String TEXT_CSV_VALUE = "text/csv";
  private static final MediaType TEXT_CSV = MediaType.parseMediaType(TEXT_CSV_VALUE);
  private static final String FILE_NAME = "(?i)^[^/\\\\\\p{C}]{1,251}\\.csv$";

  private final JobUploadService uploadService;
  private final JobService jobService;

  @PostMapping(consumes = TEXT_CSV_VALUE)
  public ResponseEntity<JobResponse> upload(
      @RequestParam @NotBlank @Size(max = 255) @Pattern(regexp = FILE_NAME) String fileName,
      HttpServletRequest request,
      Principal principal)
      throws IOException {
    var job =
        uploadService.accept(
            principal.getName(),
            fileName,
            request.getContentLengthLong(),
            request.getInputStream());
    return ResponseEntity.accepted().location(URI.create(ApiRoutes.job(job.id()))).body(job);
  }

  @GetMapping("/{id}")
  public ResponseEntity<JobResponse> get(@PathVariable UUID id, Principal principal) {
    return ResponseEntity.ok(jobService.get(id, principal.getName()));
  }

  @GetMapping
  public ResponseEntity<PageResponse<JobResponse>> history(
      @RequestParam(defaultValue = "0") @Min(0) @Max(HistoryProperties.MAX_PAGE) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size,
      @RequestParam(required = false) Set<JobStatus> status,
      Principal principal) {
    return ResponseEntity.ok(jobService.history(principal.getName(), page, size, status));
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<JobResponse> cancel(@PathVariable UUID id, Principal principal) {
    return ResponseEntity.ok(jobService.cancel(id, principal.getName()));
  }

  @GetMapping("/{id}/result")
  public ResponseEntity<Resource> result(@PathVariable UUID id, Principal principal)
      throws IOException {
    var result = jobService.result(id, principal.getName());
    var disposition =
        ContentDisposition.attachment()
            .filename(result.downloadName(), StandardCharsets.UTF_8)
            .build();
    return ResponseEntity.ok()
        .contentType(TEXT_CSV)
        .contentLength(result.sizeBytes())
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .body(result.content());
  }
}
