import type { ViewProps } from 'react-native';

export type FineScanFormat = 'qr' | 'data-matrix';

export type FineScanRegion = {
  /** Fraction of preview width, from 0 to 1. */
  width: number;
  /** Fraction of preview height, from 0 to 1. */
  height: number;
};

export type FineScanResult = {
  value: string;
  format: FineScanFormat;
  timestamp: number;
};

export type FineScannerProps = ViewProps & {
  formats?: FineScanFormat[];
  region?: FineScanRegion;
  duplicateDelay?: number;
  torch?: boolean;
  paused?: boolean;
  onScan?: (result: FineScanResult) => void;
};
