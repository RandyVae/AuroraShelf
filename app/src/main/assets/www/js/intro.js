/* ==========================================================================
   IntroTimeline: 电影级战术序章叙事与分镜事件驱动引擎
   对齐 CASE_DEEP_AUDIT_2026-10-29 与 LUST_GODDESS_CARD_GAME_PRODUCTION_GUIDE v2.7
   1. 6段结构化事件链：
      scene_establish -> hero_wake -> identity_reveal -> ally_response -> context_objective -> story_to_battle
   2. 主角全屏大尺寸登场 (占视口高 65%~80%)
   3. 双角色协同 (Kitty 突击先锋 + Dominica 战术指挥)
   4. 严格结构化运行时审计日志 (id, start, complete, skipped, audio, vfx, assertion)
   5. 完整支持点按推进、自动播放、平滑跳过、静音与 Reduce Motion
   ========================================================================== */
(function () {
  'use strict';

  const DEFAULT_SEQUENCE = {
    id: 'ch1_prologue',
    chapterId: 1,
    stageId: '1-1',
    title: '第一章 · 霓虹雨夜：战术突入序章',
    totalDurationMs: 6800,
    events: [
      {
        id: 'scene_establish',
        name: '暗场建立与环境侦测',
        timeOffsetMs: 0,
        durationMs: 1400,
        background: 'assets/bg/ch1.webp',
        camera: { scale: 1.15, panX: 0, panY: -20, shake: false },
        tacticalPrompt: '【微弱通讯】生理指标检测中：脑波频率恢复，生物电特征同步。',
        audio: { bgm: 'story', sfx: 'tutorial/vo_fight_hit_new/vo_fight_hit_new.m4a' },
        dialogue: {
          speaker: '???',
          role: '未确认声音',
          text: '“……队长？听得到吗……队长！睁开眼睛，队长！”'
        }
      },
      {
        id: 'hero_wake',
        name: '主角唤醒与近景对焦',
        timeOffsetMs: 1400,
        durationMs: 1300,
        background: 'assets/bg/ch1.webp',
        camera: { scale: 1.10, panX: -15, panY: -10, shake: false },
        actors: [
          {
            key: 'kitty',
            name: '???',
            role: '战备呼唤',
            unit: 'kitty_player',
            type: 'spine',
            animation: 'idle',
            actionState: 'wake',
            size: { heightRatio: 0.72, yRatio: 0.05, xRatio: 0.40 },
            badge: '意识苏醒'
          }
        ],
        vfx: { darkEstablish: false, flash: true },
        tacticalPrompt: '【意识唤醒】先锋队长生命体征确认稳定，外部战术视界重新接入。',
        audio: { sfx: 'tutorial/breath_kitty_5/breath_kitty_5.m4a' },
        dialogue: {
          speaker: '???',
          role: '呼唤声',
          text: '“呼……生命体征稳定，瞳孔光反应正常。战术雷达重新上线！”'
        }
      },
      {
        id: 'identity_reveal',
        name: '身份揭示与大尺寸登场',
        timeOffsetMs: 2700,
        durationMs: 1400,
        background: 'assets/bg/ch1.webp',
        camera: { scale: 1.02, panX: -25, panY: 0, shake: false },
        actors: [
          {
            key: 'kitty',
            name: 'Kitty',
            role: '突击先锋',
            unit: 'kitty_player',
            type: 'spine',
            animation: 'idle',
            actionState: 'enter',
            size: { heightRatio: 0.76, yRatio: 0.04, xRatio: 0.38 },
            badge: '突击先锋',
            quote: '“我是 Kitty！突击队长，战力已完全激活！”'
          }
        ],
        vfx: { speedlines: true, heroAura: true, flash: true },
        tacticalPrompt: '【身份认证】代号：Kitty · 突击先锋。战刃充能 100%，随时准许自由交火！',
        audio: { sfx: 'tutorial/kitty_attack/kitty_attack.m4a' },
        dialogue: {
          speaker: 'Kitty',
          role: '突击先锋',
          text: '“指挥官，我是 Kitty！前哨防线已经被我们撕开，突击准备完毕！”'
        }
      },
      {
        id: 'ally_response',
        name: '双角色同框与指令协同',
        timeOffsetMs: 4100,
        durationMs: 1300,
        background: 'assets/bg/ch1.webp',
        camera: { scale: 1.0, panX: 0, panY: 0, shake: false },
        actors: [
          {
            key: 'kitty',
            name: 'Kitty',
            role: '突击先锋',
            unit: 'kitty_player',
            type: 'spine',
            animation: 'idle',
            size: { heightRatio: 0.74, yRatio: 0.04, xRatio: 0.32 },
            badge: '先锋战位'
          },
          {
            key: 'dominica',
            name: 'Dominica',
            role: '战术指挥',
            unit: 'dominica_player',
            type: 'portrait',
            artWeb: 'assets/heroes/dominica/lega.webp',
            size: { heightRatio: 0.65, yRatio: 0.08, xRatio: 0.82 },
            badge: '战术指挥'
          }
        ],
        vfx: { tacticalLink: true },
        tacticalPrompt: '【双角色协同】Kitty（先锋）与 Dominica（指挥）战术频道接通，阵线协同完毕。',
        audio: { sfx: 'tutorial/regina_attack/regina_attack.m4a' },
        dialogue: {
          speaker: 'Dominica',
          role: '战术指挥',
          text: '“战术链路已锁定！全员保持散开阵型，准许自由交火！”'
        }
      },
      {
        id: 'context_objective',
        name: '地点威胁与任务目标',
        timeOffsetMs: 5400,
        durationMs: 1000,
        background: 'assets/bg/battle.webp',
        camera: { scale: 1.08, panX: 25, panY: 0, shake: true },
        actors: [
          {
            key: 'zoe_enemy',
            name: '叛变特勤 Zoe',
            role: '敌方先锋',
            unit: 'zoe_enemy',
            type: 'spine',
            animation: 'idle',
            size: { heightRatio: 0.65, yRatio: 0.12, xRatio: 0.72 },
            badge: '特勤警报',
            targetLock: true
          }
        ],
        vfx: { targetLock: true, speedlines: true, reticle: true },
        tacticalPrompt: '【作战目标】肃清 1-1 区段全部敌军单位，建立前哨补给站。',
        audio: { sfx: 'tutorial_next/skill_damage/skill_damage.m4a' },
        dialogue: {
          speaker: '战术无线电',
          role: '任务通报',
          text: '【首要战术目标】击溃拦截型无人机与先遣守军，确保以太核心据点不失！'
        }
      },
      {
        id: 'story_to_battle',
        name: '剧情至三路战术战斗转场',
        timeOffsetMs: 6400,
        durationMs: 400,
        background: 'assets/bg/battle.webp',
        camera: { scale: 1.0, panX: 0, panY: 0, zoomIn: true },
        actors: [],
        vfx: { cutToBattle: true },
        tacticalPrompt: '【转场进入】剧情结束，无缝切入三路战术战斗盘面！',
        audio: { bgm: 'battle' },
        dialogue: null
      }
    ]
  };

  const IntroTimeline = {
    sequences: { ch1_prologue: DEFAULT_SEQUENCE },
    active: false,
    currentSeq: null,
    eventIndex: 0,
    startTime: 0,
    eventTimer: null,
    typingTimer: null,
    spineStage: null,
    enemySpineStage: null,
    autoPlay: true,
    reduceMotion: false,
    onComplete: null,

    init() {
      const loadSeq = (typeof window.safeFetchJson === 'function')
        ? window.safeFetchJson('data/intro-sequences.json')
        : fetch('data/intro-sequences.json').then((r) => ((r.ok || r.status === 0) ? r.json() : null));
      loadSeq
        .then((data) => {
          if (data && data.sequences) {
            Object.assign(this.sequences, data.sequences);
          }
        })
        .catch(() => {});
    },

    play(sequenceId, opts) {
      opts = opts || {};
      const seq = this.sequences[sequenceId] || DEFAULT_SEQUENCE;
      this.currentSeq = seq;
      this.active = true;
      this.eventIndex = 0;
      this.autoPlay = opts.auto !== undefined ? opts.auto : true;
      this.reduceMotion = !!opts.reduceMotion;
      this.onComplete = opts.onComplete || null;

      window.PrologueLog = window.PrologueLog || [];
      window.PrologueLog.length = 0;

      const scr = document.getElementById('screen-story');
      if (scr) scr.classList.add('prologue-mode');

      this.setupDom();
      this.startEvent(0);
    },

    setupDom() {
      const scr = document.getElementById('screen-story');
      if (!scr) return;

      const autoBtn = document.getElementById('btn-story-auto');
      if (autoBtn) {
        autoBtn.textContent = this.autoPlay ? '自动播放 (开)' : '自动播放 (关)';
        autoBtn.setAttribute('aria-pressed', this.autoPlay ? 'true' : 'false');
        autoBtn.onclick = (e) => {
          e.stopPropagation();
          this.autoPlay = !this.autoPlay;
          autoBtn.textContent = this.autoPlay ? '自动播放 (开)' : '自动播放 (关)';
          autoBtn.setAttribute('aria-pressed', this.autoPlay ? 'true' : 'false');
          if (this.autoPlay) this.scheduleNext();
        };
      }

      const skipBtn = document.getElementById('btn-story-skip');
      if (skipBtn) {
        skipBtn.onclick = (e) => {
          e.stopPropagation();
          this.skip();
        };
      }

      scr.onclick = () => {
        if (!this.active) return;
        const textNode = document.getElementById('story-text');
        const curEvent = this.currentSeq.events[this.eventIndex];
        const full = (curEvent && curEvent.dialogue && curEvent.dialogue.text) || '';
        if (textNode && textNode.textContent.length < full.length) {
          clearTimeout(this.typingTimer);
          textNode.textContent = full;
          return;
        }
        this.nextEvent();
      };
    },

    startEvent(idx) {
      if (!this.active || !this.currentSeq) return;
      if (idx >= this.currentSeq.events.length) {
        this.finish();
        return;
      }
      this.eventIndex = idx;
      const ev = this.currentSeq.events[idx];
      const now = performance.now();

      // 记录机器可读事件日志 (id, start, complete, skipped, audio, vfx, assertion)
      const logEntry = {
        id: ev.id,
        name: ev.name,
        index: idx + 1,
        total: this.currentSeq.events.length,
        startTimeMs: now,
        durationMs: ev.durationMs,
        status: 'active',
        audio: ev.audio ? (ev.audio.sfx || ev.audio.bgm) : null,
        vfx: ev.vfx ? ev.vfx.type || Object.keys(ev.vfx).join('+') : null,
        skipped: false,
        assertion: {
          hasActors: !!(ev.actors && ev.actors.length),
          actorCount: (ev.actors && ev.actors.length) || 0,
          dialogueValid: !!(ev.dialogue && ev.dialogue.text),
          heightRatioTarget: (ev.actors && ev.actors[0] && ev.actors[0].size && ev.actors[0].size.heightRatio) || 0
        }
      };
      window.PrologueLog.push(logEntry);
      console.log(`[Narrative Timeline] [${idx + 1}/${this.currentSeq.events.length}] ${ev.id}: ${ev.name}`);

      // 1. 背景与镜头
      const bg = document.getElementById('story-bg');
      if (bg) {
        if (ev.background) bg.style.backgroundImage = `url(${ev.background})`;
        if (!this.reduceMotion && ev.camera) {
          const s = ev.camera.scale || 1.0;
          const px = ev.camera.panX || 0;
          const py = ev.camera.panY || 0;
          bg.style.transform = `scale(${s}) translate(${px}px, ${py}px)`;
          bg.classList.toggle('shake', !!ev.camera.shake);
        } else {
          bg.style.transform = 'scale(1.0) translate(0px, 0px)';
          bg.classList.remove('shake');
        }
      }

      // 暗场与遮罩支持 (scene_establish)
      const shade = document.querySelector('.story-shade');
      if (shade) {
        if (ev.id === 'scene_establish' || (ev.vfx && ev.vfx.darkEstablish)) {
          shade.style.background = 'radial-gradient(circle at center, rgba(5,7,15,0.92) 0%, rgba(2,3,8,0.98) 100%)';
        } else {
          shade.style.background = '';
        }
      }

      // 2. 视觉特效与转场
      this.updateVfx(ev.vfx);

      // 3. 顶栏进度与提示条
      const chip = document.getElementById('story-event-chip');
      if (chip) chip.textContent = `[${idx + 1}/${this.currentSeq.events.length}] ${ev.id.toUpperCase()}`;

      const fill = document.getElementById('story-timeline-fill');
      if (fill) fill.style.width = `${((idx + 1) / this.currentSeq.events.length) * 100}%`;

      const promptEl = document.getElementById('story-tactical-prompt');
      if (promptEl) {
        promptEl.textContent = ev.tacticalPrompt || '';
        promptEl.classList.remove('flash');
        void promptEl.offsetWidth;
        promptEl.classList.add('flash');
      }

      // 4. 角色与骨骼演员驱动
      this.updateActors(ev);

      // 5. 音频事件
      if (ev.audio) {
        if (ev.audio.bgm && window.LGAudio) window.LGAudio.playBgm(ev.audio.bgm);
        if (ev.audio.sfx && window.LGAudio) {
          if (ev.audio.sfx.includes('breath')) {
            window.LGAudio.playVoice(ev.audio.sfx);
          } else {
            window.LGAudio.playSfx('attack');
          }
        }
      }

      // 6. 对白与身份
      this.renderDialogue(ev.dialogue);

      // 7. 自动推进调度
      clearTimeout(this.eventTimer);
      if (this.autoPlay) {
        this.eventTimer = setTimeout(() => {
          logEntry.status = 'completed';
          logEntry.elapsedMs = performance.now() - logEntry.startTimeMs;
          this.nextEvent();
        }, ev.durationMs || 1500);
      }
    },

    nextEvent() {
      clearTimeout(this.eventTimer);
      clearTimeout(this.typingTimer);
      if (this.eventIndex + 1 < this.currentSeq.events.length) {
        const cur = window.PrologueLog[window.PrologueLog.length - 1];
        if (cur && cur.status === 'active') {
          cur.status = 'completed';
          cur.elapsedMs = performance.now() - cur.startTimeMs;
        }
        this.startEvent(this.eventIndex + 1);
      } else {
        this.finish();
      }
    },

    updateActors(ev) {
      const actors = ev.actors || [];
      const spineCanvas = document.getElementById('story-spine-canvas');
      const suppActor = document.getElementById('story-supp-actor');
      const enemyActor = document.getElementById('story-enemy-actor');
      const fallbackArt = document.getElementById('story-art');
      const enemyArt = document.getElementById('story-enemy-art');
      const enemyCanvas = document.getElementById('story-enemy-canvas');

      // 主角 Spine 或高清立绘 (Kitty / Regina 等) 占屏 65%~80% 视口
      const mainHero = actors.find((a) => !a.isEnemy && !a.isSupp && a.role !== '敌方' && a.role !== '敌军' && a.role !== '沙海领主' && a.role !== '终局审判者');
      if (mainHero && !this.reduceMotion && window.SpineStage && spineCanvas && mainHero.unit) {
        spineCanvas.hidden = false;
        if (fallbackArt) fallbackArt.hidden = true;
        if (!this.spineStage) {
          const ratio = (mainHero.size && mainHero.size.heightRatio) || 0.76;
          this.spineStage = new SpineStage(spineCanvas, {
            heightRatio: ratio,
            yRatio: (mainHero.size && mainHero.size.yRatio) || 0.04,
            xRatio: (mainHero.size && mainHero.size.xRatio) || 0.38
          });
        }
        this.spineStage.show(mainHero.unit, {
          flip: false,
          anim: mainHero.animation || 'idle'
        });
      } else if (mainHero && fallbackArt) {
        if (spineCanvas) spineCanvas.hidden = true;
        fallbackArt.hidden = false;
        fallbackArt.src = mainHero.artWeb || ('assets/heroes/' + mainHero.key + '/lega.webp');
      } else {
        if (spineCanvas) spineCanvas.hidden = true;
        if (fallbackArt) fallbackArt.hidden = true;
      }

      // 双角色副指挥 (Dominica / 支援角色)
      const supp = actors.find((a) => a.isSupp || a.key === 'dominica');
      if (supp && suppActor) {
        suppActor.hidden = false;
        const img = suppActor.querySelector('.supp-art');
        if (img) img.src = supp.artWeb || 'assets/heroes/dominica/lega.webp';
        const badge = suppActor.querySelector('.supp-badge');
        if (badge) badge.textContent = supp.badge || '战术协同';
      } else if (suppActor) {
        suppActor.hidden = true;
      }

      // 敌方单位展示 (Zoe / Lucretia / Lilith)
      const enemy = actors.find((a) => a.isEnemy || a.unit === 'zoe_enemy' || a.key === 'zoe_enemy' || a.key === 'lucretia' || a.key === 'lilith' || a.role === '沙海领主' || a.role === '终局审判者');
      if (enemy && enemyActor) {
        enemyActor.hidden = false;
        const badge = enemyActor.querySelector('.enemy-badge');
        if (badge) badge.textContent = enemy.badge || '敌军警报';

        if (enemy.unit === 'zoe_enemy' && !this.reduceMotion && window.SpineStage && enemyCanvas) {
          if (enemyArt) enemyArt.hidden = true;
          enemyCanvas.hidden = false;
          if (!this.enemySpineStage) {
            this.enemySpineStage = new SpineStage(enemyCanvas, {
              heightRatio: (enemy.size && enemy.size.heightRatio) || 0.58,
              yRatio: (enemy.size && enemy.size.yRatio) || 0.15,
              xRatio: (enemy.size && enemy.size.xRatio) || 0.72
            });
          }
          this.enemySpineStage.show(enemy.unit, { flip: true, anim: 'idle' });
        } else if (enemyArt) {
          if (enemyCanvas) enemyCanvas.hidden = true;
          enemyArt.hidden = false;
          enemyArt.src = enemy.artWeb || ('assets/heroes/' + enemy.key + '/lega.webp');
        }
      } else if (enemyActor) {
        enemyActor.hidden = true;
      }
    },

    updateVfx(vfx) {
      vfx = vfx || {};
      const speedlines = document.getElementById('story-speedlines');
      if (speedlines) {
        speedlines.hidden = !vfx.speedlines || this.reduceMotion;
      }
      const grid = document.getElementById('story-ambient-grid');
      if (grid) {
        grid.style.opacity = vfx.scanGrid ? '1' : '0.2';
      }
      const viewport = document.getElementById('story-viewport');
      if (viewport) {
        if (vfx.flash && !this.reduceMotion) {
          viewport.classList.add('flash-effect');
          setTimeout(() => viewport.classList.remove('flash-effect'), 260);
        }
      }
    },

    renderDialogue(dlg) {
      const dialogBox = document.querySelector('.story-dialog');
      if (!dlg) {
        if (dialogBox) dialogBox.style.opacity = '0';
        return;
      }
      if (dialogBox) dialogBox.style.opacity = '1';

      const nameEl = document.getElementById('story-name');
      const roleEl = document.getElementById('story-role-badge');
      const textEl = document.getElementById('story-text');
      const progEl = document.getElementById('story-progress');

      if (nameEl) nameEl.textContent = dlg.speaker || '???';
      if (roleEl) {
        roleEl.textContent = dlg.role || '未确认';
        roleEl.hidden = !dlg.role;
      }
      if (progEl) {
        progEl.textContent = `${this.eventIndex + 1} / ${this.currentSeq.events.length}`;
      }

      if (textEl) {
        clearTimeout(this.typingTimer);
        const full = dlg.text || '';
        textEl.textContent = '';
        let charIdx = 0;
        const step = () => {
          if (charIdx < full.length) {
            textEl.textContent = full.slice(0, charIdx + 1);
            charIdx += 2;
            this.typingTimer = setTimeout(step, this.reduceMotion ? 0 : 25);
          } else {
            textEl.textContent = full;
          }
        };
        step();
      }
    },

    skip() {
      if (!this.active) return;
      console.log('[Narrative Timeline] 用户触发跳过剧情');
      const cur = window.PrologueLog[window.PrologueLog.length - 1];
      if (cur) {
        cur.status = 'skipped';
        cur.skipped = true;
        cur.elapsedMs = performance.now() - cur.startTimeMs;
      }
      this.finish();
    },

    finish() {
      this.active = false;
      clearTimeout(this.eventTimer);
      clearTimeout(this.typingTimer);

      if (this.spineStage) {
        this.spineStage.stop();
        this.spineStage = null;
      }
      if (this.enemySpineStage) {
        this.enemySpineStage.stop();
        this.enemySpineStage = null;
      }

      const scr = document.getElementById('screen-story');
      if (scr) scr.classList.remove('prologue-mode');

      if (typeof this.onComplete === 'function') {
        this.onComplete();
      }
    }
  };

  window.IntroTimeline = IntroTimeline;
  IntroTimeline.init();
})();
