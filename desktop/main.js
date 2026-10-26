import { app, BrowserWindow, ipcMain, dialog } from 'electron';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { LinuxAudio } from './audio.js';
import { PhoneClient, discoverPhone, parseAddress } from './phone-client.js';
const here = dirname(fileURLToPath(import.meta.url));
let window, client, quitting = false, ready = false, connecting = false, attempt = 0;
let status = { state: 'starting', message: 'Preparando o microfone virtual…' };
let connection = { address: '' }, known = {};
function update(value) { status = value; if (window && !window.isDestroyed()) window.webContents.send('update', { status, connection }); }
if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', () => { window?.show(); window?.focus(); });
  app.on('window-all-closed', () => app.quit());
  app.on('before-quit', event => {
    if (quitting) return; event.preventDefault(); quitting = true; ++attempt;
    Promise.resolve(client?.stop()).finally(() => app.quit());
  });
  app.whenReady().then(async () => {
    const settingsPath = join(app.getPath('userData'), 'phones.json');
    await mkdir(app.getPath('userData'), { recursive: true });
    try { const saved = JSON.parse(await readFile(settingsPath, 'utf8')); known = saved.known ?? {}; connection.address = saved.address ?? ''; } catch {}
    window = new BrowserWindow({ width: 900, height: 820, minWidth: 640, minHeight: 600, title: 'MicBridge', icon: join(here, 'icon.png'), backgroundColor: '#10151e', webPreferences: { preload: join(here, 'preload.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true } });
    window.setMenuBarVisibility(false);
    window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
    window.webContents.on('will-navigate', event => event.preventDefault());
    ipcMain.handle('state', () => ({ status, connection }));
    ipcMain.handle('disconnect', () => { ++attempt; client?.disconnect(); update({ state: 'waiting', message: 'Desconectado' }); });
    ipcMain.handle('connect', async (_event, address) => {
      if (!ready || connecting) return;
      connecting = true; const current = ++attempt;
      try {
        const target = parseAddress(address); client.disconnect();
        update({ state: 'connecting', message: 'Procurando o celular…' });
        const identity = await discoverPhone(address);
        if (current !== attempt || quitting) return;
        if (known[target.key] !== identity.fingerprint) {
          const answer = await dialog.showMessageBox(window, { type: 'question', title: 'Conferir celular', message: 'Confirme a identidade do celular', detail: 'No Android, abra “Identidade do celular”. Os números precisam ser iguais:\n\n' + identity.fingerprint, buttons: ['Cancelar', 'São iguais · Conectar'], defaultId: 0, cancelId: 0 });
          if (current !== attempt || quitting) return;
          if (answer.response !== 1) { update({ state: 'waiting', message: 'Conexão cancelada' }); return; }
          known[target.key] = identity.fingerprint;
        }
        connection.address = address.trim();
        await writeFile(settingsPath, JSON.stringify({ known, address: connection.address }), { mode: 0o600 });
        if (current === attempt && !quitting) await client.connect(address, identity);
      } catch (error) { if (current === attempt) update({ state: 'error', message: error.code === 'ECONNREFUSED' ? 'Servidor não iniciado. Toque em Iniciar servidor no celular.' : error.message }); }
      finally { connecting = false; }
    });
    await window.loadFile(join(here, 'index.html'));
    client = new PhoneClient(new LinuxAudio()); client.on('status', update);
    try { await client.prepare(); ready = true; update({ state: 'waiting', message: 'Digite o IP mostrado no celular' }); }
    catch (error) { update({ state: 'unavailable', message: error.message }); }
  }).catch(error => { dialog.showErrorBox('MicBridge', error.message); app.quit(); });
}
