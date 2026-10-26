const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('micbridge', {
  state: () => ipcRenderer.invoke('state'),
  connect: address => ipcRenderer.invoke('connect', address),
  disconnect: () => ipcRenderer.invoke('disconnect'),
  onUpdate: callback => ipcRenderer.on('update', (_event, value) => callback(value))
});
