import axios from 'axios';

// Configurable base URL, falling back to localhost for dev
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:3000/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to attach JWT token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('sos_token');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor to handle 401 Unauthorized globally
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      // Token is invalid or expired
      localStorage.removeItem('sos_token');
      localStorage.removeItem('sos_user');

      // Redirect to login if not already there
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export const authService = {
  login: async (phone, password) => {
    const response = await api.post('/auth/login', { phone, password });
    if (response.data.token) {
      localStorage.setItem('sos_token', response.data.token);
      localStorage.setItem('sos_user', JSON.stringify(response.data.user));
    }
    return response.data;
  },
  logout: () => {
    localStorage.removeItem('sos_token');
    localStorage.removeItem('sos_user');
  },
  isAuthenticated: () => {
    return !!localStorage.getItem('sos_token');
  },
  getUser: () => {
    const user = localStorage.getItem('sos_user');
    return user ? JSON.parse(user) : null;
  }
};

export const sessionService = {
  getSessions: async () => {
    const response = await api.get('/sessions');
    return response.data.sessions;
  },
  getSessionAudios: async (sessionId) => {
    const response = await api.get(`/sessions/${sessionId}/audios`);
    return response.data.audios;
  }
};

export default api;
