const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('fridayAPI', {
  // Navigation & Window Modes
  toggleMiniMode: (mini) => ipcRenderer.send('toggle-mini-mode', mini),
  minimizeWindow: () => ipcRenderer.send('minimize-window'),
  maximizeWindow: () => ipcRenderer.send('maximize-window'),
  closeWindow: () => ipcRenderer.send('close-window'),

  // System Commands & OS Features
  executeCommand: (cmd) => ipcRenderer.invoke('execute-system-command', cmd),
  getSystemInfo: () => ipcRenderer.invoke('get-system-info'),
  openApp: (appName) => ipcRenderer.invoke('open-app', appName),
  showToastNotification: (title, body) => ipcRenderer.send('show-toast-notification', { title, body }),

  // Wake word & Custom TFLite / Audio
  uploadCustomTflite: (filePath) => ipcRenderer.invoke('upload-tflite-model', filePath),
  saveSettings: (settings) => ipcRenderer.invoke('save-settings', settings),
  getSettings: () => ipcRenderer.invoke('get-settings'),

  // Listeners
  onWakeWordDetected: (callback) => ipcRenderer.on('wakeword-detected', (event, data) => callback(data)),
  onToastAction: (callback) => ipcRenderer.on('toast-clicked', () => callback()),
  onVoiceCommandResult: (callback) => ipcRenderer.on('voice-command-result', (event, res) => callback(res))
});
