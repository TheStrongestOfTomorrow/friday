const { app, BrowserWindow, ipcMain, Tray, Menu, Notification, globalShortcut, screen } = require('electron');
const path = require('path');
const os = require('os');
const fs = require('fs');
const { exec } = require('child_process');

let mainWindow = null;
let toastWindow = null;
let tray = null;
let isMiniMode = false;

// Default Settings
const defaultSettings = {
  wakeWordEnabled: true,
  sensitivity: 0.7,
  customTflitePath: '',
  miniModeOnStart: false,
  hotkey: 'CommandOrControl+Shift+F'
};

function getSettingsPath() {
  return path.join(app.getPath('userData'), 'friday_settings.json');
}

function loadSettings() {
  try {
    const p = getSettingsPath();
    if (fs.existsSync(p)) {
      return { ...defaultSettings, ...JSON.parse(fs.readFileSync(p, 'utf8')) };
    }
  } catch (err) {
    console.error('Failed to load settings:', err);
  }
  return defaultSettings;
}

function saveSettings(settings) {
  try {
    fs.writeFileSync(getSettingsPath(), JSON.stringify(settings, null, 2));
  } catch (err) {
    console.error('Failed to save settings:', err);
  }
}

function createMainWindow() {
  const primaryDisplay = screen.getPrimaryDisplay();
  const { width, height } = primaryDisplay.workAreaSize;

  mainWindow = new BrowserWindow({
    width: 1000,
    height: 700,
    minWidth: 380,
    minHeight: 200,
    frame: false,
    transparent: true,
    alwaysOnTop: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true
    },
    icon: path.join(__dirname, '../renderer/assets/icon.png')
  });

  mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

function createToastOverlayWindow() {
  const primaryDisplay = screen.getPrimaryDisplay();
  const { width, height } = primaryDisplay.workAreaSize;

  // Bottom right floating toast mini overlay window
  toastWindow = new BrowserWindow({
    width: 320,
    height: 140,
    x: width - 340,
    y: height - 160,
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    skipTaskbar: true,
    resizable: false,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true
    }
  });

  toastWindow.loadFile(path.join(__dirname, '../renderer/toast.html'));
}

function createTray() {
  // Simple tray setup
  tray = new Tray(path.join(__dirname, '../renderer/assets/icon_tray.png'));
  const contextMenu = Menu.buildFromTemplate([
    {
      label: 'Open Friday Fullscreen',
      click: () => {
        if (mainWindow) {
          mainWindow.show();
          mainWindow.focus();
          switchToFullMode();
        }
      }
    },
    {
      label: 'Toggle Floating Toast / Mini Widget',
      click: () => {
        toggleMiniMode(!isMiniMode);
      }
    },
    { type: 'separator' },
    {
      label: 'Quit Friday',
      click: () => {
        app.quit();
      }
    }
  ]);

  tray.setToolTip('Friday Voice Assistant');
  tray.setContextMenu(contextMenu);
  tray.on('click', () => {
    if (mainWindow) {
      if (mainWindow.isVisible()) {
        mainWindow.hide();
      } else {
        mainWindow.show();
        mainWindow.focus();
      }
    }
  });
}

function toggleMiniMode(enableMini) {
  isMiniMode = enableMini;
  if (isMiniMode) {
    if (mainWindow) {
      const primaryDisplay = screen.getPrimaryDisplay();
      const { width, height } = primaryDisplay.workAreaSize;
      mainWindow.setSize(380, 220);
      mainWindow.setPosition(width - 400, height - 240);
      mainWindow.setAlwaysOnTop(true);
      mainWindow.webContents.send('mode-changed', 'mini');
    }
  } else {
    if (mainWindow) {
      mainWindow.setSize(1000, 700);
      mainWindow.center();
      mainWindow.setAlwaysOnTop(false);
      mainWindow.webContents.send('mode-changed', 'full');
    }
  }
}

