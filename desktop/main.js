import { app, BrowserWindow, ipcMain, clipboard } from 'electron';
import { readFile, mkdir } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { networkInterfaces } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { LinuxAudio } from './audio.js';
import { Receiver } from './receiver.js';
const here = dirname(fileURLToPath(import.meta.url));
let window, receiver, quitting = false;
let status = { state: 'starting', message: 'Preparando o microfone virtual…' };
let connection = null;
if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', () => { window?.show(); window?.focus(); });
  app.whenReady().then(async () => {
  window = new BrowserWindow({ width: 900, height: 820, minWidth: 640, minHeight: 600, title: 'MicBridge', icon: join(here, 'icon.png'), backgroundColor: '#10151e', webPreferences: { preload: join(here, 'preload.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true } });
  window.setMenuBarVisibility(false);
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  window.webContents.on('will-navigate', event => event.preventDefault());
  ipcMain.handle('state', () => ({ status, connection }));
  ipcMain.handle('copy', () => { if (connection) clipboard.writeText(`${connection.addresses[0] ?? 'IP_DO_PC'}:${connection.port}`); });
  ipcMain.handle('disconnect', () => receiver?.disconnect());
  const update = value => { status = value; if (!window.isDestroyed()) window.webContents.send('update', { status, connection }); };
  await window.loadFile(join(here, 'index.html'));
  try {
    const dir = join(app.getPath('userData'), 'tls'); await mkdir(dir, { recursive: true, mode: 0o700 });
    const keyPath = join(dir, 'key.pem'), certPath = join(dir, 'cert.pem');
    let key, cert;
    try { [key, cert] = await Promise.all([readFile(keyPath), readFile(certPath)]); }
    catch (error) {
      if (error.code !== 'ENOENT') throw error;
      await promisify(execFile)('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', keyPath, '-out', certPath, '-days', '3650', '-subj', '/CN=MicBridge']);
      const { chmod } = await import('node:fs/promises'); await chmod(keyPath, 0o600);
      [key, cert] = await Promise.all([readFile(keyPath), readFile(certPath)]);
    }
    receiver = new Receiver({ audio: new LinuxAudio(), key, cert });
    receiver.on('status', update);
    const port = await receiver.start();
    const addresses = Object.values(networkInterfaces()).flat().filter(i => i.family === 'IPv4' && !i.internal).map(i => i.address);
    connection = { addresses, port, code: receiver.code, fingerprint: receiver.fingerprint };
    update({ state: 'waiting', message: 'Pronto para conectar seu celular' });
  } catch (error) { update({ state: 'error', message: error.message }); }
  app.on('window-all-closed', () => app.quit());
  app.on('before-quit', event => {
    if (quitting) return;
    event.preventDefault(); quitting = true;
    Promise.resolve(receiver?.stop()).finally(() => app.quit());
  });
  });
}
