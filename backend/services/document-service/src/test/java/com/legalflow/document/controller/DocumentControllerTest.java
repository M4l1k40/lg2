package com.legalflow.document.controller;

import com.legalflow.document.config.DocumentSecurityConfig;
import com.legalflow.document.domain.Document;
import com.legalflow.document.domain.DocumentVisibility;
import com.legalflow.document.dto.DocumentRequest;
import com.legalflow.document.integration.CaseServiceOwnershipVerifier;
import com.legalflow.document.repository.DocumentRepository;
import com.legalflow.document.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import({DocumentService.class, DocumentSecurityConfig.class, DocumentExceptionHandler.class})
class DocumentControllerTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRM_B = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CASE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DOCUMENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private CaseServiceOwnershipVerifier caseServiceOwnershipVerifier;

    @Test
    void authenticatedTenantCanCreateDocument() throws Exception {
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/documents")
                        .with(jwt().jwt(token -> token
                                .subject(USER_ID.toString())
                                .claim("lawFirmId", FIRM_A.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lawFirmId").value(FIRM_A.toString()))
                .andExpect(jsonPath("$.uploadedBy").value(USER_ID.toString()))
                .andExpect(jsonPath("$.caseId").value(CASE_ID.toString()));

        verify(caseServiceOwnershipVerifier).verifyCaseBelongsToCurrentTenant(CASE_ID);
    }

    @Test
    void tenantCanReadOwnDocument() throws Exception {
        when(documentRepository.findByIdAndLawFirmId(DOCUMENT_ID, FIRM_A))
                .thenReturn(Optional.of(document(FIRM_A)));

        mockMvc.perform(get("/documents/{id}", DOCUMENT_ID)
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("contract.pdf"));
    }

    @Test
    void otherTenantCannotReadUpdateOrDeleteDocumentById() throws Exception {
        when(documentRepository.findByIdAndLawFirmId(DOCUMENT_ID, FIRM_A))
                .thenReturn(Optional.of(document(FIRM_A)));
        when(documentRepository.findByIdAndLawFirmId(DOCUMENT_ID, FIRM_B)).thenReturn(Optional.empty());

        mockMvc.perform(get("/documents/{id}", DOCUMENT_ID)
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_B.toString()))))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/documents/{id}", DOCUMENT_ID)
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_B.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/documents/{id}", DOCUMENT_ID)
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_B.toString()))))
                .andExpect(status().isNotFound());

        verify(documentRepository, never()).save(any(Document.class));
        verify(documentRepository, never()).delete(any(Document.class));
    }

    @Test
    void caseDocumentListIsAlwaysScopedToTokenTenant() throws Exception {
        when(documentRepository.findAllByLawFirmIdAndCaseIdOrderByCreatedAtDesc(FIRM_A, CASE_ID))
                .thenReturn(List.of(document(FIRM_A)));
        when(documentRepository.findAllByLawFirmIdAndCaseIdOrderByCreatedAtDesc(FIRM_B, CASE_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/documents/case/{caseId}", CASE_ID)
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_B.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(documentRepository).findAllByLawFirmIdAndCaseIdOrderByCreatedAtDesc(FIRM_B, CASE_ID);
        verify(caseServiceOwnershipVerifier).verifyCaseBelongsToCurrentTenant(CASE_ID);
    }

    @Test
    void invalidDocumentFieldsReturn400() throws Exception {
        mockMvc.perform(post("/documents")
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","fileName":"","fileType":"application/pdf","fileSize":0,"storageKey":"key","visibility":"PRIVATE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/documents/{id}", DOCUMENT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void caseServiceOwnershipFailurePreventsDocumentInsert() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "case not found in your law firm"))
                .when(caseServiceOwnershipVerifier).verifyCaseBelongsToCurrentTenant(CASE_ID);

        mockMvc.perform(post("/documents")
                        .with(jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnprocessableEntity());

        verify(documentRepository, never()).save(any(Document.class));
    }

    private Document document(UUID lawFirmId) {
        return new Document(lawFirmId, CASE_ID, USER_ID, "contract.pdf", "application/pdf",
                245678L, "documents/contract.pdf", "Client contract", DocumentVisibility.CASE_MEMBERS);
    }

    private String validRequest() {
        return """
                {"caseId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","fileName":"contract.pdf","fileType":"application/pdf","fileSize":245678,"storageKey":"documents/contract.pdf","description":"Client contract","visibility":"CASE_MEMBERS"}
                """;
    }
}
