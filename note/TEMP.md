หมวด,Component,local,local-vm / Docker,cloud / Azure,💡 Recommendation (ปรับสู่ Enterprise Standard)
Network,API Gateway,Nginx,Nginx,ACA ingress,แนะนำให้เปลี่ยนจาก Nginx เป็น Kong หรือ Azure API Management เพราะจัดการเรื่อง OIDC Token Validation และ Rate Limit สำหรับ Microservices ได้ดีกว่า
Network,Load Balancer,-,Docker port 8088:80,ACA ingress,(ดีอยู่แล้ว) ACA Ingress ตอบโจทย์การทำ Platform Routing ได้ดีมาก
Network,DNS / URL,localhost,localhost,ACA URL / Custom,(ดีอยู่แล้ว)
Compute,Runtime / Orch.,-,Docker Compose,Azure Container Apps,(ดีอยู่แล้ว) เป็นการแบ่ง Local/Cloud ที่ถูกต้องและลดค่าใช้จ่ายได้ดี
Data,Relational DB,Supabase Postgres,Supabase Postgres,Supabase Postgres,🔥 เปลี่ยนด่วน: ใช้ PostgreSQL (Docker) บน Local และ Azure Database for PostgreSQL (Flexible Server) บน Cloud เพื่อความอิสระของข้อมูล
Data,Object Storage,Supabase Storage,Supabase Storage,Supabase Storage,🔥 เปลี่ยนด่วน: ใช้ Azurite (Docker) บน Local และ Azure Blob Storage บน Cloud เพื่อใช้ Azure SDK ชุดเดียวกัน
Data,Vector DB,Supabase pgvector,Supabase pgvector,Supabase pgvector,เปิดใช้งาน Extension pgvector บนฐานข้อมูล PostgreSQL ตามข้อเสนอแนะด้านบนได้เลย
Security,Identity Provider,Supabase Auth,Supabase Auth,Supabase Auth,🔥 เปลี่ยนด่วน: ใช้ Keycloak เป็น Central Identity เพื่อรองรับสแตนดาร์ด OIDC และตัดปัญหาการซิงค์ข้อมูล User ข้ามค่าย
Security,OAuth2 Proxy,-,oauth2-proxy,oauth2-proxy,(ดีอยู่แล้ว) ทำงานร่วมกับ Keycloak ได้สมบูรณ์แบบมาก
Security,Secret Mgmt,.env,compose env,GH Secrets -> ACA,(ดีอยู่แล้ว) เพิ่มเติมคือบน Azure สามารถขยับไปใช้ Azure Key Vault ในอนาคตเพื่อความปลอดภัยสูงสุด
Observability,Metrics / Logs,Actuator / IDE,Actuator + Prometheus,Actuator + Prometheus,แนะนำให้ปรับ: บน Cloud ให้เลิก Self-host Prometheus แล้วใช้ Azure Monitor (Application Insights + Log Analytics) แทน จะลดภาระการดูแลระบบไปได้มหาศาล
Job,Job Scheduler,Spring scheduler,Spring scheduler,Spring scheduler,ระวังพัง: ถ้า ACA มีจังหวะ Scale-to-zero ตัว Spring จะตายและ Job จะไม่รัน! แนะนำให้รัน Task ผ่าน Azure Container Apps Jobs แทน (ให้ Cloud เป็นคนทริกเกอร์)
Communication,REST / WebSocket,direct / B-Post,gateway / B-Post,gateway / B-Post,(ดีอยู่แล้ว) การคุยผ่าน Gateway และแยก Service ถูกต้องตามหลักการครับ
AI/ML,LLM / Gen / Embed,Groq / Cloudflare / HF,Groq / Cloudflare / HF,Groq / Cloudflare / HF,(ดีอยู่แล้ว) การใช้ External API สำหรับโมเดล AI เป็นท่าที่คุ้มค่าที่สุดสำหรับโปรเจกต์สเกลนี้ครับ
CI/CD,Pipeline / Reg / Config,-,local / compose / env,GH Actions / GHCR / ACA,(ดีอยู่แล้ว) GitHub Actions + GHCR เป็นคอมโบที่เบาและมีประสิทธิภาพมาก