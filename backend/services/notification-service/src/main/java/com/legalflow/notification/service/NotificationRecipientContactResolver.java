package com.legalflow.notification.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.legalflow.notification.domain.NotificationRecipientType;
import com.legalflow.notification.security.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class NotificationRecipientContactResolver {

    private final RestClient clientServiceRestClient;

    public NotificationRecipientContactResolver(
            @Qualifier("clientServiceRestClient") RestClient clientServiceRestClient) {
        this.clientServiceRestClient = clientServiceRestClient;
    }

    public NotificationRecipientContact resolve(NotificationRecipientType recipientType) {
        Jwt jwt = TenantContext.requireJwt();
        String name = firstNonBlank(jwt.getClaimAsString("name"), joinedName(jwt),
                jwt.getClaimAsString("preferred_username"));
        String email = jwt.getClaimAsString("email");
        String phone = firstNonBlank(jwt.getClaimAsString("phone_number"),
                jwt.getClaimAsString("phoneNumber"), jwt.getClaimAsString("phone"));

        if (recipientType == NotificationRecipientType.CLIENT && isBlank(phone)) {
            ClientContact clientContact = findClientContact(jwt);
            name = firstNonBlank(name, joinedName(clientContact.firstName(), clientContact.lastName()));
            email = firstNonBlank(email, clientContact.email());
            phone = clientContact.phone();
        }

        return new NotificationRecipientContact(name, email, phone);
    }

    private ClientContact findClientContact(Jwt jwt) {
        UUID clientId = TenantContext.requireRecipientId();
        try {
            ClientContact contact = clientServiceRestClient.get()
                    .uri("/clients/{clientId}", clientId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue())
                    .retrieve()
                    .body(ClientContact.class);
            if (contact == null) {
                throw new NotificationDeliveryException("Client contact could not be resolved.");
            }
            return contact;
        } catch (RestClientException exception) {
            throw new NotificationDeliveryException("Client contact could not be resolved.", exception);
        }
    }

    private String joinedName(Jwt jwt) {
        return joinedName(jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name"));
    }

    private String joinedName(String firstName, String lastName) {
        String first = firstNonBlank(firstName);
        String last = firstNonBlank(lastName);
        if (first == null) {
            return last;
        }
        return last == null ? first : first + " " + last;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ClientContact(String firstName, String lastName, String email, String phone) {
    }
}