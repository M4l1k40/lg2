package com.legalflow.document.service;

import com.legalflow.document.domain.Document;
import com.legalflow.document.domain.DocumentVisibility;
import com.legalflow.document.dto.DocumentStatsResponse;
import com.legalflow.document.integration.CaseServiceOwnershipVerifier;
import com.legalflow.document.repository.DocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentServiceStatsTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CASE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID LAWYER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SECRETARY_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID OTHER_SECRETARY_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 12, 0);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private DocumentRepository repository;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        service = new DocumentService(repository, mock(CaseServiceOwnershipVerifier.class));
        when(repository.findAllByLawFirmIdOrderByCreatedAtDesc(FIRM_A)).thenReturn(List.of(
                document(DocumentVisibility.CASE_MEMBERS, LAWYER_ID, NOW.minusHours(2)),
                document(DocumentVisibility.CASE_MEMBERS, LAWYER_ID, NOW.minusDays(3)),
                document(DocumentVisibility.CASE_MEMBERS, LAWYER_ID, NOW.minusDays(30)),
                document(DocumentVisibility.LAWYER_ONLY, LAWYER_ID, NOW.minusHours(1)),
                document(DocumentVisibility.PRIVATE, OTHER_SECRETARY_ID, NOW.minusHours(1)),
                document(DocumentVisibility.PRIVATE, SECRETARY_ID, NOW.minusDays(1))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void secretaryCountsOnlyWhatSheIsAllowedToSee() {
        authenticate(SECRETARY_ID, "SECRETARY");

        DocumentStatsResponse stats = service.getStats(NOW.minusDays(7).atZone(ZONE).toInstant(),
                NOW.toLocalDate().atStartOfDay(ZONE).toInstant());

        // visibles : 3 documents partagés + son propre document privé ; ni LAWYER_ONLY ni le privé d'une collègue
        assertEquals(4, stats.total());
        assertEquals(3, stats.lastSevenDays());
        assertEquals(1, stats.today());
    }

    @Test
    void adminSeesEverything() {
        authenticate(UUID.randomUUID(), "ADMIN");

        DocumentStatsResponse stats = service.getStats(NOW.minusDays(7).atZone(ZONE).toInstant(),
                NOW.toLocalDate().atStartOfDay(ZONE).toInstant());

        assertEquals(6, stats.total());
        assertEquals(5, stats.lastSevenDays());
        assertEquals(3, stats.today());
    }

    @Test
    void windowsAreInclusiveOfTheirStartInstant() {
        authenticate(UUID.randomUUID(), "ADMIN");

        DocumentStatsResponse stats = service.getStats(NOW.minusDays(30).atZone(ZONE).toInstant(),
                NOW.minusHours(2).atZone(ZONE).toInstant());

        assertEquals(6, stats.lastSevenDays());
        assertEquals(3, stats.today());
    }

    private Document document(DocumentVisibility visibility, UUID uploadedBy, LocalDateTime createdAt) {
        Document document = new Document(FIRM_A, CASE_ID, uploadedBy, "file.pdf", "application/pdf", 10L, "documents/file.pdf",
                "desc", visibility);
        ReflectionTestUtils.setField(document, "createdAt", createdAt);
        return document;
    }

    private void authenticate(UUID userId, String role) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .claim("lawFirmId", FIRM_A.toString())
                .subject(userId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
