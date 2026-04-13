# Frontend Improvement Plan

> อ้างอิง: Notion SCB Knowledge + โค้ด FE ปัจจุบัน (Next.js 15 / React 19 / Ant Design 5 / Tailwind CSS 4)
>
> หมายเหตุ: โปรเจกต์นี้ออกแบบโดยมี ref จาก Notion เป็นหลัก แต่มีความยืดหยุ่น เช่น ชื่ออาจไม่ตรง 100% หรืออาจมีฟีเจอร์เพิ่ม/ลดบ้าง

---

## 1. ปัญหาเชิงสถาปัตยกรรม (Critical)

### 1.1 Root Layout เป็น `'use client'`
- **ไฟล์:** `app/layout.tsx`
- **ปัญหา:** ทำให้ทั้งแอปเป็น Client-side render หมด สูญเสีย SSR/static optimization ของ Next.js 15
- **แก้ไข:** ย้าย `'use client'` ออกจาก root layout, wrap เฉพาะ providers ที่ต้องเป็น client component

### 1.2 Auth System ซ้อนกัน (Supabase + NextAuth)
- **ไฟล์:** `lib/auth.ts`, `lib/supabase/client.ts`, `api/auth/[...nextauth]/route.ts`
- **ปัญหา:** มีทั้ง NextAuth (ไม่ได้ใช้งานจริง) และ Supabase Auth ทำงานอยู่ — เป็น dead code + สับสน
- **แก้ไข:** ลบ NextAuth ออกทั้งหมด (ลบ `lib/auth.ts`, ลบ folder `api/auth/`)

### 1.3 Route ที่กำหนดไว้ใน Nav แต่ไม่มีหน้าจริง
- **ไฟล์:** `constants/Title.tsx`
- **ปัญหา:** Routes `/b-post/socials`, `/b-post/messages`, `/chat-app/social` มีใน navigation แต่ไม่มี page files — กดแล้ว 404
- **แก้ไข:** สร้างหน้าให้ครบ หรือเอาออกจาก TITLE constant

---

## 2. Component ที่ต้องปรับปรุง (High)

### 2.1 SideBar ใหญ่เกินไป (~280 บรรทัด)
- **ไฟล์:** `components/SideBar/index.tsx`
- **ปัญหา:** รวม menu rendering, theme toggle, collapse state, route matching, breadcrumb ไว้ที่เดียว
- **แก้ไข:** แยกเป็น `MenuRenderer`, `useSidebarRoute` hook, `useMenuNavigation` hook

### 2.2 MessagePage เป็น monolith (~510 บรรทัด)
- **ไฟล์:** `app/chat-app/message/page.tsx`
- **ปัญหา:** auth state + room list + message history + sending + infinite scroll + UI ทั้งหมดอยู่ไฟล์เดียว
- **แก้ไข:** แยก `useRoomSelection()`, `useMessageHistory()`, `ChatHeader`, `MessageList`, `MessageInput`

### 2.3 GuardLink ไม่มี logic จริง
- **ไฟล์:** `components/GuardLink/index.tsx`
- **ปัญหา:** ชื่อบอกว่า "Guard" แต่แค่ navigate ปกติ ไม่มี permission checking
- **แก้ไข:** implement permission check จริง หรือเปลี่ยนชื่อ/ลบ

---

## 3. State Management & Context (Medium)

### 3.1 Loading State ซ้ำซ้อน 3 ที่
- `LoadingContext` (global overlay)
- `NavigateGuardProvider` (route-level)
- Page-level `tableLoading`
- **แก้ไข:** รวมเป็น loading context เดียวที่มี hierarchy

### 3.2 ThemeContext ใช้ sessionStorage
- **ไฟล์:** `context/ThemeContext.tsx`
- **ปัญหา:** Theme preference หายเมื่อปิด tab
- **แก้ไข:** เปลี่ยนเป็น localStorage

### 3.3 ไม่มี Error Boundary
- **ปัญหา:** Error ที่ไม่ได้ handle จะทำให้แอปทั้งหมด crash
- **แก้ไข:** เพิ่ม Error Boundary ที่ layout level

### 3.4 NavigateGuardProvider ไม่มี cleanup
- **ไฟล์:** `context/NavigateGuardProvider.tsx`
- **ปัญหา:** Auth listener subscription อาจ memory leak
- **แก้ไข:** เพิ่ม `return () => { mounted = false; }` ใน useEffect

