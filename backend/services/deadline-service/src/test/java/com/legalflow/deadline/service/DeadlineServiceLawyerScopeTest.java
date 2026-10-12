package com.legalflow.deadline.service;

import com.legalflow.deadline.domain.Deadline;
import com.legalflow.deadline.domain.DeadlineStatus;
import com.legalflow.deadline.domain.Priority;
import com.legalflow.deadline.integration.CaseServiceOwnershipVerifier;
import com.legalflow.deadline.repository.DeadlineRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Un avocat ne voit que les échéances des dossiers dont il a la charge ; le personnel administratif voit tout. */
class DeadlineServiceLawyerScopeTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MY_CASE = UUID.fromString("aaaa1111-aaaa-1111-aaaa-111111111111");
    private static final UUID OTHER_CASE = UUID.fromString("bbbb2222-bbbb-2222-bbbb-222222222222");
    private static final UUID DEADLINE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private DeadlineRepository repository;
    private CaseServiceOwnershipVerifier verifier;
    private DeadlineService service;

    @BeforeEach
    void setUp() {
        repository = mock(DeadlineRepository.class);
        verifier = mock(CaseServiceOwnershipVerifier.class);
        service = new DeadlineService(repository, verifier);
        when(repository.findAllByLawFirmIdOrderByDueDateAsc(FIRM_A)).thenReturn(List.of(deadline(MY_CASE), deadline(OTHER_CASE)));
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "case not found in your law firm"))
                .when(verifier).verifyCaseBelongsToCurrentTenant(OTHER_CASE);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lawyerSeesOnlyTheDeadlinesOfHisCases() {
        authenticate("LAWYER");

        List<Deadline> visible = service.getDeadlines(null);

        assertEquals(1, visible.size());
        assertEquals(MY_CASE, visible.get(0).getCaseId());
    }

    @Test
    void secretaryAndAdminSeeEveryDeadlineWithoutCallingTheCaseService() {
        for (String role : new String[] {"SECRETARY", "ADMIN"}) {
            authenticate(role);
            assertEquals(2, service.getDeadlines(null).size());
        }

        verify(verifier, never()).verifyCaseBelongsToCurrentTenant(any());
    }

    @Test
    void lawyerCannotOpenTheDeadlineOfAnotherLawyersCase() {
        authenticate("LAWYER");
        when(repository.findByIdAndLawFirmId(DEADLINE_ID, FIRM_A)).thenReturn(Optional.of(deadline(OTHER_CASE)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.getDeadline(DEADLINE_ID));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void lawyerCanOpenTheDeadlineOfHisCase() {
        authenticate("LAWYER");
        when(repository.findByIdAndLawFirmId(DEADLINE_ID, FIRM_A)).thenReturn(Optional.of(deadline(MY_CASE)));

        assertEquals(MY_CASE, service.getDeadline(DEADLINE_ID).getCaseId());
    }

    private Deadline deadline(UUID caseId) {
        return new Deadline(FIRM_A, caseId, "Dépôt des conclusions", "Description", LocalDateTime.now().plusDays(5),
                Priority.HIGH, DeadlineStatus.PENDING, "creator");
    }

    private void authenticate(String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("lawFirmId", FIRM_A.toString())
                .subject(UUID.randomUUID().toString())
                .build();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
