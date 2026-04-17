# 🏗️ Provider Architecture — Restructuring Plan

> อ้างอิง: Reference Provider Layer (SCB Pattern) → Sandbox Frontend (Next.js 15 / React 19 / Ant Design 5 / Tailwind CSS 4)
>
> หมายเหตุ: ไม่ต้อง follow ref 100% — ปรับให้เข้ากับ stack ของเรา (Supabase Auth, Next.js middleware, Zustand)

---

## สถานะปัจจุบัน vs. เป้าหมาย (Gap Analysis)

| Layer | Reference Provider | สถานะปัจจุบัน | Gap |
|-------|-------------------|--------------|-----|
| **1. Core** | SessionProvider / SessionWatcher | `useSupabaseSession` hook + `NavigateGuardProvider` (รวม auth + session) | ❌ ยังไม่แยก Session ออกจาก Guard — ทำหน้าที่ซ้อนกัน |
| **1. Core** | AuthProvider | `middleware.ts` (server-side) + `NavigateGuardProvider` (client-side) + dead `lib/auth.ts` (NextAuth) | ⚠️ มี dead code NextAuth + auth logic กระจาย 3 ที่ |
| **1. Core** | LayoutProvider | `LayoutContext.tsx` (breadcrumb, currentTitle, subSideBarConfig) | ✅ มีแล้ว — แต่ยังผสม UI state กับ page-level data |
| **2. UI** | ConfigProvider | `ThemeContext.tsx` (theme + Ant ConfigProvider รวมกัน) | ⚠️ ทำงานได้ แต่ควรแยก Config (token/font) ออกจาก Theme toggle |
| **2. UI** | AntdRegistry | ❌ ไม่มี | ❌ อาจเกิด FOUC (Flash of Unstyled Content) |
| **2. UI** | StyleSheetManager | ไม่จำเป็น (เราไม่ใช้ styled-components) | ✅ ข้ามได้ |
| **3. UI Tools** | NotificationProvider | `NotificationContext.tsx` | ✅ มีแล้ว — ใช้งานได้ดี |
| **3. UI Tools** | ModalProvider | ❌ ไม่มี | ⚠️ แต่ละหน้าจัดการ modal เอง — ยังไม่มีระบบกลาง |
| **3. UI Tools** | LoadingBarProvider | `LoadingContext.tsx` (full-screen overlay) | ⚠️ มีแล้ว แต่เป็น overlay ไม่ใช่ progress bar — ควรเสริม |
| **4. Logic** | NavigationGuardProvider | `NavigateGuardProvider.tsx` | ⚠️ มีแล้ว แต่ทำเกินหน้าที่ (รวม auth + route validation + loading) |
| **4. Logic** | PermissionProvider | ❌ ไม่มี | ❌ ยังไม่มี role/permission system |
| **4. Logic** | SubPageProvider | ❌ ไม่มี (ใช้ `LayoutContext` แทน) | ⚠️ LayoutContext ทำหน้าที่นี้อยู่บางส่วน |

---

## แผนการปรับโครงสร้าง (Layer by Layer)

### Layer 1: Core Infrastructure — รากฐานระบบ

#### 1.1 `SessionProvider` (Zustand Store) — **สร้างใหม่**

**ไฟล์:** `stores/sessionStore.ts`

**หน้าที่:** เป็น single source of truth สำหรับ session data ทั้งแอป

```
ทำไมต้อง Zustand?
- ปัจจุบัน session ถูกสร้างใหม่ทุกครั้งใน useSupabaseSession + NavigateGuardProvider + axiosConfig
- Zustand ให้ global state โดยไม่ต้อง Provider wrapper → ลด nesting
- React 19 + Zustand ทำงานร่วมกันดีกว่า Context ในเชิง performance
```

**สิ่งที่เก็บ:**
- `session: Session | null` — Supabase session object
- `status: 'loading' | 'authenticated' | 'unauthenticated'`
- `user: User | null` — derived จาก session
- `accessToken: string | null` — สำหรับ API calls

