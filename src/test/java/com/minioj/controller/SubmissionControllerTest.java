package com.minioj.controller;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minioj.entity.Difficulty;
import com.minioj.entity.JudgeStatus;
import com.minioj.entity.LanguageType;
import com.minioj.entity.Problem;
import com.minioj.entity.Submission;
import com.minioj.entity.TestCase;
import com.minioj.repository.ProblemRepository;
import com.minioj.repository.SubmissionRepository;
import com.minioj.repository.TestCaseRepository;
import com.minioj.repository.UserRepository;
import com.minioj.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:submission-test;DB_CLOSE_DELAY=-1",
        "MINIOJ_ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
@TestPropertySource(properties = "oj.sandbox.type=process")
class SubmissionControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProblemRepository problems;

    @Autowired
    private TestCaseRepository testCases;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private UserService userService;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        submissions.deleteAll();
        testCases.deleteAll();
        problems.deleteAll();
        users.deleteAll();
        session = registerAndLogin("contestant");
    }

    @ParameterizedTest
    @MethodSource("programs")
    void judgesJavaSubmissions(JudgeStatus expected, int timeLimitMs, String code) throws Exception {
        long problemId = problem(timeLimitMs);
        long submissionId = submit(problemId, code);

        assertThat(awaitResult(submissionId)).isEqualTo(expected);
        var submission = submissions.findById(submissionId).orElseThrow();
        if (expected == JudgeStatus.CE) {
            assertThat(submission.getErrorMessage()).contains("编译失败", "Main.java");
            assertThat(submission.getTimeUsedMs()).isNull();
            return;
        }
        assertThat(submission.getTimeUsedMs()).isNotNegative();
        if (expected == JudgeStatus.RE) {
            assertThat(submission.getErrorMessage()).doesNotContain("11 13");
        }
    }

    static Stream<Arguments> programs() {
        return Stream.of(
                Arguments.of(JudgeStatus.AC, 3000, """
                        public class Main {
                            public static void main(String[] args) {
                                var input = new java.util.Scanner(System.in);
                                int result = input.nextInt() + input.nextInt();
                                System.out.print(result + "  \\r\\n\\r\\n");
                            }
                        }
                        """),
                Arguments.of(JudgeStatus.WA, 3000, program("System.out.println(999);")),
                Arguments.of(JudgeStatus.TLE, 350, program("while (true) {}")),
                Arguments.of(JudgeStatus.CE, 3000, "public class Main { this is invalid }"),
                Arguments.of(JudgeStatus.RE, 3000, program("""
                        var input = new java.util.Scanner(System.in);
                        String line = input.nextLine();
                        if (line.startsWith("1 ")) {
                            System.out.println(3);
                            return;
                        }
                        System.err.print(line);
                        int[] values = {1};
                        System.out.println(values[2]);
                        """)),
                Arguments.of(JudgeStatus.MLE, 3000, program("""
                        byte[] bytes = new byte[128 * 1024 * 1024];
                        System.out.println(bytes.length);
                        """))
        );
    }

    @Test
    void killsProcessAtCombinedOutputLimit() throws Exception {
        long submissionId = submit(problem(5000), program("""
                System.out.print("x".repeat(600000));
                System.err.print("y".repeat(500000));
                while (true) {}
                """));

        assertThat(awaitResult(submissionId)).isEqualTo(JudgeStatus.RE);
        assertThat(submissions.findById(submissionId).orElseThrow().getErrorMessage()).contains("1 MiB");
    }

    @Test
    void stopsAtFirstWrongAnswerBeforeRunningLaterCases() throws Exception {
        long submissionId = submit(problem(2000), program("""
                var input = new java.util.Scanner(System.in);
                if (input.nextInt() == 11) {
                    while (true) {}
                }
                System.out.println(999);
                """));

        assertThat(awaitResult(submissionId)).isEqualTo(JudgeStatus.WA);
    }

    @Test
    void requiresAuthenticationAndDoesNotExposeAnotherUsersSubmission() throws Exception {
        long submissionId = submit(problem(3000), program("System.out.println(999);"));

        mvc.perform(get("/api/submissions/{id}", submissionId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/submissions/{id}", submissionId).session(registerAndLogin("other_user")))
                .andExpect(status().isForbidden());
        userService.initializeAdmin("secret123");
        mvc.perform(get("/api/submissions/{id}", submissionId).session(login("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(program("System.out.println(999);")));
        assertThat(awaitResult(submissionId)).isEqualTo(JudgeStatus.WA);
    }

    @Test
    void paginatesOnlyTheCurrentUsersHistoryNewestFirst() throws Exception {
        long problemId = problem(3000);
        long owner = users.findByUsername("contestant").orElseThrow().getId();
        registerAndLogin("other_history");
        long another = users.findByUsername("other_history").orElseThrow().getId();
        var first = submissions.save(new Submission(owner, problemId, LanguageType.JAVA, "first source"));
        var second = submissions.save(new Submission(owner, problemId, LanguageType.JAVA, "second source"));
        submissions.save(new Submission(another, problemId, LanguageType.JAVA, "private source"));

        mvc.perform(get("/api/submissions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/submissions").session(session).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].submissionId").value(second.getId().intValue()))
                .andExpect(jsonPath("$.data.items[0].problemTitle").value("A+B"))
                .andExpect(jsonPath("$.data.items[0].code").doesNotExist());
        mvc.perform(get("/api/submissions").session(session).param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].submissionId").value(first.getId().intValue()));
    }

    @Test
    void rejectsUnsupportedLanguage() throws Exception {
        mvc.perform(post("/api/submissions").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("problemId", problem(3000),
                                "language", "PYTHON", "code", "print(3)"))))
                .andExpect(status().isBadRequest());

        assertThat(submissions.count()).isZero();
    }

    private MockHttpSession registerAndLogin(String username) throws Exception {
        String credentials = json.writeValueAsString(Map.of("username", username, "password", "secret123"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated());
        return login(username);
    }

    private MockHttpSession login(String username) throws Exception {
        String credentials = json.writeValueAsString(Map.of("username", username, "password", "secret123"));
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private long problem(int timeLimitMs) {
        Problem problem = problems.saveAndFlush(new Problem("A+B", "计算整数之和", timeLimitMs, 32, Difficulty.EASY));
        testCases.saveAllAndFlush(List.of(
                new TestCase(problem.getId(), "1 2\n", "3\n", true),
                new TestCase(problem.getId(), "11 13\n", "24\n", false)));
        return problem.getId();
    }

    private long submit(long problemId, String code) throws Exception {
        var result = mvc.perform(post("/api/submissions").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("problemId", problemId, "language", "JAVA", "code", code))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.submissionId").isNumber()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("submissionId").asLong();
    }

    private JudgeStatus awaitResult(long id) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            var result = mvc.perform(get("/api/submissions/{id}", id).session(session))
                    .andExpect(status().isOk()).andReturn();
            var response = json.readTree(result.getResponse().getContentAsByteArray()).path("data");
            var state = JudgeStatus.valueOf(response.path("status").asText());
            if (state != JudgeStatus.PENDING && state != JudgeStatus.JUDGING) {
                return state;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("提交 " + id + " 未在测试期限内结束");
    }

    private static String program(String body) {
        return "public class Main { public static void main(String[] args) { " + body + " } }";
    }
}
