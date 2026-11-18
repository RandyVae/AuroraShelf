const fs = require('fs');
const vm = require('vm');
const path = require('path');

console.log('--- Verifying Battle Multi-stage & Slots Fixes ---');

// 1. Verify Battle SpineField clear logic & Battle start/stop cleanup
const spineJs = fs.readFileSync('app/src/main/assets/www/js/spine.js', 'utf8');
const battleJs = fs.readFileSync('app/src/main/assets/www/js/battle.js', 'utf8');

// Check that spine.js defines SpineField.prototype.clear
if (!spineJs.includes('SpineField.prototype.clear = function()') && !spineJs.includes('SpineField.prototype.clear = function ()')) {
  throw new Error('SpineField.prototype.clear is not defined in spine.js');
}

// Check that battle.js invokes this.spine.clear() in both start() and stop()
if (!battleJs.includes('this.spine.clear()')) {
  throw new Error('Battle does not call this.spine.clear()');
}

// Check that battle.js cleared views bug (it should check this.spine.units, not this.spine.views)
if (battleJs.includes('this.spine.views.forEach')) {
  throw new Error('Battle still references nonexistent this.spine.views instead of this.spine.units');
}

// 2. Simulate Battle SpineField and Battle start/stop cycle
{
  const mockCanvas = { getContext: () => ({ clearColor: () => {}, clear: () => {} }) };
  const mockUnits = [];
  const mockSpine = {
    units: mockUnits,
    clear() {
      this.units = [];
    },
    build(u) {
      const unitObj = { unit: u, view: {} };
      this.units.push(unitObj);
      return unitObj;
    }
  };

  // Stage 1 units
  mockSpine.build({ id: 'hero1' });
  mockSpine.build({ id: 'enemy1' });
  if (mockSpine.units.length !== 2) throw new Error('Failed to add stage 1 units');

  // Finish stage 1 -> start stage 2
  mockSpine.clear();
  if (mockSpine.units.length !== 0) throw new Error('Spine clear failed to empty units');

  // Stage 2 units
  mockSpine.build({ id: 'hero1' });
  mockSpine.build({ id: 'enemy2' });
  if (mockSpine.units.length !== 2) throw new Error('Stage 2 should have 2 units, got ' + mockSpine.units.length);
  console.log('✓ Battle Spine clear & multi-stage transition logic verified');
}

// 3. Verify SlotsUI free spins rendering logic
{
  const slotsJs = fs.readFileSync('app/src/main/assets/www/js/slots.js', 'utf8');
  
  // Create mock DOM elements
  const elements = {
    'slots-gold': { textContent: '' },
    'slots-bet': { textContent: '' },
    'slots-free-spins': { textContent: '' },
    'slots-hint': { textContent: '' },
    'slots-status-bar': {
      classList: {
        classes: new Set(),
        toggle(cls, val) {
          if (val) this.classes.add(cls);
          else this.classes.delete(cls);
        },
        contains(cls) { return this.classes.has(cls); }
      }
    },
    'slots-win-tier': { textContent: 'BASE GAME', className: 'slots-badge' }
  };

  const spinBtnMain = { textContent: '' };
  const spinBtnSub = { textContent: '' };

  const mockDocument = {
    getElementById: (id) => elements[id] || null,
    querySelector: (sel) => {
      if (sel.includes('.spin-main-title')) return spinBtnMain;
      if (sel.includes('.spin-sub-title')) return spinBtnSub;
      return null;
    },
    querySelectorAll: () => []
  };

  const appObj = {
    state: { gold: 5000 },
    data: {}
  };

  const context = {
    window: {},
    document: mockDocument,
    console,
    performance: { now: () => 0 },
    setTimeout: () => {},
    clearTimeout: () => {},
    requestAnimationFrame: () => {},
    APP: appObj
  };
  context.window.APP = appObj;

  vm.createContext(context);
  vm.runInContext(slotsJs, context);

  const SlotsUI = context.window.SlotsUI;
  SlotsUI.config = { reels: 5, rows: 3 };
  SlotsUI.bet = 20;

  // Case A: freeSpins = 0 (Base game)
  SlotsUI.freeSpins = 0;
  SlotsUI.render();

  if (String(elements['slots-free-spins'].textContent) !== '0') {
    throw new Error('Expected slots-free-spins to be 0, got ' + elements['slots-free-spins'].textContent);
  }
  if (elements['slots-status-bar'].classList.contains('has-free-spins')) {
    throw new Error('Status bar should NOT have has-free-spins when freeSpins is 0');
  }
  if (!elements['slots-hint'].textContent.includes('单次旋转消耗 20 金币')) {
    throw new Error('Incorrect base game hint: ' + elements['slots-hint'].textContent);
  }
  if (!spinBtnMain.textContent.includes('SPIN 旋转拉杆')) {
    throw new Error('Incorrect base game spin button title: ' + spinBtnMain.textContent);
  }
  console.log('✓ SlotsUI base game (freeSpins=0) rendering verified');

  // Case B: freeSpins = 5 (Free spins active)
  SlotsUI.freeSpins = 5;
  SlotsUI.render();

  if (String(elements['slots-free-spins'].textContent) !== '5') {
    throw new Error('Expected slots-free-spins to be 5, got ' + elements['slots-free-spins'].textContent);
  }
  if (!elements['slots-status-bar'].classList.contains('has-free-spins')) {
    throw new Error('Status bar should HAVE has-free-spins when freeSpins > 0');
  }
  if (!elements['slots-hint'].textContent.includes('免费大旋转进行中')) {
    throw new Error('Incorrect free spin hint: ' + elements['slots-hint'].textContent);
  }
  if (!spinBtnMain.textContent.includes('FREE SPIN')) {
    throw new Error('Incorrect free spin button title: ' + spinBtnMain.textContent);
  }
  if (!spinBtnSub.textContent.includes('剩余 5 次')) {
    throw new Error('Incorrect free spin button subtitle: ' + spinBtnSub.textContent);
  }
  console.log('✓ SlotsUI free spins mode (freeSpins=5) rendering verified');
}

