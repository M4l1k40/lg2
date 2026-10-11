package com.legalflow.billing.controller;

import com.legalflow.billing.config.BillingSecurityConfig;
import com.legalflow.billing.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Règles posées par @PreAuthorize sur le contrôleur de facturation (le service est simulé). */
@WebMvcTest(InvoiceController.class)
@Import(BillingSecurityConfig.class)
class InvoiceRoleAccessTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    private static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }

    private static JwtRequestPostProcessor noRole() {
        return jwt().jwt(token -> token.claim("lawFirmId", FIRM_A.toString()));
    }

    private static final String INVOICE_BODY = "{\"lawFirmId\":\"11111111-1111-1111-1111-111111111111\",\"caseId\":\"cccccccc-cccc-cccc-cccc-cccccccccccc\",\"clientId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"invoiceNumber\":\"INV-1\",\"description\":\"Honoraires\",\"amount\":100.50,\"dueDate\":\"2099-01-01\"}";

    @MockBean
    private InvoiceService invoiceService;

    @Test
    void clientCanOnlyReadInvoices() throws Exception {
        mockMvc.perform(get("/invoices").with(as("CLIENT"))).andExpect(status().isOk());
        mockMvc.perform(get("/invoices/{id}", ID).with(as("CLIENT"))).andExpect(status().isOk());

        mockMvc.perform(post("/invoices").with(as("CLIENT")).contentType(MediaType.APPLICATION_JSON).content(INVOICE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/invoices/{id}", ID).with(as("CLIENT")).contentType(MediaType.APPLICATION_JSON).content(INVOICE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/invoices/{id}/status", ID).param("status", "PAID").with(as("CLIENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/invoices/{id}", ID).with(as("CLIENT"))).andExpect(status().isForbidden());
    }

    @Test
    void secretaryTracksPaymentsButCannotCreateOrDelete() throws Exception {
        mockMvc.perform(get("/invoices").with(as("SECRETARY"))).andExpect(status().isOk());
        mockMvc.perform(patch("/invoices/{id}/status", ID).param("status", "PAID").with(as("SECRETARY")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/invoices").with(as("SECRETARY")).contentType(MediaType.APPLICATION_JSON).content(INVOICE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/invoices/{id}", ID).with(as("SECRETARY"))).andExpect(status().isForbidden());
    }

    @Test
    void lawyerCreatesAndEditsButCannotDelete() throws Exception {
        mockMvc.perform(post("/invoices").with(as("LAWYER")).contentType(MediaType.APPLICATION_JSON).content(INVOICE_BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/invoices/{id}", ID).with(as("LAWYER")).contentType(MediaType.APPLICATION_JSON).content(INVOICE_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/invoices/{id}", ID).with(as("LAWYER"))).andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminCanDeleteInvoices() throws Exception {
        mockMvc.perform(delete("/invoices/{id}", ID).with(as("ADMIN"))).andExpect(status().isNoContent());
    }

    @Test
    void userWithoutAnyRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/invoices").with(noRole())).andExpect(status().isForbidden());
    }
}
