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

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      useSessionStore.getState().clear();
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export default api;
