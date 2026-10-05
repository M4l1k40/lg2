package com.legalflow.billing.controller;

import com.legalflow.billing.domain.Invoice;
import com.legalflow.billing.dto.InvoiceRequest;
import com.legalflow.billing.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public List<Invoice> getInvoices(@RequestParam(required = false) UUID lawFirmId) {
        return invoiceService.getInvoices(lawFirmId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Invoice createInvoice(@Valid @RequestBody InvoiceRequest request) {
        return invoiceService.createInvoice(request);
    }

    @GetMapping("/{id}")
    public Invoice getInvoice(@PathVariable UUID id) {
        return invoiceService.getInvoice(id);
    }

    @PutMapping("/{id}")
    public Invoice updateInvoice(@PathVariable UUID id, @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.updateInvoice(id, request);
    }

    @PatchMapping("/{id}/status")
    public Invoice updateStatus(@PathVariable UUID id,
                               @RequestParam(required = false) String status,
                               @RequestBody(required = false) InvoiceRequest request) {
        String requestedStatus = status != null ? status : request == null ? null : request.getInvoiceNumber();
        if (requestedStatus == null || requestedStatus.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status is required.");
        }
        return invoiceService.updateStatus(id, requestedStatus);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInvoice(@PathVariable UUID id) {
        invoiceService.deleteInvoice(id);
    }
}
