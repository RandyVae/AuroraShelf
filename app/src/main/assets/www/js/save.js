/* Local save state with Schema v2, migrations, atomic backup, and ledger.
 * No network, no server; all state persists in localStorage / AndroidSave bridge.
 */
(function () {
  'use strict';

  const KEY = 'lg-local-edition-v2';
  const OLD_KEY = 'lg-local-edition-v1';
  const BACKUP_KEY = 'lg-local-edition-v2-backup';
  const STARTERS = ['kitty', 'regina', 'mitsuki'];

  function createHeroInstance(heroKey, overrides) {
    return Object.assign({
      heroKey: heroKey,
      level: 1,
      xp: 0,
      stars: 1,
      skillLevel: 1,
      equips: { weapon: null, armor: null, core: null },
    }, overrides || {});
  }

  function defaults() {
    const now = Date.now();
    const starterRoster = {};
    STARTERS.forEach((k) => {
      starterRoster[k] = createHeroInstance(k, { level: 1, stars: 1 });
    });

    return {
      v: 2,
      profile: {
        id: 'cmd_' + now.toString(36),
        name: 'Ethan',
        createdAt: now,
        updatedAt: now,
      },
      wallet: {
        gold: 1200,
        keys: 8,
        energy: 100,
        shards: 20,
        energyAt: now,
      },
      // Convenience top-level references kept synced with wallet
      gold: 1200,
      keys: 8,
      energy: 100,
      shards: 20,

      owned: STARTERS.slice(),
      squad: STARTERS.slice(),
      roster: starterRoster,

      inventory: {
        weapons: ['wp_plasma_blade'],
        armors: ['ar_kevlar_suit'],
        cores: ['cr_overclock_chip'],
      },

      missions: {
        daily: {},
        achievements: {},
        lastDailyReset: 0,
      },

      progress: {
        stars: {},
        cleared: {},
        storySeen: {},
      },
      // Compatibility mirrors
      stars: {},
      cleared: {},
      storySeen: {},

      gacha: {
        pity: 0,
        pulls: 0,
        history: [],
      },
      pity: 0,
      pulls: 0,

      settings: {
        bgm: 0.55,
        sfx: 0.8,
        reduceMotion: false,
        adult: false,
        language: 'zh-Hans',
      },

      stats: {
        battles: 0,
        wins: 0,
        sweeps: 0,
        slotsSpins: 0,
        slotsWins: 0,
        energyAt: now,
      },

      slots: {
        version: 1,
        lastSeed: null,
        freeSpins: 0,
        freeSpinBet: null,
      },
      slotsVaultClaimed: false,
      isVip: false,

      transactions: [],
    };
  }

  const FORBIDDEN_KEYS = new Set(['__proto__', 'constructor', 'prototype']);

  function safeReviver(key, value) {
    if (FORBIDDEN_KEYS.has(key)) return undefined;
    return value;
  }

  function sanitizeKeyArray(arr) {
    if (!Array.isArray(arr)) return [];
    const seen = new Set();
    const out = [];
    for (let i = 0; i < arr.length; i++) {
      const k = arr[i];
      if (typeof k === 'string' && k.length > 0 && !FORBIDDEN_KEYS.has(k) && !seen.has(k)) {
        seen.add(k);
        out.push(k);
      }
    }
    return out;
  }

  function nativeBridge() {
    if (typeof window === 'undefined') return null;
    return window.AuroraSaveBridge || window.AndroidSave || null;
  }

  function readStorage(k) {
    const bridge = nativeBridge();
    if (bridge) {
      try {
        const v = typeof bridge.getKey === 'function'
          ? bridge.getKey(k)
          : (typeof bridge.get === 'function' && k === KEY ? bridge.get() : '');
        if (v) return v;
      } catch (e) { /* fall through */ }
    }
    try {
      return localStorage.getItem(k);
    } catch (e) {
      return null;
    }
  }

  function writeStorage(k, text) {
    const bridge = nativeBridge();
    if (bridge) {
      try {
        if (typeof bridge.setKey === 'function') bridge.setKey(k, text);
        else if (typeof bridge.set === 'function' && k === KEY) bridge.set(text);
      } catch (e) { /* fall through */ }
    }
    try {
      localStorage.setItem(k, text);
    } catch (e) {
      console.warn('Local storage write error:', e);
    }
  }

  function clearStorage(k) {
    const bridge = nativeBridge();
    if (bridge) {
      try {
        if (typeof bridge.clearKey === 'function') bridge.clearKey(k);
        else if (typeof bridge.clear === 'function' && k === KEY) bridge.clear();
      } catch (e) { /* fall through */ }
    }
    try {
      localStorage.removeItem(k);
    } catch (e) { /* ignore */ }
  }

  function syncMirrors(state) {
    if (!state.wallet) {
      state.wallet = {
        gold: state.gold || 0,
        keys: state.keys || 0,
        energy: state.energy !== undefined ? state.energy : 100,
        shards: state.shards || 0,
        energyAt: (state.stats && state.stats.energyAt) || Date.now(),
      };
    }
    // Bidirectional sync: prefer whichever mirror changed since last sync (top-level wins ties).
    const prevSnapshot = state._mirrorSnapshot || {};
    [
      ['gold', 99999999, 0],
      ['keys', 99999, 0],
      ['energy', 99999, 100],
      ['shards', 99999, 0],
    ].forEach(([field, maxVal, fallback]) => {
      const topVal = state[field];
      const walletVal = state.wallet[field];
      const hasPrev = Object.prototype.hasOwnProperty.call(prevSnapshot, field);
      let nextVal;
      if (hasPrev && topVal !== prevSnapshot[field] && walletVal === prevSnapshot[field]) {
        nextVal = topVal;
      } else if (hasPrev && walletVal !== prevSnapshot[field] && topVal === prevSnapshot[field]) {
        nextVal = walletVal;
      } else if (topVal !== undefined) {
        nextVal = topVal;
      } else if (walletVal !== undefined) {
        nextVal = walletVal;
      } else {
        nextVal = fallback;
      }
      const clamped = Math.max(0, Math.min(maxVal, Math.round(nextVal || 0)));
      state[field] = state.wallet[field] = clamped;
      prevSnapshot[field] = clamped;
    });
    Object.defineProperty(state, '_mirrorSnapshot', {
      value: prevSnapshot,
      writable: true,
      enumerable: false,
      configurable: true,
    });

    if (!state.progress) state.progress = {};
    state.stars = state.progress.stars = state.progress.stars || state.stars || {};
    state.cleared = state.progress.cleared = state.progress.cleared || state.cleared || {};
    state.storySeen = state.progress.storySeen = state.progress.storySeen || state.storySeen || {};

    if (!state.gacha) state.gacha = { pity: state.pity || 0, pulls: state.pulls || 0, history: [] };
    state.pity = state.gacha.pity = Math.max(0, Math.round(state.gacha.pity || state.pity || 0));
    state.pulls = state.gacha.pulls = Math.max(0, Math.round(state.gacha.pulls || state.pulls || 0));

    if (!state.roster || typeof state.roster !== 'object') state.roster = {};
    state.owned = sanitizeKeyArray(state.owned);
    if (!state.owned.length) state.owned = STARTERS.slice();

    // Ensure every owned hero has a roster instance
    state.owned.forEach((key) => {
      if (!state.roster[key]) {
        state.roster[key] = createHeroInstance(key);
      } else {
        const inst = state.roster[key];
        inst.heroKey = key;
        inst.level = Math.max(1, Math.min(30, Math.round(inst.level || 1)));
        inst.xp = Math.max(0, Math.round(inst.xp || 0));
        inst.stars = Math.max(1, Math.min(5, Math.round(inst.stars || 1)));
        inst.skillLevel = Math.max(1, Math.min(10, Math.round(inst.skillLevel || 1)));
        if (!inst.equips || typeof inst.equips !== 'object') {
          inst.equips = { weapon: null, armor: null, core: null };
        }
      }
    });

    state.squad = sanitizeKeyArray(state.squad).slice(0, 3);
    if (!state.squad.length) state.squad = state.owned.slice(0, 3);
    if (!state.inventory || typeof state.inventory !== 'object') {
      state.inventory = { weapons: ['wp_plasma_blade'], armors: ['ar_kevlar_suit'], cores: ['cr_overclock_chip'] };
    }
    state.inventory.weapons = Array.isArray(state.inventory.weapons) ? sanitizeKeyArray(state.inventory.weapons) : ['wp_plasma_blade'];
    state.inventory.armors = Array.isArray(state.inventory.armors) ? sanitizeKeyArray(state.inventory.armors) : ['ar_kevlar_suit'];
    state.inventory.cores = Array.isArray(state.inventory.cores) ? sanitizeKeyArray(state.inventory.cores) : ['cr_overclock_chip'];

    // Migrate any currently equipped items on roster heroes into state.inventory so existing saves keep what they wear
    const equipSlotMap = { weapon: 'weapons', armor: 'armors', core: 'cores' };
    Object.keys(state.roster || {}).forEach((hKey) => {
      const eq = state.roster[hKey] && state.roster[hKey].equips;
      if (!eq || typeof eq !== 'object') return;
      Object.entries(equipSlotMap).forEach(([slot, invKey]) => {
        const itemId = eq[slot];
        if (typeof itemId === 'string' && itemId.length > 0 && !FORBIDDEN_KEYS.has(itemId)) {
          if (!state.inventory[invKey].includes(itemId)) {
            state.inventory[invKey].push(itemId);
          }
        }
      });
    });

    if (!state.missions) state.missions = { daily: {}, achievements: {}, lastDailyReset: 0 };
    if (!state.missions.daily) state.missions.daily = {};
    if (!state.missions.achievements) state.missions.achievements = {};

    if (!state.transactions) state.transactions = [];
    if (!state.settings) state.settings = {};
    if (!state.stats) state.stats = {};
    state.stats.slotsSpins = Math.max(0, Math.round(state.stats.slotsSpins || 0));
    state.stats.slotsWins = Math.max(0, Math.round(state.stats.slotsWins || 0));
    if (!state.slots || typeof state.slots !== 'object') state.slots = { version: 1, lastSeed: null, freeSpins: 0, freeSpinBet: null };
    state.slots.version = 1;
    state.slots.freeSpins = Math.max(0, Math.min(999, Math.round(state.slots.freeSpins || 0)));
    state.slotsVaultClaimed = !!state.slotsVaultClaimed;
    state.isVip = !!(state.isVip || (state.slotsSession && state.slotsSession.isVip));

    return state;
  }

  function migrateV1toV2(v1Data) {
    const base = defaults();
    const state = Object.assign(base, v1Data);
    state.v = 2;
    state.settings = Object.assign(base.settings, v1Data.settings || {});
    state.stats = Object.assign(base.stats, v1Data.stats || {});
    state.stars = v1Data.stars || {};
    state.cleared = v1Data.cleared || {};
    state.storySeen = v1Data.storySeen || {};

    const clearedCount = Object.keys(state.cleared).length;
    const initialLvl = Math.max(1, Math.min(30, 1 + Math.floor(clearedCount / 2)));

    state.roster = {};
    (state.owned || STARTERS).forEach((k) => {
      state.roster[k] = createHeroInstance(k, { level: initialLvl, stars: 1 });
    });

    syncMirrors(state);
    return state;
  }

  function load() {
    let raw = readStorage(KEY);
    if (!raw) {
      // Check for v1 save to migrate
      const rawV1 = readStorage(OLD_KEY);
      if (rawV1) {
        try {
          const parsedV1 = JSON.parse(rawV1);
          const migrated = migrateV1toV2(parsedV1);
          persist(migrated);
          return migrated;
        } catch (e) {
          console.warn('v1 migration error, using defaults:', e);
        }
      }
      return defaults();
    }

    try {
      const parsed = JSON.parse(raw);
      if (parsed.v === 1 || !parsed.v) {
        const migrated = migrateV1toV2(parsed);
        persist(migrated);
        return migrated;
      }
      const base = defaults();
      const state = Object.assign(base, parsed);
      state.wallet = Object.assign(base.wallet, parsed.wallet || {});
      state.settings = Object.assign(base.settings, parsed.settings || {});
      state.stats = Object.assign(base.stats, parsed.stats || {});
      state.inventory = Object.assign(base.inventory, parsed.inventory || {});
      state.missions = Object.assign(base.missions, parsed.missions || {});
      syncMirrors(state);
      return state;
    } catch (e) {
      console.error('Failed to parse primary save, trying backup...', e);
      const backupRaw = readStorage(BACKUP_KEY);
      if (backupRaw) {
        try {
          const backupParsed = JSON.parse(backupRaw);
          return syncMirrors(Object.assign(defaults(), backupParsed));
        } catch (e2) {
          console.error('Backup corrupted too, returning defaults', e2);
        }
      }
      return defaults();
    }
  }

  function persist(state) {
    if (!state) return;
    syncMirrors(state);
    state.profile = state.profile || {};
    state.profile.updatedAt = Date.now();

    const json = JSON.stringify(state);
    // Preserve previous known-good snapshot from KEY into BACKUP_KEY BEFORE overwriting KEY
    try {
      const prevRaw = readStorage(KEY);
      if (prevRaw && typeof prevRaw === 'string') {
        const parsedPrev = JSON.parse(prevRaw, safeReviver);
        if (parsedPrev && typeof parsedPrev === 'object' && !Array.isArray(parsedPrev)) {
          writeStorage(BACKUP_KEY, prevRaw);
        }
      }
    } catch (e) { /* existing KEY unreadable or corrupt; keep BACKUP_KEY intact */ }

    // Write main
    writeStorage(KEY, json);
  }

  function recordTransaction(state, currency, delta, reason) {
    if (!state) return;
    const current = state[currency] || 0;
    const tx = {
      id: 'tx_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 6),
      time: Date.now(),
      currency,
      delta,
      balance: current,
      reason: reason || 'unknown',
    };
    if (!state.transactions) state.transactions = [];
    state.transactions.unshift(tx);
    while (state.transactions.length > 50) state.transactions.pop();
  }

  function exportText(state) {
    syncMirrors(state);
    return JSON.stringify(state, null, 2);
  }

  function importText(text) {
    if (!text || typeof text !== 'string') throw new Error('存档文本无效');
    if (text.length > 500000) throw new Error('存档大小超出安全限制');

    const parsed = JSON.parse(text, safeReviver);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('存档格式解析失败');
    if (parsed.owned !== undefined && !Array.isArray(parsed.owned)) throw new Error('存档特勤列表格式无效');
    if (parsed.squad !== undefined && !Array.isArray(parsed.squad)) throw new Error('存档编队格式无效');
    if (Array.isArray(parsed.owned)) parsed.owned = sanitizeKeyArray(parsed.owned);
    if (Array.isArray(parsed.squad)) parsed.squad = sanitizeKeyArray(parsed.squad);

    if (parsed.v === 1 || !parsed.v) {
      return migrateV1toV2(parsed);
    }
    const base = defaults();
    const merged = Object.assign(base, parsed);
    merged.wallet = Object.assign(base.wallet, parsed.wallet || {});
    merged.settings = Object.assign(base.settings, parsed.settings || {});
    merged.stats = Object.assign(base.stats, parsed.stats || {});
    merged.inventory = Object.assign(base.inventory, parsed.inventory || {});
    merged.missions = Object.assign(base.missions, parsed.missions || {});
    merged.slots = Object.assign(base.slots, parsed.slots || {});
    syncMirrors(merged);
    return merged;
  }

  function reset() {
    const st = defaults();
    persist(st);
    return st;
  }

  function applyQAFullTestResources(state, heroes, equipment) {
    if (!state) return state;
    state.gold = 99999999;
    state.keys = 99999;
    state.energy = 99999;
    state.shards = 99999;
    if (state.wallet) {
      state.wallet.gold = 99999999;
      state.wallet.keys = 99999;
      state.wallet.energy = 99999;
      state.wallet.shards = 99999;
    }
    // Unlock all heroes if heroes list provided
    if (Array.isArray(heroes) && heroes.length > 0) {
      state.owned = heroes.map((h) => h.key);
    } else if (!Array.isArray(state.owned) || state.owned.length < 5) {
      state.owned = ['kitty', 'regina', 'mitsuki', 'dominica', 'florence', 'esther', 'charlotte', 'alisa', 'valkyrie'];
    }
    state.roster = state.roster || {};
    state.owned.forEach((k) => {
      state.roster[k] = createHeroInstance(k, {
        level: 30,
        stars: 5,
        skillLevel: 10,
        xp: 0,
      });
    });
    if (!state.squad || state.squad.length < 3) {
      state.squad = state.owned.slice(0, 3);
    }

    // Unlock all 18 equipment items
    state.inventory = state.inventory || { weapons: [], armors: [], cores: [] };
    if (Array.isArray(equipment) && equipment.length > 0) {
      state.inventory.weapons = equipment.filter((i) => i.slot === 'weapon').map((i) => i.id);
      state.inventory.armors = equipment.filter((i) => i.slot === 'armor').map((i) => i.id);
      state.inventory.cores = equipment.filter((i) => i.slot === 'core').map((i) => i.id);
    } else {
      state.inventory.weapons = ['wp_plasma_blade', 'wp_arc_rifle', 'wp_goddess_judgement', 'wp_striker_blade', 'wp_nano_dagger', 'wp_quantum_cannon'];
      state.inventory.armors = ['ar_kevlar_suit', 'ar_nano_composite', 'ar_aegis_barrier', 'ar_striker_vest', 'ar_nano_armor', 'ar_quantum_cloak'];
      state.inventory.cores = ['cr_overclock_chip', 'cr_energy_matrix', 'cr_eternity_prism', 'cr_striker_core', 'cr_nano_matrix', 'cr_quantum_singularity'];
    }

    state.settings = state.settings || {};
    state.settings.adult = true;

    syncMirrors(state);
    return state;
  }

  window.LGSave = {
    KEY,
    BACKUP_KEY,
    defaults,
    defaultState: defaults,
    load,
    persist,
    exportText,
    importText,
    reset,
    clearStorage,
    nativeBridge,
    STARTERS,
    createHeroInstance,
    recordTransaction,
    syncMirrors,
    normalize: syncMirrors,
    applyQAFullTestResources,
  };
})();
