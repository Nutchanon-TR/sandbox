import axios from 'axios';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080',
  headers: {
    sourceSystem: process.env.SOURCE_SYSTEM_NAME || 'FRONTEND',
  },
});

// (Optional) เพิ่ม Interceptors สำหรับจัดการ Request/Response
api.interceptors.request.use((config) => {
  // เช่น ใส่ Token อัตโนมัติ
  // const token = localStorage.getItem('token');
  // if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export default api;