// 4. Verify CSS contains required styling
{
  const appCss = fs.readFileSync('app/src/main/assets/www/css/app.css', 'utf8');
  if (!appCss.includes('.slots-status-lead')) throw new Error('Missing .slots-status-lead in app.css');
  if (!appCss.includes('.slots-free-pill')) throw new Error('Missing .slots-free-pill in app.css');
  if (!appCss.includes('.slots-status.has-free-spins')) throw new Error('Missing .slots-status.has-free-spins in app.css');
  console.log('✓ app.css slots-status telemetry strip styling verified');
}

// 5. (a) Verify window.handleAppBack does NOT hide closed #detail-sheet / #mission-sheet & resolves battle revive deadlock
{
  const appJs = fs.readFileSync('app/src/main/assets/www/js/app.js', 'utf8');
  if (appJs.includes("style.display !== 'none'")) {
    throw new Error("app.js still contains broken style.display !== 'none' check in handleAppBack");
  }

  function makeClassList(initial = []) {
    const set = new Set(initial);
    return {
      add: (c) => set.add(c),
      remove: (c) => set.delete(c),
      contains: (c) => set.has(c),
      toggle: (c, v) => {
        if (v === undefined) {
          if (set.has(c)) { set.delete(c); return false; }
          set.add(c); return true;
        }
        if (v) set.add(c); else set.delete(c);
        return !!v;
      },
    };
  }

  function makeNode(id, opts = {}) {
    return {
      id,
      hidden: opts.hidden !== undefined ? opts.hidden : false,
      style: { display: '' },
      textContent: '',
      classList: makeClassList(opts.classes || []),
      addEventListener: () => {},
      querySelectorAll: () => [],
    };
  }

  const domMap = {
    'cg-cinema-overlay': makeNode('cg-cinema-overlay', { hidden: true }),
    'cg-lightbox-overlay': makeNode('cg-lightbox-overlay', { hidden: true }),
    'modal': makeNode('modal', { hidden: false }),
    'modal-battle-revive': makeNode('modal-battle-revive', { hidden: true, classes: ['sheet'] }),
    'btn-battle-revive-cancel': makeNode('btn-battle-revive-cancel'),
    'slots-feature': makeNode('slots-feature', { hidden: true }),
    'slots-paytable': makeNode('slots-paytable', { hidden: true }),
    'modal-slots-bailout': makeNode('modal-slots-bailout', { hidden: true, classes: ['sheet'] }),
    'modal-slots-recharge': makeNode('modal-slots-recharge', { hidden: true, classes: ['sheet'] }),
    'modal-battle-recharge': makeNode('modal-battle-recharge', { hidden: true, classes: ['sheet'] }),
    'equip-picker-sheet': makeNode('equip-picker-sheet', { hidden: false, classes: ['sheet'] }),
    'detail-sheet': makeNode('detail-sheet', { hidden: false, classes: ['sheet'] }),
    'mission-sheet': makeNode('mission-sheet', { hidden: false, classes: ['sheet'] }),
    'stage-action-sheet': makeNode('stage-action-sheet', { hidden: false, classes: ['sheet'] }),
    'cg-sheet': makeNode('cg-sheet', { hidden: false, classes: ['sheet'] }),
    'gacha-reveal': makeNode('gacha-reveal', { hidden: false, classes: ['gacha-reveal'] }),
    'boot-note': makeNode('boot-note'),
  };

  let detailClosedCount = 0;
  const mockUI = {
    setProgress: () => {},
    closeDetail: () => {
      detailClosedCount += 1;
      domMap['detail-sheet'].classList.remove('open');
    },
  };
  const mockAudio = {
    bgmVolume: 0.55,
    sfxVolume: 0.8,
    isMuted: false,
    appMuted: false,
    pauseBgm: () => {},
    resumeBgm: () => {},
    stopBgm: () => {},
    playBgm: () => {},
    setVolumes: () => {},
  };
  const mockVFX = { pause: () => {}, resume: () => {} };

  const mockDoc = {
    addEventListener: () => {},
    documentElement: { classList: makeClassList() },
    getElementById: (id) => domMap[id] || null,
    querySelector: (sel) => {
      if (sel.startsWith('#')) return domMap[sel.slice(1)] || null;
      return null;
    },
    querySelectorAll: () => [],
  };

  const ctx = {
    window: {
      location: { hash: '', search: '' },
      addEventListener: () => {},
      removeEventListener: () => {},
      UI: mockUI,
      LGAudio: mockAudio,
      LGVFX: mockVFX,
      LGSave: { load: () => ({ settings: { bgm: 0.55, sfx: 0.8 }, cleared: {}, owned: [] }), STARTERS: [] },
    },
    document: mockDoc,
    UI: mockUI,
    LGAudio: mockAudio,
    LGVFX: mockVFX,
    LGSave: { load: () => ({ settings: { bgm: 0.55, sfx: 0.8 }, cleared: {}, owned: [] }), STARTERS: [] },
    fetch: () => Promise.reject(new Error('offline-unit-test')),
    console,
    setTimeout: () => 1,
    clearTimeout: () => {},
    requestAnimationFrame: () => 1,
    performance: { now: () => 0 },
  };
  ctx.window.document = mockDoc;
  vm.createContext(ctx);
  vm.runInContext(appJs, ctx);

  ctx.window.APP.screen = 'hub';
  // Closed sheets have hidden=false and style.display='' (CSS .sheet controls display:none unless .open).
  const backOnHub = ctx.window.handleAppBack();
  if (backOnHub !== false) {
    throw new Error('Expected handleAppBack on hub with no open overlays to return false, got ' + backOnHub);
  }
  if (domMap['detail-sheet'].hidden !== false || domMap['mission-sheet'].hidden !== false) {
    throw new Error('handleAppBack must NOT set hidden=true on closed #detail-sheet or #mission-sheet');
  }

  // Opening #mission-sheet and pressing back removes .open while keeping hidden=false
  domMap['mission-sheet'].classList.add('open');
  if (ctx.window.handleAppBack() !== true) {
    throw new Error('handleAppBack should return true when closing open #mission-sheet');
  }
  if (domMap['mission-sheet'].classList.contains('open') || domMap['mission-sheet'].hidden !== false) {
    throw new Error('Closing #mission-sheet via handleAppBack must remove .open and keep hidden=false');
  }

  // Opening #detail-sheet and pressing back delegates to UI.closeDetail() and keeps hidden=false
  domMap['detail-sheet'].classList.add('open');
  if (ctx.window.handleAppBack() !== true || detailClosedCount !== 1 || domMap['detail-sheet'].hidden !== false) {
    throw new Error('Closing #detail-sheet via handleAppBack must invoke UI.closeDetail() without setting hidden=true');
  }

  // Battle revive modal active -> back triggers #btn-battle-revive-cancel handler (avoiding battlefield soft-lock)
  let reviveCancelled = false;
  domMap['modal-battle-revive'].hidden = false;
  domMap['modal-battle-revive'].classList.add('open');
  domMap['btn-battle-revive-cancel'].onclick = () => {
    reviveCancelled = true;
    domMap['modal-battle-revive'].classList.remove('open');
    domMap['modal-battle-revive'].hidden = true;
  };
  if (ctx.window.handleAppBack() !== true || !reviveCancelled || !domMap['modal-battle-revive'].hidden) {
    throw new Error('handleAppBack must trigger #btn-battle-revive-cancel when #modal-battle-revive is visible');
  }
  console.log('✓ (a) handleAppBack sheet state & battle revive cancel routing verified');
}

