package com.legalflow.billing.repository;

import com.legalflow.billing.domain.Invoice;
import com.legalflow.billing.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndLawFirmId(UUID id, UUID lawFirmId);

    List<Invoice> findAllByLawFirmIdOrderByDueDateAsc(UUID lawFirmId);

    List<Invoice> findAllByLawFirmIdAndStatusOrderByDueDateAsc(UUID lawFirmId, InvoiceStatus status);

    List<Invoice> findAllByStatusAndDueDateBefore(InvoiceStatus status, LocalDate dueDate);

    boolean existsByInvoiceNumberAndLawFirmId(String invoiceNumber, UUID lawFirmId);
}
