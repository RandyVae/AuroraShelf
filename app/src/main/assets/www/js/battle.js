/* Lane auto-battler using original Spine skeletons + seeded deterministic PRNG. */
(function () {
  'use strict';

  const LANE_FRACTIONS = [0.30, 0.55, 0.80];  // feet position as a fraction from top
  const PLAYER_FRAC = [0.12, 0.17, 0.22];
  const ENEMY_FRAC = [0.88, 0.83, 0.78];

  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }

  // Mulberry32 deterministic 32-bit PRNG
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

  function Battle(opts) {
    this.field = opts.field;
    this.canvas = opts.canvas;
    this.onEnd = opts.onEnd;
    this.onLog = opts.onLog || function () {};
    this.stage = null;
    this.units = [];
    this.projectiles = [];
    this.time = 0;
    this.speed = 1;
    this.running = false;
    this.ended = false;
    this.spine = new SpineField(this.canvas);
    this.spineReady = false;
    this.tags = new Map();
    this.el = document.getElementById('battle-field');
    this.constants = opts.constants || { timerSeconds: 60, skillDeliveryCooldown: 4 };
    this.playerLevel = opts.playerLevel || 1;
    this.commanderEnergy = 25;
    this.maxCommanderEnergy = 100;
    if (window.LGVFX && this.el) {
      window.LGVFX.init(this.el);
    }
    this.commanderEnergy = 25;
    this.updateCommanderUI();

    // Seeded determinism
    this.seed = (opts.seed >>> 0) || ((Date.now() ^ (Math.random() * 0x7fffffff)) >>> 0);
    this.rng = createPrng(this.seed);
    this.replay = {
      seed: this.seed,
      stageId: null,
      heroes: [],
      enemies: [],
      events: [],
      outcome: null,
    };

    if (window.Balance && this.constants.balance) window.Balance.configure(this.constants.balance);
  }

  Battle.prototype.solveLaneY = function (fraction) {
    const W = this.spine.W, H = this.spine.H;
    const v = this.spine.screenToWorld(W / 2, (1 - fraction) * H);
    return v.y;
  };

  Battle.prototype.preload = async function (heroes, enemies) {
    for (const h of heroes) this.spine.queue(h.unit);
    for (const e of enemies) this.spine.queue(e.unit);
    await this.spine.loadAll();
    this.spineReady = true;
  };

  Battle.prototype.start = function (stage, heroes, enemies, customSeed) {
    if (customSeed !== undefined) {
      this.seed = customSeed >>> 0;
      this.rng = createPrng(this.seed);
    }
    this.stage = stage;
    this.onLog('战斗开始：' + (stage.title || ''));
    this.units = [];
    this.time = 0;
    this.running = true;
    this.ended = false;
    this.speed = 1;
    this.el.querySelectorAll('.unit-tag, .dmg-float').forEach((n) => n.remove());
    this.tags.clear();
    if (window.LGVFX && this.el) {
      window.LGVFX.init(this.el);
    }
    this.commanderEnergy = 25;
    this.updateCommanderUI();

    this.replay = {
      seed: this.seed,
      stageId: stage.id,
      heroes: heroes.map((h) => ({ key: h.key, name: h.name, atk: h.atk, hp: h.hp })),
      enemies: enemies.map((e) => ({ key: e.unit, name: e.name, atk: e.atk, hp: e.hp })),
      events: [],
      outcome: null,
    };

    this.spine.fit();
    this.W = this.spine.W;
    this.H = this.spine.H;
    this.lanes = LANE_FRACTIONS.map((f) => this.solveLaneY(f));
    // 大幅提升戰鬥角色骨骼尺寸 (由 0.21/0.34 提升至 0.38/0.58，高度約 180~320px)
    const unitH = Math.max(160, Math.min(this.H * 0.38, this.W * 0.58));
    this.unitH = unitH;
    const lvl = stage.level || 1;

    heroes.slice(0, 3).forEach((h, i) => {
      // Use progression stats if provided, otherwise compute from Balance
      const st = h._stats || (window.Balance
        ? window.Balance.heroStats(h, this.playerLevel, { leader: i === 0 })
        : { atk: h.atk, maxHp: h.battleHp });

      const u = this.makeUnit({
        side: 'ally', lane: i, x: PLAYER_FRAC[i] * this.W,
        name: h.name, unit: h.unit, key: h.key, thumb: (h.artWeb && (h.artWeb.baseThumb || h.artWeb.legaThumb)) || '',
        atk: st.atk, hp: st.maxHp || st.hp, heightPx: unitH,
        range: h.class === 'ranged' ? this.W * 0.30 : this.W * 0.065,
        speed: this.W * (h.class === 'ranged' ? 0.052 : 0.075),
        skills: h.skills || [], rarity: h.rarity,
        race: h.race, class: h.class, leader: i === 0,
        crit: st.crit, mitigation: st.mitigation,
      });
      this.units.push(u);
    });

    // Faction synergies calculation & application
    const allyHeroes = heroes.slice(0, 3);
    this.factionSynergies = (window.Progression && window.Progression.computeFactionSynergy)
      ? window.Progression.computeFactionSynergy(allyHeroes)
      : [];

    if (this.factionSynergies.length) {
      this.factionSynergies.forEach((syn) => {
        this.log('【阵营共鸣】激活「' + syn.name + '」：' + syn.desc);
        this.units.filter((u) => u.side === 'ally').forEach((u) => {
          if (syn.hpBonus) {
            u.maxHp = Math.round(u.maxHp * (1 + syn.hpBonus));
            u.hp = u.maxHp;
          }
          if (syn.atkBonus) u.atk = Math.round(u.atk * (1 + syn.atkBonus));
          if (syn.mitBonus) u.mitigation = Math.min(0.75, (u.mitigation || 0) + syn.mitBonus);
          if (syn.critBonus) u.crit = Math.min(1.0, (u.crit || 0) + syn.critBonus);
          if (syn.speedBonus) u.speed = Math.round(u.speed * (1 + syn.speedBonus));
          if (syn.startShieldPct) u.shield = (u.shield || 0) + Math.round(u.maxHp * syn.startShieldPct);
          if (syn.critDmgBonus) u.critDmgBonus = syn.critDmgBonus;
          if (syn.healBoost) u.healBoost = syn.healBoost;
          if (syn.lifesteal) u.lifesteal = syn.lifesteal;
        });
      });
    }

    // Justina Signature: Aegis Holy Ward (Grant all allies Holy Shield = 100% ATK)
    const justina = this.units.find((u) => u.side === 'ally' && u.key === 'justina');
    if (justina) {
      this.units.filter((u) => u.side === 'ally').forEach((u) => {
        u.shield = (u.shield || 0) + Math.round(justina.atk * 1.0);
      });
      this.log('【神圣圣盾】Justina 为全队展开神圣护盾！');
    }

    this.renderSquadStrip();
    const appear = this.units.slice();
    setTimeout(() => appear.forEach((u) => this.trySkills(u, 'on_appear')), 600);

    enemies.slice(0, 3).forEach((e, i) => {
      const est = window.Balance
        ? window.Balance.enemyStats(e, lvl, { elite: (stage.stage || 0) >= (stage.totalStages || 99) })
        : { atk: e.atk, maxHp: e.battleHp };
      const u = this.makeUnit({
        side: 'enemy', lane: i, x: ENEMY_FRAC[i] * this.W,
        name: e.name, unit: e.unit, key: e.unit,
        atk: est.atk, hp: est.maxHp, heightPx: unitH, flip: true,
        range: e.class === 'ranged' ? this.W * 0.30 : this.W * 0.065,
        speed: this.W * (e.class === 'ranged' ? 0.05 : 0.072),
        skills: [], rarity: e.rarity, isEnemy: true,
        race: e.race, class: e.class,
      });
      this.units.push(u);
    });
  };

  Battle.prototype.makeUnit = function (o) {
    const maxHp = Math.max(10, Math.round(o.hp || 100));
    const atk = Math.max(1, Math.round(o.atk || 10));
    const u = {
      id: 'u_' + o.side + '_' + o.lane + '_' + Math.floor(this.rng() * 100000),
      side: o.side, lane: o.lane, x: o.x, footY: this.lanes[o.lane],
      name: o.name, unit: o.unit, key: o.key, thumb: o.thumb || '',
      atk, maxHp, hp: maxHp, shield: 0,
      range: o.range || this.W * 0.065, speed: o.speed || this.W * 0.07,
      heightPx: o.heightPx || 160,
      flip: !!o.flip, isEnemy: !!o.isEnemy,
      skills: o.skills || [], rarity: o.rarity,
      race: o.race || '', class: o.class || 'melee', leader: !!o.leader,
      cd: 0.6 + this.rng() * 0.5, energy: 0, alive: true,
      stun: 0, poison: 0, poisonTick: 0, buffAtk: 0, debuffAtk: 0,
      counter: 0, evade: 0, lunge: 0, hurt: 0,
      crit: o.crit, mitigation: o.mitigation,
    };
    u.view = this.spine.build(o.unit, { x: u.x, y: u.footY, heightPx: u.heightPx, flip: u.flip });
    return u;
  };

  Battle.prototype.enemiesOf = function (u) {
    return this.units.filter((o) => o.alive && o.side !== u.side);
  };

  Battle.prototype.pickTarget = function (u) {
    const foes = this.enemiesOf(u);
    if (!foes.length) return null;
    const same = foes.filter((f) => f.lane === u.lane);
    const pool = same.length ? same : foes;
    let best = null, bd = Infinity;
    for (const f of pool) {
      const d = Math.abs(f.x - u.x);
      if (d < bd) { bd = d; best = f; }
    }
    return best;
  };

  Battle.prototype.log = function (text) {
    this.onLog(text);
    if (this.replay && this.replay.events.length < 200) {
      this.replay.events.push({ t: Math.round(this.time * 100) / 100, log: text });
    }
  };

  Battle.prototype.damage = function (src, dst, amount, opts) {
    opts = opts || {};
    if (!dst.alive) return 0;
    let dmg = 0;
    let crit = false;

    if (window.Balance && !opts.raw) {
      const base = src ? src.atk : dst.atk;
      const mult = opts.mult != null ? opts.mult : (base ? amount / base : 1);
      const res = window.Balance.computeDamage(
        {
          atk: base,
          class: src ? src.class : 'melee',
          race: src ? src.race : '',
          rarity: src ? src.rarity : 'rare',
          leaderAura: src ? !!src.leader : false,
          rangeFrac: opts.rangeFrac || 0,
        },
        { class: dst.class, race: dst.race },
        { mult, rng: this.rng },
      );
      if (res.dodged) {
        this.float(dst, '闪避', '');
        return 0;
      }
      dmg = res.damage;
      crit = res.crit;
    } else {
      dmg = Math.max(1, Math.round(amount));
    }

    if (isNaN(dmg) || dmg < 0) dmg = 1;

    // --- Hero Signatures (Damage Modifiers) ---
    // 1. Kitty: Falcon Execute (+50% dmg if target HP <= 35%)
    if (src && src.key === 'kitty' && !opts.raw && dst.hp > 0 && (dst.hp / dst.maxHp) <= 0.35) {
      dmg = Math.round(dmg * 1.50);
      this.float(src, '斩杀 +50%', 'crit');
    }
    // 2. Esther: Shieldbreaker (Destroys extra shield & applies 3s armor break)
    if (src && src.key === 'esther' && dst.shield > 0) {
      dst.shield = Math.max(0, dst.shield - Math.round(dmg * 1.5));
      dst.armorBreak = Math.max(dst.armorBreak || 0, 3.0);
      this.float(dst, '破甲', '');
    }
    // If target has armor break, take +25% damage
    if (dst.armorBreak > 0) {
      dmg = Math.round(dmg * 1.25);
    }
    // Chaos 3-piece crit damage bonus
    if (crit && src && src.critDmgBonus) {
      dmg = Math.round(dmg * (1 + src.critDmgBonus));
    }

    if (dst.shield > 0) {
      const absorbed = Math.min(dst.shield, dmg);
      dst.shield -= absorbed;
      dmg -= absorbed;
      if (absorbed > 0) this.float(dst, '盾 -' + absorbed, '');
    }

    if (dmg > 0) {
      dst.hp = Math.max(0, dst.hp - dmg);
      this.float(dst, (crit ? '暴击 ' : '') + '-' + dmg, crit ? 'crit' : '');
      if (window.LGVFX) {
        const dp = this.spine.project(dst.x, dst.footY);
        const normPos = { x: (dp.x / this.W) * 100, y: (1 - dp.y / this.H) * 100 };
        window.LGVFX.hitSpark(normPos, crit);
      }

      // Regina Signature: Stun 0.8s on critical hit
      if (src && src.key === 'regina' && crit && dst.alive) {
        dst.stun = Math.max(dst.stun || 0, 0.8);
        this.float(dst, '震荡 0.8s', '');
      }

      // Arachne Signature: Lifesteal 20%
      if (src && src.key === 'arachne' && src.alive && !opts.reflect && !opts.isCounter) {
        const stolen = Math.round(dmg * 0.20);
        if (stolen > 0) {
          src.hp = Math.min(src.maxHp, src.hp + stolen);
          this.float(src, '+' + stolen, 'heal');
        }
      }

      // Nature Synergy Lifesteal
      if (src && src.lifesteal && src.alive && !opts.reflect && !opts.isCounter) {
        const stolen = Math.round(dmg * src.lifesteal);
        if (stolen > 0) {
          src.hp = Math.min(src.maxHp, src.hp + stolen);
          this.float(src, '+' + stolen, 'heal');
        }
      }

      // Zoe Signature: Neurotoxin apply
      if (src && src.key === 'zoechloe' && dst.alive && !opts.raw) {
        dst.poison = Math.max(dst.poison || 0, 3.0);
        dst.poisonSrc = src;
        dst.poisonTick = 1;
        this.float(dst, '剧毒', '');
      }

      // Mitsuki Signature: Shadow Evade & Counter (25% chance to counter-attack)
      if (dst.key === 'mitsuki' && src && dst.alive && !opts.reflect && !opts.isCounter) {
        if (this.rng() < 0.25) {
          this.float(dst, '暗影反击', 'crit');
          this.damage(dst, src, Math.round(dst.atk * 0.60), { isCounter: true, silent: true, mult: 0.6 });
        }
      }

      if (dst.hp <= 0) this.kill(dst, src);
      else if (!opts.silent) this.trySkills(dst, 'on_attacked', src);
    }

    if (!opts.silent) {
      const hasHit = dst.view.data.animations.some((a) => a.name === 'hit');
      this.spine.setAnim(dst.view, hasHit ? 'hit' : 'damage', false, 0.45);
      dst.hurt = 0.25;
    }

    if (src && !src.isEnemy && dst.alive && dst.counter > 0 && !opts.reflect) {
      this.damage(dst, src, Math.round(dst.atk * 0.5), { reflect: true, silent: true, mult: 0.5 });
    }
    return dmg;
  };

  Battle.prototype.kill = function (u, src) {
    if (!u.alive) return;
    u.alive = false;
    u.hp = 0;
    this.trySkills(u, 'on_death', src);
    if (src && src.alive) this.trySkills(src, 'on_kill', u);

    // Kitty Signature: Falcon Kill Chain (Reset attack CD + grant Commander Energy)
    if (src && src.key === 'kitty' && src.alive) {
      src.cd = 0;
      this.commanderEnergy = Math.min(this.maxCommanderEnergy, this.commanderEnergy + 10);
      this.updateCommanderUI();
      this.float(src, '连杀重置!', 'crit');
      this.log('【猎鹰连杀】Kitty 击倒目标，攻击冷却即时重置！');
    }

    // Zoe Signature: Neurotoxin Detonation
    if (u.poison > 0 && u.poisonSrc && u.poisonSrc.key === 'zoechloe' && u.poisonSrc.alive) {
      const foes = this.enemiesOf(u.poisonSrc);
      const detDmg = Math.round(u.poisonSrc.atk * 0.80);
      foes.forEach((f) => {
        if (f.alive && f !== u) this.damage(u.poisonSrc, f, detDmg, { raw: true, silent: true });
      });
      this.log('【混沌剧毒】Zoe 剧毒爆裂，对敌方全员造成毒爆冲击！');
    }

    u.view.alpha = 0.35;
    const tag = this.tags.get(u.id);
    if (tag) tag.classList.add('dead');
    this.log(u.name + ' 被击倒');
    if (u.side === 'enemy') {
      this.commanderEnergy = Math.min(this.maxCommanderEnergy, this.commanderEnergy + 15);
      this.updateCommanderUI();
    }
    this.spine.setAnim(u.view, 'damage', false);
    setTimeout(() => {
      if (!this.spine.units.includes(u.view)) return;
      u.view.alpha = 0;
      this.spine.remove(u.view);
    }, 900);
  };

  Battle.prototype.float = function (u, text, cls) {
    const el = document.createElement('div');
    el.className = 'dmg-float ' + (cls || '');
    el.textContent = text;
    const p = this.spine.project(u.x, u.footY);
    el.style.left = (p.x / this.W * 100) + '%';
    el.style.top = ((1 - p.y / this.H) * 100 - 15) + '%';
    this.el.appendChild(el);
    setTimeout(() => el.remove(), 850);
  };

  Battle.prototype.scopeTargets = function (u, scope, target) {
    const foes = this.enemiesOf(u);
    const allies = this.units.filter((a) => a.alive && a.side === u.side);
    const rnd = (arr) => (arr.length ? [arr[Math.floor(this.rng() * arr.length)]] : []);
    switch (scope) {
      case 'self': return [u];
      case 'all_allies': return allies;
      case 'random_ally': return rnd(allies);
      case 'all_enemies': return foes;
      case 'random_enemy': return rnd(foes);
      case 'lane': return foes.filter((f) => f.lane === u.lane);
      case 'adjacent': return foes.slice().sort((a, b) => Math.abs(a.x - u.x) - Math.abs(b.x - u.x)).slice(0, 2);
      case 'column': return foes.filter((f) => f.lane === u.lane);
      case 'melee': return allies.filter((a) => a.range < 150);
      case 'ranged': return allies.filter((a) => a.range >= 150);
      case 'warlord': {
        const leader = allies.find((a) => a.leader);
        return leader ? [leader] : allies.slice(0, 1);
      }
      case 'enemy_warlord': {
        const leader = foes.find((f) => f.leader);
        return leader ? [leader] : rnd(foes);
      }
      default: return target && target.alive ? [target] : [];
    }
  };

  Battle.prototype.trySkills = function (u, trigger, target) {
    if (!u.alive || !u.skills || !u.skills.length) return;
    u.skillReady = u.skillReady || {};
    const now = this.time;
    u.skills.forEach((spec, i) => {
      if (!spec || spec.token === 'basic' || !spec.action) return;
      if ((spec.trigger || 'on_attack') !== trigger) return;
      if ((u.skillReady[i] || 0) > now) return;
      u.skillReady[i] = now + (this.constants.skillDeliveryCooldown || 4);
      this.castSpec(u, spec, target);
    });
  };

  Battle.prototype.castSpec = function (u, spec, target) {
    const scope = spec.target || 'single_enemy';
    const targets = this.scopeTargets(u, scope, target);
    if (!targets.length) return;
    const raw = Math.max(0, Number(spec.value) || 0);
    const pct = raw > 0 ? Math.min(3, raw / 100) : 1;
    const HARM = new Set(['damage', 'instant_kill', 'poison', 'freeze', 'silence', 'debuff_atk', 'steal', 'lifesteal']);
    const BENEFIT = new Set(['heal', 'shield', 'buff_atk', 'buff_hp', 'cleanse', 'reborn', 'counter', 'transform', 'summon']);
    let chosen = targets;
    if (HARM.has(spec.action) && chosen.every((t) => t.side === u.side)) {
      const foes = this.enemiesOf(u);
      chosen = foes.length ? [foes.reduce((a, b) => (Math.abs(a.x - u.x) <= Math.abs(b.x - u.x) ? a : b))] : [];
    } else if (BENEFIT.has(spec.action) && chosen.every((t) => t.side !== u.side)) {
      chosen = this.units.filter((a) => a.alive && a.side === u.side);
    }
    this.log(u.name + ' 触发「' + (spec.name || '技能') + '」');
    const skillAnim = u.class === 'ranged' ? 'rangehit' : (u.view.data.animations.some((a) => a.name === 'hit') ? 'hit' : 'damage');
    this.spine.setAnim(u.view, skillAnim, false, 0.45);
    if (window.LGVFX) {
      window.LGVFX.skillCutIn(u, spec);
    }

    // Dominica Signature: Tactical Overclock (speed up squad attack & movement)
    if (u.key === 'dominica') {
      const allies = this.units.filter((a) => a.alive && a.side === 'ally');
      allies.forEach((a) => {
        a.buffAtk = Math.max(a.buffAtk || 0, 4.0);
      });
      this.log('【以太超频】Dominica 激活战术链路，全队攻击过载强化！');
    }
    switch (spec.action) {
      case 'damage':
        chosen.forEach((t) => this.damage(u, t, u.atk * Math.max(0.4, pct)));
        break;
      case 'heal':
        chosen.forEach((t) => {
          const n = Math.round(t.maxHp * Math.max(0.05, pct));
          t.hp = Math.min(t.maxHp, t.hp + n);
          this.float(t, '+' + n, 'heal');
          if (window.LGVFX) {
            const tp = this.spine.project(t.x, t.footY);
            window.LGVFX.healAura({ x: (tp.x / this.W) * 100, y: (1 - tp.y / this.H) * 100 });
          }
        });
        break;
      case 'shield':
        chosen.forEach((t) => {
          t.shield += Math.round(u.atk * Math.max(0.3, pct));
          this.float(t, '护盾', 'heal');
          if (window.LGVFX) {
            const tp = this.spine.project(t.x, t.footY);
            window.LGVFX.shieldBarrier({ x: (tp.x / this.W) * 100, y: (1 - tp.y / this.H) * 100 });
          }
        });
        break;
      case 'buff_atk':
        this.units.filter((a) => a.alive && a.side === u.side).forEach((a) => { a.buffAtk = Math.max(a.buffAtk, 5); });
        this.float(u, '攻击强化', 'heal');
        break;
      case 'buff_hp':
        chosen.forEach((t) => { t.shield += Math.round(t.maxHp * Math.max(0.05, pct)); });
        this.float(u, '生命强化', 'heal');
        break;
      case 'debuff_atk':
        chosen.forEach((t) => { t.debuffAtk = 5; this.float(t, '破甲', ''); });
        break;
      case 'freeze':
      case 'silence':
        chosen.forEach((t) => {
          if (!spec.chance || this.rng() * 100 < spec.chance) {
            t.stun = Math.max(t.stun, 1.5);
            this.float(t, spec.action === 'silence' ? '沉默' : '冻结', 'heal');
          }
        });
        break;
      case 'poison':
        chosen.forEach((t) => { t.poison = Math.max(t.poison, 5); t.poisonTick = 1; t.poisonSrc = u; });
        break;
      case 'lifesteal':
        chosen.forEach((t) => {
          const d = this.damage(u, t, u.atk);
          u.hp = Math.min(u.maxHp, u.hp + Math.round(d * 0.6));
        });
        break;
      case 'counter':
        u.counter = Math.max(u.counter, 6);
        this.float(u, '反击', 'heal');
        break;
      case 'cleanse':
        this.units.filter((a) => a.alive && a.side === u.side).forEach((a) => { a.poison = 0; a.stun = 0; a.debuffAtk = 0; });
        this.float(u, '净化', 'heal');
        break;
      case 'reborn':
        u.reborn = true;
        this.float(u, '重生', 'heal');
        break;
      case 'instant_kill':
        chosen.forEach((t) => {
          const chance = Math.max(5, spec.chance || spec.value || 10);
          if (this.rng() * 100 < chance) this.damage(u, t, t.maxHp);
        });
        break;
      case 'transform':
        u.buffAtk = Math.max(u.buffAtk, 6);
        u.shield += Math.round(u.atk * 0.5);
        this.float(u, '转换', 'heal');
        break;
      case 'steal':
        chosen.forEach((t) => {
          const d = this.damage(u, t, u.atk * Math.max(0.5, pct));
          u.hp = Math.min(u.maxHp, u.hp + Math.round(d * 0.5));
        });
        break;
      case 'summon':
      case 'stat_change':
      case 'skill_control':
        u.buffAtk = Math.max(u.buffAtk, 4);
        this.float(u, '强化', 'heal');
        break;
      default:
        chosen.forEach((t) => this.damage(u, t, u.atk));
        break;
    }
  };

  Battle.prototype.renderSquadStrip = function () {
    const box = document.getElementById('battle-squads');
    if (!box) return;
    box.innerHTML = '';
    this.strip = [];
    this.units.filter((u) => u.side === 'ally').forEach((u) => {
      const chip = document.createElement('div');
      chip.className = 'squad-chip';
      chip.innerHTML = '<img alt="" src="' + (u.thumb || '') + '"><div class="sc-main"><b></b><div class="sc-rail"><i></i></div></div>';
      chip.querySelector('b').textContent = u.name;
      box.appendChild(chip);
      this.strip.push({ u, chip, rail: chip.querySelector('i') });
    });
  };

  Battle.prototype.step = function (dt) {
    if (!this.running || this.ended) return;
    this.time = Math.max(0, this.time + dt);

    // Dominica Signature: Ether Overclock (+35% passive energy generation)
    const hasDominica = this.units.some((a) => a.alive && a.side === 'ally' && a.key === 'dominica');
    const energyRate = hasDominica ? 6.5 * 1.35 : 6.5;
    this.commanderEnergy = Math.min(this.maxCommanderEnergy, this.commanderEnergy + dt * energyRate);
    this.updateCommanderUI();

    for (const u of this.units) {
      if (!u.alive) continue;
      u.cd = Math.max(0, u.cd - dt);
      u.hurt = Math.max(0, u.hurt - dt);
      u.lunge = Math.max(0, u.lunge - dt);
      u.stun = Math.max(0, u.stun - dt);
      u.buffAtk = Math.max(0, u.buffAtk - dt);
      u.debuffAtk = Math.max(0, u.debuffAtk - dt);
      u.counter = Math.max(0, u.counter - dt);
      u.evade = Math.max(0, u.evade - dt);
      u.armorBreak = Math.max(0, (u.armorBreak || 0) - dt);
      if (u.poison > 0) {
        u.poison -= dt;
        u.poisonTick -= dt;
        if (u.poisonTick <= 0) {
          u.poisonTick = 1;
          this.damage(u.poisonSrc || null, u, ((u.poisonSrc && u.poisonSrc.atk) || u.atk) * 0.2, { raw: true, silent: true });
        }
      }
      if (u.stun > 0) continue;

      const target = this.pickTarget(u);
      if (!target) {
        u.x += (u.side === 'ally' ? 1 : -1) * u.speed * dt;
      } else {
        const dist = Math.abs(target.x - u.x);
        if (dist > u.range) {
          const dir = target.x > u.x ? 1 : -1;
          u.x += dir * Math.min(u.speed * dt, dist - u.range);
        } else if (u.cd <= 0) {
          u.cd = 1.05 + this.rng() * 0.35;
          const mult = (u.buffAtk > 0 ? 1.35 : 1) * (u.debuffAtk > 0 ? 0.7 : 1);
          const isRanged = u.range > 150;
          if (isRanged) this.spine.setAnim(u.view, 'rangehit', false, 0.5);
          else this.spine.setAnim(u.view, 'hit', false, 0.4);
          u.lunge = 0.16;
          
          if (window.LGVFX && !this.skipping) {
            const sp = this.spine.project(u.x, u.footY);
            const tp = this.spine.project(target.x, target.footY);
            const sNorm = { x: (sp.x / this.W) * 100, y: (1 - sp.y / this.H) * 100 };
            const tNorm = { x: (tp.x / this.W) * 100, y: (1 - tp.y / this.H) * 100 };
            if (isRanged) {
              const boltColor = u.side === 'ally' ? '#38bdf8' : '#f43f5e';
              window.LGVFX.projectile(sNorm, tNorm, boltColor, () => {
                this.damage(u, target, u.atk * mult);
                // Regina Signature: Heavy Cannon 35% splash damage to adjacent lane enemies
                if (u.key === 'regina') {
                  const foes = this.enemiesOf(u);
                  const splashFoes = foes.filter((f) => f.alive && f !== target && Math.abs(f.lane - target.lane) === 1);
                  if (splashFoes.length) {
                    const splashDmg = Math.round(u.atk * 0.35 * mult);
                    splashFoes.forEach((sf) => this.damage(u, sf, splashDmg, { raw: true, silent: true }));
                    this.float(u, '重砲溅射', '');
                  }
                }
                this.trySkills(u, 'on_attack', target);
              });
              continue;
            } else {
              window.LGVFX.slash(tNorm, false);
            }
          }
          this.damage(u, target, u.atk * mult);
          // Regina Signature: Heavy Cannon 35% splash damage to adjacent lane enemies
          if (u.key === 'regina') {
            const foes = this.enemiesOf(u);
            const splashFoes = foes.filter((f) => f.alive && f !== target && Math.abs(f.lane - target.lane) === 1);
            if (splashFoes.length) {
              const splashDmg = Math.round(u.atk * 0.35 * mult);
              splashFoes.forEach((sf) => this.damage(u, sf, splashDmg, { raw: true, silent: true }));
              this.float(u, '重砲溅射', '');
            }
          }
          this.trySkills(u, 'on_attack', target);
        }
      }
      u.x = clamp(u.x, this.W * 0.03, this.W * 0.97);
      // 程序化前冲位移：lunge 存在时在视觉渲染层向前偏移 18px
      const isReduce = window.LGVFX && window.LGVFX.isReduceMotion();
      const lungeOffset = (!isReduce && u.lunge > 0) ? (u.side === 'ally' ? 1 : -1) * 18 * Math.sin((u.lunge / 0.16) * Math.PI) : 0;
      this.spine.position(u.view, u.x + lungeOffset, u.footY);
    }

    // HUD overlays
    for (const u of this.units) {
      let tag = this.tags.get(u.id);
      if (!tag) {
        tag = document.createElement('div');
        tag.className = 'unit-tag ' + (u.isEnemy ? 'enemy' : 'ally');
        tag.innerHTML = '<div class="ut-name"></div><div class="ut-rail"><i></i></div>';
        tag.querySelector('.ut-name').textContent = u.name;
        this.el.appendChild(tag);
        this.tags.set(u.id, tag);
      }
      const tp = this.spine.project(u.x, u.footY);
      tag.style.left = (tp.x / this.W * 100) + '%';
      tag.style.top = ((1 - tp.y / this.H) * 100 - 3) + '%';
      const pct = clamp(u.hp / u.maxHp, 0, 1) * 100;
      tag.querySelector('i').style.width = pct + '%';
      tag.style.opacity = u.alive ? 1 : 0;
    }

    if (this.strip) {
      for (const s2 of this.strip) {
        s2.rail.style.width = clamp(s2.u.hp / s2.u.maxHp, 0, 1) * 100 + '%';
        s2.chip.classList.toggle('dead', !s2.u.alive);
      }
    }

    const ally = this.units.filter((u) => u.side === 'ally');
    const foe = this.units.filter((u) => u.side === 'enemy');
    const allyHp = ally.reduce((s, u) => s + Math.max(0, u.hp), 0) / Math.max(1, ally.reduce((s, u) => s + u.maxHp, 0));
    const foeHp = foe.reduce((s, u) => s + Math.max(0, u.hp), 0) / Math.max(1, foe.reduce((s, u) => s + u.maxHp, 0));
    const a = document.getElementById('ally-hp');
    const f = document.getElementById('enemy-hp');
    if (a) a.style.width = (allyHp * 100) + '%';
    if (f) f.style.width = (foeHp * 100) + '%';
    const timer = document.getElementById('battle-timer');
    if (timer) timer.textContent = String(Math.floor(this.time / 60)).padStart(2, '0') + ':' + String(Math.floor(this.time % 60)).padStart(2, '0');

    if (this.time >= (this.nextDelivery || 0)) {
      this.nextDelivery = this.time + (this.constants.skillDeliveryCooldown || 4);
      for (const u of this.units) if (u.alive) this.trySkills(u, 'on_turn');
    }
    if (!foe.some((u) => u.alive)) this.finish(true);
    else if (!ally.some((u) => u.alive)) this.finish(false);
    else if (this.time > (this.constants.timerSeconds || 60)) this.finish(false);
  };

  Battle.prototype.finish = function (win, immediate) {
    if (this.ended) return;
    this.ended = true;
    this.running = false;
    if (this.skipping) immediate = true;
    const alive = this.units.filter((u) => u.side === 'ally' && u.alive).length;
    let stars = 0;
    if (win) {
      stars = (this.time <= 48 && alive === 3) ? 3 : (alive >= 2 ? 2 : 1);
      if (window.LGVFX && !immediate) window.LGVFX.victoryBurst();
    }
    this.replay.outcome = { win, stars, time: this.time, alive };
    if (immediate) {
      this.onEnd({ win, stars, time: this.time, alive, replay: this.replay, skipped: true });
    } else {
      setTimeout(() => this.onEnd({ win, stars, time: this.time, alive, replay: this.replay }), 700);
    }
  };

  Battle.prototype.skip = function () {
    if (this.ended || !this.units || !this.units.length) return;
    this.skipping = true;
    this.running = true;
    const entranceBox = document.getElementById('battle-entrance-cutin');
    if (entranceBox) entranceBox.hidden = true;
    if (window.APP && window.APP.stages && window.APP.stages.entrance) {
      try { window.APP.stages.entrance.stop(); } catch (e) {}
    }
    // Fast-forward simulation ticks until conclusion
    let safety = 2500;
    const dt = 0.05;
    while (!this.ended && safety > 0) {
      safety--;
      this.step(dt);
    }
    if (!this.ended) {
      this.finish(true, true);
    }
  };

  Battle.prototype.updateCommanderUI = function () {
    const bar = document.getElementById('commander-energy-bar');
    const val = document.getElementById('commander-energy-val');
    if (bar) bar.style.width = Math.min(100, Math.round(this.commanderEnergy)) + '%';
    if (val) val.textContent = Math.round(this.commanderEnergy) + ' / ' + this.maxCommanderEnergy;

    const btnHeal = document.getElementById('cmd-skill-heal');
    const btnEmp = document.getElementById('cmd-skill-emp');
    const btnStrike = document.getElementById('cmd-skill-strike');
    if (btnHeal) btnHeal.disabled = this.commanderEnergy < 35 || !this.running;
    if (btnEmp) btnEmp.disabled = this.commanderEnergy < 50 || !this.running;
    if (btnStrike) btnStrike.disabled = this.commanderEnergy < 75 || !this.running;
  };

  Battle.prototype.useCommanderSkill = function (skillId) {
    if (!this.running || this.ended) return;
    const costs = { heal: 35, emp: 50, strike: 75 };
    const cost = costs[skillId] || 100;
    if (this.commanderEnergy < cost) {
      if (typeof window.toast === 'function') window.toast('战术能量不足 (' + Math.round(this.commanderEnergy) + '/' + cost + ')');
      return;
    }
    this.commanderEnergy -= cost;
    this.updateCommanderUI();

    if (skillId === 'heal') {
      if (window.LGAudio) window.LGAudio.playSfx('heal');
      const allies = this.units.filter((u) => u.side === 'ally' && u.alive);
      allies.forEach((u) => {
        const amt = Math.round(u.maxHp * 0.25);
        u.hp = Math.min(u.maxHp, u.hp + amt);
        u.shield += Math.round(u.atk * 0.6);
        this.float(u, '+' + amt, 'heal');
        if (window.LGVFX) {
          const tp = this.spine.project(u.x, u.footY);
          window.LGVFX.healAura({ x: (tp.x / this.W) * 100, y: (1 - tp.y / this.H) * 100 });
        }
      });
      this.log('指挥官支援：【战术空投】全体特勤生命恢复+以太护盾！');
      if (typeof window.toast === 'function') window.toast('战术空投部署完成！');
    } else if (skillId === 'emp') {
      if (window.LGAudio) window.LGAudio.playSfx('stun');
      const enemies = this.units.filter((u) => u.side === 'enemy' && u.alive);
      const leader = this.units.find((u) => u.side === 'ally' && u.leader) || this.units.find((u) => u.side === 'ally');
      const baseDmg = leader ? Math.round(leader.atk * 0.8) : 260;
      enemies.forEach((e) => {
        e.stun = Math.max(e.stun, 2.5);
        e.debuffAtk = Math.max(e.debuffAtk, 4.0);
        this.damage(leader || e, e, baseDmg, { raw: true });
        this.float(e, 'EMP瘫痪', '');
        if (window.LGVFX) {
          const tp = this.spine.project(e.x, e.footY);
          window.LGVFX.freezeStatus({ x: (tp.x / this.W) * 100, y: (1 - tp.y / this.H) * 100 });
        }
      });
      this.log('指挥官支援：【电磁脉冲】全场敌军瘫痪 2.5 秒并受创！');
      if (typeof window.toast === 'function') window.toast('电磁脉冲覆盖全场！');
    } else if (skillId === 'strike') {
      if (window.LGAudio) window.LGAudio.playSfx('skill');
      const enemies = this.units.filter((u) => u.side === 'enemy' && u.alive);
      if (!enemies.length) return;
      enemies.sort((a, b) => b.maxHp - a.maxHp);
      const prime = enemies[0];
      const leader = this.units.find((u) => u.side === 'ally' && u.leader) || this.units[0];
      const strikeDmg = Math.round((leader ? leader.atk : 400) * 2.8);
      this.damage(leader || prime, prime, strikeDmg, { raw: true });
      this.float(prime, 'CRITICAL ' + strikeDmg, 'crit');
      if (window.LGVFX) {
        window.LGVFX.cameraPunch(true);
        window.LGVFX.skillCutIn('轨道歼灭炮', '#ffd049');
      }
      this.log('指挥官支援：【轨道歼灭炮】天基光束对 ' + prime.name + ' 造成 ' + strikeDmg + ' 毁灭伤害！');
      if (typeof window.toast === 'function') window.toast('天基轨道炮歼灭命中！');
    }
  };

  Battle.prototype.setSpeed = function (s) { this.speed = s; };
  Battle.prototype.stop = function () { this.running = false; this.ended = true; };

  window.Battle = Battle;
})();
