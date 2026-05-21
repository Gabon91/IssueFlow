package com.att.tdp.issueflow.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.att.tdp.issueflow.common.security.JwtAuthenticationFilter;
import com.att.tdp.issueflow.user.dto.UserCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies Bean Validation surfaces as 400 + field errors and that bad input never reaches the service. */
@WebMvcTest(controllers = UserController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockBean UserService userService;

    @Test
    void rejectsBlankUsernameWithFourHundredAndFieldErrors() throws Exception {
        // Invalid: blank username AND too-short password AND malformed email.
        String body = om.writeValueAsString(new UserCreateRequest(
            "", "not-an-email", "Alice", Role.DEVELOPER, "short"));

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors").isArray())
            .andExpect(jsonPath("$.fieldErrors[*].field", org.hamcrest.Matchers.hasItem("username")))
            .andExpect(jsonPath("$.fieldErrors[*].field", org.hamcrest.Matchers.hasItem("email")))
            .andExpect(jsonPath("$.fieldErrors[*].field", org.hamcrest.Matchers.hasItem("password")));

        verify(userService, never()).create(any());
    }

    @Test
    void rejectsUsernameWithIllegalCharacters() throws Exception {
        String body = om.writeValueAsString(new UserCreateRequest(
            "has space!", "alice@example.com", "Alice", Role.DEVELOPER, "password1"));

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", org.hamcrest.Matchers.hasItem("username")));

        verify(userService, never()).create(any());
    }

    @Test
    void rejectsMissingRequiredRole() throws Exception {
        String body = """
            {"username":"alice","email":"alice@example.com","fullName":"Alice","password":"password1"}""";

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[*].field", org.hamcrest.Matchers.hasItem("role")));

        verify(userService, never()).create(any());
    }
}
