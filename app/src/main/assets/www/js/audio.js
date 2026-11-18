/* Original audio clips extracted from the shipped AssetBundles. Local files only. */
(function () {
  'use strict';
  const A = 'assets/audio/';
  const BGM = {
    title: A + 'main/bgm_blackcat/bgm_blackcat.m4a',
    hub: A + 'main/bgm_temptation/bgm_temptation.m4a',
    campaign: A + 'main/bgm_summerrain/bgm_summerrain.m4a',
    battle: A + 'tutorial/mus_fight1/mus_fight1.m4a',
    boss: A + 'main/mus_fight_2_final/mus_fight_2_final.m4a',
    story: A + 'main/bgm_crawl/bgm_crawl.m4a',
    gacha: A + 'gochi/bgm_gochi_main/bgm_gochi_main.m4a',
  };
  const SFX = {
    attack: [A + 'tutorial/kitty_attack/kitty_attack.m4a', A + 'main/regina_attack/regina_attack.m4a', A + 'main/bonita_attack/bonita_attack.m4a'],
    hit: [A + 'tutorial/vo_fight_hit_new/vo_fight_hit_new.m4a'],
    skill: [A + 'tutorial_next/skill_damage/skill_damage.m4a', A + 'main/skill_shock/skill_shock.m4a'],
    buff: [A + 'tutorial_next/skill_buff/skill_buff.m4a'],
    stun: [A + 'main/skill_stun/skill_stun.m4a'],
    shield: [A + 'main/skill_block_hit/skill_block_hit.m4a'],
    heal: [A + 'tutorial_next/fight_health_up/fight_health_up.m4a'],
    win: [A + 'tutorial/fight_win/fight_win.m4a'],
    lose: [A + 'main/fight_lose/fight_lose.m4a'],
    collect: [A + 'tutorial/fight_card_get/fight_card_get.m4a', A + 'tutorial/fight_win/fight_win.m4a'],
    card: [A + 'tutorial/fight_card_get/fight_card_get.m4a'],
    click: [A + 'tutorial/fight_flash/fight_flash.m4a'],
  };

  function AudioManager() {
    this.bgmEl = null;
    this.current = null;
    this.sfxVolume = 0.8;
    this.bgmVolume = 0.55;
    this.isMuted = false;
    this.appMuted = false;
    this.unlocked = false;
    this.pool = new Map();
    this.activeChannels = [];
  }

  AudioManager.prototype.unlock = function () {
    if (this.unlocked) return;
    this.unlocked = true;
    if (!this.bgmEl) {
      this.bgmEl = new Audio();
      this.bgmEl.loop = true;
      this.bgmEl.preload = 'auto';
      this.bgmEl.volume = 0;
    }
    if (this.current && !this.appMuted) this.playBgm(this.current, true);
  };

  AudioManager.prototype.setVolumes = function (bgm, sfx) {
    this.bgmVolume = Math.max(0, Math.min(1, bgm));
    this.sfxVolume = Math.max(0, Math.min(1, sfx));
    this.isMuted = this.appMuted || (this.bgmVolume === 0 && this.sfxVolume === 0);
    if (this.bgmEl) this.bgmEl.volume = this.appMuted ? 0 : this.bgmVolume;
  };

  AudioManager.prototype.pauseBgm = function () {
    this._bgmPaused = true;
    if (this.bgmEl) {
      try { this.bgmEl.pause(); } catch (e) {}
    }
  };

  AudioManager.prototype.resumeBgm = function () {
    this._bgmPaused = false;
    if (this.appMuted || this.isMuted || !this.bgmVolume) return;
    if (this.bgmEl && this.bgmEl.src && this.bgmEl.dataset && this.bgmEl.dataset.src) {
      this.bgmEl.volume = this.bgmVolume;
      const p = this.bgmEl.play();
      if (p && p.catch) p.catch(() => {});
    } else if (this.current) {
      this.playBgm(this.current, true);
    }
  };

  AudioManager.prototype.stopBgm = function () {
    if (this.bgmEl) {
      try { this.bgmEl.pause(); } catch (e) {}
    }
  };

  AudioManager.prototype.playBgm = function (name, force) {
    const src = BGM[name];
    if (!src) return;
    this.current = name;
    this._bgmPaused = false;
    if (this.appMuted) return;
    this.isMuted = (this.bgmVolume === 0 && this.sfxVolume === 0);
    if (!this.bgmEl || !this.bgmVolume) return;
    if (!force && this.bgmEl.dataset.src === src && !this.bgmEl.paused) return;
    this.bgmEl.dataset.src = src;
    this.bgmEl.src = src;
    this.bgmEl.volume = this.bgmVolume;
    const p = this.bgmEl.play();
    if (p && p.catch) p.catch(() => {});
  };

  AudioManager.prototype._acquireAudio = function (src) {
    let el = this.pool.get(src);
    if (el && (el.paused || el.ended)) {
      try { el.currentTime = 0; } catch (e) {}
      return el;
    }
    if (this.activeChannels.length >= 8) {
      const oldest = this.activeChannels.shift();
      if (oldest) {
        try { oldest.pause(); oldest.currentTime = 0; } catch (e) {}
        for (const [k, v] of this.pool.entries()) {
          if (v === oldest) this.pool.delete(k);
        }
        el = oldest;
        el.src = src;
        this.pool.set(src, el);
        this.activeChannels.push(el);
        return el;
      }
    }
    el = new Audio(src);
    this.pool.set(src, el);
    this.activeChannels.push(el);
    return el;
  };

  AudioManager.prototype.playSfx = function (name) {
    const resolved = name === 'tap' ? 'click' : name;
    const list = SFX[resolved];
    if (!list || !list.length) return;
    if (!this.sfxVolume || this.isMuted || this.appMuted) return;
    const src = list[Math.floor(Math.random() * list.length)];
    try {
      const el = this._acquireAudio(src);
      el.volume = this.sfxVolume;
      el.play().catch(() => {});
    } catch (e) { /* ignore */ }
  };

  AudioManager.prototype.playSe = function (name) {
    this.playSfx(name);
  };

  AudioManager.prototype.playVoice = function (path) {
    if (!path || !this.sfxVolume || this.isMuted || this.appMuted) return;
    const src = (path.startsWith(A) || path.startsWith('/') || /^https?:/i.test(path))
      ? path
      : (A + path);
    try {
      const el = this._acquireAudio(src);
      el.volume = this.sfxVolume;
      el.play().catch(() => {});
    } catch (e) { /* ignore */ }
  };

  window.LGAudio = new AudioManager();
})();
