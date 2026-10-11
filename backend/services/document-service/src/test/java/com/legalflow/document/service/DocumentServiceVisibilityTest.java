package com.legalflow.document.service;

import com.legalflow.document.domain.Document;
import com.legalflow.document.domain.DocumentVisibility;
import com.legalflow.document.integration.CaseServiceOwnershipVerifier;
import com.legalflow.document.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceVisibilityTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private void authenticate(UUID userId, UUID clientId, String... roles) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("lawFirmId", FIRM_A.toString())
                .subject(userId.toString());
        if (clientId != null) {
            builder.claim("clientId", clientId.toString());
        }
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), authorities));
    }

    private static final UUID CASE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID LAWYER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SECRETARY_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID DOCUMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private DocumentRepository repository;
    private CaseServiceOwnershipVerifier verifier;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        verifier = mock(CaseServiceOwnershipVerifier.class);
        service = new DocumentService(repository, verifier);
        when(repository.findAllByLawFirmIdAndCaseIdOrderByCreatedAtDesc(FIRM_A, CASE_ID)).thenReturn(List.of(
                document(DocumentVisibility.CASE_MEMBERS, LAWYER_ID),
                document(DocumentVisibility.LAWYER_ONLY, LAWYER_ID),
                document(DocumentVisibility.PRIVATE, LAWYER_ID)));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clientSeesOnlySharedDocuments() {
        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");

        List<Document> visible = service.getDocumentsByCase(CASE_ID);

        assertEquals(1, visible.size());
        assertEquals(DocumentVisibility.CASE_MEMBERS, visible.get(0).getVisibility());
    }

    @Test
    void secretaryDoesNotSeeLawyerOnlyNorSomeoneElsesPrivateDocuments() {
        authenticate(SECRETARY_ID, null, "SECRETARY");

        List<Document> visible = service.getDocumentsByCase(CASE_ID);

        assertEquals(1, visible.size());
        assertEquals(DocumentVisibility.CASE_MEMBERS, visible.get(0).getVisibility());
    }

    @Test
    void lawyerSeesLawyerOnlyAndOwnPrivateDocuments() {
        authenticate(LAWYER_ID, null, "LAWYER");
        assertEquals(3, service.getDocumentsByCase(CASE_ID).size());

        authenticate(UUID.randomUUID(), null, "LAWYER");
        assertEquals(2, service.getDocumentsByCase(CASE_ID).size());
    }

    @Test
    void adminSeesEverything() {
        authenticate(UUID.randomUUID(), null, "ADMIN");

        assertEquals(3, service.getDocumentsByCase(CASE_ID).size());
    }

    @Test
    void clientCannotOpenDocumentsOfACaseThatIsNotTheirs() {
        when(repository.findByIdAndLawFirmId(DOCUMENT_ID, FIRM_A))
                .thenReturn(Optional.of(document(DocumentVisibility.CASE_MEMBERS, LAWYER_ID)));
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "case not found in your law firm"))
                .when(verifier).verifyCaseBelongsToCurrentTenant(CASE_ID);

        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.getDocument(DOCUMENT_ID));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void clientCannotOpenALawyerOnlyDocumentOfItsOwnCase() {
        when(repository.findByIdAndLawFirmId(DOCUMENT_ID, FIRM_A))
                .thenReturn(Optional.of(document(DocumentVisibility.LAWYER_ONLY, LAWYER_ID)));

        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.getDocument(DOCUMENT_ID));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void clientCanOpenASharedDocumentOfItsOwnCase() {
        when(repository.findByIdAndLawFirmId(DOCUMENT_ID, FIRM_A))
                .thenReturn(Optional.of(document(DocumentVisibility.CASE_MEMBERS, LAWYER_ID)));

        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");

        assertEquals(DocumentVisibility.CASE_MEMBERS, service.getDocument(DOCUMENT_ID).getVisibility());
        verify(verifier).verifyCaseBelongsToCurrentTenant(CASE_ID);
    }

    private Document document(DocumentVisibility visibility, UUID uploadedBy) {
        return new Document(FIRM_A, CASE_ID, uploadedBy, "contract.pdf", "application/pdf", 245678L,
                "documents/contract.pdf", "Client contract", visibility);
    }
}
