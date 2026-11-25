package com.minioj.config;

import java.util.List;

import com.minioj.dto.ProblemRequest;
import com.minioj.dto.ProblemRequest.TestCaseRequest;
import com.minioj.entity.Difficulty;
import com.minioj.repository.ProblemRepository;
import com.minioj.service.ProblemService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
class DemoProblemInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final ProblemRepository problems;
    private final ProblemService problemService;

    DemoProblemInitializer(JdbcTemplate jdbc, ProblemRepository problems, ProblemService problemService) {
        this.jdbc = jdbc;
        this.problems = problems;
        this.problemService = problemService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int initialized = jdbc.update("""
                insert into oj_bootstrap (seed_name)
                select 'demo-a-plus-b' where not exists
                    (select 1 from oj_bootstrap where seed_name = 'demo-a-plus-b')
                """);
        if (initialized == 0 || problems.existsByTitle("A+B")) {
            return;
        }

        problemService.create(new ProblemRequest("A+B", """
                读入两个整数 A 和 B，输出它们的和。

                输入一行，包含两个空格分隔的整数 A 和 B（−10⁹ ≤ A, B ≤ 10⁹）。
                输出一行，包含 A+B。
                """, 1000, 128, Difficulty.EASY, List.of(
                new TestCaseRequest("1 2\n", "3\n", true),
                new TestCaseRequest("-7 4\n", "-3\n", false),
                new TestCaseRequest("1000000000 1000000000\n", "2000000000\n", false))));
    }
}
