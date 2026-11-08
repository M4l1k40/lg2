package com.legalflow.billing.service;

import com.legalflow.billing.domain.Invoice;
import com.legalflow.billing.domain.InvoiceStatus;
import com.legalflow.billing.dto.InvoiceRequest;
import com.legalflow.billing.integration.CaseServiceOwnershipVerifier;
import com.legalflow.billing.repository.InvoiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceServiceUpdateRulesTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CASE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID INVOICE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private InvoiceRepository repository;
    private InvoiceService service;

    @BeforeEach
    void setUp() {
        repository = mock(InvoiceRepository.class);
        service = new InvoiceService(repository, mock(CaseServiceOwnershipVerifier.class));
        when(repository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("token").header("alg", "none")
                        .claim("lawFirmId", FIRM_A.toString())
                        .subject(UUID.randomUUID().toString()).build(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anOpenInvoiceCanStillBeModified() {
        for (InvoiceStatus status : new InvoiceStatus[] {InvoiceStatus.DRAFT, InvoiceStatus.SENT, InvoiceStatus.OVERDUE}) {
            stub(invoice(status));

            Invoice updated = service.updateInvoice(INVOICE_ID, request("INV-2", new BigDecimal("250.00")));

            assertEquals("INV-2", updated.getInvoiceNumber());
            assertEquals(new BigDecimal("250.00"), updated.getAmount());
        }
    }

    @Test
    void aPaidOrCancelledInvoiceIsFrozen() {
        for (InvoiceStatus status : new InvoiceStatus[] {InvoiceStatus.PAID, InvoiceStatus.CANCELLED}) {
            stub(invoice(status));

            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> service.updateInvoice(INVOICE_ID, request("INV-2", new BigDecimal("250.00"))));

            assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode().value());
        }
        verify(repository, never()).save(any(Invoice.class));
    }

    private void stub(Invoice invoice) {
        when(repository.findByIdAndLawFirmId(INVOICE_ID, FIRM_A)).thenReturn(Optional.of(invoice));
    }

    private Invoice invoice(InvoiceStatus status) {
        Invoice invoice = new Invoice(FIRM_A, CASE_ID, CLIENT_ID, "INV-1", "Honoraires", BigDecimal.TEN,
                LocalDate.now().plusDays(10), status, "creator");
        ReflectionTestUtils.setField(invoice, "id", INVOICE_ID);
        return invoice;
    }

    private InvoiceRequest request(String number, BigDecimal amount) {
        InvoiceRequest request = new InvoiceRequest();
        request.setLawFirmId(FIRM_A);
        request.setCaseId(CASE_ID);
        request.setClientId(CLIENT_ID);
        request.setInvoiceNumber(number);
        request.setDescription("Honoraires modifiés");
        request.setAmount(amount);
        request.setDueDate(LocalDate.now().plusDays(20));
        return request;
    }
}
