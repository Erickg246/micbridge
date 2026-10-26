import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
const exec = promisify(execFile);

export class LinuxAudio {
  constructor() { this.modules = []; this.player = null; }
  async prepare() {
    if (process.platform !== 'linux') throw new Error('O receptor virtual requer Linux.');
    try { await exec('pactl', ['info']); }
    catch { throw new Error('Servidor de áudio indisponível. Instale pulseaudio-utils e inicie a sessão PulseAudio/PipeWire do seu usuário.'); }
    // Unique names avoid touching devices belonging to another app instance.
    this.sink = `micbridge_${process.pid}`;
    try {
      const sink = await exec('pactl', ['load-module', 'module-null-sink', `sink_name=${this.sink}`, 'rate=48000', 'channels=1', 'sink_properties=device.description=MicBridge_Audio']);
      this.modules.push(sink.stdout.trim());
      const source = await exec('pactl', ['load-module', 'module-remap-source', `master=${this.sink}.monitor`, `source_name=${this.sink}_mic`, 'source_properties=device.description=MicBridge_Microfone_do_celular']);
      this.modules.push(source.stdout.trim());
    } catch (error) { await this.close(); throw new Error(`Não foi possível criar o microfone virtual: ${error.message}`); }
  }
  async open() {
    if (this.player) throw new Error('Já existe uma transmissão.');
    const child = spawn('pacat', ['--playback', `--device=${this.sink}`, '--raw', '--format=s16le', '--rate=48000', '--channels=1', '--latency-msec=40'], { stdio: ['pipe', 'ignore', 'pipe'] });
    let details = '';
    child.stderr.on('data', data => { details = (details + data).slice(-1000); });
    await new Promise((resolve, reject) => { child.once('spawn', resolve); child.once('error', reject); });
    this.player = child;
    child.stdin.on('error', () => {});
    return {
      input: child.stdin,
      onExit: callback => child.once('exit', code => callback(code, details)),
      stop: () => { child.stdin.end(); child.kill(); if (this.player === child) this.player = null; }
    };
  }
  async close() {
    if (this.player) { this.player.stdin.end(); this.player.kill(); this.player = null; }
    for (const id of this.modules.reverse()) await exec('pactl', ['unload-module', id]).catch(() => {});
    this.modules = [];
  }
}
