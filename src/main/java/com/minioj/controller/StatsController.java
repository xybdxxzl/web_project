package com.minioj.controller;

import com.minioj.dto.ApiResponse;
import com.minioj.dto.CurrentUser;
import com.minioj.dto.RankingPage;
import com.minioj.dto.SiteStats;
import com.minioj.dto.UserStats;
import com.minioj.service.StatsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import static com.minioj.config.AuthInterceptor.SESSION_USER;

@RestController
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/api/stats")
    public ApiResponse<SiteStats> site() {
        return ApiResponse.success(stats.site());
    }

    @GetMapping("/api/users/me/stats")
    public ResponseEntity<ApiResponse<UserStats>> currentUserStats(@SessionAttribute(SESSION_USER) CurrentUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(stats.currentUser(user)));
    }

    @GetMapping("/api/ranking")
    public ApiResponse<RankingPage> ranking(
            @RequestParam(defaultValue = "1") @Min(1) @Max(Integer.MAX_VALUE / 100) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(stats.ranking(page, size));
    }
}
