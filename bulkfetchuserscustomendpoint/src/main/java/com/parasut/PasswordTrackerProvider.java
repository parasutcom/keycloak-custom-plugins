package com.parasut;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.resource.RealmResourceProvider;

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

public class PasswordTrackerProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public PasswordTrackerProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return this;
    }

    @POST
    @Path("/last-updates")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPasswordLastUpdates(List<String> userIds) {
        RealmModel realm = session.getContext().getRealm();
        Map<String, List<Map<String, Object>>> responseMap = new HashMap<>();

        if (userIds == null || userIds.isEmpty()) {
            return Response.ok(responseMap).build();
        }

        for (String userId : userIds) {
            UserModel user = session.users().getUserById(realm, userId);
            
            if (user != null) {
                System.out.println("[DEBUG-TRACKER] Kullanıcı BULUNDU, bulk credential toplanıyor: " + user.getUsername());
                List<Map<String, Object>> userCredentialsList = new ArrayList<>();
                
                // Keycloak 25'te import edilmiş hazır kullanıcılarda bile ham veriyi sekmeksizin okuyan en kararlı stream yapısı
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
            } else {
                System.out.println("[DEBUG-TRACKER] Kullanıcı bulunamadı! ID: " + userId);
            }
        }

        return Response.ok(responseMap).build();
    }

    @Override
    public void close() {
        // Temizlenecek bir kaynak yok
    }
}