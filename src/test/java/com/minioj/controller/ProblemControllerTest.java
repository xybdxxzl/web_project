package com.minioj.controller;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minioj.dto.ProblemRequest;
import com.minioj.dto.ProblemRequest.TestCaseRequest;
import com.minioj.entity.Difficulty;
import com.minioj.entity.Problem;
import com.minioj.entity.Role;
import com.minioj.entity.TestCase;
import com.minioj.entity.User;
import com.minioj.repository.ProblemRepository;
import com.minioj.repository.TestCaseRepository;
import com.minioj.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:problem-test;DB_CLOSE_DELAY=-1",
        "MINIOJ_ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class ProblemControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ProblemRepository problems;

    @Autowired
    private TestCaseRepository testCases;

    @Autowired
    private UserRepository users;

    @Autowired
    private BCryptPasswordEncoder bcrypt;

    @BeforeEach
    void clearDatabase() {
        testCases.deleteAll();
        problems.deleteAll();
        users.deleteAll();
    }

    @Test
    void combinesFiltersAndPaginatesInStableOrder() throws Exception {
        addProblem("Array sum", Difficulty.EASY);
        Problem first = addProblem("Array intersection", Difficulty.MEDIUM);
        Problem second = addProblem("Sorted ARRAY", Difficulty.MEDIUM);
        addProblem("Binary search", Difficulty.MEDIUM);

        mvc.perform(get("/api/problems").param("difficulty", "MEDIUM").param("keyword", " array ")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value(first.getTitle()))
                .andExpect(jsonPath("$.data.items[0].testCases").doesNotExist());

        mvc.perform(get("/api/problems").param("difficulty", "MEDIUM").param("keyword", "array")
                        .param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value(second.getTitle()));
    }

    @Test
    void treatsKeywordPunctuationLiterally() throws Exception {
        addProblem("100% correct", Difficulty.EASY);
        addProblem("100 points", Difficulty.EASY);

        mvc.perform(get("/api/problems").param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("100% correct"));
    }

    @Test
    void publicDetailNeverIncludesHiddenInputOrOutput() throws Exception {
        Problem problem = addProblem("A+B", Difficulty.EASY);
        testCases.save(new TestCase(problem.getId(), "hidden secret input", "hidden secret output", false));
        testCases.save(new TestCase(problem.getId(), "1 2\n", "3\n", true));

        mvc.perform(get("/api/problems/{id}", problem.getId()).param("includeHidden", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("输入数据\n输出答案"))
                .andExpect(jsonPath("$.data.testCases.length()").value(1))
                .andExpect(jsonPath("$.data.testCases[0].input").value("1 2\n"))
                .andExpect(jsonPath("$.data.testCases[0].expectedOutput").value("3\n"))
                .andExpect(jsonPath("$.data.testCases[0].isSample").value(true))
                .andExpect(content().string(not(containsString("hidden secret"))));
    }

    @Test
    void adminCanCreateEditAndDeleteProblemWithCases() throws Exception {
        MockHttpSession admin = login(Role.ADMIN);
        var data = new ProblemRequest("A+B", "输入两个整数", 1000, 128, Difficulty.EASY, List.of(
                new TestCaseRequest("1 2\n", "3\n", true),
                new TestCaseRequest("10 -3\n", "7\n", false)));

        var result = mvc.perform(post("/api/admin/problems").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(data)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.testCases.length()").value(2))
                .andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();

        mvc.perform(get("/api/admin/problems/{id}", id).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.testCases[1].expectedOutput").value("7\n"))
                .andExpect(jsonPath("$.data.testCases[1].isSample").value(false));

        var revised = new ProblemRequest("两数相加", "题面\n更新", 2000, 256, Difficulty.MEDIUM,
                List.of(new TestCaseRequest("", "", true)));
        mvc.perform(put("/api/admin/problems/{id}", id).session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(revised)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("两数相加"))
                .andExpect(jsonPath("$.data.testCases.length()").value(1))
                .andExpect(jsonPath("$.data.testCases[0].expectedOutput").value(""));

        assertThat(testCases.findByProblemIdOrderByIdAsc(id)).hasSize(1);
        mvc.perform(delete("/api/admin/problems/{id}", id).session(admin)).andExpect(status().isOk());
        assertThat(testCases.findByProblemIdOrderByIdAsc(id)).isEmpty();
        mvc.perform(get("/api/problems/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void anonymousAndNonAdminRequestsCannotReadHiddenCasesOrWrite() throws Exception {
        Problem problem = addProblem("A+B", Difficulty.EASY);
        MockHttpSession user = login(Role.USER);
        String detailPath = "/api/admin/problems/" + problem.getId();

        mvc.perform(get(detailPath)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/problems")).andExpect(status().isUnauthorized());
        mvc.perform(get(detailPath).session(user)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/problems").session(user)).andExpect(status().isForbidden());
        mvc.perform(put(detailPath).session(user)).andExpect(status().isForbidden());
        mvc.perform(delete(detailPath).session(user)).andExpect(status().isForbidden());
        assertThat(problems.existsById(problem.getId())).isTrue();
    }

    @Test
    void rejectsInvalidFiltersAndNestedTestCaseWithoutWritingData() throws Exception {
        mvc.perform(get("/api/problems").param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/problems").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/problems").param("difficulty", "UNKNOWN")).andExpect(status().isBadRequest());

        MockHttpSession admin = login(Role.ADMIN);
        var invalid = new ProblemRequest("A+B", "题面", 1000, 128, Difficulty.EASY,
                List.of(new TestCaseRequest("1 2", null, true)));
        mvc.perform(post("/api/admin/problems").session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请提供预期输出"));
        assertThat(problems.count()).isZero();
        assertThat(testCases.count()).isZero();
    }

    @Test
    void servesProblemPages() throws Exception {
        mvc.perform(get("/problems.html")).andExpect(status().isOk());
        mvc.perform(get("/problem.html")).andExpect(status().isOk());
        mvc.perform(get("/admin.html")).andExpect(status().isOk());
    }

    private Problem addProblem(String title, Difficulty difficulty) {
        return problems.saveAndFlush(new Problem(title, "输入数据\n输出答案", 1000, 128, difficulty));
    }

    private MockHttpSession login(Role role) throws Exception {
        users.saveAndFlush(new User("test_user", bcrypt.encode("secret123"), role));
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "test_user", "password": "secret123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
