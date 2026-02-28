require('dotenv').config();
const twilio = require('twilio');

const accountSid = process.env.TWILIO_ACCOUNT_SID;
const authToken = process.env.TWILIO_AUTH_TOKEN;
const twilioPhoneNumber = process.env.TWILIO_PHONE_NUMBER;

let client;
if (accountSid && authToken) {
  client = twilio(accountSid, authToken);
} else {
  console.warn('Twilio credentials not found in environment variables. SMS will only be mocked.');
}

const sendSMS = async (to, message) => {
  if (!client) {
    console.log(`[MOCK SMS] To: ${to} | Message: ${message}`);
    return true; // Pretend it succeeded
  }

  try {
    const response = await client.messages.create({
      body: message,
      from: twilioPhoneNumber,
      to: to
    });
    console.log(`[TWILIO SMS] Successfully sent to ${to}. SID: ${response.sid}`);
    return true;
  } catch (error) {
    console.error(`[TWILIO SMS ERROR] Failed to send SMS to ${to}:`, error.message);
    throw error;
  }
};

module.exports = { sendSMS };
