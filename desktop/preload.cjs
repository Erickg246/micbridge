const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('micbridge', {
  state: () => ipcRenderer.invoke('state'),
  copy: () => ipcRenderer.invoke('copy'),
  disconnect: () => ipcRenderer.invoke('disconnect'),
  onUpdate: callback => ipcRenderer.on('update', (_event, value) => callback(value))
});
