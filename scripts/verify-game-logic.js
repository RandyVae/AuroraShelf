const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('app/src/main/assets/www/js/slots.js', 'utf8');
const config = JSON.parse(fs.readFileSync('app/src/main/assets/www/data/slots.json', 'utf8'));
const context = {
  window: {},
  document: { getElementById: () => null },
  console,
  performance: { now: () => 0 },
  setTimeout,
  clearTimeout,
  requestAnimationFrame: (callback) => callback(0),
};
vm.createContext(context);
vm.runInContext(source, context);

const testConfig = { ...config, paylines: [[1, 1, 1, 1, 1]] };
const wildFollowedByDifferentSymbols = context.window.SlotsGame.spin(
  testConfig,
  20,
  0x1234,
  { totalSpins: 99 },
);
if (wildFollowedByDifferentSymbols.wins.length !== 0) {
  throw new Error('A leading Wild must not merge different symbols into one win');
}

const seededResult = context.window.SlotsGame.spin(testConfig, 20, 0x1234, { totalSpins: 99 });
const repeatedResult = context.window.SlotsGame.spin(testConfig, 20, 0x1234, { totalSpins: 99 });
if (JSON.stringify(seededResult) !== JSON.stringify(repeatedResult)) {
  throw new Error('Seeded Slot results must be deterministic');
}

// --- Regression: Free Spin 3x Multiplier Fixed-Seed (3285004900 -> 480, 3285006502 -> 438) ---
const fs1 = context.window.SlotsGame.spin(config, 20, 3285004900, { totalSpins: 3, isFreeSpin: true, freeSpins: 5 });
if (fs1.totalWin !== 480 || !fs1.wins.every((w) => w.isFreeSpin === true)) {
  throw new Error(`Free spin regression seed 3285004900 failed: totalWin ${fs1.totalWin}`);
}
const fs2 = context.window.SlotsGame.spin(config, 20, 3285006502, { totalSpins: 4, isFreeSpin: true, freeSpins: 4 });
if (fs2.totalWin !== 438 || !fs2.wins.every((w) => w.isFreeSpin === true)) {
  throw new Error(`Free spin regression seed 3285006502 failed: totalWin ${fs2.totalWin}`);
}

// --- Regression: Sheet open/close modal helper state ---
const fakeEl = {
  hidden: true,
  classList: {
    classes: new Set(),
    add(cls) { this.classes.add(cls); },
    remove(cls) { this.classes.delete(cls); },
    contains(cls) { return this.classes.has(cls); },
  },
};
context.window.SlotsUI.openSheet(fakeEl);
if (fakeEl.hidden !== false || !fakeEl.classList.contains('open')) {
  throw new Error('openSheet must set hidden=false and add open class');
}
context.window.SlotsUI.closeSheet(fakeEl);
if (fakeEl.hidden !== true || fakeEl.classList.contains('open')) {
  throw new Error('closeSheet must set hidden=true and remove open class');
}

// --- Regression: Bailout 3-claim threshold & Recharge branch ---
const mockApp = {
  state: {
    gold: 0,
    slotsSession: { bailoutClaims: 0 },
  },
};
context.window.APP = mockApp;
let openedId = null;
const origGetEl = context.document.getElementById;
context.document.getElementById = (id) => {
  return {
    id,
    hidden: true,
    classList: {
      add: () => { openedId = id; },
      remove: () => {},
    },
  };
};

// 0 claims -> modal-slots-bailout
mockApp.state.slotsSession.bailoutClaims = 0;
context.window.SlotsUI.handleOutOfGold();
if (openedId !== 'modal-slots-bailout') throw new Error(`Expected modal-slots-bailout, got ${openedId}`);

// 2 claims -> modal-slots-bailout
mockApp.state.slotsSession.bailoutClaims = 2;
context.window.SlotsUI.handleOutOfGold();
if (openedId !== 'modal-slots-bailout') throw new Error(`Expected modal-slots-bailout, got ${openedId}`);

// 3 claims -> modal-slots-recharge
mockApp.state.slotsSession.bailoutClaims = 3;
context.window.SlotsUI.handleOutOfGold();
if (openedId !== 'modal-slots-recharge') throw new Error(`Expected modal-slots-recharge, got ${openedId}`);

// --- Regression: QA Boost isolation check ---
const appJsSource = fs.readFileSync('app/src/main/assets/www/js/app.js', 'utf8');
if (appJsSource.includes('LGSave.applyQAFullTestResources(APP.state, APP.data.heroes, APP.data.equipment);\n      save();')) {
  throw new Error('Unconditional LGSave.applyQAFullTestResources found in app.js boot()');
}
if (!appJsSource.includes('window.__AURORA_QA_BOOST__ === true')) {
  throw new Error('QA Boost must be guarded by window.__AURORA_QA_BOOST__ or qa=1');
}

context.document.getElementById = origGetEl;
console.log('Game logic verification passed');
