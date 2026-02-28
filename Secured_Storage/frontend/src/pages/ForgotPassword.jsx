import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Shield, Smartphone, Key, AlertTriangle, ArrowLeft } from 'lucide-react';
import api from '../services/api';

const ForgotPassword = () => {
  const [step, setStep] = useState(1);
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [debugOtp, setDebugOtp] = useState(null); // For hackathon purposes to view the OTP

  const navigate = useNavigate();

  const handleRequestOtp = async (e) => {
    e.preventDefault();
    if (!phone) {
      setError('Please enter your registered phone number');
      return;
    }

    setLoading(true);
    setError(null);
    setMessage(null);
    setDebugOtp(null);

    try {
      const response = await api.post('/auth/forgot-password', { phone });
      setMessage(response.data.message);
      if (response.data.debugOtp) {
        setDebugOtp(response.data.debugOtp);
      }
      setStep(2); // Move to OTP entry step
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to request OTP. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
    if (!otp || !newPassword) {
      setError('Please enter the OTP and your new password');
      return;
    }

    setLoading(true);
    setError(null);
    setMessage(null);

    try {
      const response = await api.post('/auth/reset-password', { phone, otp, newPassword });
      setMessage(response.data.message);

      // Navigate to login after successful reset
      setTimeout(() => {
        navigate('/login');
      }, 2000);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to reset password. Check your OTP.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="card login-card">
        <Link to="/login" className="back-link" style={{ marginBottom: '2rem' }}>
          <ArrowLeft size={16} />
          Back to Login
        </Link>

        <div className="login-header" style={{ marginBottom: '1.5rem' }}>
          <Shield size={48} color="var(--accent-color)" />
          <h2 style={{ marginTop: '1rem' }}>Account Recovery</h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginTop: '0.5rem' }}>
            {step === 1 ? 'Recover access to your SOS evidence portal' : 'Verify identity with trusted contacts'}
          </p>
        </div>

        {error && <div className="alert alert-error">{error}</div>}
        {message && <div className="alert custom-alert-success" style={{ backgroundColor: 'rgba(46, 160, 67, 0.1)', borderColor: 'rgba(46, 160, 67, 0.4)', color: 'var(--success-color)', padding: '0.75rem 1rem', borderRadius: '6px', marginBottom: '1rem', fontSize: '0.875rem' }}>{message}</div>}

        {/* HACKATHON HELPER: display OTP dynamically to demo bypassing SMS blockages */}
        {debugOtp && (
          <div className="alert" style={{ backgroundColor: 'rgba(47, 129, 247, 0.1)', borderColor: 'rgba(47, 129, 247, 0.4)', color: 'var(--accent-color)' }}>
            <strong>[Mock SMS System]</strong> The OTP sent to user and trusted contacts is: <br />
            <span style={{ fontSize: '1.25rem', letterSpacing: '2px', fontWeight: 'bold' }}>{debugOtp}</span>
          </div>
        )}

        {step === 1 && (
          <form onSubmit={handleRequestOtp}>
            <div className="form-group">
              <label className="form-label" htmlFor="phone">Registered Phone Number</label>
              <input
                id="phone"
                type="tel"
                className="form-control"
                placeholder="+1234567890"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                disabled={loading}
              />
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: '100%', justifyContent: 'center', marginTop: '1rem' }}
              disabled={loading}
            >
              <Smartphone size={16} />
              {loading ? 'Sending...' : 'Send OTP via SMS'}
            </button>
          </form>
        )}

        {step === 2 && (
          <form onSubmit={handleResetPassword}>
            <div className="form-group" style={{ marginBottom: '1rem' }}>
              <label className="form-label" htmlFor="otp">Enter 6-digit OTP</label>
              <input
                id="otp"
                type="text"
                className="form-control"
                placeholder="123456"
                value={otp}
                onChange={(e) => setOtp(e.target.value)}
                disabled={loading || !!message}
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="newPassword">New Password</label>
              <input
                id="newPassword"
                type="password"
                className="form-control"
                placeholder="••••••••"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                disabled={loading || !!message}
              />
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: '100%', justifyContent: 'center', marginTop: '1rem' }}
              disabled={loading || !!message}
            >
              <Key size={16} />
              {loading ? 'Verifying...' : 'Reset Password'}
            </button>

            <p style={{ marginTop: '1.5rem', fontSize: '0.8rem', color: 'var(--text-secondary)', display: 'flex', gap: '8px', alignItems: 'center' }}>
              <AlertTriangle size={14} color="var(--error-color)" />
              A trusted contact must relay the OTP to you if your primary device is inaccessible.
            </p>
          </form>
        )}
      </div>
    </div>
  );
};

export default ForgotPassword;
