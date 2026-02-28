const { createClient } = require('@supabase/supabase-js');

const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_ANON_KEY;

if (!supabaseUrl || !supabaseKey) {
  console.warn('Missing SUPABASE_URL or SUPABASE_ANON_KEY environment variables');
}

// Create a custom fetch that times out quickly (1.5 seconds) so we can gracefully fallback to dummy data
const fetchWithTimeout = async (url, options) => {
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), 1500);

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal
    });
    clearTimeout(id);
    return response;
  } catch (error) {
    clearTimeout(id);
    throw error;
  }
};

// Create a single supabase client for interacting with your database
const supabase = createClient(
  supabaseUrl || 'https://placeholder.supabase.co',
  supabaseKey || 'placeholder_key',
  {
    global: {
      fetch: fetchWithTimeout
    }
  }
);

module.exports = supabase;
