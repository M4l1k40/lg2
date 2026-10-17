package com.legalflow.notification.integration;

import com.legalflow.notification.service.NotificationRecipientContact;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClientDirectoryClientTest {

    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FIRM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String URL = "http://client-service/internal/clients/" + CLIENT_ID + "/contact?lawFirmId=" + FIRM;

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://client-service");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ServiceAccountTokenProvider tokens = mock(ServiceAccountTokenProvider.class);
    private final ClientDirectoryClient client = new ClientDirectoryClient(builder.build(), tokens);

    @Test
    void readsTheContactWithTheServiceAccountToken() {
        when(tokens.accessToken()).thenReturn(Optional.of("tok"));
        server.expect(requestTo(URL))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{\"id\":\"" + CLIENT_ID + "\",\"firstName\":\"Client\",\"lastName\":\"A\","
                        + "\"email\":\"client@example.test\",\"phone\":\"555-0100\",\"address\":\"ignoré\"}",
                        MediaType.APPLICATION_JSON));

        Optional<NotificationRecipientContact> contact = client.findClientContact(CLIENT_ID, FIRM);

        assertEquals(new NotificationRecipientContact("Client A", "client@example.test", "555-0100"), contact.orElseThrow());
        server.verify();
    }

    @Test
    void unknownClientGivesNoContact() {
        when(tokens.accessToken()).thenReturn(Optional.of("tok"));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(client.findClientContact(CLIENT_ID, FIRM).isEmpty());
    }

    @Test
    void refusedTokenIsInvalidatedSoTheNextCallGetsANewOne() {
        when(tokens.accessToken()).thenReturn(Optional.of("tok"));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertTrue(client.findClientContact(CLIENT_ID, FIRM).isEmpty());

        verify(tokens).invalidate();
    }

    @Test
    void withoutTokenNoRequestIsMade() {
        when(tokens.accessToken()).thenReturn(Optional.empty());

        assertTrue(client.findClientContact(CLIENT_ID, FIRM).isEmpty());

        server.verify();
    }
}