function setupIpcHandlers() {
  ipcMain.on('toggle-mini-mode', (event, mini) => {
    toggleMiniMode(mini);
  });

  ipcMain.on('minimize-window', () => {
    if (mainWindow) mainWindow.minimize();
  });

  ipcMain.on('maximize-window', () => {
    if (mainWindow) {
      if (mainWindow.isMaximized()) {
        mainWindow.unmaximize();
      } else {
        mainWindow.maximize();
      }
    }
  });

  ipcMain.on('close-window', () => {
    if (mainWindow) mainWindow.hide();
  });

  ipcMain.handle('get-system-info', () => {
    return {
      platform: os.platform(),
      release: os.release(),
      arch: os.arch(),
      cpus: os.cpus().length,
      memoryGB: (os.totalmem() / (1024 * 1024 * 1024)).toFixed(1),
      freeMemGB: (os.freemem() / (1024 * 1024 * 1024)).toFixed(1),
      hostname: os.hostname(),
      uptime: Math.floor(os.uptime())
    };
  });

  ipcMain.handle('execute-system-command', async (event, commandStr) => {
    return new Promise((resolve) => {
      const platform = os.platform();
      let execCmd = commandStr;

      // Desktop specific special commands
      if (commandStr.toLowerCase().startsWith('volume ')) {
        const val = commandStr.split(' ')[1];
        if (platform === 'linux') execCmd = `amixer -D pulse sset Master ${val}%`;
        else if (platform === 'win32') execCmd = `powershell -c "(nwc.exe) ... "`;
        else if (platform === 'darwin') execCmd = `osascript -e "set volume output volume ${val}"`;
      }

      exec(execCmd, { timeout: 10000 }, (error, stdout, stderr) => {
        if (error) {
          resolve({ success: false, output: stderr || error.message });
        } else {
          resolve({ success: true, output: stdout.trim() || 'Command executed successfully.' });
        }
      });
    });
  });

  ipcMain.handle('open-app', async (event, appName) => {
    return new Promise((resolve) => {
      const platform = os.platform();
      let cmd = '';
      if (platform === 'linux') cmd = `gtk-launch ${appName} || ${appName}`;
      else if (platform === 'darwin') cmd = `open -a "${appName}"`;
      else if (platform === 'win32') cmd = `start ${appName}`;

      exec(cmd, (err) => {
        if (err) resolve({ success: false, error: err.message });
        else resolve({ success: true, app: appName });
      });
    });
  });

  ipcMain.handle('get-settings', () => loadSettings());

  ipcMain.handle('save-settings', (event, settings) => {
    saveSettings(settings);
    return { success: true };
  });

  ipcMain.handle('upload-tflite-model', async (event, sourcePath) => {
    try {
      const destDir = path.join(app.getPath('userData'), 'models');
      if (!fs.existsSync(destDir)) fs.mkdirSync(destDir, { recursive: true });
      const destPath = path.join(destDir, 'custom_wakeword.tflite');
      fs.copyFileSync(sourcePath, destPath);

      const curr = loadSettings();
      curr.customTflitePath = destPath;
      saveSettings(curr);

      return { success: true, path: destPath };
    } catch (err) {
      return { success: false, error: err.message };
    }
  });

  ipcMain.on('show-toast-notification', (event, { title, body }) => {
    if (Notification.isSupported()) {
      const notif = new Notification({
        title: title || 'Friday Assistant',
        body: body || 'Listening...',
        icon: path.join(__dirname, '../renderer/assets/icon.png')
      });
      notif.on('click', () => {
        if (mainWindow) {
          mainWindow.show();
          mainWindow.focus();
        }
      });
      notif.show();
    }
  });
}

app.whenReady().then(() => {
  createMainWindow();
  createToastOverlayWindow();
  createTray();
  setupIpcHandlers();

  // Register Global Hotkey to summon Friday
  const settings = loadSettings();
  globalShortcut.register(settings.hotkey || 'CommandOrControl+Shift+F', () => {
    if (mainWindow) {
      if (mainWindow.isVisible() && !mainWindow.isMinimized()) {
        mainWindow.hide();
      } else {
        mainWindow.show();
        mainWindow.focus();
      }
    }
  });

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createMainWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