---

## 4. Type Safety (Medium)

### 4.1 API Response ไม่มี type ชัดเจน
- **ไฟล์:** `utils/api.tsx`, `interface/common/ApiDetail.ts`
- **ปัญหา:** ใช้ `Record<any, any>` ไม่มี type checking
- **แก้ไข:** สร้าง generic `ApiResponse<T>`, `ApiError` types

### 4.2 ChatMessage type ไม่ consistent
- **ปัญหา:** `id` เป็น optional แต่ใช้เป็น required ในหลายที่
- **แก้ไข:** แยก `ChatMessageInput` (ไม่มี id) กับ `ChatMessage` (มี id required)

---

## 5. Performance (Medium)

### 5.1 ไม่มี useMemo ใน filter operations
- **ไฟล์:** `app/dinner/supplier/page.tsx`
- **ปัญหา:** Filter re-run ทุก render แม้ data ไม่เปลี่ยน
- **แก้ไข:** wrap ด้วย `useMemo`

### 5.2 Image ไม่ optimize
- **ไฟล์:** `app/login/page.tsx`
- **ปัญหา:** ใช้ `fill` โดยไม่มี `sizes` — layout shift risk
- **แก้ไข:** เพิ่ม `sizes` prop

### 5.3 useSupabaseSession สร้าง client ทุก render
- **ไฟล์:** `hooks/useSupabaseSession.ts`
- **แก้ไข:** memoize browser client creation

---

## 6. Design System & Consistency (Low-Medium)

### 6.1 Ant Design Theme + Tailwind ผสมกันไม่ลงตัว
- **ปัญหา:** ConfigProvider ตั้ง theme แต่หลาย component override ด้วย inline Tailwind
- **แก้ไข:** ใช้ Ant Design token system เป็นหลัก, Tailwind เฉพาะ layout/spacing

### 6.2 Spacing ไม่ consistent
- **ปัญหา:** บางที่ใช้ `gap-6` บางที่ `gap-4`, padding ไม่เท่ากัน
- **แก้ไข:** กำหนด spacing scale ใน Tailwind config

### 6.3 ไม่มี Accessibility
- **ไฟล์:** `components/SubSideBar/index.tsx` และอื่นๆ
- **ปัญหา:** ไม่มี ARIA labels, ไม่มี keyboard navigation
- **แก้ไข:** เพิ่ม `role`, `aria-selected`, `aria-label`

---

## 7. Dependencies (Low)

### 7.1 Unused packages
- `@mui/material`, `@mui/icons-material` — ไม่ได้ใช้ (ใช้ Ant Design แทน)
- `class-variance-authority` — ไม่มี CVA ใน codebase
- **แก้ไข:** `npm uninstall @mui/material @mui/icons-material class-variance-authority`

---

## 8. ฟีเจอร์ที่ขาด

| ฟีเจอร์ | สถานะ | หมายเหตุ |
|---------|--------|----------|
| Blog page (B-Post) | placeholder | แสดงแค่ card, ไม่มี blog จริง |
| Real-time chat | ไม่มี | ควรใช้ Supabase Realtime |
| Optimistic updates (chat) | ไม่มี | Message ต้องรอ response ก่อนแสดง |
| Loading skeletons | ไม่มี | ใช้ spinner อยู่ |
| Input validation | ไม่มี | ไม่มี validation ก่อนส่ง API |
| Rate limiting (client) | ไม่มี | Message sending spam ได้ |
| Error tracking | ไม่มี | ใช้ console.error อย่างเดียว |
| Unit tests | ไม่มี | ไม่มี test files |

---

## สรุปลำดับความสำคัญ

1. **ลบ NextAuth** + แก้ root layout — ลดความสับสน + ได้ SSR กลับมา
2. **สร้างหน้าที่ขาด** หรือลบ route ที่ไม่มีหน้า — แก้ UX ที่พัง
3. **แยก component ใหญ่** (SideBar, MessagePage) — maintainability
4. **เพิ่ม Error Boundary + fix memory leaks** — stability
5. **Type safety + API types** — developer experience
6. **Design system consistency** — polish
7. **Performance optimizations** — useMemo, image sizes, code splitting
8. **ลบ unused dependencies** — bundle size
