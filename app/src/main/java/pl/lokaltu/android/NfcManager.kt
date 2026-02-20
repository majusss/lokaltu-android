package pl.lokaltu.android

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.util.Log

class NfcManager(
    private val activity: Activity,
    private val bridge: NativeBridge
) {
    private var nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    var isNfcScanRequested = false
        private set

    fun isNfcAvailable(): Boolean = nfcAdapter != null && nfcAdapter!!.isEnabled

    fun startScan() {
        Log.d("NfcManager", "Starting NFC scan")
        if (!isNfcAvailable()) {
            bridge.sendNfcError("NFC niedostępne lub wyłączone na tym urządzeniu.")
            return
        }
        isNfcScanRequested = true
        enableNfcReader()
    }

    fun stopScan() {
        Log.d("NfcManager", "Stopping NFC scan")
        isNfcScanRequested = false
        disableNfcReader()
    }

    fun onResume() {
        if (isNfcScanRequested) {
            enableNfcReader()
        }
    }

    fun onPause() {
        disableNfcReader()
    }

    private fun enableNfcReader() {
        val adapter = nfcAdapter ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        adapter.enableReaderMode(activity, ::onTagDiscovered, flags, null)
        Log.d("NfcManager", "NFC reader mode enabled")
    }

    private fun disableNfcReader() {
        try {
            nfcAdapter?.disableReaderMode(activity)
        } catch (e: Exception) {
            Log.e("NfcManager", "Error disabling reader mode", e)
        }
        Log.d("NfcManager", "NFC reader mode disabled")
    }

    private fun onTagDiscovered(tag: Tag) {
        val tagIdAsHex = tag.id.joinToString("") { "%02X".format(it) }
        Log.d("NfcManager", "Tag discovered: $tagIdAsHex")

        // Auto-stop scanning after detection
        isNfcScanRequested = false
        disableNfcReader()

        // Try to read NDEF payload
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                val ndefMessage = ndef.ndefMessage
                ndef.close()

                if (ndefMessage != null && ndefMessage.records.isNotEmpty()) {
                    val record = ndefMessage.records[0]
                    val payload = record.payload
                    
                    val textContent = if (record.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN &&
                        record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT)) {
                        val langCodeLen = payload[0].toInt() and 0x3F
                        String(payload, 1 + langCodeLen, payload.size - 1 - langCodeLen, Charsets.UTF_8)
                    } else {
                        String(payload, Charsets.UTF_8).trimStart('\u0000', '\u0002', '\u0003')
                    }
                    bridge.sendNfcResult(tagIdAsHex, textContent)
                    return
                }
            } catch (e: Exception) {
                Log.e("NfcManager", "Error reading NDEF", e)
            }
        }

        // No NDEF or error – just return the raw tag ID
        bridge.sendNfcResult(tagIdAsHex, null)
    }
}
