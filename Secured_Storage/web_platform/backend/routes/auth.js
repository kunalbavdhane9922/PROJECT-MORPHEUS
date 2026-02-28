const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const User = require('../models/User');

const { sendSMS } = require('../utils/sms');

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

// Step 1: Request Login OTP (requires User Phone + Trusted Contact Phone)
router.post('/login/request-otp', async (req, res) => {
  const { phone, trustedContactPhone } = req.body;

  if (!phone || !trustedContactPhone) {
    return res.status(400).json({ error: 'Both your phone number and a trusted contact phone number are required.' });
  }

  try {
    const user = await User.findOne({ phone });
    if (!user) {
      return res.status(401).json({ error: 'User not found.' });
    }

    // Verify the provided trusted contact phone is actually in the user's trusted contacts list
    const isTrusted = user.trustedContacts.includes(trustedContactPhone);
    if (!isTrusted) {
      return res.status(403).json({ error: 'The provided number is not in your trusted contacts list.' });
    }

    // Generate 6 digit OTP
    const otp = Math.floor(100000 + Math.random() * 900000).toString();

    // Using the same resetOtp fields for login OTP for simplicity
    user.resetOtp = otp;
    user.resetOtpExpires = Date.now() + 15 * 60 * 1000; // 15 minutes expires
    await user.save();

    // Send SMS to both using Twilio (or mock if not configured)
    const message = `Your SOS Portal Login OTP is: ${otp}. Do not share this code.`;

    // Attempt to send to both independently so one invalid format doesn't block the other
    const smsPromises = [
      sendSMS(user.phone, message).catch(err => console.error(`[OTP Error] Failed to send to user ${user.phone}: ${err.message}`)),
      sendSMS(trustedContactPhone, message).catch(err => console.error(`[OTP Error] Failed to send to trusted contact ${trustedContactPhone}: ${err.message}`))
    ];
    await Promise.all(smsPromises);

    res.json({
      message: 'OTP sent successfully to both your phone and your trusted contact.',
      debugOtp: process.env.NODE_ENV !== 'production' ? otp : undefined // Helpful for debugging
    });
  } catch (err) {
    console.error('Login request OTP error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Step 2: Verify Login OTP
router.post('/login/verify-otp', async (req, res) => {
  const { phone, otp } = req.body;

  if (!phone || !otp) {
    return res.status(400).json({ error: 'Phone and OTP are required.' });
  }

  try {
    // 1. Find user in MongoDB and check OTP
    const user = await User.findOne({
      phone,
      resetOtp: otp,
      resetOtpExpires: { $gt: Date.now() }
    });

    if (!user) {
      return res.status(401).json({ error: 'Invalid or expired OTP.' });
    }

    // Clear the OTP
    user.resetOtp = null;
    user.resetOtpExpires = null;
    await user.save();

    // 3. Generate JWT
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
    console.error('Server error during login verification:', err);
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
