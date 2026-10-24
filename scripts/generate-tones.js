// Original, short PCM tones for TimeLock. Run with `node scripts/generate-tones.js`.
const fs = require('node:fs');
const path = require('node:path');

const sampleRate = 22050;
const outputDir = path.join(__dirname, '..', 'app', 'src', 'main', 'res', 'raw');
const tones = [
  { name: 'reminder_bright', peak: 0.83, length: 0.54, notes: [[0.00, 0.22, 920], [0.28, 0.22, 1180]] },
  { name: 'reminder_soft', peak: 0.78, length: 0.66, notes: [[0.00, 0.30, 620], [0.35, 0.27, 820]] },
  { name: 'reminder_high_low', peak: 0.83, length: 0.65, notes: [[0.00, 0.26, 1350], [0.32, 0.29, 780]] },
  { name: 'reminder_triple', peak: 0.82, length: 0.68, notes: [[0.00, 0.17, 900], [0.25, 0.17, 1100], [0.50, 0.16, 1320]] },
];

fs.mkdirSync(outputDir, { recursive: true });
for (const tone of tones) {
  const count = Math.ceil(tone.length * sampleRate);
  const samples = new Float64Array(count);
  let rawPeak = 0;
  for (let index = 0; index < count; index++) {
    const time = index / sampleRate;
    for (const [start, duration, frequency] of tone.notes) {
      const local = time - start;
      if (local < 0 || local >= duration) continue;
      const envelope = Math.min(1, local / 0.012, (duration - local) / 0.028);
      const fundamental = Math.sin(2 * Math.PI * frequency * local);
      const harmonic = 0.11 * Math.sin(4 * Math.PI * frequency * local);
      samples[index] += Math.max(0, envelope) * (fundamental + harmonic);
    }
    rawPeak = Math.max(rawPeak, Math.abs(samples[index]));
  }
  const data = Buffer.alloc(44 + count * 2);
  data.write('RIFF', 0);
  data.writeUInt32LE(36 + count * 2, 4);
  data.write('WAVEfmt ', 8);
  data.writeUInt32LE(16, 16);
  data.writeUInt16LE(1, 20);
  data.writeUInt16LE(1, 22);
  data.writeUInt32LE(sampleRate, 24);
  data.writeUInt32LE(sampleRate * 2, 28);
  data.writeUInt16LE(2, 32);
  data.writeUInt16LE(16, 34);
  data.write('data', 36);
  data.writeUInt32LE(count * 2, 40);
  for (let index = 0; index < count; index++) {
    const normalized = samples[index] * tone.peak / rawPeak;
    data.writeInt16LE(Math.round(normalized * 32767), 44 + index * 2);
  }
  const destination = path.join(outputDir, `${tone.name}.wav`);
  fs.writeFileSync(destination, data);
  console.log(`${tone.name}: ${tone.length}s, peak ${tone.peak}, ${data.length} bytes`);
}
