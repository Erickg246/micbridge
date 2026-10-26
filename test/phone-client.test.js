import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { once } from 'node:events';
import { PassThrough } from 'node:stream';
import tls from 'node:tls';
import { PhoneClient, discoverPhone, parseAddress } from '../desktop/phone-client.js';
const exec = promisify(execFile);
async function fixture(t, handler) {
  const dir = await mkdtemp(join(tmpdir(), 'micbridge-phone-'));
  await exec('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', join(dir, 'key'), '-out', join(dir, 'cert'), '-days', '1', '-subj', '/CN=MicBridge Android']);
  const cert = await readFile(join(dir, 'cert')), key = await readFile(join(dir, 'key'));
  const sockets = new Set(); let requests = 0;
  const server = tls.createServer({ key, cert, minVersion: 'TLSv1.2' }, socket => {
    sockets.add(socket); socket.on('close', () => sockets.delete(socket)); socket.on('error', () => {});
    socket.once('data', data => { requests++; assert.equal(data.toString(), 'MICBRIDGE/2\n'); handler(socket); });
  });
  server.on('tlsClientError', () => {});
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const chunks = []; let opens = 0;
  const audio = { async prepare() {}, async close() {}, async open() { opens++; const input = new PassThrough(); input.on('data', b => chunks.push(b)); return { input, stop: () => input.end() }; } };
  const client = new PhoneClient(audio); await client.prepare();
  const address = `127.0.0.1:${server.address().port}`;
  t.after(async () => { await client.stop(); for (const socket of sockets) socket.destroy(); await new Promise(resolve => server.close(resolve)); await rm(dir, { recursive: true, force: true }); });
  return { client, address, chunks, get opens() { return opens; }, get requests() { return requests; } };
}
function state(client, desired) {
  return new Promise(resolve => { const listener = value => { if (value.state === desired) { client.off('status', listener); resolve(value); } }; client.on('status', listener); });
}
test('accepts a phone IP with optional port and rejects malformed addresses', () => {
  assert.equal(parseAddress('192.168.1.10').port, 47321);
  assert.equal(parseAddress('192.168.1.10:1234').port, 1234);
  for (const bad of ['', 'https://phone', 'phone:0', 'phone:65536', 'phone:12:13', 'a b']) assert.throws(() => parseAddress(bad));
});
test('discovers certificate without requesting audio, waits for consent, and forwards PCM', { timeout: 10000 }, async t => {
  let allow;
  const f = await fixture(t, socket => { socket.write('WAIT\n'); allow = () => socket.write(Buffer.concat([Buffer.from('OK 48000 1 s16le\n'), Buffer.from([10, 20, 30, 40])])); });
  const identity = await discoverPhone(f.address); assert.match(identity.fingerprint, /^[0-9A-F:]{95}$/); assert.equal(f.requests, 0);
  const approving = state(f.client, 'approving'); await f.client.connect(f.address, identity); await approving; assert.equal(f.opens, 0);
  const streaming = state(f.client, 'streaming'); allow(); await streaming;
  assert.deepEqual(Buffer.concat(f.chunks), Buffer.from([10, 20, 30, 40]));
});
test('refusal on the phone never opens the audio device', { timeout: 10000 }, async t => {
  const f = await fixture(t, socket => socket.end('WAIT\nDENIED\n'));
  const identity = await discoverPhone(f.address); const error = state(f.client, 'error');
  await f.client.connect(f.address, identity); assert.match((await error).message, /recusada/); assert.equal(f.opens, 0);
});
test('rejects unsupported audio response', { timeout: 10000 }, async t => {
  const f = await fixture(t, socket => socket.end('OK 44100 1 s16le\n'));
  const identity = await discoverPhone(f.address), error = state(f.client, 'error');
  await f.client.connect(f.address, identity); assert.match((await error).message, /44100/); assert.equal(f.opens, 0);
});
test('rejects a different certificate before sending any microphone request', { timeout: 10000 }, async t => {
  const first = await fixture(t, () => {}), second = await fixture(t, () => {});
  const wrongIdentity = await discoverPhone(first.address); const error = state(second.client, 'error');
  await second.client.connect(second.address, wrongIdentity); await error;
  assert.equal(second.requests, 0); assert.equal(second.opens, 0);
});
test('disconnect during consent closes the connection without starting capture', { timeout: 10000 }, async t => {
  let peer;
  const f = await fixture(t, socket => { peer = socket; socket.write('WAIT\n'); });
  const identity = await discoverPhone(f.address), approving = state(f.client, 'approving');
  await f.client.connect(f.address, identity); await approving;
  const closed = once(peer, 'close'); f.client.disconnect(); await closed; assert.equal(f.opens, 0);
});
