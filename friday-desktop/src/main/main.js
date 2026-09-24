const { app, BrowserWindow, ipcMain, Tray, Menu, Notification, globalShortcut, screen } = require('electron');
const path = require('path');
const os = require('os');
const fs = require('fs');
const { exec } = require('child_process');

let mainWindow = null;
let isMiniMode = false;

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1000,
    height: 700,
    frame: false,
    transparent: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true
    }
  });

  mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));
}

app.whenReady().then(() => {
  createMainWindow();
});
