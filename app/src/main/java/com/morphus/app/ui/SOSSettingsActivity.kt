package com.morphus.app.ui

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.morphus.app.R
import com.morphus.app.data.AppRepository
import com.morphus.app.data.SettingsManager
import com.morphus.app.databinding.ActivitySosSettingsBinding
import java.io.File

/**
 * Secret SOS Settings Activity.
 * Accessible only via PIN entry in the calculator.
 * Closes automatically when put in background to maintain secrecy.
 */
class SOSSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySosSettingsBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var repository: AppRepository

    // ── Trusted Contacts ──
    private val contactsList = mutableListOf<TrustedContact>()
    private lateinit var contactsAdapter: ContactsAdapter

    // ── Recordings ──
    private val recordingsList = mutableListOf<RecordingItem>()
    private lateinit var recordingsAdapter: RecordingsAdapter
    private var mediaPlayer: MediaPlayer? = null

    // ── Data Classes ──
    data class TrustedContact(val name: String, val number: String)
    data class RecordingItem(val name: String, val path: String, val size: Long)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivitySosSettingsBinding.inflate(layoutInflater)
            setContentView(binding.root)

            settingsManager = SettingsManager(this)
            repository = AppRepository(this)

            setupContactsRecyclerView()
            setupRecordingsRecyclerView()
            loadSettings()
            loadRecordings()
            setupListeners()
        } catch (e: Exception) {
            Log.e("MORPHUS_UI", "CRITICAL ViewBinding or Load Failure: ${e.message}", e)
            finish()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finish()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    // ═══════════════════════════════════
    // Setup
    // ═══════════════════════════════════

    private fun setupContactsRecyclerView() {
        contactsAdapter = ContactsAdapter(contactsList) { position ->
            contactsList.removeAt(position)
            contactsAdapter.notifyItemRemoved(position)
            Log.d("MORPHUS_CONTACT", "Removed contact at position $position")
        }
        binding.rvContacts.layoutManager = LinearLayoutManager(this)
        binding.rvContacts.adapter = contactsAdapter
    }

    private fun setupRecordingsRecyclerView() {
        recordingsAdapter = RecordingsAdapter(
            items = recordingsList,
            onPlay = { item -> playRecording(item) },
            onDelete = { position -> deleteRecording(position) }
        )
        binding.rvRecordings.layoutManager = LinearLayoutManager(this)
        binding.rvRecordings.adapter = recordingsAdapter
    }

    private fun loadSettings() {
        try {
            // Load contacts into visual list
            val contacts = repository.getEmergencyContacts()
            contactsList.clear()
            contacts.forEach { number ->
                contactsList.add(TrustedContact(name = "Contact", number = number))
            }
            contactsAdapter.notifyDataSetChanged()

            // Load Toggles
            binding.switchShake.isChecked = settingsManager.isShakeEnabled
            binding.switchPower.isChecked = settingsManager.isPowerButtonEnabled
            binding.switchAutoCall.isChecked = settingsManager.isAutoCallEnabled
            binding.switchAudioRecord.isChecked = settingsManager.isAudioRecordEnabled
            binding.switchVolunteers.isChecked = settingsManager.isAlertVolunteersEnabled

            // Load Thresholds
            binding.etMovementThreshold.setText(settingsManager.movementThreshold.toString())
            binding.etUpdateInterval.setText(settingsManager.updateInterval.toString())

            // Load Message Template
            binding.etMessageTemplate.setText(settingsManager.messageTemplate)
        } catch (e: Exception) {
            Log.e("MORPHUS_UI", "Error loading settings: ${e.message}")
            finish()
        }
    }

    private fun loadRecordings() {
        try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            recordingsList.clear()

            dir?.listFiles()?.filter { it.extension == "m4a" }?.sortedByDescending { it.lastModified() }?.forEach {
                recordingsList.add(RecordingItem(it.name, it.absolutePath, it.length()))
            }

            recordingsAdapter.notifyDataSetChanged()

            binding.tvNoRecordings.visibility =
                if (recordingsList.isEmpty()) View.VISIBLE else View.GONE

            Log.d("MORPHUS_UI", "Loaded recordings count=${recordingsList.size}")
        } catch (e: Exception) {
            Log.e("MORPHUS_UI", "Failed to load recordings: ${e.message}")
        }
    }

    // ═══════════════════════════════════
    // Listeners
    // ═══════════════════════════════════

    private fun setupListeners() {
        binding.btnSave.setOnClickListener { saveAllSettings() }

        binding.btnChangePin.setOnClickListener { showChangePinDialog() }

        binding.btnDeactivateSos.setOnClickListener {
            com.morphus.app.manager.SosManager(this).deactivate()
            Toast.makeText(this, "SOS Deactivated", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddContact.setOnClickListener {
            val number = binding.etContacts.text?.toString()?.trim() ?: ""
            if (number.isEmpty()) {
                Toast.makeText(this, "Enter a phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (contactsList.size >= 5) {
                Toast.makeText(this, "Maximum 5 contacts", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            contactsList.add(TrustedContact(name = "Contact", number = number))
            contactsAdapter.notifyItemInserted(contactsList.size - 1)
            binding.etContacts.text?.clear()
            Log.d("MORPHUS_CONTACT", "Added contact: $number")
        }
    }

    // ═══════════════════════════════════
    // Save
    // ═══════════════════════════════════

    private fun saveAllSettings() {
        try {
            // Save Contacts from visual list
            repository.getEmergencyContacts().forEach { repository.removeEmergencyContact(it) }
            contactsList.forEach { repository.saveEmergencyContact(it.number) }

            val csv = contactsList.joinToString(",") { it.number }
            Log.d("MORPHUS_CONTACT", "Saved CSV=$csv")

            // Save Toggles
            settingsManager.isShakeEnabled = binding.switchShake.isChecked
            settingsManager.isPowerButtonEnabled = binding.switchPower.isChecked
            settingsManager.isAutoCallEnabled = binding.switchAutoCall.isChecked
            settingsManager.isAudioRecordEnabled = binding.switchAudioRecord.isChecked
            settingsManager.isAlertVolunteersEnabled = binding.switchVolunteers.isChecked

            // Save Thresholds
            val threshold = binding.etMovementThreshold.text?.toString()?.toIntOrNull() ?: 50
            settingsManager.movementThreshold = threshold

            val interval = binding.etUpdateInterval.text?.toString()?.toIntOrNull() ?: 5
            settingsManager.updateInterval = interval

            // Save Message Template
            settingsManager.messageTemplate = binding.etMessageTemplate.text?.toString() ?: ""

            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Log.e("MORPHUS_UI", "Error saving settings: ${e.message}")
            finish()
        }
    }

    // ═══════════════════════════════════
    // Recordings Playback / Delete
    // ═══════════════════════════════════

    private fun playRecording(item: RecordingItem) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(item.path)
                prepare()
                start()
            }
            Log.d("MORPHUS_UI", "Playing: ${item.name}")
            Toast.makeText(this, "Playing ${item.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MORPHUS_UI", "Playback failed: ${e.message}")
            Toast.makeText(this, "Cannot play recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteRecording(position: Int) {
        try {
            val item = recordingsList[position]
            File(item.path).delete()
            recordingsList.removeAt(position)
            recordingsAdapter.notifyItemRemoved(position)

            binding.tvNoRecordings.visibility =
                if (recordingsList.isEmpty()) View.VISIBLE else View.GONE

            Log.d("MORPHUS_UI", "Deleted recording: ${item.name}")
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MORPHUS_UI", "Delete failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════
    // Change PIN Dialog
    // ═══════════════════════════════════

    private fun showChangePinDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_pin, null)
        val etOldPin = dialogView.findViewById<EditText>(R.id.etOldPin)
        val etNewPin = dialogView.findViewById<EditText>(R.id.etNewPin)

        AlertDialog.Builder(this)
            .setTitle("Change SOS PIN")
            .setView(dialogView)
            .setPositiveButton("Change") { _, _ ->
                val oldPin = etOldPin.text.toString()
                val newPin = etNewPin.text.toString()

                if (settingsManager.verifyPin(oldPin)) {
                    if (newPin.length == 4) {
                        settingsManager.sosPin = newPin
                        Toast.makeText(this, "PIN updated successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "New PIN must be 4 digits", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Incorrect current PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ═══════════════════════════════════
    // Contacts Adapter
    // ═══════════════════════════════════

    private class ContactsAdapter(
        private val items: MutableList<TrustedContact>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<ContactsAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvAvatar: TextView = v.findViewById(R.id.tvContactAvatar)
            val tvName: TextView = v.findViewById(R.id.tvContactName)
            val tvNumber: TextView = v.findViewById(R.id.tvContactNumber)
            val btnDelete: ImageButton = v.findViewById(R.id.btnDeleteContact)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_trusted_contact, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvAvatar.text = item.name.take(1).uppercase()
            holder.tvName.text = item.name
            holder.tvNumber.text = item.number
            holder.btnDelete.setOnClickListener { onDelete(holder.adapterPosition) }
        }

        override fun getItemCount() = items.size
    }

    // ═══════════════════════════════════
    // Recordings Adapter
    // ═══════════════════════════════════

    private class RecordingsAdapter(
        private val items: MutableList<RecordingItem>,
        private val onPlay: (RecordingItem) -> Unit,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<RecordingsAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvRecordingName)
            val tvSize: TextView = v.findViewById(R.id.tvRecordingSize)
            val btnPlay: ImageButton = v.findViewById(R.id.btnPlay)
            val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recording, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.tvSize.text = formatSize(item.size)
            holder.btnPlay.setOnClickListener { onPlay(item) }
            holder.btnDelete.setOnClickListener { onDelete(holder.adapterPosition) }
        }

        override fun getItemCount() = items.size

        private fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            }
        }
    }
}
