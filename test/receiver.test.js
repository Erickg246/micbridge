import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import tls from 'node:tls';
import { PassThrough } from 'node:stream';
import { once } from 'node:events';
import { Receiver } from '../desktop/receiver.js';

async function setup(t) {
  const dir = await mkdtemp(join(tmpdir(), 'micbridge-test-'));
  execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', join(dir, 'key'), '-out', join(dir, 'cert'), '-days', '1', '-subj', '/CN=localhost'], { stdio: 'ignore' });
  const cert = await readFile(join(dir, 'cert')), key = await readFile(join(dir, 'key'));
  const chunks = []; let stopped = 0, opened = 0;
  const audio = { async prepare() {}, async close() {}, async open() { opened++; const input = new PassThrough(); input.on('data', b => chunks.push(b)); return { input, stop() { stopped++; input.end(); } }; } };
  const receiver = new Receiver({ audio, key, cert, code: '123456' });
  const port = await receiver.start(0, '127.0.0.1');
  t.after(async () => { await receiver.stop(); await rm(dir, { recursive: true, force: true }); });
  async function client() { const socket = tls.connect({ port, host: '127.0.0.1', servername: 'localhost', ca: cert }); socket.on('error', () => {}); await once(socket, 'secureConnect'); return socket; }
  return { receiver, client, chunks, get stopped() { return stopped; }, get opened() { return opened; } };
}
const header = code => JSON.stringify({ code, rate: 48000, channels: 1, format: 's16le' }) + '\n';
async function reply(socket, data) { const next = once(socket, 'data'); socket.write(data); return (await next)[0].toString(); }

test('authenticates TLS, accepts fragmented header and forwards actual PCM unchanged', { timeout: 10000 }, async t => {
  const f = await setup(t), client = await f.client();
  client.write(header('123456').slice(0, 8));
  assert.equal(await reply(client, header('123456').slice(8)), 'OK\n');
  const pcm = Buffer.alloc(1920); for (let i = 0; i < pcm.length; i++) pcm[i] = i % 256;
  const received = new Promise(resolve => { const check = () => { if (Buffer.concat(f.chunks).length === pcm.length) resolve(); else setTimeout(check, 5); }; check(); });
  client.write(pcm); await received;
  assert.deepEqual(Buffer.concat(f.chunks), pcm);
  const closed = once(client, 'close'); const waiting = once(f.receiver, 'status'); f.receiver.disconnect(); await Promise.all([closed, waiting]);
  assert.equal(f.stopped, 1);
});

test('rejects wrong pairing code without opening audio', { timeout: 10000 }, async t => {
  const f = await setup(t), client = await f.client();
  assert.equal(await reply(client, header('000000')), 'ERROR wrong-code\n');
  assert.equal(f.opened, 0); client.destroy();
});

test('rejects unsupported formats and second sender', { timeout: 10000 }, async t => {
  const f = await setup(t), bad = await f.client();
  assert.equal(await reply(bad, header('123456').replace('48000', '44100')), 'ERROR unsupported-format\n'); bad.destroy();
  const first = await f.client(); assert.equal(await reply(first, header('123456')), 'OK\n');
  const second = await f.client(); assert.equal(await reply(second, header('123456')), 'ERROR busy\n'); second.destroy(); first.destroy();
});

test('closes oversized headers and reports unavailable audio', { timeout: 10000 }, async t => {
  const f = await setup(t), bad = await f.client();
  const closed = once(bad, 'close'); bad.write('x'.repeat(1025)); await closed;
  f.receiver.audio.open = async () => { throw new Error('audio failed'); };
  const client = await f.client(); assert.equal(await reply(client, header('123456')), 'ERROR audio-unavailable\n'); client.destroy();
});
