package com.minioj.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.minioj.entity.JudgeStatus;
import com.minioj.entity.LanguageType;
import com.minioj.entity.Problem;
import com.minioj.entity.Role;
import com.minioj.entity.Submission;
import com.minioj.entity.User;
import com.minioj.repository.ProblemRepository;
import com.minioj.repository.SubmissionRepository;
import com.minioj.repository.TestCaseRepository;
import com.minioj.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
@ConditionalOnProperty(name = "oj.seed.demo-data", havingValue = "true")
class DemoDataInitializer implements ApplicationRunner {

    private static final List<String> USERNAMES = List.of("demo_ada", "demo_lin", "demo_kai", "demo_mia");
    private static final List<JudgeStatus> FAILED = List.of(JudgeStatus.WA, JudgeStatus.TLE, JudgeStatus.CE);

    private final JdbcTemplate jdbc;
    private final ProblemRepository problems;
    private final TestCaseRepository testCases;
    private final UserRepository users;
    private final SubmissionRepository submissions;
    private final BCryptPasswordEncoder bcrypt;

    DemoDataInitializer(JdbcTemplate jdbc, ProblemRepository problems, TestCaseRepository testCases,
                        UserRepository users, SubmissionRepository submissions, BCryptPasswordEncoder bcrypt) {
        this.jdbc = jdbc;
        this.problems = problems;
        this.testCases = testCases;
        this.users = users;
        this.submissions = submissions;
        this.bcrypt = bcrypt;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Problem> available = problems.findAll(PageRequest.of(0, 8, Sort.by("id"))).getContent();
        if (available.isEmpty()) {
            return;
        }
        int initialized = jdbc.update("""
                insert into oj_bootstrap (seed_name)
                select 'demo-activity-v1' where not exists
                    (select 1 from oj_bootstrap where seed_name = 'demo-activity-v1')
                """);
        if (initialized == 0) {
            return;
        }

        Map<Long, Integer> caseCounts = new HashMap<>();
        for (Problem problem : available) {
            caseCounts.put(problem.getId(), testCases.countByProblemId(problem.getId()));
        }
        Instant now = Instant.now();
        for (int i = 0; i < USERNAMES.size(); i++) {
            String username = availableUsername(USERNAMES.get(i));
            User user = users.save(new User(username, bcrypt.encode("demo1234"), Role.USER));
            submissions.saveAll(activity(user.getId(), i, available, caseCounts, now));
        }
    }

    private String availableUsername(String base) {
        String username = base;
        for (int suffix = 1; users.existsByUsername(username); suffix++) {
            username = base + "_" + suffix;
        }
        return username;
    }

    private List<Submission> activity(Long userId, int userIndex, List<Problem> available,
                                      Map<Long, Integer> caseCounts, Instant now) {
        List<Submission> activity = new ArrayList<>();
        int attempt = 0;
        for (int daysAgo = userIndex; daysAgo < 365; daysAgo += 4 + userIndex) {
            Long problemId = available.get(attempt % available.size()).getId();
            LanguageType language = attempt % 2 == 0 ? LanguageType.JAVA : LanguageType.CPP;
            int score = (attempt * 17 + userIndex * 7) % 11;
            JudgeStatus status = score < 6 - userIndex * 2 ? JudgeStatus.AC : FAILED.get(score % FAILED.size());
            int total = status == JudgeStatus.CE ? 0 : caseCounts.get(problemId);
            int passed = status == JudgeStatus.AC ? total : 0;
            Instant createdAt = now.minus(daysAgo, ChronoUnit.DAYS);
            activity.add(new Submission(userId, problemId, language, "// 演示提交", status, createdAt, passed, total));
            attempt++;
        }
        return activity;
    }
}
