import axios from 'axios';
import { createSupabaseBrowser } from '@/lib/supabase/client';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080',
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