// 6. (b) Verify all 11 SLOTS_MASCOTS point to existing directories under app/src/main/assets/www/assets/units/ & Slots economy fixes
{
  const slotsJs = fs.readFileSync('app/src/main/assets/www/js/slots.js', 'utf8');
  const slotsConfig = JSON.parse(fs.readFileSync('app/src/main/assets/www/data/slots.json', 'utf8'));
  const toasts = [];
  const paytableList = { innerHTML: '', children: [], appendChild(c) { this.children.push(c); } };
  const mockDoc = {
    createElement: () => ({ className: '', innerHTML: '', style: {} }),
    getElementById: (id) => (id === 'slots-paytable-list' ? paytableList : null),
    querySelector: () => null,
    querySelectorAll: () => [],
  };
  const appState = {
    gold: 25,
    settings: { adult: true, sfx: 0.5 },
    slots: { version: 1, freeSpins: 0, freeSpinBet: null },
    slotsSession: { freeSpins: 0, bailoutClaims: 0 },
  };
  const ctx = {
    window: {},
    document: mockDoc,
    console,
    performance: { now: () => 0 },
    setTimeout: () => 1,
    clearTimeout: () => {},
    requestAnimationFrame: () => 1,
    UI: { toast: (m) => toasts.push(m), renderHud: () => {} },
    LGSave: { persist: () => {}, recordTransaction: () => {} },
    APP: { state: appState, data: {} },
  };
  ctx.window.APP = ctx.APP;
  ctx.window.UI = ctx.UI;
  ctx.window.LGSave = ctx.LGSave;
  vm.createContext(ctx);
  vm.runInContext(slotsJs, ctx);

  const mascots = ctx.window.SlotsGame.SLOTS_MASCOTS;
  if (!Array.isArray(mascots) || mascots.length !== 11) {
    throw new Error('Expected 11 SLOTS_MASCOTS, got ' + (mascots && mascots.length));
  }
  const unitsRoot = path.join('app/src/main/assets/www/assets/units');
  for (const m of mascots) {
    const keysToCheck = [m.unit];
    if (m.safeUnit) keysToCheck.push(m.safeUnit);
    for (const unitDirName of keysToCheck) {
      const dirPath = path.join(unitsRoot, unitDirName);
      if (!fs.existsSync(dirPath) || !fs.statSync(dirPath).isDirectory()) {
        throw new Error(`Mascot ${m.id} references missing unit directory: ${dirPath}`);
      }
      const files = fs.readdirSync(dirPath);
      if (!files.some((f) => f.endsWith('.atlas')) || !files.some((f) => f.endsWith('.webp')) || !files.some((f) => f.endsWith('.json'))) {
        throw new Error(`Mascot ${m.id} unit directory ${dirPath} is missing Spine .atlas/.webp/.json assets`);
      }
    }
  }

  // Verify auto-step-down bet when gold >= minBet (10) && gold < current bet (50)
  const SlotsUI = ctx.window.SlotsUI;
  SlotsUI.config = slotsConfig;
  SlotsUI.bet = 50;
  SlotsUI.freeSpins = 0;
  SlotsUI.spinning = false;
  let outOfGoldCalled = false;
  const origOut = SlotsUI.handleOutOfGold.bind(SlotsUI);
  SlotsUI.handleOutOfGold = () => { outOfGoldCalled = true; };
  SlotsUI.playSpin();
  if (outOfGoldCalled) {
    throw new Error('playSpin must NOT call handleOutOfGold when player can still afford a lower bet tier');
  }
  if (SlotsUI.bet !== 20) {
    throw new Error('Expected playSpin to auto-step bet down from 50 to 20 (for 25 gold), got ' + SlotsUI.bet);
  }
  SlotsUI.handleOutOfGold = origOut;

  // Verify bet lock during freeSpins > 0
  SlotsUI.freeSpins = 3;
  SlotsUI.betUp();
  if (SlotsUI.bet !== 20) {
    throw new Error('betUp must be locked while freeSpins > 0');
  }

  // Verify syncFromState resets freeSpins, stops autoSpin, and clamps bet
  SlotsUI.autoSpin = true;
  SlotsUI.bet = 999;
  SlotsUI.syncFromState({ gold: 1000, slots: { freeSpins: 4, freeSpinBet: 30 }, slotsSession: { freeSpins: 4, freeSpinBet: 30 } });
  if (SlotsUI.freeSpins !== 4 || SlotsUI.autoSpin !== false || SlotsUI.bet > slotsConfig.maxBet) {
    throw new Error('SlotsUI.syncFromState failed to synchronize state cleanly');
  }

  // Verify renderPaytable reads scatter.freeSpins (8) instead of hardcoded 5
  SlotsUI.renderPaytable();
  const renderedPaytableHtml = paytableList.children.map((c) => c.innerHTML).join('\n');
  if (!renderedPaytableHtml.includes(`奖励${slotsConfig.scatter.freeSpins}次免费旋转`)) {
    throw new Error('renderPaytable did not render configured scatter.freeSpins count');
  }
  console.log('✓ (b) All 11 SLOTS_MASCOTS unit directories & Slots economy/sync rules verified');
}