**สิ่งที่ทำ:**
- Subscribe Supabase `onAuthStateChange` ครั้งเดียว
- Expose `signOut()` action

**Migration:**
- แทนที่ `useSupabaseSession` hook
- แทนที่ session logic ใน `NavigateGuardProvider`
- ให้ `axiosConfig` ดึง token จาก store โดยตรง

---

#### 1.2 `AuthProvider` — **Refactor จาก NavigateGuardProvider**

**ไฟล์:** `providers/AuthProvider.tsx`

**หน้าที่:** จัดการ lifecycle ของ authentication *เท่านั้น*

**สิ่งที่ทำ:**
- Initialize Supabase auth listener → sync เข้า `sessionStore`
- Handle `signOut()` (clear session + redirect)
- **ไม่ทำ:** route guard, permission check, loading overlay

**Migration:**
- ย้าย auth subscription logic จาก `NavigateGuardProvider` มาที่นี่
- **ลบ `lib/auth.ts`** (NextAuth dead code)
- **ลบ `app/api/auth/`** (NextAuth route handler)

> ⚠️ **หมายเหตุ:** `middleware.ts` ยังใช้สำหรับ server-side auth guard — ไม่ต้องลบ
> AuthProvider ทำงานคู่กับ middleware: middleware คุม server request, AuthProvider คุม client state

---

#### 1.3 `LayoutProvider` — **Refactor (ลด scope)**

**ไฟล์:** `providers/LayoutProvider.tsx` (ย้ายจาก `context/LayoutContext.tsx`)

**หน้าที่:** กำหนดโครงสร้างหน้าเว็บเบื้องต้นเท่านั้น

**สิ่งที่เก็บ (หลัง refactor):**
- `sidebarCollapsed: boolean`
- `layoutDirection: 'horizontal' | 'vertical'` (ถ้าต้องการในอนาคต)

**สิ่งที่ย้ายออก:**
- `breadCrumb` → ย้ายไป `SubPageProvider`
- `currentTitle` → ย้ายไป `SubPageProvider`
- `subSideBarConfig` → ย้ายไป `SubPageProvider`

---

### Layer 2: UI & Design System — หน้าตาและการแสดงผล

#### 2.1 `ConfigProvider` (Ant Design) — **แยกจาก ThemeContext**

**ไฟล์:** `providers/ConfigProvider.tsx`

**หน้าที่:** ตั้งค่า Ant Design theme token, font, global config

**สิ่งที่ทำ:**
- Wrap `antd ConfigProvider` กับ theme config ที่เหมาะสม
- รับ `mode` จาก `ThemeProvider` → เลือก `darkAlgorithm` / `defaultAlgorithm`
- กำหนด font family, border radius, color tokens

**Migration:**
- ย้าย `themeConfig` object ออกจาก `ThemeContext.tsx` มาที่นี่
- `ThemeContext` จะเหลือแค่ toggle state + CSS variables

---

#### 2.2 `ThemeProvider` — **Slim down**

**ไฟล์:** `providers/ThemeProvider.tsx` (refactor จาก `context/ThemeContext.tsx`)

**หน้าที่:** จัดการ dark/light mode toggle + CSS variables *เท่านั้น*

**สิ่งที่ทำ:**
- เก็บ theme preference ใน **localStorage** (แก้จาก sessionStorage)
- Set CSS variables + Tailwind `dark` class
- Expose `theme` + `toggleTheme`

**สิ่งที่ไม่ทำแล้ว:**
- ไม่ wrap Ant Design `ConfigProvider` (ย้ายไป `ConfigProvider`)

---

#### 2.3 `AntdRegistry` — **สร้างใหม่**

**ไฟล์:** `providers/AntdRegistry.tsx`

**หน้าที่:** ป้องกัน FOUC ของ Ant Design styles ใน Next.js

**วิธีทำ:**
- ใช้ `@ant-design/nextjs-registry` (official package)
- Wrap ไว้รอบ ConfigProvider

