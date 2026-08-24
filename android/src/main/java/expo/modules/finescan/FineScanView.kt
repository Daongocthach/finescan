package expo.modules.finescan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Size
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FineScanView(context: android.content.Context, appContext: AppContext) : ExpoView(context, appContext) {
  private val previewView = PreviewView(context)
  private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
  private val processing = AtomicBoolean(false)
  private val onScan by EventDispatcher()

  private var camera: Camera? = null
  private var scanner: BarcodeScanner = createScanner(listOf("qr", "data-matrix"))
  private var regionWidth = 0.75
  private var regionHeight = 0.35
  private var paused = false
  private var torchEnabled = false
  private var lastValue: String? = null
  private var lastScanAt = 0L

  var duplicateDelayMs: Long = 1500L

  init {
    addView(previewView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    post { bindCamera() }
  }

  fun setFormats(formats: List<String>) {
    scanner.close()
    scanner = createScanner(formats)
  }

  fun setRegion(width: Double, height: Double) {
    regionWidth = width.coerceIn(0.1, 1.0)
    regionHeight = height.coerceIn(0.1, 1.0)
  }

  fun setTorch(enabled: Boolean) {
    torchEnabled = enabled
    camera?.cameraControl?.enableTorch(enabled)
  }

  fun setPaused(value: Boolean) {
    paused = value
  }

  private fun bindCamera() {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
    val owner = appContext.currentActivity as? LifecycleOwner ?: return
    val providerFuture = ProcessCameraProvider.getInstance(context)

    providerFuture.addListener({
      val provider = providerFuture.get()
      val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
      val analysis = ImageAnalysis.Builder()
        .setTargetResolution(Size(1280, 720))
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

      analysis.setAnalyzer(analysisExecutor) { proxy ->
        if (paused || processing.getAndSet(true)) {
          proxy.close()
          return@setAnalyzer
        }

        val mediaImage = proxy.image
        if (mediaImage == null) {
          processing.set(false)
          proxy.close()
          return@setAnalyzer
        }

        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        scanner.process(image)
          .addOnSuccessListener { barcodes ->
            val crop = centerRoi(image.width, image.height)
            val candidate = barcodes.firstOrNull { barcode ->
              barcode.boundingBox?.let { Rect.intersects(it, crop) } == true
            }
            candidate?.let(::emitIfAllowed)
          }
          .addOnCompleteListener {
            processing.set(false)
            proxy.close()
          }
      }

      provider.unbindAll()
      camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
      camera?.cameraControl?.enableTorch(torchEnabled)
    }, ContextCompat.getMainExecutor(context))
  }

  private fun centerRoi(width: Int, height: Int): Rect {
    val roiW = (width * regionWidth).toInt()
    val roiH = (height * regionHeight).toInt()
    val left = (width - roiW) / 2
    val top = (height - roiH) / 2
    return Rect(left, top, left + roiW, top + roiH)
  }

  private fun emitIfAllowed(barcode: Barcode) {
    val value = barcode.rawValue ?: return
    val now = System.currentTimeMillis()
    if (value == lastValue && now - lastScanAt < duplicateDelayMs) return

    lastValue = value
    lastScanAt = now
    val format = if (barcode.format == Barcode.FORMAT_DATA_MATRIX) "data-matrix" else "qr"
    onScan(mapOf("value" to value, "format" to format, "timestamp" to now.toDouble()))
  }

  private fun createScanner(formats: List<String>): BarcodeScanner {
    var flags = 0
    if (formats.contains("qr")) flags = flags or Barcode.FORMAT_QR_CODE
    if (formats.contains("data-matrix")) flags = flags or Barcode.FORMAT_DATA_MATRIX
    if (flags == 0) flags = Barcode.FORMAT_QR_CODE or Barcode.FORMAT_DATA_MATRIX
    return BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(flags).build())
  }

  override fun onDetachedFromWindow() {
    scanner.close()
    analysisExecutor.shutdown()
    super.onDetachedFromWindow()
  }
}
