package com.legalflow.billing.scheduler;

import com.legalflow.billing.domain.Invoice;
import com.legalflow.billing.domain.InvoiceStatus;
import com.legalflow.billing.repository.InvoiceRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class InvoiceOverdueScheduler {

    private final InvoiceRepository invoiceRepository;

    public InvoiceOverdueScheduler(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Scheduled(fixedDelayString = "${legalflow.billing.processing-interval-ms:60000}")
    @Transactional
    public void markOverdueInvoices() {
        List<Invoice> staleInvoices = invoiceRepository.findAllByStatusAndDueDateBefore(
                InvoiceStatus.SENT, LocalDate.now());

        for (Invoice invoice : staleInvoices) {
            invoice.setStatus(InvoiceStatus.OVERDUE);
        }
    }
}
