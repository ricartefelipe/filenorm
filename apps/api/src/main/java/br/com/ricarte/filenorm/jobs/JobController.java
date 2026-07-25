package br.com.ricarte.filenorm.jobs;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "auto") String format,
            @RequestParam(value = "preset", required = false) String preset
    ) {
        Map<String, Object> body = jobService.createJob(file, format, preset);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getJob(@PathVariable UUID id) {
        return jobService.getJob(id);
    }

    @GetMapping("/{id}/events")
    public Map<String, Object> getEvents(@PathVariable UUID id) {
        return jobService.getEvents(id);
    }
}
