// Friday Desktop UI Renderer Logic

document.addEventListener('DOMContentLoaded', async () => {
  // Navigation Tabs
  const navItems = document.querySelectorAll('.nav-item');
  const tabContents = document.querySelectorAll('.tab-content');

  navItems.forEach(item => {
    item.addEventListener('click', () => {
      navItems.forEach(n => n.classList.remove('active'));
      tabContents.forEach(t => t.classList.remove('active'));

      item.classList.add('active');
      const targetTab = item.getAttribute('data-tab');
      document.getElementById(targetTab).classList.add('active');
    });
  });

  // Title bar controls
  document.getElementById('btnMinimize')?.addEventListener('click', () => window.fridayAPI.minimizeWindow());
  document.getElementById('btnMaximize')?.addEventListener('click', () => window.fridayAPI.maximizeWindow());
  document.getElementById('btnClose')?.addEventListener('click', () => window.fridayAPI.closeWindow());
  document.getElementById('btnToastToggle')?.addEventListener('click', () => window.fridayAPI.toggleMiniMode(true));

  // Voice Mic Listening
  const btnMic = document.getElementById('btnMicListen');
  const assistantPrompt = document.getElementById('assistantPrompt');
  let isListening = false;

  btnMic?.addEventListener('click', () => {
    isListening = !isListening;
    if (isListening) {
      btnMic.classList.add('listening');
      assistantPrompt.textContent = "Listening... Speak your command";
      addLogEntry('system', 'Microphone active. Listening...');
      window.fridayAPI.showToastNotification('Friday Assistant', 'Listening for your command...');
    } else {
      btnMic.classList.remove('listening');
      assistantPrompt.textContent = '"Say \'Hey Friday\' or click below to speak"';
      addLogEntry('system', 'Microphone paused.');
    }
  });

  // Response Log Helper
  const responseLog = document.getElementById('responseLog');
  function addLogEntry(type, text) {
    if (!responseLog) return;
    const entry = document.createElement('div');
    entry.className = `log-entry ${type}`;
    const timestamp = new Date().toLocaleTimeString();
    entry.textContent = `[${timestamp}] ${text}`;
    responseLog.appendChild(entry);
    responseLog.scrollTop = responseLog.scrollHeight;
  }

  // Text Command Processing
  const textInput = document.getElementById('textCommandInput');
  const btnSend = document.getElementById('btnSendCmd');

  async function handleCommandSend() {
    const query = textInput.value.trim();
    if (!query) return;
    addLogEntry('user', query);
    textInput.value = '';

    const lower = query.toLowerCase();
    if (lower.includes('time')) {
      addLogEntry('assistant', `Current time is ${new Date().toLocaleTimeString()}`);
    } else if (lower.includes('date')) {
      addLogEntry('assistant', `Today's date is ${new Date().toLocaleDateString()}`);
    } else if (lower.startsWith('open ')) {
      const appName = lower.replace('open ', '');
      addLogEntry('system', `Launching app: ${appName}`);
      const res = await window.fridayAPI.openApp(appName);
      addLogEntry('assistant', res.success ? `Opened ${appName}` : `Failed to open ${appName}: ${res.error}`);
    } else if (lower.startsWith('volume ')) {
      const res = await window.fridayAPI.executeCommand(query);
      addLogEntry('assistant', res.output);
    } else {
      addLogEntry('assistant', `Executing desktop command: ${query}`);
      const res = await window.fridayAPI.executeCommand(query);
      addLogEntry('assistant', res.output);
    }
  }

  btnSend?.addEventListener('click', handleCommandSend);
  textInput?.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') handleCommandSend();
  });

  // Desktop Tools: Quick Launch
  document.querySelectorAll('.tool-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const appName = btn.getAttribute('data-app');
      addLogEntry('system', `Quick Launch: ${appName}`);
      const res = await window.fridayAPI.openApp(appName);
      addLogEntry('assistant', res.success ? `Opened ${appName}` : `Error launching ${appName}`);
    });
  });

  // Volume Slider
  const volumeSlider = document.getElementById('volumeSlider');
  const volumeVal = document.getElementById('volumeVal');
  volumeSlider?.addEventListener('input', async (e) => {
    const val = e.target.value;
    if (volumeVal) volumeVal.textContent = `${val}%`;
    await window.fridayAPI.executeCommand(`volume ${val}`);
  });

  // Terminal Execution Tool
  const sysCmdInput = document.getElementById('sysCmdInput');
  const btnRunSysCmd = document.getElementById('btnRunSysCmd');
  const sysCmdOutput = document.getElementById('sysCmdOutput');

  btnRunSysCmd?.addEventListener('click', async () => {
    const cmd = sysCmdInput.value.trim();
    if (!cmd) return;
    if (sysCmdOutput) sysCmdOutput.textContent = `Running: ${cmd}...`;
    const res = await window.fridayAPI.executeCommand(cmd);
    if (sysCmdOutput) sysCmdOutput.textContent = res.output;
  });

  // Load Settings & System Info
  try {
    const settings = await window.fridayAPI.getSettings();
    const chkWakeWord = document.getElementById('chkWakeWord');
    if (chkWakeWord) chkWakeWord.checked = settings.wakeWordEnabled;

    const sysInfo = await window.fridayAPI.getSystemInfo();
    const sysBox = document.getElementById('systemInfoBox');
    if (sysBox) {
      sysBox.innerHTML = `
        <p><strong>OS Platform:</strong> ${sysInfo.platform} (${sysInfo.arch})</p>
        <p><strong>Kernel Release:</strong> ${sysInfo.release}</p>
        <p><strong>CPU Cores:</strong> ${sysInfo.cpus}</p>
        <p><strong>Total RAM:</strong> ${sysInfo.memoryGB} GB (Free: ${sysInfo.freeMemGB} GB)</p>
        <p><strong>Hostname:</strong> ${sysInfo.hostname}</p>
      `;
    }
  } catch (e) {
    console.error('Failed loading system settings:', e);
  }

  // File Upload for Custom TFLite
  const btnChooseTflite = document.getElementById('btnChooseTflite');
  const tfliteFileInput = document.getElementById('tfliteFileInput');
  const tfliteFileName = document.getElementById('tfliteFileName');
  const btnUploadTflite = document.getElementById('btnUploadTflite');
  let selectedFilePath = null;

  btnChooseTflite?.addEventListener('click', () => tfliteFileInput.click());
  tfliteFileInput?.addEventListener('change', (e) => {
    if (e.target.files && e.target.files[0]) {
      selectedFilePath = e.target.files[0].path;
      tfliteFileName.textContent = e.target.files[0].name;
      btnUploadTflite.disabled = false;
    }
  });

  btnUploadTflite?.addEventListener('click', async () => {
    if (!selectedFilePath) return;
    addLogEntry('system', 'Uploading custom TFLite wake word model...');
    const res = await window.fridayAPI.uploadCustomTflite(selectedFilePath);
    if (res.success) {
      addLogEntry('assistant', `Custom model installed successfully at ${res.path}`);
      alert('Custom TFLite model loaded successfully!');
    } else {
      addLogEntry('system', `Failed to upload TFLite model: ${res.error}`);
    }
  });

  // Canvas Audio Waveform animation
  const canvas = document.getElementById('waveformCanvas');
  if (canvas) {
    const ctx = canvas.getContext('2d');
    let step = 0;
    function drawWave() {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.beginPath();
      ctx.lineWidth = 3;
      ctx.strokeStyle = isListening ? '#ef4444' : '#7c3aed';

      for (let x = 0; x < canvas.width; x += 5) {
        const amp = isListening ? 25 : 8;
        const y = canvas.height / 2 + Math.sin((x + step) * 0.05) * amp * Math.cos(step * 0.02);
        if (x === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.stroke();
      step += 2;
      requestAnimationFrame(drawWave);
    }
    drawWave();
  }
});
