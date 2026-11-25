package com.minioj.controller;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minioj.entity.Role;
import com.minioj.entity.User;
import com.minioj.repository.UserRepository;
import com.minioj.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-test;DB_CLOSE_DELAY=-1",
        "MINIOJ_ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private BCryptPasswordEncoder bcrypt;

    @Autowired
    private UserService userService;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void registersUserWithBcryptHashAndWithoutSession() throws Exception {
        var result = credentials("register", "new_user", "secret123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.username").value("new_user"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();

        User saved = users.findByUsername("new_user").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(bcrypt.matches("secret123", saved.getPasswordHash())).isTrue();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        credentials("register", "new_user", "secret123").andExpect(status().isCreated());

        credentials("register", "new_user", "another123")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("用户名已存在"));

        assertThat(users.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "ab, secret123",
            "bad-name, secret123",
            "abcdefghijklmnopqrstuvwxyz, secret123",
            "new_user, short",
            "new_user, 123456789012345678901234567890123"
    })
    void validatesCredentialsOnRegistrationAndLogin(String username, String password) throws Exception {
        credentials("register", username, password)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        credentials("login", username, password)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        assertThat(users.count()).isZero();
    }

    @Test
    void logsInRotatesSessionIdAndReadsCurrentUser() throws Exception {
        credentials("register", "new_user", "secret123").andExpect(status().isCreated());
        MockHttpSession existing = new MockHttpSession();
        String originalId = existing.getId();

        var result = mvc.perform(post("/api/auth/login")
                        .session(existing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "new_user", "password", "secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("new_user"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);

        assertThat(session.getId()).isNotEqualTo(originalId);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("new_user"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void rejectsIncorrectPasswordWithoutCreatingSession() throws Exception {
        credentials("register", "new_user", "secret123").andExpect(status().isCreated());

        var result = credentials("login", "new_user", "wrong123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void supportsMultibytePasswordsBeyondBcryptByteLimit() throws Exception {
        String prefix = "密".repeat(24);
        String password = prefix + "码";
        credentials("register", "unicode_user", password).andExpect(status().isCreated());

        credentials("login", "unicode_user", prefix + "钥").andExpect(status().isUnauthorized());
        credentials("login", "unicode_user", password).andExpect(status().isOk());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        credentials("register", "new_user", "secret123").andExpect(status().isCreated());
        MockHttpSession session = login("new_user", "secret123");

        mvc.perform(post("/api/auth/logout").session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("confirm=yes"))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(session.isInvalid()).isFalse();

        mvc.perform(post("/api/auth/logout").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRequestsToProtectedRoutesReturn401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(post("/api/submissions"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/api/admin/problems"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void nonAdminCannotAccessAdminRoutesEvenIfRoleWasInRegistrationBody() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", "new_user", "password", "secret123", "role", "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("USER"));

        mvc.perform(get("/api/admin/problems").session(login("new_user", "secret123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void adminCanAccessAdminRoutes() throws Exception {
        users.saveAndFlush(new User("admin_user", bcrypt.encode("admin123"), Role.ADMIN));

        mvc.perform(get("/api/admin/problems").session(login("admin_user", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void initializingAdminAgainKeepsTheExistingPassword() throws Exception {
        credentials("register", "admin", "attacker123").andExpect(status().isConflict());
        userService.initializeAdmin("first123");
        userService.initializeAdmin("second123");

        assertThat(users.count()).isEqualTo(1);
        credentials("login", "admin", "first123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
        credentials("login", "admin", "second123").andExpect(status().isUnauthorized());
    }

    @Test
    void servesLoginAndRegistrationPages() throws Exception {
        mvc.perform(get("/login.html")).andExpect(status().isOk());
        mvc.perform(get("/register.html")).andExpect(status().isOk());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        var result = credentials("login", username, password).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions credentials(String action, String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/" + action)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))));
    }
}
