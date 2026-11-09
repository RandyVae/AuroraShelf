/* Battle math — authored for the local edition.
 *
 * The original damage resolver could not be decompiled (see
 * docs/battle-recovery.md), so this module defines a single, documented
 * formula used by both the game and the offline balance simulator.
 *
 *   atk   = attack × 2 × (1 + 0.04·(L−1))
 *   maxHP = hp × 16 × (1 + 0.06·(L−1))
 *   dmg   = atk
 *         × skillMult        (basic 1.0 / skills from token value)
 *         × raceMult         (order > chaos > nature > order: 1.15 / 0.87)
 *         × classMult        (melee→ranged 1.10, ranged→melee 0.95, vs building 0.90)
 *         × positionMult     (ranged fighting at 60%+ of range: 1.05)
 *         × leaderMult       (warlord aura: allies +8%)
 *         × critMult         (1.60, chance by rarity/class)
 *         × variance         (0.92–1.08)
 *         × (1 − mitigation) (melee 12%, ranged 6%, building 20%)
 *
 * Values marked "authored" are balance choices, not recovered constants; the
 * coefficients 2 (attack_power_coef) and 1 (hp_power_coef) come from the
 * shipped Constants table.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.Balance = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const DEFAULTS = {
    attackCoef: 2.0,
    enemyAtkBase: 300,
    enemyAtkRange: 200,
    enemyHpBase: 3000,
    enemyHpRange: 500,
    hpCoef: 16.0,
    attackPerLevel: 0.04,
    hpPerLevel: 0.06,
    enemyLevelAtk: 0.01,
    enemyLevelHp: 0.02,
    raceBeats: { order: 'chaos', chaos: 'nature', nature: 'order' },
    raceWin: 1.15,
    raceLose: 0.87,
    classMod: { melee_vs_ranged: 1.10, ranged_vs_melee: 0.95, vs_building: 0.90 },
    mitigation: { melee: 0.12, ranged: 0.06, building: 0.20 },
    critBase: 0.08,
    critRarity: { legendary: 0.03, epic: 0.015, rare: 0.005, common: 0 },
    critRanged: 0.02,
    critMult: 1.6,
    dodgeBase: 0.03,
    dodgeRanged: 0.03,
    leaderAtkAura: 0.08,
    leaderHpBonus: 0.15,
    positionRangedBonus: 1.05,
    positionRangedMinFrac: 0.6,
    varianceMin: 0.92,
    varianceMax: 1.08,
    skillMultCap: 3.0,
    skillMultFloor: 0.25,
    basicMult: 1.0,
    eliteStageMult: 1.12,
    chapterGrowth: 0.06,
  };

  let C = JSON.parse(JSON.stringify(DEFAULTS));

  function configure(partial) {
    C = Object.assign(C, partial || {});
    return C;
  }

  function statsFor(kind, source, level, opts) {
    opts = opts || {};
    const L = Math.max(1, level || 1);
    const elite = opts.elite ? C.eliteStageMult : 1;
    let atk;
    let hp;
    if (kind === 'hero') {
      atk = source.atk * C.attackCoef * (1 + C.attackPerLevel * (L - 1));
      hp = source.hp * C.hpCoef * (1 + C.hpPerLevel * (L - 1));
      if (opts.leader) {
        hp *= 1 + C.leaderHpBonus;
        atk *= 1.03;
      }
    } else {
      const rank = typeof source.rank === 'number' ? source.rank : 0.5;
      const chapterMult = 1 + C.chapterGrowth * Math.max(0, (opts.chapter || 1) - 1);
      atk = (C.enemyAtkBase + C.enemyAtkRange * rank) * (1 + C.enemyLevelAtk * (L - 1)) * elite * chapterMult;
      hp = (C.enemyHpBase + C.enemyHpRange * rank) * (1 + C.enemyLevelHp * (L - 1)) * elite * chapterMult;
    }
    return { atk: Math.round(atk), maxHp: Math.round(hp) };
  }

  function raceMult(attackerRace, defenderRace) {
    if (!attackerRace || !defenderRace || attackerRace === defenderRace) return 1;
    if (C.raceBeats[attackerRace] === defenderRace) return C.raceWin;
    if (C.raceBeats[defenderRace] === attackerRace) return C.raceLose;
    return 1;
  }

  function classMult(attackerClass, defenderClass) {
    if (defenderClass === 'building') return C.classMod.vs_building;
    if (attackerClass === 'melee' && defenderClass === 'ranged') return C.classMod.melee_vs_ranged;
    if (attackerClass === 'ranged' && defenderClass === 'melee') return C.classMod.ranged_vs_melee;
    return 1;
  }

  function critChance(unit) {
    const rarity = unit.rarity || 'rare';
    return Math.min(0.5, C.critBase + (C.critRarity[rarity] || 0) + (unit.class === 'ranged' ? C.critRanged : 0));
  }

  function dodgeChance(unit) {
    return C.dodgeBase + (unit.class === 'ranged' ? C.dodgeRanged : 0);
  }

  function mitigation(defenderClass) {
    return C.mitigation[defenderClass] != null ? C.mitigation[defenderClass] : 0;
  }

  /**
   * Resolve one hit. Returns {damage, crit, dodged, mult}.
   * All inputs are plain objects: {atk, class, race, rarity, leader, rangeFrac}
   */
  function computeDamage(attacker, defender, opts) {
    opts = opts || {};
    const skillMult = Math.max(C.skillMultFloor, Math.min(C.skillMultCap, opts.mult == null ? C.basicMult : opts.mult));
    const rnd = opts.rng || Math.random;
    if (rnd() < dodgeChance(defender)) return { damage: 0, crit: false, dodged: true, mult: skillMult };
    let dmg = attacker.atk * skillMult;
    dmg *= raceMult(attacker.race, defender.race);
    dmg *= classMult(attacker.class, defender.class);
    if (attacker.class === 'ranged' && (attacker.rangeFrac || 0) >= C.positionRangedMinFrac) {
      dmg *= C.positionRangedBonus;
    }
    if (attacker.leaderAura) dmg *= 1 + C.leaderAtkAura;
    let crit = false;
    const effectiveCritChance = (attacker.critChance != null) ? attacker.critChance : critChance(attacker);
    if (rnd() < effectiveCritChance) {
      crit = true;
      dmg *= C.critMult;
    }
    const v = C.varianceMin + rnd() * (C.varianceMax - C.varianceMin);
    dmg *= v;
    dmg *= 1 - mitigation(defender.class);
    return { damage: Math.max(1, Math.round(dmg)), crit, dodged: false, mult: skillMult };
  }

  function skillMultiplier(action, value) {
    const v = Number(value) || 0;
    if (action === 'damage' || action === 'lifesteal' || action === 'steal') {
      if (v <= 0) return C.basicMult;
      return Math.max(C.skillMultFloor, Math.min(C.skillMultCap, v / 100));
    }
    if (action === 'heal' || action === 'shield' || action === 'buff_atk' || action === 'debuff_atk' || action === 'buff_hp') {
      return v > 0 ? Math.min(2.5, v / 100) : 0.3;
    }
    return 1;
  }

  return {
    DEFAULTS,
    configure,
    config: () => C,
    heroStats: (hero, level, opts) => statsFor('hero', hero, level, opts),
    enemyStats: (enemy, level, opts) => statsFor('enemy', enemy, level, opts),
    computeDamage,
    skillMultiplier,
    critChance,
    dodgeChance,
    mitigation,
    raceMult,
    classMult,
  };
});
