// RadioNet для Windows: окно Electron с тем же плеером, что и в Android-приложении (assets/index.html)
const { app, BrowserWindow, shell, Menu } = require('electron');
const path = require('path');

app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');
app.setAppUserModelId('com.anderza.radionet');

if (!app.requestSingleInstanceLock()) { app.quit(); }

let win = null;
function create(){
  win = new BrowserWindow({
    width: 1180, height: 800, minWidth: 360, minHeight: 480,
    title: 'RadioNet', backgroundColor: '#07344B', autoHideMenuBar: true,
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: {
      // страница лежит внутри приложения и, как в Android-версии, читает каталоги станций с любых серверов
      webSecurity: false,
      contextIsolation: true,
      sandbox: true,
      backgroundThrottling: false,
    },
  });
  Menu.setApplicationMenu(null);
  win.loadFile(path.join(__dirname, 'index.html'));
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https?:/i.test(url)) shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('file:')) { e.preventDefault(); if (/^https?:/i.test(url)) shell.openExternal(url); } });
  win.webContents.on('before-input-event', (e, input) => {
    if (input.type !== 'keyDown') return;
    if (input.key === 'F11') { win.setFullScreen(!win.isFullScreen()); e.preventDefault(); }
    if (input.key === 'Escape' && win.isFullScreen()) win.setFullScreen(false);
    if (input.key === 'F12' && input.control && input.shift) win.webContents.toggleDevTools();
  });
}
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.whenReady().then(create);
app.on('window-all-closed', () => app.quit());
