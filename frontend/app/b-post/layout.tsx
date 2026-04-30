'use client';

import { BPostRealtimeProvider } from '@/providers/BPostRealtimeProvider';

export default function BPostLayout({ children }: { children: React.ReactNode }) {
    return <BPostRealtimeProvider>{children}</BPostRealtimeProvider>;
}
