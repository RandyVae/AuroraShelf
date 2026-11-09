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

console.log('Game logic verification passed');
