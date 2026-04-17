'use client';

import { AntdRegistry } from '@ant-design/nextjs-registry';

export default function StyledRegistry({ children }: { children: React.ReactNode }) {
  return <AntdRegistry>{children}</AntdRegistry>;
}
