package com.legalflow.billing.service;

import com.legalflow.billing.domain.Invoice;
import com.legalflow.billing.domain.InvoiceStatus;
import com.legalflow.billing.dto.InvoiceRequest;
import com.legalflow.billing.integration.CaseServiceOwnershipVerifier;
import com.legalflow.billing.repository.InvoiceRepository;
import com.legalflow.billing.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> TRANSITIONS = Map.of(
            InvoiceStatus.DRAFT, Set.of(InvoiceStatus.SENT, InvoiceStatus.CANCELLED),
            InvoiceStatus.SENT, Set.of(InvoiceStatus.PAID, InvoiceStatus.OVERDUE, InvoiceStatus.CANCELLED),
            InvoiceStatus.OVERDUE, Set.of(InvoiceStatus.PAID, InvoiceStatus.CANCELLED),
            InvoiceStatus.PAID, Set.of(),
            InvoiceStatus.CANCELLED, Set.of()
    );

    private final InvoiceRepository invoiceRepository;
    private final CaseServiceOwnershipVerifier caseServiceOwnershipVerifier;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          CaseServiceOwnershipVerifier caseServiceOwnershipVerifier) {
        this.invoiceRepository = invoiceRepository;
        this.caseServiceOwnershipVerifier = caseServiceOwnershipVerifier;
    }

    @Transactional(readOnly = true)
    public List<Invoice> getInvoices(UUID requestedLawFirmId) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(requestedLawFirmId, lawFirmId);
        return invoiceRepository.findAllByLawFirmIdOrderByDueDateAsc(lawFirmId);
    }

    @Transactional
    public Invoice createInvoice(InvoiceRequest request) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(request.getLawFirmId(), lawFirmId);

        if (invoiceRepository.existsByInvoiceNumberAndLawFirmId(request.getInvoiceNumber(), lawFirmId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice number already exists in this law firm.");
        }

        caseServiceOwnershipVerifier.verifyCaseBelongsToCurrentTenant(request.getCaseId());

        Invoice invoice = new Invoice(
                lawFirmId,
                request.getCaseId(),
                request.getClientId(),
                request.getInvoiceNumber(),
                request.getDescription(),
                request.getAmount(),
                request.getDueDate(),
                InvoiceStatus.SENT,
                TenantContext.currentUserSubjectOrNull()
        );
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public Invoice getInvoice(UUID id) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        return invoiceRepository.findByIdAndLawFirmId(id, lawFirmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + id));
    }

    @Transactional
    public Invoice updateInvoice(UUID id, InvoiceRequest request) {
        UUID lawFirmId = TenantContext.requireLawFirmId();
        rejectMismatchedTenant(request.getLawFirmId(), lawFirmId);

        Invoice invoice = getInvoice(id);
        caseServiceOwnershipVerifier.verifyCaseBelongsToCurrentTenant(request.getCaseId());

        if (!invoice.getInvoiceNumber().equals(request.getInvoiceNumber())
                && invoiceRepository.existsByInvoiceNumberAndLawFirmId(request.getInvoiceNumber(), lawFirmId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice number already exists in this law firm.");
        }

        invoice.setCaseId(request.getCaseId());
        invoice.setClientId(request.getClientId());
        invoice.setInvoiceNumber(request.getInvoiceNumber());
        invoice.setDescription(request.getDescription());
        invoice.setAmount(request.getAmount());
        invoice.setDueDate(request.getDueDate());
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice updateStatus(UUID id, String requestedStatus) {
        Invoice invoice = getInvoice(id);
        InvoiceStatus currentStatus = invoice.getStatus();
        InvoiceStatus nextStatus;
        try {
            nextStatus = InvoiceStatus.valueOf(requestedStatus.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unknown invoice status: " + requestedStatus);
        }

        Set<InvoiceStatus> allowed = TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(nextStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invoice status transition from " + currentStatus + " to " + nextStatus + " is not allowed.");
        }

        invoice.setStatus(nextStatus);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public void deleteInvoice(UUID id) {
        Invoice invoice = getInvoice(id);
        invoiceRepository.delete(invoice);
    }

    private void rejectMismatchedTenant(UUID requestedLawFirmId, UUID authenticatedLawFirmId) {
        if (requestedLawFirmId != null && !authenticatedLawFirmId.equals(requestedLawFirmId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Requested lawFirmId does not match the authenticated law firm.");
        }
    }
}
