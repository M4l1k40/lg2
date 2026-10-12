package com.legalflow.user.controller;

import com.legalflow.user.config.UserSecurityConfig;
import com.legalflow.user.service.UserDirectoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Règles posées par @PreAuthorize sur le contrôleur (le service est simulé). */
@WebMvcTest(UserController.class)
@Import(UserSecurityConfig.class)
class UserControllerRoleAccessTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String USER_ID = "33333333-3333-3333-3333-333333333333";
    private static final String CREATE_BODY = "{\"username\":\"jdupont\",\"email\":\"jdupont@cabinet.test\","
            + "\"firstName\":\"Jean\",\"lastName\":\"Dupont\",\"role\":\"LAWYER\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDirectoryService userDirectoryService;

    @Test
    void adminManagesTheDirectory() throws Exception {
        mockMvc.perform(get("/users").with(as("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(get("/users/lawyers").with(as("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(post("/users").with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/users/{id}/enabled", USER_ID).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isOk());
        mockMvc.perform(post("/users/{id}/reset-password", USER_ID).with(as("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void secretaryAndLawyerCanOnlyListLawyers() throws Exception {
        for (String role : new String[] {"SECRETARY", "LAWYER"}) {
            mockMvc.perform(get("/users/lawyers").with(as(role))).andExpect(status().isOk());

            mockMvc.perform(get("/users").with(as(role))).andExpect(status().isForbidden());
            mockMvc.perform(post("/users").with(as(role)).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/users/{id}/enabled", USER_ID).with(as(role)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":false}")).andExpect(status().isForbidden());
            mockMvc.perform(post("/users/{id}/reset-password", USER_ID).with(as(role))).andExpect(status().isForbidden());
        }
    }

    @Test
    void clientCanOnlyReadItsOwnProfile() throws Exception {
        mockMvc.perform(get("/users/me").with(as("CLIENT"))).andExpect(status().isOk());

        mockMvc.perform(get("/users").with(as("CLIENT"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/users/lawyers").with(as("CLIENT"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/users").with(as("CLIENT")).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPayloadIsRejectedWithABadRequest() throws Exception {
        mockMvc.perform(post("/users").with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"x\",\"email\":\"not-an-email\",\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userWithoutAnyRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/users/me").with(noRole())).andExpect(status().isForbidden());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }

    private static JwtRequestPostProcessor noRole() {
        return jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }
}
