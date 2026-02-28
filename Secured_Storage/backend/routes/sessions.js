const express = require('express');
const router = express.Router();
const supabase = require('../config/supabase');
const authenticateToken = require('../middleware/auth');

// Apply authentication middleware to all routes in this file
router.use(authenticateToken);

// GET /api/sessions - Get all SOS sessions for the logged-in user
router.get('/', async (req, res) => {
  try {
    const { data: sessions, error } = await supabase
      .from('sos_sessions')
      .select('*')
      .eq('user_id', req.user.userId)
      .order('created_at', { ascending: false });

    if (error) {
      throw error;
    }

    res.json({ sessions });
  } catch (err) {
    console.error('Error fetching sessions:', err);
    res.status(500).json({ error: 'Failed to fetch sessions' });
  }
});

// GET /api/sessions/:id/audios - Get all audio files for a specific session
router.get('/:id/audios', async (req, res) => {
  const sessionId = req.params.id;

  try {
    // 1. First, verify that this session actually belongs to the user
    // This prevents users from guessing session IDs and accessing other's metadata
    const { data: sessionDoc, error: sessionError } = await supabase
      .from('sos_sessions')
      .select('id')
      .eq('id', sessionId)
      .eq('user_id', req.user.userId)
      .single();

    if (sessionError || !sessionDoc) {
      return res.status(404).json({ error: 'Session not found or access denied' });
    }

    // 2. Fetch audio recordings metadata for this session
    const { data: audios, error: audioError } = await supabase
      .from('audio_recordings')
      .select('*')
      .eq('sos_session_id', sessionId)
      .order('created_at', { ascending: true });

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
    console.error('Error fetching session audios:', err);
    res.status(500).json({ error: 'Failed to fetch audio recordings' });
  }
});

module.exports = router;
