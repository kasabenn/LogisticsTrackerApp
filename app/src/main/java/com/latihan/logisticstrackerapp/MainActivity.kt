package com.latihan.logisticstrackerapp

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    // 1. Deklarasi Elemen Antarmuka Pengguna (camelCase tanpa singkatan)
    private lateinit var editTextTrackingNumber: EditText
    private lateinit var buttonTrack: Button
    private lateinit var progressBarLoading: ProgressBar
    private lateinit var cardResult: CardView
    private lateinit var textViewErrorMessage: TextView

    // Elemen Informasi Paket Kiriman
    private lateinit var textViewResiTitle: TextView
    private lateinit var textViewStatusBadge: TextView
    private lateinit var textViewCourier: TextView
    private lateinit var textViewLocation: TextView
    private lateinit var textViewRecipient: TextView
    private lateinit var textViewEta: TextView

    // Tombol Uji Coba Cepat (Quick Sample Buttons)
    private var btnQuickResi1: Button? = null
    private var btnQuickResi2: Button? = null
    private var btnQuickResi3: Button? = null
    private var btnQuickResi4: Button? = null
    private var btnQuickResi5: Button? = null
    private var btnQuickResi404: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 2. Inisialisasi Seluruh Elemen Tampilan UI
        editTextTrackingNumber = findViewById(R.id.editTextTrackingNumber)
        buttonTrack = findViewById(R.id.buttonTrack)
        progressBarLoading = findViewById(R.id.progressBarLoading)
        cardResult = findViewById(R.id.cardResult)
        textViewErrorMessage = findViewById(R.id.textViewErrorMessage)

        textViewResiTitle = findViewById(R.id.textViewResiTitle)
        textViewStatusBadge = findViewById(R.id.textViewStatusBadge) // Inisialisasi wajib agar tidak crash
        textViewCourier = findViewById(R.id.textViewCourier)
        textViewLocation = findViewById(R.id.textViewLocation)
        textViewRecipient = findViewById(R.id.textViewRecipient)
        textViewEta = findViewById(R.id.textViewEta)

        // Inisialisasi tombol cepat pengujian jika tersedia di layout
        btnQuickResi1 = findViewById(R.id.btnQuickResi1)
        btnQuickResi2 = findViewById(R.id.btnQuickResi2)
        btnQuickResi3 = findViewById(R.id.btnQuickResi3)
        btnQuickResi4 = findViewById(R.id.btnQuickResi4)
        btnQuickResi5 = findViewById(R.id.btnQuickResi5)
        btnQuickResi404 = findViewById(R.id.btnQuickResi404)

        setupQuickButtons()

        // 3. Aksi Tombol Lacak
        buttonTrack.setOnClickListener {
            val nomorResi = editTextTrackingNumber.text.toString().trim()
            if (nomorResi.isNotEmpty()) {
                prosesLacak(nomorResi)
            } else {
                Toast.makeText(
                    this,
                    "Silakan ketik nomor resi terlebih dahulu (contoh: EXP-8801)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupQuickButtons() {
        val quickMap = mapOf(
            btnQuickResi1 to "EXP-8801",
            btnQuickResi2 to "EXP-8802",
            btnQuickResi3 to "EXP-8803",
            btnQuickResi4 to "EXP-8804",
            btnQuickResi5 to "EXP-8805",
            btnQuickResi404 to "EXP-9999"
        )

        for ((button, resi) in quickMap) {
            button?.setOnClickListener {
                editTextTrackingNumber.setText(resi)
                editTextTrackingNumber.setSelection(resi.length)
                prosesLacak(resi)
            }
        }
    }

    // Fungsi Tugas Tambahan Modul: Deteksi Konektivitas Jaringan Sebelum Request
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun prosesLacak(nomorResi: String) {
        // Validasi Tugas 1: Cek apakah perangkat offline sebelum mengirim request
        if (!isNetworkAvailable()) {
            progressBarLoading.visibility = View.GONE
            cardResult.visibility = View.GONE
            textViewErrorMessage.text = "Perangkat Offline - Periksa Sambungan Jaringan Anda"
            textViewErrorMessage.visibility = View.VISIBLE
            Toast.makeText(this, "Perangkat Offline - Periksa Sambungan Jaringan Anda", Toast.LENGTH_LONG).show()
            return
        }

        lacakPaket(nomorResi)
    }

    private fun lacakPaket(nomorResi: String) {
        // STATE 1: LOADING STATE (Tampilkan ProgressBar, sembunyikan Card & Pesan Error)
        progressBarLoading.visibility = View.VISIBLE
        cardResult.visibility = View.GONE
        textViewErrorMessage.visibility = View.GONE
        buttonTrack.isEnabled = false

        // Eksekusi Panggilan Jaringan di Background Thread via Coroutine
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Memanggil Retrofit Service
                val response = ApiClient.apiService.getTrackingDetail(nomorResi)

                // Kembali ke Main UI Thread untuk memperbarui tampilan visual
                withContext(Dispatchers.Main) {
                    progressBarLoading.visibility = View.GONE
                    buttonTrack.isEnabled = true

                    if (response.isSuccessful && response.body() != null) {
                        // STATE 2: SUCCESS STATE (Data berhasil ditemukan)
                        val dataPaket = response.body()!!
                        tampilkanDataPaket(dataPaket)
                    } else {
                        // STATE 3: SERVER ERROR STATE (Misal 404 Resi Tidak Ditemukan)
                        textViewErrorMessage.text =
                            "Nomor resi [$nomorResi] tidak ditemukan pada database server (HTTP ${response.code()})"
                        textViewErrorMessage.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                // STATE 4: NETWORK FAILURE STATE (Koneksi mati / DNS Error / Timeout)
                withContext(Dispatchers.Main) {
                    progressBarLoading.visibility = View.GONE
                    buttonTrack.isEnabled = true
                    textViewErrorMessage.text =
                        "Koneksi internet bermasalah: ${e.localizedMessage ?: "Gagal terhubung ke server"}"
                    textViewErrorMessage.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun tampilkanDataPaket(dataPaket: TrackingResponse) {
        cardResult.visibility = View.VISIBLE
        textViewResiTitle.text = "Resi: ${dataPaket.trackingNumber}"
        textViewCourier.text = "Kurir: ${dataPaket.serviceType} (${dataPaket.courierName})"
        textViewLocation.text = "Posisi Terakhir: ${dataPaket.lastLocation}"
        textViewRecipient.text = "Penerima: ${dataPaket.recipientName}"
        textViewEta.text = "Estimasi Tiba: ${dataPaket.estimatedDelivery}"

        // Pengaturan Badge Status Secara Dinamis
        textViewStatusBadge.text = dataPaket.status
        when (dataPaket.status.uppercase()) {
            "DELIVERED" -> {
                textViewStatusBadge.setBackgroundColor(Color.parseColor("#DCFCE7")) // Latar Hijau Muda
                textViewStatusBadge.setTextColor(Color.parseColor("#15803D"))       // Teks Hijau Tua
            }
            "IN_TRANSIT" -> {
                textViewStatusBadge.setBackgroundColor(Color.parseColor("#FEF3C7")) // Latar Kuning Amber
                textViewStatusBadge.setTextColor(Color.parseColor("#B45309"))       // Teks Cokelat Oranye
            }
            else -> {
                textViewStatusBadge.setBackgroundColor(Color.parseColor("#F1F5F9")) // Latar Abu Netral
                textViewStatusBadge.setTextColor(Color.parseColor("#475569"))       // Teks Abu Gelap
            }
        }
    }
}
