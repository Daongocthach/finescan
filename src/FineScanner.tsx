import { requireNativeViewManager } from 'expo-modules-core';
import React from 'react';

import type { FineScanResult, FineScannerProps } from './FineScan.types';

type NativeScanEvent = {
  nativeEvent: FineScanResult;
};

type NativeProps = Omit<FineScannerProps, 'onScan'> & {
  onScan?: (event: NativeScanEvent) => void;
};

const NativeFineScanner = requireNativeViewManager<NativeProps>('FineScan');

export function FineScanner({
  formats = ['qr', 'data-matrix'],
  region = { width: 0.75, height: 0.35 },
  duplicateDelay = 1500,
  torch = false,
  paused = false,
  onScan,
  ...viewProps
}: FineScannerProps) {
  return (
    <NativeFineScanner
      {...viewProps}
      formats={formats}
      region={region}
      duplicateDelay={duplicateDelay}
      torch={torch}
      paused={paused}
      onScan={onScan ? (event) => onScan(event.nativeEvent) : undefined}
    />
  );
}
