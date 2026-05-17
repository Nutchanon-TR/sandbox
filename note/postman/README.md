# Sandbox Postman Collections

Import `sandbox-local.postman_environment.json` first, then import one or more controller collections.

Controller-split collections live under:

```text
note/postman/<service>/<service>_service/<ControllerName>/<ControllerName>.postman_collection.json
```

Examples:

```text
note/postman/users/users_service/UserController/UserController.postman_collection.json
note/postman/chatapp/chatapp_service/ChatController/ChatController.postman_collection.json
note/postman/dinner/dinner_service/SupplierOrderController/SupplierOrderController.postman_collection.json
note/postman/bpost/bpost_service/CommentController/CommentController.postman_collection.json
```

Each controller-split request has a short `description` explaining what the API does.

Default base URLs point at Next.js dev rewrites:

```text
http://localhost:3000/v1/api/...
```

For Docker gateway, change each `*BaseUrl` variable to `http://localhost/v1/api/...`.

For direct local Spring Boot services, use:

```text
usersBaseUrl=http://localhost:8080/v1/api/user
chatAppBaseUrl=http://localhost:8081/v1/api/chat-app
dinnerBaseUrl=http://localhost:8082/v1/api/dinner
bpostBaseUrl=http://localhost:8083/v1/api/b-post
```

Paste a real Supabase session access token into `accessToken`. The `supabaseUid` variable must match the JWT `sub` claim for `/user/sync`.

Common REST headers used by the frontend:

```text
Authorization: Bearer <Supabase access_token>
sourceSystem: FRONTEND
Accept: application/json
Content-Type: application/json
```

`Content-Type` is only set for JSON requests. Multipart upload requests let Postman generate the `multipart/form-data` boundary.

B-Post realtime is not a normal REST call. The SockJS/STOMP connection sends this STOMP CONNECT header:

```text
Authorization: Bearer <Supabase access_token>
```
