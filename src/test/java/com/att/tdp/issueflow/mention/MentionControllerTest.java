package com.att.tdp.issueflow.mention;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.common.security.IssueFlowUserDetails;
import com.att.tdp.issueflow.common.security.JwtAuthenticationFilter;
import com.att.tdp.issueflow.mention.dto.MentionPage;
import com.att.tdp.issueflow.user.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice covering the mention inbox's permission rule: non-admins can only read their
 * own inbox; ADMINs can read anyone's. Method security is enabled via a local test config so
 * the {@code @PreAuthorize} expression is actually evaluated.
 */
@WebMvcTest(controllers = MentionController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = JwtAuthenticationFilter.class))
@Import(MentionControllerTest.MethodSecurityTestConfig.class)
class MentionControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig { }

    @Autowired MockMvc mvc;
    @MockBean MentionService mentionService;

    @Test
    void rejectsNonAdminReadingAnotherUsersInbox() throws Exception {
        IssueFlowUserDetails alice = new IssueFlowUserDetails(1L, "alice", Role.DEVELOPER);

        mvc.perform(get("/users/2/mentions").with(user(alice)))
            .andExpect(status().isForbidden());

        verifyNoInteractions(mentionService);
    }

    @Test
    void allowsUserToReadOwnInbox() throws Exception {
        IssueFlowUserDetails alice = new IssueFlowUserDetails(1L, "alice", Role.DEVELOPER);
        when(mentionService.inbox(1L, 1, 20)).thenReturn(new MentionPage(List.of(), 0L, 1));

        mvc.perform(get("/users/1/mentions").with(user(alice)))
            .andExpect(status().isOk());
    }

    @Test
    void allowsAdminToReadAnyInbox() throws Exception {
        IssueFlowUserDetails admin = new IssueFlowUserDetails(99L, "admin", Role.ADMIN);
        when(mentionService.inbox(2L, 1, 20)).thenReturn(new MentionPage(List.of(), 0L, 1));

        mvc.perform(get("/users/2/mentions").with(user(admin)))
            .andExpect(status().isOk());
    }
}
