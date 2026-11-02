/* App controller: boot, routing, story, battle, gacha, progression, sweep, missions, shop, settings. */
(function () {
  'use strict';

  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => [...(r || document).querySelectorAll(s)];
  const APP = {
    state: null, data: null, screen: 'boot',
    battle: null, raf: 0, story: null,
  };
  window.APP = APP;

  const SCREENS = ['boot', 'title', 'hub', 'campaign', 'story', 'battle', 'result', 'squad', 'gacha', 'roster', 'shop', 'slots', 'settings'];

  function show(name) {
    SCREENS.forEach((s) => {
      const node = $('#screen-' + s);
      if (node) node.classList.toggle('active', s === name);
    });
    APP.screen = name;
    if (APP.stages && name !== 'hub' && APP.stages.hub) APP.stages.hub.stop();
    if (name === 'slots' && window.SlotsUI && SlotsUI.resumeMascot) {
      SlotsUI.resumeMascot();
    } else if (name !== 'slots' && window.SlotsUI && SlotsUI.pauseMascot) {
      SlotsUI.pauseMascot();
    }
    const bgm = { title: 'title', hub: 'hub', campaign: 'campaign', story: 'story', battle: 'battle', gacha: 'gacha', slots: 'gacha' }[name];
    if (bgm) LGAudio.playBgm(bgm);
  }
  APP.show = show;

  function save() { LGSave.persist(APP.state); }

  function initStages() {
    if (!window.SpineStage) return;
    if (APP.state && APP.state.settings && APP.state.settings.reduceMotion) return;
    APP.stages = {
      hub: new SpineStage($('#hub-canvas'), { heightRatio: 0.96, yRatio: 0.02, xRatio: 0.55 }),
      detail: new SpineStage($('#detail-canvas'), { heightRatio: 1.05, yRatio: 0.02, xRatio: 0.5 }),
      gacha: new SpineStage($('#gacha-canvas'), { heightRatio: 0.94, yRatio: 0.04, xRatio: 0.5 }),
    };
  }

  function regenEnergy() {
    const st = APP.state;
    const now = Date.now();
    const last = (st.stats && st.stats.energyAt) || now;
    const mins = Math.floor((now - last) / 60000);
    if (mins > 0) {
      const cap = (APP.data && APP.data.constants && APP.data.constants.maxEnergy) || 100;
      const per = Math.max(1, (APP.data && APP.data.constants && APP.data.constants.energyRestoreMin) || 5);
      const gain = Math.floor(mins / per);
      if (gain > 0 && st.energy < cap) {
        st.energy = Math.min(cap, st.energy + gain);
        if (LGSave.recordTransaction) LGSave.recordTransaction(st, 'energy', gain, 'energy_regen');
      }
      st.stats.energyAt = last + gain * per * 60000;
      if (st.energy >= cap) st.stats.energyAt = now;
      save();
    }
  }

  function toast(msg) { UI.toast(msg); }

  function confirmDialog(title, body, okLabel) {
    return new Promise((resolve) => {
      $('#modal-title').textContent = title;
      $('#modal-body').textContent = body;
      const ok = $('#modal-ok');
      const cancel = $('#modal-cancel');
      ok.textContent = okLabel || '确认';
      const close = (v) => { $('#modal').classList.remove('open'); ok.onclick = null; cancel.onclick = null; resolve(v); };
      ok.onclick = () => close(true);
      cancel.onclick = () => close(false);
      $('#modal').classList.add('open');
    });
  }

  async function loadData() {
    const files = ['heroes', 'enemies', 'chapters', 'gacha', 'shop', 'constants', 'strings', 'videos', 'battle', 'skills', 'equipment', 'missions', 'slots'];
    const out = {};
    for (let i = 0; i < files.length; i++) {
      const r = await fetch('data/' + files[i] + '.json', { cache: 'no-store' });
      out[files[i]] = await r.json();
      UI.setProgress((i + 1) / (files.length + 1));
    }
    try {
      const origRes = await fetch('data/original-assets-manifest.json', { cache: 'no-store' });
      if (origRes.ok) {
        window.ORIGINAL_ASSETS_MANIFEST = await origRes.json();
        out['original-assets-manifest'] = window.ORIGINAL_ASSETS_MANIFEST;
        // 雙軌動態熱重定向：若特勤在清單中註冊了原創立繪，自動優先掛載原創美術
        const units = window.ORIGINAL_ASSETS_MANIFEST.units || {};
        for (const [uKey, uData] of Object.entries(units)) {
          if (uData.artWeb && uData.key) {
            const matchedHero = out.heroes.find((h) => h.key === uData.key);
            if (matchedHero) {
              matchedHero.artWebOriginal = uData.artWeb;
            }
          }
        }
      }
    } catch (_) {
      window.ORIGINAL_ASSETS_MANIFEST = null;
    }
    out.heroByKey = new Map(out.heroes.map((h) => [h.key, h]));
    out.strings = out.strings || {};
    return out;
  }

  function heroFor(key) { return APP.data.heroByKey.get(key); }

  // ---------------- story & prologue ----------------
  APP.playStory = function (lines, bg, onDone, chapterId) {
    const seqMap = { 1: 'ch1_prologue', 4: 'ch4_desert', 8: 'ch8_finale' };
    const seqKey = (!chapterId) ? 'ch1_prologue' : seqMap[chapterId];
    if (window.IntroTimeline && seqKey) {
      show('story');
      window.IntroTimeline.play(seqKey, {
        auto: true,
        reduceMotion: APP.state.settings && APP.state.settings.reduceMotion,
        onComplete: onDone
      });
      return;
    }
    if (!lines || !lines.length) { onDone(); return; }
    APP.story = { lines, i: 0, timer: 0, auto: false, done: onDone, typing: null };
    $('#story-bg').style.backgroundImage = bg ? 'url(' + bg + ')' : 'none';
    show('story');
    LGAudio.playBgm('story');
    renderLine();
  };

  function speakerHero(who) {
    return APP.data.heroes.find((h) => h.name.toLowerCase() === String(who).toLowerCase());
  }

  function renderLine() {
    const s = APP.story;
    const line = s.lines[s.i];
    $('#story-name').textContent = line.who;
    $('#story-progress').textContent = (s.i + 1) + ' / ' + s.lines.length;
    const art = $('#story-art');
    const h = speakerHero(line.who);
    if (h && h.artWeb && (h.artWeb.lega || h.artWeb.base)) {
      art.src = UI.heroArt(h, 'lega');
      art.hidden = false;
      art.alt = h.name;
      art.classList.toggle('left', s.i % 2 === 1);
    } else {
      art.hidden = true;
      art.removeAttribute('src');
    }
    const text = $('#story-text');
    text.textContent = '';
    clearTimeout(s.typing);
    let i = 0;
    const full = line.text;
    const step = () => {
      text.textContent = full.slice(0, i);
      i++;
      if (i <= full.length) s.typing = setTimeout(step, 26);
    };
    step();
  }

  function advanceStory() {
    const s = APP.story;
    if (!s) return;
    const line = s.lines[s.i];
    const text = $('#story-text');
    if (text.textContent.length < line.text.length) {
      clearTimeout(s.typing);
      text.textContent = line.text;
      return;
    }
    s.i++;
    if (s.i >= s.lines.length) { finishStory(); return; }
    renderLine();
    if (s.auto) scheduleAuto();
  }

  function scheduleAuto() {
    const s = APP.story;
    clearTimeout(s.timer);
    s.timer = setTimeout(advanceStory, 2600);
  }

  function finishStory() {
    const s = APP.story;
    if (!s) return;
    clearTimeout(s.timer);
    clearTimeout(s.typing);
    APP.story = null;
    const done = s.done;
    done();
  }

  // ---------------- battle & sweep ----------------
  APP.startStage = function (chapter, stage) {
    const st = APP.state;
    const cost = 6;
    if (st.energy < cost) { toast('行动力不足，请先回大厅或去军需商店补给'); return; }
    const first = chapter.stages[0].id === stage.id;
    const playIntro = first && !st.storySeen[chapter.id];
    const launch = () => {
      st.energy -= cost;
      if (LGSave.recordTransaction) LGSave.recordTransaction(st, 'energy', -cost, 'battle_stage:' + stage.id);
      st.stats.battles = (st.stats.battles || 0) + 1;
      save();
      UI.renderHud(st);
      runBattle(chapter, stage, playIntro);
    };
    if (playIntro) {
      APP.playStory(chapter.story, chapter.bgWeb, () => { st.storySeen[chapter.id] = true; save(); launch(); }, chapter.id);
    } else {
      launch();
    }
  };

  APP.sweepStage = function (chapter, stage, times) {
    const st = APP.state;
    times = times || 1;
    const totalCost = 6 * times;
    if (st.energy < totalCost) {
      toast(`行动力不足，扫荡 ${times} 次需要 ${totalCost} ⚡行动力（当前 ${st.energy}）`);
      return;
    }

    st.energy -= totalCost;
    if (LGSave.recordTransaction) LGSave.recordTransaction(st, 'energy', -totalCost, `sweep_stage_${times}x:` + stage.id);

    const gainGold = (stage.rewardGold || 150) * times;
    const gainShards = (stage.rewardShards || 2) * times;
    const gainKeys = (stage.rewardKeys || 0) * times;

    st.gold += gainGold;
    st.shards += gainShards;
    if (gainKeys) st.keys += gainKeys;

    st.stats.sweeps = (st.stats.sweeps || 0) + times;
    st.stats.battles = (st.stats.battles || 0) + times;
    st.stats.wins = (st.stats.wins || 0) + times;

    // Squad heroes gain some EXP
    st.squad.forEach((k) => {
      const inst = Progression.getHeroInstance(st, k);
      inst.xp = (inst.xp || 0) + 50 * times;
    });

    if (window.Missions) {
      Missions.track(st, APP.data.missions, 'battle', times);
      Missions.track(st, APP.data.missions, 'sweep', times);
    }

    save();
    UI.renderHud(st);
    LGAudio.playSfx('win');
    $('#stage-action-sheet').classList.remove('open');

    toast(`扫荡 ${times} 次完成！获得 🪙 +${gainGold.toLocaleString()} 金币、💎 +${gainShards} 碎片${gainKeys ? '、🗝 +' + gainKeys + ' 钥匙' : ''}`);
  };

  async function runBattle(chapter, stage, fromPrologue) {
    const st = APP.state;
    // Prepare squad heroes with progression stats
    const heroes = st.squad.map((key, i) => {
      const h = heroFor(key);
      if (!h) return null;
      const inst = Progression.getHeroInstance(st, key);
      const computed = Progression.computeHeroCombatStats(h, inst, APP.data.equipment, { leader: i === 0 });
      const copy = Object.assign({}, h);
      copy._stats = computed;
      return copy;
    }).filter(Boolean);

    const enemies = (stage.enemy || []).filter((e) => e && e.unit);
    $('#battle-field').style.backgroundImage = 'url(assets/bg/battle.webp)';
    show('battle');
    const canvas = $('#battle-canvas');

    APP.currentChapter = chapter;
    APP.currentStage = stage;
    APP.currentHeroes = heroes;

    if (!APP.battle) {
      const playerLevel = Math.min(30, 1 + Math.floor(Object.keys(st.cleared).length / 2));
      APP.battle = new Battle({
        field: $('#battle-field'), canvas,
        playerLevel,
        onEnd: (r) => endBattle(APP.currentChapter || chapter, APP.currentStage || stage, r, APP.currentHeroes || heroes),
        constants: Object.assign({}, APP.data.battle || { timerSeconds: 60, skillDeliveryCooldown: 4 }, {
          timerSeconds: (stage.level || 1) <= 10
            ? ((APP.data.battle && APP.data.battle.noobTimerSeconds) || 120)
            : ((APP.data.battle && APP.data.battle.timerSeconds) || 60),
        }),
        onLog: (t) => {
          $('#battle-log').textContent = t;
          const list = $('#battle-loglist');
          if (list) {
            const li = document.createElement('li');
            li.textContent = t;
            list.prepend(li);
            while (list.children.length > 6) list.lastChild.remove();
          }
        },
      });
    }
    const b = APP.battle;
    b.chapter = chapter;
    b.stage = stage;
    b.heroes = heroes;
    b.onEnd = (r) => endBattle(chapter, stage, r, heroes);
    b.ended = false;
    b.running = false;
    b.units = [];
    b.skipping = false;
    $('#battle-log').textContent = fromPrologue
      ? '【战术无线电】先锋突入完成，第一波敌兵进入射程！'
      : '正在载入特勤作战单位…';
    const loglist = $('#battle-loglist');
    if (loglist) loglist.innerHTML = '';
    if (fromPrologue && loglist) {
      const li = document.createElement('li');
      li.textContent = '【战术无线电】先锋突入完成，第一波敌兵进入射程！';
      loglist.appendChild(li);
    }

    try {
      await b.preload(heroes, enemies);
    } catch (e) {
      console.error(e);
      toast('单位资源载入失败：' + e.message);
      show('campaign');
      return;
    }

    b.start(stage, heroes, enemies);
    LGAudio.playBgm('battle');

    // 挂载案例式战术网格与教程反馈 (TacticalHud)
    if (window.TacticalHud) {
      APP.tacticalHud = new TacticalHud(b);
    }

    // 戰鬥入場 Spine 骨骼特寫展示：序章已完整演繹時不再冗餘阻斷，非序章時展示短特寫
    const entranceBox = $('#battle-entrance-cutin');
    const leaderHero = heroes[0];
    if (!fromPrologue && entranceBox && leaderHero && window.SpineStage) {
      entranceBox.hidden = false;
      $('#entrance-name').textContent = leaderHero.name;
      const quotes = {
        kitty: '“锁定前方战术目标，全员随我突击！”',
        dominica: '“战局已进入计算序列，执行压制方案。”',
        regina: '“让杂兵们见识真正的战场火力！”',
        florence: '“治疗信标就绪，放心冲锋吧，指挥官。”',
      };
      $('#entrance-quote').textContent = quotes[leaderHero.key] || '“特勤作战指令已下达，准备接敌！”';
      const entranceCanvas = $('#entrance-spine-canvas');
      if (!APP.stages.entrance) {
        APP.stages.entrance = new SpineStage(entranceCanvas, { heightRatio: 0.98, yRatio: 0.05, xRatio: 0.5 });
      }
      APP.stages.entrance.show(leaderHero.unit);
      b.running = false;
      setTimeout(() => {
        entranceBox.hidden = true;
        if (APP.stages.entrance) APP.stages.entrance.stop();
        b.running = true;
      }, 1200);
    }

    cancelAnimationFrame(APP.raf);
    let last = performance.now();
    const loop = (now) => {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      const scaled = dt * (b.speed || 1);
      b.step(scaled);
      b.spine.update(scaled);
      b.spine.draw();
      if (!b.ended) APP.raf = requestAnimationFrame(loop);
    };
    APP.raf = requestAnimationFrame(loop);

    $('#btn-battle-speed').onclick = () => {
      b.speed = b.speed >= 3 ? 1 : b.speed + 1;
      $('#btn-battle-speed').textContent = b.speed + '×';
    };
    $('#btn-battle-skip').onclick = () => {
      toast('战术跳过中…');
      if (b && typeof b.skip === 'function') {
        b.skip();
      } else {
        b.speed = 10;
      }
    };

    const skillHeal = $('#cmd-skill-heal');
    const skillEmp = $('#cmd-skill-emp');
    const skillStrike = $('#cmd-skill-strike');
    if (skillHeal) skillHeal.onclick = () => b.useCommanderSkill('heal');
    if (skillEmp) skillEmp.onclick = () => b.useCommanderSkill('emp');
    if (skillStrike) skillStrike.onclick = () => b.useCommanderSkill('strike');

    if (APP._cmdKeyHandler) window.removeEventListener('keydown', APP._cmdKeyHandler);
    APP._cmdKeyHandler = (e) => {
      if (!b.running || b.ended) return;
      if (e.key === '1') b.useCommanderSkill('heal');
      else if (e.key === '2') b.useCommanderSkill('emp');
      else if (e.key === '3') b.useCommanderSkill('strike');
    };
    window.addEventListener('keydown', APP._cmdKeyHandler);
  }

  function endBattle(chapter, stage, r, heroes) {
    if (APP._cmdKeyHandler) {
      window.removeEventListener('keydown', APP._cmdKeyHandler);
      APP._cmdKeyHandler = null;
    }
    const st = APP.state;
    cancelAnimationFrame(APP.raf);
    const rewards = [];
    if (r.win) {
      st.gold += stage.rewardGold;
      rewards.push(['金币', stage.rewardGold]);
      if (stage.rewardKeys) { st.keys += stage.rewardKeys; rewards.push(['契约钥匙', stage.rewardKeys]); }
      if (stage.rewardShards) { st.shards += stage.rewardShards; rewards.push(['突破碎片', stage.rewardShards]); }

      st.cleared[stage.id] = true;
      st.stars[stage.id] = Math.max(st.stars[stage.id] || 0, r.stars);
      st.stats.wins = (st.stats.wins || 0) + 1;

      if (window.Missions) {
        Missions.track(st, APP.data.missions, 'battle', 1);
        const lastStage = chapter.stages[chapter.stages.length - 1];
        if (st.cleared[lastStage.id]) {
          Missions.track(st, APP.data.missions, 'chapter_clear', 1, { chapterId: chapter.id });
        }
      }

      save();
      LGAudio.playSfx('win');
    } else {
      LGAudio.playSfx('lose');
    }

    $('#result-title').textContent = r.win ? '战斗胜利' : '战斗失败';
    $('#result-stars').textContent = r.win ? UI.starsText(r.stars) : '☆☆☆';
    $('#result-sub').textContent = '用时 ' + Math.round(r.time) + ' 秒 · 存活 ' + r.alive + ' / 3';
    const box = $('#result-rewards');
    box.innerHTML = '';
    if (r.win) {
      box.appendChild(UI.el('div', { class: 'rr' }, [UI.el('span', { text: '关卡' }), UI.el('b', { text: stage.title })]));
      rewards.forEach(([k, v]) => box.appendChild(UI.el('div', { class: 'rr' }, [UI.el('span', { text: k }), UI.el('b', { text: '+' + v })])));
    } else {
      box.appendChild(UI.el('div', { class: 'rr' }, [UI.el('span', { text: '战术建议' }), UI.el('b', { text: '前往单位详情强化等级、突破星级或穿戴战术装备' })]));
    }
    const curIdx = (chapter.stages || []).findIndex((s) => s.id === stage.id);
    const nextStage = curIdx >= 0 && curIdx < (chapter.stages || []).length - 1 ? chapter.stages[curIdx + 1] : null;

    if (r.win && nextStage) {
      $('#btn-result-next').textContent = '下一关 (' + nextStage.stage + ')';
      $('#btn-result-next').onclick = () => {
        APP.startStage(chapter, nextStage);
      };
    } else {
      $('#btn-result-next').textContent = r.win ? '返回战役' : '重试';
      $('#btn-result-next').onclick = () => {
        if (r.win) { show('campaign'); UI.renderCampaign(st, APP.data); UI.openChapter(st, APP.data, chapter.id); }
        else { runBattle(chapter, stage); }
      };
    }
    $('#btn-result-back').onclick = () => { show('campaign'); UI.renderCampaign(st, APP.data); UI.openChapter(st, APP.data, chapter.id); };

    const showSummary = () => {
      // 檢查關卡或章節是否有專屬 CG 動畫穿插播放 (章節通關或特定 BOSS 關)
      const lastStage = (chapter.stages || [])[(chapter.stages || []).length - 1];
      const isChapterFinal = lastStage && lastStage.id === stage.id;
      const cgSrc = stage.rewardCg || (isChapterFinal && chapter && chapter.rewardCg);
      const isFirstClear = r.win && (!st.cgSeen || !st.cgSeen[stage.id]);
      if (isFirstClear && cgSrc && !r.skipped) {
        st.cgSeen = st.cgSeen || {};
        st.cgSeen[stage.id] = true;
        save();
        playCinemaCg(cgSrc, () => {
          show('result');
          UI.renderHud(st);
        });
        return;
      }
      show('result');
      UI.renderHud(st);
    };

    // 戰鬥勝利凱旋特寫 (非跳過時展示，跳過時直接結算進入結果頁)
    const victoryBox = $('#battle-victory-cutin');
    if (r.win && victoryBox && !r.skipped) {
      victoryBox.hidden = false;
      $('#victory-stars').textContent = UI.starsText(r.stars);
      const leader = (heroes && heroes[0]) || (APP.state && APP.state.squad && heroFor(APP.state.squad[0]));
      if (leader) $('#victory-name').textContent = leader.name + ' & 作战特勤队';
      setTimeout(() => {
        victoryBox.hidden = true;
        showSummary();
      }, 2200);
      return;
    }

    showSummary();
  }

  function playCinemaCg(src, onComplete) {
    const overlay = $('#cg-cinema-overlay');
    const video = $('#cg-cinema-video');
    const skipBtn = $('#btn-cg-skip');
    if (!overlay || !video) { if (onComplete) onComplete(); return; }

    overlay.hidden = false;
    video.src = src;
    video.currentTime = 0;
    const finish = () => {
      video.pause();
      overlay.hidden = true;
      video.onended = null;
      skipBtn.onclick = null;
      if (onComplete) onComplete();
    };

    video.onended = finish;
    skipBtn.onclick = finish;
    video.play().catch(() => {});
  }

  // ---------------- gacha ----------------
  function pullOne(state, pity) {
    const g = APP.data.gacha;
    pity += 1;
    let rarity;
    if (pity >= g.pity) rarity = 'SSR';
    else {
      const roll = Math.random();
      rarity = roll < g.rates.SSR ? 'SSR' : roll < g.rates.SSR + g.rates.SR ? 'SR' : 'R';
    }
    const pool = g.pools[rarity] || g.pools.SSR;
    const key = pool[Math.floor(Math.random() * pool.length)];
    const hero = heroFor(key);
    const isNew = !state.owned.includes(key);
    let shards = 0;
    if (isNew) {
      state.owned.push(key);
      Progression.getHeroInstance(state, key);
    } else {
      shards = rarity === 'SSR' ? 50 : rarity === 'SR' ? 25 : 12;
      state.shards += shards;
    }
    if (rarity === 'SSR') pity = 0;
    return { hero, isNew, shards, rarity };
  }

  APP.doPull = function (count) {
    const st = APP.state;
    const g = APP.data.gacha;
    const useKeys = st.keys >= (count === 10 ? g.costTen.keys : g.costSingle.keys);
    if (useKeys) {
      const cost = count === 10 ? g.costTen.keys : g.costSingle.keys;
      st.keys -= cost;
      if (LGSave.recordTransaction) LGSave.recordTransaction(st, 'keys', -cost, 'gacha_pull:' + count);
    } else if (st.gold >= (count === 10 ? g.costTen.gold : g.costSingle.gold)) {
      const cost = count === 10 ? g.costTen.gold : g.costSingle.gold;
      st.gold -= cost;
      if (LGSave.recordTransaction) LGSave.recordTransaction(st, 'gold', -cost, 'gacha_pull:' + count);
    } else {
      toast('契约钥匙与金币都不够');
      return;
    }

    let pity = st.pity;
    const results = [];
    for (let i = 0; i < count; i++) {
      const r = pullOne(st, pity);
      pity = st.pity = (r.rarity === 'SSR' ? 0 : pity + 1);
      results.push(r);
    }
    st.pulls += count;

    if (window.Missions) {
      Missions.track(st, APP.data.missions, 'recruit', count);
      Missions.track(st, APP.data.missions, 'pulls_count', count);
    }

    save();
    LGAudio.playSfx('collect');
    UI.renderHud(st);
    UI.showReveal(results);
  };

  APP.toggleSquad = function (key) {
    const st = APP.state;
    if (!st.owned.includes(key)) { toast('尚未获得该单位，请先在招募中心抽取'); return; }
    const i = st.squad.indexOf(key);
    if (i >= 0) {
      if (st.squad.length <= 1) { toast('编队至少保留 1 名出征特勤'); return; }
      st.squad.splice(i, 1);
    } else {
      if (st.squad.length >= 3) st.squad.shift();
      st.squad.push(key);
    }
    save();
    UI.renderSquad(st, APP.data);
    UI.renderHud(st);
  };

  APP.buy = function (item) {
    const st = APP.state;
    for (const [k, v] of Object.entries(item.cost)) {
      if ((st[k] || 0) < v) { toast('物资不足'); return; }
    }
    for (const [k, v] of Object.entries(item.cost)) {
      st[k] -= v;
      if (LGSave.recordTransaction) LGSave.recordTransaction(st, k, -v, 'shop_buy:' + item.id);
    }

    for (const [k, v] of Object.entries(item.gain)) {
      if (k === 'shards') st.shards += v;
      else if (k === 'energy') st.energy = Math.min((APP.data.constants && APP.data.constants.maxEnergy) || 100, st.energy + v);
      else if (k === 'equipment') {
        const catalog = APP.data.equipment || [];
        if (catalog.length) {
          const picked = catalog[Math.floor(Math.random() * catalog.length)];
          const slot = picked.slot + 's';
          if (!st.inventory[slot]) st.inventory[slot] = [];
          st.inventory[slot].push(picked.id);
          toast('开启装备箱，获得：' + picked.name);
        }
      } else {
        st[k] = (st[k] || 0) + v;
      }
      if (LGSave.recordTransaction) LGSave.recordTransaction(st, k, v, 'shop_gain:' + item.id);
    }
    save();
    UI.renderShop(st, APP.data);
    UI.renderHud(st);
    toast('已采购：' + item.name);
  };

  // ---------------- settings ----------------
  function bindSettings() {
    const st = APP.state;
    const bgm = $('#set-bgm'); const sfx = $('#set-sfx'); const motion = $('#set-motion'); const adult = $('#set-adult');
    bgm.value = Math.round(st.settings.bgm * 100);
    sfx.value = Math.round(st.settings.sfx * 100);
    motion.checked = !!st.settings.reduceMotion;
    adult.checked = !!st.settings.adult;
    bgm.addEventListener('input', () => { st.settings.bgm = Number(bgm.value) / 100; LGAudio.setVolumes(st.settings.bgm, st.settings.sfx); save(); });
    sfx.addEventListener('input', () => { st.settings.sfx = Number(sfx.value) / 100; LGAudio.setVolumes(st.settings.bgm, st.settings.sfx); save(); });
    motion.addEventListener('change', () => { st.settings.reduceMotion = motion.checked; document.documentElement.classList.toggle('reduce-motion', st.settings.reduceMotion); save(); });
    adult.addEventListener('change', () => { st.settings.adult = adult.checked; save(); });
    const btnQaMax = $('#btn-qa-max');
    if (btnQaMax) {
      btnQaMax.onclick = () => {
        LGSave.applyQAFullTestResources(st, APP.data.heroes, APP.data.equipment);
        save();
        UI.renderHud(st);
        if (APP.screen === 'hub') UI.renderHub(st, APP.data);
        toast('🎉 全 55 名特勤英雄已解锁！金币/钥匙/体力/碎片已全部拉满！');
      };
    }
    $('#btn-export').onclick = async () => {
      const text = LGSave.exportText(st);
      try { await navigator.clipboard.writeText(text); toast('存档已复制到剪贴板'); }
      catch (e) { prompt('复制下面的存档：', text); }
    };
    $('#btn-import').onclick = async () => {
      let text = '';
      try { text = await navigator.clipboard.readText(); } catch (e) { /* fall through */ }
      if (!text) text = prompt('粘贴存档内容：') || '';
      if (!text) return;
      try {
        const next = LGSave.importText(text);
        APP.state = next;
        LGSave.persist(next);
        bindSettings();
        UI.renderHub(APP.state, APP.data);
        toast('存档已成功导入');
      } catch (e) { toast('导入失败：' + e.message); }
    };
    $('#btn-reset').onclick = async () => {
      const ok = await confirmDialog('重置存档', '这会清空全部本机进度与养成数据，且无法撤销。', '重置');
      if (!ok) return;
      APP.state = LGSave.reset();
      bindSettings();
      UI.renderHub(APP.state, APP.data);
      show('hub');
      toast('存档已重置');
    };
    LGAudio.setVolumes(st.settings.bgm, st.settings.sfx);
  }

  // ---------------- wire events ----------------
  function bindNav() {
    $('#btn-start').onclick = () => { LGAudio.unlock(); finishBoot(); };
    $('#btn-continue').onclick = () => { LGAudio.unlock(); finishBoot(); };
    $('#btn-settings').onclick = () => { UI.renderHud(APP.state); bindSettings(); show('settings'); };
    $('#btn-missions').onclick = () => { UI.renderMissions(APP.state, APP.data, 'daily'); };
    $('#nav-campaign').onclick = () => { UI.renderCampaign(APP.state, APP.data); show('campaign'); };
    $('#nav-gacha').onclick = () => { UI.renderGacha(APP.state, APP.data); show('gacha'); };
    $('#nav-squad').onclick = () => { UI.renderSquad(APP.state, APP.data); show('squad'); };
    $('#nav-roster').onclick = () => { UI.renderRoster(APP.state, APP.data); show('roster'); };
    $('#nav-shop').onclick = () => { UI.renderShop(APP.state, APP.data); show('shop'); };
    $('#nav-slots').onclick = () => { show('slots'); SlotsUI.render(); };
    $$('[data-back]').forEach((b) => { b.onclick = () => { UI.renderHub(APP.state, APP.data); show('hub'); }; });
    $('#btn-chapter-back').onclick = () => { UI.renderCampaign(APP.state, APP.data); };
    $('#btn-squad-done').onclick = () => { UI.renderHub(APP.state, APP.data); show('hub'); };
    $('#btn-detail-close').onclick = () => UI.closeDetail();
    $('#detail-sheet').addEventListener('click', (e) => { if (e.target.id === 'detail-sheet') UI.closeDetail(); });
    const closeReveal = () => {
      $('#gacha-reveal').classList.remove('open');
      if (APP.stages && APP.stages.gacha) APP.stages.gacha.stop();
    };
    $('#btn-reveal-close').onclick = closeReveal;
    $('#btn-reveal-ok').onclick = closeReveal;
    $('#btn-cg').onclick = () => UI.renderCg(APP.data.videos);
    $('#btn-cg-close').onclick = () => $('#cg-sheet').classList.remove('open');
    $('#cg-sheet').addEventListener('click', (e) => { if (e.target.id === 'cg-sheet') $('#cg-sheet').classList.remove('open'); });
    $('#btn-pull1').onclick = () => APP.doPull(1);
    $('#btn-pull10').onclick = () => APP.doPull(10);

    // story controls
    $('#screen-story').addEventListener('click', (e) => {
      if (e.target.closest('button')) return;
      advanceStory();
    });
    $('#btn-story-skip').onclick = () => finishStory();
    $('#btn-story-auto').onclick = (e) => {
      const s = APP.story;
      if (!s) return;
      s.auto = !s.auto;
      e.currentTarget.setAttribute('aria-pressed', String(s.auto));
      e.currentTarget.textContent = s.auto ? '自动播放中' : '自动播放';
      if (s.auto) scheduleAuto(); else clearTimeout(s.timer);
    };
    document.addEventListener('keydown', (e) => {
      if (APP.screen === 'story' && (e.key === ' ' || e.key === 'Enter')) { e.preventDefault(); advanceStory(); }
      if (e.key === 'Escape') { UI.closeDetail(); closeReveal(); $('#mission-sheet').classList.remove('open'); $('#stage-action-sheet').classList.remove('open'); $('#equip-picker-sheet').classList.remove('open'); }
    });
  }

  function finishBoot() {
    regenEnergy();
    if (window.Missions) Missions.checkDailyReset(APP.state);
    UI.setProgress(1);
    UI.renderHub(APP.state, APP.data);
    show('hub');
    if ($('#hub-art') && $('#hub-art').decode) $('#hub-art').decode().catch(() => {});
  }

  async function boot() {
    APP.state = LGSave.load();
    UI.setProgress(0.04);
    try {
      APP.data = await loadData();
      // QA Auto-Boost: Unlock all 55 heroes, max out all resources (Gold, Keys, Energy, Shards, Equipment)
      LGSave.applyQAFullTestResources(APP.state, APP.data.heroes, APP.data.equipment);
      save();
    } catch (e) {
      $('#boot-note').textContent = '载入失败：' + e.message;
      return;
    }
    UI.setProgress(0.92);
    initStages();
    if (window.SlotsUI) SlotsUI.init().catch((e) => console.warn('Slots init failed:', e));
    $('#title-art').style.backgroundImage = 'url(assets/bg/hub.webp)';
    const hasSave = Object.keys(APP.state.cleared || {}).length > 0 || (APP.state.owned && APP.state.owned.length > LGSave.STARTERS.length);
    $('#btn-start').textContent = hasSave ? '进入游戏' : '开始游戏';
    $('#btn-continue').hidden = !hasSave;
    document.documentElement.classList.toggle('reduce-motion', !!APP.state.settings.reduceMotion);
    LGAudio.setVolumes(APP.state.settings.bgm, APP.state.settings.sfx);
    UI.setProgress(1);
    show('title');
    bindNav();
  }

  boot();
})();
