import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { sessionService } from '../services/api';
import { Clock, Calendar, ChevronRight, Activity } from 'lucide-react';

const Dashboard = () => {
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchSessions = async () => {
      try {
        const data = await sessionService.getSessions();
        setSessions(data);
      } catch (err) {
        setError('Failed to load SOS sessions.');
      } finally {
        setLoading(false);
      }
    };
    fetchSessions();
  }, []);

  const formatDate = (isoString) => {
    return new Date(isoString).toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  };

  const formatTime = (isoString) => {
    return new Date(isoString).toLocaleTimeString(undefined, {
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  if (loading) return <div className="main-content">Loading your evidence...</div>;

  return (
    <>
      <div className="page-header">
        <div>
          <h2>Your SOS Sessions</h2>
          <p style={{ color: 'var(--text-secondary)' }}>Event timeline and recorded evidence.</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {sessions.length === 0 && !error ? (
        <div className="empty-state">
          <Activity size={48} color="var(--text-secondary)" style={{ margin: '0 auto 1rem' }} />
          <h3>No SOS history found</h3>
          <p>You have not triggered any SOS recordings.</p>
        </div>
      ) : (
        <ul className="data-list">
          {sessions.map(session => (
            <li key={session.id}>
              <Link to={`/sessions/${session.id}/audios`} className="data-item">
                <div className="data-item-info">
                  <div className="data-item-title">Emergency Event</div>
                  <div className="data-item-meta">
                    <span className="meta-badge">
                      <Calendar size={14} />
                      {formatDate(session.created_at)}
                    </span>
                    <span className="meta-badge">
                      <Clock size={14} />
                      {formatTime(session.created_at)}
                    </span>
                  </div>
                </div>
                <ChevronRight size={20} color="var(--text-secondary)" />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
};

export default Dashboard;
