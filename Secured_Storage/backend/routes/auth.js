const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const User = require('../models/User');

// Register endpoint (added for creating users easily in the new MongoDB system)
router.post('/register', async (req, res) => {
  const { phone, password } = req.body;

  if (!phone || !password) {
    return res.status(400).json({ error: 'Phone and password are required' });
  }

  try {
    const existingUser = await User.findOne({ phone });
    if (existingUser) {
      return res.status(400).json({ error: 'User with this phone number already exists' });
    }

    const newUser = new User({ phone, password });
    await newUser.save();

    res.status(201).json({ message: 'User registered successfully. You can now log in.' });
  } catch (err) {
    console.error('Registration error:', err);
    res.status(500).json({ error: 'Failed to register user' });
  }
});

// Login endpoint using MongoDB
router.post('/login', async (req, res) => {
  const { phone, password } = req.body;

  if (!phone || !password) {
    return res.status(400).json({ error: 'Phone and password are required' });
  }

  try {
    // 1. Find user in MongoDB
    const user = await User.findOne({ phone });
    if (!user) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    // 2. Verify password
    const isMatch = await user.comparePassword(password);
    if (!isMatch) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    // 3. Generate JWT
    // We include both the MongoDB _id and the phone. 
    // Phone might be used as the foreign key in Supabase sos_sessions if Android transitioned to it.
    const token = jwt.sign(
      { userId: user._id.toString(), phone: user.phone },
      process.env.JWT_SECRET || 'fallback_secret_do_not_use_in_prod',
      { expiresIn: '8h' } // 8 hours session
    );

    res.json({
      message: 'Login successful',
      token,
      user: {
        id: user._id.toString(),
        phone: user.phone
      }
    });

  } catch (err) {
    console.error('Server error during login:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Request OTP
router.post('/forgot-password', async (req, res) => {
  const { phone } = req.body;

  if (!phone) {
    return res.status(400).json({ error: 'Phone number is required' });
  }

  try {
    const user = await User.findOne({ phone });
    if (!user) {
      // Return 200 anyway to prevent user enumeration
      return res.json({ message: 'If the phone number is registered, an OTP has been sent.' });
    }

    // Generate 6 digit OTP
    const otp = Math.floor(100000 + Math.random() * 900000).toString();

    user.resetOtp = otp;
    user.resetOtpExpires = Date.now() + 15 * 60 * 1000; // 15 minutes expires
    await user.save();

    // Mock sending OTP
    console.log(`[MOCK SMS] Sending OTP ${otp} to User: ${user.phone}`);
    user.trustedContacts.forEach(contact => {
      console.log(`[MOCK SMS] Sending OTP ${otp} to Trusted Contact: ${contact} (for User ${user.phone})`);
    });

    res.json({ message: 'If the phone number is registered, an OTP has been sent to the user and their trusted contacts.', debugOtp: otp });
  } catch (err) {
    console.error('Forgot password error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Reset Password
router.post('/reset-password', async (req, res) => {
  const { phone, otp, newPassword } = req.body;

  if (!phone || !otp || !newPassword) {
    return res.status(400).json({ error: 'Phone, OTP, and new password are required' });
  }

  try {
    const user = await User.findOne({
      phone,
      resetOtp: otp,
      resetOtpExpires: { $gt: Date.now() }
    });

    if (!user) {
      return res.status(400).json({ error: 'Invalid or expired OTP' });
    }

    user.password = newPassword;
    user.resetOtp = null;
    user.resetOtpExpires = null;
    await user.save();

    res.json({ message: 'Password has been reset successfully' });
  } catch (err) {
    console.error('Reset password error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;
