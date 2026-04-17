'use client';

import { useEffect, useRef } from 'react';
import { createSupabaseBrowser } from '@/lib/supabase/client';
import { useSessionStore } from '@/stores/sessionStore';
import { API_SANDBOX } from '@/constants/api/ApiSandbox';
import { fetchApi } from '@/utils/api';
import { UserResolveResponse } from '@/interface/ChatApp';

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const setSession = useSessionStore((s) => s.setSession);
  const setInternalUserId = useSessionStore((s) => s.setInternalUserId);
  const resolvedUidRef = useRef<string | null>(null);

  useEffect(() => {
    const supabase = createSupabaseBrowser();
    let mounted = true;

    async function syncUser(supabaseUid: string, email: string, name: string) {
      if (resolvedUidRef.current === supabaseUid) return;
      try {
        const response = await fetchApi<UserResolveResponse>(
          API_SANDBOX.USER_SYNC,
          { supabaseUid, email, username: name }
        );
        if (mounted) {
          setInternalUserId(response.userId);
          resolvedUidRef.current = supabaseUid;
        }
      } catch (error) {
        console.error('[AuthProvider] Failed to sync user:', error);
      }
    }

    async function init() {
      const { data: { session } } = await supabase.auth.getSession();
      if (!mounted) return;
      setSession(session);
      if (session?.user) {
        const { id, email, user_metadata } = session.user;
        await syncUser(id, email || '', user_metadata?.full_name || email?.split('@')[0] || 'user');
      }
    }

    init();

    const { data: { subscription } } = supabase.auth.onAuthStateChange(
      async (_event, session) => {
        if (!mounted) return;
        setSession(session);
        if (session?.user) {
          const { id, email, user_metadata } = session.user;
          await syncUser(id, email || '', user_metadata?.full_name || email?.split('@')[0] || 'user');
        } else {
          setInternalUserId(null);
          resolvedUidRef.current = null;
        }
      }
    );

    return () => {
      mounted = false;
      subscription.unsubscribe();
    };
  }, [setSession, setInternalUserId]);

  return <>{children}</>;
}