```tsx
// providers/AntdRegistry.tsx
import { AntdRegistry } from '@ant-design/nextjs-registry';
export default function StyledRegistry({ children }) {
  return <AntdRegistry>{children}</AntdRegistry>;
}
```

**สิ่งที่ต้องลง:** `npm install @ant-design/nextjs-registry`

---

#### 2.4 `StyleSheetManager` — **ข้ามไม่ทำ**

เราไม่ใช้ `styled-components` หรือ CSS-in-JS library ที่ต้องการ SSR injection  
Tailwind CSS 4 + Ant Design ทำงานได้เลยโดยไม่ต้องมี StyleSheetManager

---

### Layer 3: Global UI Tools — เครื่องมืออำนวยความสะดวก

#### 3.1 `NotificationProvider` — **คงไว้, ไม่ต้องแก้**

**ไฟล์:** `providers/NotificationProvider.tsx` (ย้ายจาก `context/NotificationContext.tsx`)

**สถานะ:** ✅ ใช้งานได้ดีแล้ว — แค่ย้ายไฟล์ตาม convention ใหม่

---

#### 3.2 `ModalProvider` — **สร้างใหม่ (Optional Phase 2)**

**ไฟล์:** `providers/ModalProvider.tsx`

**หน้าที่:** จัดการแสดงผล confirmation dialog / modal queue จากส่วนกลาง

**สิ่งที่ทำ:**
- ใช้ `antd Modal.useModal()` เหมือนที่ NotificationProvider ใช้ `notification.useNotification()`
- Expose `showConfirm()`, `showModal()` ผ่าน context

**ทำไมถึง optional:**
- ปัจจุบันแอปยังไม่มี modal ซับซ้อน — ทำได้ภายหลังเมื่อต้องการ

---

#### 3.3 `LoadingBarProvider` — **Refactor**

**ไฟล์:** `providers/LoadingBarProvider.tsx` (refactor จาก `context/LoadingContext.tsx`)

**สิ่งที่เปลี่ยน:**
- แยก **initial page load** (full-screen overlay) ออกจาก **navigation loading** (top progress bar)
- เสริม NProgress-style bar สำหรับ route change
- คง full-screen overlay สำหรับ initial load + manual trigger

**สิ่งที่ต้องลง (optional):** `npm install nprogress` (หรือเขียน CSS เอง)

---

### Layer 4: Logic & Security Guard — ความปลอดภัยและเนื้อหา

#### 4.1 `NavigationGuardProvider` — **Refactor (ลดหน้าที่ลงมาก)**

**ไฟล์:** `providers/NavigationGuardProvider.tsx`

**หน้าที่ (หลัง refactor):** เช็คเฉพาะ "unsaved changes" guard

**สิ่งที่ทำ:**
- Intercept `beforeunload` + Next.js route change
- ถ้า user มี dirty form → แสดง confirmation dialog
- Expose `setDirty(boolean)` ให้ form components ใช้

**สิ่งที่ย้ายออก:**
- Auth checking → ไปที่ `AuthProvider` + `middleware.ts`
- Route validation → ไปที่ `middleware.ts`
- Loading state → ไปที่ `LoadingBarProvider`

---

#### 4.2 `PermissionProvider` — **สร้างใหม่ (Phase 2)**

**ไฟล์:** `providers/PermissionProvider.tsx`

**หน้าที่:** ตรวจสอบ role/permission จาก session

**สิ่งที่ทำ:**
- Read user role จาก `sessionStore` (หรือ Supabase user metadata)
- Expose `hasPermission(permission: string)` + `hasRole(role: string)`
- Provide `<Can permission="...">` component สำหรับ conditional render

**ทำไมถึงเป็น Phase 2:**
- ปัจจุบัน backend ยังไม่มี role system ที่ชัดเจน
- ทำหลังจาก backend มี role/permission API พร้อม

---

#### 4.3 `SubPageProvider` — **สร้างใหม่**

**ไฟล์:** `providers/SubPageProvider.tsx`

**หน้าที่:** เก็บ page-level context ที่หน้านั้นๆ ต้องใช้ร่วมกัน

