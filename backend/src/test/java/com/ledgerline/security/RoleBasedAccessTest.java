package com.ledgerline.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.ledgerline.domain.UserAccount;
import com.ledgerline.domain.UserRole;
import com.ledgerline.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleBasedAccessTest {
    private static final String TEST_PASSWORD = "Test-Only-Password-123!";
    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createTestUsers() {
        userAccountRepository.deleteAll();
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        userAccountRepository.save(new UserAccount("test-admin", hash, UserRole.ADMIN));
        userAccountRepository.save(new UserAccount("test-auditor", hash, UserRole.AUDITOR));
        userAccountRepository.save(new UserAccount("test-holder", hash, UserRole.ACCOUNT_HOLDER));
    }

    @Test
    void adminCanAccessAdminRoute() throws Exception {
        mockMvc.perform(get("/api/admin/status").with(httpBasic("test-admin", TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("ADMIN"));
    }

    @Test
    void accountHolderCannotAccessAdminRoute() throws Exception {
        mockMvc.perform(get("/api/admin/status").with(httpBasic("test-holder", TEST_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditorCanAccessAuditorRouteButNotAdminRoute() throws Exception {
        mockMvc.perform(get("/api/auditor/status").with(httpBasic("test-auditor", TEST_PASSWORD)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/status").with(httpBasic("test-auditor", TEST_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/access/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanReadOwnIdentityAndRole() throws Exception {
        mockMvc.perform(get("/api/access/me").with(httpBasic("test-holder", TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("test-holder"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_ACCOUNT_HOLDER"));
    }
}
