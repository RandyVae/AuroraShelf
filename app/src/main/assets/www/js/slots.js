/* Deterministic, virtual-currency Slots mode with 3D mechanical physics & procedural audio.
   No network, payments, or cash-out. All outcomes are 100% deterministic & offline. */
(function () {
  'use strict';

  function mulberry32(seed) {
    let a = seed >>> 0;
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function weightedSymbol(symbols, rnd) {
    const total = symbols.reduce((sum, s) => sum + Math.max(0, s.weight || 0), 0);
    let cursor = rnd() * total;
    for (const symbol of symbols) {
      cursor -= Math.max(0, symbol.weight || 0);
      if (cursor <= 0) return symbol;
    }
    return symbols[symbols.length - 1];
  }

  function matches(line, symbols, symbolMap) {
    const first = symbolMap[line[0]];
    if (!first || first.kind === 'scatter') return null;

    // A leading Wild substitutes for the first real symbol. It must not make
    // every following symbol part of the same win. Resolve the base symbol
    // before counting so a line such as Wild, Kitty, Dominica stops at Kitty.
    const base = (first.kind === 'wild'
      ? line.slice(1).map((id) => symbolMap[id]).find((symbol) => symbol && symbol.kind !== 'wild' && symbol.kind !== 'scatter')
      : first) || first;
    if (!base) return null;

    let count = 0;
    for (const id of line) {
      const current = symbolMap[id];
      if (!current || current.kind === 'scatter' || (current.kind !== 'wild' && current.id !== base.id)) break;
      count += 1;
    }
    return count >= 2 ? { symbol: base, count } : null;
  }

  function spin(config, bet, seed, session) {
    const hasFlow = !!(session && typeof session.totalSpins === 'number');
    const spinIndex = hasFlow ? session.totalSpins : -1;
    const isBailout = !!(session && session.luckyBailout);
    const symbols = config.symbols || [];
    const symbolMap = Object.fromEntries(symbols.map((s) => [s.id, s]));
    const rnd = mulberry32(seed == null ? (Date.now() >>> 0) : seed);

    // 经典心流与上瘾机制调度（仅在带会话时启用）：
    // (a) 新手蜜月期 (0~5转) 或 破产救济期：极大甜头与高潮体验
    // (b) 爬坡考验期 (6转以后)：真实胜率收紧 (28%~35%) + 65% 概率触发高压 Near-Miss 假近中
    let isNearMiss = false;
    let scripted = null;

    if (hasFlow) {
      if (isBailout) {
        // 破产救济保底翻盘：中轴 4 WILD 爆出惊天大奖！
        scripted = 'bailout_wild';
      } else if (spinIndex === 0) {
        // 第 1 转：中轴 Kitty 3 连线，小试牛刀首胜甜头
        scripted = 'kitty_hit';
      } else if (spinIndex === 1) {
        // 第 2 转：中轴 Dominica 3 连线，连胜信心建立
        scripted = 'dominica_hit';
      } else if (spinIndex === 2) {
        // 第 3 转：必定命中 3 个 SCATTER，激活 5 次免费大旋转！
        scripted = 'scatter_free';
      } else if (spinIndex === 3 || spinIndex === 4) {
        // 免费旋转期间：命中 WILD 狂暴爆奖 (MEGA BIG WIN)
        scripted = 'wild_big';
      }
    }

    const grid = [];
    for (let row = 0; row < config.rows; row += 1) {
      grid[row] = [];
      for (let col = 0; col < config.reels; col += 1) {
        grid[row][col] = weightedSymbol(symbols, rnd).id;
      }
    }

    if (scripted === 'kitty_hit') {
      grid[1][0] = 'kitty'; grid[1][1] = 'kitty'; grid[1][2] = 'kitty';
    } else if (scripted === 'dominica_hit') {
      grid[1][0] = 'dominica'; grid[1][1] = 'dominica'; grid[1][2] = 'dominica';
    } else if (scripted === 'scatter_free') {
      grid[0][1] = 'scatter'; grid[1][2] = 'scatter'; grid[2][3] = 'scatter';
    } else if (scripted === 'wild_big' || scripted === 'bailout_wild') {
      grid[1][0] = 'wild'; grid[1][1] = 'wild'; grid[1][2] = 'wild'; grid[1][3] = 'florence';
    } else if (hasFlow && spinIndex >= 5) {
      // 考验期：有 60% 概率触发高压 Dual Scatter Near-Miss（假近中：精准命中 2 个核心，差 1 个触发大旋转）
      if (rnd() < 0.60) {
        // 清理现存 scatter 避免意外达到 3 个
        for (let r = 0; r < config.rows; r++) {
          for (let c = 0; c < config.reels; c++) {
            if (grid[r][c] === 'scatter') grid[r][c] = 'kitty';
          }
        }
        grid[0][1] = 'scatter';
        grid[1][3] = 'scatter';
        isNearMiss = true;
      }
    }

    const wins = [];
    for (let lineIndex = 0; lineIndex < config.paylines.length; lineIndex += 1) {
      const line = config.paylines[lineIndex].map((row, reelIndex) => grid[row][reelIndex]);
      const result = matches(line, symbols, symbolMap);
      if (result) {
        const isFreeSpin = !!(session && (session.freeSpins > 0 || session.isFreeSpin));
        const winMult = isFreeSpin ? 3 : 1;
        const multiplier = (result.symbol.payouts[String(result.count)] || 0) * winMult;
        if (multiplier) {
          const lineBet = Math.max(1, Math.round(bet / (config.paylines.length || 9)));
          const amount = lineBet * multiplier;
          wins.push({ line: lineIndex + 1, symbol: result.symbol.id, count: result.count, multiplier, amount, isFreeSpin });
        }
      }
    }

    // 奖池判定与实际结算（与界面 GRAND/MAJOR/MINOR/MINI 100% 对齐）
    let jackpot = null;
    for (let i = 0; i < wins.length; i++) {
      const w = wins[i];
      if (w.count === 5 && w.symbol === 'wild') {
        jackpot = { tier: 'GRAND', name: 'Grand Jackpot', bonus: 50000 };
        break;
      }
      if (!jackpot && w.count === 5 && w.symbol === 'hilda_enemy') {
        jackpot = { tier: 'MAJOR', name: 'Major Jackpot', bonus: 10000 };
      } else if (!jackpot && w.count === 5 && (w.symbol === 'florence' || w.symbol === 'regina' || w.symbol === 'dominica' || w.symbol === 'kitty')) {
        jackpot = { tier: 'MINOR', name: 'Minor Jackpot', bonus: 2000 };
      } else if (!jackpot && w.count === 4 && w.symbol === 'wild') {
        jackpot = { tier: 'MINI', name: 'Mini Jackpot', bonus: 500 };
      }
    }

    const scatterCount = grid.flat().filter((id) => id === config.scatter.id).length;
    const freeSpinsAwarded = scatterCount >= config.scatter.threshold ? config.scatter.freeSpins : 0;
    const bestWin = wins.reduce((best, win) => (!best || win.count > best.count ? win : best), null);
    const lineWinTotal = wins.reduce((sum, win) => sum + win.amount, 0);
    return {
      seed: seed == null ? null : seed >>> 0,
      grid,
      wins,
      totalWin: lineWinTotal + (jackpot ? jackpot.bonus : 0),
      jackpot: jackpot,
      isJackpot: !!jackpot,
      scatterCount,
      freeSpinsAwarded,
      bestCombo: bestWin ? bestWin.count : 0,
      featureSymbol: bestWin ? bestWin.symbol : null,
      isNearMiss: isNearMiss && freeSpinsAwarded === 0,
      flowPhase: spinIndex < 5 ? 'honeymoon' : (isNearMiss ? 'near_miss' : 'normal'),
    };
  }

  function resolveSymbol(symbol, data) {
    if (typeof symbol === 'string') symbol = { id: symbol };
    if (!symbol || !data) return null;
    const id = symbol.id;
    if (id === 'florence' || id === 'esther') {
      return (data.heroes || []).find((h) => h.key === 'esther' || h.key === 'florence') || null;
    }
    if (id === 'zoe_enemy') {
      return (data.heroes || []).find((h) => h.key === 'zoechloe') || (data.enemies || []).find((e) => e.id === 'zoe_enemy') || null;
    }
    if (id === 'hilda_enemy') {
      return (data.heroes || []).find((h) => h.key === 'hilda') || (data.enemies || []).find((e) => e.id === 'hilda_enemy') || null;
    }
    const hero = (data.heroes || []).find((h) => h.key === id);
    if (hero) return hero;
    const enemy = (data.enemies || []).find((e) => e.id === id || e.key === id);
    if (enemy) return enemy;
    return null;
  }

  function resolveSymbolVisual(symbol, data) {
    if (typeof symbol === 'string') symbol = { id: symbol };
    if (!symbol) return { artSrc: '', displayName: '', isHero: false, quote: '', fallbackIcon: 'assets/slots/symbol_wild.webp' };
    const id = symbol.id;
    const hero = resolveSymbol(symbol, data);
    const heroKey = (hero && hero.key) || (id === 'florence' ? 'esther' : (id === 'zoe_enemy' ? 'zoechloe' : (id === 'hilda_enemy' ? 'hilda' : (id === 'kitty' ? 'kitty' : (id === 'dominica' ? 'dominica' : (id === 'regina' ? 'regina' : null))))));

    let artSrc = '';
    if (hero && hero.artWeb) {
      artSrc = hero.artWeb.nude || hero.artWeb.lega || hero.artWeb.base || '';
    }
    if (!artSrc && heroKey && typeof heroKey === 'string' && heroKey !== 'undefined') {
      if (heroKey === 'kitty') {
        artSrc = 'assets/heroes/kitty/lega.webp';
      } else {
        artSrc = `assets/heroes/${heroKey}/nude.webp`;
      }
    }
    if (!artSrc) {
      artSrc = symbol.icon || SYMBOL_ASSETS[id] || 'assets/slots/symbol_wild.webp';
    }

    const isHero = !!(hero && (hero.key || hero.rarity || (id && (id.includes('hero') || ['kitty', 'dominica', 'regina', 'florence', 'esther', 'zoe_enemy', 'hilda_enemy'].includes(id)))));
    const displayName = (hero && hero.name) || symbol.label || id;

    let quote = '“战术连线达成，高额战利品已直接入库控制台！”';
    if (isHero && hero && hero.name) {
      quote = `“特勤英雄【${hero.name}】已直接入库特勤名册！战备奖励已就位。”`;
    } else if (id === 'wild') {
      quote = '“皇冠百搭狂飙！全轴替代连线，战利品倍率极限翻腾！”';
    } else if (id === 'scatter') {
      quote = '“能量核心激活！战术免费旋转已解锁，火力全开！”';
    }

    return {
      hero,
      heroKey,
      artSrc,
      displayName,
      isHero,
      quote,
      fallbackIcon: symbol.icon || SYMBOL_ASSETS[id] || 'assets/slots/symbol_wild.webp'
    };
  }

  const SYMBOL_ASSETS = {
    kitty: 'assets/slots/symbol_kitty.webp',
    dominica: 'assets/slots/symbol_dominica.webp',
    regina: 'assets/slots/symbol_regina.webp',
    florence: 'assets/slots/symbol_esther.webp',
    esther: 'assets/slots/symbol_esther.webp',
    zoe_enemy: 'assets/slots/symbol_zoe.webp',
    drone_enemy: 'assets/slots/symbol_zoe.webp',
    hilda_enemy: 'assets/slots/symbol_hilda.webp',
    wild: 'assets/slots/symbol_wild.webp',
    scatter: 'assets/slots/symbol_scatter.webp',
  };

  const PAYLINE_COLORS = [
    '#ff2a85', '#24e0ff', '#ffe24a', '#8eff3a', '#bf55ec',
    '#ff8400', '#00ffc8', '#ff3366', '#3388ff'
  ];

  /* Procedural Web Audio synthesizer for tactile slot machine mechanics */
  const SlotsAudio = {
    ctx: null,
    getCtx() {
      if (!this.ctx && (window.AudioContext || window.webkitAudioContext)) {
        const AC = window.AudioContext || window.webkitAudioContext;
        this.ctx = new AC();
      }
      if (this.ctx && this.ctx.state === 'suspended') {
        this.ctx.resume().catch(() => {});
      }
      return this.ctx;
    },
    isMuted() {
      if (window.APP && APP.state && APP.state.settings) {
        if (APP.state.settings.sfx === false || APP.state.settings.sfxVol === 0) return true;
      }
      return false;
    },
    lever() {
      if (this.isMuted()) return;
      const ctx = this.getCtx();
      if (!ctx) return;
      const now = ctx.currentTime;
      // Mechanical ratchet clack
      for (let i = 0; i < 3; i++) {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(320 + i * 80, now + i * 0.05);
        gain.gain.setValueAtTime(0.18, now + i * 0.05);
        gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.05 + 0.04);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now + i * 0.05);
        osc.stop(now + i * 0.05 + 0.05);
      }
      // Damped spring release thump
      const osc2 = ctx.createOscillator();
      const gain2 = ctx.createGain();
      osc2.type = 'sine';
      osc2.frequency.setValueAtTime(140, now + 0.18);
      osc2.frequency.exponentialRampToValueAtTime(45, now + 0.35);
      gain2.gain.setValueAtTime(0.28, now + 0.18);
      gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.38);
      osc2.connect(gain2);
      gain2.connect(ctx.destination);
      osc2.start(now + 0.18);
      osc2.stop(now + 0.4);
    },
    reelStop(col) {
      if (this.isMuted()) return;
      const ctx = this.getCtx();
      if (!ctx) return;
      const now = ctx.currentTime;
      const baseFreq = 180 + (col || 0) * 35;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(baseFreq, now);
      osc.frequency.exponentialRampToValueAtTime(60, now + 0.08);
      gain.gain.setValueAtTime(0.25, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.09);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(now);
      osc.stop(now + 0.1);
    },
    coin() {
      if (this.isMuted()) return;
      const ctx = this.getCtx();
      if (!ctx) return;
      const now = ctx.currentTime;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      const f = 1900 + Math.random() * 400;
      osc.frequency.setValueAtTime(f, now);
      gain.gain.setValueAtTime(0.12, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.18);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(now);
      osc.stop(now + 0.2);
    },
    winChime(count) {
      if (this.isMuted()) return;
      const ctx = this.getCtx();
      if (!ctx) return;
      const now = ctx.currentTime;
      const chords = [523.25, 659.25, 783.99, 1046.50, 1318.51];
      const notes = count >= 4 ? chords : chords.slice(0, Math.min(chords.length, count + 1));
      notes.forEach((freq, i) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(freq, now + i * 0.07);
        gain.gain.setValueAtTime(0.18, now + i * 0.07);
        gain.gain.exponentialRampToValueAtTime(0.001, now + i * 0.07 + 0.35);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now + i * 0.07);
        osc.stop(now + i * 0.07 + 0.4);
      });
    },
    bigWin() {
      if (this.isMuted()) return;
      const ctx = this.getCtx();
      if (!ctx) return;
      const now = ctx.currentTime;
      const fanfare = [523.25, 659.25, 783.99, 1046.50, 1174.66, 1318.51, 1567.98];
      fanfare.forEach((freq, idx) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(freq, now + idx * 0.08);
        gain.gain.setValueAtTime(0.22, now + idx * 0.08);
        gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.08 + 0.5);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now + idx * 0.08);
        osc.stop(now + idx * 0.08 + 0.55);
      });
    },

    heroCutin() {
      if (this.isMuted()) return;
      const ctx = this.getCtx();
      if (!ctx) return;
      const now = ctx.currentTime;
      // 科技感跃迁与超载能量斩击音效
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(260, now);
      osc.frequency.exponentialRampToValueAtTime(960, now + 0.16);
      osc.frequency.exponentialRampToValueAtTime(140, now + 0.42);
      gain.gain.setValueAtTime(0.32, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.45);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(now);
      osc.stop(now + 0.46);

      // 低音冲击轰鸣
      const sub = ctx.createOscillator();
      const subGain = ctx.createGain();
      sub.type = 'sine';
      sub.frequency.setValueAtTime(160, now);
      sub.frequency.exponentialRampToValueAtTime(45, now + 0.35);
      subGain.gain.setValueAtTime(0.42, now);
      subGain.gain.exponentialRampToValueAtTime(0.001, now + 0.38);
      sub.connect(subGain);
      subGain.connect(ctx.destination);
      sub.start(now);
      sub.stop(now + 0.4);
    }
  };

  const SLOTS_MASCOTS = [
    {
      id: 'charlotte',
      unit: 'charlotte_maze_nude_enemy',
      name: 'Charlotte 18+',
      title: '魅惑特勤',
      mult: '+18% 爆率',
      favor: 'Lv.7 · 依恋',
      scaleMult: 0.68,
      yRatio: 0.0,
      dialogue: {
        idle: '指挥官，今天手气很旺盛呢，转一转试试看？',
        spin: '幸运能量注入中！转出 MEGA JACKPOT 吧～',
        win: '🎉 命中战术连线！好棒呀指挥官～',
        bigWin: '💖 哇！超级大奖！指挥官太威武了～',
        tap: '呀～摸到了奇怪的地方啦，好痒～'
      }
    },
    {
      id: 'kart',
      unit: 'cartilla_agent_nude_enemy',
      name: 'Cartilla 18+',
      title: '突击先锋',
      mult: '+22% 爆率',
      favor: 'Lv.8 · 亲密',
      scaleMult: 1.26,
      yRatio: -0.06,
      dialogue: {
        idle: '指挥官，突击步枪与好运皆已校准完毕！',
        spin: '全轴功率超载！大奖锁定！',
        win: '精准射击！金币悉数命中！',
        bigWin: '天命头奖！不愧是指挥官大人！',
        tap: '呀……战术挂件很敏感的，轻一点啦！'
      }
    },
    {
      id: 'misa',
      unit: 'misaki_agent_nude_enemy',
      name: 'Misaki 18+',
      title: '王牌狙击',
      mult: '+25% 爆率',
      favor: 'Lv.9 · 誓约',
      scaleMult: 1.26,
      yRatio: -0.06,
      dialogue: {
        idle: '八百米内目标全数锁定，请下达旋转指令。',
        spin: '狙击瞄准中轴，命中概率 100%！',
        win: '命中红心，丰厚战利品入库！',
        bigWin: '一击必杀！这就是长官与我的绝对默契！',
        tap: '指挥官的手心……比瞄准镜还要炽热呢。'
      }
    },
    {
      id: 'ines',
      unit: 'ines_agent_nude_enemy',
      name: 'Ines 18+',
      title: '灵能护盾',
      mult: '+20% 爆率',
      favor: 'Lv.7 · 信任',
      scaleMult: 1.26,
      yRatio: -0.06,
      dialogue: {
        idle: '神圣灵能力场已展开，庇护指挥官每一轮转动。',
        spin: '灵能波动共振！逆转幸运概率！',
        win: '护盾捕捉到充沛的胜利金币能量！',
        bigWin: '奇迹降临！神圣之光指引着最高头奖！',
        tap: '触碰护盾时……心扉也被指挥官打开了呢。'
      }
    },
    {
      id: 'luf',
      unit: 'luf_agent_nude_enemy',
      name: 'Luf 18+',
      title: '圣光圣灵',
      mult: '+24% 爆率',
      favor: 'Lv.8 · 依恋',
      scaleMult: 1.26,
      yRatio: -0.06,
      dialogue: {
        idle: '元气满满！今天的转轮一定会爆出超级大奖！',
        spin: '圣光飞舞！为指挥官破除所有厄运！',
        win: '太棒啦！闪闪发光的战利品到手！',
        bigWin: '哇啊啊！超级大爆奖！指挥官最帅了！',
        tap: '嘻嘻，被指挥官摸头好开心～要抱抱！'
      }
    },
    {
      id: 'astara',
      unit: 'astara_18_unit',
      name: 'Astara 18+',
      title: '深渊魔女',
      mult: '+26% 爆率',
      favor: 'Lv.9 · 誓约',
      scaleMult: 1.26,
      yRatio: -0.06,
      dialogue: {
        idle: '深渊契约在此，渴望力量与金币吗，我的长官？',
        spin: '黑暗轮盘，为吾等献上无尽财宝！',
        win: '契约已成，战利品如约奉上。',
        bigWin: '深渊狂欢！整个奖池都在为您颤抖！',
        tap: '胆敢触碰魔女……做好被彻底吞噬的准备了吗？'
      }
    },
    {
      id: 'aphrodite',
      unit: 'aphrodite_18_unit',
      name: 'Aphrodite 18+',
      title: '爱欲女神',
      mult: '+28% 爆率',
      favor: 'Lv.10 · 沉沦',
      scaleMult: 0.68,
      yRatio: 0.0,
      dialogue: {
        idle: '长官的视线……比最热烈的黄金还要诱人呢～',
        spin: '爱神轻抚转轮～奇迹就要绽放了哦～',
        win: '命中爱意的奖赏，甜美吗，长官？',
        bigWin: '啊……至高的狂喜！长官太强大了～',
        tap: '再靠近一点嘛……感受一下我的心跳～'
      }
    },
    {
      id: 'skye',
      unit: 'skye_skin_nude_enemy',
      name: 'Skye 18+',
      title: '机动先锋',
      mult: '+15% 爆率',
      favor: 'Lv.5 · 亲密',
      scaleMult: 1.26,
      yRatio: -0.06,
      dialogue: {
        idle: '战线准备就绪，随时可以扣动转轮。',
        spin: '全速推进！让滚轮飞速旋转起来！',
        win: '目标锁定，高额战利品已入库！',
        bigWin: '爆裂中奖！这就是我们的战术实力！',
        tap: '战前接触……心跳有点加快了呢。'
      }
    },
    {
      id: 'ivy',
      unit: 'ivy_skin_nude_enemy',
      name: 'Ivy 18+',
      title: '剧毒潜行',
      mult: '+16% 爆率',
      favor: 'Lv.6 · 迷恋',
      scaleMult: 1.26,
      yRatio: -0.06,
      dialogue: {
        idle: '空气里弥漫着幸运的气味呢，指挥官。',
        spin: '下注锁定，命运在为你转动～',
        win: '瞧，甜美的赏金已经到手了～',
        bigWin: '天哪！这就是天命指挥官的力量吗？！',
        tap: '唔……指挥官的手总是这么不安分呢～'
      }
    },
    {
      id: 'kitty',
      unit: 'kitty_player',
      name: 'Kitty 战备',
      title: '看板特勤',
      mult: '+20% 爆率',
      favor: 'Lv.10 · 誓约',
      scaleMult: 1.26,
      yRatio: -0.05,
      dialogue: {
        idle: '指挥官，有我在，今天的转轮一定会大发！',
        spin: '冲鸭！给滚轮来一次华丽的暴击！',
        win: '耶！中奖啦中奖啦！金币哗啦啦！',
        bigWin: '太神啦指挥官！请我吃豪华大餐吧！',
        tap: '喵呜～耳朵好敏感，不要突然摸啦～'
      }
    },
    {
      id: 'regina',
      unit: 'regina_player',
      name: 'Regina 近卫',
      title: '近卫队长',
      mult: '+15% 爆率',
      favor: 'Lv.5 · 亲信',
      scaleMult: 1.26,
      yRatio: -0.04,
      dialogue: {
        idle: '保持专注，胜负就在刹那之间。',
        spin: '全军压上！碾碎那些低倍率！',
        win: '战利品清点完毕，指挥官表现优异。',
        bigWin: '惊人的战果！不愧是我的长官！',
        tap: '在控制台前请保持军纪……不过，准许你握住我的手。'
      }
    }
  ];

  const CUTIN_HEROES = [
    {
      id: 'kart',
      name: 'CARTILLA 18+',
      title: '突击制服',
      art: 'assets/heroes/kart/lega.webp',
      quote: '「全轴功率超载！长官，头奖非你莫属～」',
      color: '#ff3a9d'
    },
    {
      id: 'misa',
      name: 'MISAKI 18+',
      title: '王牌禁锢',
      art: 'assets/heroes/misa/lega.webp',
      quote: '「红心弹道锁定！长官只需享受爆奖快感～」',
      color: '#38e1ff'
    },
    {
      id: 'ines',
      name: 'INES 18+',
      title: '灵能力场',
      art: 'assets/heroes/ines/lega.webp',
      quote: '「灵能力场全开！让赔率随心跳一同狂飙～」',
      color: '#ffe55b'
    },
    {
      id: 'luf',
      name: 'LUF 18+',
      title: '圣光觉醒',
      art: 'assets/heroes/luf/lega.webp',
      quote: '「圣光热力全开！为长官打破所有厄运锁链～」',
      color: '#a78bfa'
    },
    {
      id: 'astara',
      name: 'ASTARA 18+',
      title: '深渊契约',
      art: 'assets/heroes/astara/lega.webp',
      quote: '「深渊契约生效，滚轮已臣服于您的意志～」',
      color: '#f43f5e'
    },
    {
      id: 'aphrodite',
      name: 'APHRODITE 18+',
      title: '爱欲神格',
      art: 'assets/heroes/aphrodite/base.webp',
      quote: '「爱意汹涌绽放～请收下这无尽的黄金恩赐～」',
      color: '#ec4899'
    },
    {
      id: 'ariana',
      name: 'ARIANA 18+',
      title: '暗影战刃',
      art: 'assets/heroes/ariana/base.webp',
      quote: '「影之刃破空！为您斩下所有高倍率组合！」',
      color: '#38bdf8'
    },
    {
      id: 'charlotte',
      name: 'CHARLOTTE 18+',
      title: '魅惑觉醒',
      art: 'assets/heroes/charlotte/nude.webp',
      quote: '「呀～转轮的心跳，比昨夜的私语还要热烈呢～」',
      color: '#f472b6'
    },
    {
      id: 'shelby',
      name: 'SHELBY 18+',
      title: '狂野烈焰',
      art: 'assets/heroes/shelby/nude.webp',
      quote: '「燃烧起来吧！让金币如狂暴暴雨般倾泻！」',
      color: '#fb923c'
    },
    {
      id: 'esther',
      name: 'ESTHER 18+',
      title: '绝对领域',
      art: 'assets/heroes/esther/nude.webp',
      quote: '「战术核心超频！所有轴线尽在掌控之中～」',
      color: '#38bdf8'
    },
    {
      id: 'bonita',
      name: 'BONITA 18+',
      title: '暗夜魅影',
      art: 'assets/heroes/bonita/nude.webp',
      quote: '「长官的手气……今晚已被我施下不败魔法～」',
      color: '#c084fc'
    },
    {
      id: 'casey',
      name: 'CASEY 18+',
      title: '战场甜心',
      art: 'assets/heroes/casey/nude.webp',
      quote: '「火力全开！为您打通通往财富的超音速通道！」',
      color: '#34d399'
    }
  ];

  const SlotsGame = { spin, resolveSymbol, resolveSymbolVisual, SYMBOL_ASSETS, SLOTS_MASCOTS, CUTIN_HEROES };
  window.SlotsGame = SlotsGame;

  const SlotsUI = {
    config: null,
    bet: 20,
    freeSpins: 0,
    turbo: false,
    autoSpin: false,
    autoSpinsLeft: 0,
    spinning: false,

    isReduceMotion() {
      const sys = typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      const app = !!(typeof APP !== 'undefined' && APP.state && APP.state.settings && APP.state.settings.reduceMotion);
      return !!(sys || app);
    },
    currentReelGrid: null,
    mascotIndex: 0,
    mascotStage: null,
    speechTimer: null,

    async init() {
      this.config = (window.APP && APP.data && APP.data.slots) || null;
      if (!this.config) {
        if (typeof window.safeFetchJson === 'function') {
          this.config = await window.safeFetchJson('data/slots.json');
        } else {
          const response = await fetch('data/slots.json');
          if (!response.ok && response.status !== 0) throw new Error(`Failed to load slots (${response.status})`);
          this.config = await response.json();
        }
      }
      this.bet = this.config.defaultBet || 20;
      this.freeSpins = (window.APP && APP.state && (
        (APP.state.slots && APP.state.slots.freeSpins) ||
        (APP.state.slotsSession && APP.state.slotsSession.freeSpins)
      )) || 0;
      this.bind();
      this.bindModals();
      this.render();
      this.renderPlaceholder();
      this.initMarqueeLights();
      this.initMascot();
    },

    openSheet(el) {
      if (!el) return;
      el.hidden = false;
      el.classList.add('open');
    },

    closeSheet(el) {
      if (!el) return;
      el.classList.remove('open');
      el.hidden = true;
    },

    bindModals() {
      // 伴游破产救济领取
      const bailoutModal = document.getElementById('modal-slots-bailout');
      const bailoutBtn = document.getElementById('btn-bailout-claim');
      if (bailoutBtn) {
        bailoutBtn.onclick = () => {
          const state = APP.state;
          state.gold = (state.gold || 0) + 300;
          state.slotsSession = state.slotsSession || {};
          state.slotsSession.bailoutClaims = (state.slotsSession.bailoutClaims || 0) + 1;
          state.slotsSession.luckyBailout = true; // 下一次旋转保底大翻盘！
          LGSave.recordTransaction(state, 'gold', 300, 'slots_bailout');
          LGSave.persist(state);
          this.closeSheet(bailoutModal);
          this.render();
          SlotsAudio.coin();
          if (window.NativeBridge) NativeBridge.haptic('success');
          UI.toast('🎉 伴游空投已到账！获得 300 金币与天命好运，快拉动拉杆！');
          const m = SLOTS_MASCOTS[this.mascotIndex || 0];
          if (m) this.setMascotSpeech('收到我的私密补给啦？这次女神一定会眷顾你的～');
        };
      }
      if (bailoutModal) {
        bailoutModal.addEventListener('click', (e) => {
          if (e.target === bailoutModal) this.closeSheet(bailoutModal);
        });
      }

      // 充值特权金库
      const rechargeModal = document.getElementById('modal-slots-recharge');
      const rechargeBtn = document.getElementById('btn-slots-recharge-pay');
      const rechargeClose = document.getElementById('btn-slots-recharge-close');
      if (rechargeClose) {
        rechargeClose.onclick = () => {
          this.closeSheet(rechargeModal);
        };
      }
      if (rechargeModal) {
        rechargeModal.addEventListener('click', (e) => {
          if (e.target === rechargeModal) this.closeSheet(rechargeModal);
        });
      }
      if (rechargeBtn) {
        rechargeBtn.onclick = () => {
          const state = APP.state;
          state.gold = (state.gold || 0) + 50000;
          state.slotsSession = state.slotsSession || {};
          state.slotsSession.isVip = true;
          this.freeSpins += 10;
          state.slotsSession.freeSpins = this.freeSpins;
          state.slotsSession.isFreeSpin = true;
          if (state.slots) state.slots.freeSpins = this.freeSpins;
          LGSave.recordTransaction(state, 'gold', 50000, 'first_recharge_vip');
          LGSave.persist(state);
          this.closeSheet(rechargeModal);
          this.render();
          SlotsAudio.bigWin();
          this.dropCoins(24);
          if (window.NativeBridge) NativeBridge.haptic('bigWin');
          UI.toast('👑 恭喜指挥官！首充超值金库已激活！50,000 金币与 VIP 1 极速到账！');
          const m = SLOTS_MASCOTS[this.mascotIndex || 0];
          if (m) this.setMascotSpeech('哇！太霸气了指挥官！全员 18+ 装扮已随侍就绪～');
        };
      }
    },

    handleOutOfGold() {
      const state = (typeof window !== 'undefined' && window.APP && window.APP.state) || (typeof APP !== 'undefined' ? APP.state : null);
      if (!state) return;
      state.slotsSession = state.slotsSession || {};
      const claims = state.slotsSession.bailoutClaims || 0;
      if (claims < 3) {
        const modal = document.getElementById('modal-slots-bailout');
        this.openSheet(modal);
        if (window.NativeBridge) NativeBridge.haptic('warning');
      } else {
        const modal = document.getElementById('modal-slots-recharge');
        this.openSheet(modal);
        if (window.NativeBridge) NativeBridge.haptic('selection');
      }
    },

    initMascot() {
      const canvas = document.getElementById('slots-mascot-canvas');
      if (!canvas || !window.SpineStage) return;
      if (!this.mascotStage) {
        this.mascotStage = new SpineStage(canvas, {
          heightRatio: 1.0,
          scaleMultiplier: 0.68,
          yRatio: 0.0,
          xRatio: 0.5
        });
      }
      const switchBtn = document.getElementById('btn-slots-mascot-switch');
      if (switchBtn) switchBtn.onclick = () => this.switchMascot();

      const actorWrap = document.getElementById('mascot-actor-wrap');
      if (actorWrap) {
        actorWrap.onclick = () => this.onMascotTap();
        actorWrap.onkeydown = (e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            this.onMascotTap();
          }
        };
      }

      this.loadCurrentMascot(false);
      this.initCutin();
    },

    initCutin() {
      const canvas = document.getElementById('slots-cutin-canvas');
      if (!canvas || !window.SpineStage) return;
      if (!this.cutinStage) {
        this.cutinStage = new SpineStage(canvas, {
          heightRatio: 1.15,
          scaleMultiplier: 1.25,
          yRatio: -0.15,
          xRatio: 0.52
        });
      }
    },

    async triggerInSpinHeroEntrance(result) {
      const cutinEl = document.getElementById('slots-hero-cutin');
      const artImg = document.getElementById('slots-cutin-art');
      const nameEl = document.getElementById('cutin-hero-name');
      const quoteEl = document.getElementById('cutin-quote');
      const slashFx = document.getElementById('cutin-slash-fx');
      const reelWindow = document.getElementById('slots-reel-window');
      if (!cutinEl || !artImg) return;

      const hero = CUTIN_HEROES[Math.floor(Math.random() * CUTIN_HEROES.length)];
      artImg.src = hero.art;
      artImg.alt = `${hero.name} 18+ 原画突入`;

      if (nameEl) nameEl.textContent = `${hero.name} 魅惑突入`;
      if (quoteEl) quoteEl.textContent = hero.quote;
      cutinEl.style.setProperty('--cutin-accent', hero.color || '#ff3a9d');

      cutinEl.hidden = false;
      cutinEl.classList.remove('active', 'exit');
      artImg.classList.remove('art-impact-zoom');
      void cutinEl.offsetWidth; // force reflow
      cutinEl.classList.add('active');
      artImg.classList.add('art-impact-zoom');

      if (reelWindow) reelWindow.classList.add('cutin-shake');
      SlotsAudio.heroCutin();
      if (window.NativeBridge) NativeBridge.haptic('impact');

      if (slashFx) {
        slashFx.classList.remove('slash-anim');
        void slashFx.offsetWidth;
        slashFx.classList.add('slash-anim');
      }

      // 在各轴依次停转前夕平滑退出，确保开奖无缝展现
      const holdTime = this.turbo ? 480 : 800;
      setTimeout(() => {
        if (cutinEl) cutinEl.classList.add('exit');
        if (reelWindow) reelWindow.classList.remove('cutin-shake');
        setTimeout(() => {
          if (cutinEl) {
            cutinEl.classList.remove('active', 'exit');
            cutinEl.hidden = true;
          }
        }, 300);
      }, holdTime);
    },

    loadCurrentMascot(showDialogue = false) {
      const m = SLOTS_MASCOTS[this.mascotIndex || 0];
      if (!m) return;
      const nameEl = document.getElementById('slots-mascot-name');
      const multEl = document.getElementById('mascot-lucky-mult');
      const favorEl = document.getElementById('mascot-favor-level');
      if (nameEl) nameEl.textContent = m.name;
      if (multEl) multEl.textContent = m.mult;
      if (favorEl) favorEl.textContent = m.favor;
      if (showDialogue) {
        this.setMascotSpeech(m.dialogue.idle, 3200);
      } else {
        const bubble = document.getElementById('mascot-speech');
        if (bubble) bubble.hidden = true;
      }

      if (this.mascotStage) {
        this.mascotStage.opts.scaleMultiplier = m.scaleMult || 1.28;
        this.mascotStage.opts.yRatio = m.yRatio !== undefined ? m.yRatio : -0.06;
        this.mascotStage.show(m.unit, { anim: 'idle' });
      }
    },

    setMascotSpeech(text, autoHideMs = 3500) {
      const bubble = document.getElementById('mascot-speech');
      const textEl = document.getElementById('mascot-speech-text');
      if (!bubble || !textEl) return;
      textEl.textContent = text;
      bubble.hidden = false;
      clearTimeout(this.speechTimer);
      this.speechTimer = setTimeout(() => {
        bubble.hidden = true;
      }, autoHideMs);
    },

    switchMascot() {
      this.mascotIndex = (this.mascotIndex + 1) % SLOTS_MASCOTS.length;
      SlotsAudio.coin();
      this.loadCurrentMascot(true);
      const m = SLOTS_MASCOTS[this.mascotIndex];
      UI.toast(`✦ 幸运伴游已切换为：${m.name}`);
    },

    onMascotSpin() {
      const m = SLOTS_MASCOTS[this.mascotIndex || 0];
      if (!m) return;
      this.setMascotSpeech(m.dialogue.spin, 2800);
      if (this.mascotStage && this.mascotStage.field && this.mascotStage.view) {
        this.mascotStage.field.setAnim(this.mascotStage.view, 'hit', false);
        setTimeout(() => {
          if (this.mascotStage && this.mascotStage.view) {
            this.mascotStage.field.resumeIdle(this.mascotStage.view);
          }
        }, 800);
      }
    },

    onMascotWin(win, combo) {
      const m = SLOTS_MASCOTS[this.mascotIndex || 0];
      if (!m) return;
      if (win > 0) {
        this.setMascotSpeech(combo >= 3 ? m.dialogue.bigWin : m.dialogue.win, 3500);
        if (this.mascotStage && this.mascotStage.field && this.mascotStage.view) {
          this.mascotStage.field.setAnim(this.mascotStage.view, 'hit', false);
          setTimeout(() => {
            if (this.mascotStage && this.mascotStage.view) {
              this.mascotStage.field.resumeIdle(this.mascotStage.view);
            }
          }, 1200);
        }
      }
    },

    onMascotTap() {
      const m = SLOTS_MASCOTS[this.mascotIndex || 0];
      if (!m) return;
      SlotsAudio.coin();
      this.setMascotSpeech(m.dialogue.tap, 3000);
      if (this.mascotStage && this.mascotStage.field && this.mascotStage.view) {
        this.mascotStage.field.setAnim(this.mascotStage.view, 'damage', false);
        setTimeout(() => {
          if (this.mascotStage && this.mascotStage.view) {
            this.mascotStage.field.resumeIdle(this.mascotStage.view);
          }
        }, 700);
      }
    },

    resumeMascot() {
      if (this.mascotStage) {
        this.mascotStage.start();
        const m = SLOTS_MASCOTS[this.mascotIndex || 0];
        if (m) {
          this.mascotStage.opts.scaleMultiplier = m.scaleMult || 1.28;
          this.mascotStage.opts.yRatio = m.yRatio !== undefined ? m.yRatio : -0.06;
          this.mascotStage.show(m.unit, { anim: 'idle' });
        }
      }
    },

    pauseMascot() {
      if (this.mascotStage) {
        this.mascotStage.stop();
      }
    },

    bind() {
      const spinButton = document.getElementById('btn-slots-spin');
      if (spinButton) spinButton.onclick = () => this.pullLever();

      const sideLever = document.getElementById('cabinet-side-lever');
      if (sideLever) sideLever.onclick = () => this.pullLever();

      const betDown = document.getElementById('btn-slots-bet-down');
      const betUp = document.getElementById('btn-slots-bet-up');
      if (betDown) betDown.onclick = () => { this.bet = Math.max(this.config.minBet, this.bet - 10); this.render(); SlotsAudio.coin(); };
      if (betUp) betUp.onclick = () => { this.bet = Math.min(this.config.maxBet, this.bet + 10); this.render(); SlotsAudio.coin(); };

      const maxBet = document.getElementById('btn-slots-max-bet');
      if (maxBet) maxBet.onclick = () => { this.bet = this.config.maxBet; this.render(); SlotsAudio.coin(); };

      const paytable = document.getElementById('btn-slots-paytable');
      const paytableClose = document.getElementById('btn-slots-paytable-close');
      if (paytable) paytable.onclick = () => { this.renderPaytable(); document.getElementById('slots-paytable').hidden = false; };
      if (paytableClose) paytableClose.onclick = () => { document.getElementById('slots-paytable').hidden = true; };

      const turbo = document.getElementById('btn-slots-turbo');
      if (turbo) turbo.onclick = () => {
        this.turbo = !this.turbo;
        turbo.setAttribute('aria-pressed', String(this.turbo));
        turbo.classList.toggle('active', this.turbo);
        turbo.textContent = this.turbo ? 'TURBO ON' : 'TURBO';
        SlotsAudio.coin();
      };

      const auto = document.getElementById('btn-slots-auto');
      if (auto) auto.onclick = () => {
        if (this.autoSpin) {
          this.stopAutoSpin();
        } else {
          this.startAutoSpin(25);
        }
      };

      const closeFeature = document.getElementById('btn-slots-feature-close');
      if (closeFeature) closeFeature.onclick = () => this.hideFeature();

      // Keyboard spacebar spin shortcut
      window.addEventListener('keydown', (e) => {
        if (e.code === 'Space' && !e.repeat && document.getElementById('screen-slots')?.classList.contains('active')) {
          const pt = document.getElementById('slots-paytable');
          const ft = document.getElementById('slots-feature');
          if ((!pt || pt.hidden) && (!ft || ft.hidden)) {
            e.preventDefault();
            this.playSpin();
          }
        }
      });
    },

    pullLever() {
      if (this.spinning) return;
      const spinBtn = document.getElementById('btn-slots-spin');
      const sideLever = document.getElementById('cabinet-side-lever');
      if (spinBtn) {
        spinBtn.classList.add('pulling');
        setTimeout(() => spinBtn.classList.remove('pulling'), 360);
      }
      if (sideLever) {
        sideLever.classList.add('pulling');
        setTimeout(() => sideLever.classList.remove('pulling'), 360);
      }
      SlotsAudio.lever();
      this.playSpin();
    },

    startAutoSpin(count) {
      this.autoSpin = true;
      this.autoSpinsLeft = count;
      const btn = document.getElementById('btn-slots-auto');
      if (btn) {
        btn.setAttribute('aria-pressed', 'true');
        btn.classList.add('active');
        btn.textContent = `AUTO (${this.autoSpinsLeft})`;
      }
      if (!this.spinning) this.playSpin();
    },

    stopAutoSpin() {
      this.autoSpin = false;
      this.autoSpinsLeft = 0;
      if (this._autoSpinTimer) {
        clearTimeout(this._autoSpinTimer);
        this._autoSpinTimer = null;
      }
      const btn = document.getElementById('btn-slots-auto');
      if (btn) {
        btn.setAttribute('aria-pressed', 'false');
        btn.classList.remove('active');
        btn.textContent = 'AUTO';
      }
    },

    pause() {
      this.stopAutoSpin();
      this.pauseMascot();
      this.spinning = false;
      const button = document.getElementById('btn-slots-spin');
      if (button) button.disabled = false;
    },

    destroy() {
      this.stopAutoSpin();
      if (this.mascotStage && typeof this.mascotStage.stop === 'function') {
        this.mascotStage.stop();
      }
      if (window.APP && APP.stages && APP.stages.slotsFeature && typeof APP.stages.slotsFeature.stop === 'function') {
        try { APP.stages.slotsFeature.stop(); } catch (e) {}
      }
    },

    initMarqueeLights() {
      const container = document.getElementById('marquee-lights');
      if (!container || container.children.length > 0) return;
      for (let i = 0; i < 16; i++) {
        const bulb = document.createElement('span');
        bulb.className = 'ml-bulb';
        container.appendChild(bulb);
      }
    },

    render() {
      if (!this.config || !window.APP || !APP.state) return;
      const state = APP.state;
      const gold = document.getElementById('slots-gold');
      const bet = document.getElementById('slots-bet');
      const free = document.getElementById('slots-free-spins');
      const hint = document.getElementById('slots-hint');
      const statusBar = document.getElementById('slots-status-bar');
      const winTier = document.getElementById('slots-win-tier');
      const spinBtnSub = document.querySelector('.slots-master-spin-btn .spin-sub-title');
      const spinBtnMain = document.querySelector('.slots-master-spin-btn .spin-main-title');

      if (gold) gold.textContent = (state.gold || 0).toLocaleString();
      if (bet) bet.textContent = this.bet.toLocaleString();
      if (free) free.textContent = this.freeSpins;

      const hasFree = this.freeSpins > 0;
      if (statusBar) statusBar.classList.toggle('has-free-spins', hasFree);

      if (hint) {
        hint.textContent = hasFree
          ? `免费大旋转进行中 · 本次旋转免扣金币 · 狂暴倍率生效`
          : `单次旋转消耗 ${this.bet} 金币 · 命中 3 个 Scatter 激活免费大旋转`;
      }
      if (winTier && hasFree && winTier.textContent === 'BASE GAME') {
        winTier.textContent = 'FREE SPINS';
        winTier.className = 'slots-badge win-active';
      }
      if (spinBtnMain && spinBtnSub) {
        if (hasFree) {
          spinBtnMain.textContent = `FREE SPIN · 免费旋转`;
          spinBtnSub.textContent = `剩余 ${this.freeSpins} 次 · 本次旋转免扣金币`;
        } else {
          spinBtnMain.textContent = `SPIN 旋转拉杆`;
          spinBtnSub.textContent = `PULL LEVER · 开启幸运旋转`;
        }
      }

      const turbo = document.getElementById('btn-slots-turbo');
      if (turbo) turbo.setAttribute('aria-pressed', String(this.turbo));
      const jackpotValues = { grand: 50000, major: 10000, minor: 2000, mini: 500 };
      Object.entries(jackpotValues).forEach(([tier, amount]) => {
        const value = document.getElementById(`slots-${tier}`);
        if (value) value.textContent = amount.toLocaleString();
      });
    },

    renderGrid(result) {
      const grid = document.getElementById('slots-grid');
      if (!grid || !result) return;
      grid.innerHTML = '';
      this.currentReelGrid = result.grid;
      const wins = new Set(result.wins.flatMap((win) => this.config.paylines[win.line - 1].map((row, col) => `${row}-${col}`)));
      
      result.grid.forEach((row, rowIndex) => row.forEach((id, colIndex) => {
        const symbol = this.config.symbols.find((item) => item.id === id);
        const tile = document.createElement('div');
        const isWinning = wins.has(`${rowIndex}-${colIndex}`);
        tile.className = 'slot-symbol ' + (symbol ? symbol.kind : '') + (isWinning ? ' winning' : '');
        tile.setAttribute('data-row', rowIndex);
        tile.setAttribute('data-col', colIndex);

        const iconSrc = (symbol && symbol.icon) || SYMBOL_ASSETS[id] || '';
        if (iconSrc) {
          tile.style.backgroundImage = `url(${iconSrc})`;
        }
        tile.innerHTML = `<span class="symbol-label">${symbol ? symbol.label : id}</span>`;
        grid.appendChild(tile);
      }));

      // Render neon laser paylines
      const overlay = document.getElementById('slots-line-overlay');
      if (overlay) {
        overlay.innerHTML = '';
        result.wins.forEach((win, idx) => {
          const rows = this.config.paylines[win.line - 1];
          const line = document.createElementNS('http://www.w3.org/2000/svg', 'polyline');
          // Map 5 cols to svg coordinates (10, 30, 50, 70, 90) and rows to (10, 30, 50)
          const points = rows.map((row, col) => `${10 + col * 20},${10 + row * 20}`).join(' ');
          line.setAttribute('points', points);
          const color = PAYLINE_COLORS[(win.line - 1) % PAYLINE_COLORS.length];
          line.style.stroke = color;
          line.style.filter = `drop-shadow(0 0 6px ${color})`;
          overlay.appendChild(line);
        });
        overlay.classList.toggle('active', result.wins.length > 0);
      }

      // Update payline rail indicators
      const activeLines = new Set(result.wins.map((w) => String(w.line)));
      document.querySelectorAll('.payline-rails .pl-tag').forEach((tag) => {
        const line = tag.getAttribute('data-line');
        tag.classList.toggle('active', activeLines.has(line));
      });
    },

    renderPaytable() {
      const list = document.getElementById('slots-paytable-list');
      if (!list || !this.config) return;
      list.innerHTML = '';
      this.config.symbols.forEach((symbol) => {
        const iconSrc = symbol.icon || SYMBOL_ASSETS[symbol.id] || '';
        const row = document.createElement('div');
        row.className = 'paytable-row ' + symbol.kind;
        const payouts = symbol.payouts || {};
        const payoutDesc = symbol.kind === 'scatter' 
          ? '3个及以上: 奖励5次免费旋转'
          : `2连 ${payouts['2'] || 0}x · 3连 ${payouts['3'] || 0}x · 5连 ${payouts['5'] || 0}x`;
        row.innerHTML = `
          <div class="pay-icon" style="background-image:url(${iconSrc})"></div>
          <div class="pay-info">
            <div class="pay-header">
              <b>${symbol.label}</b>
              <span class="pay-kind">${symbol.kind.toUpperCase()}</span>
            </div>
            <span class="pay-rates">${payoutDesc}</span>
          </div>`;
        list.appendChild(row);
      });
    },

    renderPlaceholder() {
      const grid = document.getElementById('slots-grid');
      if (!grid || grid.children.length) return;
      grid.innerHTML = '';
      const initialSymbols = [
        'kitty', 'wild', 'regina', 'dominica', 'esther',
        'drone_enemy', 'kitty', 'scatter', 'hilda_enemy', 'dominica',
        'regina', 'drone_enemy', 'wild', 'esther', 'kitty'
      ];
      for (let i = 0; i < 15; i += 1) {
        const id = initialSymbols[i] || 'kitty';
        const symbol = this.config ? this.config.symbols.find((item) => item.id === id) : null;
        const tile = document.createElement('div');
        tile.className = 'slot-symbol ' + (symbol ? symbol.kind : 'hero');
        const iconSrc = (symbol && symbol.icon) || SYMBOL_ASSETS[id] || '';
        if (iconSrc) tile.style.backgroundImage = `url(${iconSrc})`;
        tile.innerHTML = `<span class="symbol-label">${symbol ? symbol.label : id}</span>`;
        grid.appendChild(tile);
      }
    },

    async playSpin() {
      const state = (typeof window !== 'undefined' && window.APP && window.APP.state) || (typeof APP !== 'undefined' ? APP.state : null);
      if (this.spinning || !this.config || !state) return;
      state.slotsSession = state.slotsSession || {};
      const useFree = this.freeSpins > 0;
      if (!useFree && (state.gold || 0) < this.bet) {
        this.stopAutoSpin();
        this.handleOutOfGold();
        return;
      }
      this.spinning = true;
      this.onMascotSpin();

      const button = document.getElementById('btn-slots-spin');
      if (button) button.disabled = true;

      // 保证引擎与 UI 使用单一、统一的免费旋转状态源
      state.slotsSession.freeSpins = this.freeSpins;
      state.slotsSession.isFreeSpin = useFree;

      // 结合心流状态的确定性 PRNG 轮盘计算
      const seed = (Date.now() ^ ((state.stats && state.stats.slotsSpins) || 0)) >>> 0;
      const result = SlotsGame.spin(this.config, this.bet, seed, state.slotsSession);
      state.slotsSession.totalSpins = (state.slotsSession.totalSpins || 0) + 1;
      if (state.slotsSession.luckyBailout) {
        state.slotsSession.luckyBailout = false;
      }

      // 扣除押注或消耗免费旋转（严格只递减 1 次，严禁双重递减）
      if (!useFree) {
        state.gold -= this.bet;
        LGSave.recordTransaction(state, 'gold', -this.bet, 'slots_spin');
      } else {
        this.freeSpins -= 1;
      }

      // Update basic status & clear previous line highlights
      const overlay = document.getElementById('slots-line-overlay');
      if (overlay) overlay.classList.remove('active');
      document.querySelectorAll('.payline-rails .pl-tag').forEach((t) => t.classList.remove('active'));
      const statusMessage = document.getElementById('slots-status-message');
      if (statusMessage) statusMessage.textContent = '滚轮高速旋转中...';

      // Reel window spin state
      const reelWindow = document.getElementById('slots-reel-window');
      const reel = document.getElementById('slots-grid');
      if (reel) reel.classList.add('spinning');
      if (reelWindow) reelWindow.classList.add('spinning');

      // 旋转中途特勤动态突入：高赔率(>=5x)/大奖(combo>=3)/免费旋转/Jackpot 突入，普通空转或超低赔率保持滚轮纯净
      const reduceMotion = this.isReduceMotion();
      const isHighImpactSpin = result.isJackpot || result.bestCombo >= 3 || (result.totalWin >= this.bet * 5) || (result.freeSpinsAwarded > 0);
      const shouldCutin = !reduceMotion && isHighImpactSpin;
      if (shouldCutin) {
        this.triggerInSpinHeroEntrance(result);
      }
      const stepDuration = reduceMotion ? 10 : (this.turbo ? 80 : 200);
      const baseDelay = reduceMotion ? 10 : (this.turbo ? 120 : 450);

      // Staggered column stops with physical bounce & audio & haptic
      for (let col = 0; col < this.config.reels; col++) {
        await new Promise((resolve) => setTimeout(resolve, col === 0 ? baseDelay : stepDuration));
        SlotsAudio.reelStop(col);
        if (window.NativeBridge && !reduceMotion) NativeBridge.haptic('selection');
        // Add momentary column bounce effect
        if (reelWindow && !reduceMotion) {
          reelWindow.classList.add(`stop-col-${col}`);
          setTimeout(() => reelWindow.classList.remove(`stop-col-${col}`), 220);
        }
      }

      // Final stop
      if (reel) reel.classList.remove('spinning');
      if (reelWindow) reelWindow.classList.remove('spinning');
      this.renderGrid(result);

      // Near-Miss 假近中急促警报与中性客观提示
      if (result.isNearMiss) {
        if (reelWindow) reelWindow.classList.add('near-miss-tension');
        if (statusMessage) statusMessage.textContent = '⚡ 差一格达成 3 连 Scatter！本次仅出现 2 个能量核心。';
        const m = SLOTS_MASCOTS[this.mascotIndex || 0];
        if (m) this.setMascotSpeech('唔……差一点就凑齐 3 个核心了，再接再厉指挥官！');
        if (window.NativeBridge) NativeBridge.haptic('nearMiss');
        setTimeout(() => { if (reelWindow) reelWindow.classList.remove('near-miss-tension'); }, 2200);
      }

      // Record win & transactions
      const win = result.totalWin;
      state.gold += win;
      if (win > 0) LGSave.recordTransaction(state, 'gold', win, 'slots_win');
      if (result.freeSpinsAwarded) this.freeSpins += result.freeSpinsAwarded;

      // 奖池命中动效与提示
      if (result.isJackpot && result.jackpot) {
        const tierCls = 'tier-' + result.jackpot.tier.toLowerCase();
        const tierEl = document.querySelector(`.jackpot-tier.${tierCls}`);
        if (tierEl) {
          tierEl.classList.add('jackpot-won');
          setTimeout(() => tierEl.classList.remove('jackpot-won'), 4000);
        }
        UI.toast(`🎰 恭喜斩获【${result.jackpot.tier} JACKPOT】！额外奖金 +${result.jackpot.bonus.toLocaleString()} 金币！`);
        SlotsAudio.bigWin();
        this.dropCoins(36);
        if (window.NativeBridge) NativeBridge.haptic('bigWin');
      }

      // Check for hero symbol wins to directly unlock into inventory/roster
      const heroWins = (result.wins || []).filter((w) => {
        const s = (this.config.symbols || []).find((item) => item.id === w.symbol);
        return s && (s.kind === 'hero' || ['kitty', 'dominica', 'regina', 'florence', 'esther'].includes(s.id));
      });
      if (heroWins.length > 0) {
        heroWins.forEach((hw) => {
          const sym = (this.config.symbols || []).find((item) => item.id === hw.symbol);
          const resolved = SlotsGame.resolveSymbol(sym, APP.data);
          const heroKey = (resolved && resolved.key) || hw.symbol;
          const heroName = (resolved && resolved.name) || (sym && sym.label) || heroKey;
          if (heroKey) {
            state.owned = state.owned || [];
            state.roster = state.roster || {};
            if (!state.owned.includes(heroKey)) {
              state.owned.push(heroKey);
              state.roster[heroKey] = LGSave.createHeroInstance(heroKey, { level: 30, stars: 5, skillLevel: 10 });
              UI.toast(`🎉 抽中特勤英雄【${heroName}】！已直接加入特勤仓库！`);
            } else {
              state.wallet.shards = (state.wallet.shards || 0) + 50;
              state.shards = state.wallet.shards;
              UI.toast(`🎉 抽中已有英雄【${heroName}】，已转化为 50 突破碎片直接入库！`);
            }
          }
        });
      }

      state.slotsSession.freeSpins = this.freeSpins;
      state.slotsSession.isFreeSpin = this.freeSpins > 0;
      state.slots = state.slots || { version: 2, lastSeed: null, freeSpins: 0 };
      state.slots.lastSeed = seed;
      state.slots.freeSpins = this.freeSpins;
      state.stats.slotsSpins = (state.stats.slotsSpins || 0) + 1;
      if (win > 0) state.stats.slotsWins = (state.stats.slotsWins || 0) + 1;
      if (window.Missions) Missions.track(state, APP.data.missions, 'slots_spin', 1);
      LGSave.persist(state);

      // Update win displays with rolling odometer
      const resultText = document.getElementById('slots-result');
      if (resultText) {
        const jackpotText = (result.isJackpot && result.jackpot) ? ` · 【${result.jackpot.tier} JACKPOT】+${result.jackpot.bonus.toLocaleString()}` : '';
        resultText.textContent = win
          ? `赢得 ${win.toLocaleString()} 金币${result.freeSpinsAwarded ? ` · 免费旋转 +${result.freeSpinsAwarded}` : ''}${jackpotText}`
          : (result.freeSpinsAwarded ? `Scatter · 免费旋转 +${result.freeSpinsAwarded}` : '未中奖，再试一次');
      }

      const winTier = document.getElementById('slots-win-tier');
      const tierLabel = (result.isJackpot && result.jackpot)
        ? `${result.jackpot.tier} JACKPOT`
        : (result.bestCombo >= 4 ? 'MEGA HIT' : result.bestCombo === 3 ? 'SUPER WIN' : win > 0 ? 'LINE HIT' : 'BASE GAME');
      if (winTier) {
        winTier.textContent = tierLabel;
        winTier.className = 'slots-badge ' + (win > 0 ? 'win-active' : '');
      }

      if (statusMessage) {
        statusMessage.textContent = result.bestCombo >= 3 ? 'MEGA HIT · 三连及以上奖励！' : result.bestCombo === 2 ? 'LINE HIT · 二连奖励' : (win > 0 ? 'WIN' : 'BASE GAME');
      }

      // Rolling odometer count-up on slots-last-win
      await this.rollCountUp(win);
      this.onMascotWin(win, result.bestCombo);

      // Trigger audio & coin cascade & tactile haptics
      if (win > 0) {
        if (result.bestCombo >= 3 || win >= this.bet * 5) {
          SlotsAudio.bigWin();
          this.dropCoins(18);
          if (window.NativeBridge) NativeBridge.haptic('bigWin');
        } else {
          SlotsAudio.winChime(result.bestCombo);
          this.dropCoins(6);
          if (window.NativeBridge) NativeBridge.haptic('success');
        }
        if (window.LGVFX) LGVFX.victoryBurst(document.getElementById('slots-grid'));
      } else if (!result.isNearMiss) {
        SlotsAudio.coin();
        if (window.NativeBridge) NativeBridge.haptic('light');
      }

      // Feature cut-in popup
      // 依 Codex 审计报告整改：普通 2 连线通过滚轮连线高亮、跳字计数器与伴游反应自然反馈，保持机器快节奏；
      // 仅在 3 连线及以上、或免费旋转触发、或高额大奖（>=8倍下注）时弹出大奖特勤奖励弹层，避免视觉疲劳与抢夺焦点
      const isFeatureReward = result.bestCombo >= 3 || (result.freeSpinsAwarded > 0) || (win >= this.bet * 8);
      if (isFeatureReward) {
        await this.showFeature(result.featureSymbol, result.bestCombo >= 3);
      }

      this.spinning = false;
      if (button) button.disabled = false;
      this.render();
      UI.renderHud(state);

      // Auto spin loop
      if (this.autoSpin) {
        this.autoSpinsLeft -= 1;
        const autoBtn = document.getElementById('btn-slots-auto');
        if (autoBtn) autoBtn.textContent = `AUTO (${this.autoSpinsLeft})`;
        if (this.autoSpinsLeft <= 0 || (state.gold || 0) < this.bet) {
          this.stopAutoSpin();
        } else {
          setTimeout(() => { if (this.autoSpin) this.playSpin(); }, 600);
        }
      }
    },

    async rollCountUp(targetAmount) {
      const lastWin = document.getElementById('slots-last-win');
      if (!lastWin) return;
      if (targetAmount === 0) {
        lastWin.textContent = '0';
        return;
      }
      const duration = 400;
      const start = 0;
      const startTime = performance.now();
      return new Promise((resolve) => {
        function tick(now) {
          const progress = Math.min(1, (now - startTime) / duration);
          const current = Math.floor(start + (targetAmount - start) * progress);
          lastWin.textContent = current.toLocaleString();
          if (progress < 1) {
            requestAnimationFrame(tick);
          } else {
            lastWin.textContent = targetAmount.toLocaleString();
            resolve();
          }
        }
        requestAnimationFrame(tick);
      });
    },

    dropCoins(count) {
      const basin = document.getElementById('tray-coins');
      if (!basin) return;
      basin.innerHTML = '';
      for (let i = 0; i < count; i++) {
        const coin = document.createElement('div');
        coin.className = 'hopper-coin';
        coin.style.left = `${15 + Math.random() * 70}%`;
        coin.style.animationDelay = `${i * 60}ms`;
        basin.appendChild(coin);
      }
      setTimeout(() => { if (basin) basin.innerHTML = ''; }, 2400);
    },

    async showFeature(symbol, playCg) {
      const layer = document.getElementById('slots-feature');
      const canvas = document.getElementById('slots-feature-canvas');
      const portrait = document.getElementById('slots-feature-portrait');
      const name = document.getElementById('slots-feature-name');
      const badge = document.getElementById('slots-feature-badge');
      const quote = document.getElementById('slots-feature-quote');
      const closeBtn = document.getElementById('btn-slots-feature-close');
      if (!layer || !symbol) return;
      if (typeof symbol === 'string') symbol = this.config.symbols.find((item) => item.id === symbol);
      if (!symbol) return;

      const visual = SlotsGame.resolveSymbolVisual(symbol, APP.data);
      layer.hidden = false;

      if (badge) {
        badge.textContent = visual.isHero ? '✦ 18+ AWAKEN MEGA HIT ✦' : (playCg ? 'MEGA HIT 3-COMBO' : 'LINE HIT COMBO');
      }
      if (name) {
        name.textContent = visual.isHero ? `【${visual.displayName}】18+ 觉醒登场` : `${visual.displayName} · 连线达成`;
      }
      if (quote) {
        quote.textContent = visual.quote;
      }

      if (canvas) canvas.hidden = true;
      if (APP.stages && APP.stages.slotsFeature) {
        try { APP.stages.slotsFeature.stop(); } catch (e) {}
      }

      if (portrait) {
        portrait.hidden = false;
        portrait.onerror = function() {
          this.onerror = null;
          this.src = visual.fallbackIcon;
        };
        portrait.src = visual.artSrc;
      }

      // 弹层时序与交互闭环（依据 Codex 审计报告 P1 整改）：
      // 1. 若处于减少动效模式 (reduce-motion)，快速可跳过
      // 2. 若在 AUTO 模式：提供 2.5 秒倒计时，按钮显示“继续 (2s)”，倒计时完毕或点击均安全推进
      // 3. 若在手动模式：等待用户主动点击“继续”或按空格/回车键，保障用户阅读与控制权
      await this.waitForFeatureDismissal(closeBtn);

      if (playCg && !this.isReduceMotion()) {
        await this.playFeatureCg(symbol);
      }
      this.hideFeature();
    },

    waitForFeatureDismissal(closeBtn) {
      return new Promise((resolve) => {
        let timer = null;
        let countdownTimer = null;
        let done = false;

        const cleanupAndResolve = () => {
          if (done) return;
          done = true;
          if (timer) clearTimeout(timer);
          if (countdownTimer) clearInterval(countdownTimer);
          if (closeBtn) {
            closeBtn.removeEventListener('click', onBtnClick);
            closeBtn.textContent = '继续';
          }
          window.removeEventListener('keydown', onKey);
          resolve();
        };

        const onBtnClick = (e) => {
          if (e) e.preventDefault();
          cleanupAndResolve();
        };

        const onKey = (e) => {
          if (e.code === 'Space' || e.key === 'Escape' || e.key === 'Enter') {
            e.preventDefault();
            cleanupAndResolve();
          }
        };

        if (closeBtn) {
          closeBtn.addEventListener('click', onBtnClick);
          try { closeBtn.focus(); } catch (e) {}
        }
        window.addEventListener('keydown', onKey);

        if (this.isReduceMotion()) {
          // 减少动效模式：500ms 快速自动允许跳过或点击即走
          timer = setTimeout(cleanupAndResolve, 500);
          return;
        }

        if (this.autoSpin) {
          // 自动旋转：2.5 秒倒计时提示
          let remaining = 2;
          if (closeBtn) closeBtn.textContent = `继续 (${remaining}s)`;
          countdownTimer = setInterval(() => {
            remaining -= 1;
            if (closeBtn) {
              closeBtn.textContent = remaining > 0 ? `继续 (${remaining}s)` : '继续';
            }
          }, 1000);
          timer = setTimeout(cleanupAndResolve, 2500);
        } else {
          // 手动模式：最多 10 秒保底防挂起，主交互由用户点击或按键控制
          timer = setTimeout(cleanupAndResolve, 10000);
        }
      });
    },

    hideFeature() {
      const layer = document.getElementById('slots-feature');
      if (layer) layer.hidden = true;
      if (APP.stages && APP.stages.slotsFeature) {
        try { APP.stages.slotsFeature.stop(); } catch (e) {}
      }
    },

    async playFeatureCg(symbol) {
      const allKnown = [
        'assets/videos/cg_ch1.mp4',
        'assets/videos/cg_ch2.mp4',
        'assets/videos/cg_ch3.mp4',
        'assets/videos/cg_ch4.mp4',
        'assets/videos/v_comics_maze_2_48.mp4',
        'assets/videos/cg_ch2.mp4',
        'assets/videos/cg_ch3.mp4',
        'assets/videos/cg_ch4.mp4',
        'assets/videos/cg_rea_general.mp4',
        'assets/videos/cg_rea_event049.mp4'
      ];
      const pool = Array.from(new Set([
        ...allKnown,
        ...((APP.data && APP.data.videos) || []).map((item) => typeof item === 'string' ? item : item.src).filter(Boolean)
      ]));

      // 建立特勤/符號與 CG 的特色關聯映射，兼顧專屬感與隨機豐富度
      const symbolPreferred = {
        kitty: ['assets/videos/cg_ch1.mp4', 'assets/videos/v_comics_maze_2_48.mp4'],
        dominica: ['assets/videos/cg_ch2.mp4', 'assets/videos/cg_ch2.mp4'],
        regina: ['assets/videos/cg_ch3.mp4', 'assets/videos/cg_ch3.mp4'],
        florence: ['assets/videos/cg_ch4.mp4', 'assets/videos/cg_ch4.mp4'],
        zoe_enemy: ['assets/videos/cg_ch3.mp4', 'assets/videos/cg_ch3.mp4'],
        hilda_enemy: ['assets/videos/cg_ch4.mp4', 'assets/videos/cg_ch4.mp4'],
        kart: ['assets/videos/cg_rea_general.mp4', 'assets/videos/cg_rea_event049.mp4'],
        ines: ['assets/videos/cg_rea_general.mp4'],
        luf: ['assets/videos/cg_rea_event049.mp4'],
        misa: ['assets/videos/cg_rea_event049.mp4', 'assets/videos/cg_rea_general.mp4'],
        wild: allKnown,
        scatter: allKnown
      };

      const symId = symbol && symbol.id;
      let candidates = (symId && symbolPreferred[symId]) ? symbolPreferred[symId] : pool;
      // 避免連續兩次播放完全相同的 CG
      let nonRepeating = candidates.filter((v) => v !== this._lastPlayedCg);
      if (nonRepeating.length === 0) nonRepeating = pool.filter((v) => v !== this._lastPlayedCg);
      if (nonRepeating.length === 0) nonRepeating = pool;

      const source = nonRepeating[Math.floor(Math.random() * nonRepeating.length)];
      this._lastPlayedCg = source;
      if (!source) return;
      const overlay = document.getElementById('cg-cinema-overlay');
      const video = document.getElementById('cg-cinema-video');
      if (!overlay || !video) return;
      overlay.hidden = false;
      video.src = source;
      video.currentTime = 0;
      const finish = () => { video.pause(); overlay.hidden = true; video.onended = null; };
      video.onended = finish;
      const skip = document.getElementById('btn-cg-skip');
      if (skip) skip.onclick = finish;
      if (this.isReduceMotion()) { finish(); return; }
      await video.play().catch(() => {});
      await new Promise((resolve) => {
        const done = () => { resolve(); };
        video.addEventListener('ended', done, { once: true });
        setTimeout(done, 4200);
      });
      finish();
    },
  };

  window.SlotsUI = SlotsUI;
})();