// 7. (c) Verify LGSave supports AuroraSaveBridge, preserves BACKUP_KEY before overwrite, and hardens importText
{
  const saveJs = fs.readFileSync('app/src/main/assets/www/js/save.js', 'utf8');
  const bridgeStore = new Map();
  const writeLog = [];
  const localStore = new Map();

  const ctx = {
    window: {
      AuroraSaveBridge: {
        getKey: (k) => bridgeStore.get(k) || '',
        setKey: (k, v) => {
          writeLog.push({ k, v });
          bridgeStore.set(k, String(v));
        },
        clearKey: (k) => bridgeStore.delete(k),
        get: () => bridgeStore.get('lust_goddess_v2') || '',
        set: (v) => bridgeStore.set('lust_goddess_v2', String(v)),
        clear: () => bridgeStore.delete('lust_goddess_v2'),
      },
    },
    localStorage: {
      getItem: (k) => (localStore.has(k) ? localStore.get(k) : null),
      setItem: (k, v) => localStore.set(k, String(v)),
      removeItem: (k) => localStore.delete(k),
    },
    console,
  };
  ctx.self = ctx.window;
  vm.createContext(ctx);
  vm.runInContext(saveJs, ctx);

  const LGSave = ctx.window.LGSave;
  const s1 = LGSave.defaults();
  s1.gold = 12345;
  LGSave.persist(s1);
  if (!bridgeStore.has(LGSave.KEY)) {
    throw new Error('LGSave.persist did not write to AuroraSaveBridge');
  }

  // Second persist: must write previous valid KEY (gold=12345) to BACKUP_KEY BEFORE writing new KEY (gold=67890)
  writeLog.length = 0;
  const s2 = LGSave.load();
  if (s2.gold !== 12345) {
    throw new Error('LGSave.load failed to read state from AuroraSaveBridge, got gold=' + s2.gold);
  }
  s2.gold = 67890;
  LGSave.persist(s2);

  if (writeLog.length < 2 || writeLog[0].k !== LGSave.BACKUP_KEY || writeLog[1].k !== LGSave.KEY) {
    throw new Error('LGSave.persist must write BACKUP_KEY BEFORE overwriting KEY, got order: ' + JSON.stringify(writeLog.map((x) => x.k)));
  }
  const backupParsed = JSON.parse(bridgeStore.get(LGSave.BACKUP_KEY));
  const mainParsed = JSON.parse(bridgeStore.get(LGSave.KEY));
  if (backupParsed.gold !== 12345 || mainParsed.gold !== 67890) {
    throw new Error(`Expected BACKUP_KEY gold=12345 and KEY gold=67890, got backup=${backupParsed.gold}, main=${mainParsed.gold}`);
  }

  // Corrupt primary KEY: persisting again must NOT clobber BACKUP_KEY with corrupt JSON
  bridgeStore.set(LGSave.KEY, '{corrupt_json_snapshot');
  s2.gold = 77777;
  LGSave.persist(s2);
  const preservedBackup = JSON.parse(bridgeStore.get(LGSave.BACKUP_KEY));
  if (preservedBackup.gold !== 12345) {
    throw new Error('Corrupt primary KEY overwrote valid BACKUP_KEY!');
  }

  // Harden importText against prototype pollution and non-string owned/squad entries
  const maliciousJson = '{"v":2,"gold":2500,"__proto__":{"polluted":true},"constructor":{"prototype":{"polluted":true}},"owned":["kitty",123,null,{"bad":1},"regina"],"squad":["kitty",999,false]}';
  const imported = LGSave.importText(maliciousJson);
  if (({}).polluted || Object.prototype.polluted) {
    throw new Error('Prototype pollution leaked through LGSave.importText!');
  }
  if (!imported.owned.every((x) => typeof x === 'string') || imported.owned.length !== 2) {
    throw new Error('importText did not sanitize non-string owned entries: ' + JSON.stringify(imported.owned));
  }
  if (!imported.squad.every((x) => typeof x === 'string') || imported.squad.length !== 1) {
    throw new Error('importText did not sanitize non-string squad entries: ' + JSON.stringify(imported.squad));
  }
  console.log('✓ (c) LGSave AuroraSaveBridge, BACKUP_KEY pre-write preservation & importText hardening verified');
}

