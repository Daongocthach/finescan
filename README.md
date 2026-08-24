# FineScan

FineScan is an offline-first QR Code and Data Matrix scanner SDK for React Native / Expo.

> Status: `v0.1.0-alpha` — Android-first foundation.

## Goals

- Scan locally on-device; no FineScan API or image upload.
- Optimize for one code inside a region of interest (ROI).
- QR Code + Data Matrix first.
- Stable React Native API while the native engine evolves independently.

## Planned public API

```tsx
import { FineScanner } from '@finescan/react-native';

<FineScanner
  formats={['qr', 'data-matrix']}
  region={{ width: 0.75, height: 0.35 }}
  duplicateDelay={1500}
  torch={false}
  onScan={(result) => console.log(result.value)}
/>
```

## v0.1 architecture

```text
React Native / Expo
       ↓
FineScanner.tsx
       ↓
Expo Native View
       ↓
Android CameraX
       ↓
center ROI crop
       ↓
ML Kit on-device decoder (QR + Data Matrix)
       ↓
deduplicate
       ↓
onScan → JS
```

ML Kit is used only as the initial local decoder. The camera pipeline, ROI policy, lifecycle and JS API belong to FineScan, allowing the decoder to be benchmarked/replaced later without changing app integrations.

## Development milestones

- [x] Package/API skeleton
- [x] Android Expo native module skeleton
- [x] CameraX preview + analysis pipeline
- [x] Center ROI crop before decode
- [x] QR Code + Data Matrix formats
- [x] Duplicate suppression
- [x] Torch prop
- [ ] Build/verify on a physical Android device
- [ ] Benchmark corpus
- [ ] Adaptive preprocessing
- [ ] Smart ROI/focus/zoom
- [ ] iOS implementation

## Important

FineScan contains native code and therefore requires an Expo development build / prebuild. It does not run inside stock Expo Go.

## License

MIT
