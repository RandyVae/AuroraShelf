/* Screen renderers + shared UI helpers for Tactical RPG. */
(function () {
  'use strict';

  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => [...(r || document).querySelectorAll(s)];
  const el = (tag, attrs, children) => {
    const n = document.createElement(tag);
    if (attrs) for (const k in attrs) {
      if (k === 'class') n.className = attrs[k];
      else if (k === 'html') n.innerHTML = attrs[k];
      else if (k === 'text') n.textContent = attrs[k];
      else if (k.startsWith('on')) n.addEventListener(k.slice(2), attrs[k]);
      else if (attrs[k] !== null && attrs[k] !== undefined) n.setAttribute(k, attrs[k]);
    }
    (children || []).forEach((c) => c && n.appendChild(c));
    return n;
  };

  function toast(msg, ms) {
    const t = $('#toast');
    if (!t) return;
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(toast._t);
    toast._t = setTimeout(() => t.classList.remove('show'), ms || 1800);
  }

  function starsText(n) {
    return '★★★'.slice(0, Math.min(3, n || 0)) + '☆☆☆'.slice(0, Math.max(0, 3 - (n || 0)));
  }

  function fiveStarsText(n) {
    return '★★★★★'.slice(0, Math.min(5, n || 1)) + '☆☆☆☆☆'.slice(0, Math.max(0, 5 - (n || 1)));
  }

  function heroThumb(h) {
    if (!h) return '';
    const a = h.artWeb || {};
    return a.baseThumb || a.base || a.legaThumb || a.lega || '';
  }

  function heroArt(h, variant) {
    if (!h) return '';
    const a = h.artWeb || {};
    if (variant === 'nude') {
      // 18+ 觉醒：优先 nude，无 nude 时使用高露出战术装 lega，兜底 base
      return a.nude || a.lega || a.base || '';
    } else {
      // 战术特勤装：
      // 若英雄有 nude 和 lega，则 lega 为战术装；
      // 若英雄无 nude（如 kitty, kira），因 lega 已作为 18+ 觉醒装，故战术装切换为 base（或 lega 兜底）
      if (a.nude && a.lega) return a.lega;
      if (a.base) return a.base;
      return a.lega || '';
    }
  }

  const UI = {
    toast, el, $, $$, starsText, fiveStarsText, heroThumb, heroArt,

    setProgress(pct) {
      const bar = $('#boot-bar');
      if (!bar) return;
      bar.style.width = Math.round(pct * 100) + '%';
      const wrap = bar.parentElement;
      if (wrap) wrap.setAttribute('aria-valuenow', String(Math.round(pct * 100)));
    },

    renderHud(state) {
      if (!state) return;
      const gold = (state.gold || 0).toLocaleString();
      const keys = state.keys || 0;
      const energy = state.energy || 0;

      if ($('#hud-gold')) $('#hud-gold').textContent = gold;
      if ($('#hud-keys')) $('#hud-keys').textContent = keys;
      if ($('#hud-energy')) $('#hud-energy').textContent = energy;
      if ($('#camp-energy')) $('#camp-energy').textContent = energy;
      if ($('#gacha-keys')) $('#gacha-keys').textContent = keys;
      if ($('#shop-gold')) $('#shop-gold').textContent = gold;

      // Unclaimed missions badge indicator
      const dot = $('#mission-dot');
      if (dot && window.Missions && window.APP && window.APP.data) {
        const has = Missions.hasUnclaimed(state, APP.data.missions);
        dot.hidden = !has;
      }
    },

    renderHub(state, data) {
      const leadKey = (state.squad && state.squad[0]) || 'kitty';
      const lead = (data.heroes && data.heroes.find((h) => h.key === leadKey)) || data.heroes[0];
      $('#hub-bg').style.backgroundImage = 'url(assets/bg/hub.webp)';

      // Determine hero outfit: prioritize 18+ awaken (nude) or tactical armor (lega), NEVER casual clothes (base)
      const adultOn = state.settings && state.settings.adult !== false;
      const outfitPref = state.hubOutfit || (adultOn ? 'nude' : 'lega');
      const img = $('#hub-art');
      img.src = heroArt(lead, outfitPref);
      img.hidden = !img.src;
      img.alt = lead.name + ' 立绘';
      img.classList.add('hero-breathing');

      // Apply per-hero visual adaptation & scale normalization (58 hero adaptive engine)
      const HERO_HUB_ADAPTATION = {
        esther: { scale: 1.00, y: 0, x: 0 },
        justina: { scale: 1.00, y: 0, x: 0 },
        zoechloe: { scale: 1.00, y: 0, x: 0 },
        bonita: { scale: 0.98, y: 0, x: 0 },
        gwenny: { scale: 1.00, y: 0, x: 0 },
        arachne: { scale: 0.98, y: 0, x: 0 },
        asha: { scale: 0.98, y: 0, x: 0 },
        dominica: { scale: 1.00, y: 0, x: 0 },
        yumiko: { scale: 1.00, y: 0, x: 0 },
        britney: { scale: 1.00, y: 0, x: 0 },
        nicole: { scale: 1.00, y: 0, x: 0 },
        orin: { scale: 0.98, y: 0, x: 0 },
        lorraine: { scale: 1.00, y: 0, x: 0 },
        maddie: { scale: 1.00, y: 0, x: 0 },
        morana: { scale: 1.00, y: 0, x: 0 },
        blanche: { scale: 0.98, y: 0, x: 0 },
        falenia: { scale: 1.00, y: 0, x: 0 },
        lilith: { scale: 0.98, y: 0, x: 0 },
        omega: { scale: 0.98, y: 0, x: 0 },
        bea: { scale: 1.00, y: 0, x: 0 },
        charlotte: { scale: 0.98, y: 0, x: 0 },
        selina: { scale: 1.00, y: 0, x: 0 },
        lola: { scale: 0.98, y: 0, x: 0 },
        vespina: { scale: 1.00, y: 0, x: 0 },
        diya: { scale: 1.00, y: 0, x: 0 },
        hilda: { scale: 1.00, y: 0, x: 0 },
        casey: { scale: 0.98, y: 0, x: 0 },
        kiana: { scale: 1.00, y: 0, x: 0 },
        victoria: { scale: 0.98, y: 0, x: 0 },
        tsintah: { scale: 1.00, y: 0, x: 0 },
        alisha: { scale: 0.98, y: 0, x: 0 },
        vivienne: { scale: 1.00, y: 0, x: 0 },
        kitty: { scale: 1.00, y: 0, x: 0 },
        wendy: { scale: 0.98, y: 0, x: 0 },
        paloma: { scale: 0.98, y: 0, x: 0 },
        kirsten: { scale: 1.00, y: 0, x: 0 },
        shelby: { scale: 1.00, y: 0, x: 0 },
        irma: { scale: 1.00, y: 0, x: 0 },
        grace: { scale: 1.00, y: 0, x: 0 },
        nonna: { scale: 1.00, y: 0, x: 0 },
        thora: { scale: 1.00, y: 0, x: 0 },
        lucretia: { scale: 1.00, y: 0, x: 0 },
        sixtine: { scale: 0.98, y: 0, x: 0 },
        zelia: { scale: 0.98, y: 0, x: 0 },
        regina: { scale: 1.00, y: 0, x: 0 },
        pixie: { scale: 1.00, y: 0, x: 0 },
        rose: { scale: 1.00, y: 0, x: 0 },
        mitsuki: { scale: 0.98, y: 0, x: 0 },
        ravenna: { scale: 0.98, y: 0, x: 0 },
        kira: { scale: 1.00, y: 0, x: 0 },
        harper: { scale: 0.98, y: 0, x: 0 },
        naomi: { scale: 0.98, y: 0, x: 0 },
        belle: { scale: 0.98, y: 0, x: 0 },
        river: { scale: 1.00, y: 0, x: 0 },
        nymeria: { scale: 1.00, y: 0, x: 0 },
        astara: { scale: 0.80, y: 10, x: 0 },
        aphrodite: { scale: 0.74, y: 15, x: 0 },
        ines: { scale: 0.78, y: 15, x: 0 },
        kart: { scale: 0.78, y: 15, x: 0 },
        luf: { scale: 0.80, y: 10, x: 0 },
        misa: { scale: 0.78, y: 15, x: 0 },
        ariana: { scale: 0.80, y: 10, x: 0 },
      };
      const adapt = HERO_HUB_ADAPTATION[leadKey] || { scale: 1.0, y: 0, x: 0 };
      img.style.setProperty('--hero-scale', String(adapt.scale || 1.0));
      img.style.setProperty('--hero-offset-y', (adapt.y || 0) + 'px');
      img.style.setProperty('--hero-offset-x', (adapt.x || 0) + 'px');

      // Update outfit toggle button
      const outfitBtn = $('#btn-hub-outfit');
      const outfitLabel = $('#hub-outfit-label');
      if (outfitBtn && outfitLabel) {
        const isNude = outfitPref === 'nude';
        outfitLabel.textContent = isNude ? '18+觉醒' : '战术装';
        outfitBtn.className = 'hub-outfit-btn ' + (isNude ? 'outfit-nude' : 'outfit-tactical');
        outfitBtn.onclick = () => {
          state.hubOutfit = isNude ? 'lega' : 'nude';
          if (window.LGSave) LGSave.persist(state);
          this.renderHub(state, data);
          toast('立绘装扮已切换为：' + (state.hubOutfit === 'nude' ? '18+ 觉醒装扮' : '战术特勤装'));
        };
      }

      // Hero Switcher: cycle through all owned heroes (Point 2)
      const ownedList = (state.owned && state.owned.length)
        ? state.owned
        : (data.heroes || []).map((h) => h.key);
      const currentIdx = Math.max(0, ownedList.indexOf(leadKey));

      const switchHero = (offset) => {
        const nextIdx = (currentIdx + offset + ownedList.length) % ownedList.length;
        const nextKey = ownedList[nextIdx];
        if (!state.squad || !state.squad.length) state.squad = [nextKey];
        else state.squad[0] = nextKey;
        if (window.LGSave) LGSave.persist(state);
        this.renderHub(state, data);
        const nextHeroObj = (data.heroes || []).find((h) => h.key === nextKey);
        toast('看板特勤已切换：' + (nextHeroObj ? nextHeroObj.name : nextKey) + ' (' + (nextIdx + 1) + '/' + ownedList.length + ')');
      };

      // 手势横划切换特勤看板 (Swipe Gestures)
      const hubScreen = $('#screen-hub');
      if (hubScreen && !hubScreen._swipeBound) {
        hubScreen._swipeBound = true;
        let startX = 0;
        let startY = 0;
        hubScreen.addEventListener('touchstart', (e) => {
          if (!e.touches || !e.touches[0]) return;
          startX = e.touches[0].clientX;
          startY = e.touches[0].clientY;
        }, { passive: true });
        hubScreen.addEventListener('touchend', (e) => {
          if (!e.changedTouches || !e.changedTouches[0]) return;
          const dx = e.changedTouches[0].clientX - startX;
          const dy = e.changedTouches[0].clientY - startY;
          if (Math.abs(dx) > 42 && Math.abs(dx) > Math.abs(dy) * 1.4) {
            if (dx > 0) switchHero(-1);
            else switchHero(1);
          }
        }, { passive: true });
      }

      const btnPrev = $('#btn-hub-hero-prev');
      const btnNext = $('#btn-hub-hero-next');
      const btnSwitch = $('#btn-hub-hero-switch');
      const heroNameEl = $('#hub-hero-name');
      const heroCountEl = $('#hub-hero-count');

      if (btnPrev) btnPrev.onclick = () => switchHero(-1);
      if (btnNext) btnNext.onclick = () => switchHero(1);
      if (btnSwitch) btnSwitch.onclick = () => switchHero(1);
      if (heroNameEl) heroNameEl.textContent = lead.name || leadKey;
      if (heroCountEl) heroCountEl.textContent = (currentIdx + 1) + '/' + ownedList.length;

      $('#hub-name').textContent = 'Ethan';

      const cleared = Object.keys(state.cleared || {}).length;
      $('#hub-meta').textContent = '指挥官 · 通关 ' + cleared + ' 关';
      $('#nav-campaign-sub').textContent = data.chapters.length + ' 章 · ' + data.chapters.reduce((s, c) => s + c.stages.length, 0) + ' 关卡';
      $('#nav-squad-sub').textContent = (state.squad || []).length + ' / 3';
      $('#nav-roster-sub').textContent = '已收集 ' + (state.owned || []).length + ' / ' + data.heroes.length;

      const lines = [
        ['欢迎回家，指挥官。', '今天该推进哪条战线？'],
        ['新的契约已就绪。', '招募中心有新的呼唤。'],
        ['全队战力已就位。', '请下达作战部署指令。'],
        ['军需库物资充沛。', '适时为特勤队员进行强化。'],
      ];
      const pick = lines[(cleared + (state.owned || []).length) % lines.length];
      $('#hub-lead').innerHTML = '<b>' + pick[0] + '</b>' + pick[1];

      const av = $('#hub-avatar');
      if (av) av.style.backgroundImage = 'url(' + heroThumb(lead) + ')';

      // 绑定大厅英雄全动态点击互动舞台 (Touch Reaction & Spine Animation)
      const stageEl = $('#hub-hero-stage');
      if (stageEl && !stageEl._tapBound) {
        stageEl._tapBound = true;
        stageEl.addEventListener('click', (e) => {
          if (e.target.closest('.hub-hero-nav')) return; // 避开左右切换箭头
          this.onHubHeroTap(this._currentLeadHero);
        });
      }
      this._currentLeadHero = lead;

      // 激活大厅特勤超高清 18+ 原画立绘与物理有机呼吸律动（停用战斗 Spine 小人，呈现高画质 18+ 艺术）
      if (APP.stages && APP.stages.hub) {
        try { APP.stages.hub.stop(); } catch (e) {}
      }
      const canvas = $('#hub-canvas');
      if (canvas) canvas.hidden = true;
      const artImg = $('#hub-art');
      if (artImg) {
        artImg.hidden = false;
        const outfitPref = state.hubOutfit || (state.settings && state.settings.adult ? 'nude' : 'lega');
        const artSrc = heroArt(lead, outfitPref) || heroArt(lead, 'nude') || heroArt(lead, 'base') || heroThumb(lead);
        artImg.src = artSrc;
        artImg.alt = (lead.name || '') + ' 18+ 原画立绘';
        artImg.className = 'hub-art hero-breathing';
      }
      this.showHubSpeech('「指挥官，' + (lead.name || '') + ' 专属随侍在侧，请下达作战指令～」');

      this.renderHud(state);
    },

    onHubHeroTap(lead) {
      if (!lead) return;
      const artImg = $('#hub-art');
      if (artImg) {
        artImg.classList.remove('hero-tapped');
        void artImg.offsetWidth;
        artImg.classList.add('hero-tapped');
      }
      if (window.NativeBridge && window.NativeBridge.haptic) {
        NativeBridge.haptic('selection');
      }
      if (window.LGAudio && LGAudio.playSe) {
        try { LGAudio.playSe('tap'); } catch (e) {}
      }
      const HERO_INTERACTION_VOICES = {
        kart: [
          '「指挥官，我的突击步枪已充能完毕，随时可以出击！」',
          '「呀……别突然戳我腰带啦，战术挂钩会松开的！」',
          '「今天的心率传感器有点异常……是指挥官靠得太近了吗？」',
        ],
        misa: [
          '「八百米内目标全数锁定，指挥官只需下达开火指令。」',
          '「狙击手需要绝对的专注……但你的视线，更让我心跳加速。」',
          '「轻点碰……这身战术紧身衣的灵敏传感器很敏感的。」',
        ],
        ines: [
          '「神圣护盾已展开，没有任何人能伤害指挥官分毫。」',
          '「被指挥官触碰时，护盾似乎产生了奇异的共鸣涟漪呢。」',
          '「无论战场还是生活，我都会一直守护在你的身侧。」',
        ],
        luf: [
          '「神圣光辉庇佑着我们！指挥官今天也元气满满呢！」',
          '「嘿嘿，被指挥官摸头好舒服～要不要再摸一下？」',
          '「无论前方是什么敌人，只要两个人在一起就绝对不会输！」',
        ],
        astara: [
          '「黑暗契约的力量在体内涌动……想要见识真正的深渊吗？」',
          '「指挥官的手，总是带着让人无法抗拒的温度呢。」',
          '「再碰一下……我可不敢保证接下来会发生什么哦？」',
        ],
        aphrodite: [
          '「爱意如同潮水～指挥官的目光就是我最大的力量源泉。」',
          '「靠近一点嘛……我的心跳声，你能听得清吗？」',
          '「今天也要沉醉在我的魅力之中哦，亲爱的长官～」',
        ],
        kitty: [
          '「喵～本喵的利爪已经磨好啦，指挥官要看杂技吗？」',
          '「耳朵是敏感部位！不准随便捏啦……喵呜～」',
          '「小鱼干……不对，胜利的战利品准备好了吗？」',
        ],
        charlotte: [
          '「指挥官今天的手法还是这么温柔呢～」',
          '「需要我换上那套更私密的特别装扮吗？只要你一句话。」',
          '「胜负就在一瞬之间，但我整个人都是属于你的。」',
        ],
      };
      const heroKey = lead.key || lead.id || '';
      const pool = HERO_INTERACTION_VOICES[heroKey] || [
        '「特勤整备完毕，战况实时同步中，请指示！」',
        '「指挥官，无论面对任何强敌，我们都绝不后退半步！」',
        '「感受到了指挥官的信赖，战斗效能已提升至 120%！」',
        '「呀……轻点碰，战术服的传感器可是很敏锐的哦！」',
      ];
      const speech = pool[Math.floor(Math.random() * pool.length)];
      this.showHubSpeech(speech);
    },

    showHubSpeech(text) {
      const bubble = $('#hub-speech-bubble');
      const textEl = $('#hub-speech-text');
      if (!bubble || !textEl) return;
      textEl.textContent = text;
      bubble.hidden = false;
      bubble.classList.remove('pop-anim');
      void bubble.offsetWidth;
      bubble.classList.add('pop-anim');
      clearTimeout(this._hubSpeechTimer);
      this._hubSpeechTimer = setTimeout(() => {
        bubble.classList.remove('pop-anim');
        bubble.hidden = true;
      }, 4000);
    },

    renderCampaign(state, data) {
      const list = $('#chapter-list');
      list.innerHTML = '';
      $('#chapter-detail').hidden = true;
      list.hidden = false;

      data.chapters.forEach((ch) => {
        const unlocked = this.chapterUnlocked(state, data, ch.id);
        const lastId = ch.id + '-' + ch.stages.length;
        const cleared = !!(state.cleared && state.cleared[lastId]);
        const stars = ch.stages.reduce((s, st) => s + ((state.stars && state.stars[st.id]) || 0), 0);

        const card = el('button', {
          class: 'chapter-card' + (unlocked ? '' : ' locked'),
          'aria-label': ch.title + (unlocked ? '' : '（未解锁）'),
          onclick: () => {
            if (!unlocked) { toast('请先通关前一章全部关卡'); return; }
            this.openChapter(state, data, ch.id);
          },
        }, [
          el('div', { class: 'chapter-bg', style: 'background-image:url(' + (ch.bgWeb || '') + ')' }),
          el('div', { class: 'ch-meta' }, [
            el('b', { text: ch.title }),
            el('span', { text: ch.subtitle }),
          ]),
          el('span', { class: 'ch-stars', text: cleared ? '已通关' : stars ? '★ ' + stars : '' }),
        ]);
        list.appendChild(card);
      });
      this.renderHud(state);
    },

    chapterUnlocked(state, data, chapterId) {
      if (chapterId === 1) return true;
      const prev = data.chapters.find((c) => c.id === chapterId - 1);
      if (!prev) return true;
      const last = prev.stages[prev.stages.length - 1];
      return !!(state.cleared && state.cleared[last.id]);
    },

    openChapter(state, data, chapterId) {
      const ch = data.chapters.find((c) => c.id === chapterId);
      $('#chapter-list').hidden = true;
      $('#chapter-detail').hidden = false;
      $('#chapter-title').textContent = ch.title;
      const grid = $('#stage-grid');
      grid.innerHTML = '';

      ch.stages.forEach((st, i) => {
        const prevCleared = i === 0 || !!(state.cleared && state.cleared[ch.stages[i - 1].id]);
        const stars = (state.stars && state.stars[st.id]) || 0;
        const btn = el('button', {
          class: 'stage-btn' + (stars ? ' cleared' : '') + (prevCleared ? '' : ' locked'),
          'aria-label': st.title + '，' + (stars ? stars + ' 星' : '未通关'),
          onclick: () => {
            if (!prevCleared) { toast('请先通关前置关卡'); return; }
            this.openStageAction(ch, st);
          },
        }, [
          el('b', { text: '第 ' + st.stage + ' 关' }),
          el('span', { class: 'stars', text: starsText(stars) }),
          el('span', { class: 'stars', text: 'Lv.' + st.level, style: 'color:var(--text-tertiary)' }),
        ]);
        grid.appendChild(btn);
      });
    },

    openStageAction(ch, st) {
      const state = APP.state;
      const stars = (state.stars && state.stars[st.id]) || 0;
      $('#stage-action-title').textContent = `${ch.title} · 第 ${st.stage} 关`;
      $('#stage-action-info').innerHTML = `
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
          <span>推荐战力等级：<b>Lv.${st.level}</b></span>
          <span style="color:var(--accent)">${starsText(stars)}</span>
        </div>
        <div>通关奖励：🪙 +${st.rewardGold} 金币 ${st.rewardShards ? ' · 💎 +' + st.rewardShards + ' 碎片' : ''}</div>
      `;

      $('#btn-stage-launch').onclick = () => {
        $('#stage-action-sheet').classList.remove('open');
        APP.startStage(ch, st);
      };

      const sweepGroup = $('#stage-sweep-group');
      if (stars >= 3) {
        sweepGroup.style.display = 'flex';
        $('#btn-stage-sweep1').onclick = () => APP.sweepStage(ch, st, 1);
        $('#btn-stage-sweep5').onclick = () => APP.sweepStage(ch, st, 5);
      } else {
        sweepGroup.style.display = 'none';
      }

      $('#btn-stage-action-close').onclick = () => $('#stage-action-sheet').classList.remove('open');
      $('#stage-action-sheet').classList.add('open');
    },

    renderSquad(state, data) {
      const slots = $('#squad-slots');
      slots.innerHTML = '';
      for (let i = 0; i < 3; i++) {
        const key = state.squad[i];
        const h = data.heroes.find((x) => x.key === key);
        slots.appendChild(el('div', { class: 'slot' }, h ? [el('img', { src: heroThumb(h), alt: h.name, title: h.name })] : [el('span', { text: '空位' })]));
      }

      const grid = $('#squad-grid');
      grid.innerHTML = '';
      data.heroes.forEach((h) => {
        const owned = state.owned.includes(h.key);
        const picked = state.squad.includes(h.key);
        const inst = window.Progression ? Progression.getHeroInstance(state, h.key) : null;
        const stats = inst && window.Progression ? Progression.computeHeroCombatStats(h, inst, data.equipment) : null;

        const card = el('button', {
          class: 'hero-card' + (picked ? ' picked' : '') + (owned ? '' : ' locked'),
          'aria-pressed': picked ? 'true' : 'false',
          'aria-label': h.name + ' ' + h.rarityCn + (owned ? '' : ' 未获得'),
          onclick: () => APP.toggleSquad(h.key),
        }, [
          el('img', { src: heroThumb(h), alt: '', loading: 'lazy' }),
          el('div', { class: 'hc-foot' }, [
            el('span', { class: 'hc-name', text: h.name + (inst ? ' Lv.' + inst.level : '') }),
            el('span', { class: 'hc-rar', text: h.rarityCn, style: 'color:var(--rarity-' + (h.rarity === 'legendary' ? 'ssr' : h.rarity === 'epic' ? 'sr' : 'r') + ')' }),
          ]),
          el('span', { class: 'pick-mark', text: '✓' }),
        ]);
        grid.appendChild(card);
      });

      const totalPower = state.squad.reduce((sum, key) => {
        const h = data.heroes.find((x) => x.key === key);
        if (!h) return sum;
        const inst = window.Progression ? Progression.getHeroInstance(state, key) : null;
        const stats = inst && window.Progression ? Progression.computeHeroCombatStats(h, inst, data.equipment, { leader: state.squad[0] === key }) : null;
        return sum + (stats ? stats.power : (h.power || 0));
      }, 0);

      $('#squad-power').textContent = totalPower.toLocaleString();

      // Faction Synergies indicator
      const synBox = $('#squad-faction-synergies');
      if (synBox) {
        synBox.innerHTML = '';
        const squadHeroes = state.squad.map((k) => data.heroes.find((x) => x.key === k)).filter(Boolean);
        const synergies = window.Progression && window.Progression.computeFactionSynergy ? Progression.computeFactionSynergy(squadHeroes) : [];
        if (synergies.length) {
          synergies.forEach((syn) => {
            const badge = el('div', {
              class: 'faction-syn-badge',
              style: 'display:inline-flex;align-items:center;gap:6px;padding:4px 10px;margin-right:6px;margin-bottom:4px;border-radius:6px;font-size:var(--type-caption);background:rgba(56,231,255,0.14);border:1px solid rgba(56,231,255,0.4);color:#38e7ff;'
            }, [
              el('b', { text: '✦ ' + syn.name + ' (' + syn.count + '人)：' }),
              el('span', { text: syn.desc })
            ]);
            synBox.appendChild(badge);
          });
        }
      }
    },

    renderRoster(state, data) {
      const grid = $('#roster-grid');
      grid.innerHTML = '';
      $('#roster-count').textContent = '已收集 ' + state.owned.length + ' / ' + data.heroes.length;

      data.heroes.forEach((h) => {
        const owned = state.owned.includes(h.key);
        const inst = owned && window.Progression ? Progression.getHeroInstance(state, h.key) : null;

        const btn = el('button', {
          class: 'hero-card' + (owned ? '' : ' locked'),
          'aria-label': h.name + (owned ? '' : ' 未获得'),
          onclick: () => this.openDetail(h),
        }, [
          el('img', { src: heroThumb(h), alt: '', loading: 'lazy' }),
          el('div', { class: 'hc-foot' }, [
            el('span', { class: 'hc-name', text: h.name + (inst ? ' Lv.' + inst.level : '') }),
            el('span', { class: 'hc-rar', text: h.rarityCn, style: 'color:var(--rarity-' + (h.rarity === 'legendary' ? 'ssr' : h.rarity === 'epic' ? 'sr' : 'r') + ')' }),
          ]),
        ]);
        grid.appendChild(btn);
      });
    },

    openDetail(h) {
      const state = APP.state;
      const owned = state.owned.includes(h.key);
      const inst = window.Progression ? Progression.getHeroInstance(state, h.key) : { level: 1, stars: 1, skillLevel: 1, equips: {} };
      const stats = window.Progression ? Progression.computeHeroCombatStats(h, inst, APP.data.equipment, { leader: state.squad[0] === h.key }) : { atk: h.atk, maxHp: h.hp, crit: 0.1, power: h.power, level: 1, stars: 1, skillLevel: 1 };

      $('#detail-name').textContent = h.name;
      const tag = $('#detail-rarity');
      tag.textContent = h.rarityCn;
      tag.className = 'tag ' + (h.rarity === 'legendary' ? 'ssr' : h.rarity === 'epic' ? 'sr' : 'r');

      // Progression header
      $('#detail-lvl').textContent = 'Lv.' + stats.level + ' / 30';
      $('#detail-stars').textContent = fiveStarsText(stats.stars);
      $('#detail-power').textContent = stats.power.toLocaleString();

      const upCost = Progression.levelCost(stats.level);
      const btnUp = $('#btn-hero-upgrade');
      if (!upCost) {
        btnUp.textContent = '已达满级 (Lv.30)';
        btnUp.disabled = true;
      } else {
        btnUp.textContent = `强化升级 (${upCost.gold.toLocaleString()} 金币)`;
        btnUp.disabled = !owned || state.gold < upCost.gold;
      }
      btnUp.onclick = () => {
        if (!owned) { toast('尚未获得该特勤女神'); return; }
        const res = Progression.levelUp(state, h.key);
        if (res.ok) {
          toast(`${h.name} 升级至 Lv.${res.level}！`);
          LGAudio.playSfx('win');
          LGSave.persist(state);
          this.openDetail(h);
          this.renderHud(state);
          if (window.Missions) Missions.track(state, APP.data.missions, 'upgrade', 1);
        } else {
          toast(res.msg);
        }
      };

      const breakCost = Progression.starBreakthroughCost(stats.stars);
      const btnBreak = $('#btn-hero-break');
      if (!breakCost) {
        btnBreak.textContent = '已达满星 (5★)';
        btnBreak.disabled = true;
      } else {
        btnBreak.textContent = `突破 (${state.shards}/${breakCost.shards} 碎片)`;
        btnBreak.disabled = !owned || state.shards < breakCost.shards || state.gold < breakCost.gold;
      }
      btnBreak.onclick = () => {
        if (!owned) { toast('尚未获得该特勤女神'); return; }
        const res = Progression.starBreakthrough(state, h.key);
        if (res.ok) {
          toast(`${h.name} 突破成功，晋升为 ${fiveStarsText(res.stars)}！`);
          LGAudio.playSfx('win');
          LGSave.persist(state);
          this.openDetail(h);
          this.renderHud(state);
          if (window.Missions) Missions.track(state, APP.data.missions, 'star_break', 1);
        } else {
          toast(res.msg);
        }
      };

      // Equipment slots
      const equipBox = $('#detail-equip-slots');
      equipBox.innerHTML = '';
      const slots = [
        { key: 'weapon', label: '武器' },
        { key: 'armor', label: '防具' },
        { key: 'core', label: '核心' },
      ];
      slots.forEach((s) => {
        const eqId = inst.equips && inst.equips[s.key];
        const item = eqId && (APP.data.equipment || []).find((x) => x.id === eqId);
        const slotEl = el('div', {
          class: 'equip-slot' + (item ? ' filled' : ''),
          role: 'button',
          tabindex: '0',
          'aria-label': `${s.label}：${item ? item.name : '未装备'}`,
          onclick: () => this.openEquipPicker(h, s.key),
          onkeydown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); this.openEquipPicker(h, s.key); } },
        }, [
          el('span', { class: 'eq-tag', text: s.label }),
          el('span', { class: 'eq-name', text: item ? item.name : '+ 穿戴装备' }),
          el('span', { class: 'eq-stat', text: item ? (item.atk ? `ATK +${item.atk}` : item.hp ? `HP +${item.hp}` : `暴击 +${Math.round((item.crit||0)*100)}%`) : '空槽位' }),
        ]);
        equipBox.appendChild(slotEl);
      });

      // Active set bonuses indicator
      const existingSets = $('#detail-set-bonuses');
      if (existingSets) existingSets.remove();
      if (stats.activeSets && stats.activeSets.length > 0) {
        const setDiv = el('div', {
          id: 'detail-set-bonuses',
          class: 'set-bonuses-badge',
          style: 'margin:8px 0 12px;padding:8px 12px;background:rgba(56,231,255,.12);border:1px solid rgba(56,231,255,.35);border-radius:8px;font-size:var(--type-caption);color:#38e7ff;'
        }, [
          el('b', { text: '✦ 套装共鸣：' }),
          el('span', { text: stats.activeSets.join(' · ') })
        ]);
        equipBox.parentNode.insertBefore(setDiv, equipBox.nextSibling);
      }

      // Art & spine variants
      const variants = [];
      if (h.artWeb.base) variants.push(['base', '常服']);
      if (h.artWeb.lega) variants.push(['lega', '战术装']);
      if (h.artWeb.nude && state.settings.adult) variants.push(['nude', '18+']);

      const img = $('#detail-art');
      const tabs = $('#detail-tabs');
      tabs.innerHTML = '';
      const canvas = document.getElementById('detail-canvas');
      const stage = APP.stages && APP.stages.detail;

      if (canvas && !canvas._tapBound && stage) {
        canvas._tapBound = true;
        canvas.style.cursor = 'pointer';
        canvas.addEventListener('click', () => {
          const anims = ['rangehit', 'attack', 'hit', 'damage'];
          const a = anims[Math.floor(Math.random() * anims.length)];
          stage.playAction(a);
          if (window.NativeBridge && window.NativeBridge.haptic) NativeBridge.haptic('selection');
          if (window.LGAudio && LGAudio.playSe) { try { LGAudio.playSe('tap'); } catch (e) {} }
        });
      }

      const showVariant = (v) => {
        img.src = heroArt(h, v);
        img.alt = h.name + ' 立绘';
        const animate = !!(stage && h.unit);
        img.style.display = animate ? 'none' : '';
        if (canvas) canvas.hidden = !animate;
        if (animate) {
          stage.show(h.unit, { anim: 'idle' }).then((ok) => {
            if (!ok) { img.style.display = ''; if (canvas) canvas.hidden = true; }
          });
        } else if (stage) {
          stage.stop();
        }
      };

      variants.forEach(([v, label], i) => {
        const b = el('button', {
          class: 'btn sm' + (i === 0 ? ' primary' : ''),
          text: label,
          onclick: (e) => {
            tabs.querySelectorAll('.btn').forEach((x) => x.classList.remove('primary'));
            e.currentTarget.classList.add('primary');
            showVariant(v);
          },
        });
        tabs.appendChild(b);
      });
      showVariant(variants.length ? variants[0][0] : 'base');
      if (!variants.length) img.src = heroThumb(h);

      // Stats
      const statBox = $('#detail-stats');
      statBox.innerHTML = '';
      [
        ['攻击力', stats.atk],
        ['最大生命', stats.maxHp],
        ['暴击率', Math.round(stats.crit * 100) + '%'],
        ['免伤抗性', Math.round(stats.mitigation * 100) + '%'],
      ].forEach(([k, v]) => {
        statBox.appendChild(el('div', { class: 'stat' }, [
          el('b', { text: String(v) }),
          el('span', { text: k }),
        ]));
      });

      // Skills
      $('#detail-skill-lvl').textContent = `Lv.${stats.skillLevel} / 10`;
      const skBox = $('#detail-skills');
      skBox.innerHTML = '';
      (h.skills || []).forEach((s) => {
        skBox.appendChild(el('div', { class: 'skill-row' }, [
          el('span', { class: 'sk-ico', text: '✦' }),
          el('div', {}, [
            el('b', { text: s.name }),
            el('p', { text: s.desc }),
          ]),
        ]));
      });

      const skillCost = Progression.skillUpgradeCost(stats.skillLevel);
      const btnSkill = $('#btn-hero-skillup');
      if (!skillCost) {
        btnSkill.textContent = '技能已达最高等级 Lv.10';
        btnSkill.disabled = true;
      } else {
        btnSkill.textContent = `提升技能等级 (${skillCost.gold} 金币 + ${skillCost.shards} 碎片)`;
        btnSkill.disabled = !owned || state.gold < skillCost.gold || state.shards < skillCost.shards;
      }
      btnSkill.onclick = () => {
        if (!owned) { toast('尚未获得该特勤女神'); return; }
        const res = Progression.upgradeSkill(state, h.key);
        if (res.ok) {
          toast(`${h.name} 战技强化至 Lv.${res.skillLevel}！`);
          LGAudio.playSfx('win');
          LGSave.persist(state);
          this.openDetail(h);
          this.renderHud(state);
        } else {
          toast(res.msg);
        }
      };

      const pick = $('#btn-detail-pick');
      pick.hidden = !owned;
      pick.onclick = () => { APP.toggleSquad(h.key); this.openDetail(h); };

      const voiceBtn = $('#btn-detail-voice');
      voiceBtn.disabled = !(h.voice && h.voice.length);
      voiceBtn.onclick = () => {
        if (h.voice && h.voice.length) {
          const clip = h.voice[Math.floor(Math.random() * h.voice.length)];
          LGAudio.playVoice('assets/audio/' + clip);
        } else {
          toast('该单位无随包语音');
        }
      };

      $('#detail-sheet').classList.add('open');
    },

    openEquipPicker(hero, slot) {
      const state = APP.state;
      const catalog = APP.data.equipment || [];
      const inst = Progression.getHeroInstance(state, hero.key);
      const curEquip = inst.equips && inst.equips[slot];

      $('#equip-picker-title').textContent = `为 ${hero.name} 选择${slot === 'weapon' ? '武器' : slot === 'armor' ? '防具' : '核心'}`;
      const list = $('#equip-picker-list');
      list.innerHTML = '';

      // Option to unequip
      if (curEquip) {
        const unequipBtn = el('div', { class: 'equip-item-row' }, [
          el('div', { class: 'ei-main' }, [
            el('b', { text: '卸下当前装备', style: 'color:var(--danger)' }),
            el('p', { text: '卸下后属性将恢复基础值' }),
          ]),
          el('button', {
            class: 'btn sm danger',
            text: '卸下',
            onclick: () => {
              Progression.unequipItem(state, hero.key, slot);
              LGSave.persist(state);
              $('#equip-picker-sheet').classList.remove('open');
              this.openDetail(hero);
              toast('已卸下装备');
            },
          }),
        ]);
        list.appendChild(unequipBtn);
      }

      // Available items in inventory
      const slotItems = catalog.filter((item) => item.slot === slot);
      slotItems.forEach((item) => {
        const isCurrent = curEquip === item.id;
        const wearer = Object.keys(state.roster || {}).find((k) => state.roster[k].equips && state.roster[k].equips[slot] === item.id);
        const wearerHero = wearer ? APP.data.heroes.find((x) => x.key === wearer) : null;

        const row = el('div', { class: 'equip-item-row' }, [
          el('div', { class: 'ei-main' }, [
            el('b', { text: `${item.name} (${item.rarity})`, style: 'color:var(--text-primary)' }),
            el('p', { text: item.desc }),
            el('p', { text: item.atk ? `攻击力 +${item.atk}` : item.hp ? `生命值 +${item.hp}` : `暴击率 +${Math.round((item.crit||0)*100)}%`, style: 'color:var(--accent)' }),
          ]),
          el('button', {
            class: 'btn sm' + (isCurrent ? ' ghost' : ' primary'),
            text: isCurrent ? '当前穿戴' : wearerHero ? `由 ${wearerHero.name} 替换` : '穿戴',
            disabled: isCurrent,
            onclick: () => {
              Progression.equipItem(state, hero.key, slot, item.id);
              LGSave.persist(state);
              $('#equip-picker-sheet').classList.remove('open');
              this.openDetail(hero);
              toast(`已为 ${hero.name} 穿戴 ${item.name}`);
            },
          }),
        ]);
        list.appendChild(row);
      });

      $('#btn-equip-picker-close').onclick = () => $('#equip-picker-sheet').classList.remove('open');
      $('#equip-picker-sheet').classList.add('open');
    },

    renderMissions(state, data, activeTab) {
      activeTab = activeTab || 'daily';
      const list = $('#mission-list');
      list.innerHTML = '';

      $('#tab-mission-daily').classList.toggle('primary', activeTab === 'daily');
      $('#tab-mission-daily').classList.toggle('ghost', activeTab !== 'daily');
      $('#tab-mission-ach').classList.toggle('primary', activeTab === 'ach');
      $('#tab-mission-ach').classList.toggle('ghost', activeTab !== 'ach');

      $('#tab-mission-daily').onclick = () => this.renderMissions(state, data, 'daily');
      $('#tab-mission-ach').onclick = () => this.renderMissions(state, data, 'ach');

      const items = Missions.getList(state, data.missions, activeTab === 'daily' ? 'daily' : 'achievements');
      items.forEach((m) => {
        const pct = Math.round((m.progress / m.target) * 100);
        const rewardText = Object.entries(m.rewards || {}).map(([k, v]) => {
          const map = { gold: '金币', keys: '钥匙', energy: '行动力', shards: '碎片' };
          return `<span>+${v} ${map[k] || k}</span>`;
        }).join('');

        const row = el('div', { class: 'mission-row' }, [
          el('div', { class: 'm-main' }, [
            el('b', { text: m.title }),
            el('p', { text: m.desc }),
            el('div', { class: 'm-bar' }, [el('i', { style: 'width:' + pct + '%' })]),
            el('div', { class: 'm-rewards', html: `奖励：${rewardText}` }),
          ]),
          el('button', {
            class: 'btn sm' + (m.claimed ? ' ghost' : m.done ? ' primary' : ' ghost'),
            text: m.claimed ? '已领取' : m.done ? '领取' : `${m.progress} / ${m.target}`,
            disabled: m.claimed || !m.done,
            onclick: () => {
              const res = Missions.claimReward(state, data.missions, activeTab === 'daily' ? 'daily' : 'achievements', m.id);
              if (res.ok) {
                toast('领取成功：' + res.granted);
                LGAudio.playSfx('win');
                LGSave.persist(state);
                this.renderHud(state);
                this.renderMissions(state, data, activeTab);
              } else {
                toast(res.msg);
              }
            },
          }),
        ]);
        list.appendChild(row);
      });

      $('#btn-mission-close').onclick = () => $('#mission-sheet').classList.remove('open');
      $('#mission-sheet').classList.add('open');
    },

    closeDetail() {
      $('#detail-sheet').classList.remove('open');
      if (APP.stages && APP.stages.detail) APP.stages.detail.stop();
      const img = document.getElementById('detail-art');
      if (img) img.style.display = '';
    },

    renderShop(state, data) {
      const list = $('#shop-list');
      list.innerHTML = '';
      data.shop.forEach((item) => {
        const cost = Object.entries(item.cost).map(([k, v]) => v + ' ' + ({ gold: '金币', keys: '钥匙' }[k] || k)).join(' + ');
        list.appendChild(el('div', { class: 'shop-row' }, [
          el('div', { class: 's-ico', text: '▣' }),
          el('div', { class: 's-main' }, [el('b', { text: item.name }), el('p', { text: item.desc })]),
          el('button', { class: 'btn sm primary', text: '购买', 'aria-label': '购买' + item.name + '，花费 ' + cost, onclick: () => APP.buy(item) }),
          el('div', { class: 's-cost', text: cost }),
        ]));
      });
      this.renderHud(state);
    },

    renderCg(videos, cgs) {
      const box = $('#cg-list');
      box.innerHTML = '';

      // 1. 特勤服飾圖鑑 / 典藏 CG (優先展示豐富的美術畫廊)
      if (cgs && cgs.length > 0) {
        const secCg = el('div', { class: 'cg-section-header', style: 'margin-bottom:10px' }, [
          el('div', { style: 'font-weight:700;font-size:15px;color:var(--text-primary);letter-spacing:0.3px', text: '特勤圖鑑 · 高清立繪與典藏 CG' }),
          el('div', { style: 'font-size:12px;color:var(--text-tertiary);margin-top:2px', text: '點擊卡片可放大全螢幕檢視高清原畫' })
        ]);
        box.appendChild(secCg);

        const grid = el('div', { class: 'cg-gallery-grid', style: 'display:grid;grid-template-columns:repeat(2,1fr);gap:10px;margin-bottom:20px' });
        cgs.forEach((cg) => {
          const item = el('div', { class: 'cg-gallery-card', style: 'background:rgba(255,255,255,0.06);border-radius:var(--radius-m);overflow:hidden;cursor:pointer;border:1px solid rgba(255,255,255,0.08);transition:transform 0.2s,box-shadow 0.2s' }, [
            el('div', { style: 'position:relative;width:100%;aspect-ratio:16/9;overflow:hidden;background:#151515' }, [
              el('img', { src: cg.thumb || cg.src, alt: cg.title, loading: 'lazy', style: 'width:100%;height:100%;object-fit:cover;display:block' }),
              el('div', { style: 'position:absolute;bottom:0;inset-inline:0;height:40%;background:linear-gradient(to top, rgba(0,0,0,0.8), transparent);pointer-events:none' })
            ]),
            el('div', { style: 'padding:8px' }, [
              el('div', { style: 'font-size:12px;font-weight:600;color:var(--text-primary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap', text: cg.title }),
              el('div', { style: 'font-size:11px;color:var(--accent);margin-top:2px;font-weight:500', text: cg.char })
            ])
          ]);
          item.onclick = () => UI.showLightbox(cg);
          grid.appendChild(item);
        });
        box.appendChild(grid);
      }

      // 2. 戰役動態 CG (MP4)
      const secVideo = el('div', { class: 'cg-section-header', style: 'margin-bottom:8px' }, [
        el('div', { style: 'font-weight:700;font-size:15px;color:var(--text-primary);letter-spacing:0.3px', text: '戰役突破 · 專屬動態 CG' }),
        el('div', { style: 'font-size:12px;color:var(--text-tertiary);margin-top:2px', text: '原版劇情戰役與突破動畫重溫' })
      ]);
      box.appendChild(secVideo);

      (videos || []).forEach((src, i) => {
        const title = src.includes('event049') ? '諸神降臨 · 盛典特寫' :
                      src.includes('general') ? '神殿巡禮 · 史詩序幕' :
                      src.includes('comics_maze') ? ('番外迷宮 · 記憶切片 ' + (i - 3)) :
                      ('戰役 CG · 第 ' + (i + 1) + ' 章');
        const wrap = el('div', { style: 'margin-top:10px;margin-bottom:14px' }, [
          el('div', { style: 'font-size:var(--type-caption);color:var(--text-secondary);font-weight:600;margin-bottom:4px', text: title }),
          el('video', { src: src, controls: '', playsinline: '', preload: 'metadata', style: 'width:100%;border-radius:var(--radius-m);background:#000;border:1px solid rgba(255,255,255,0.08)' }),
        ]);
        box.appendChild(wrap);
      });

      $('#cg-sheet').classList.add('open');
    },

    showLightbox(cg) {
      let overlay = $('#cg-lightbox-overlay');
      if (!overlay) {
        overlay = el('div', { class: 'cg-lightbox-overlay', id: 'cg-lightbox-overlay', role: 'dialog', 'aria-modal': 'true', 'aria-label': '全屏 CG 鑑賞' }, [
          el('div', { class: 'cg-lightbox-wrap' }, [
            el('img', { class: 'cg-lightbox-img', id: 'cg-lightbox-img', alt: 'CG' }),
            el('div', { class: 'cg-lightbox-bar' }, [
              el('div', { id: 'cg-lightbox-info' }, [
                el('div', { id: 'cg-lightbox-title', style: 'font-size:14px;font-weight:700;color:#fff' }),
                el('div', { id: 'cg-lightbox-char', style: 'font-size:12px;color:var(--accent);margin-top:2px' })
              ]),
              el('span', { class: 'spacer' }),
              el('button', { class: 'icon-btn', id: 'btn-cg-lightbox-close', text: '✕', 'aria-label': '關閉', style: 'color:#fff;background:rgba(255,255,255,0.2);border-radius:50%;width:34px;height:34px;display:flex;align-items:center;justify-content:center;border:none;cursor:pointer' })
            ])
          ])
        ]);
        document.body.appendChild(overlay);
        const closeLightbox = () => { overlay.hidden = true; };
        $('#btn-cg-lightbox-close', overlay).onclick = closeLightbox;
        overlay.onclick = (e) => { if (e.target === overlay) closeLightbox(); };
      }
      $('#cg-lightbox-img', overlay).src = cg.src;
      $('#cg-lightbox-title', overlay).textContent = cg.title;
      $('#cg-lightbox-char', overlay).textContent = cg.char;
      overlay.hidden = false;
    },

    renderGacha(state, data) {
      const banner = data.heroes.find((h) => h.rarity === 'legendary');
      $('#gacha-banner-img').src = heroArt(banner, 'lega');
      $('#gacha-pity').textContent = state.pity + ' / ' + data.gacha.pity;
      this.renderHud(state);
    },

    showReveal(results) {
      const grid = $('#reveal-grid');
      grid.innerHTML = '';
      results.forEach((r) => {
        const card = el('div', { class: 'reveal-card' }, [
          el('img', { src: heroThumb(r.hero), alt: r.hero.name }),
          el('div', { class: 'rc-name' }, [
            el('span', { text: r.hero.name }),
            el('span', { text: r.isNew ? 'NEW' : '+' + r.shards + ' 碎片', style: 'color:' + (r.isNew ? 'var(--success)' : 'var(--text-tertiary)') }),
          ]),
        ]);
        grid.appendChild(card);
      });
      $('#gacha-reveal').classList.add('open');
      const stage = APP.stages && APP.stages.gacha;
      const first = results[0] && results[0].hero;
      if (stage && first && first.unit) stage.show(first.unit);
    },
  };

  window.UI = UI;
})();
