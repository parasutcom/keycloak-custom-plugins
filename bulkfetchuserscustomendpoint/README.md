# Bulk Fetch Users Custom Endpoint (Keycloak Plugin)

Keycloak için özel bir REST endpoint sağlayan plugin. Verilen kullanıcı ID listesi için her kullanıcının credential (kimlik bilgisi) kayıtlarını toplu (bulk) olarak döner. Özellikle parolaların / credential'ların en son ne zaman oluşturulduğunu/güncellendiğini takip etmek için kullanılır.

## Ne Yapar?

Plugin, Keycloak'a `password-tracker` adında bir `AdminRealmResourceProvider` ekler. Bu sayede endpoint **Admin API** altında kullanılabilir hale gelir:

- **Endpoint:** `POST /admin/realms/{realm}/password-tracker/last-updates`
- **Yetkilendirme (Authorization):** Geçerli bir admin access token gereklidir (`Authorization: Bearer <token>`). Çağıran kullanıcının kullanıcıları görüntüleme (`view-users`) yetkisi olmalıdır.
- **Content-Type:** `application/json`
- **İstek Gövdesi (Request Body):** Kullanıcı ID'lerinden oluşan bir JSON dizisi
  ```json
  ["user-id-1", "user-id-2", "user-id-3"]
  ```
- **Yanıt (Response):** Her kullanıcı ID'si için credential listesi
  ```json
  {
    "user-id-1": [
      {
        "id": "credential-uuid",
        "type": "password",
        "userLabel": null,
        "createdDate": 1700000000000
      }
    ],
    "user-id-2": []
  }
  ```

Her credential için şu alanlar döner: `id`, `type`, `userLabel`, `createdDate`.

> Not: İstek gövdesi boş veya null gönderilirse boş bir obje (`{}`) döner. Bulunamayan kullanıcı ID'leri yanıta dahil edilmez.

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

curl -X POST "https://<keycloak-host>/admin/realms/<realm>/password-tracker/last-updates" \
  -H "Authorization: Bearer $PARASUT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '["user-id-1", "user-id-2"]'
```
