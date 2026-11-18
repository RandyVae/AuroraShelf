/* App controller: boot, routing, story, battle, gacha, progression, sweep, missions, shop, settings. */
(function () {
  'use strict';

  // 禁用多指捏合缩放手势（双击放缩由 CSS touch-action: manipulation 处理，不拦截快速连击 touchend）
  (function preventPinchZoom() {
    document.addEventListener('touchstart', function (event) {
      if (event.touches.length > 1) {
        event.preventDefault();
      }
    }, { passive: false });

    document.addEventListener('gesturestart', function (event) {
      event.preventDefault();
    }, { passive: false });
    document.addEventListener('gesturechange', function (event) {
      event.preventDefault();
    }, { passive: false });
    document.addEventListener('gestureend', function (event) {
      event.preventDefault();
    }, { passive: false });
  })();

  // 原生 iOS / Android 触觉与生命周期桥梁
  const NativeBridge = {
    haptic(style) {
      try {
        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.NativeBridge) {
          window.webkit.messageHandlers.NativeBridge.postMessage({ type: 'haptic', style: style || 'medium' });
        } else if (window.AndroidNative && window.AndroidNative.haptic) {
          window.AndroidNative.haptic(style || 'medium');
        }
      } catch (_) {}
    },
    exitGame() {
      try {
        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.NativeBridge) {
          window.webkit.messageHandlers.NativeBridge.postMessage({ type: 'exitGame' });
        } else if (window.AndroidNative && window.AndroidNative.exitGame) {
          window.AndroidNative.exitGame();
        }
      } catch (_) {}
    },
    toast(msg) {
      try {
        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.NativeBridge) {
          window.webkit.messageHandlers.NativeBridge.postMessage({ type: 'toast', message: msg });
        } else if (window.AndroidNative && window.AndroidNative.toast) {
          window.AndroidNative.toast(msg);
        }
      } catch (_) {}
    }
  };
  window.NativeBridge = NativeBridge;

  window.setAppMuted = function (muted) {
    if (window.LGAudio) {
      LGAudio.appMuted = !!muted;
      if (muted) {
        LGAudio.isMuted = true;
        if (LGAudio.pauseBgm) LGAudio.pauseBgm();
        else LGAudio.stopBgm();
      } else {
        LGAudio.isMuted = (LGAudio.bgmVolume === 0 && LGAudio.sfxVolume === 0);
        if (!LGAudio.isMuted) {
          if (APP && APP.screen) {
            const bgm = { title: 'title', hub: 'hub', campaign: 'campaign', story: 'story', battle: 'battle', gacha: 'gacha', slots: 'gacha' }[APP.screen];
            if (bgm) LGAudio.playBgm(bgm);
          } else if (LGAudio.resumeBgm) {
            LGAudio.resumeBgm();
          }
        }
      }
    }
  };

  window.handleAppBack = function () {
    // 1. Cinema CG overlay
    const cgCinema = document.querySelector('#cg-cinema-overlay');
    if (cgCinema && !cgCinema.hidden) {
      const skip = document.getElementById('btn-cg-skip');
      if (skip && typeof skip.onclick === 'function') skip.onclick();
      else if (skip && typeof skip.click === 'function') skip.click();
      else cgCinema.hidden = true;
      return true;
    }

    // 2. CG Lightbox overlay
    const cgLightbox = document.querySelector('#cg-lightbox-overlay');
    if (cgLightbox && !cgLightbox.hidden) {
      const closeLb = document.getElementById('btn-cg-lightbox-close');
      if (closeLb && typeof closeLb.onclick === 'function') closeLb.onclick();
      else if (closeLb && typeof closeLb.click === 'function') closeLb.click();
      else cgLightbox.hidden = true;
      return true;
    }

    // 3. Generic confirmation / export / import modal (#modal uses .open)
    const modal = document.querySelector('#modal');
    if (modal && modal.classList.contains('open')) {
      const cancel = document.getElementById('modal-cancel');
      if (cancel && typeof cancel.onclick === 'function') cancel.onclick();
      else if (cancel && typeof cancel.click === 'function') cancel.click();
      else modal.classList.remove('open');
      return true;
    }

    // 4. Battle emergency revival modal: must run cancel handler so renderDefeatResult() settles cleanly
    const reviveModal = document.querySelector('#modal-battle-revive');
    if (reviveModal && !reviveModal.hidden) {
      const cancelRevive = document.getElementById('btn-battle-revive-cancel');
      if (cancelRevive && typeof cancelRevive.onclick === 'function') {
        cancelRevive.onclick();
      } else if (cancelRevive && typeof cancelRevive.click === 'function') {
        cancelRevive.click();
      } else {
        reviveModal.classList.remove('open');
        reviveModal.hidden = true;
      }
      return true;
    }

    // 5. Slots feature reward overlay: trigger #btn-slots-feature-close click so waitForFeatureDismissal resolves
    const slotsFeature = document.querySelector('#slots-feature');
    if (slotsFeature && !slotsFeature.hidden) {
      const closeFeat = document.getElementById('btn-slots-feature-close');
      if (closeFeat && typeof closeFeat.click === 'function') {
        closeFeat.click();
      } else if (closeFeat && typeof closeFeat.onclick === 'function') {
        closeFeat.onclick();
      } else {
        slotsFeature.hidden = true;
      }
      return true;
    }

    // 6. Slots paytable & transient hidden/open modals
    const transientOverlays = ['#slots-paytable', '#modal-slots-bailout', '#modal-slots-recharge', '#modal-battle-recharge'];
    for (let i = 0; i < transientOverlays.length; i++) {
      const el = document.querySelector(transientOverlays[i]);
      if (el && !el.hidden && (el.classList.contains('open') || !el.classList.contains('sheet'))) {
        el.classList.remove('open');
        el.hidden = true;
        return true;
      }
    }

    // 7. Persistent .sheet & .gacha-reveal overlays (controlled by .open; NEVER leave hidden = true)
    const openSheets = [
      '#equip-picker-sheet', '#detail-sheet', '#mission-sheet',
      '#stage-action-sheet', '#cg-sheet', '#gacha-reveal',
    ];
    for (let i = 0; i < openSheets.length; i++) {
      const sel = openSheets[i];
      const el = document.querySelector(sel);
      if (el && el.classList.contains('open')) {
        if (sel === '#detail-sheet' && window.UI && typeof UI.closeDetail === 'function') {
          UI.closeDetail();
        } else if (sel === '#gacha-reveal') {
          el.classList.remove('open');
          if (APP.stages && APP.stages.gacha) APP.stages.gacha.stop();
        } else if (sel === '#cg-sheet') {
          if (el.querySelectorAll) {
            el.querySelectorAll('video').forEach((v) => { try { v.pause(); } catch (_) {} });
          }
          el.classList.remove('open');
        } else {
          el.classList.remove('open');
        }
        return true;
      }
    }

    // 8. Navigate from sub-screens back to Hub
    if (APP && APP.screen && APP.screen !== 'hub' && APP.screen !== 'title' && APP.screen !== 'boot') {
      if (APP.battle && APP.battle.running) APP.battle.stop();
      if (window.SlotsUI && SlotsUI.stopAutoSpin) SlotsUI.stopAutoSpin();
      if (window.UI && UI.renderHub && APP.state && APP.data) UI.renderHub(APP.state, APP.data);
      APP.show('hub');
      return true;
    }
    return false;
  };

  // 检测宿主是否为原生 iOS/Android App，若是则隐藏网页内置胶囊以避免双重渲染
  if ((window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.NativeBridge) || window.AndroidNative) {
    document.body.classList.add('native-app');
  }

  // 绑定纯网页端小程序胶囊交互
  document.addEventListener('DOMContentLoaded', () => {
    const mpClose = document.getElementById('mp-btn-close');
    if (mpClose) {
      mpClose.addEventListener('click', () => {
        NativeBridge.exitGame();
        try { window.close(); } catch (_) {}
      });
    }
    const mpMenu = document.getElementById('mp-btn-menu');
    if (mpMenu) {
      mpMenu.addEventListener('click', () => {
        const isMuted = Boolean(window.LGAudio && window.LGAudio.isMuted);
        window.setAppMuted(!isMuted);
        if (window.UI && window.UI.toast) {
          window.UI.toast(!isMuted ? '🔇 已静音' : '🔊 已开启音效');
        }
      });
    }
  });

  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => [...(r || document).querySelectorAll(s)];
  const APP = {
    state: null, data: null, screen: 'boot',
    battle: null, raf: 0, story: null,
    gamePaused: false,
    resultSettled: false,
    _entranceTimer: null,
  };
  window.APP = APP;

  const SCREENS = ['boot', 'title', 'hub', 'campaign', 'story', 'battle', 'result', 'squad', 'gacha', 'roster', 'shop', 'slots', 'settings'];

  function show(name) {
    SCREENS.forEach((s) => {
      const node = $('#screen-' + s);
      if (node) node.classList.toggle('active', s === name);
    });
    APP.screen = name;
    if (name !== 'battle') {
      if (APP._entranceTimer) {
        clearTimeout(APP._entranceTimer);
        APP._entranceTimer = null;
      }
      if (APP.battle && APP.battle.running) APP.battle.stop();
    }
    if (name !== 'story') {
      if (window.IntroTimeline && IntroTimeline.active && typeof IntroTimeline.abort === 'function') {
        IntroTimeline.abort();
      }
      if (APP.story) {
        clearTimeout(APP.story.timer);
        clearTimeout(APP.story.typing);
        APP.story = null;
      }
    }
    if (name !== 'slots' && window.SlotsUI && SlotsUI.stopAutoSpin) SlotsUI.stopAutoSpin();
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
  APP.openSlots = function () {
    show('slots');
    if (window.SlotsUI) {
      SlotsUI.render();
    }
  };

  // Shared lifecycle boundary for Android WebView, iOS WKWebView and browser
  // visibility changes. It stops active work without clearing saved progress.
  APP.pauseGame = function () {
    APP.gamePaused = true;
    if (APP._entranceTimer) {
      clearTimeout(APP._entranceTimer);
      APP._entranceTimer = null;
      const entranceBox = $('#battle-entrance-cutin');
      if (entranceBox) entranceBox.hidden = true;
      if (APP.stages && APP.stages.entrance) APP.stages.entrance.stop();
    }
    if (APP.battle) APP.battle.running = false;
    if (window.LGVFX && typeof LGVFX.pause === 'function') LGVFX.pause();
    if (window.SlotsUI) {
      if (SlotsUI.stopAutoSpin) SlotsUI.stopAutoSpin();
      if (SlotsUI.pauseMascot) SlotsUI.pauseMascot();
    }
    if (window.LGAudio) {
      if (typeof LGAudio.pauseBgm === 'function') LGAudio.pauseBgm();
      else if (typeof LGAudio.stopBgm === 'function') LGAudio.stopBgm();
    }
  };

  APP.resumeGame = function () {
    APP.gamePaused = false;
    if (window.LGAudio && typeof LGAudio.resumeBgm === 'function') {
      LGAudio.resumeBgm();
    }
    if (APP.screen === 'battle' && APP.battle && !APP.battle.ended) {
      APP.battle.running = true;
      if (window.LGVFX && typeof LGVFX.resume === 'function') LGVFX.resume();
    } else if (APP.screen === 'slots' && window.SlotsUI && typeof SlotsUI.resumeMascot === 'function') {
      SlotsUI.resumeMascot();
    }
  };

  APP.destroyGame = function () {
    APP.pauseGame();
    cancelAnimationFrame(APP.raf);
    if (APP.battle) APP.battle.stop();
    if (window.SlotsUI && typeof SlotsUI.destroy === 'function') SlotsUI.destroy();
    if (APP._cmdKeyHandler) {
      window.removeEventListener('keydown', APP._cmdKeyHandler);
      APP._cmdKeyHandler = null;
    }
    if (APP.stages) {
      Object.values(APP.stages).forEach((stage) => {
        if (stage && typeof stage.stop === 'function') {
          try { stage.stop(); } catch (e) {}
        }
      });
    }
  };

  document.addEventListener('visibilitychange', () => {
    if (document.hidden) APP.pauseGame();
    else APP.resumeGame();
  });
  window.addEventListener('pagehide', () => APP.destroyGame());

  function save() { LGSave.persist(APP.state); }

  function initStages() {
    if (!window.SpineStage) return;
    if (APP.state && APP.state.settings && APP.state.settings.reduceMotion) return;
    APP.stages = {
      hub: new SpineStage($('#hub-canvas'), { heightRatio: 0.94, yRatio: 0.06, xRatio: 0.5 }),
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
      const bodyEl = $('#modal-body');
      bodyEl.innerHTML = '';
      bodyEl.textContent = body;
      const ok = $('#modal-ok');
      const cancel = $('#modal-cancel');
      ok.textContent = okLabel || '确认';
      cancel.textContent = '取消';
      const close = (v) => { $('#modal').classList.remove('open'); ok.onclick = null; cancel.onclick = null; resolve(v); };
      ok.onclick = () => close(true);
      cancel.onclick = () => close(false);
      $('#modal').classList.add('open');
    });
  }

  function saveTextModal(opts) {
    return new Promise((resolve) => {
      $('#modal-title').textContent = opts.title || '存档数据';
      const bodyEl = $('#modal-body');
      bodyEl.innerHTML = '';
      const hint = document.createElement('p');
      hint.style.margin = '0 0 8px';
      hint.style.fontSize = 'var(--type-caption)';
      hint.style.color = 'var(--text-secondary)';
      hint.textContent = opts.hint || '';
      const ta = document.createElement('textarea');
      ta.className = 'modal-textarea';
      ta.rows = 6;
      ta.spellcheck = false;
      ta.value = opts.initialValue || '';
      if (opts.placeholder) ta.placeholder = opts.placeholder;
      if (opts.readOnly) ta.readOnly = true;
      bodyEl.appendChild(hint);
      bodyEl.appendChild(ta);

      const ok = $('#modal-ok');
      const cancel = $('#modal-cancel');
      ok.textContent = opts.okLabel || '确认';
      cancel.textContent = opts.cancelLabel || '关闭';
      const close = (val) => {
        $('#modal').classList.remove('open');
        ok.onclick = null;
        cancel.onclick = null;
        bodyEl.innerHTML = '';
        resolve(val);
      };
      ok.onclick = () => {
        if (opts.onConfirm) {
          const keepOpen = opts.onConfirm(ta);
          if (keepOpen === true) return;
        }
        close(ta.value);
      };
      cancel.onclick = () => close(null);
      $('#modal').classList.add('open');
      setTimeout(() => {
        try {
          ta.focus();
          if (opts.readOnly) ta.select();
        } catch (_) {}
      }, 30);
    });
  }

  function safeFetchJson(url) {
    return fetch(url).then((res) => {
      if (!res.ok && res.status !== 0 && res.status !== 200) {
        throw new Error(`HTTP ${res.status}`);
      }
      return res.json();
    }).catch(() => {
      return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.overrideMimeType('application/json');
        xhr.open('GET', url, true);
        xhr.onload = () => {
          if (xhr.status === 200 || xhr.status === 0) {
            try {
              resolve(JSON.parse(xhr.responseText));
            } catch (err) {
              reject(err);
            }
          } else {
            reject(new Error(`XHR failed (${xhr.status})`));
          }
        };
        xhr.onerror = () => reject(new Error(`XHR error loading ${url}`));
        xhr.send(null);
      });
    });
  }
  window.safeFetchJson = safeFetchJson;

  async function loadData() {
    const files = ['heroes', 'enemies', 'chapters', 'gacha', 'shop', 'constants', 'strings', 'videos', 'cgs', 'battle', 'skills', 'equipment', 'missions', 'slots', 'intro-sequences'];
    const out = {};
    await Promise.all(files.map((file, index) => {
      return safeFetchJson('data/' + file + '.json').then((payload) => {
        out[file] = payload;
        UI.setProgress((index + 1) / (files.length + 1));
        return payload;
      });
    }));
    if (out['intro-sequences'] && out['intro-sequences'].sequences && window.IntroTimeline) {
      Object.assign(window.IntroTimeline.sequences, out['intro-sequences'].sequences);
    }
    try {
      const origData = await safeFetchJson('data/original-assets-manifest.json');
      if (origData) {
        window.ORIGINAL_ASSETS_MANIFEST = origData;
        out['original-assets-manifest'] = origData;
        // 雙軌動態熱重定向：若特勤在清單中註冊了原創立繪，自動優先掛載原創美術
        const units = origData.units || {};
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
    // Ensure Prologue-only widgets are hidden when playing standard chapter story
    if (window.IntroTimeline) window.IntroTimeline.active = false;
    const scr = $('#screen-story');
    if (scr) {
      scr.classList.remove('prologue-mode');
      const track = scr.querySelector('.story-timeline-track');
      if (track) track.hidden = true;
    }
    ['#story-event-chip', '#story-tactical-prompt', '#story-role-badge', '#story-supp-actor', '#story-enemy-actor', '#story-spine-canvas', '#story-speedlines'].forEach((sel) => {
      const n = $(sel);
      if (n) n.hidden = true;
    });
    const bgEl = $('#story-bg');
    if (bgEl) {
      bgEl.style.transform = '';
      bgEl.classList.remove('shake');
      bgEl.style.backgroundImage = bg ? 'url(' + bg + ')' : 'none';
    }
    const dlgEl = $('.story-dialog');
    if (dlgEl) dlgEl.style.opacity = '1';
    const autoBtn = $('#btn-story-auto');
    if (autoBtn) {
      autoBtn.textContent = '自动播放';
      autoBtn.setAttribute('aria-pressed', 'false');
    }

    APP.story = { lines, i: 0, timer: 0, auto: false, done: onDone, typing: null };
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
    APP.resultSettled = false;
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
    const chapId = (chapter && chapter.id) || (stage && stage.id && parseInt(String(stage.id).split('-')[0], 10)) || 1;
    let bgUrl = (chapter && chapter.bgWeb) || '';
    if (!bgUrl) {
      if (chapId === 9) bgUrl = 'assets/bg/rea_temple_b001.webp';
      else if (chapId === 10) bgUrl = 'assets/bg/rea_throne_b002.webp';
      else if (chapId >= 1 && chapId <= 8) bgUrl = `assets/bg/ch${chapId}.webp`;
      else bgUrl = 'assets/bg/battle.webp';
    }
    $('#battle-field').style.backgroundImage = `url(${bgUrl})`;
    show('battle');
    const canvas = $('#battle-canvas');

    APP.currentChapter = chapter;
    APP.currentStage = stage;
    APP.currentHeroes = heroes;

    const playerLevel = Math.min(30, 1 + Math.floor(Object.keys(st.cleared || {}).length / 2));
    const stageConstants = Object.assign({}, APP.data.battle || { timerSeconds: 60, skillDeliveryCooldown: 4 }, {
      timerSeconds: (stage.level || 1) <= 10
        ? ((APP.data.battle && APP.data.battle.noobTimerSeconds) || 120)
        : ((APP.data.battle && APP.data.battle.timerSeconds) || 60),
    });

    if (APP.battle) {
      APP.battle.stop();
    } else {
      APP.battle = new Battle({
        field: $('#battle-field'), canvas,
        playerLevel,
        onEnd: (r) => endBattle(APP.currentChapter || chapter, APP.currentStage || stage, r, APP.currentHeroes || heroes),
        constants: stageConstants,
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
    b.playerLevel = playerLevel;
    b.constants = stageConstants;
    b.chapter = chapter;
    b.stage = stage;
    b.heroes = heroes;
    b.onEnd = (r) => endBattle(chapter, stage, r, heroes);
    b.ended = false;
    b.running = false;
    b.units = [];
    b.skipping = false;
    b.nextDelivery = 0;
    b._reviveUsed = false;
    b._reviving = false;
    b.comboCount = 0;
    b.lastHitTime = 0;
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

    // 严格限制教学引导：仅在 1-1 且未完成教学时挂载，后续关卡（1-2、第2章、第3章等）永久静默
    const isFirstStage = stage && (stage.id === '1-1' || stage.id === '1');
    const tutorialDone = APP.state && APP.state.tutorialCompleted;
    if (window.TacticalHud && isFirstStage && !tutorialDone) {
      APP.tacticalHud = new TacticalHud(b, {
        onComplete: () => {
          if (APP.state) {
            APP.state.tutorialCompleted = true;
            save();
          }
        }
      });
    } else {
      APP.tacticalHud = null;
    }

    // 戰鬥入場 Spine 骨骼特寫展示：序章已完整演繹時不再冗餘阻斷，非序章時展示短特寫
    if (APP._entranceTimer) {
      clearTimeout(APP._entranceTimer);
      APP._entranceTimer = null;
    }
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
      APP._entranceTimer = setTimeout(() => {
        APP._entranceTimer = null;
        entranceBox.hidden = true;
        if (APP.stages.entrance) APP.stages.entrance.stop();
        if (!APP.gamePaused && !b.ended && !b.stopped) b.running = true;
      }, 1200);
    }

    function startBattleLoop(battleInst) {
      cancelAnimationFrame(APP.raf);
      let last = performance.now();
      const loop = (now) => {
        const dt = Math.min(0.05, Math.max(0, (now - last) / 1000));
        last = now;
        const scaled = dt * (battleInst.speed || 1);
        if (!APP.gamePaused) {
          battleInst.step(scaled);
          if (battleInst.spine) {
            battleInst.spine.update(scaled);
            battleInst.spine.draw();
          }
        }
        if (!battleInst.ended && !battleInst.stopped) {
          APP.raf = requestAnimationFrame(loop);
        } else {
          battleInst._reviving = false;
        }
      };
      APP.raf = requestAnimationFrame(loop);
    }
    startBattleLoop(b);

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
  window.runBattleDirect = runBattle;

  function endBattle(chapter, stage, r, heroes) {
    if (APP.resultSettled) return;
    APP.resultSettled = true;
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

      // 新手蜜月期高爽额外首通空投
      const isRookie = (stage.id === '1-1' || stage.id === '1-2' || stage.id === '1-3');
      if (isRookie) {
        st.gold += stage.rewardGold;
        st.keys = (st.keys || 0) + 2;
        rewards.push(['先锋特惠空投', '金币翻倍 + 契约钥匙 +2']);
      }

      st.cleared[stage.id] = true;
      if (stage.id === '1-1') st.tutorialCompleted = true;
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
      if (window.NativeBridge) NativeBridge.haptic('success');

      // 充值诱导：通关第 1 章关键战役节点，弹出 SSR Esther 首充神级礼包
      if ((stage.id === '1-3' || stage.id === '1-5') && !st.firstBattleRecharge) {
        const rechModal = $('#modal-battle-recharge');
        if (rechModal) {
          rechModal.hidden = false;
          rechModal.classList.add('open');
          $('#btn-battle-recharge-close').onclick = () => {
            rechModal.classList.remove('open');
            rechModal.hidden = true;
          };
          $('#btn-battle-recharge-pay').onclick = () => {
            st.firstBattleRecharge = true;
            st.isVip = true;
            st.energy = (st.energy || 0) + 200;
            st.keys = (st.keys || 0) + 10;
            st.gold = (st.gold || 0) + 2000;
            st.owned = st.owned || [];
            if (!st.owned.includes('esther')) {
              st.owned.push('esther');
              st.roster['esther'] = LGSave.createHeroInstance('esther', { level: 30, stars: 5, skillLevel: 10 });
            }
            save();
            rechModal.classList.remove('open');
            rechModal.hidden = true;
            UI.renderHud(st);
            if (window.NativeBridge) NativeBridge.haptic('bigWin');
            UI.toast('👑 SSR 顶级战神 Esther 已加入作战特勤！行动力 +200，金钥匙 +10！');
          };
        }
      }
    } else {
      LGAudio.playSfx('lose');
      if (window.NativeBridge) NativeBridge.haptic('defeat');

      const renderDefeatResult = () => {
        $('#result-title').textContent = '战斗失败';
        $('#result-stars').textContent = '☆☆☆';
        $('#result-sub').textContent = '用时 ' + Math.round(r.time) + ' 秒 · 存活 ' + r.alive + ' / 3';
        const box = $('#result-rewards');
        box.innerHTML = '';
        box.appendChild(UI.el('div', { class: 'rr' }, [UI.el('span', { text: '战术建议' }), UI.el('b', { text: '前往单位详情强化等级、突破星级或穿戴战术装备' })]));
        $('#btn-result-next').textContent = '重试';
        $('#btn-result-next').onclick = () => {
          if (APP.isTransitioning) return;
          APP.isTransitioning = true;
          setTimeout(() => { APP.isTransitioning = false; }, 600);
          APP.startStage(chapter, stage);
        };
        show('result');
        UI.renderHud(st);
      };

      // 绝地救援协议：全队濒危时仅首次弹出急救血清
      if (!APP.battle || !APP.battle._reviveUsed) {
        const reviveModal = $('#modal-battle-revive');
        if (reviveModal) {
          reviveModal.hidden = false;
          reviveModal.classList.add('open');
          $('#btn-battle-revive-ok').onclick = () => {
            if (APP.battle && APP.battle._reviving) return;
            if (APP.battle) APP.battle._reviving = true;
            reviveModal.classList.remove('open');
            reviveModal.hidden = true;
            if (APP.battle) {
              APP.resultSettled = false;
              APP.battle.ended = false;
              APP.battle.isSettling = false;
              APP.battle.running = true;
              APP.battle._reviveUsed = true;
              APP.battle._reviving = false;
              APP.battle.triggerReviveProtocol();
              startBattleLoop(APP.battle);
            }
          };
          $('#btn-battle-revive-cancel').onclick = () => {
            reviveModal.classList.remove('open');
            reviveModal.hidden = true;
            renderDefeatResult();
          };
          return;
        }
      }
      renderDefeatResult();
      return;
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
        if (APP.isTransitioning) return;
        APP.isTransitioning = true;
        setTimeout(() => { APP.isTransitioning = false; }, 600);
        APP.startStage(chapter, nextStage);
      };
    } else {
      $('#btn-result-next').textContent = r.win ? '返回战役' : '重试';
      $('#btn-result-next').onclick = () => {
        if (APP.isTransitioning) return;
        APP.isTransitioning = true;
        setTimeout(() => { APP.isTransitioning = false; }, 600);
        if (r.win) { show('campaign'); UI.renderCampaign(st, APP.data); UI.openChapter(st, APP.data, chapter.id); }
        else { APP.startStage(chapter, stage); }
      };
    }
    $('#btn-result-back').onclick = () => {
      if (APP.isTransitioning) return;
      APP.isTransitioning = true;
      setTimeout(() => { APP.isTransitioning = false; }, 600);
      show('campaign');
      UI.renderCampaign(st, APP.data);
      UI.openChapter(st, APP.data, chapter.id);
    };

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

    // 戰鬥勝利凱旋特寫 (非跳過時展示，跳過時直接結算進入結果頁；支援點擊即時跳過)
    const victoryBox = $('#battle-victory-cutin');
    if (r.win && victoryBox && !r.skipped) {
      victoryBox.hidden = false;
      $('#victory-stars').textContent = UI.starsText(r.stars);
      const leader = (heroes && heroes[0]) || (APP.state && APP.state.squad && heroFor(APP.state.squad[0]));
      if (leader) $('#victory-name').textContent = leader.name + ' & 作战特勤队';
      let cutinDone = false;
      const finishCutin = () => {
        if (cutinDone) return;
        cutinDone = true;
        clearTimeout(cutinTimer);
        victoryBox.hidden = true;
        victoryBox.onclick = null;
        showSummary();
      };
      victoryBox.onclick = finishCutin;
      const cutinTimer = setTimeout(finishCutin, 2200);
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
    let finished = false;
    const finish = () => {
      if (finished) return;
      finished = true;
      clearTimeout(safetyTimer);
      try { video.pause(); } catch (_) {}
      overlay.hidden = true;
      video.onended = null;
      video.onerror = null;
      if (skipBtn) skipBtn.onclick = null;
      if (onComplete) onComplete();
    };

    const safetyTimer = setTimeout(finish, 8000);
    video.onended = finish;
    video.onerror = finish;
    if (skipBtn) skipBtn.onclick = finish;
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

  APP.doPull = async function (count) {
    if (APP._pulling) return;
    const st = APP.state;
    const g = APP.data.gacha;
    const needKeys = count === 10 ? g.costTen.keys : g.costSingle.keys;
    const needGold = count === 10 ? g.costTen.gold : g.costSingle.gold;
    const useKeys = st.keys >= needKeys;
    if (useKeys) {
      st.keys -= needKeys;
      if (LGSave.recordTransaction) LGSave.recordTransaction(st, 'keys', -needKeys, 'gacha_pull:' + count);
    } else if (st.gold >= needGold) {
      APP._pulling = true;
      const ok = await confirmDialog(
        '契约钥匙不足',
        `当前契约钥匙不足 ${needKeys} 把（剩余 ${st.keys}），是否消耗 ${needGold.toLocaleString()} 金币进行${count === 10 ? '十连' : '单次'}招募？`,
        '消耗金币招募'
      );
      APP._pulling = false;
      if (!ok) return;
      if (st.gold < needGold) {
        toast('金币不足');
        return;
      }
      st.gold -= needGold;
      if (LGSave.recordTransaction) LGSave.recordTransaction(st, 'gold', -needGold, 'gacha_pull:' + count);
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
      else if (k === 'energy') st.energy = (st.energy || 0) + v;
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
    motion.addEventListener('change', () => {
      st.settings.reduceMotion = motion.checked;
      const sys = typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      document.documentElement.classList.toggle('reduce-motion', sys || motion.checked);
      save();
    });
    adult.onchange = () => {
      st.settings.adult = adult.checked;
      if (!adult.checked && st.hubOutfit === 'nude') st.hubOutfit = 'lega';
      save();
      if (window.SlotsUI && SlotsUI.syncMascotLabels) SlotsUI.syncMascotLabels(false);
    };
    const btnQaMax = $('#btn-qa-max');
    if (btnQaMax) {
      btnQaMax.onclick = () => {
        LGSave.applyQAFullTestResources(st, APP.data.heroes, APP.data.equipment);
        save();
        UI.renderHud(st);
        if (APP.screen === 'hub') UI.renderHub(st, APP.data);
        toast('🎉 全 ' + (APP.data.heroes ? APP.data.heroes.length : 62) + ' 名特勤英雄已解锁！金币/钥匙/体力/碎片已全部拉满！');
      };
    }
    $('#btn-export').onclick = async () => {
      const text = LGSave.exportText(APP.state);
      let copied = false;
      try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          await navigator.clipboard.writeText(text);
          copied = true;
        }
      } catch (e) { /* fall through to in-page modal */ }
      await saveTextModal({
        title: '导出存档',
        hint: copied ? '已尝试复制到剪贴板，您也可在下方全选复制备份文本：' : '请复制下方 JSON 存档备份文本：',
        initialValue: text,
        readOnly: true,
        okLabel: '复制文本',
        cancelLabel: '关闭',
        onConfirm: (ta) => {
          try {
            ta.select();
            if (navigator.clipboard && navigator.clipboard.writeText) {
              navigator.clipboard.writeText(ta.value).catch(() => {});
            } else {
              document.execCommand('copy');
            }
            toast('存档已复制');
          } catch (_) {
            toast('请长按选中框内文本复制');
          }
          return false;
        },
      });
    };
    $('#btn-import').onclick = async () => {
      let clip = '';
      try {
        if (navigator.clipboard && navigator.clipboard.readText) {
          const rawClip = await navigator.clipboard.readText();
          if (rawClip && rawClip.trim().startsWith('{')) clip = rawClip.trim();
        }
      } catch (e) { /* ignore clipboard read errors in WKWebView */ }
      const text = await saveTextModal({
        title: '导入存档',
        hint: '请粘贴导出的 JSON 存档文本并点击「确认导入」：',
        initialValue: clip,
        placeholder: '{"v":2,"wallet":{...}}',
        readOnly: false,
        okLabel: '确认导入',
        cancelLabel: '取消',
      });
      if (!text || !text.trim()) return;
      try {
        const next = LGSave.importText(text.trim());
        APP.state = next;
        LGSave.persist(next);
        if (window.SlotsUI && SlotsUI.syncFromState) SlotsUI.syncFromState(APP.state);
        bindSettings();
        UI.renderHub(APP.state, APP.data);
        toast('存档已成功导入');
      } catch (e) { toast('导入失败：' + e.message); }
    };
    $('#btn-reset').onclick = async () => {
      const ok = await confirmDialog('重置存档', '这会清空全部本机进度与养成数据，且无法撤销。', '重置');
      if (!ok) return;
      APP.state = LGSave.reset();
      if (window.SlotsUI && SlotsUI.syncFromState) SlotsUI.syncFromState(APP.state);
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
    $('#btn-cg').onclick = () => UI.renderCg(APP.data.videos, APP.data.cgs);
    $('#btn-cg-close').onclick = () => $('#cg-sheet').classList.remove('open');
    $('#cg-sheet').addEventListener('click', (e) => { if (e.target.id === 'cg-sheet') $('#cg-sheet').classList.remove('open'); });
    $('#btn-pull1').onclick = () => APP.doPull(1);
    $('#btn-pull10').onclick = () => APP.doPull(10);

    // story controls (dynamically route between IntroTimeline and standard APP.story)
    $('#screen-story').addEventListener('click', (e) => {
      if (e.target.closest('button')) return;
      if (window.IntroTimeline && IntroTimeline.active) {
        IntroTimeline.handleTap();
        return;
      }
      advanceStory();
    });
    $('#btn-story-skip').onclick = (e) => {
      if (e) e.stopPropagation();
      if (window.IntroTimeline && IntroTimeline.active) {
        IntroTimeline.skip();
        return;
      }
      finishStory();
    };
    $('#btn-story-auto').onclick = (e) => {
      if (e) e.stopPropagation();
      if (window.IntroTimeline && IntroTimeline.active) {
        IntroTimeline.toggleAuto(e.currentTarget);
        return;
      }
      const s = APP.story;
      if (!s) return;
      s.auto = !s.auto;
      e.currentTarget.setAttribute('aria-pressed', String(s.auto));
      e.currentTarget.textContent = s.auto ? '自动播放中' : '自动播放';
      if (s.auto) scheduleAuto(); else clearTimeout(s.timer);
    };
    document.addEventListener('keydown', (e) => {
      if (APP.screen === 'story' && (e.key === ' ' || e.key === 'Enter')) {
        e.preventDefault();
        if (window.IntroTimeline && IntroTimeline.active) IntroTimeline.handleTap();
        else advanceStory();
      }
      if (e.key === 'Escape') { UI.closeDetail(); closeReveal(); $('#mission-sheet').classList.remove('open'); $('#stage-action-sheet').classList.remove('open'); $('#equip-picker-sheet').classList.remove('open'); }
    });
  }

  function finishBoot() {
    regenEnergy();
    if (window.Missions) Missions.checkDailyReset(APP.state, APP.data && APP.data.missions);
    const params = new URLSearchParams(window.location.search);
    const heroParam = params.get('hero');
    if (heroParam) {
      APP.state.squad = [heroParam];
    }
    UI.setProgress(1);
    UI.renderHub(APP.state, APP.data);
    if (window.__AUTO_SLOTS__) {
      show('slots');
      if (window.SlotsUI) {
        SlotsUI.render();
      }
    } else {
      show('hub');
    }
    if ($('#hub-art') && $('#hub-art').decode) $('#hub-art').decode().catch(() => {});
  }

  async function boot() {
    APP.state = LGSave.load();
    UI.setProgress(0.04);
    try {
      APP.data = await loadData();
      // QA Auto-Boost: Strictly isolated to explicit Debug/Test harness flag.
      // Release builds must never auto-grant max gold, all heroes/equips, or force adult mode.
      const isQAAllowed = (typeof window !== 'undefined' && (
        window.__AURORA_QA_BOOST__ === true ||
        (window.location && window.location.search && window.location.search.includes('qa=1'))
      ));
      if (isQAAllowed) {
        LGSave.applyQAFullTestResources(APP.state, APP.data.heroes, APP.data.equipment);
        save();
      }
    } catch (e) {
      $('#boot-note').textContent = '载入失败：' + e.message;
      return;
    }
    UI.setProgress(0.92);
    initStages();
    if (window.SlotsUI) SlotsUI.init().catch((e) => console.warn('Slots init failed:', e));
    $('#title-art').style.backgroundImage = 'url(assets/bg/title_rea.webp)';
    const hasSave = Object.keys(APP.state.cleared || {}).length > 0 || (APP.state.owned && APP.state.owned.length > LGSave.STARTERS.length);
    $('#btn-start').textContent = hasSave ? '进入游戏' : '开始游戏';
    $('#btn-continue').hidden = !hasSave;
    const syncReduceMotion = () => {
      const sys = typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      const app = !!(APP.state && APP.state.settings && APP.state.settings.reduceMotion);
      document.documentElement.classList.toggle('reduce-motion', sys || app);
    };
    syncReduceMotion();
    if (typeof window !== 'undefined' && window.matchMedia) {
      try {
        window.matchMedia('(prefers-reduced-motion: reduce)').addEventListener('change', syncReduceMotion);
      } catch (e) {}
    }
    LGAudio.setVolumes(APP.state.settings.bgm, APP.state.settings.sfx);
    UI.setProgress(1);
    show('title');
    bindNav();
    if (window.location.hash === '#battle' || window.location.search.includes('battle')) {
      finishBoot();
      setTimeout(() => {
        if (APP.data && APP.data.chapters && APP.data.chapters[0]) {
          const chap = APP.data.chapters[0];
          const stg = chap.stages[0];
          runBattle(chap, stg);
        }
      }, 350);
    } else if (window.location.hash === '#slots' || window.location.search.includes('slots') || window.__AUTO_SLOTS__) {
      finishBoot();
      setTimeout(() => {
        show('slots');
        if (window.SlotsUI) {
          SlotsUI.render();
          if (SlotsUI.initMascot) SlotsUI.initMascot();
        }
      }, 350);
    }
  }

  boot();
})();
