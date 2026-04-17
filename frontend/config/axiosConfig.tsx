import axios from 'axios';
import { useSessionStore } from '@/stores/sessionStore';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || '',
  headers: {
    sourceSystem: process.env.SOURCE_SYSTEM_NAME || 'FRONTEND',
  },
});

api.interceptors.request.use(async (config) => {
  const token = useSessionStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default api;
