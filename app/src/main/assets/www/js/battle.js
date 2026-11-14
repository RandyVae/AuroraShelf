/* Lane auto-battler using original Spine skeletons + seeded deterministic PRNG. */
(function () {
  'use strict';

  const LANE_FRACTIONS = [0.14, 0.37, 0.60];  // feet position in world space: Lane 0 (bottom: 0.14), Lane 1 (middle: 0.37), Lane 2 (top: 0.60)
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
    this.chapter = null;
    this.units = [];
    this.projectiles = [];
    this.time = 0;
    this.speed = 1;
    this.running = false;
    this.ended = false;
    this.spine = new SpineField(this.canvas);
    this.spineReady = false;
    this.tags = new Map();
    this.shadows = new Map();
    this.hitStop = 0;
    this.el = document.getElementById('battle-field');
    this.constants = opts.constants || { timerSeconds: 60, skillDeliveryCooldown: 4 };
    this.playerLevel = opts.playerLevel || 1;
    this.commanderEnergy = 25;
    this.maxCommanderEnergy = 100;
    this._timers = [];
    this.isSettling = false;
    if (window.LGVFX && this.el) {
      window.LGVFX.init(this.el);
    }
    
    // 战役心流数值设计：新手蜜月期 (1-1 到 1-3) 开局即赠送 65 点指挥官能量，直接解锁空投技能秒全场
    const initStage = opts.stage;
    const isRookieStage = (initStage && (initStage.id === '1-1' || initStage.id === '1-2' || initStage.id === '1-3'));
    this.commanderEnergy = isRookieStage ? 65 : 25;
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
    // 彻底重置战场：清空 Spine 骨骼列表与 WebGL 画面，杜绝上一关卡存活角色残留在战场
    if (this.spine && this.spine.clear) {
      this.spine.clear();
    }
    this.units = [];
    this.time = 0;
    this.running = true;
    this.ended = false;
    this.stopped = false;
    this.isSettling = false;
    this.speed = 1;
    this.hitStop = 0;

    // 动态绑定章节超高清原厂战役背景 (ch1.webp ~ ch8.webp，保底 battle.webp)
    const chapId = (this.chapter && this.chapter.id) || (stage && stage.id && parseInt(stage.id.split('-')[0], 10)) || 1;
    const bgFile = (chapId >= 1 && chapId <= 8) ? `ch${chapId}.webp` : 'battle.webp';
    if (this.el) {
      this.el.style.backgroundImage = `url(assets/bg/${bgFile})`;
      this.el.querySelectorAll('.unit-tag, .dmg-float, .unit-shadow, .unit-fx, .slash-trail, .crit-vignette, .battle-combo').forEach((n) => n.remove());
    }
    if (this.tags) this.tags.clear();
    if (this.shadows) this.shadows.clear();
    if (this.deployedWaves) this.deployedWaves.clear();
    if (window.LGVFX && this.el) {
      window.LGVFX.init(this.el);
    }
    
    // 战役心流数值设计：新手蜜月期 (1-1 到 1-3) 开局即赠送 65 点指挥官能量，直接解锁空投技能秒全场
    const isRookieStage = (stage.id === '1-1' || stage.id === '1-2' || stage.id === '1-3');
    this.commanderEnergy = isRookieStage ? 65 : 25;
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
    // 戰鬥角色骨骼尺寸比例自適應居中 (高度約佔 30% 視口，頂部預留充足空間，底部下沉貼合，徹底杜絕頂部角色被截斷)
    const unitH = Math.max(140, Math.min(this.H * 0.30, this.W * 0.48));
    this.unitH = unitH;
    const lvl = stage.level || 1;

    this.pendingHeroes = heroes.slice(0, 3);
    this.pendingEnemies = enemies.slice(0, 3);
    this.deployedWaves = new Set();
    this.stage = stage;
    this.isRookieStage = isRookieStage;

    // Faction synergies calculation & application
    const allyHeroes = heroes.slice(0, 3);
    this.factionSynergies = (window.Progression && window.Progression.computeFactionSynergy)
      ? window.Progression.computeFactionSynergy(allyHeroes)
      : [];

    if (this.factionSynergies.length) {
      this.factionSynergies.forEach((syn) => {
        this.log('【阵营共鸣】激活「' + syn.name + '」：' + syn.desc);
      });
    }

    // 率先部署第 0 梯次（我方先锋与敌方先锋 1v1 出阵交锋）
    this.deployWave(0);
  };

  Battle.prototype.deployWave = function (laneIdx) {
    if (laneIdx < 0 || laneIdx >= 3) return;
    if (this.deployedWaves && this.deployedWaves.has(laneIdx)) return;
    if (!this.deployedWaves) this.deployedWaves = new Set();
    this.deployedWaves.add(laneIdx);

    const h = this.pendingHeroes && this.pendingHeroes[laneIdx];
    const e = this.pendingEnemies && this.pendingEnemies[laneIdx];
    const unitH = this.unitH;
    const lvl = (this.stage && this.stage.level) || 1;
    const isRookieStage = this.isRookieStage;

    // 1. 部署我方特勤 (Ally)
    if (h && !this.units.some((u) => u.side === 'ally' && u.lane === laneIdx)) {
      const st = h._stats || (window.Balance
        ? window.Balance.heroStats(h, this.playerLevel, { leader: laneIdx === 0 })
        : { atk: h.atk, maxHp: h.battleHp });

      let heroAtk = st.atk;
      let heroCrit = st.crit || 0.10;
      if (isRookieStage) {
        heroAtk = Math.round(heroAtk * 1.15); // 温和新手加成，杜绝夸张无脑暴击
      }
      // 我方在左侧面向右，若使用 _enemy 资产则翻转，_player 资产不翻转（天然面右）
      const isEnemyAsset = Boolean(h.unit && h.unit.endsWith('_enemy'));
      const flipAlly = isEnemyAsset;

      const allyUnit = this.makeUnit({
        side: 'ally', lane: laneIdx, x: PLAYER_FRAC[laneIdx] * this.W,
        name: h.name, unit: h.unit, key: h.key,
        thumb: (h.artWeb && (h.artWeb.baseThumb || h.artWeb.legaThumb)) || '',
        atk: heroAtk, hp: st.maxHp || st.hp, heightPx: unitH,
        flip: flipAlly,
        range: h.class === 'ranged' ? this.W * 0.30 : this.W * 0.065,
        speed: this.W * (h.class === 'ranged' ? 0.052 : 0.075),
        skills: h.skills || [], rarity: h.rarity,
        race: h.race, class: h.class, leader: laneIdx === 0,
        crit: heroCrit, mitigation: st.mitigation,
      });

      // 应用阵营共鸣增益
      if (this.factionSynergies && this.factionSynergies.length) {
        this.factionSynergies.forEach((syn) => {
          if (syn.hpBonus) { allyUnit.maxHp = Math.round(allyUnit.maxHp * (1 + syn.hpBonus)); allyUnit.hp = allyUnit.maxHp; }
          if (syn.atkBonus) allyUnit.atk = Math.round(allyUnit.atk * (1 + syn.atkBonus));
          if (syn.mitBonus) allyUnit.mitigation = Math.min(0.75, (allyUnit.mitigation || 0) + syn.mitBonus);
          if (syn.critBonus) allyUnit.crit = Math.min(0.40, (allyUnit.crit || 0) + syn.critBonus);
          if (syn.speedBonus) allyUnit.speed = Math.round(allyUnit.speed * (1 + syn.speedBonus));
          if (syn.startShieldPct) allyUnit.shield = (allyUnit.shield || 0) + Math.round(allyUnit.maxHp * syn.startShieldPct);
          if (syn.critDmgBonus) allyUnit.critDmgBonus = syn.critDmgBonus;
          if (syn.healBoost) allyUnit.healBoost = syn.healBoost;
          if (syn.lifesteal) allyUnit.lifesteal = syn.lifesteal;
        });
      }

      // Justina Signature: Aegis Holy Ward
      if (h.key === 'justina') {
        allyUnit.shield = (allyUnit.shield || 0) + Math.round(allyUnit.atk * 1.0);
        this.log('【神圣圣盾】Justina 为全队展开神圣护盾！');
      }

      this.units.push(allyUnit);
      this.schedule(() => this.trySkills(allyUnit, 'on_appear'), 300);

      const allyRoleText = laneIdx === 0 ? '【先锋出击】' : (laneIdx === 1 ? '【战术增援】' : '【主力压上】');
      this.log(`${allyRoleText} ${allyUnit.name} 切入前线战位！`);
    }

    // 2. 部署敌方特勤 (Enemy)
    if (e && !this.units.some((u) => u.side === 'enemy' && u.lane === laneIdx)) {
      let est = window.Balance
        ? window.Balance.enemyStats(e, lvl, { elite: ((this.stage && this.stage.stage) || 0) >= ((this.stage && this.stage.totalStages) || 99) })
        : { atk: e.atk, maxHp: e.battleHp };

      if (isRookieStage) {
        est.maxHp = Math.round(est.maxHp * 0.82);
        est.atk = Math.round(est.atk * 0.82);
      }
      // 敌方在右侧面向左，原厂 _enemy 资产天然面朝左（不翻转）；若使用 _player 资产则翻转面向左
      const isPlayerAsset = Boolean(e.unit && e.unit.endsWith('_player'));
      const flipEnemy = isPlayerAsset;

      const enemyUnit = this.makeUnit({
        side: 'enemy', lane: laneIdx, x: ENEMY_FRAC[laneIdx] * this.W,
        name: e.name, unit: e.unit, key: e.unit,
        atk: est.atk, hp: est.maxHp, heightPx: unitH,
        flip: flipEnemy,
        range: e.class === 'ranged' ? this.W * 0.30 : this.W * 0.065,
        speed: this.W * (e.class === 'ranged' ? 0.05 : 0.072),
        skills: [], rarity: e.rarity, isEnemy: true,
        race: e.race, class: e.class,
      });

      this.units.push(enemyUnit);
      const enemyRoleText = laneIdx === 0 ? '【敌军迎击】' : (laneIdx === 1 ? '【敌方支援】' : '【敌方主力】');
      this.log(`${enemyRoleText} ${enemyUnit.name} 进场迎战！`);
    }

    this.renderSquadStrip();
  };

  Battle.prototype.triggerReviveProtocol = function () {
    this.ended = false;
    this.running = true;
    this.units.filter((u) => u.side === 'ally').forEach((u) => {
      u.alive = true;
      u.dead = false;
      u.hp = u.maxHp;
      u.shield = Math.round(u.maxHp * 0.6);
      u.atk = Math.round(u.atk * 2.2); // 狂暴翻倍斩杀！
      u.speed = Math.round(u.speed * 1.6);
      if (this.spine && u.view) {
        this.spine.resumeIdle(u.view);
      }
    });
    this.commanderEnergy = 100;
    this.updateCommanderUI();
    this.log('💉【战术急救血清注入】全员满血复活，攻击力+120%，开始绝地反杀！');
    if (window.NativeBridge) NativeBridge.haptic('bigWin');
    if (window.LGVFX && this.el) {
      window.LGVFX.victoryBurst(this.el);
    }
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
      knockback: 0, knockbackDir: 0, stagger: 0, recoil: 0,
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
          critChance: src ? src.crit : 0.10,
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
      this.float(src, '斩杀 +50%', 'execute');
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

      // 1. 受击骨骼瞬态闪白与弹性形变 (Hit-Flash 80ms & Squash & Stretch)
      if (dst.view) {
        dst.view.hitFlash = 0.08;
        if (this.spine && this.spine.squash) {
          this.spine.squash(dst.view, crit ? 1.22 : 1.10, crit ? 0.78 : 0.90, crit ? 0.22 : 0.14);
        }
      }

      // 2. 受击硬直微抖与物理击退 (Knockback & Stagger)
      dst.stagger = 0.12;
      dst.knockback = crit ? 14 : 7;
      dst.knockbackDir = dst.side === 'ally' ? -1 : 1;

      // 3. 暴击顿帧 (Hit-Stop 45ms) 与 Apple/Android 原生触感反馈
      if (crit) {
        this.hitStop = 0.045;
        if (window.LGVFX) window.LGVFX.haptic('heavy');
      } else {
        if (window.LGVFX) window.LGVFX.haptic('light');
      }

      // 4. 己方连击累加与浮现
      if (src && src.side === 'ally' && !opts.silent) {
        if (this.time - (this.lastHitTime || 0) < 2.2) {
          this.comboCount = (this.comboCount || 0) + 1;
        } else {
          this.comboCount = 1;
        }
        this.lastHitTime = this.time;
        if (this.comboCount >= 3) {
          this.showCombo(this.comboCount);
        }
      }

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
          this.float(dst, '暗影反击', 'counter');
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
      this.float(src, '连杀重置!', 'kill-chain');
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
    const shadow = this.shadows.get(u.id);
    if (shadow) shadow.style.opacity = '0';
    if (window.LGVFX && this.spine) {
      const dp = this.spine.project(u.x, u.footY);
      const normPos = { x: (dp.x / this.W) * 100, y: (1 - dp.y / this.H) * 100 };
      window.LGVFX.deathDissolve(normPos, u.side);
    }
    if (u.side === 'ally' && window.LGVFX) {
      window.LGVFX.haptic('defeat');
    }
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

  Battle.prototype.showCombo = function (count) {
    if (!this.el || count < 3) return;
    let comboEl = this.el.querySelector('.battle-combo');
    if (!comboEl) {
      comboEl = document.createElement('div');
      comboEl.className = 'battle-combo';
      comboEl.innerHTML = '<span class="bc-num"></span><span class="bc-text">COMBO HITS</span>';
      this.el.appendChild(comboEl);
    }
    comboEl.querySelector('.bc-num').textContent = count;
    comboEl.style.animation = 'none';
    void comboEl.offsetWidth; // Force reflow for re-triggering pop
    comboEl.style.animation = 'vfxComboPop 0.28s cubic-bezier(0.18, 0.89, 0.32, 1.28)';
    clearTimeout(this._comboTimeout);
    this._comboTimeout = this.schedule(() => {
      if (comboEl && comboEl.parentNode) comboEl.parentNode.removeChild(comboEl);
      this.comboCount = 0;
    }, 2200);
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

    // 顿帧 (Hit-Stop): 暴击或技能重击时，短暂停滞游戏模拟 45ms 营造极致打击感
    if (this.hitStop > 0) {
      this.hitStop = Math.max(0, this.hitStop - dt);
      return;
    }

    this.time = Math.max(0, this.time + dt);

    // 梯次交锋出兵推进 (Staged Duel & Sequential Deployment Flow)
    // 1. 第二梯次（支援特勤）：时间到达 2.4s 或场上已有单位退场时增援进场
    if (this.deployedWaves && !this.deployedWaves.has(1)) {
      if (this.time >= 2.4 || this.units.some((u) => !u.alive)) {
        this.deployWave(1);
      }
    }
    // 2. 第三梯次（主力决战）：时间到达 4.8s 或敌方先遣队全灭时主力全线压上
    if (this.deployedWaves && !this.deployedWaves.has(2)) {
      if (this.time >= 4.8 || this.units.filter((u) => u.alive && u.side === 'enemy').length === 0) {
        this.deployWave(2);
      }
    }

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
      u.recoil = Math.max(0, (u.recoil || 0) - dt);
      u.stagger = Math.max(0, (u.stagger || 0) - dt);
      u.knockback = Math.max(0, (u.knockback || 0) - dt * 42);
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
          if (isRanged) {
            this.spine.setAnim(u.view, 'rangehit', false, 0.5);
            u.recoil = 0.14;
            if (this.spine && this.spine.squash) this.spine.squash(u.view, 0.90, 1.12, 0.14);
          } else {
            this.spine.setAnim(u.view, 'hit', false, 0.4);
            u.lunge = 0.18;
            if (this.spine && this.spine.squash) this.spine.squash(u.view, 1.16, 0.88, 0.18);
          }
          
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
              window.LGVFX.slash(tNorm, false, { side: u.side });
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
      const isReduce = window.LGVFX && window.LGVFX.isReduceMotion();
      // 1. 近战爆发瞬身突刺 (32px 前冲 + 弹性衰减)
      const lungeOffset = (!isReduce && u.lunge > 0)
        ? (u.side === 'ally' ? 1 : -1) * 32 * Math.sin((u.lunge / 0.18) * Math.PI)
        : 0;
      // 2. 远程枪械反冲后坐力 (10px 后仰)
      const recoilOffset = (!isReduce && u.recoil > 0)
        ? (u.side === 'ally' ? -1 : 1) * 10 * Math.sin((u.recoil / 0.14) * Math.PI)
        : 0;
      // 3. 受击物理击退 (8~16px 击退位移)
      const knockOffset = (!isReduce && u.knockback > 0)
        ? (u.knockbackDir || (u.side === 'ally' ? -1 : 1)) * u.knockback
        : 0;
      // 4. 受击高频硬直颤抖 (Stagger Jitter)
      const staggerOffset = (!isReduce && u.stagger > 0)
        ? (Math.random() - 0.5) * 6
        : 0;

      const renderX = u.x + lungeOffset + recoilOffset + knockOffset + staggerOffset;
      this.spine.position(u.view, renderX, u.footY);

      // 单位脚底动态战术阴影圈 (消除悬浮感)
      let shadow = this.shadows.get(u.id);
      if (!shadow) {
        shadow = document.createElement('div');
        shadow.className = 'unit-shadow';
        this.el.appendChild(shadow);
        this.shadows.set(u.id, shadow);
      }
      const sp = this.spine.project(renderX, u.footY);
      shadow.style.left = (sp.x / this.W * 100) + '%';
      shadow.style.top = ((1 - sp.y / this.H) * 100) + '%';
      shadow.style.opacity = u.alive ? '0.72' : '0';
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
    if (this.el) {
      this.el.classList.toggle('danger-pulse', allyHp > 0 && allyHp < 0.32);
    }
    const timer = document.getElementById('battle-timer');
    if (timer) timer.textContent = String(Math.floor(this.time / 60)).padStart(2, '0') + ':' + String(Math.floor(this.time % 60)).padStart(2, '0');

    if (this.time >= (this.nextDelivery || 0)) {
      this.nextDelivery = this.time + (this.constants.skillDeliveryCooldown || 4);
      for (const u of this.units) if (u.alive) this.trySkills(u, 'on_turn');
    }
    const hasMoreWaves = this.deployedWaves && (!this.deployedWaves.has(1) || !this.deployedWaves.has(2));
    if (!foe.some((u) => u.alive)) {
      if (hasMoreWaves) {
        if (!this.deployedWaves.has(1)) this.deployWave(1);
        else if (!this.deployedWaves.has(2)) this.deployWave(2);
      } else {
        this.finish(true);
      }
    } else if (!ally.some((u) => u.alive)) {
      if (hasMoreWaves) {
        if (!this.deployedWaves.has(1)) this.deployWave(1);
        else if (!this.deployedWaves.has(2)) this.deployWave(2);
      } else {
        this.finish(false);
      }
    } else if (this.time > (this.constants.timerSeconds || 60)) {
      this.finish(false);
    }
  };

  Battle.prototype.finish = function (win, immediate) {
    if (this.ended || this.isSettling) return;
    this.isSettling = true;
    this.ended = true;
    this.running = false;
    if (this.el) {
      this.el.classList.remove('danger-pulse');
      const combo = this.el.querySelector('.battle-combo');
      if (combo) combo.remove();
    }
    if (this.skipping) immediate = true;
    const alive = this.units.filter((u) => u.side === 'ally' && u.alive).length;
    let stars = 0;
    if (win) {
      stars = (this.time <= 48 && alive === 3) ? 3 : (alive >= 2 ? 2 : 1);
      if (window.LGVFX && !immediate) {
        window.LGVFX.victoryBurst();
        window.LGVFX.haptic('bigWin');
      }
    } else {
      if (window.LGVFX && !immediate) {
        window.LGVFX.haptic('defeat');
      }
    }
    this.replay.outcome = { win, stars, time: this.time, alive };
    if (immediate) {
      this.onEnd({ win, stars, time: this.time, alive, replay: this.replay, skipped: true });
    } else {
      const scheduled = this.schedule(() => this.onEnd({ win, stars, time: this.time, alive, replay: this.replay }), 700);
      if (!scheduled) {
        this.onEnd({ win, stars, time: this.time, alive, replay: this.replay });
      }
    }
  };

  Battle.prototype.skip = function () {
    if (this.ended) return;
    this.skipping = true;
    this.running = true;
    if (this.deployWave) {
      for (let i = 0; i < 3; i++) this.deployWave(i);
    }
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

    const dock = document.getElementById('battle-commander-dock');
    if (dock) {
      dock.classList.toggle('dock-ready', this.commanderEnergy >= 100);
    }
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
      if (window.LGVFX) {
        window.LGVFX.skillCutIn('战术支援', { name: '纳米国度空投' });
        window.LGVFX.haptic('success');
      }
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
      if (window.LGVFX) {
        window.LGVFX.skillCutIn('电子战中枢', { name: '全域电磁脉冲' });
        window.LGVFX.screenFlash('rgba(56, 189, 248, 0.35)', 120);
        window.LGVFX.haptic('heavy');
      }
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
        window.LGVFX.cameraPunch(7);
        window.LGVFX.skillCutIn('天基武器系统', { name: '轨道歼灭主炮' });
        window.LGVFX.screenFlash('rgba(255, 208, 73, 0.45)', 160);
        window.LGVFX.haptic('bigWin');
      }
      this.log('指挥官支援：【轨道歼灭炮】天基光束对 ' + prime.name + ' 造成 ' + strikeDmg + ' 毁灭伤害！');
      if (typeof window.toast === 'function') window.toast('天基轨道炮歼灭命中！');
    }
  };

  Battle.prototype.schedule = function (fn, ms) {
    if (this.stopped) return null;
    const tid = setTimeout(() => {
      const idx = this._timers.indexOf(tid);
      if (idx !== -1) this._timers.splice(idx, 1);
      fn();
    }, ms);
    this._timers.push(tid);
    return tid;
  };

  Battle.prototype.setSpeed = function (s) { this.speed = s; };

  Battle.prototype.stop = function () {
    this.stopped = true;
    this.running = false;
    this.ended = true;
    this.isSettling = false;
    if (this._timers) {
      this._timers.forEach(clearTimeout);
      this._timers = [];
    }
    if (this._comboTimeout) {
      clearTimeout(this._comboTimeout);
      this._comboTimeout = null;
    }
    if (this.el) {
      this.el.classList.remove('danger-pulse');
      const combo = this.el.querySelector('.battle-combo');
      if (combo) combo.remove();
      this.el.querySelectorAll('.unit-tag, .dmg-float, .unit-shadow, .unit-fx, .slash-trail, .crit-vignette, .battle-combo').forEach((n) => n.remove());
    }
    if (this.spine) {
      if (this.spine.clear) {
        this.spine.clear();
      } else if (this.spine.units) {
        this.spine.units.forEach((v) => {
          try { if (v && v.state) v.state.clearTracks(); } catch (_) {}
        });
        this.spine.units = [];
      }
    }
    this.units = [];
    if (this.tags) this.tags.clear();
    if (this.shadows) this.shadows.clear();
    if (this.deployedWaves) this.deployedWaves.clear();
  };

  window.Battle = Battle;
})();
