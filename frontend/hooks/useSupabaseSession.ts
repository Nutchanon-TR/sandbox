import { useEffect, useState } from "react";
import { createSupabaseBrowser } from "@/lib/supabase/client";
import { Session } from "@supabase/supabase-js";

export function useSupabaseSession() {
  const supabase = createSupabaseBrowser();
  const [session, setSession] = useState<Session | null>(null);
  const [status, setStatus] = useState<"loading" | "authenticated" | "unauthenticated">("loading");

  useEffect(() => {
    let mounted = true;

    async function getInitialSession() {
      const { data: { session: initialSession } } = await supabase.auth.getSession();
      if (mounted) {
        setSession(initialSession);
        setStatus(initialSession ? "authenticated" : "unauthenticated");
      }
    }

    getInitialSession();

    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, currentSession) => {
      if (mounted) {
        setSession(currentSession);
        setStatus(currentSession ? "authenticated" : "unauthenticated");
      }
    });

    return () => {
      mounted = false;
      subscription.unsubscribe();
    };
  }, [supabase.auth]);

  return { data: session, status, supabase };
}
