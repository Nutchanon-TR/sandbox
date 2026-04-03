# สรุปคีย์ของโปรเจกต์

## Frontend (Next.js)
| คีย์ | รายละเอียด |
|------|------------|
| NEXT_PUBLIC_SUPABASE_URL | URL ของโปรเจกต์ Supabase ที่ใช้ในฝั่งเบราว์เซอร์ |
| NEXT_PUBLIC_SUPABASE_ANON_KEY | คีย์แบบ anon สำหรับการทำงานของ Supabase บนฝั่ง client |

## Backend (Spring Boot)
| คีย์ | รายละเอียด |
|------|------------|
| SUPABASE_DB_USERNAME | ชื่อผู้ใช้ฐานข้อมูล PostgreSQL ของ Supabase (ใช้ใน JDBC) |
| SUPABASE_DB_PASSWORD | รหัสผ่านของฐานข้อมูล PostgreSQL ของ Supabase |
| SUPABASE_SERVICE_ROLE_KEY | คีย์ระดับ service‑role ที่ให้สิทธิ์เต็มสำหรับ Supabase Storage และ RPC |

## Gateway / OAuth2‑Proxy
| คีย์ | รายละเอียด |
|------|------------|
| SUPABASE_PROJECT_ID | ID ของโปรเจกต์ Supabase ที่ OAuth2‑Proxy ใช้เป็น OIDC issuer |
| OAUTH2_PROXY_CLIENT_ID | Client ID ของ OAuth2‑Proxy สำหรับ OIDC provider |
| OAUTH2_PROXY_CLIENT_SECRET | Client Secret ของ OAuth2‑Proxy |
| OAUTH2_PROXY_COOKIE_SECRET | คีย์สำหรับเข้ารหัสคุกกี้ของ OAuth2‑Proxy |

## GitHub Workflows (CI/CD)
| คีย์ | รายละเอียด |
|------|------------|
| AZURE_CONTAINER_REGISTRY | ชื่อ Azure Container Registry ที่ใช้สำหรับ push images |
| RESOURCE_GROUP | ชื่อ Resource Group ของ Azure ที่ใช้ Deploy Container Apps |
| AZURE_CREDENTIALS | JSON ของ Service Principal สำหรับ login Azure |
| ACR_USERNAME | Username ของ Azure Container Registry |
| ACR_PASSWORD | Password ของ Azure Container Registry |

---

*ไฟล์นี้สรุปคีย์สำคัญทั้งหมดที่พบในโค้ดและไฟล์กำหนดค่าของโปรเจกต์ Sandbox*
