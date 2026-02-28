import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { Shield } from 'lucide-react';

import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import SessionAudios from './pages/SessionAudios';
import ForgotPassword from './pages/ForgotPassword';

import { authService } from './services/api';

// Private Route wrapper
const PrivateRoute = () => {
  return authService.isAuthenticated() ? <Outlet /> : <Navigate to="/login" replace />;
};

// Layout for authenticated pages
const AppLayout = () => {
  const user = authService.getUser();

  const handleLogout = () => {
    authService.logout();
    window.location.href = '/login';
  };

  return (
    <div className="app-container">
      <nav className="navbar">
        <div className="navbar-brand">
          <Shield size={24} color="var(--accent-color)" />
          <span>SOS Evidence Portal</span>
        </div>
        <div className="navbar-user">
          <span>{user?.phone}</span>
          <button className="btn" onClick={handleLogout}>Logout</button>
        </div>
      </nav>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
};

const App = () => {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />

        {/* Protected Routes */}
        <Route element={<PrivateRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/sessions/:id/audios" element={<SessionAudios />} />
          </Route>
        </Route>

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
};

export default App;
