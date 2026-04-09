# Report: Sub Sidebar Implementation

> เอกสารสรุปแนวทาง Sub Sidebar โดยอัปเดตให้กรณี `Chat App` ใช้งานข้อมูลจาก backend endpoint `GET /room/list/{userId}` และนำชื่อห้องมาแสดงใน sidebar

---

## 1. ภาพรวมของงาน

เพิ่มระบบ **Sub Sidebar** สำหรับหน้าที่ต้องมีเมนูย่อยด้านซ้ายระหว่าง Main Sidebar กับ Content Area

แนวทางที่ใช้คือ:
- `SubSideBar` เป็น shell component สำหรับ render list
- แต่ละ `page.tsx` เป็นคน fetch data และส่ง `items` เข้า component
- สำหรับ `Chat App` ให้ดึงรายการห้องจาก backend endpoint `GET /room/list/{userId}`
- ใน Sub Sidebar ให้แสดง **ชื่อห้อง (`room.name`)** ที่ได้จาก backend
- เมื่อผู้ใช้กดเลือกห้อง ให้เปลี่ยน `roomId` ปัจจุบัน แล้วค่อยโหลด chat history ของห้องนั้น

---

## 2. รูปแบบการทำงานของ Chat App

### Flow ที่ต้องการ

```text
page.tsx mount
  -> resolve user จาก session
  -> ได้ userId
  -> call GET /room/list/{userId}
  -> เก็บ room list ลง state
  -> map room list เป็น Sub Sidebar items
  -> render ชื่อห้องใน sidebar
  -> user click ห้อง
  -> set selected roomId
  -> call API history ของ room นั้น
  -> update content area
```

### ตัวอย่างโค้ดที่ควรเป็น

```tsx
const [rooms, setRooms] = useState<RoomDto[]>([]);
const [roomId, setRoomId] = useState<number | null>(null);

useEffect(() => {
  if (!userId) return;

  fetchApi<RoomDto[]>(API_SANDBOX.CHAT_APP_ROOM_LIST, {}, { userId })
    .then((response) => {
      setRooms(response);
      setRoomId(response[0]?.id ?? null);
    });
}, [userId]);

return (
  <div className="flex h-full">
    <SubSideBar
      title="Rooms"
      items={rooms.map((room) => ({
        key: room.id,
        label: room.name,
      }))}
      selectedKey={roomId ?? undefined}
      onSelect={(key) => setRoomId(Number(key))}
    />

    <div className="flex-1">
      {/* content area */}
    </div>
  </div>
);
```

---

## 3. API ที่ Chat App ต้องใช้

### 3.1 Room list สำหรับ Sub Sidebar

| Method | Endpoint | ใช้งาน |
|---|---|---|
| `GET` | `/room/list/{userId}` | ดึงรายการห้องทั้งหมดของ user เพื่อนำไปแสดงใน Sub Sidebar |

### 3.2 รูปข้อมูลที่ใช้แสดงผล

backend ส่ง `RoomDto` กลับมา โดย field ที่ Sub Sidebar ใช้โดยตรงคือ:

```ts
interface RoomDto {
  id: number;
  name: string;
  isGroup: boolean;
  aiModel?: string | null;
  createdAt?: string;
}
```

ค่าที่ต้องใช้ใน sidebar:
- `id` ใช้เป็น `key`
- `name` ใช้เป็น label ที่แสดงใน list
- `isGroup` / `aiModel` ใช้ทำ subtitle หรือ icon เพิ่มเติมได้

---

## 4. Files ที่เกี่ยวข้อง

| Action | File | รายละเอียด |
|---|---|---|
| `[NEW]` | `frontend/interface/common/SubSideBarItem.ts` | type กลางของ item ที่ส่งเข้า `SubSideBar` |
| `[NEW]` | `frontend/components/SubSideBar/index.tsx` | shell component สำหรับ render menu list |
| `[UPDATE]` | `frontend/constants/api/ApiSandbox.ts` | เพิ่ม API constant สำหรับ `CHAT_APP_ROOM_LIST` |
| `[UPDATE]` | `frontend/app/chat-app/message/page.tsx` | fetch `/room/list/{userId}` แล้ว render ชื่อห้องใน Sub Sidebar |

---

## 5. ข้อสรุปสำคัญ

- `Chat App` ไม่ควรใช้ friend list สำหรับ Sub Sidebar ในงานนี้
- แหล่งข้อมูลของ sidebar ต้องมาจาก `GET /room/list/{userId}`
- สิ่งที่แสดงใน sidebar คือ **รายชื่อห้อง**
- `page.tsx` เป็นผู้ควบคุม state ของ room list, selected room และการ fetch history
- `SubSideBar` ทำหน้าที่เป็น UI shell เท่านั้น


comment: 
1.อ่าน  report REPORT_SubSidebar มาหน่อย ใน chatapp subSideBar ต้องการให้ไปอ่าน /room/list/{userId} จาก be และลิสชื่อออกมา
2.แก้ REPORT_SubSidebar.md ตามที่ว่ามาในข้อ1