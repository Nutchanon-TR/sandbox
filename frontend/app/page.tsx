'use client'

import { useChangeTitle } from "@/utils/breadCrumbUtil";
import { TITLE } from "@/constants/Title";
import { HomeOutlined } from "@ant-design/icons";

export default function Home() {
  useChangeTitle(TITLE.HOME);

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-4 flex items-center gap-2">
        <HomeOutlined /> หน้าแรก
      </h1>
      <p className="text-gray-600 dark:text-gray-300">
        ยินดีต้อนรับสู่ระบบ Sandbox! ลองคลิกที่เมนูด้านซ้ายเพื่อเริ่มต้นใช้งาน
      </p>
    </div>
  );
}