**สิ่งที่เก็บ:**
- `breadcrumb: BreadcrumbItemType[]`
- `pageTitle: string`
- `subSideBarConfig: SubSideBarConfig | null`

**ตำแหน่งใน tree:**
- ไม่ใช่ wrap ทั้งแอป — ใส่ที่ระดับ **layout ของแต่ละ route group**
- เช่น `app/(main)/layout.tsx` wrap ด้วย `SubPageProvider`

**Migration:**
- ย้าย breadCrumb, currentTitle, subSideBarConfig ออกจาก `LayoutContext`

---

## โครงสร้าง Provider Tree (เป้าหมาย)

```
app/layout.tsx (Server Component ✅)
│
└── providers/AppProviders.tsx ('use client')
    │
    ├── Layer 1: Core
    │   ├── AuthProvider          ← init Supabase listener → sessionStore
    │   └── LayoutProvider        ← sidebar collapse state
    │
    ├── Layer 2: UI
    │   ├── ThemeProvider         ← dark/light toggle + CSS vars
    │   │   └── AntdRegistry      ← SSR style injection
    │   │       └── ConfigProvider ← Ant Design token config
    │   
    ├── Layer 3: UI Tools
    │   ├── NotificationProvider  ← global notification API
    │   ├── ModalProvider         ← global modal API (Phase 2)
    │   └── LoadingBarProvider    ← loading overlay + progress bar
    │
    └── Layer 4: Logic
        └── NavigationGuardProvider ← unsaved changes guard
            │
            └── <Sidebar>
                  └── {children}
                        │
                        └── (ที่ระดับ route group layout)
                              └── SubPageProvider ← breadcrumb, page title
```

### ตัวอย่าง `AppProviders.tsx`

```tsx
// providers/AppProviders.tsx
'use client';

import { AuthProvider } from './AuthProvider';
import { LayoutProvider } from './LayoutProvider';
import { ThemeProvider } from './ThemeProvider';
import { ConfigProvider } from './ConfigProvider';
import { AntdRegistry } from './AntdRegistry';
import { NotificationProvider } from './NotificationProvider';
import { LoadingBarProvider } from './LoadingBarProvider';
import { NavigationGuardProvider } from './NavigationGuardProvider';

export function AppProviders({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <LayoutProvider>
        <ThemeProvider>
          <AntdRegistry>
            <ConfigProvider>
              <NotificationProvider>
                <LoadingBarProvider>
                  <NavigationGuardProvider>
                    {children}
                  </NavigationGuardProvider>
                </LoadingBarProvider>
              </NotificationProvider>
            </ConfigProvider>
          </AntdRegistry>
        </ThemeProvider>
      </LayoutProvider>
    </AuthProvider>
  );
}
```

### ตัวอย่าง `app/layout.tsx` (Server Component)

```tsx
// app/layout.tsx — ไม่มี 'use client' อีกต่อไป ✅
import './globals.css';
import { AppProviders } from '@/providers/AppProviders';
import Sidebar from '@/components/SideBar';

export const metadata = {
  title: 'Sandbox App',
  description: '...',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <AppProviders>
          <Sidebar>
            {children}
          </Sidebar>
        </AppProviders>
      </body>
    </html>
  );
}
```

---

## โครงสร้างไฟล์ (เป้าหมาย)

