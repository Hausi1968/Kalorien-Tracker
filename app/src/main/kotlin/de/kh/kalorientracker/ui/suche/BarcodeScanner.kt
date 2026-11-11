package de.kh.kalorientracker.ui.suche

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Barcode-Scanner über Google Play-Dienste: öffnet eine fertige Kamera-Ansicht,
 * braucht keine Kamera-Berechtigung in der App und kein eigenes Kamerabild.
 */
fun starteBarcodeScan(
    context: Context,
    onCode: (String) -> Unit,
    onAbbruch: () -> Unit,
    onFehler: () -> Unit,
) {
    val optionen = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, optionen)
        .startScan()
        .addOnSuccessListener { b ->
            val code = b.rawValue?.filter { it.isDigit() }
            if (code.isNullOrEmpty()) onFehler() else onCode(code)
        }
        .addOnCanceledListener { onAbbruch() }
        .addOnFailureListener { onFehler() }
}
