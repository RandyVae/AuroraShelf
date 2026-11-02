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
    attack: [A + 'tutorial/kitty_attack/kitty_attack.m4a', A + 'tutorial/regina_attack/regina_attack.m4a', A + 'main/bonita_attack/bonita_attack.m4a'],
    hit: [A + 'tutorial/vo_fight_hit_new/vo_fight_hit_new.m4a'],
    skill: [A + 'tutorial_next/skill_damage/skill_damage.m4a', A + 'main/skill_shock/skill_shock.m4a'],
    buff: [A + 'tutorial_next/skill_buff/skill_buff.m4a'],
    stun: [A + 'main/skill_stun/skill_stun.m4a'],
    shield: [A + 'main/skill_block_hit/skill_block_hit.m4a'],
    heal: [A + 'tutorial_next/fight_health_up/fight_health_up.m4a'],
    win: [A + 'tutorial/fight_win/fight_win.m4a'],
    lose: [A + 'main/fight_lose/fight_lose.m4a'],
    collect: [A + 'Disable/vo_collect_epic_suc/vo_collect_epic_suc.m4a', A + 'Disable/vo_collect_legendary_suc/vo_collect_legendary_suc.m4a'],
    card: [A + 'tutorial/fight_card_get/fight_card_get.m4a'],
    click: [A + 'tutorial/fight_flash/fight_flash.m4a'],
  };

  function AudioManager() {
    this.bgmEl = null;
    this.current = null;
    this.sfxVolume = 0.8;
    this.bgmVolume = 0.55;
    this.unlocked = false;
    this.pool = new Map();
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
    if (this.current) this.playBgm(this.current, true);
  };

  AudioManager.prototype.setVolumes = function (bgm, sfx) {
    this.bgmVolume = Math.max(0, Math.min(1, bgm));
    this.sfxVolume = Math.max(0, Math.min(1, sfx));
    if (this.bgmEl) this.bgmEl.volume = this.bgmVolume;
  };

  AudioManager.prototype.playBgm = function (name, force) {
    const src = BGM[name];
    if (!src) return;
    this.current = name;
    if (!this.bgmEl || this.bgmEl.dataset.src === src) return;
    this.bgmEl.dataset.src = src;
    this.bgmEl.src = src;
    this.bgmEl.volume = this.bgmVolume;
    const p = this.bgmEl.play();
    if (p && p.catch) p.catch(() => {});
  };

  AudioManager.prototype.playSfx = function (name) {
    const list = SFX[name];
    if (!list || !list.length) return;
    if (!this.sfxVolume) return;
    const src = list[Math.floor(Math.random() * list.length)];
    try {
      const el = new Audio(src);
      el.volume = this.sfxVolume;
      el.play().catch(() => {});
    } catch (e) { /* ignore */ }
  };

  AudioManager.prototype.playVoice = function (path) {
    if (!path) return;
    try {
      const el = new Audio(path);
      el.volume = Math.max(0.5, this.sfxVolume);
      el.play().catch(() => {});
    } catch (e) { /* ignore */ }
  };

  window.LGAudio = new AudioManager();
})();
