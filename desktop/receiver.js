import tls from 'node:tls';
import { randomInt, timingSafeEqual, X509Certificate } from 'node:crypto';
import { EventEmitter } from 'node:events';

export class Receiver extends EventEmitter {
  constructor({ audio, key, cert, code = String(randomInt(100000, 1000000)) }) {
    super(); this.audio = audio; this.code = code; this.key = key; this.cert = cert;
    this.fingerprint = new X509Certificate(cert).fingerprint256;
    this.sockets = new Set(); this.active = null; this.failures = new Map(); this.stopping = false;
  }
  async start(port = 47321, host = '0.0.0.0') {
    await this.audio.prepare();
    this.server = tls.createServer({ key: this.key, cert: this.cert, minVersion: 'TLSv1.2', handshakeTimeout: 5000 }, socket => this.accept(socket));
    this.server.on('tlsClientError', () => {});
    this.server.on('error', error => this.emit('status', { state: 'error', message: error.message }));
    try {
      await new Promise((resolve, reject) => { this.server.once('error', reject); this.server.listen(port, host, resolve); });
    } catch (error) { await this.audio.close(); throw error; }
    return this.server.address().port;
  }
  accept(socket) {
    this.sockets.add(socket); socket.on('error', () => {});
    const remote = socket.remoteAddress;
    const previous = this.failures.get(remote);
    if (previous && previous.until > Date.now() && previous.count >= 5) { socket.destroy(); return; }
    socket.setTimeout(10000, () => socket.destroy());
    let header = Buffer.alloc(0), stream = null, pending = false;
    const end = () => {
      this.sockets.delete(socket); stream?.stop();
      if (this.active === socket) { this.active = null; this.emit('status', { state: 'waiting', message: 'Aguardando o celular' }); }
    };
    socket.once('close', end);
    const write = data => {
      if (!stream || !data.length) return;
      if (!stream.input.write(data)) { socket.pause(); stream.input.once('drain', () => { if (!socket.destroyed) socket.resume(); }); }
    };
    socket.on('data', async data => {
      if (stream) { write(data); return; }
      if (pending) { socket.destroy(); return; }
      header = Buffer.concat([header, data]);
      const newline = header.indexOf(10);
      if (newline < 0) { if (header.length > 1024) socket.destroy(); return; }
      if (newline > 1024) { socket.destroy(); return; }
      let request;
      try { request = JSON.parse(header.subarray(0, newline)); } catch { socket.end('ERROR invalid-header\n'); return; }
      const supplied = Buffer.from(String(request.code ?? ''));
      const expected = Buffer.from(this.code);
      if (supplied.length !== expected.length || !timingSafeEqual(supplied, expected)) {
        const entry = previous && previous.until > Date.now() ? previous : { count: 0, until: Date.now() + 60000 };
        entry.count++; if (this.failures.size >= 256) this.failures.clear(); this.failures.set(remote, entry);
        socket.end('ERROR wrong-code\n'); return;
      }
      if (request.rate !== 48000 || request.channels !== 1 || request.format !== 's16le') { socket.end('ERROR unsupported-format\n'); return; }
      if (this.active) { socket.end('ERROR busy\n'); return; }
      this.active = socket; pending = true; socket.pause();
      try {
        stream = await this.audio.open();
        if (socket.destroyed || this.stopping) { stream.stop(); return; }
        stream.onExit?.((code, details) => {
          if (!socket.destroyed) { this.emit('status', { state: 'error', message: `Saída de áudio interrompida (${code}): ${details}` }); socket.destroy(); }
        });
        socket.write('OK\n');
        this.emit('status', { state: 'streaming', message: 'Microfone do celular conectado' });
        write(header.subarray(newline + 1)); header = null; socket.resume();
      } catch (error) { this.emit('status', { state: 'error', message: error.message }); socket.end('ERROR audio-unavailable\n'); }
    });
  }
  disconnect() { this.active?.destroy(); }
  async stop() {
    this.stopping = true;
    for (const socket of this.sockets) socket.destroy();
    if (this.server) await new Promise(resolve => this.server.close(resolve));
    await this.audio.close();
  }
}
