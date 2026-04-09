# Frontend Plan

## Sub Sidebar

- เพิ่มระบบ Sub Sidebar สำหรับหน้าที่ต้องการเมนูย่อยด้านข้าง (เช่น Chat App มี Friend, Group)
- กำหนดค่าใน `TITLE` (constants) ว่า route ไหนต้องแสดง Sub Sidebar
  - เช่น ถ้า url ตรงกับ `/chat-app/*` ให้แสดง Sub Sidebar ของ ChatApp
  - TITLE เป็นตัวควบคุมว่าเปิด/ปิด Sub Sidebar โดยไม่ต้องไปแก้ layout เอง
- สร้าง Layout สำหรับ Sub Sidebar แยกออกมา
  - อยู่ระหว่าง Main Sidebar กับ Content area
  - รองรับ collapse/expand ได้เหมือน Main Sidebar (ถ้าต้องการ)
- Component structure อยู่ใน `components/SubSideBar/...`
  - แต่ละหน้ามี folder ของตัวเอง เช่น `SubSideBar/ChatApp/`, `SubSideBar/Dinner/`
  - ภายใน folder เป็น component ย่อยที่ render เนื้อหาของ Sub Sidebar นั้นๆ
- การเรียกใช้ในแต่ละหน้าทำง่ายๆ ผ่าน config array
  - เช่น `sub = ["friend", "group"]` แล้ว Sub Sidebar จะ render เมนูตาม array นี้
  - แต่ละ item map ไปหา component ที่เตรียมไว้ใน `SubSideBar/` โดยอัตโนมัติ
  - หน้าไหนไม่ได้กำหนด sub ก็ไม่แสดง Sub Sidebar (content เต็มจอ)

จุดประสงค์คือ ทำรายการสำหรับ dynamic เช่น chatapp/message จะเก็บ user ที่เป็นเพื่อเรา dinner จะเก็บพวกรายการของ supplier แต่บะเจ้า้อะไรประมาณนี้