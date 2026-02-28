require('dotenv').config();
const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const User = require('../models/User');

const seedUsers = async () => {
  try {
    const mongoUri = process.env.MONGO_URI || 'mongodb://localhost:27017/sos_platform';
    await mongoose.connect(mongoUri);
    console.log('Connected to MongoDB');

    // Clear existing users
    await User.deleteMany({});
    console.log('Cleared existing users');

    // Dummy users
    const users = [
      {
        phone: '+1234567890',
        password: 'password123',
        trustedContacts: ['+1987654321', '+1122334455']
      },
      {
        phone: '+1112223333',
        password: 'securePassword!',
        trustedContacts: ['+1555666777', '+1888999000']
      },
      {
        phone: '+9998887777',
        password: 'testpassword',
        trustedContacts: ['+1231231234']
      }
    ];

    for (const u of users) {
      const newUser = new User(u);
      await newUser.save();
      console.log(`User ${u.phone} seeded with trusted contacts: ${u.trustedContacts.join(', ')}`);
    }

    console.log('Database seeding completed successfully');
    process.exit(0);
  } catch (err) {
    console.error('Error seeding database:', err);
    process.exit(1);
  }
};

seedUsers();
