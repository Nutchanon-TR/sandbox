'use client'

import { useChangeTitle } from "@/utils/breadCrumbUtil";
import { TITLE } from "@/constants/Title";
import { HomeOutlined } from "@ant-design/icons";

export default function Home() {
  useChangeTitle(TITLE.HOME);

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-4 flex items-center gap-2">
        <HomeOutlined /> Home
      </h1>
      <p className="text-text-secondary">
        Welcome to Sandbox! Click on the menu on the left to get started.
      </p>
    </div>
  );
}
