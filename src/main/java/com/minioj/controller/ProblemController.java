package com.minioj.controller;

import com.minioj.dto.ApiResponse;
import com.minioj.dto.ProblemDetails;
import com.minioj.dto.ProblemPage;
import com.minioj.dto.ProblemRequest;
import com.minioj.entity.Difficulty;
import com.minioj.service.ProblemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProblemController {

    private final ProblemService problems;

    public ProblemController(ProblemService problems) {
        this.problems = problems;
    }

    @GetMapping({"/api/problems", "/api/admin/problems"})
    public ApiResponse<ProblemPage> list(
            @RequestParam(defaultValue = "1") @Min(1) @Max(Integer.MAX_VALUE / 100) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.success(problems.list(page, size, difficulty, keyword));
    }

    @GetMapping("/api/problems/{id}")
    public ApiResponse<ProblemDetails> detail(@PathVariable @Min(1) Long id) {
        return ApiResponse.success(problems.detail(id, false));
    }

    @GetMapping("/api/admin/problems/{id}")
    public ResponseEntity<ApiResponse<ProblemDetails>> adminDetail(@PathVariable @Min(1) Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(problems.detail(id, true)));
    }

    @PostMapping("/api/admin/problems")
    public ResponseEntity<ApiResponse<ProblemDetails>> create(@Valid @RequestBody ProblemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(problems.create(request)));
    }

    @PutMapping("/api/admin/problems/{id}")
    public ResponseEntity<ApiResponse<ProblemDetails>> update(
            @PathVariable @Min(1) Long id, @Valid @RequestBody ProblemRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(problems.update(id, request)));
    }

    @DeleteMapping("/api/admin/problems/{id}")
    public ApiResponse<Void> delete(@PathVariable @Min(1) Long id) {
        problems.delete(id);
        return ApiResponse.success(null);
    }
}
