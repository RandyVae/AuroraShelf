#!/usr/bin/env node
/* Slots engine regression checks: fixed-seed replay, payout bounds, and scatter rules. */
const fs = require('fs');
const vm = require('vm');

const slotsPath = fs.existsSync('app/src/main/assets/www/data/slots.json') ? 'app/src/main/assets/www/data/slots.json' : 'web/data/slots.json';
const jsPath = fs.existsSync('app/src/main/assets/www/js/slots.js') ? 'app/src/main/assets/www/js/slots.js' : 'web/js/slots.js';
const config = JSON.parse(fs.readFileSync(slotsPath, 'utf8'));
const context = { window: {}, console };
vm.runInNewContext(fs.readFileSync(jsPath, 'utf8'), context);
const spin = context.window.SlotsGame.spin;

function stable(value) { return JSON.stringify(value); }
let checked = 0;
for (let seed = 1; seed <= 1000; seed += 1) {
  const a = spin(config, 20, seed);
  const b = spin(config, 20, seed);
  if (stable(a) !== stable(b)) throw new Error(`seed ${seed} replay mismatch`);
  if (a.totalWin < 0 || a.wins.some((w) => w.amount < 0)) throw new Error(`seed ${seed} negative payout`);
  if (a.grid.length !== 3 || a.grid.some((row) => row.length !== 5)) throw new Error(`seed ${seed} invalid grid`);
  checked += 1;
}

const highBet = spin(config, config.maxBet, 0xC0FFEE);
if (highBet.totalWin < 0) throw new Error('max bet produced invalid payout');
const scatter = Object.assign({}, config, {
  symbols: config.symbols.map((s) => s.id === 'scatter' ? Object.assign({}, s, { weight: 100 }) : Object.assign({}, s, { weight: 0 })),
});
const scatterResult = spin(scatter, 20, 42);
if (scatterResult.scatterCount !== 15 || scatterResult.freeSpinsAwarded !== config.scatter.freeSpins) throw new Error('scatter rule failed');

const twoMatch = Object.assign({}, config, {
  reels: 2,
  paylines: [[0, 0]],
  symbols: config.symbols.map((s) => s.id === 'kitty' ? Object.assign({}, s, { weight: 100 }) : Object.assign({}, s, { weight: 0 })),
});
const twoMatchResult = spin(twoMatch, 20, 7);
if (twoMatchResult.bestCombo !== 2 || twoMatchResult.totalWin <= 0) throw new Error('two-match payout rule failed');

const allLineWins = Object.assign({}, config, {
  symbols: config.symbols.map((s) => s.id === 'kitty' ? Object.assign({}, s, { weight: 1 }) : Object.assign({}, s, { weight: 0 })),
});
const nineLineResult = spin(allLineWins, 20, 99);
if (nineLineResult.wins.length !== config.paylines.length || nineLineResult.wins.some((win) => win.count !== config.reels)) {
  throw new Error('all nine paylines must read one symbol per reel');
}

console.log(`PASS: ${checked} fixed-seed Slots replays are identical.`);
console.log('PASS: 2-match payout, payout bounds, 5x3 grid shape, max bet, and scatter rules validated.');
console.log('PASS: all nine paylines evaluate across all five reels.');
