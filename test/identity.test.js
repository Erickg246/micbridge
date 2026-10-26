import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { PassThrough } from 'node:stream';
import { Receiver } from '../desktop/receiver.js';
const exec = promisify(execFile);

test('native Android Java TLS code verifies PC identity and authenticates with the actual receiver', { skip: process.env.MICBRIDGE_JAVA_TEST !== '1', timeout: 20000 }, async t => {
  const dir = await mkdtemp(join(tmpdir(), 'micbridge-java-')); t.after(() => rm(dir, { recursive: true, force: true }));
  await exec('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', join(dir, 'key'), '-out', join(dir, 'cert'), '-days', '1', '-subj', '/CN=localhost']);
  const chunks = [];
  const audio = { async prepare() {}, async close() {}, async open() { const input = new PassThrough(); input.on('data', c => chunks.push(c)); return { input, stop: () => input.end() }; } };
  const receiver = new Receiver({ audio, cert: await readFile(join(dir, 'cert')), key: await readFile(join(dir, 'key')), code: '123456' });
  const port = await receiver.start(0, '127.0.0.1'); t.after(() => receiver.stop());
  await exec('javac', ['-d', dir, 'android/app/src/main/java/app/micbridge/Identity.java', 'test/java/app/micbridge/IdentityCheck.java']);
  const { stdout } = await exec('java', ['-cp', dir, 'app.micbridge.IdentityCheck', '127.0.0.1', String(port), receiver.fingerprint]);
  assert.match(stdout, /passed/);
  assert.deepEqual(Buffer.concat(chunks), Buffer.from([1, 2, 3, 4]));
});
