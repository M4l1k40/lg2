package com.legalflow.billing.service;

import com.legalflow.billing.domain.Invoice;
import com.legalflow.billing.domain.InvoiceStatus;
import com.legalflow.billing.integration.CaseServiceOwnershipVerifier;
import com.legalflow.billing.repository.InvoiceRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;

class InvoiceServiceRulesTest {

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

    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_CLIENT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID INVOICE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private InvoiceRepository repository;
    private InvoiceService service;

    @BeforeEach
    void setUp() {
        repository = mock(InvoiceRepository.class);
        service = new InvoiceService(repository, mock(CaseServiceOwnershipVerifier.class));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clientSeesOnlyItsOwnInvoices() {
        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");
        service.getInvoices(null);

        verify(repository).findAllByLawFirmIdAndClientIdOrderByDueDateAsc(FIRM_A, CLIENT_ID);
        verify(repository, never()).findAllByLawFirmIdOrderByDueDateAsc(any());
    }

    @Test
    void staffSeesAllInvoicesOfTheFirm() {
        for (String role : new String[] {"ADMIN", "LAWYER", "SECRETARY"}) {
            authenticate(UUID.randomUUID(), null, role);
            service.getInvoices(null);
        }

        verify(repository, org.mockito.Mockito.times(3)).findAllByLawFirmIdOrderByDueDateAsc(FIRM_A);
        verify(repository, never()).findAllByLawFirmIdAndClientIdOrderByDueDateAsc(any(), any());
    }

    @Test
    void clientCannotReadAnotherClientsInvoice() {
        when(repository.findByIdAndLawFirmId(INVOICE_ID, FIRM_A)).thenReturn(Optional.of(invoice(CLIENT_ID)));

        authenticate(UUID.randomUUID(), OTHER_CLIENT_ID, "CLIENT");
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.getInvoice(INVOICE_ID));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        authenticate(UUID.randomUUID(), CLIENT_ID, "CLIENT");
        assertEquals(CLIENT_ID, service.getInvoice(INVOICE_ID).getClientId());

        authenticate(UUID.randomUUID(), null, "SECRETARY");
        assertTrue(service.getInvoice(INVOICE_ID).getAmount().compareTo(BigDecimal.TEN) == 0);
    }

    @Test
    void clientWithoutClientIdClaimIsForbidden() {
        authenticate(UUID.randomUUID(), null, "CLIENT");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.getInvoices(null));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    private Invoice invoice(UUID clientId) {
        return new Invoice(FIRM_A, UUID.randomUUID(), clientId, "INV-1", "Honoraires", BigDecimal.TEN,
                LocalDate.now().plusDays(10), InvoiceStatus.SENT, "creator");
    }
}
