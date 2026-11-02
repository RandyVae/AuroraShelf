// Offline balance simulator: replays the lane fight without rendering.
// Usage: node tools/sim_balance.js [runsPerStage]
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const Balance = require(path.join(root, 'web/js/balance.js'));
const heroes = JSON.parse(fs.readFileSync(path.join(root, 'web/data/heroes.json'), 'utf8'));
const chapters = JSON.parse(fs.readFileSync(path.join(root, 'web/data/chapters.json'), 'utf8'));
const byKey = new Map(heroes.map((h) => [h.key, h]));

const RUNS = Number(process.argv[2] || 120);
const LANE_Y = [0, 1, 2];
const PLAYER_X = [120, 170, 220];
const ENEMY_X = [880, 830, 780];
const W = 1000;

function makeUnit(kind, src, level, lane, side, leader, chapter) {
  const st = kind === 'hero' ? Balance.heroStats(src, level, { leader }) : Balance.enemyStats(src, level, { elite: false, chapter });
  const isRanged = src.class === 'ranged';
  return {
    kind, src, side, lane, x: side === 'ally' ? PLAYER_X[lane] : ENEMY_X[lane],
    atk: st.atk, hp: st.maxHp, maxHp: st.maxHp, shield: 0,
    range: isRanged ? W * 0.30 : W * 0.065,
    speed: W * (isRanged ? 0.052 : 0.075),
    cd: 0.6 + Math.random() * 0.5,
    class: src.class, race: src.race, rarity: src.rarity, leader,
  };
}

function enemiesOf(list, u) { return list.filter((o) => o.side !== u.side && o.hp > 0); }
function pickTarget(list, u) {
  const foes = enemiesOf(list, u);
  if (!foes.length) return null;
  const same = foes.filter((f) => f.lane === u.lane);
  const pool = same.length ? same : foes;
  let best = null, bd = Infinity;
  for (const f of pool) { const d = Math.abs(f.x - u.x); if (d < bd) { bd = d; best = f; } }
  return best;
}

function applyDamage(att, def, mult) {
  if (Balance.dodgeChance(def) > Math.random()) return;
  const res = Balance.computeDamage(
    { atk: att.atk, class: att.class, race: att.race, rarity: att.rarity, leaderAura: att.leader, rangeFrac: 0.5 },
    { class: def.class, race: def.race }, { mult });
  let dmg = res.damage;
  if (def.shield > 0) { const a = Math.min(def.shield, dmg); def.shield -= a; dmg -= a; }
  def.hp -= dmg;
}

function simulate(stage, squad, level, chapter) {
  stage.__chapter = chapter;
  const units = [];
  squad.forEach((h, i) => units.push(makeUnit('hero', h, level, i, 'ally', i === 0)));
  stage.enemy.forEach((e, i) => units.push(makeUnit('enemy', e, stage.level, i, 'enemy', false, stage.__chapter)));
  let t = 0;
  while (t < 120) {
    t += 0.1;
    for (const u of units) {
      if (u.hp <= 0) continue;
      u.cd -= 0.1;
      const target = pickTarget(units, u);
      if (!target) continue;
      const dist = Math.abs(target.x - u.x);
      if (dist > u.range) {
        const dir = target.x > u.x ? 1 : -1;
        u.x += dir * Math.min(u.speed * 0.1, dist - u.range);
        continue;
      }
      if (u.cd > 0) continue;
      u.cd = 1.05 + Math.random() * 0.35;
      const buff = 1;
      applyDamage(u, target, buff);
      if (units.filter((x) => x.side === 'enemy').every((x) => x.hp <= 0)) return { win: true, time: t };
      if (units.filter((x) => x.side === 'ally').every((x) => x.hp <= 0)) return { win: false, time: t };
    }
  }
  return { win: false, time: t };
}

function run(runs) {
  const squad = ['kitty', 'regina', 'mitsuki'].map((k) => byKey.get(k)).filter(Boolean);
  const report = [];
  let totalWins = 0, totalRuns = 0;
let cleared = 0;
for (const ch of chapters) {
  let wins = 0, time = 0, runs = 0;
  for (const st of ch.stages) {
    const level = Math.min(30, 1 + Math.floor(cleared / 2));
    cleared++;
    for (let i = 0; i < RUNS; i++) {
      const r = simulate(st, squad, level, ch.id);
      if (r.win) { wins++; time += r.time; }
      runs++;
    }
  }
  totalWins += wins; totalRuns += runs;
  report.push({ chapter: ch.id, win: +(wins / runs * 100).toFixed(1), avgWinTime: wins ? +(time / wins).toFixed(1) : null });
}
  return { runs, overall: +(totalWins / totalRuns * 100).toFixed(1), chapters: report };
}

if (require.main === module) {
  const squad = ['kitty', 'regina', 'mitsuki'].map((k) => byKey.get(k)).filter(Boolean);
  const r = run(RUNS);
  console.log(JSON.stringify(r, null, 1));
}

module.exports = { run, simulate, Balance, heroes, chapters, byKey };
