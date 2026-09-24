const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('fridayAPI', {
  toggleMiniMode: (mini) => ipcRenderer.send('toggle-mini-mode', mini),
  minimizeWindow: () => ipcRenderer.send('minimize-window'),
  maximizeWindow: () => ipcRenderer.send('maximize-window'),
  closeWindow: () => ipcRenderer.send('close-window'),
  executeCommand: (cmd) => ipcRenderer.invoke('execute-system-command', cmd),
  getSystemInfo: () => ipcRenderer.invoke('get-system-info'),
  openApp: (appName) => ipcRenderer.invoke('open-app', appName),
  showToastNotification: (title, body) => ipcRenderer.send('show-toast-notification', { title, body }),
  uploadCustomTflite: (filePath) => ipcRenderer.invoke('upload-tflite-model', filePath),
  saveSettings: (settings) => ipcRenderer.invoke('save-settings', settings),
  getSettings: () => ipcRenderer.invoke('get-settings')
});
