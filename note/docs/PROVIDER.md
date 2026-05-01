# Frontend Provider Architecture - Current State

เอกสารนี้อัปเดตจากโค้ด frontend ปัจจุบัน ไม่ใช่ restructuring plan เก่า

---

## Provider Tree ปัจจุบัน

ไฟล์หลัก:

- `frontend/app/layout.tsx`
- `frontend/providers/AppProviders.tsx`
- `frontend/app/b-post/layout.tsx`

โครงสร้างจริงตอนนี้:

```tsx
// app/layout.tsx
<AppProviders>
  <Sidebar>
    <NavigationGuardProvider>
      {children}
    </NavigationGuardProvider>
  </Sidebar>
</AppProviders>
```

```tsx
// providers/AppProviders.tsx
<AuthProvider>
  <LayoutProvider>
    <ThemeProvider>
      <AntdRegistry>
        <ConfigProvider>
          <NotificationProvider>
            <LoadingBarProvider>
              {children}
            </LoadingBarProvider>
          </NotificationProvider>
        </ConfigProvider>
      </AntdRegistry>
    </ThemeProvider>
  </LayoutProvider>
</AuthProvider>
```

สำหรับ route group `/b-post`:

```tsx
// app/b-post/layout.tsx
<BPostRealtimeProvider>
  {children}
</BPostRealtimeProvider>
```

---

## Providers ปัจจุบัน

| Provider | ไฟล์ | หน้าที่ |
|---|---|---|
| `AuthProvider` | `providers/AuthProvider.tsx` | subscribe Supabase session, sync user เข้า `chat_app.users`, update Zustand session store |
| `LayoutProvider` | `providers/LayoutProvider.tsx` | เก็บ breadcrumb, current title, sub sidebar config |
| `ThemeProvider` | `providers/ThemeProvider.tsx` | dark/light theme, localStorage key `app-theme`, set CSS variables |
| `AntdRegistry` | `providers/AntdRegistry.tsx` | wrap `@ant-design/nextjs-registry` |
| `ConfigProvider` | `providers/ConfigProvider.tsx` | Ant Design tokens และ dark/default algorithm |
| `NotificationProvider` | `providers/NotificationProvider.tsx` | global Ant Design notification API |
| `LoadingBarProvider` | `providers/LoadingBarProvider.tsx` | full-screen loading overlay และ manual loading state |
| `NavigationGuardProvider` | `providers/NavigationGuardProvider.tsx` | auth guard, route allow-list, redirect, loading state |
| `BPostRealtimeProvider` | `providers/BPostRealtimeProvider.tsx` | STOMP/SockJS connection สำหรับ b-post realtime |

---

## Session Store

ไฟล์: `frontend/stores/sessionStore.ts`

ใช้ Zustand และเก็บ:

| field | ความหมาย |
|---|---|
| `session` | Supabase session |
| `status` | `loading`, `authenticated`, `unauthenticated` |
| `user` | Supabase user |
| `accessToken` | JWT |
| `internalUserId` | id จาก `chat_app.users` |

`AuthProvider` เป็นคน set `session` และ `internalUserId`

`axiosConfig.tsx` ดึง `accessToken` จาก store แล้วใส่ header:

```text
Authorization: Bearer <token>
sourceSystem: FRONTEND
```

ถ้า response เป็น `401` จะ clear store แล้ว redirect ไป `/login`

---

## Auth Flow ใน Frontend

1. `AuthProvider` สร้าง Supabase browser client
2. เรียก `supabase.auth.getSession()`
3. set session ลง Zustand
4. ถ้ามี user จะเรียก `USER_SYNC`
5. `/sync` คืน `userId`
6. set `internalUserId`
7. subscribe `onAuthStateChange` เพื่อ update session ต่อเนื่อง

ไฟล์ที่เกี่ยวข้อง:

- `frontend/lib/supabase/client.ts`
- `frontend/lib/supabase/server.ts`
- `frontend/app/auth/callback/route.ts`
- `frontend/app/login/page.tsx`
- `frontend/middleware.ts`

---

## Navigation Guard ปัจจุบัน

`NavigationGuardProvider` ยังทำหน้าที่มากกว่า unsaved-change guard:

- อ่าน `status` จาก `sessionStore`
- ใช้ `TITLE` เพื่อ generate valid paths
- ถ้าไม่ authenticated จะแจ้งเตือนและ redirect `/login`
- ถ้า path ไม่อยู่ใน allow-list จะ redirect `/login`
- เปิด/ปิด loading overlay ผ่าน `LoadingBarProvider`

Protected route ฝั่ง server ยังมีใน `frontend/middleware.ts` ด้วย:

- ไม่ login และ path ไม่ใช่ `/`, `/login`, `/auth/*` -> redirect `/login`
- login แล้วเข้า `/login` -> redirect `/`

---

## Theme และ Ant Design

`ThemeProvider`:

- default theme = `light`
- อ่าน/เขียน `localStorage` key `app-theme`
- ใส่ class `dark` ที่ `document.documentElement`
- set CSS variables เช่น `--background`, `--surface`, `--accent`

`ConfigProvider`:

- ใช้ `antdTheme.defaultAlgorithm` หรือ `antdTheme.darkAlgorithm`
- config token สี, font, border radius
- borderRadius ปัจจุบันตั้งไว้ที่ `20`

---

## B-Post Realtime Provider

ไฟล์: `frontend/providers/BPostRealtimeProvider.tsx`

ใช้:

- `@stomp/stompjs`
- `sockjs-client`
- Zustand store `bpostStore`

WebSocket path:

```text
${NEXT_PUBLIC_API_URL || ''}/v1/api/b-post/ws
```

CONNECT headers:

```text
Authorization: Bearer <accessToken>
```

Subscriptions:

| Destination | Action |
|---|---|
| `/user/queue/messages` | append message และ update conversation last |
| `/user/queue/notifications` | push notification |
| `/topic/presence` | update online state |

Provider จะ connect เฉพาะเมื่อมีทั้ง `accessToken` และ `internalUserId`

---

## Packages ที่เกี่ยวข้อง

จาก `frontend/package.json`:

| Package | ใช้สำหรับ |
|---|---|
| `@supabase/ssr`, `@supabase/supabase-js` | auth/session |
| `zustand` | session และ b-post state |
| `antd`, `@ant-design/icons`, `@ant-design/nextjs-registry` | UI |
| `axios` | API client |
| `@stomp/stompjs`, `sockjs-client` | b-post realtime |
| `lucide-react` | icons |
| `next` 15, `react` 19 | framework/runtime |
| `tailwindcss` 4 | styling |

---

## Things Not Implemented Yet

| รายการ | สถานะปัจจุบัน |
|---|---|
| `ModalProvider` | ยังไม่มี |
| `PermissionProvider` | ยังไม่มี |
| `SubPageProvider` | ยังไม่มี, `LayoutProvider` ยังถือ page-level state อยู่ |
| Navigation guard แบบ unsaved changes only | ยังไม่ใช่, provider ปัจจุบันเป็น auth/route guard |
| Top progress bar แบบ NProgress | ยังไม่มี, มี full-screen overlay |
| ChatApp realtime provider | ยังไม่มี, realtime มีเฉพาะ b-post |
