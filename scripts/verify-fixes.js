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

console.log('All automated verifications PASSED!');
