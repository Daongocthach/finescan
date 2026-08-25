package expo.modules.finescan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.util.Size
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FineScanView(context: android.content.Context, appContext: AppContext) : ExpoView(context, appContext) {
  private val previewView = PreviewView(context).apply {
    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    scaleType = PreviewView.ScaleType.FILL_CENTER
  }
  private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
  private val processing = AtomicBoolean(false)
  private val onScan by EventDispatcher()

  private var camera: Camera? = null
  private var cameraProvider: ProcessCameraProvider? = null
  private val cameraBinding = AtomicBoolean(false)
  private val cameraBound = AtomicBoolean(false)
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
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    bindCamera()
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
    if (cameraBound.get() || !cameraBinding.compareAndSet(false, true)) return
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      cameraBinding.set(false)
      return
    }

    val owner = appContext.currentActivity as? LifecycleOwner
    if (owner == null) {
      cameraBinding.set(false)
      return
    }

    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
      try {
        val provider = providerFuture.get()
        cameraProvider = provider

        val preview = Preview.Builder().build().also {
          it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
          .setTargetResolution(Size(1280, 720))
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()

        analysis.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }

        provider.unbindAll()
        camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        camera?.cameraControl?.enableTorch(torchEnabled)
        cameraBound.set(true)
      } finally {
        cameraBinding.set(false)
      }
    }, ContextCompat.getMainExecutor(context))
  }

  private fun analyze(proxy: ImageProxy) {
    if (paused || processing.getAndSet(true)) {
      proxy.close()
      return
    }

    try {
      val rotation = proxy.imageInfo.rotationDegrees
      val crop = centerRoiForSensor(proxy.width, proxy.height, rotation)
      val nv21 = cropToNv21(proxy, crop)
      val image = InputImage.fromByteArray(
        nv21,
        crop.width(),
        crop.height(),
        rotation,
        InputImage.IMAGE_FORMAT_NV21
      )

      scanner.process(image)
        .addOnSuccessListener { barcodes -> barcodes.firstOrNull()?.let(::emitIfAllowed) }
        .addOnCompleteListener {
          processing.set(false)
          proxy.close()
        }
    } catch (_: Throwable) {
      processing.set(false)
      proxy.close()
    }
  }

  /**
   * The public ROI is expressed in display orientation. CameraX gives us sensor-oriented
   * buffers, so width/height fractions swap when the frame will rotate by 90/270 degrees.
   */
  private fun centerRoiForSensor(width: Int, height: Int, rotation: Int): Rect {
    val rotated = rotation == 90 || rotation == 270
    val sensorWidthFraction = if (rotated) regionHeight else regionWidth
    val sensorHeightFraction = if (rotated) regionWidth else regionHeight

    var roiW = (width * sensorWidthFraction).toInt().coerceAtLeast(2)
    var roiH = (height * sensorHeightFraction).toInt().coerceAtLeast(2)

    // YUV420 chroma samples are 2x2, so keep crop origin and dimensions even.
    roiW = (roiW and 1.inv()).coerceAtMost(width and 1.inv())
    roiH = (roiH and 1.inv()).coerceAtMost(height and 1.inv())
    val left = (((width - roiW) / 2) and 1.inv()).coerceAtLeast(0)
    val top = (((height - roiH) / 2) and 1.inv()).coerceAtLeast(0)
    return Rect(left, top, left + roiW, top + roiH)
  }

  /** Converts only the selected YUV_420_888 ROI to NV21; the decoder never sees the full frame. */
  private fun cropToNv21(proxy: ImageProxy, crop: Rect): ByteArray {
    require(proxy.format == ImageFormat.YUV_420_888) { "FineScan expects YUV_420_888 frames" }
    val planes = proxy.planes
    require(planes.size >= 3) { "FineScan requires Y/U/V planes" }

    val width = crop.width()
    val height = crop.height()
    val output = ByteArray(width * height + width * height / 2)
    var out = 0

    copyPlaneRegion(
      plane = planes[0],
      startX = crop.left,
      startY = crop.top,
      width = width,
      height = height,
      output = output,
      outputOffset = out
    )
    out += width * height

    val chromaWidth = width / 2
    val chromaHeight = height / 2
    val chromaX = crop.left / 2
    val chromaY = crop.top / 2
    val u = planes[1]
    val v = planes[2]
    val uBuffer = u.buffer.duplicate()
    val vBuffer = v.buffer.duplicate()

    for (y in 0 until chromaHeight) {
      val uRow = (chromaY + y) * u.rowStride
      val vRow = (chromaY + y) * v.rowStride
      for (x in 0 until chromaWidth) {
        val uIndex = uRow + (chromaX + x) * u.pixelStride
        val vIndex = vRow + (chromaX + x) * v.pixelStride
        output[out++] = vBuffer.get(vIndex)
        output[out++] = uBuffer.get(uIndex)
      }
    }

    return output
  }

  private fun copyPlaneRegion(
    plane: ImageProxy.PlaneProxy,
    startX: Int,
    startY: Int,
    width: Int,
    height: Int,
    output: ByteArray,
    outputOffset: Int
  ) {
    val buffer: ByteBuffer = plane.buffer.duplicate()
    var out = outputOffset
    for (y in 0 until height) {
      val rowStart = (startY + y) * plane.rowStride + startX * plane.pixelStride
      for (x in 0 until width) {
        output[out++] = buffer.get(rowStart + x * plane.pixelStride)
      }
    }
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
    cameraProvider?.unbindAll()
    cameraProvider = null
    camera = null
    cameraBound.set(false)
    cameraBinding.set(false)
    super.onDetachedFromWindow()
  }
}
