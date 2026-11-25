package com.minioj.controller;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:submission-flow-test;DB_CLOSE_DELAY=-1",
        "MINIOJ_ADMIN_PASSWORD="
})
@TestPropertySource(properties = "oj.sandbox.type=process")
class SubmissionFlowTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void registersSolvesSeededProblemAndFindsSubmissionInHistory() throws Exception {
        assertThat(get("/register.html", 200)).contains("/api/auth/register");
        assertThat(get("/login.html", 200)).contains("/api/auth/login");
        var credentials = Map.of("username", "flow_user", "password", "secret123");
        post("/api/auth/register", credentials, 201);
        post("/api/auth/login", credentials, 200);

        assertThat(get("/problems.html", 200)).contains("/js/problems.js");
        var problem = data(get("/api/problems?keyword=A%2BB", 200)).path("items").get(0);
        assertThat(problem.path("title").asText()).isEqualTo("A+B");
        long problemId = problem.path("id").asLong();
        assertThat(get("/problem.html?id=" + problemId, 200)).contains("/js/problem.js");
        assertThat(data(get("/api/problems/" + problemId, 200)).path("testCases").size()).isEqualTo(1);

        String code = """
                public class Main {
                    public static void main(String[] args) {
                        var input = new java.util.Scanner(System.in);
                        System.out.println(input.nextLong() + input.nextLong());
                    }
                }
                """;
        var accepted = post("/api/submissions", Map.of("problemId", problemId, "language", "JAVA", "code", code), 202);
        long submissionId = accepted.path("submissionId").asLong();
        var result = awaitTerminal(submissionId);
        assertThat(result.path("status").asText()).isEqualTo("AC");
        assertThat(result.path("timeUsedMs").asLong()).isNotNegative();
        assertThat(result.path("code").asText()).isEqualTo(code);

        assertThat(get("/submissions.html", 200)).contains("/js/submissions.js");
        var row = data(get("/api/submissions", 200)).path("items").get(0);
        assertThat(row.path("submissionId").asLong()).isEqualTo(submissionId);
        assertThat(row.path("problemTitle").asText()).isEqualTo("A+B");
        assertThat(row.path("status").asText()).isEqualTo("AC");
        assertThat(row.has("code")).isFalse();
    }

    private JsonNode awaitTerminal(long id) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            var result = data(get("/api/submissions/" + id, 200));
            String status = result.path("status").asText();
            if (!"PENDING".equals(status) && !"JUDGING".equals(status)) {
                return result;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("提交 " + id + " 未在测试期限内结束");
    }

    private String get(String path, int expectedStatus) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET(), expectedStatus);
    }

    private JsonNode post(String path, Map<String, ?> body, int expectedStatus) throws Exception {
        return data(send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))), expectedStatus));
    }

    private String send(HttpRequest.Builder request, int expectedStatus) throws Exception {
        var response = client.send(request.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
        return response.body();
    }

    private JsonNode data(String body) throws Exception {
        return json.readTree(body).get("data");
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
