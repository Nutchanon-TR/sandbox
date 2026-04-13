import axios from 'axios';
import { createSupabaseBrowser } from '@/lib/supabase/client';

const api = axios.create({
  // Production (ACA): NEXT_PUBLIC_API_URL is baked at build time via --build-arg in CI.
  // Local Dev: leave NEXT_PUBLIC_API_URL empty → axios uses relative URLs, which route
  //            through the Nginx gateway (http://localhost) automatically.
  baseURL: process.env.NEXT_PUBLIC_API_URL || '',
  headers: {
    sourceSystem: process.env.SOURCE_SYSTEM_NAME || 'FRONTEND',
  },
});

api.interceptors.request.use(async (config) => {
  const supabase = createSupabaseBrowser();
  const { data: { session } } = await supabase.auth.getSession();
  if (session?.access_token) {
    config.headers.Authorization = `Bearer ${session.access_token}`;
  }
  return config;
});

export default api;