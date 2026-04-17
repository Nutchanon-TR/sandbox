'use client';

import { useEffect } from 'react';
import { createSupabaseBrowser } from '@/lib/supabase/client';
import { useSessionStore } from '@/stores/sessionStore';

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const setSession = useSessionStore((s) => s.setSession);

  useEffect(() => {
    const supabase = createSupabaseBrowser();
    let mounted = true;

    async function init() {
      const { data: { session } } = await supabase.auth.getSession();
      if (mounted) setSession(session);
    }

    init();

    const { data: { subscription } } = supabase.auth.onAuthStateChange(
      (_event, session) => {
        if (mounted) setSession(session);
      }
    );

    return () => {
      mounted = false;
      subscription.unsubscribe();
    };
  }, [setSession]);

  return <>{children}</>;
}
