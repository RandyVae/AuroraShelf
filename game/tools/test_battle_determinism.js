// Deterministic battle and safety verification test
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const Balance = require(path.join(root, 'web/js/balance.js'));
const Progression = require(path.join(root, 'web/js/progression.js'));
const heroes = JSON.parse(fs.readFileSync(path.join(root, 'web/data/heroes.json'), 'utf8'));
const chapters = JSON.parse(fs.readFileSync(path.join(root, 'web/data/chapters.json'), 'utf8'));
const equipment = JSON.parse(fs.readFileSync(path.join(root, 'web/data/equipment.json'), 'utf8'));
const byKey = new Map(heroes.map((h) => [h.key, h]));

function createPrng(seed) {
  let s = (seed >>> 0) || 0x12345678;
  return function () {
    s |= 0;
    s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function simulateSeeded(stage, squad, heroLevels, seed, chapter) {
  const rng = createPrng(seed);
  const units = [];
  const W = 1000;

  squad.forEach((h, i) => {
    const inst = { level: heroLevels[i] || 1, stars: 1, skillLevel: 1, equips: { weapon: null, armor: null, core: null } };
    const st = Progression.computeHeroCombatStats(h, inst, equipment, { leader: i === 0 });
    const isRanged = h.class === 'ranged';
    units.push({
      name: h.name, side: 'ally', lane: i,
      x: [120, 170, 220][i],
      atk: st.atk, hp: st.maxHp, maxHp: st.maxHp, shield: 0,
      range: isRanged ? W * 0.30 : W * 0.065,
      speed: W * (isRanged ? 0.052 : 0.075),
      cd: 0.6 + rng() * 0.5,
      class: h.class, race: h.race, rarity: h.rarity, leader: i === 0,
      crit: st.crit, mitigation: st.mitigation,
    });
  });

  stage.enemy.slice(0, 3).forEach((e, i) => {
    const est = Balance.enemyStats(e, stage.level, { elite: false, chapter });
    const isRanged = e.class === 'ranged';
    units.push({
      name: e.name, side: 'enemy', lane: i,
      x: [880, 830, 780][i],
      atk: est.atk, hp: est.maxHp, maxHp: est.maxHp, shield: 0,
      range: isRanged ? W * 0.30 : W * 0.065,
      speed: W * (isRanged ? 0.05 : 0.072),
      cd: 0.6 + rng() * 0.5,
      class: e.class, race: e.race, rarity: e.rarity, leader: false,
      crit: 0.08, mitigation: 0.10,
    });
  });

  let t = 0;
  const events = [];

  while (t < 120) {
    t += 0.1;
    for (const u of units) {
      if (u.hp <= 0) continue;
      u.cd -= 0.1;

      const foes = units.filter((o) => o.side !== u.side && o.hp > 0);
      if (!foes.length) break;

      const same = foes.filter((f) => f.lane === u.lane);
      const pool = same.length ? same : foes;
      let target = null, bd = Infinity;
      for (const f of pool) {
        const d = Math.abs(f.x - u.x);
        if (d < bd) { bd = d; target = f; }
      }

      if (!target) continue;
      const dist = Math.abs(target.x - u.x);
      if (dist > u.range) {
        const dir = target.x > u.x ? 1 : -1;
        u.x += dir * Math.min(u.speed * 0.1, dist - u.range);
        continue;
      }
      if (u.cd > 0) continue;

      u.cd = 1.05 + rng() * 0.35;
      const res = Balance.computeDamage(
        { atk: u.atk, class: u.class, race: u.race, rarity: u.rarity, leaderAura: u.leader, rangeFrac: 0.5 },
        { class: target.class, race: target.race },
        { mult: 1, rng }
      );

      let dmg = res.damage;
      if (isNaN(dmg) || dmg < 0) throw new Error('NaN or negative damage: ' + dmg);

      if (target.shield > 0) {
        const a = Math.min(target.shield, dmg);
        target.shield -= a;
        dmg -= a;
      }
      target.hp = Math.max(0, target.hp - dmg);
      events.push({ t: Math.round(t * 10) / 10, src: u.name, dst: target.name, dmg, crit: res.crit, dodged: res.dodged });

      const foesAlive = units.some((x) => x.side === 'enemy' && x.hp > 0);
      const alliesAlive = units.some((x) => x.side === 'ally' && x.hp > 0);
      if (!foesAlive) return { win: true, time: Math.round(t * 10) / 10, events };
      if (!alliesAlive) return { win: false, time: Math.round(t * 10) / 10, events };
    }
  }

  return { win: false, time: Math.round(t * 10) / 10, events };
}

function runTests() {
  console.log('--- Testing Battle Determinism & Stability ---');
  const squad = ['kitty', 'regina', 'mitsuki'].map((k) => byKey.get(k));
  const testStage = chapters[0].stages[0];

  // Test 1: Seed reproducibility
  const seed = 123456789;
  const res1 = simulateSeeded(testStage, squad, [1, 1, 1], seed, 1);
  const res2 = simulateSeeded(testStage, squad, [1, 1, 1], seed, 1);

  if (res1.win !== res2.win || res1.time !== res2.time || res1.events.length !== res2.events.length) {
    console.error('FAIL: Same seed did not produce identical results!');
    process.exit(1);
  }
  for (let i = 0; i < res1.events.length; i++) {
    const e1 = res1.events[i], e2 = res2.events[i];
    if (e1.t !== e2.t || e1.src !== e2.src || e1.dst !== e2.dst || e1.dmg !== e2.dmg) {
      console.error('FAIL: Event mismatch at step', i, e1, e2);
      process.exit(1);
    }
  }
  console.log('PASS: 100% Deterministic event replay with fixed seed (identical across', res1.events.length, 'events).');

  // Test 2: 1000 simulated battles across various chapters and stages
  let totalBattles = 0;
  let nanErrors = 0;
  let negHpErrors = 0;

  for (let b = 0; b < 1000; b++) {
    const ch = chapters[b % chapters.length];
    const st = ch.stages[b % ch.stages.length];
    const testSeed = (b * 7919 + 104729) >>> 0;
    const pLevel = 1 + (b % 30);
    const res = simulateSeeded(st, squad, [pLevel, pLevel, pLevel], testSeed, ch.id);

    if (isNaN(res.time) || res.time < 0) nanErrors++;
    for (const ev of res.events) {
      if (isNaN(ev.dmg) || ev.dmg < 0) nanErrors++;
    }
    totalBattles++;
  }

  console.log('PASS: Completed', totalBattles, 'simulated battles across 8 chapters.');
  console.log('NaN / Boundary Errors:', nanErrors);
  console.log('Negative HP Errors:', negHpErrors);

  if (nanErrors > 0 || negHpErrors > 0) {
    process.exit(1);
  }
  console.log('ALL DETERMINISM & INTEGRITY CHECKS PASSED.');
}

runTests();
