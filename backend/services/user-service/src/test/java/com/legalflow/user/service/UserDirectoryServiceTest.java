package com.legalflow.user.service;

import com.legalflow.user.dto.CreateUserRequest;
import com.legalflow.user.dto.CreatedUserResponse;
import com.legalflow.user.dto.LawyerSummary;
import com.legalflow.user.dto.UserResponse;
import com.legalflow.user.integration.ClientServiceOwnershipVerifier;
import com.legalflow.user.keycloak.KeycloakAdminClient;
import com.legalflow.user.keycloak.KeycloakUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDirectoryServiceTest {

    private static final UUID FIRM_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRM_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CLIENT_RECORD_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final String NEW_USER_ID = "99999999-9999-9999-9999-999999999999";
    private static final String LAWYER_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String OTHER_FIRM_USER_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";

    private KeycloakAdminClient keycloak;
    private ClientServiceOwnershipVerifier verifier;
    private UserDirectoryService service;

    @BeforeEach
    void setUp() {
        keycloak = mock(KeycloakAdminClient.class);
        verifier = mock(ClientServiceOwnershipVerifier.class);
        service = new UserDirectoryService(keycloak, verifier);
        authenticateAsAdmin();
        when(keycloak.createUser(anyString(), anyString(), anyString(), anyString(), any(), anyString())).thenReturn(NEW_USER_ID);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUserForcesTheLawFirmFromTheTokenAndAssignsTheRole() {
        CreatedUserResponse created = service.createUser(request("LAWYER", null, null));

        verify(keycloak).createUser(eq("jdupont"), eq("jdupont@cabinet.test"), eq("Jean"), eq("Dupont"),
                argThat((Map<String, List<String>> attributes) ->
                        List.of(FIRM_A.toString()).equals(attributes.get("lawFirmId")) && !attributes.containsKey("clientId")),
                anyString());
        verify(keycloak).addRealmRole(NEW_USER_ID, "LAWYER");
        assertEquals("LAWYER", created.user().role());
        assertNotNull(created.temporaryPassword());
        assertEquals(14, created.temporaryPassword().length());
    }

    @Test
    void aPasswordChosenByTheAdminIsNotEchoedBack() {
        CreatedUserResponse created = service.createUser(request("SECRETARY", null, "ChosenPassword42"));

        assertNull(created.temporaryPassword());
        verify(keycloak).createUser(anyString(), anyString(), anyString(), anyString(), any(), eq("ChosenPassword42"));
    }

    @Test
    void clientAccountsMustBeLinkedToAnExistingClientRecord() {
        ResponseStatusException missingClientId = assertThrows(ResponseStatusException.class,
                () -> service.createUser(request("CLIENT", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, missingClientId.getStatusCode());
        verify(keycloak, never()).createUser(anyString(), anyString(), anyString(), anyString(), any(), anyString());

        service.createUser(request("CLIENT", CLIENT_RECORD_ID, null));

        verify(verifier).verifyClientBelongsToCurrentTenant(CLIENT_RECORD_ID);
        verify(keycloak).createUser(anyString(), anyString(), anyString(), anyString(),
                argThat((Map<String, List<String>> attributes) ->
                        List.of(CLIENT_RECORD_ID.toString()).equals(attributes.get("clientId"))),
                anyString());
    }

    @Test
    void clientIdIsRejectedForStaffRoles() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createUser(request("LAWYER", CLIENT_RECORD_ID, null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void aUserWhoseRoleCannotBeAssignedIsRolledBack() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "role failure"))
                .when(keycloak).addRealmRole(NEW_USER_ID, "LAWYER");

        assertThrows(ResponseStatusException.class, () -> service.createUser(request("LAWYER", null, null)));

        verify(keycloak).deleteUser(NEW_USER_ID);
    }

    @Test
    void cannotManageAUserOfAnotherLawFirm() {
        when(keycloak.findUser(OTHER_FIRM_USER_ID)).thenReturn(Optional.of(user(OTHER_FIRM_USER_ID, FIRM_B, true)));

        ResponseStatusException disable = assertThrows(ResponseStatusException.class,
                () -> service.setEnabled(OTHER_FIRM_USER_ID, false));
        ResponseStatusException reset = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword(OTHER_FIRM_USER_ID));

        assertEquals(HttpStatus.NOT_FOUND, disable.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, reset.getStatusCode());
        verify(keycloak, never()).setEnabled(anyString(), anyBoolean());
        verify(keycloak, never()).resetPassword(anyString(), anyString(), anyBoolean());
    }

    @Test
    void anIdThatIsNotAUuidIsTreatedAsNotFound() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.setEnabled("../../admin", false));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(keycloak, never()).findUser(anyString());
    }

    @Test
    void anAdminCannotDisableTheirOwnAccount() {
        when(keycloak.findUser(ADMIN_ID.toString())).thenReturn(Optional.of(user(ADMIN_ID.toString(), FIRM_A, true)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.setEnabled(ADMIN_ID.toString(), false));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(keycloak, never()).setEnabled(anyString(), anyBoolean());
    }

    @Test
    void anAdminCanDisableAnotherUserOfTheirFirm() {
        KeycloakUser lawyer = user(LAWYER_ID, FIRM_A, true);
        when(keycloak.findUser(LAWYER_ID)).thenReturn(Optional.of(lawyer));
        when(keycloak.findUserIdsByRole("LAWYER")).thenReturn(Set.of(LAWYER_ID));

        UserResponse response = service.setEnabled(LAWYER_ID, false);

        verify(keycloak).setEnabled(LAWYER_ID, false);
        assertEquals("LAWYER", response.role());
    }

    @Test
    void resetPasswordGeneratesATemporaryPassword() {
        when(keycloak.findUser(LAWYER_ID)).thenReturn(Optional.of(user(LAWYER_ID, FIRM_A, true)));

        String password = service.resetPassword(LAWYER_ID).temporaryPassword();

        assertEquals(14, password.length());
        verify(keycloak).resetPassword(LAWYER_ID, password, true);
    }

    @Test
    void lawyerListKeepsOnlyEnabledLawyersOfTheFirm() {
        String disabledLawyerId = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";
        String secretaryId = "ffffffff-ffff-ffff-ffff-ffffffffffff";
        when(keycloak.findUsersByLawFirm(FIRM_A)).thenReturn(List.of(
                user(LAWYER_ID, FIRM_A, true), user(disabledLawyerId, FIRM_A, false), user(secretaryId, FIRM_A, true)));
        when(keycloak.findUserIdsByRole("LAWYER")).thenReturn(Set.of(LAWYER_ID, disabledLawyerId, OTHER_FIRM_USER_ID));

        List<LawyerSummary> lawyers = service.listLawyers();

        assertEquals(1, lawyers.size());
        assertEquals(LAWYER_ID, lawyers.get(0).id());
    }

    @Test
    void listedUsersGetTheirHighestRole() {
        when(keycloak.findUsersByLawFirm(FIRM_A)).thenReturn(List.of(user(LAWYER_ID, FIRM_A, true)));
        when(keycloak.findUserIdsByRole("ADMIN")).thenReturn(Set.of(LAWYER_ID));
        when(keycloak.findUserIdsByRole("LAWYER")).thenReturn(Set.of(LAWYER_ID));

        List<UserResponse> users = service.listUsers();

        assertEquals(1, users.size());
        assertEquals("ADMIN", users.get(0).role());
    }

    private CreateUserRequest request(String role, UUID clientId, String password) {
        return new CreateUserRequest("JDupont", "jdupont@cabinet.test", "Jean", "Dupont", role, clientId, password);
    }

    private KeycloakUser user(String id, UUID lawFirmId, boolean enabled) {
        return new KeycloakUser(id, "user-" + id.substring(0, 4), "u@cabinet.test", "Prénom", "Nom", enabled,
                Map.of("lawFirmId", List.of(lawFirmId.toString())));
    }

    private void authenticateAsAdmin() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("lawFirmId", FIRM_A.toString())
                .subject(ADMIN_ID.toString())
                .build();
        List<GrantedAuthority> authorities = Arrays.<GrantedAuthority>asList(new SimpleGrantedAuthority("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
