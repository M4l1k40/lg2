package com.legalflow.user.service;

import com.legalflow.user.dto.ClientInvitationResponse;
import com.legalflow.user.integration.ClientRecordReader;
import com.legalflow.user.integration.ClientRecordReader.ClientRecord;
import com.legalflow.user.keycloak.KeycloakAdminClient;
import com.legalflow.user.keycloak.KeycloakUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientInvitationServiceTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRM_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CLIENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID STAFF_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String NEW_USER_ID = "99999999-9999-9999-9999-999999999999";
    private static final String EXISTING_USER_ID = "88888888-8888-8888-8888-888888888888";

    private KeycloakAdminClient keycloak;
    private ClientRecordReader reader;
    private ClientInvitationService service;

    @BeforeEach
    void setUp() {
        keycloak = mock(KeycloakAdminClient.class);
        reader = mock(ClientRecordReader.class);
        service = new ClientInvitationService(keycloak, reader, "http://localhost:5173", "legalflow-frontend");
        authenticate("ROLE_SECRETARY");
        when(reader.read(CLIENT_ID)).thenReturn(new ClientRecord("Client", "A", "Client.A@example.test"));
        when(keycloak.findUserByClientId(CLIENT_ID)).thenReturn(Optional.empty());
        when(keycloak.findUserByEmail(anyString())).thenReturn(Optional.empty());
        when(keycloak.createInvitedUser(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(NEW_USER_ID);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsAPasswordlessAccountLinkedToTheClientRecordAndSendsTheLink() {
        ClientInvitationResponse response = service.invite(CLIENT_ID);

        verify(keycloak).createInvitedUser(eq("client.a@example.test"), eq("Client.A@example.test"), eq("Client"),
                eq("A"), argThat(attributes -> FIRM_A.toString().equals(attributes.get("lawFirmId").get(0))
                        && CLIENT_ID.toString().equals(attributes.get("clientId").get(0))));
        verify(keycloak).addRealmRole(NEW_USER_ID, "CLIENT");
        verify(keycloak).sendActionsEmail(NEW_USER_ID, List.of("UPDATE_PASSWORD"), 259200, "legalflow-frontend",
                "http://localhost:5173/");
        assertEquals("INVITED", response.status());
        assertEquals("Client.A@example.test", response.email());
    }

    @Test
    void anExistingAccountIsNotRecreatedOnlyReinvited() {
        when(keycloak.findUserByClientId(CLIENT_ID)).thenReturn(Optional.of(existingUser(FIRM_A, true)));

        ClientInvitationResponse response = service.invite(CLIENT_ID);

        verify(keycloak, never()).createInvitedUser(anyString(), anyString(), anyString(), anyString(), any());
        verify(keycloak).sendActionsEmail(eq(EXISTING_USER_ID), anyList(), anyInt(), anyString(), anyString());
        assertEquals("REINVITED", response.status());
    }

    @Test
    void anInvitationDoesNotReEnableADisabledAccount() {
        when(keycloak.findUserByClientId(CLIENT_ID)).thenReturn(Optional.of(existingUser(FIRM_A, false)));

        assertStatus(HttpStatus.CONFLICT);

        verify(keycloak, never()).sendActionsEmail(anyString(), anyList(), anyInt(), anyString(), anyString());
    }

    @Test
    void anAccountOfAnotherLawFirmIsRefused() {
        when(keycloak.findUserByClientId(CLIENT_ID)).thenReturn(Optional.of(existingUser(FIRM_B, true)));

        assertStatus(HttpStatus.CONFLICT);

        verify(keycloak, never()).sendActionsEmail(anyString(), anyList(), anyInt(), anyString(), anyString());
    }

    @Test
    void aClientRecordWithoutEmailCannotBeInvited() {
        when(reader.read(CLIENT_ID)).thenReturn(new ClientRecord("Client", "A", "  "));

        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(keycloak, never()).createInvitedUser(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void anEmailAlreadyUsedByAnotherAccountIsRefusedWithoutCreatingAnything() {
        when(keycloak.findUserByEmail("Client.A@example.test")).thenReturn(Optional.of(existingUser(FIRM_A, true)));

        assertStatus(HttpStatus.CONFLICT);

        verify(keycloak, never()).createInvitedUser(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void anUnknownClientIsRefusedBeforeTouchingKeycloak() {
        when(reader.read(CLIENT_ID)).thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "not found"));

        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(keycloak, never()).findUserByClientId(any());
        verify(keycloak, never()).createInvitedUser(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void anEmailFailureRemovesTheAccountThatWasJustCreated() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "smtp")).when(keycloak)
                .sendActionsEmail(anyString(), anyList(), anyInt(), anyString(), anyString());

        assertStatus(HttpStatus.BAD_GATEWAY);

        verify(keycloak).deleteUser(NEW_USER_ID);
    }

    @Test
    void anEmailFailureNeverDeletesAnExistingAccount() {
        when(keycloak.findUserByClientId(CLIENT_ID)).thenReturn(Optional.of(existingUser(FIRM_A, true)));
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "smtp")).when(keycloak)
                .sendActionsEmail(anyString(), anyList(), anyInt(), anyString(), anyString());

        assertStatus(HttpStatus.BAD_GATEWAY);

        verify(keycloak, never()).deleteUser(anyString());
    }

    @Test
    void aFailedRoleAssignmentRemovesTheAccount() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "role")).when(keycloak)
                .addRealmRole(NEW_USER_ID, "CLIENT");

        assertStatus(HttpStatus.BAD_GATEWAY);

        verify(keycloak).deleteUser(NEW_USER_ID);
        verify(keycloak, never()).sendActionsEmail(anyString(), anyList(), anyInt(), anyString(), anyString());
    }

    private void assertStatus(HttpStatus expected) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.invite(CLIENT_ID));
        assertEquals(expected.value(), exception.getStatusCode().value());
    }

    private KeycloakUser existingUser(UUID lawFirmId, boolean enabled) {
        return new KeycloakUser(EXISTING_USER_ID, "client.a@example.test", "client.a@example.test", "Client", "A", enabled,
                Map.of("lawFirmId", List.of(lawFirmId.toString()), "clientId", List.of(CLIENT_ID.toString())));
    }

    private void authenticate(String authority) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .claim("lawFirmId", FIRM_A.toString())
                .subject(STAFF_ID.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority))));
    }
}
