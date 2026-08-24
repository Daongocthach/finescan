import ExpoModulesCore

public class FineScanModule: Module {
  public func definition() -> ModuleDefinition {
    Name("FineScan")

    View(FineScanView.self) {
      Events("onScan")
    }
  }
}

final class FineScanView: ExpoView {
  // iOS scanner implementation is intentionally deferred until the
  // Android v0.1 pipeline has been benchmarked and its public API validated.
}
