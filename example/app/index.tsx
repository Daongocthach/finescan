import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { FineScanner, type FineScanResult } from '@finescan/react-native';

export default function ScannerScreen() {
  const [result, setResult] = useState<FineScanResult | null>(null);
  const [torch, setTorch] = useState(false);
  const [paused, setPaused] = useState(false);

  return (
    <View style={styles.container}>
      <StatusBar style="light" />
      <FineScanner
        style={StyleSheet.absoluteFill}
        formats={['qr', 'data-matrix']}
        region={{ width: 0.75, height: 0.35 }}
        duplicateDelay={1500}
        torch={torch}
        paused={paused}
        onScan={setResult}
      />

      <View pointerEvents="none" style={styles.roi} />

      <View style={styles.header}>
        <Text style={styles.title}>FineScan</Text>
        <Text style={styles.subtitle}>QR + Data Matrix · on-device</Text>
      </View>

      <View style={styles.footer}>
        <Text style={styles.result} numberOfLines={2}>
          {result ? `${result.format}: ${result.value}` : 'Place one code inside the frame'}
        </Text>
        <View style={styles.actions}>
          <Pressable style={styles.button} onPress={() => setTorch((value) => !value)}>
            <Text style={styles.buttonText}>{torch ? 'Torch off' : 'Torch on'}</Text>
          </Pressable>
          <Pressable style={styles.button} onPress={() => setPaused((value) => !value)}>
            <Text style={styles.buttonText}>{paused ? 'Resume' : 'Pause'}</Text>
          </Pressable>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  header: { position: 'absolute', top: 64, left: 24, right: 24 },
  title: { color: '#fff', fontSize: 28, fontWeight: '700' },
  subtitle: { color: '#ddd', marginTop: 4 },
  roi: {
    position: 'absolute',
    width: '75%',
    height: '35%',
    left: '12.5%',
    top: '32.5%',
    borderWidth: 2,
    borderColor: '#fff',
    borderRadius: 20,
  },
  footer: { position: 'absolute', left: 20, right: 20, bottom: 48, gap: 14 },
  result: { color: '#fff', backgroundColor: 'rgba(0,0,0,0.65)', padding: 14, borderRadius: 12 },
  actions: { flexDirection: 'row', gap: 12 },
  button: { flex: 1, backgroundColor: 'rgba(0,0,0,0.72)', padding: 14, borderRadius: 12, alignItems: 'center' },
  buttonText: { color: '#fff', fontWeight: '600' },
});
