package com.aayush.voiceai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvStatus: TextView
    private lateinit var tvHeard: TextView
    private lateinit var tvReply: TextView
    private lateinit var btnMic: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvHeard = findViewById(R.id.tvHeard)
        tvReply = findViewById(R.id.tvReply)
        btnMic = findViewById(R.id.btnMic)

        tts = TextToSpeech(this, this)
        requestMissingPermissions()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    tvStatus.text = "Sun raha hoon... Boliye!"
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    tvStatus.text = "Process kar raha hoon..."
                }
                override fun onError(error: Int) {
                    isListening = false
                    btnMic.text = "SPEAK"
                    tvStatus.text = "Mic error ($error). Dobara try karein."
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    btnMic.text = "SPEAK"
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val cmd = matches?.firstOrNull().orEmpty()
                    if (cmd.isNotBlank()) {
                        tvHeard.text = "You: $cmd"
                        handleCommand(cmd)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) tvHeard.text = partial
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        btnMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestMissingPermissions()
                return@setOnClickListener
            }
            if (!isListening) startListening() else stopListening()
        }
    }

    private fun requestMissingPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        isListening = true
        btnMic.text = "STOP"
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        btnMic.text = "SPEAK"
        speechRecognizer?.stopListening()
    }

    private fun handleCommand(rawCmd: String) {
        val lower = rawCmd.lowercase(Locale.getDefault())

        // 1. CALL
        if (lower.contains("call") || lower.contains("phone") || lower.contains("कॉल") || lower.contains("फोन")) {
            val number = findPhoneNumber(rawCmd)
            if (number != null) {
                replyAndSpeak("$number par call laga raha hoon.")
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(callIntent)
                } else {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                }
            } else {
                replyAndSpeak("Contact nahi mila. Naam ya phone number saaf bolein.")
            }
            return
        }

        // 2. WHATSAPP / SMS MESSAGE
        if (lower.contains("whatsapp") || lower.contains("message") || lower.contains("sms") || lower.contains("मैसेज")) {
            val number = findPhoneNumber(rawCmd)
            if (number != null) {
                val msgText = extractMessageBody(rawCmd)
                if (lower.contains("sms")) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                        SmsManager.getDefault().sendTextMessage(number, null, msgText, null, null)
                        replyAndSpeak("$number ko SMS bhej diya gaya hai.")
                    } else {
                        val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$number")).apply {
                            putExtra("sms_body", msgText)
                        }
                        startActivity(smsIntent)
                        replyAndSpeak("SMS app khol raha hoon.")
                    }
                } else {
                    val waNumber = normalizeIndianPhone(number)
                    val url = "https://wa.me/$waNumber?text=${URLEncoder.encode(msgText, "UTF-8")}"
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    replyAndSpeak("WhatsApp par message khol raha hoon.")
                }
            } else {
                replyAndSpeak("Message bhejne ke liye contact ka naam ya number bolein.")
            }
            return
        }

        // 3. PLAY VIDEO (YOUTUBE)
        if (lower.contains("video") || lower.contains("play") || lower.contains("youtube") || lower.contains("chalao") || lower.contains("वीडियो")) {
            val query = rawCmd
                .replace(Regex("(?i)\b(play|video|youtube|par|pe|chalao|karo|dikhao)\b"), "")
                .trim()
                .ifBlank { "trending" }
            val ytUrl = "https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)))
            replyAndSpeak("YouTube par $query chala raha hoon.")
            return
        }

        // 4. TIME
        if (lower.contains("time") || lower.contains("samay") || lower.contains("समय")) {
            val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            replyAndSpeak("Abhi samay $now hai.")
            return
        }

        // 5. GOOGLE SEARCH
        val searchUrl = "https://www.google.com/search?q=${URLEncoder.encode(rawCmd, "UTF-8")}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)))
        replyAndSpeak("Google par search kar raha hoon.")
    }

    private fun findPhoneNumber(command: String): String? {
        val digitMatch = Regex("(\+?\d[\d\s-]{8,14}\d)").find(command)
        if (digitMatch != null) {
            return digitMatch.value.replace(Regex("[\s-]"), "")
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val lowerCmd = command.lowercase(Locale.getDefault())
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)?.lowercase(Locale.getDefault()) ?: continue
                val num = cursor.getString(numIdx)?.replace(Regex("[\s-]"), "") ?: continue
                if (lowerCmd.contains(name)) return num
            }
        }
        return null
    }

    private fun extractMessageBody(command: String): String {
        val cleaned = command
            .replace(Regex("(?i)\b(whatsapp|message|msg|sms|bhejo|send|karo|ko)\b"), "")
            .trim()
        return cleaned.ifBlank { "Hello!" }
    }

    private fun normalizeIndianPhone(phone: String): String {
        val digits = phone.replace(Regex("\D"), "")
        return if (digits.length == 10) "91$digits" else digits
    }

    private fun replyAndSpeak(text: String) {
        tvReply.text = "AI: $text"
        tvStatus.text = "Ready"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply_id")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
