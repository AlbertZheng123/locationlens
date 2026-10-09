# LocationLens
## Command to start and run Docker

```
docker run -p 127.0.0.1:8084:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -v kcdata:/opt/keycloak/data \
  --name keycloak \
  quay.io/keycloak/keycloak:26.7.4 start-dev
```

1. Go to localhost:8084 in your browser and login with admin/admin.
1. Click on Manage Realms -- Create Realm.
1. Type realm name as `locationlens`.
1. click create
1. Ensure the current realm is locationlens
1. Go to Users -- create a new user. I am using linus, linus@example.com,
Linus Sebastian
1. Click on the Credentials tab -- Set Password, and set a password. Disable
temporary. I used 11111 as a very secure and totally not easy to guess password.
1. Now select Clients, Create client. Name it locationlens, type OIDC (which is
OAuth2)
1. Enable Client authentication, click on next
1. Valid redirect uri: http://localhost:8080/login/oauth2/code/common-idp
1. The client ID is locationlens, client secret is found under credentials tab.

create .env file:
```
IDP_CLIENT_ID=<clientid>
IDP_CLIENT_SECRET=<clientsecret>
IDP_URI=http://localhost:8084/realms/locationlens
```

run:
```
set -a
. .env
set +a
```

now run the app

```
./mvnw spring:run
```

## Reviews frontend

The React `ReviewView`, API contract, and frontend test/build instructions are
in [frontend/README.md](frontend/README.md).
