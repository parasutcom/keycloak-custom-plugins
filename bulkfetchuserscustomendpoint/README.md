# Bulk Fetch Users Custom Endpoint (Keycloak Plugin)

Keycloak için özel bir REST endpoint sağlayan plugin. Verilen kullanıcı ID listesi için kullanıcıların credential (kimlik bilgisi) tarihlerini ve required action'larını toplu (bulk) olarak döndürür; ayrıca toplu olarak oturum kapatıp `UPDATE_PASSWORD` zorlayabilir. Per-user senkron Keycloak çağrılarını chunk başına tek isteğe indirmek için kullanılır.

## Ne Yapar?

Plugin, Keycloak'a `password-tracker` adında bir `AdminRealmResourceProvider` ekler. Tüm endpoint'ler **Admin API** altında, `POST` ile çalışır; istek gövdesi her zaman kullanıcı ID'lerinden oluşan bir JSON dizisidir.

- **Base path:** `/admin/realms/{realm}/password-tracker`
- **Content-Type:** `application/json`
- **Yetkilendirme (Authorization):** Geçerli bir admin access token gereklidir (`Authorization: Bearer <token>`). Read endpoint'leri için `view-users`, write endpoint'i için `manage-users` yetkisi gerekir.
- **İstek Gövdesi (Request Body):** Tüm endpoint'lerde kullanıcı ID'lerinden oluşan bir JSON dizisi:
  ```json
  ["user-id-1", "user-id-2", "user-id-3"]
  ```

> Not: İstek gövdesi boş veya null gönderilirse boş bir obje (`{}`) döner.

### Endpoint A — `POST /password-tracker/last-updates`

Verilen kullanıcıların credential kayıtlarını toplu döner (yetki: `view-users`). `?includeRequiredActions=true` query parametresi verilirse cevaba `requiredActions` da eklenir.

- **Query parametresi:** `includeRequiredActions` (opsiyonel, varsayılan `false`)
- Backfill worker bayraksız çağırır (yalnızca `credentials`).
- User-group worker `?includeRequiredActions=true` ile çağırıp zaten `UPDATE_PASSWORD` beklentisi olan kullanıcıları eler.

`?includeRequiredActions=true` ile yanıt:

```json
{
  "user-id-1": {
    "credentials": [
      {
        "id": "credential-uuid",
        "type": "password",
        "userLabel": null,
        "createdDate": 1700000000000
      }
    ],
    "requiredActions": ["UPDATE_PASSWORD"]
  }
}
```

Bayraksız (varsayılan) yanıtta `requiredActions` alanı **hiç bulunmaz**:

```json
{
  "user-id-1": {
    "credentials": [
      { "id": "credential-uuid", "type": "password", "userLabel": null, "createdDate": 1700000000000 }
    ]
  }
}
```

- `credentials`: kullanıcının tüm credential kayıtları; her biri `id`, `type`, `userLabel`, `createdDate` döner. Parola tarihi için `type == "password"` olan kaydın `createdDate`'i kullanılır.
- `requiredActions`: yalnızca `includeRequiredActions=true` iken döner; kullanıcının `requiredActions` listesi (yoksa `[]`).
- Bulunamayan kullanıcı ID'leri yanıta dahil edilmez.

### Endpoint B — `POST /password-tracker/require-password-update`

Verilen her kullanıcı için server-side olarak **tüm oturumları kapatır** ve `UPDATE_PASSWORD` required action'ı ekler (yetki: `manage-users`). İdempotenttir.

```json
{
  "user-id-1": "updated",
  "user-id-2": "error"
}
```

- Her kullanıcı için: tüm session'lar invalidate edilir (backchannel logout) + `UPDATE_PASSWORD` eklenir (zaten varsa tekrar eklenmez).
- Bulunamayan veya işlenemeyen kullanıcılar için değer `"error"` olur.

## Proje Yapısı

```
bulkfetchuserscustomendpoint/
├── pom.xml
└── src/main/
    ├── java/com/parasut/
    │   ├── PasswordTrackerResource.java         # Endpoint mantığı (JAX-RS REST resource + yetki kontrolü)
    │   ├── PasswordTrackerProvider.java         # AdminRealmResourceProvider (resource'u döner)
    │   └── PasswordTrackerProviderFactory.java  # Provider factory (provider id: "password-tracker")
    └── resources/META-INF/services/
        └── org.keycloak.services.resources.admin.ext.AdminRealmResourceProviderFactory  # SPI kaydı
```

## Gereksinimler

- Keycloak 25.x (geliştirme `keycloak.version` = 25.0.2 ile yapılmıştır)
- Java 17
- Maven (yerel kurulu değilse aşağıdaki Docker komutu yeterli)

## JAR Dosyası Oluşturma

Yerelde Maven/Java kurmaya gerek kalmadan, proje dizininde (`bulkfetchuserscustomendpoint/`) aşağıdaki komutu çalıştır:

```bash
docker run --rm -v "$(pwd)":/app -w /app maven:3.9.6-eclipse-temurin-17 mvn clean package
```

Komut tamamlandığında JAR dosyası şurada oluşur:

```
target/bulkfetchuserscustomendpoint-1.0.0.jar
```

## Kurulum (Deploy)

1. Oluşan `target/bulkfetchuserscustomendpoint-1.0.0.jar` dosyasını Keycloak'ın `providers/` dizinine kopyala.
2. Keycloak'ı yeniden derleyip (build) başlat:
   ```bash
   /opt/keycloak/bin/kc.sh build
   /opt/keycloak/bin/kc.sh start
   ```
   (Docker imajı kullanıyorsan JAR'ı `/opt/keycloak/providers/` altına mount edip container'ı yeniden başlatman yeterli.)

## Örnek Kullanım

```bash
PARASUT_TOKEN=$(curl -s -X POST http://localhost:8080/realms/parasut/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=parasut-app" \
  -d "client_secret=very_very_secret_key" | jq -r .access_token)

# Endpoint A — yalnızca credential kayıtları (backfill)
curl -X POST "https://<keycloak-host>/admin/realms/<realm>/password-tracker/last-updates" \
  -H "Authorization: Bearer $PARASUT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '["user-id-1", "user-id-2"]'

# Endpoint A — credential kayıtları + required actions (user-group)
curl -X POST "https://<keycloak-host>/admin/realms/<realm>/password-tracker/last-updates?includeRequiredActions=true" \
  -H "Authorization: Bearer $PARASUT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '["user-id-1", "user-id-2"]'

# Endpoint B — oturumları kapat + UPDATE_PASSWORD zorla
curl -X POST "https://<keycloak-host>/admin/realms/<realm>/password-tracker/require-password-update" \
  -H "Authorization: Bearer $PARASUT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '["user-id-1", "user-id-2"]'
```
