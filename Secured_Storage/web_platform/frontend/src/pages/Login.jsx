import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Shield, Lock, Smartphone, Key } from 'lucide-react';
import { authService } from '../services/api';

const Login = () => {
  const [step, setStep] = useState(1);
  const [phone, setPhone] = useState('');
  const [trustedPhone, setTrustedPhone] = useState('');
  const [otp, setOtp] = useState('');

  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [loading, setLoading] = useState(false);
  const [debugOtp, setDebugOtp] = useState(null);

  const navigate = useNavigate();

  const handleRequestOtp = async (e) => {
    e.preventDefault();
    if (!phone || !trustedPhone) {
      setError('Please enter both your phone number and a trusted contact\'s phone number');
      return;
    }

    setError(null);
    setMessage(null);
    setLoading(true);

    try {
      const response = await authService.requestLoginOtp(phone, trustedPhone);
      setMessage(response.message);
      if (response.debugOtp) {
        setDebugOtp(response.debugOtp);
      }
      setStep(2);
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to request OTP. Please check the numbers.');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    if (!otp) {
      setError('Please enter the OTP');
      return;
    }

    setError(null);
    setLoading(true);

    try {
      await authService.verifyLoginOtp(phone, otp);
      navigate('/dashboard');
    } catch (err) {
      setError(err.response?.data?.error || 'Invalid OTP. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="card login-card">
        <div className="login-header">
          <Shield size={48} color="var(--accent-color)" />
          <h2 style={{ marginTop: '1rem' }}>Secure Access portal</h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginTop: '0.5rem' }}>
            {step === 1 ? 'Enter your credentials and a trusted contact to receive an OTP.' : 'Enter the OTP sent to you and your trusted contact.'}
          </p>
        </div>

        {error && <div className="alert alert-error">{error}</div>}
        {message && <div className="alert custom-alert-success" style={{ backgroundColor: 'rgba(46, 160, 67, 0.1)', borderColor: 'rgba(46, 160, 67, 0.4)', color: 'var(--success-color)', padding: '0.75rem 1rem', borderRadius: '6px', marginBottom: '1rem', fontSize: '0.875rem' }}>{message}</div>}

        {debugOtp && (
          <div className="alert" style={{ backgroundColor: 'rgba(47, 129, 247, 0.1)', borderColor: 'rgba(47, 129, 247, 0.4)', color: 'var(--accent-color)' }}>
            <strong>[Mock SMS System]</strong> Login OTP is: <br />
            <span style={{ fontSize: '1.25rem', letterSpacing: '2px', fontWeight: 'bold' }}>{debugOtp}</span>
          </div>
        )}

        {step === 1 ? (
          <form onSubmit={handleRequestOtp}>
            <div className="form-group">
              <label className="form-label" htmlFor="phone">My Phone Number</label>
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

            <div className="form-group">
              <label className="form-label" htmlFor="trustedPhone">Trusted Contact's Phone Number</label>
              <input
                id="trustedPhone"
                type="tel"
                className="form-control"
                placeholder="+1987654321"
                value={trustedPhone}
                onChange={(e) => setTrustedPhone(e.target.value)}
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
              {loading ? 'Requesting...' : 'Send OTP via SMS'}
            </button>

            <div style={{ marginTop: '1.5rem', textAlign: 'center' }}>
              <Link to="/forgot-password" style={{ fontSize: '0.875rem' }}>
                Account Recovery
              </Link>
            </div>
          </form>
        ) : (
          <form onSubmit={handleVerifyOtp}>
            <div className="form-group">
              <label className="form-label" htmlFor="otp">Enter 6-digit OTP</label>
              <input
                id="otp"
                type="text"
                className="form-control"
                placeholder="123456"
                value={otp}
                onChange={(e) => setOtp(e.target.value)}
                disabled={loading}
              />
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: '100%', justifyContent: 'center', marginTop: '1rem' }}
              disabled={loading}
            >
              <Lock size={16} />
              {loading ? 'Verifying...' : 'Secure Login'}
            </button>
            <button
              type="button"
              className="btn"
              style={{ width: '100%', justifyContent: 'center', marginTop: '0.5rem' }}
              onClick={() => { setStep(1); setOtp(''); setMessage(null); setDebugOtp(null); }}
              disabled={loading}
            >
              Go Back
            </button>
          </form>
        )}
      </div>
    </div>
  );
};

export default Login;
