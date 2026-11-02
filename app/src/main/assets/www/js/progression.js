/* Character progression, equipment, and sweep domain service. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.Progression = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const MAX_LEVEL = 30;
  const MAX_STARS = 5;
  const MAX_SKILL_LVL = 10;

  const STAR_COSTS = {
    1: 20,   // 1★ -> 2★
    2: 40,   // 2★ -> 3★
    3: 80,   // 3★ -> 4★
    4: 150,  // 4★ -> 5★
  };

  function levelCost(currentLvl) {
    if (currentLvl >= MAX_LEVEL) return null;
    return {
      gold: Math.round(150 * currentLvl * (1 + 0.05 * currentLvl)),
    };
  }

  function starBreakthroughCost(currentStars) {
    if (currentStars >= MAX_STARS) return null;
    return {
      shards: STAR_COSTS[currentStars] || 100,
      gold: currentStars * 1000,
    };
  }

  function skillUpgradeCost(currentSkillLvl) {
    if (currentSkillLvl >= MAX_SKILL_LVL) return null;
    return {
      gold: currentSkillLvl * 400,
      shards: currentSkillLvl * 5,
    };
  }

  function getHeroInstance(state, heroKey) {
    if (!state.roster) state.roster = {};
    if (!state.roster[heroKey]) {
      state.roster[heroKey] = {
        heroKey,
        level: 1,
        xp: 0,
        stars: 1,
        skillLevel: 1,
        equips: { weapon: null, armor: null, core: null },
      };
    }
    return state.roster[heroKey];
  }

  function computeHeroCombatStats(hero, instance, equipCatalog, opts) {
    opts = opts || {};
    const lvl = (instance && instance.level) || 1;
    const stars = (instance && instance.stars) || 1;
    const skillLvl = (instance && instance.skillLevel) || 1;

    // Base math aligned with Balance formula
    let baseAtk = hero.atk * 2.0 * (1 + 0.04 * (lvl - 1));
    let baseHp = hero.hp * 16.0 * (1 + 0.06 * (lvl - 1));

    // Star boost: +10% per star above 1
    const starMult = 1 + 0.10 * (stars - 1);
    baseAtk *= starMult;
    baseHp *= starMult;

    // Leader boost
    if (opts.leader) {
      baseHp *= 1.15;
      baseAtk *= 1.03;
    }

    let addAtk = 0;
    let addHp = 0;
    let crit = 0.08 + (hero.rarity === 'legendary' ? 0.03 : hero.rarity === 'epic' ? 0.015 : 0.005);
    if (hero.class === 'ranged') crit += 0.02;
    let mitigation = (hero.class === 'melee' ? 0.12 : hero.class === 'ranged' ? 0.06 : 0.20);

    // Apply equipment and set bonuses
    const activeSets = [];
    if (instance && instance.equips && equipCatalog) {
      const setCounts = {};
      ['weapon', 'armor', 'core'].forEach((slot) => {
        const eqId = instance.equips[slot];
        if (eqId) {
          const item = equipCatalog.find((x) => x.id === eqId);
          if (item) {
            if (item.atk) addAtk += item.atk;
            if (item.hp) addHp += item.hp;
            if (item.crit) crit += item.crit;
            if (item.mitigation) mitigation += item.mitigation;
            if (item.setKey) {
              setCounts[item.setKey] = (setCounts[item.setKey] || 0) + 1;
            }
          }
        }
      });

      // Apply Set Bonuses
      Object.keys(setCounts).forEach((key) => {
        const count = setCounts[key];
        if (count >= 2) {
          if (key === 'set_striker') {
            addAtk += Math.round(baseAtk * 0.15);
            activeSets.push('突击者 2件套 (+15% 攻击)');
            if (count >= 3) {
              crit += 0.10;
              addAtk += Math.round(baseAtk * 0.10);
              activeSets.push('突击者 3件套 (+10% 暴击, +25% 攻击)');
            }
          } else if (key === 'set_nano') {
            addHp += Math.round(baseHp * 0.20);
            activeSets.push('纳米生化 2件套 (+20% 生命)');
            if (count >= 3) {
              mitigation += 0.08;
              addHp += Math.round(baseHp * 0.10);
              activeSets.push('纳米生化 3件套 (+8% 减伤, 持续自愈)');
            }
          } else if (key === 'set_quantum') {
            crit += 0.12;
            activeSets.push('量子终结 2件套 (+12% 暴击)');
            if (count >= 3) {
              crit += 0.08;
              addAtk += Math.round(baseAtk * 0.15);
              activeSets.push('量子终结 3件套 (+20% 暴击, 致命伤害)');
            }
          }
        }
      });
    }

    const finalAtk = Math.round(baseAtk + addAtk);
    const finalHp = Math.round(baseHp + addHp);
    const skillMult = 1 + 0.05 * (skillLvl - 1);

    // Combat power formula
    const power = Math.round(finalAtk * 2.4 + finalHp * 0.22 * (1 + crit * 0.7 + mitigation * 1.1));

    return {
      atk: finalAtk,
      maxHp: finalHp,
      hp: finalHp,
      crit: Math.min(0.60, crit),
      mitigation: Math.min(0.50, mitigation),
      power,
      skillMult,
      level: lvl,
      stars,
      skillLevel: skillLvl,
      activeSets,
    };
  }

  function levelUp(state, heroKey) {
    const inst = getHeroInstance(state, heroKey);
    const cost = levelCost(inst.level);
    if (!cost) return { ok: false, msg: '已达等级上限 Lv.' + MAX_LEVEL };
    if (state.gold < cost.gold) return { ok: false, msg: '金币不足，需要 ' + cost.gold.toLocaleString() + ' 金币' };

    state.gold -= cost.gold;
    inst.level += 1;
    if (typeof window !== 'undefined' && window.LGSave && window.LGSave.recordTransaction) {
      window.LGSave.recordTransaction(state, 'gold', -cost.gold, 'hero_levelup:' + heroKey);
    }
    return { ok: true, level: inst.level, cost: cost.gold };
  }

  function starBreakthrough(state, heroKey) {
    const inst = getHeroInstance(state, heroKey);
    const cost = starBreakthroughCost(inst.stars);
    if (!cost) return { ok: false, msg: '已达最高星级 5★' };
    if (state.shards < cost.shards) {
      return { ok: false, msg: '碎片不足，需要 ' + cost.shards + ' 碎片（当前 ' + state.shards + '）' };
    }
    if (state.gold < cost.gold) {
      return { ok: false, msg: '金币不足，需要 ' + cost.gold.toLocaleString() + ' 金币' };
    }

    state.shards -= cost.shards;
    state.gold -= cost.gold;
    inst.stars += 1;
    if (typeof window !== 'undefined' && window.LGSave && window.LGSave.recordTransaction) {
      window.LGSave.recordTransaction(state, 'shards', -cost.shards, 'star_breakthrough:' + heroKey);
      window.LGSave.recordTransaction(state, 'gold', -cost.gold, 'star_breakthrough:' + heroKey);
    }
    return { ok: true, stars: inst.stars };
  }

  function upgradeSkill(state, heroKey) {
    const inst = getHeroInstance(state, heroKey);
    const cost = skillUpgradeCost(inst.skillLevel);
    if (!cost) return { ok: false, msg: '技能已达最高等级 Lv.' + MAX_SKILL_LVL };
    if (state.gold < cost.gold) return { ok: false, msg: '金币不足，需要 ' + cost.gold.toLocaleString() + ' 金币' };
    if (state.shards < cost.shards) return { ok: false, msg: '碎片不足，需要 ' + cost.shards + ' 碎片' };

    state.gold -= cost.gold;
    state.shards -= cost.shards;
    inst.skillLevel += 1;
    if (typeof window !== 'undefined' && window.LGSave && window.LGSave.recordTransaction) {
      window.LGSave.recordTransaction(state, 'gold', -cost.gold, 'skill_upgrade:' + heroKey);
      window.LGSave.recordTransaction(state, 'shards', -cost.shards, 'skill_upgrade:' + heroKey);
    }
    return { ok: true, skillLevel: inst.skillLevel };
  }

  function equipItem(state, heroKey, slot, itemId) {
    const inst = getHeroInstance(state, heroKey);
    const current = inst.equips[slot];
    // Un-equip from whoever is wearing it
    for (const k in state.roster) {
      if (state.roster[k].equips && state.roster[k].equips[slot] === itemId) {
        state.roster[k].equips[slot] = null;
      }
    }
    inst.equips[slot] = itemId;
    return { ok: true, previous: current };
  }

  function computeFactionSynergy(squadHeroes) {
    const counts = { order: 0, chaos: 0, nature: 0 };
    (squadHeroes || []).forEach((h) => {
      if (h && h.race && counts[h.race] !== undefined) counts[h.race]++;
    });
    const synergies = [];
    if (counts.order >= 3) {
      synergies.push({ race: 'order', count: counts.order, tier: 3, name: '秩序正义', desc: '全队生命 +20%、免伤 +10%、开局护盾 +15%', hpBonus: 0.20, mitBonus: 0.10, startShieldPct: 0.15 });
    } else if (counts.order >= 2) {
      synergies.push({ race: 'order', count: counts.order, tier: 2, name: '秩序同盟', desc: '全队生命 +10%、免伤 +5%', hpBonus: 0.10, mitBonus: 0.05, startShieldPct: 0 });
    }
    if (counts.chaos >= 3) {
      synergies.push({ race: 'chaos', count: counts.chaos, tier: 3, name: '混沌湮灭', desc: '全队攻击 +20%、暴击率 +10%、暴伤 +40%', atkBonus: 0.20, critBonus: 0.10, critDmgBonus: 0.40 });
    } else if (counts.chaos >= 2) {
      synergies.push({ race: 'chaos', count: counts.chaos, tier: 2, name: '混沌狂暴', desc: '全队攻击 +10%、暴击率 +5%', atkBonus: 0.10, critBonus: 0.05, critDmgBonus: 0 });
    }
    if (counts.nature >= 3) {
      synergies.push({ race: 'nature', count: counts.nature, tier: 3, name: '自然繁衍', desc: '全队攻速/移速 +20%、受治疗 +25%、10%自愈吸血', speedBonus: 0.20, healBoost: 0.25, lifesteal: 0.10 });
    } else if (counts.nature >= 2) {
      synergies.push({ race: 'nature', count: counts.nature, tier: 2, name: '自然共生', desc: '全队攻速/移速 +10%、受治疗 +15%', speedBonus: 0.10, healBoost: 0.15, lifesteal: 0 });
    }
    return synergies;
  }

  function unequipItem(state, heroKey, slot) {
    const inst = getHeroInstance(state, heroKey);
    const prev = inst.equips[slot];
    inst.equips[slot] = null;
    return { ok: true, unequipped: prev };
  }

  return {
    MAX_LEVEL,
    MAX_STARS,
    MAX_SKILL_LVL,
    levelCost,
    starBreakthroughCost,
    skillUpgradeCost,
    getHeroInstance,
    computeHeroCombatStats,
    computeFactionSynergy,
    levelUp,
    starBreakthrough,
    upgradeSkill,
    equipItem,
    unequipItem,
  };
});
