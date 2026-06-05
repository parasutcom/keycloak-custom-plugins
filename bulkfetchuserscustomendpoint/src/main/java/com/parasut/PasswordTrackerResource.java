package com.parasut;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PasswordTrackerResource {

    private final KeycloakSession session;
    private final AdminPermissionEvaluator auth;

    public PasswordTrackerResource(KeycloakSession session, AdminPermissionEvaluator auth) {
        this.session = session;
        this.auth = auth;
    }

    // Read endpoint — her kullanıcı için credential kayıtları döner.
    // includeRequiredActions=true verilirse cevaba requiredActions da eklenir.
    // Backfill worker bayraksız (yalnızca credentials) çağırır; user-group worker bayrakla çağırıp requiredActions'ı okur.
    @POST
    @Path("/last-updates")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPasswordLastUpdates(List<String> userIds,
                                           @QueryParam("includeRequiredActions") @DefaultValue("false") boolean includeRequiredActions) {
        // Admin API yetki kontrolü: çağıran kullanıcının kullanıcıları görüntüleme yetkisi olmalı
        auth.users().requireView();

        RealmModel realm = session.getContext().getRealm();
        Map<String, Map<String, Object>> responseMap = new HashMap<>();

        if (userIds == null || userIds.isEmpty()) {
            return Response.ok(responseMap).build();
        }

        for (String userId : userIds) {
            UserModel user = session.users().getUserById(realm, userId);

            // Bulunamayan kullanıcılar cevaba hiç eklenmez
            if (user == null) {
                continue;
            }

            List<Map<String, Object>> userCredentialsList = new ArrayList<>();
            user.credentialManager().getStoredCredentialsStream()
                    .forEach(cred -> {
                        Map<String, Object> credDetails = new HashMap<>();
                        credDetails.put("id", cred.getId());
                        credDetails.put("type", cred.getType());
                        credDetails.put("userLabel", cred.getUserLabel());
                        credDetails.put("createdDate", cred.getCreatedDate());

                        userCredentialsList.add(credDetails);
                    });

            Map<String, Object> userData = new HashMap<>();
            userData.put("credentials", userCredentialsList);

            // requiredActions yalnızca istendiğinde hesaplanır (1M backfill'de gereksiz iş yapılmaz)
            if (includeRequiredActions) {
                userData.put("requiredActions", user.getRequiredActionsStream().collect(Collectors.toList()));
            }

            responseMap.put(userId, userData);
        }

        return Response.ok(responseMap).build();
    }

    // Write endpoint — verilen kullanıcılar için tüm oturumları kapatır ve UPDATE_PASSWORD ekler (idempotent)
    @POST
    @Path("/require-password-update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response requirePasswordUpdate(List<String> userIds) {
        auth.users().requireManage();

        RealmModel realm = session.getContext().getRealm();
        Map<String, String> responseMap = new HashMap<>();

        if (userIds == null || userIds.isEmpty()) {
            return Response.ok(responseMap).build();
        }

        for (String userId : userIds) {
            try {
                UserModel user = session.users().getUserById(realm, userId);

                if (user == null) {
                    responseMap.put(userId, "error");
                    continue;
                }

                logoutAllSessions(realm, user);

                user.addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);

                responseMap.put(userId, "updated");
            } catch (Exception e) {
                responseMap.put(userId, "error");
            }
        }

        return Response.ok(responseMap).build();
    }

    private void logoutAllSessions(RealmModel realm, UserModel user) {
        List<UserSessionModel> userSessions = session.sessions()
                .getUserSessionsStream(realm, user)
                .collect(Collectors.toList());

        for (UserSessionModel userSession : userSessions) {
            AuthenticationManager.backchannelLogout(
                    session,
                    realm,
                    userSession,
                    session.getContext().getUri(),
                    session.getContext().getConnection(),
                    session.getContext().getRequestHeaders(),
                    true);
        }
    }
}
