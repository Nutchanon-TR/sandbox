'use client';

import { useBPostStore } from '@/stores/bpostStore';

export function PresenceDot({ userId, size = 10 }: { userId?: number; size?: number }) {
    const online = useBPostStore((s) => (userId == null ? false : s.online.has(userId)));
    return (
        <span
            aria-label={online ? 'online' : 'offline'}
            className={`inline-block rounded-full border-2 border-white ${online ? 'bg-green-500' : 'bg-slate-400'}`}
            style={{ width: size, height: size }}
        />
    );
}
