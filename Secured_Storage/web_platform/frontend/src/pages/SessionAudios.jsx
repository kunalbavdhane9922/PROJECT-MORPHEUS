import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { sessionService } from '../services/api';
import { ArrowLeft, PlayCircle, AlertTriangle } from 'lucide-react';

const SessionAudios = () => {
  const { id } = useParams();
  const [audios, setAudios] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchAudios = async () => {
      try {
        const data = await sessionService.getSessionAudios(id);
        setAudios(data);
      } catch (err) {
        setError('Failed to load audio recordings or access denied.');
      } finally {
        setLoading(false);
      }
    };
    fetchAudios();
  }, [id]);

  const formatTimestamp = (isoString) => {
    return new Date(isoString).toLocaleTimeString(undefined, {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  if (loading) return <div className="main-content">Securely retrieving audios...</div>;

  return (
    <>
      <Link to="/dashboard" className="back-link">
        <ArrowLeft size={16} />
        Back to Dashboard
      </Link>

      <div className="page-header">
        <div>
          <h2>Session Audio Evidence</h2>
          <p style={{ color: 'var(--text-secondary)' }}>Audio recordings captured during this SOS event.</p>
        </div>
      </div>

      {error ? (
        <div className="alert alert-error">{error}</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {audios.length === 0 ? (
            <div className="empty-state">
              <PlayCircle size={48} color="var(--text-secondary)" style={{ margin: '0 auto 1rem' }} />
              <h3>No audio found</h3>
              <p>No audio files were retrieved for this specific session.</p>
            </div>
          ) : (
            audios.map((audio, index) => (
              <div key={audio.id || index} className="card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <h3 style={{ fontSize: '1.25rem' }}>Recording #{index + 1}</h3>
                    <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
                      Captured at {formatTimestamp(audio.created_at)}
                    </p>
                  </div>
                  {/* Status Indicator */}
                  <span className="meta-badge" style={{ color: audio.signedUrl ? 'var(--success-color)' : 'var(--error-color)' }}>
                    {audio.signedUrl ? 'Secure stream active' : <><AlertTriangle size={14} /> Access Error</>}
                  </span>
                </div>

                <div className="audio-player-wrapper">
                  {audio.signedUrl ? (
                    <audio controls controlsList="nodownload noplaybackrate">
                      <source src={audio.signedUrl} type="audio/mpeg" />
                      Your browser does not support the audio element.
                    </audio>
                  ) : (
                    <div className="alert alert-error" style={{ margin: '0' }}>
                      {audio.urlError || 'Unable to generate playback stream'}
                    </div>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </>
  );
};

export default SessionAudios;