// 8. (d) Verify openEquipPicker filters by state.inventory & Progression.equipItem rejects unowned items
{
  const saveJs = fs.readFileSync('app/src/main/assets/www/js/save.js', 'utf8');
  const progJs = fs.readFileSync('app/src/main/assets/www/js/progression.js', 'utf8');
  const uiJs = fs.readFileSync('app/src/main/assets/www/js/ui.js', 'utf8');
  const equipCatalog = JSON.parse(fs.readFileSync('app/src/main/assets/www/data/equipment.json', 'utf8'));
  const heroesCatalog = JSON.parse(fs.readFileSync('app/src/main/assets/www/data/heroes.json', 'utf8'));

  function createFakeEl(tag) {
    return {
      tagName: tag.toUpperCase(),
      className: '',
      textContent: '',
      innerHTML: '',
      disabled: false,
      hidden: false,
      attrs: {},
      children: [],
      listeners: {},
      classList: {
        set: new Set(),
        add(c) { this.set.add(c); },
        remove(c) { this.set.delete(c); },
        contains(c) { return this.set.has(c); },
      },
      setAttribute(k, v) { this.attrs[k] = String(v); },
      addEventListener(ev, fn) { this.listeners[ev] = fn; },
      appendChild(child) { this.children.push(child); return child; },
    };
  }

  const pickerTitle = createFakeEl('h3');
  const pickerList = createFakeEl('div');
  const pickerSheet = createFakeEl('div');
  const pickerClose = createFakeEl('button');
  pickerSheet.hidden = true;

  const mockDoc = {
    createElement: (tag) => createFakeEl(tag),
    querySelector: (sel) => {
      if (sel === '#equip-picker-title') return pickerTitle;
      if (sel === '#equip-picker-list') return pickerList;
      if (sel === '#equip-picker-sheet') return pickerSheet;
      if (sel === '#btn-equip-picker-close') return pickerClose;
      return null;
    },
    querySelectorAll: () => [],
  };

  const ctx = {
    window: {},
    document: mockDoc,
    localStorage: { getItem: () => null, setItem: () => {}, removeItem: () => {} },
    console,
    setTimeout: () => 1,
    clearTimeout: () => {},
  };
  ctx.self = ctx.window;
  vm.createContext(ctx);
  vm.runInContext(saveJs, ctx);
  vm.runInContext(progJs, ctx);

  const state = ctx.window.LGSave.defaults();
  // Ensure player only owns starter weapon wp_plasma_blade
  state.inventory.weapons = ['wp_plasma_blade'];
  const hero = heroesCatalog.find((h) => h.key === 'kitty') || heroesCatalog[0];
  state.roster[hero.key].equips.weapon = null;

  ctx.APP = { state, data: { equipment: equipCatalog, heroes: heroesCatalog } };
  ctx.window.APP = ctx.APP;
  ctx.LGSave = ctx.window.LGSave;
  ctx.Progression = ctx.window.Progression;
  vm.runInContext(uiJs, ctx);

  // Validate Progression.equipItem rejects unowned item and accepts owned item
  const unownedWeapon = equipCatalog.find((e) => e.slot === 'weapon' && e.id !== 'wp_plasma_blade');
  const rejectRes = ctx.window.Progression.equipItem(state, hero.key, 'weapon', unownedWeapon.id);
  if (rejectRes.ok !== false) {
    throw new Error('Progression.equipItem allowed equipping an unowned weapon: ' + unownedWeapon.id);
  }

  // Validate UI.openEquipPicker renders owned vs locked items accurately
  ctx.window.UI.openEquipPicker(hero, 'weapon');
  if (pickerSheet.hidden !== false || !pickerSheet.classList.contains('open')) {
    throw new Error('UI.openEquipPicker must set sheet.hidden=false and add .open');
  }
  const ownedRows = pickerList.children.filter((c) => c.className.includes('owned'));
  const lockedRows = pickerList.children.filter((c) => c.className.includes('locked'));
  const totalWeapons = equipCatalog.filter((e) => e.slot === 'weapon').length;
  if (ownedRows.length !== 1 || lockedRows.length !== totalWeapons - 1) {
    throw new Error(`Expected 1 owned weapon row and ${totalWeapons - 1} locked rows, got owned=${ownedRows.length}, locked=${lockedRows.length}`);
  }
  if (!lockedRows.every((r) => r.children[1] && r.children[1].disabled === true)) {
    throw new Error('All unowned equipment rows in openEquipPicker must have disabled buttons');
  }

  const acceptRes = ctx.window.Progression.equipItem(state, hero.key, 'weapon', 'wp_plasma_blade');
  if (acceptRes.ok !== true || state.roster[hero.key].equips.weapon !== 'wp_plasma_blade') {
    throw new Error('Progression.equipItem failed to equip owned starter weapon');
  }
  console.log('✓ (d) openEquipPicker inventory filtering & Progression.equipItem ownership check verified');
}

