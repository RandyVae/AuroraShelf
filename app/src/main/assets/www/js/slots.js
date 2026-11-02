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
    let count = 1;
    for (let col = 1; col < line.length; col += 1) {
      const current = symbolMap[line[col]];
      if (!current || (current.id !== first.id && current.kind !== 'wild' && first.kind !== 'wild')) break;
      count += 1;
    }
    return count >= 2 ? { symbol: first, count } : null;
  }

  function spin(config, bet, seed) {
    const symbols = config.symbols || [];
    const symbolMap = Object.fromEntries(symbols.map((s) => [s.id, s]));
    const rnd = mulberry32(seed == null ? (Date.now() >>> 0) : seed);
    const grid = [];
    for (let row = 0; row < config.rows; row += 1) {
      grid[row] = [];
      for (let col = 0; col < config.reels; col += 1) grid[row][col] = weightedSymbol(symbols, rnd).id;
    }

    const wins = [];
    for (let lineIndex = 0; lineIndex < config.paylines.length; lineIndex += 1) {
      const line = config.paylines[lineIndex].map((row, reelIndex) => grid[row][reelIndex]);
      const result = matches(line, symbols, symbolMap);
      if (result) {
        const multiplier = result.symbol.payouts[String(result.count)] || 0;
        if (multiplier) wins.push({ line: lineIndex + 1, symbol: result.symbol.id, count: result.count, multiplier, amount: bet * multiplier });
      }
    }

    const scatterCount = grid.flat().filter((id) => id === config.scatter.id).length;
    const freeSpinsAwarded = scatterCount >= config.scatter.threshold ? config.scatter.freeSpins : 0;
    const bestWin = wins.reduce((best, win) => (!best || win.count > best.count ? win : best), null);
    return {
      seed: seed == null ? null : seed >>> 0,
      grid,
      wins,
      totalWin: wins.reduce((sum, win) => sum + win.amount, 0),
      scatterCount,
      freeSpinsAwarded,
      bestCombo: bestWin ? bestWin.count : 0,
      featureSymbol: bestWin ? bestWin.symbol : null,
    };
  }

  function resolveSymbol(symbol, data) {
    if (typeof symbol === 'string') symbol = { id: symbol };
    if (!symbol || !data) return null;
    const id = symbol.id;
    if (id === 'florence') return (data.heroes || []).find((h) => h.key === 'esther' || h.key === 'florence');
    const hero = (data.heroes || []).find((h) => h.key === id);
    if (hero) return hero;
    const enemy = (data.enemies || []).find((e) => e.id === id || e.key === id);
    if (enemy) return enemy;
    return null;
  }

  const SYMBOL_ASSETS = {
    kitty: 'assets/slots/symbol_kitty.webp',
    dominica: 'assets/slots/symbol_dominica.webp',
    regina: 'assets/slots/symbol_regina.webp',
    florence: 'assets/slots/symbol_esther.webp',
    esther: 'assets/slots/symbol_esther.webp',
    drone_enemy: 'assets/slots/symbol_drone.webp',
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
      dialogue: {
        idle: '指挥官，今天手气很旺盛呢，转一转试试看？',
        spin: '幸运能量注入中！转出 MEGA JACKPOT 吧～',
        win: '🎉 命中战术连线！好棒呀指挥官～',
        bigWin: '💖 哇！超级大奖！指挥官太威武了～',
        tap: '呀～摸到了奇怪的地方啦，好痒～'
      }
    },
    {
      id: 'skye',
      unit: 'skye_skin_nude_enemy',
      name: 'Skye 18+',
      title: '机动先锋',
      mult: '+15% 爆率',
      favor: 'Lv.5 · 亲密',
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
      dialogue: {
        idle: '保持专注，胜负就在刹那之间。',
        spin: '全军压上！碾碎那些低倍率！',
        win: '战利品清点完毕，指挥官表现优异。',
        bigWin: '惊人的战果！不愧是我的长官！',
        tap: '在控制台前请保持军纪……不过，准许你握住我的手。'
      }
    }
  ];

  const SlotsGame = { spin, resolveSymbol, SYMBOL_ASSETS, SLOTS_MASCOTS };
  window.SlotsGame = SlotsGame;

  const SlotsUI = {
    config: null,
    bet: 20,
    freeSpins: 0,
    turbo: false,
    autoSpin: false,
    autoSpinsLeft: 0,
    spinning: false,
    currentReelGrid: null,
    mascotIndex: 0,
    mascotStage: null,
    speechTimer: null,

    async init() {
      const response = await fetch('data/slots.json', { cache: 'no-store' });
      this.config = await response.json();
      this.bet = this.config.defaultBet || 20;
      this.freeSpins = (window.APP && APP.state && APP.state.slots && APP.state.slots.freeSpins) || 0;
      this.bind();
      this.render();
      this.renderPlaceholder();
      this.initMarqueeLights();
      this.initMascot();
    },

    initMascot() {
      const canvas = document.getElementById('slots-mascot-canvas');
      if (!canvas || !window.SpineStage) return;
      if (!this.mascotStage) {
        this.mascotStage = new SpineStage(canvas, { heightRatio: 0.94, yRatio: 0.04, xRatio: 0.5 });
      }
      const switchBtn = document.getElementById('btn-slots-mascot-switch');
      if (switchBtn) switchBtn.onclick = () => this.switchMascot();

      const actorWrap = document.getElementById('mascot-actor-wrap');
      if (actorWrap) actorWrap.onclick = () => this.onMascotTap();

      this.loadCurrentMascot();
    },

    loadCurrentMascot() {
      const m = SLOTS_MASCOTS[this.mascotIndex || 0];
      if (!m) return;
      const nameEl = document.getElementById('slots-mascot-name');
      const multEl = document.getElementById('mascot-lucky-mult');
      const favorEl = document.getElementById('mascot-favor-level');
      if (nameEl) nameEl.textContent = m.name;
      if (multEl) multEl.textContent = m.mult;
      if (favorEl) favorEl.textContent = m.favor;
      this.setMascotSpeech(m.dialogue.idle);

      if (this.mascotStage) {
        this.mascotStage.show(m.unit, { anim: 'idle' });
      }
    },

    setMascotSpeech(text) {
      const bubble = document.getElementById('mascot-speech');
      const textEl = document.getElementById('mascot-speech-text');
      if (!bubble || !textEl) return;
      textEl.textContent = text;
      bubble.hidden = false;
      clearTimeout(this.speechTimer);
      this.speechTimer = setTimeout(() => {
        const m = SLOTS_MASCOTS[this.mascotIndex || 0];
        if (m) textEl.textContent = m.dialogue.idle;
      }, 4200);
    },

    switchMascot() {
      this.mascotIndex = (this.mascotIndex + 1) % SLOTS_MASCOTS.length;
      SlotsAudio.coin();
      this.loadCurrentMascot();
      const m = SLOTS_MASCOTS[this.mascotIndex];
      UI.toast(`✦ 幸运伴游已切换为：${m.name}`);
    },

    onMascotSpin() {
      const m = SLOTS_MASCOTS[this.mascotIndex || 0];
      if (!m) return;
      this.setMascotSpeech(m.dialogue.spin);
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
        this.setMascotSpeech(combo >= 3 ? m.dialogue.bigWin : m.dialogue.win);
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
      this.setMascotSpeech(m.dialogue.tap);
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
        if (m) this.mascotStage.show(m.unit, { anim: 'idle' });
      }
    },

    pauseMascot() {
      if (this.mascotStage) {
        this.mascotStage.stop();
      }
    },

    bind() {
      const spinButton = document.getElementById('btn-slots-spin');
      if (spinButton) spinButton.onclick = () => this.playSpin();

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
      const lever = document.getElementById('slots-lever');
      if (lever) {
        lever.classList.add('pulling');
        SlotsAudio.lever();
        setTimeout(() => lever.classList.remove('pulling'), 320);
      }
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
      const btn = document.getElementById('btn-slots-auto');
      if (btn) {
        btn.setAttribute('aria-pressed', 'false');
        btn.classList.remove('active');
        btn.textContent = 'AUTO';
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
      if (gold) gold.textContent = (state.gold || 0).toLocaleString();
      if (bet) bet.textContent = this.bet.toLocaleString();
      if (free) free.textContent = this.freeSpins;
      if (hint) hint.textContent = this.freeSpins ? `免费旋转剩余 ${this.freeSpins} 次` : '每次旋转消耗虚拟金币，中奖自动结算';
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
      if (this.spinning || !this.config || !APP.state) return;
      const state = APP.state;
      const useFree = this.freeSpins > 0;
      if (!useFree && (state.gold || 0) < this.bet) {
        UI.toast('金币不足，请先完成战役或任务获得奖励');
        this.stopAutoSpin();
        return;
      }
      this.spinning = true;
      this.onMascotSpin();

      const button = document.getElementById('btn-slots-spin');
      if (button) button.disabled = true;

      // Deterministic PRNG calculation
      const seed = (Date.now() ^ ((state.stats && state.stats.slotsSpins) || 0)) >>> 0;
      const result = SlotsGame.spin(this.config, this.bet, seed);

      // Deduct bet / free spins
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

      // Staggered stop timing:
      const reduceMotion = state.settings && state.settings.reduceMotion;
      const stepDuration = reduceMotion ? 10 : (this.turbo ? 80 : 200);
      const baseDelay = reduceMotion ? 10 : (this.turbo ? 120 : 450);

      // Staggered column stops with physical bounce & audio
      for (let col = 0; col < this.config.reels; col++) {
        await new Promise((resolve) => setTimeout(resolve, col === 0 ? baseDelay : stepDuration));
        SlotsAudio.reelStop(col);
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

      // Record win & transactions
      const win = result.totalWin;
      state.gold += win;
      if (win > 0) LGSave.recordTransaction(state, 'gold', win, 'slots_win');
      if (result.freeSpinsAwarded) this.freeSpins += result.freeSpinsAwarded;

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
        resultText.textContent = win
          ? `赢得 ${win.toLocaleString()} 金币${result.freeSpinsAwarded ? ` · 免费旋转 +${result.freeSpinsAwarded}` : ''}`
          : (result.freeSpinsAwarded ? `Scatter · 免费旋转 +${result.freeSpinsAwarded}` : '未中奖，再试一次');
      }

      const winTier = document.getElementById('slots-win-tier');
      const tierLabel = result.bestCombo >= 4 ? 'MEGA JACKPOT' : result.bestCombo === 3 ? 'SUPER WIN' : win > 0 ? 'LINE HIT' : 'BASE GAME';
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

      // Trigger audio & coin cascade
      if (win > 0) {
        if (result.bestCombo >= 3) {
          SlotsAudio.bigWin();
        } else {
          SlotsAudio.winChime(result.bestCombo);
        }
        if (window.LGVFX) LGVFX.victoryBurst(document.getElementById('slots-grid'));
        this.dropCoins(Math.min(12, Math.max(3, Math.floor(win / 30))));
      } else {
        SlotsAudio.coin();
      }

      // Feature cut-in popup
      if (result.bestCombo >= 2) {
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
      if (!layer || !symbol) return;
      if (typeof symbol === 'string') symbol = this.config.symbols.find((item) => item.id === symbol);
      if (!symbol) return;

      const hero = SlotsGame.resolveSymbol(symbol, APP.data);
      layer.hidden = false;
      if (name) name.textContent = `${symbol.label} · ${playCg ? '三连奖励' : '二连奖励'}`;
      if (badge) badge.textContent = playCg ? 'MEGA HIT 3-COMBO' : 'LINE HIT 2-COMBO';
      if (quote) {
        quote.textContent = hero && hero.name ? `“特勤英雄【${hero.name}】已直接入库特勤名册！战备奖励已就位。”` : '“战术连线达成，高额战利品已直接入库控制台！”';
      }

      // 依用戶指示：杜絕常態/戰鬥 Q 版樣式，直接展現震撼 18+ 覺醒全高精立繪
      if (canvas) canvas.hidden = true;
      if (APP.stages && APP.stages.slotsFeature) {
        try { APP.stages.slotsFeature.stop(); } catch (e) {}
      }

      if (portrait) {
        const artSrc = (hero && hero.artWeb && (hero.artWeb.nude || hero.artWeb.lega))
          || (hero ? `assets/heroes/${hero.key}/nude.webp` : (symbol.icon || SYMBOL_ASSETS[symbol.id] || ''));
        if (artSrc) {
          portrait.src = artSrc;
          portrait.hidden = false;
        } else {
          portrait.hidden = true;
        }
      }

      if (badge) badge.textContent = hero ? '✦ 18+ AWAKEN MEGA HIT ✦' : (playCg ? 'MEGA HIT 3-COMBO' : 'LINE HIT 2-COMBO');
      if (name) name.textContent = hero ? `【${hero.name}】18+ 觉醒登场` : `${symbol.label} · 连线达成`;

      await new Promise((resolve) => setTimeout(resolve, APP.state.settings.reduceMotion ? 120 : 1100));
      if (playCg) await this.playFeatureCg();
      this.hideFeature();
    },

    hideFeature() {
      const layer = document.getElementById('slots-feature');
      if (layer) layer.hidden = true;
      if (APP.stages && APP.stages.slotsFeature) {
        try { APP.stages.slotsFeature.stop(); } catch (e) {}
      }
    },

    async playFeatureCg() {
      const videos = (APP.data.videos || []).map((item) => typeof item === 'string' ? item : item.src).filter(Boolean);
      const source = videos.find((item) => /cg_ch[1-4]\.mp4$/i.test(item)) || videos.find((item) => item.endsWith('.mp4'));
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
      if (APP.state.settings.reduceMotion) { finish(); return; }
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
