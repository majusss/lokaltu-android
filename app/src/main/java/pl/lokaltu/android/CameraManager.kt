package pl.lokaltu.android

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

class CameraManager(private val bridge: NativeBridge) {

    fun handleCameraResult(bitmap: Bitmap?) {
        if (bitmap == null) {
            bridge.sendCameraError("Nie zrobiono zdjęcia.")
            return
        }
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            bridge.sendCameraResult(base64)
            Log.d("CameraManager", "Camera result sent to web")
        } catch (e: Exception) {
            Log.e("CameraManager", "Compress error", e)
            bridge.sendCameraError("Błąd przetwarzania zdjęcia.")
        }
    }

    fun handleCameraError(error: String) {
        bridge.sendCameraError(error)
    }
}