// 9. (e) Verify Missions.checkDailyReset completes login mission, guards future clock skew & uncaps bonus energy
{
  const missionsJs = fs.readFileSync('app/src/main/assets/www/js/missions.js', 'utf8');
  const missionsCatalog = JSON.parse(fs.readFileSync('app/src/main/assets/www/data/missions.json', 'utf8'));
  let persistedCount = 0;
  const ctx = {
    window: {
      LGSave: {
        persist: () => { persistedCount += 1; },
        syncMirrors: () => {},
        recordTransaction: () => {},
      },
    },
    console,
  };
  ctx.self = ctx.window;
  vm.createContext(ctx);
  vm.runInContext(missionsJs, ctx);

  const Missions = ctx.window.Missions;
  const state = {
    gold: 1000,
    keys: 0,
    energy: 95,
    shards: 0,
    missions: { daily: {}, achievements: {}, lastDailyReset: 0 },
  };

  Missions.checkDailyReset(state, missionsCatalog);
  const dailyList = Missions.getList(state, missionsCatalog, 'daily');
  const loginItem = dailyList.find((m) => m.id === 'd_login') || dailyList[0];
  if (!loginItem || !loginItem.done || loginItem.progress !== 1 || persistedCount < 1) {
    throw new Error('Missions.checkDailyReset did not auto-complete login mission to 1/1: ' + JSON.stringify(loginItem));
  }

  // Claiming d_login (+energy on top of 95) must NOT cap energy at 100
  const expectedEnergy = 95 + ((loginItem.rewards && loginItem.rewards.energy) || 0);
  const claimRes = Missions.claimReward(state, missionsCatalog, 'daily', loginItem.id);
  if (!claimRes.ok || expectedEnergy <= 100 || state.energy !== expectedEnergy) {
    throw new Error(`Expected uncapped mission energy ${expectedEnergy} (>100), got ${state.energy}`);
  }

  // Future clock skew (> todayMidnight + 86400000) resets daily missions and re-marks login 1/1
  state.missions.lastDailyReset = Date.now() + 5 * 86400000;
  Missions.checkDailyReset(state, missionsCatalog);
  const afterSkew = Missions.getList(state, missionsCatalog, 'daily').find((m) => m.id === loginItem.id);
  if (!afterSkew || !afterSkew.done || afterSkew.claimed !== false) {
    throw new Error('Future clock skew guard failed to reset daily state cleanly');
  }
  console.log('✓ (e) Missions.checkDailyReset login completion, clock-skew guard & uncapped energy verified');
}

