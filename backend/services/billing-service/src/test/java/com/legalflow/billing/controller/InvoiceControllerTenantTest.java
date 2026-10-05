package com.legalflow.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalflow.billing.domain.Invoice;
import com.legalflow.billing.domain.InvoiceStatus;
import com.legalflow.billing.dto.InvoiceRequest;
import com.legalflow.billing.integration.CaseServiceOwnershipVerifier;
import com.legalflow.billing.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InvoiceControllerTenantTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @MockBean
    private CaseServiceOwnershipVerifier caseServiceOwnershipVerifier;

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
    }

    @Test
    void createInvoiceUsesAuthenticatedLawFirmAndRejectsTenantMismatch() throws Exception {
        UUID firmA = UUID.randomUUID();
        UUID firmB = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        InvoiceRequest request = new InvoiceRequest();
        request.setLawFirmId(firmA);
        request.setCaseId(caseId);
        request.setClientId(clientId);
        request.setInvoiceNumber("INV-1001");
        request.setDescription("Retainer invoice");
        request.setAmount(new BigDecimal("1250.50"));
        request.setDueDate(LocalDate.now().plusDays(15));

        mockMvc.perform(post("/invoices")
                        .with(jwt().jwt(token -> token.subject("lawyer-a").claim("lawFirmId", firmA.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lawFirmId").value(firmA.toString()))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-1001"));

        Invoice invoice = invoiceRepository.findAll().getFirst();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);

        mockMvc.perform(post("/invoices")
                        .with(jwt().jwt(token -> token.subject("lawyer-b").claim("lawFirmId", firmB.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAndStatusFlowRespectsTenantIsolation() throws Exception {
        UUID firmA = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        InvoiceRequest request = new InvoiceRequest();
        request.setLawFirmId(firmA);
        request.setCaseId(caseId);
        request.setClientId(clientId);
        request.setInvoiceNumber("INV-2001");
        request.setDescription("Second invoice");
        request.setAmount(new BigDecimal("500.00"));
        request.setDueDate(LocalDate.now().plusDays(3));

        mockMvc.perform(post("/invoices")
                        .with(jwt().jwt(token -> token.subject("lawyer-a").claim("lawFirmId", firmA.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/invoices")
                        .with(jwt().jwt(token -> token.subject("lawyer-a").claim("lawFirmId", firmA.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceNumber").value("INV-2001"));

        mockMvc.perform(patch("/invoices/{id}/status", invoiceRepository.findAll().getFirst().getId())
                        .param("status", "PAID")
                        .with(jwt().jwt(token -> token.subject("lawyer-a").claim("lawFirmId", firmA.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }
}
