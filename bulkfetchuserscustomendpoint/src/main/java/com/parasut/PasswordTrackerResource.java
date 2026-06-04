package com.parasut;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PasswordTrackerResource {

    private final KeycloakSession session;
    private final AdminPermissionEvaluator auth;

    public PasswordTrackerResource(KeycloakSession session, AdminPermissionEvaluator auth) {
        this.session = session;
        this.auth = auth;
    }

    @POST
    @Path("/last-updates")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPasswordLastUpdates(List<String> userIds) {
        // Admin API yetki kontrolü: çağıran kullanıcının kullanıcıları görüntüleme yetkisi olmalı
        auth.users().requireView();

        RealmModel realm = session.getContext().getRealm();
        Map<String, List<Map<String, Object>>> responseMap = new HashMap<>();

        if (userIds == null || userIds.isEmpty()) {
            return Response.ok(responseMap).build();
        }

        for (String userId : userIds) {
            UserModel user = session.users().getUserById(realm, userId);

            if (user != null) {
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

                responseMap.put(userId, userCredentialsList);
            }
        }

        return Response.ok(responseMap).build();
    }
}
