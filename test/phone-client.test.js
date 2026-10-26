import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { once } from 'node:events';
import { PassThrough } from 'node:stream';
import net from 'node:net';
import { PhoneClient, parseAddress } from '../desktop/phone-client.js';
const exec = promisify(execFile);
async function fixture(t, handler) {
  const dir = await mkdtemp(join(tmpdir(), 'micbridge-phone-'));
  const sockets = new Set(); let requests = 0;
  const server = net.createServer(socket => {
    sockets.add(socket); socket.on('close', () => sockets.delete(socket)); socket.on('error', () => {});
    socket.once('data', data => { requests++; assert.equal(data.toString(), 'MICBRIDGE/2\n'); handler(socket); });
  });
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
test('connects over plain TCP, waits for consent, and forwards PCM', { timeout: 10000 }, async t => {
  let allow;
  const f = await fixture(t, socket => { socket.write('WAIT\n'); allow = () => socket.write(Buffer.concat([Buffer.from('OK 48000 1 s16le\n'), Buffer.from([10, 20, 30, 40])])); });
  assert.equal(f.requests, 0);
  const approving = state(f.client, 'approving'); await f.client.connect(f.address); await approving; assert.equal(f.opens, 0);
  const streaming = state(f.client, 'streaming'); allow(); await streaming;
  assert.deepEqual(Buffer.concat(f.chunks), Buffer.from([10, 20, 30, 40]));
});
test('refusal on the phone never opens the audio device', { timeout: 10000 }, async t => {
  const f = await fixture(t, socket => socket.end('WAIT\nDENIED\n'));
  const error = state(f.client, 'error');
  await f.client.connect(f.address); assert.match((await error).message, /recusada/); assert.equal(f.opens, 0);
});
test('rejects unsupported audio response', { timeout: 10000 }, async t => {
  const f = await fixture(t, socket => socket.end('OK 44100 1 s16le\n'));
  const error = state(f.client, 'error');
  await f.client.connect(f.address); assert.match((await error).message, /44100/); assert.equal(f.opens, 0);
});
test('disconnect during consent closes the connection without starting capture', { timeout: 10000 }, async t => {
  let peer;
  const f = await fixture(t, socket => { peer = socket; socket.write('WAIT\n'); });
  const approving = state(f.client, 'approving');
  await f.client.connect(f.address); await approving;
  const closed = once(peer, 'close'); f.client.disconnect(); await closed; assert.equal(f.opens, 0);
});
