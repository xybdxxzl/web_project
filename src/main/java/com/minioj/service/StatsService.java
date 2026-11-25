package com.minioj.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.minioj.dto.CurrentUser;
import com.minioj.dto.RankingPage;
import com.minioj.dto.SiteStats;
import com.minioj.dto.UserStats;
import com.minioj.dto.UserStats.CalendarDay;
import com.minioj.dto.UserStats.DailyActivity;
import com.minioj.dto.UserStats.DifficultyStats;
import com.minioj.entity.Difficulty;
import com.minioj.entity.JudgeStatus;
import com.minioj.repository.ProblemRepository;
import com.minioj.repository.SubmissionRepository;
import com.minioj.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StatsService {

    private static final ZoneId STATS_ZONE = ZoneId.of("Asia/Shanghai");

    private final SubmissionRepository submissions;
    private final ProblemRepository problems;
    private final UserRepository users;

    public StatsService(SubmissionRepository submissions, ProblemRepository problems, UserRepository users) {
        this.submissions = submissions;
        this.problems = problems;
        this.users = users;
    }

    public SiteStats site() {
        long total = 0;
        long accepted = 0;
        for (var count : submissions.countStatuses()) {
            total += count.count();
            if (count.status() == JudgeStatus.AC) {
                accepted = count.count();
            }
        }
        return new SiteStats(problems.count(), users.count(), total, acRate(accepted, total),
                submissions.findRecent(PageRequest.of(0, 8)));
    }

    public UserStats currentUser(CurrentUser user) {
        LocalDate today = LocalDate.now(STATS_ZONE);
        LocalDate firstCalendarDay = today.minusDays(364);
        LocalDate firstTrendDay = today.minusDays(29);
        LocalDate tomorrow = today.plusDays(1);
        var activity = submissions.findActivity(user.id(), firstCalendarDay.atStartOfDay(STATS_ZONE).toInstant(),
                tomorrow.atStartOfDay(STATS_ZONE).toInstant());

        Map<LocalDate, Long> totals = new HashMap<>();
        Map<LocalDate, Long> accepted = new HashMap<>();
        for (var submission : activity) {
            LocalDate date = submission.createdAt().atZone(STATS_ZONE).toLocalDate();
            totals.merge(date, 1L, Long::sum);
            if (submission.status() == JudgeStatus.AC) {
                accepted.merge(date, 1L, Long::sum);
            }
        }
        List<DailyActivity> dailyTrend = firstTrendDay.datesUntil(tomorrow)
                .map(date -> new DailyActivity(date, totals.getOrDefault(date, 0L), accepted.getOrDefault(date, 0L)))
                .toList();
        List<CalendarDay> yearlyCalendar = firstCalendarDay.datesUntil(tomorrow)
                .map(date -> new CalendarDay(date, totals.getOrDefault(date, 0L)))
                .toList();
        return new UserStats(user.username(), submissions.countByUserId(user.id()),
                submissions.countAcceptedProblems(user.id()), difficultyStats(user.id()), dailyTrend, yearlyCalendar);
    }

    public RankingPage ranking(int page, int size) {
        var pageable = PageRequest.of(page - 1, size);
        var result = submissions.findRanking(pageable);
        long rank = pageable.getOffset();
        var entries = new ArrayList<RankingPage.Entry>(result.getNumberOfElements());
        for (var score : result.getContent()) {
            entries.add(new RankingPage.Entry(++rank, score.userId(), score.username(), score.acProblemCount(),
                    score.totalSubmissions(), acRate(score.acSubmissions(), score.totalSubmissions())));
        }
        return new RankingPage(entries, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private Map<Difficulty, DifficultyStats> difficultyStats(Long userId) {
        var stats = new EnumMap<Difficulty, DifficultyStats>(Difficulty.class);
        for (Difficulty difficulty : Difficulty.values()) {
            stats.put(difficulty, new DifficultyStats(0, 0));
        }
        for (var count : problems.countByDifficulty(userId)) {
            stats.put(count.difficulty(), new DifficultyStats(count.solved(), count.total()));
        }
        return stats;
    }

    private double acRate(long accepted, long total) {
        return total == 0 ? 0 : (double) accepted / total;
    }
}
