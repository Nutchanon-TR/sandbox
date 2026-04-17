import { create } from 'zustand';
import { Session, User } from '@supabase/supabase-js';

interface SessionState {
  session: Session | null;
  status: 'loading' | 'authenticated' | 'unauthenticated';
  user: User | null;
  accessToken: string | null;
  setSession: (session: Session | null) => void;
  clear: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  session: null,
  status: 'loading',
  user: null,
  accessToken: null,
  setSession: (session) =>
    set({
      session,
      status: session ? 'authenticated' : 'unauthenticated',
      user: session?.user ?? null,
      accessToken: session?.access_token ?? null,
    }),
  clear: () =>
    set({
      session: null,
      status: 'unauthenticated',
      user: null,
      accessToken: null,
    }),
}));
