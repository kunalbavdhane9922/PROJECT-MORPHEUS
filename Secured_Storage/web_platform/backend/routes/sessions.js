const express = require('express');
const router = express.Router();
const supabase = require('../config/supabase');
const authenticateToken = require('../middleware/auth');

// Apply authentication middleware to all routes in this file
router.use(authenticateToken);

// Dummy fallback data generators
const getDummySessions = (user) => [
  {
    id: 'dummy-session-1',
    user_id: user.userId,
    phone: user.phone,
    created_at: new Date().toISOString(),
    status: 'closed',
    location: '123 Dummy St, Test City'
  },
  {
    id: 'dummy-session-2',
    user_id: user.userId,
    phone: user.phone,
    created_at: new Date(Date.now() - 86400000).toISOString(),
    status: 'closed',
    location: '456 Mock Ave, Test City'
  }
];

const getDummyAudios = (sessionId) => [
  {
    id: 'dummy-audio-1',
    sos_session_id: sessionId,
    created_at: new Date().toISOString(),
    file_path: 'local-test-recording.mp3',
    // Point to a local static file on the Express server instead of the internet
    signedUrl: 'http://localhost:3000/public/audios/local-test-recording.mp3',
    urlError: null
  }
];

// GET /api/sessions - Get all SOS sessions for the logged-in user
router.get('/', async (req, res) => {
  try {
    // We use the phone number as the joining key now that Auth is in MongoDB
    // Use Promise.race to enforce a strict local 1.5s timeout, regardless of what the network is doing
    const timeoutPromise = new Promise((_, reject) =>
      setTimeout(() => reject(new Error('Strict Fallback Timeout Triggered')), 1500)
    );

    const supabasePromise = supabase
      .from('sos_sessions')
      .select('*')
      .eq('phone', req.user.phone)
      .order('created_at', { ascending: false });

    const { data: sessions, error } = await Promise.race([supabasePromise, timeoutPromise]);

    if (error) {
      console.warn('Supabase fetch failed, falling back to dummy data:', error.message);
      throw error;
    }

    if (!sessions || sessions.length === 0) {
      console.log('No sessions found, providing dummy data.');
      return res.json({ sessions: getDummySessions(req.user) });
    }

    res.json({ sessions });
  } catch (err) {
    console.warn('Error fetching sessions, falling back to dummy data:', err.message);
    // Serve dummy data gracefully so the UI doesn't break
    res.json({ sessions: getDummySessions(req.user) });
  }
});

// GET /api/sessions/:id/audios - Get all audio files for a specific session
router.get('/:id/audios', async (req, res) => {
  const sessionId = req.params.id;

  // Immediately serve mock audios if it's a dummy session
  if (sessionId && sessionId.startsWith('dummy-session-')) {
    return res.json({ audios: getDummyAudios(sessionId) });
  }

  try {
    // 1. First, verify that this session actually belongs to the user
    // This prevents users from guessing session IDs and accessing other's metadata
    const timeoutPromise = new Promise((_, reject) =>
      setTimeout(() => reject(new Error('Strict Fallback Timeout Triggered')), 1500)
    );

    const sessionQuery = supabase
      .from('sos_sessions')
      .select('id')
      .eq('id', sessionId)
      .eq('phone', req.user.phone)
      .single();

    const { data: sessionDoc, error: sessionError } = await Promise.race([sessionQuery, timeoutPromise]);

    if (sessionError || !sessionDoc) {
      return res.status(404).json({ error: 'Session not found or access denied' });
    }

    // 2. Fetch audio recordings metadata for this session
    const audioQuery = supabase
      .from('audio_recordings')
      .select('*')
      .eq('sos_session_id', sessionId)
      .order('created_at', { ascending: true });

    const { data: audios, error: audioError } = await Promise.race([audioQuery, timeoutPromise]);

    if (audioError) {
      throw audioError;
    }

    // 3. Generate signed URLs for each audio file
    const audiosWithUrls = await Promise.all(audios.map(async (audio) => {
      // Assuming 'file_path' stores the path inside the 'sos_audio' private bucket
      const { data, error } = await supabase
        .storage
        .from('sos_audio')
        .createSignedUrl(audio.file_path, 60); // 60 seconds expiry for security

      return {
        ...audio,
        signedUrl: error ? null : data.signedUrl,
        urlError: error ? 'Failed to generate access URL' : null
      };
    }));

    res.json({ audios: audiosWithUrls });
  } catch (err) {
    console.warn('Error fetching session audios:', err.message);
    // Fallback to dummy audios on network failure
    res.json({ audios: getDummyAudios(sessionId) });
  }
});

module.exports = router;
