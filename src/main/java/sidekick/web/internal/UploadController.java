package sidekick.web.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import sidekick.ingestion.IngestionJobs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The upload escape hatch (PLAN §3): POST /api/documents accepts a SID PDF for
 * a fund outside the catalog, returns 202 + a job id immediately; the caller
 * polls GET /api/jobs/{id}. The SID sanity check and the pinned-fund guard
 * live in the ingestion module — this edge only validates the request shape.
 */
@RestController
class UploadController {

    private static final Pattern FUND_SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final IngestionJobs jobs;

    UploadController(IngestionJobs jobs) {
        this.jobs = jobs;
    }

    @PostMapping("/api/documents")
    ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                               @RequestParam("fundId") String fundId,
                                               @RequestParam(value = "displayName", required = false) String displayName)
            throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no file uploaded");
        }
        if (!FUND_SLUG.matcher(fundId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fundId must be a lowercase-kebab slug like 'nippon-small-cap'");
        }
        Path temp = Files.createTempFile("sidekick-upload-", ".pdf");
        file.transferTo(temp);
        UUID jobId = jobs.submit(temp, file.getOriginalFilename(), fundId,
                displayName == null || displayName.isBlank() ? null : displayName.strip());
        return ResponseEntity.accepted().body(Map.of("jobId", jobId.toString()));
    }

    @GetMapping("/api/jobs/{id}")
    IngestionJobs.JobView job(@PathVariable UUID id) {
        return jobs.status(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown job: " + id));
    }

    @ExceptionHandler(IngestionJobs.PinnedFundException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> pinnedFund(IngestionJobs.PinnedFundException e) {
        return Map.of("error", e.getMessage());
    }
}