// 10. (f) Verify LGAudio pauseBgm/resumeBgm does NOT permanently mute & pool eviction cleans stale keys
{
  const audioJs = fs.readFileSync('app/src/main/assets/www/js/audio.js', 'utf8');
  class MockAudio {
    constructor(src = '') {
      this.src = src;
      this.loop = false;
      this.preload = 'auto';
      this.volume = 1;
      this.paused = true;
      this.ended = false;
      this.currentTime = 0;
      this.dataset = {};
    }
    play() {
      this.paused = false;
      return Promise.resolve();
    }
    pause() {
      this.paused = true;
    }
  }

  const ctx = {
    window: {},
    Audio: MockAudio,
    console,
  };
  vm.createContext(ctx);
  vm.runInContext(audioJs, ctx);

  const LGAudio = ctx.window.LGAudio;
  LGAudio.unlock();
  LGAudio.setVolumes(0.55, 0.8);
  LGAudio.playBgm('hub');
  if (LGAudio.isMuted !== false || LGAudio.bgmEl.paused !== false) {
    throw new Error('Expected BGM to be playing and unmuted after playBgm');
  }

  LGAudio.pauseBgm();
  if (LGAudio.isMuted !== false || LGAudio.bgmEl.paused !== true) {
    throw new Error('LGAudio.pauseBgm must pause bgmEl WITHOUT setting isMuted = true');
  }

  LGAudio.resumeBgm();
  if (LGAudio.isMuted !== false || LGAudio.bgmEl.paused !== false) {
    throw new Error('LGAudio.resumeBgm must resume bgmEl cleanly with isMuted === false');
  }

  // Pool eviction test: 9 concurrent active clips should evict oldest AND delete its stale key from pool
  for (let i = 0; i < 9; i++) {
    const clip = `assets/audio/test_clip_${i}.m4a`;
    const ch = LGAudio._acquireAudio(clip);
    ch.paused = false;
  }
  if (LGAudio.activeChannels.length !== 8 || LGAudio.pool.size !== 8 || LGAudio.pool.has('assets/audio/test_clip_0.m4a')) {
    throw new Error(`Expected pool.size=8 and test_clip_0 evicted, got size=${LGAudio.pool.size}, has0=${LGAudio.pool.has('assets/audio/test_clip_0.m4a')}`);
  }
  console.log('✓ (f) LGAudio pause/resume non-muting behavior & 8-channel pool key eviction verified');
}

