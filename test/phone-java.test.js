import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { PassThrough } from 'node:stream';
import { once } from 'node:events';
import { PhoneClient } from '../desktop/phone-client.js';
const exec = promisify(execFile);
function state(client, desired) { return new Promise(resolve => { const fn = s => { if (s.state === desired) { client.off('status', fn); resolve(s); } }; client.on('status', fn); }); }
test('PC client interoperates with Android Java server protocol over plain TCP, including consent denial', { skip: process.env.MICBRIDGE_JAVA_TEST !== '1', timeout: 20000 }, async t => {
  const dir = await mkdtemp(join(tmpdir(), 'micbridge-java-server-')); t.after(() => rm(dir, { recursive: true, force: true }));
  await exec('javac', ['-d', dir, 'android/app/src/main/java/app/micbridge/PhoneProtocol.java', 'test/java/app/micbridge/PhoneServerCheck.java']);
  const server = spawn('java', ['-cp', dir, 'app.micbridge.PhoneServerCheck']); t.after(() => server.kill());
  let errors = ''; server.stderr.on('data', chunk => { errors += chunk; });
  const finished = once(server, 'exit');
  const port = Number((await once(server.stdout, 'data'))[0].toString().trim()); assert.ok(port > 0);
  const chunks = []; let opens = 0, signalData; const received = new Promise(resolve => { signalData = resolve; });
  const audio = { async prepare() {}, async close() {}, async open() { opens++; const input = new PassThrough(); input.on('data', chunk => { chunks.push(chunk); signalData(); }); return { input, stop: () => input.end() }; } };
  const client = new PhoneClient(audio); t.after(() => client.stop()); await client.prepare();
  const address = `127.0.0.1:${port}`;
  const denied = state(client, 'error'); await client.connect(address); assert.match((await denied).message, /recusada/); assert.equal(opens, 0);
  const streaming = state(client, 'streaming'); await client.connect(address); await streaming; await received;
  assert.deepEqual(Buffer.concat(chunks), Buffer.from([1, 2, 3, 4]));
  assert.equal((await finished)[0], 0, errors);
});