```
frontend/
├── stores/                          ← NEW: Zustand stores
│   └── sessionStore.ts              ← session state (Zustand)
│
├── providers/                       ← NEW: ย้าย + สร้างใหม่
│   ├── AppProviders.tsx             ← รวม provider tree
│   ├── AuthProvider.tsx             ← auth lifecycle
│   ├── LayoutProvider.tsx           ← layout structure state
│   ├── ThemeProvider.tsx            ← dark/light toggle
│   ├── ConfigProvider.tsx           ← Ant Design token config
│   ├── AntdRegistry.tsx             ← SSR style injection
│   ├── NotificationProvider.tsx     ← notification API
│   ├── ModalProvider.tsx            ← modal API (Phase 2)
│   ├── LoadingBarProvider.tsx       ← loading states
│   ├── NavigationGuardProvider.tsx  ← unsaved changes
│   ├── PermissionProvider.tsx       ← role/perm (Phase 2)
│   └── SubPageProvider.tsx          ← page-level context
│
├── context/                         ← จะลบหลัง migrate เสร็จ
│   ├── ThemeContext.tsx              → providers/ThemeProvider.tsx
│   ├── LoadingContext.tsx            → providers/LoadingBarProvider.tsx
│   ├── NavigateGuardProvider.tsx     → providers/NavigationGuardProvider.tsx
│   ├── NotificationContext.tsx       → providers/NotificationProvider.tsx
│   └── LayoutContext.tsx             → providers/LayoutProvider.tsx + SubPageProvider
│
├── hooks/
│   └── useSupabaseSession.ts        ← จะลบ (แทนที่ด้วย sessionStore)
│
├── lib/
│   ├── auth.ts                      ← จะลบ (NextAuth dead code)
│   └── supabase/
│       ├── client.ts                ← คงไว้
│       └── server.ts                ← คงไว้
```

---

## ลำดับการทำงาน (Phased Approach)

### Phase 1 — Foundation (ทำก่อน) 🎯

| # | งาน | ความเสี่ยง | หมายเหตุ |
|---|------|-----------|----------|
| 1.1 | ลบ NextAuth (`lib/auth.ts` + `app/api/auth/`) | ต่ำ | dead code — ลบได้เลย |
| 1.2 | สร้าง `stores/sessionStore.ts` (Zustand) | กลาง | ลง `zustand`, สร้าง store |
| 1.3 | สร้าง `providers/AuthProvider.tsx` | กลาง | ย้าย auth logic จาก NavigateGuardProvider |
| 1.4 | สร้าง `providers/AppProviders.tsx` | ต่ำ | รวม providers, ย้าย 'use client' ออกจาก layout |
| 1.5 | Refactor `app/layout.tsx` → Server Component | กลาง | ได้ SSR/metadata กลับมา |
| 1.6 | ลง `@ant-design/nextjs-registry` + สร้าง `AntdRegistry` | ต่ำ | ป้องกัน FOUC |

### Phase 2 — Restructure (ขยาย)

| # | งาน | ความเสี่ยง | หมายเหตุ |
|---|------|-----------|----------|
| 2.1 | แยก `ThemeProvider` / `ConfigProvider` | กลาง | แยก concerns |
| 2.2 | Refactor `NavigationGuardProvider` → unsaved changes only | กลาง | ลดหน้าที่ |
| 2.3 | สร้าง `SubPageProvider` + ย้าย breadcrumb ออกจาก Layout | กลาง | ใช้ที่ route group level |
| 2.4 | Refactor `LoadingBarProvider` (เพิ่ม progress bar) | ต่ำ | style change |
| 2.5 | เปลี่ยน theme storage จาก sessionStorage → localStorage | ต่ำ | one-line fix |

### Phase 3 — Enhancement (เสริม)

| # | งาน | ความเสี่ยง | หมายเหตุ |
|---|------|-----------|----------|
| 3.1 | สร้าง `ModalProvider` | ต่ำ | optional convenience |
| 3.2 | สร้าง `PermissionProvider` | กลาง | ต้องรอ backend role API |
| 3.3 | ลบ `context/` folder ทั้งหมด | ต่ำ | หลังย้ายเสร็จทุกอัน |
| 3.4 | ลบ `hooks/useSupabaseSession.ts` | ต่ำ | หลัง sessionStore พร้อม |

---

## สิ่งที่ต้องลงเพิ่ม

```bash
npm install zustand                    # Session store
npm install @ant-design/nextjs-registry # Ant Design SSR
npm uninstall next-auth                 # ลบ NextAuth (dead code)
npm uninstall @mui/material @mui/icons-material  # ลบ MUI (ไม่ได้ใช้)
npm uninstall class-variance-authority  # ลบ CVA (ไม่ได้ใช้)
```
