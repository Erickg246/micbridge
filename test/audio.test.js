import test from 'node:test';
import assert from 'node:assert/strict';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { setTimeout as delay } from 'node:timers/promises';
import { LinuxAudio } from '../desktop/audio.js';
const exec = promisify(execFile);

test('Linux virtual microphone is visible and delivers a real 440 Hz PCM signal', { skip: process.env.MICBRIDGE_AUDIO_TEST !== '1', timeout: 15000 }, async t => {
  const audio = new LinuxAudio(); await audio.prepare();
  t.after(() => audio.close());
  const source = `${audio.sink}_mic`;
  const { stdout } = await exec('pactl', ['list', 'sources', 'short']);
  assert.ok(stdout.includes(source), 'virtual recording device must be listed');
  const recording = spawn('parec', ['--device=' + source, '--raw', '--format=s16le', '--rate=48000', '--channels=1', '--latency-msec=20'], { stdio: ['ignore', 'pipe', 'pipe'] });
  t.after(() => recording.kill());
  const chunks = []; recording.stdout.on('data', chunk => chunks.push(chunk));
  let errors = ''; recording.stderr.on('data', chunk => { errors += chunk; });
  const player = await audio.open(); t.after(() => player.stop());
  await delay(100);
  for (let frame = 0; frame < 170; frame++) {
    const pcm = Buffer.alloc(1920);
    for (let i = 0; i < 960; i++) pcm.writeInt16LE(Math.round(Math.sin(2 * Math.PI * 440 * (frame * 960 + i) / 48000) * 12000), i * 2);
    player.input.write(pcm); await delay(20);
  }
  await delay(750);
  const data = Buffer.concat(chunks);
  assert.ok(data.length > 48000, `expected recorded PCM, got ${data.length} bytes. ${errors}`);
  const samples = []; for (let i = 0; i + 1 < data.length; i += 2) samples.push(data.readInt16LE(i));
  const rms = Math.sqrt(samples.reduce((sum, s) => sum + s * s, 0) / samples.length);
  assert.ok(rms > 1000, `audio must reach the virtual mic, RMS=${rms}`);
  let crossings = 0, voiced = 0;
  for (let i = 1; i < samples.length; i++) { if (Math.abs(samples[i]) > 100) voiced++; if (samples[i-1] < 0 && samples[i] >= 0) crossings++; }
  const frequency = crossings / (voiced / 48000);
  assert.ok(frequency > 420 && frequency < 470, `expected 440 Hz tone, got ${frequency} Hz`);
});
