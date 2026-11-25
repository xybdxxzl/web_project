package com.minioj.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.minioj.entity.Difficulty;
import com.minioj.entity.JudgeStatus;
import com.minioj.entity.Problem;
import com.minioj.entity.Role;
import com.minioj.entity.Submission;
import com.minioj.entity.TestCase;
import com.minioj.entity.User;
import com.minioj.repository.ProblemRepository;
import com.minioj.repository.SubmissionRepository;
import com.minioj.repository.TestCaseRepository;
import com.minioj.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = "oj.seed.demo-data=false")
class DemoDataInitializerTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProblemRepository problems;

    @Autowired
    private TestCaseRepository cases;

    @Autowired
    private UserRepository users;

    @Autowired
    private SubmissionRepository submissions;

    @Test
    void insertsVariedActivityOnceAndPreservesExistingUsers() {
        var existing = users.save(new User("demo_ada", "existing-hash", Role.USER));
        var problem = problems.save(new Problem("Sum", "题面", 1000, 128, Difficulty.EASY));
        cases.save(new TestCase(problem.getId(), "1 2", "3", true));
        var bcrypt = new BCryptPasswordEncoder(4);
        var initializer = new DemoDataInitializer(jdbc, problems, cases, users, submissions, bcrypt);
        Instant oldest = Instant.now().minus(365, ChronoUnit.DAYS);

        initializer.run(new DefaultApplicationArguments());
        var activity = submissions.findAll();
        assertThat(activity).isNotEmpty().allSatisfy(submission -> {
            assertThat(submission.getUserId()).isNotEqualTo(existing.getId());
            assertThat(submission.getProblemId()).isEqualTo(problem.getId());
            assertThat(submission.getCreatedAt()).isBetween(oldest, Instant.now());
        });
        assertThat(activity).extracting(Submission::getStatus)
                .contains(JudgeStatus.AC, JudgeStatus.WA, JudgeStatus.CE, JudgeStatus.TLE);
        assertThat(activity.stream().map(Submission::getCreatedAt).distinct().count()).isGreaterThan(30);
        assertThat(users.findAll()).hasSize(5);
        assertThat(users.findByUsername("demo_ada").orElseThrow().getPasswordHash()).isEqualTo("existing-hash");
        var demo = users.findByUsername("demo_ada_1").orElseThrow();
        assertThat(demo.getRole()).isEqualTo(Role.USER);
        assertThat(bcrypt.matches("demo1234", demo.getPasswordHash())).isTrue();

        initializer.run(new DefaultApplicationArguments());
        assertThat(users.count()).isEqualTo(5);
        assertThat(submissions.count()).isEqualTo(activity.size());
    }
}
