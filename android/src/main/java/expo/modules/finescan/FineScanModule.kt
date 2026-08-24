package expo.modules.finescan

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class FineScanModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("FineScan")

    View(FineScanView::class) {
      Events("onScan")

      Prop("formats") { view: FineScanView, formats: List<String>? ->
        view.setFormats(formats ?: listOf("qr", "data-matrix"))
      }

      Prop("region") { view: FineScanView, region: Map<String, Double>? ->
        view.setRegion(
          region?.get("width") ?: 0.75,
          region?.get("height") ?: 0.35
        )
      }

      Prop("duplicateDelay") { view: FineScanView, delay: Int ->
        view.duplicateDelayMs = delay.coerceAtLeast(0).toLong()
      }

      Prop("torch") { view: FineScanView, enabled: Boolean ->
        view.setTorch(enabled)
      }

      Prop("paused") { view: FineScanView, paused: Boolean ->
        view.setPaused(paused)
      }
    }
  }
}
