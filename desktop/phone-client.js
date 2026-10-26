import net from 'node:net';
import { EventEmitter } from 'node:events';

export function parseAddress(value) {
  if (typeof value !== 'string' || value.length > 260) throw new Error('Informe o IP mostrado no celular.');
  const parts = value.trim().split(':');
  const host = parts[0], port = parts.length === 1 ? 47321 : Number(parts[1]);
  if (parts.length > 2 || !/^[a-zA-Z0-9.-]+$/.test(host) || !Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Endereço inválido. Exemplo: 192.168.1.10');
  return { host, port, key: `${host}:${port}` };
}

export class PhoneClient extends EventEmitter {
  constructor(audio) { super(); this.audio = audio; this.socket = null; this.stream = null; this.attempt = 0; }
  async prepare() { await this.audio.prepare(); }
  async connect(address) {
    this.disconnect();
    const generation = ++this.attempt;
    const { host, port } = parseAddress(address);
    this.emit('status', { state: 'connecting', message: 'Conectando ao celular…' });
    const socket = net.createConnection({ host, port });
    this.socket = socket;
    let header = Buffer.alloc(0), phase = 'header', input = null, failed = false;
    socket.setTimeout(70000, () => socket.destroy(new Error('Tempo esgotado. Autorize a conexão na tela do celular.')));
    const fail = error => { failed = true; if (generation === this.attempt) this.emit('status', { state: 'error', message: error.message }); };
    socket.on('error', fail);
    socket.once('close', () => {
      if (generation !== this.attempt) return;
      this.stream?.stop(); this.stream = null; this.socket = null;
      if (!failed) this.emit('status', { state: 'waiting', message: 'Celular desconectado. Clique em Conectar para tentar novamente.' });
    });
    socket.once('connect', () => socket.write('MICBRIDGE/2\n'));
    const write = data => {
      if (!data.length) return true;
      const ready = input.write(data);
      if (!ready) { socket.pause(); input.once('drain', () => { if (!socket.destroyed) socket.resume(); }); }
      return ready;
    };
    socket.on('data', async chunk => {
      if (phase === 'audio') { write(chunk); return; }
      if (phase === 'opening') { socket.destroy(new Error('Resposta inesperada.')); return; }
      header = Buffer.concat([header, chunk]);
      while (phase === 'header') {
        const at = header.indexOf(10);
        if (at < 0) { if (header.length > 512) socket.destroy(new Error('Resposta inválida do celular.')); return; }
        if (at > 512) { socket.destroy(new Error('Resposta muito longa.')); return; }
        const line = header.subarray(0, at).toString(); header = header.subarray(at + 1);
        if (line === 'WAIT') { this.emit('status', { state: 'approving', message: 'Toque em Permitir PC na tela do celular.' }); continue; }
        if (line !== 'OK 48000 1 s16le') { socket.destroy(new Error(line === 'DENIED' ? 'Conexão recusada no celular.' : `O celular respondeu: ${line}`)); return; }
        phase = 'opening'; socket.pause();
        try {
          const stream = await this.audio.open();
          if (socket.destroyed || generation !== this.attempt) { stream.stop(); return; }
          this.stream = stream; input = stream.input;
          stream.onExit?.(() => { if (!socket.destroyed) socket.destroy(new Error('Saída de áudio interrompida.')); });
          phase = 'audio'; socket.setTimeout(10000);
          this.emit('status', { state: 'streaming', message: 'Conectado · Microfone do celular ativo' });
          const ready = write(header); header = null; if (ready) socket.resume();
        } catch (error) { socket.destroy(error); }
      }
    });
  }
  disconnect() {
    ++this.attempt; this.socket?.destroy(); this.socket = null;
    this.stream?.stop(); this.stream = null;
  }
  async stop() { this.disconnect(); await this.audio.close(); }
}
