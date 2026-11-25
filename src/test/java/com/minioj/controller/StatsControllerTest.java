package com.minioj.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.minioj.dto.CurrentUser;
import com.minioj.entity.Difficulty;
import com.minioj.entity.JudgeStatus;
import com.minioj.entity.LanguageType;
import com.minioj.entity.Problem;
import com.minioj.entity.Role;
import com.minioj.entity.Submission;
import com.minioj.entity.User;
import com.minioj.repository.ProblemRepository;
import com.minioj.repository.SubmissionRepository;
import com.minioj.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static com.minioj.config.AuthInterceptor.SESSION_USER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stats-test;DB_CLOSE_DELAY=-1",
        "MINIOJ_ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class StatsControllerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant FIXED_TIME = Instant.parse("2025-01-02T08:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProblemRepository problems;

    @Autowired
    private UserRepository users;

    @Autowired
    private SubmissionRepository submissions;

    @BeforeEach
    void clearDatabase() {
        submissions.deleteAll();
        problems.deleteAll();
        users.deleteAll();
    }

    @Test
    void servesPublicTotalsAndEightRecentSubmissionsInStableOrder() throws Exception {
        var user = addUser("contestant");
        var problem = addProblem("Sum", Difficulty.EASY);
        for (int i = 0; i < 8; i++) {
            submit(user, problem, JudgeStatus.WA, FIXED_TIME);
        }
        var latest = submit(user, problem, JudgeStatus.AC, FIXED_TIME.plusSeconds(1));
        var second = submit(user, problem, JudgeStatus.AC, FIXED_TIME);

        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProblems").value(1))
                .andExpect(jsonPath("$.data.totalUsers").value(1))
                .andExpect(jsonPath("$.data.totalSubmissions").value(10))
                .andExpect(jsonPath("$.data.acRate").value(0.2))
                .andExpect(jsonPath("$.data.recentSubmissions.length()").value(8))
                .andExpect(jsonPath("$.data.recentSubmissions[0].id").value(latest.getId().intValue()))
                .andExpect(jsonPath("$.data.recentSubmissions[0].username").value("contestant"))
                .andExpect(jsonPath("$.data.recentSubmissions[0].problemId").value(problem.getId().intValue()))
                .andExpect(jsonPath("$.data.recentSubmissions[0].problemTitle").value("Sum"))
                .andExpect(jsonPath("$.data.recentSubmissions[0].status").value("AC"))
                .andExpect(jsonPath("$.data.recentSubmissions[0].language").value("JAVA"))
                .andExpect(jsonPath("$.data.recentSubmissions[0].createdAt").value(latest.getCreatedAt().toString()))
                .andExpect(jsonPath("$.data.recentSubmissions[0].code").doesNotExist())
                .andExpect(jsonPath("$.data.recentSubmissions[1].id").value(second.getId().intValue()));
    }

    @Test
    void returnsZeroRatesAndEmptyListsWithoutActivity() throws Exception {
        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSubmissions").value(0))
                .andExpect(jsonPath("$.data.acRate").value(0.0))
                .andExpect(jsonPath("$.data.recentSubmissions").isEmpty());
        mvc.perform(get("/api/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void returnsOnlyCurrentUsersActivityWithInclusiveDateWindowsAndZeroDays() throws Exception {
        var owner = addUser("owner");
        var another = addUser("another");
        var easy = addProblem("Sum", Difficulty.EASY);
        var hard = addProblem("Paths", Difficulty.HARD);
        LocalDate today = LocalDate.now(ZONE);
        Instant startToday = today.atStartOfDay(ZONE).toInstant();
        submit(owner, easy, JudgeStatus.AC, startToday);
        submit(owner, easy, JudgeStatus.AC, startToday);
        submit(owner, easy, JudgeStatus.WA, startToday.minusSeconds(1));
        submit(owner, hard, JudgeStatus.WA, dayStart(today.minusDays(29)));
        submit(owner, hard, JudgeStatus.WA, dayStart(today.minusDays(30)));
        submit(owner, hard, JudgeStatus.WA, dayStart(today.minusDays(364)));
        submit(owner, hard, JudgeStatus.WA, dayStart(today.minusDays(365)));
        submit(another, hard, JudgeStatus.AC, startToday);

        mvc.perform(get("/api/users/me/stats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/stats").session(session(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.username").value("owner"))
                .andExpect(jsonPath("$.data.totalSubmissions").value(7))
                .andExpect(jsonPath("$.data.acProblemCount").value(1))
                .andExpect(jsonPath("$.data.difficultyStats.EASY.solved").value(1))
                .andExpect(jsonPath("$.data.difficultyStats.EASY.total").value(1))
                .andExpect(jsonPath("$.data.difficultyStats.MEDIUM.solved").value(0))
                .andExpect(jsonPath("$.data.difficultyStats.MEDIUM.total").value(0))
                .andExpect(jsonPath("$.data.difficultyStats.HARD.solved").value(0))
                .andExpect(jsonPath("$.data.difficultyStats.HARD.total").value(1))
                .andExpect(jsonPath("$.data.dailyTrend.length()").value(30))
                .andExpect(jsonPath("$.data.dailyTrend[0].date").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.data.dailyTrend[0].total").value(1))
                .andExpect(jsonPath("$.data.dailyTrend[1].total").value(0))
                .andExpect(jsonPath("$.data.dailyTrend[1].ac").value(0))
                .andExpect(jsonPath("$.data.dailyTrend[28].total").value(1))
                .andExpect(jsonPath("$.data.dailyTrend[29].date").value(today.toString()))
                .andExpect(jsonPath("$.data.dailyTrend[29].total").value(2))
                .andExpect(jsonPath("$.data.dailyTrend[29].ac").value(2))
                .andExpect(jsonPath("$.data.yearlyCalendar.length()").value(365))
                .andExpect(jsonPath("$.data.yearlyCalendar[0].date").value(today.minusDays(364).toString()))
                .andExpect(jsonPath("$.data.yearlyCalendar[0].count").value(1))
                .andExpect(jsonPath("$.data.yearlyCalendar[1].count").value(0))
                .andExpect(jsonPath("$.data.yearlyCalendar[334].count").value(1))
                .andExpect(jsonPath("$.data.yearlyCalendar[364].date").value(today.toString()))
                .andExpect(jsonPath("$.data.yearlyCalendar[364].count").value(2));
    }

    @Test
    void ordersRankingByDistinctSolvedCountThenRateAndKeepsGlobalRanksAcrossPages() throws Exception {
        var lowerRate = addUser("lower_rate");
        var higherRate = addUser("higher_rate");
        var perfect = addUser("first_perfect");
        var tied = addUser("tied_perfect");
        var inactive = addUser("inactive");
        var easy = addProblem("Sum", Difficulty.EASY);
        var hard = addProblem("Paths", Difficulty.HARD);
        for (var user : new User[]{lowerRate, higherRate}) {
            submit(user, easy, JudgeStatus.AC, FIXED_TIME);
            submit(user, hard, JudgeStatus.AC, FIXED_TIME);
            submit(user, hard, JudgeStatus.WA, FIXED_TIME);
        }
        submit(lowerRate, easy, JudgeStatus.WA, FIXED_TIME);
        submit(perfect, easy, JudgeStatus.AC, FIXED_TIME);
        submit(perfect, easy, JudgeStatus.AC, FIXED_TIME);
        submit(tied, hard, JudgeStatus.AC, FIXED_TIME);

        mvc.perform(get("/api/ranking").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value(higherRate.getId().intValue()))
                .andExpect(jsonPath("$.data.items[0].username").value("higher_rate"))
                .andExpect(jsonPath("$.data.items[0].acProblemCount").value(2))
                .andExpect(jsonPath("$.data.items[0].totalSubmissions").value(3))
                .andExpect(jsonPath("$.data.items[0].acRate").value(2.0 / 3))
                .andExpect(jsonPath("$.data.items[1].userId").value(lowerRate.getId().intValue()));
        mvc.perform(get("/api/ranking").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].rank").value(3))
                .andExpect(jsonPath("$.data.items[0].userId").value(perfect.getId().intValue()))
                .andExpect(jsonPath("$.data.items[0].acProblemCount").value(1))
                .andExpect(jsonPath("$.data.items[1].rank").value(4))
                .andExpect(jsonPath("$.data.items[1].userId").value(tied.getId().intValue()));
        mvc.perform(get("/api/ranking").param("page", "3").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].rank").value(5))
                .andExpect(jsonPath("$.data.items[0].userId").value(inactive.getId().intValue()))
                .andExpect(jsonPath("$.data.items[0].acRate").value(0.0));
        mvc.perform(get("/api/ranking").param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ranking").param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void listsPerProblemCountsAndDistinguishesViewerStatus() throws Exception {
        var owner = addUser("owner");
        var another = addUser("another");
        var solved = addProblem("Sum", Difficulty.EASY);
        var tried = addProblem("Tree", Difficulty.MEDIUM);
        var untouched = addProblem("Paths", Difficulty.HARD);
        addProblem("Sorting", Difficulty.EASY);
        submit(owner, solved, JudgeStatus.WA, FIXED_TIME);
        submit(owner, solved, JudgeStatus.AC, FIXED_TIME);
        submit(another, solved, JudgeStatus.AC, FIXED_TIME);
        submit(owner, tried, JudgeStatus.PENDING, FIXED_TIME);
        submit(another, tried, JudgeStatus.AC, FIXED_TIME);
        submit(another, untouched, JudgeStatus.AC, FIXED_TIME);

        mvc.perform(get("/api/problems").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.items[0].submitCount").value(3))
                .andExpect(jsonPath("$.data.items[0].acCount").value(2))
                .andExpect(jsonPath("$.data.items[0].userStatus").value("NONE"));
        mvc.perform(get("/api/problems").session(session(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items[0].userStatus").value("AC"))
                .andExpect(jsonPath("$.data.items[1].submitCount").value(2))
                .andExpect(jsonPath("$.data.items[1].acCount").value(1))
                .andExpect(jsonPath("$.data.items[1].userStatus").value("TRIED"))
                .andExpect(jsonPath("$.data.items[2].userStatus").value("NONE"))
                .andExpect(jsonPath("$.data.items[3].submitCount").value(0))
                .andExpect(jsonPath("$.data.items[3].acCount").value(0))
                .andExpect(jsonPath("$.data.items[3].userStatus").value("NONE"));
    }

    private User addUser(String username) {
        return users.save(new User(username, "unused-hash", Role.USER));
    }

    private Problem addProblem(String title, Difficulty difficulty) {
        return problems.save(new Problem(title, "题面", 1000, 128, difficulty));
    }

    private Submission submit(User user, Problem problem, JudgeStatus status, Instant time) {
        return submissions.save(new Submission(user.getId(), problem.getId(), LanguageType.JAVA, "source",
                status, time, 0, 0));
    }

    private MockHttpSession session(User user) {
        var session = new MockHttpSession();
        session.setAttribute(SESSION_USER, new CurrentUser(user.getId(), user.getUsername(), user.getRole()));
        return session;
    }

    private Instant dayStart(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