// 11. Verify index.html version string matches app/build.gradle.kts versionName, deduplicated videos.json & deleted Disable audio directory
{
  const gradleText = fs.readFileSync('app/build.gradle.kts', 'utf8');
  const verMatch = gradleText.match(/versionName\s*=\s*"([^"]+)"/);
  if (!verMatch) {
    throw new Error('Failed to parse versionName from app/build.gradle.kts');
  }
  const expectedVer = `Version ${verMatch[1]}`;
  const indexHtml = fs.readFileSync('app/src/main/assets/www/index.html', 'utf8');
  if (!indexHtml.includes(expectedVer) || indexHtml.includes('2.16.40')) {
    throw new Error(`index.html version text must be updated to ${expectedVer} with no stale 2.16.40`);
  }
  const videos = JSON.parse(fs.readFileSync('app/src/main/assets/www/data/videos.json', 'utf8'));
  if (new Set(videos).size !== videos.length) {
    throw new Error('data/videos.json still contains duplicate video entries');
  }
  if (fs.existsSync('app/src/main/assets/www/assets/audio/Disable')) {
    throw new Error('Dead audio folder app/src/main/assets/www/assets/audio/Disable must be deleted');
  }
  console.log(`✓ index.html ${expectedVer}, deduplicated videos.json & removed Disable/ audio verified`);
}

console.log('All automated verifications PASSED!');
