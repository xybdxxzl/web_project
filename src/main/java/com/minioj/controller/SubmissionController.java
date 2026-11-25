package com.minioj.controller;

import com.minioj.dto.ApiResponse;
import com.minioj.dto.CurrentUser;
import com.minioj.dto.SubmissionAccepted;
import com.minioj.dto.SubmissionPage;
import com.minioj.dto.SubmissionRequest;
import com.minioj.dto.SubmissionResult;
import com.minioj.service.SubmissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import static com.minioj.config.AuthInterceptor.SESSION_USER;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissions;

    public SubmissionController(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<SubmissionAccepted> submit(@SessionAttribute(SESSION_USER) CurrentUser user,
                                                  @Valid @RequestBody SubmissionRequest request) {
        return ApiResponse.success(submissions.submit(user.id(), request));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SubmissionPage>> history(
            @SessionAttribute(SESSION_USER) CurrentUser user,
            @RequestParam(defaultValue = "1") @Min(1) @Max(Integer.MAX_VALUE / 100) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(submissions.history(user.id(), page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubmissionResult>> result(@SessionAttribute(SESSION_USER) CurrentUser user,
                                                                @PathVariable @Min(1) Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(submissions.result(user, id)));
    }
}
