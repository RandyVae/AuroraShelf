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

// --- P1: Free Spin 3x Multiplier Fixed-Seed Regressions (seeds 3285004900 & 3285006502) ---
const freeSpinRes1 = spin(config, 20, 3285004900, { totalSpins: 3, isFreeSpin: true, freeSpins: 5 });
if (freeSpinRes1.totalWin !== 480) {
  throw new Error(`Free spin regression failed for seed 3285004900: expected totalWin 480, got ${freeSpinRes1.totalWin}`);
}
if (!freeSpinRes1.wins.every((w) => w.isFreeSpin === true)) {
  throw new Error('All wins during free spin must have isFreeSpin === true');
}

const freeSpinRes2 = spin(config, 20, 3285006502, { totalSpins: 4, isFreeSpin: true, freeSpins: 4 });
if (freeSpinRes2.totalWin !== 438) {
  throw new Error(`Free spin regression failed for seed 3285006502: expected totalWin 438, got ${freeSpinRes2.totalWin}`);
}
if (!freeSpinRes2.wins.every((w) => w.isFreeSpin === true)) {
  throw new Error('All wins during free spin must have isFreeSpin === true');
}
console.log('PASS: Free spin 3x multiplier regressions passed (seeds 3285004900 -> 480, 3285006502 -> 438).');

// --- P2: Jackpot Settlement Rules & Payout Alignment ---
// 1. Grand Jackpot: 5 Wilds on any payline
const grandConfig = Object.assign({}, config, {
  paylines: [[1, 1, 1, 1, 1]],
  symbols: config.symbols.map((s) => s.id === 'wild' ? Object.assign({}, s, { weight: 100 }) : Object.assign({}, s, { weight: 0 })),
});
const grandResult = spin(grandConfig, 20, 101);
if (!grandResult.isJackpot || !grandResult.jackpot || grandResult.jackpot.tier !== 'GRAND' || grandResult.jackpot.bonus !== 50000) {
  throw new Error(`Grand jackpot settlement failed: ${JSON.stringify(grandResult.jackpot)}`);
}
if (grandResult.totalWin < 50000) {
  throw new Error('Grand jackpot totalWin must include 50000 bonus');
}

// 2. Major Jackpot: 5 Hilda Boss
const majorConfig = Object.assign({}, config, {
  paylines: [[1, 1, 1, 1, 1]],
  symbols: config.symbols.map((s) => s.id === 'hilda_enemy' ? Object.assign({}, s, { weight: 100 }) : Object.assign({}, s, { weight: 0 })),
});
const majorResult = spin(majorConfig, 20, 102);
if (!majorResult.isJackpot || !majorResult.jackpot || majorResult.jackpot.tier !== 'MAJOR' || majorResult.jackpot.bonus !== 10000) {
  throw new Error(`Major jackpot settlement failed: ${JSON.stringify(majorResult.jackpot)}`);
}

// 3. Minor Jackpot: 5 Hero (Kitty)
const minorConfig = Object.assign({}, config, {
  paylines: [[1, 1, 1, 1, 1]],
  symbols: config.symbols.map((s) => s.id === 'kitty' ? Object.assign({}, s, { weight: 100 }) : Object.assign({}, s, { weight: 0 })),
});
const minorResult = spin(minorConfig, 20, 103);
if (!minorResult.isJackpot || !minorResult.jackpot || minorResult.jackpot.tier !== 'MINOR' || minorResult.jackpot.bonus !== 2000) {
  throw new Error(`Minor jackpot settlement failed: ${JSON.stringify(minorResult.jackpot)}`);
}
console.log('PASS: Jackpot settlement rules (GRAND 50000 / MAJOR 10000 / MINOR 2000) validated.');
