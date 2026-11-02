/* Daily missions and achievement tracking service. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.Missions = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  function checkDailyReset(state) {
    if (!state.missions) state.missions = { daily: {}, achievements: {}, lastDailyReset: 0 };
    const now = new Date();
    const todayMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();

    if (state.missions.lastDailyReset < todayMidnight) {
      state.missions.daily = {};
      state.missions.lastDailyReset = todayMidnight;
      // Trigger daily login mission automatically
      track(state, null, 'login', 1);
    }
  }

  function track(state, missionsCatalog, type, amount, meta) {
    if (!state || !state.missions) return;
    amount = amount || 1;
    meta = meta || {};

    // Track daily missions
    if (missionsCatalog && missionsCatalog.daily) {
      missionsCatalog.daily.forEach((m) => {
        if (m.type === type) {
          if (!state.missions.daily[m.id]) {
            state.missions.daily[m.id] = { progress: 0, claimed: false };
          }
          const cur = state.missions.daily[m.id];
          if (!cur.claimed) {
            cur.progress = Math.min(m.target, (cur.progress || 0) + amount);
          }
        }
      });
    }

    // Track achievements
    if (missionsCatalog && missionsCatalog.achievements) {
      missionsCatalog.achievements.forEach((ach) => {
        if (!state.missions.achievements[ach.id]) {
          state.missions.achievements[ach.id] = { progress: 0, claimed: false };
        }
        const cur = state.missions.achievements[ach.id];
        if (cur.claimed) return;

        if (ach.type === type) {
          cur.progress = Math.min(ach.target, (cur.progress || 0) + amount);
        } else if (ach.type === 'chapter_clear' && type === 'chapter_clear') {
          if (meta.chapterId === ach.chapterId) cur.progress = 1;
        } else if (ach.type === 'total_stars') {
          const totalStars = Object.values(state.stars || {}).reduce((s, v) => s + (v || 0), 0);
          cur.progress = Math.min(ach.target, totalStars);
        } else if (ach.type === 'collect_count') {
          cur.progress = Math.min(ach.target, (state.owned || []).length);
        } else if (ach.type === 'hero_level') {
          let maxLvl = 1;
          for (const k in state.roster || {}) {
            if (state.roster[k].level > maxLvl) maxLvl = state.roster[k].level;
          }
          cur.progress = Math.min(ach.target, maxLvl);
        } else if (ach.type === 'star_break' && type === 'star_break') {
          cur.progress = Math.min(ach.target, (cur.progress || 0) + amount);
        } else if (ach.type === 'pulls_count') {
          cur.progress = Math.min(ach.target, state.pulls || 0);
        }
      });
    }
  }

  function getList(state, catalog, category) {
    checkDailyReset(state);
    const defs = (catalog && catalog[category]) || [];
    const store = (category === 'daily' ? state.missions.daily : state.missions.achievements) || {};

    return defs.map((m) => {
      let rec = store[m.id] || { progress: 0, claimed: false };
      let prog = rec.progress || 0;

      // Auto update dynamic metrics
      if (m.type === 'total_stars') {
        prog = Object.values(state.stars || {}).reduce((s, v) => s + (v || 0), 0);
      } else if (m.type === 'collect_count') {
        prog = (state.owned || []).length;
      } else if (m.type === 'hero_level') {
        let maxLvl = 1;
        for (const k in state.roster || {}) {
          if (state.roster[k].level > maxLvl) maxLvl = state.roster[k].level;
        }
        prog = maxLvl;
      } else if (m.type === 'pulls_count') {
        prog = state.pulls || 0;
      }

      const done = prog >= m.target;
      return {
        id: m.id,
        title: m.title,
        desc: m.desc,
        progress: Math.min(m.target, prog),
        target: m.target,
        done,
        claimed: !!rec.claimed,
        rewards: m.rewards,
        category,
      };
    });
  }

  function claimReward(state, catalog, category, missionId) {
    const list = getList(state, catalog, category);
    const item = list.find((x) => x.id === missionId);
    if (!item) return { ok: false, msg: '任务不存在' };
    if (!item.done) return { ok: false, msg: '任务尚未完成' };
    if (item.claimed) return { ok: false, msg: '奖励已领取' };

    const store = category === 'daily' ? state.missions.daily : state.missions.achievements;
    if (!store[missionId]) store[missionId] = { progress: item.progress, claimed: false };
    store[missionId].claimed = true;

    // Grant rewards
    const granted = [];
    if (item.rewards) {
      for (const [k, v] of Object.entries(item.rewards)) {
        if (k === 'gold') {
          state.gold = (state.gold || 0) + v;
          granted.push(v + ' 金币');
        } else if (k === 'keys') {
          state.keys = (state.keys || 0) + v;
          granted.push(v + ' 契约钥匙');
        } else if (k === 'energy') {
          state.energy = Math.min(100, (state.energy || 0) + v);
          granted.push(v + ' 点行动力');
        } else if (k === 'shards') {
          state.shards = (state.shards || 0) + v;
          granted.push(v + ' 突破碎片');
        }
        if (typeof window !== 'undefined' && window.LGSave && window.LGSave.recordTransaction) {
          window.LGSave.recordTransaction(state, k, v, 'mission_claim:' + missionId);
        }
      }
    }

    if (typeof window !== 'undefined' && window.LGSave) window.LGSave.syncMirrors(state);
    return { ok: true, granted: granted.join('、') };
  }

  function hasUnclaimed(state, catalog) {
    if (!state || !catalog) return false;
    checkDailyReset(state);
    const daily = getList(state, catalog, 'daily');
    const ach = getList(state, catalog, 'achievements');
    return daily.some((x) => x.done && !x.claimed) || ach.some((x) => x.done && !x.claimed);
  }

  return {
    checkDailyReset,
    track,
    getList,
    claimReward,
    hasUnclaimed,
  };
});
