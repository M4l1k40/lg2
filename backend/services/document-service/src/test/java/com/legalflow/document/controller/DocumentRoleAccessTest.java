package com.legalflow.document.controller;

import com.legalflow.document.config.DocumentSecurityConfig;
import com.legalflow.document.service.DocumentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Règles posées par @PreAuthorize sur le contrôleur de documents (le service est simulé). */
@WebMvcTest(DocumentController.class)
@Import(DocumentSecurityConfig.class)
class DocumentRoleAccessTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }

    private static JwtRequestPostProcessor noRole() {
        return jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }

    private static final String DOCUMENT_BODY = "{\"caseId\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\",\"fileName\":\"contract.pdf\",\"fileType\":\"application/pdf\",\"fileSize\":245678,\"storageKey\":\"documents/contract.pdf\",\"visibility\":\"CASE_MEMBERS\"}";

    @MockBean
    private DocumentService documentService;

    @Test
    void clientCanOnlyReadDocuments() throws Exception {
        mockMvc.perform(get("/documents/{id}", ID).with(as("CLIENT"))).andExpect(status().isOk());
        mockMvc.perform(get("/documents/case/{id}", ID).with(as("CLIENT"))).andExpect(status().isOk());

        mockMvc.perform(post("/documents").with(as("CLIENT")).contentType(MediaType.APPLICATION_JSON).content(DOCUMENT_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/documents/{id}", ID).with(as("CLIENT")).contentType(MediaType.APPLICATION_JSON).content(DOCUMENT_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/documents/{id}", ID).with(as("CLIENT"))).andExpect(status().isForbidden());
    }

    @Test
    void secretaryCanUploadButNotEditOrDelete() throws Exception {
        mockMvc.perform(post("/documents").with(as("SECRETARY")).contentType(MediaType.APPLICATION_JSON).content(DOCUMENT_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/documents/{id}", ID).with(as("SECRETARY")).contentType(MediaType.APPLICATION_JSON).content(DOCUMENT_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/documents/{id}", ID).with(as("SECRETARY"))).andExpect(status().isForbidden());
    }

    @Test
    void lawyerAndAdminManageDocuments() throws Exception {
        for (String role : new String[] {"LAWYER", "ADMIN"}) {
            mockMvc.perform(put("/documents/{id}", ID).with(as(role)).contentType(MediaType.APPLICATION_JSON).content(DOCUMENT_BODY))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/documents/{id}", ID).with(as(role))).andExpect(status().isNoContent());
        }
    }

    @Test
    void userWithoutAnyRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/documents/{id}", ID).with(noRole())).andExpect(status().isForbidden());
    }
}
