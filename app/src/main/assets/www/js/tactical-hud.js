/* ==========================================================================
   TacticalHud: 还原案例式战术网格、教程肖像气泡、高亮格与手形指引
   对齐 CASE_DEEP_AUDIT_V3_2026-10-29 A2/A3/P1/P2
   ========================================================================== */
(function () {
  'use strict';

  function TacticalHud(battle) {
    this.battle = battle;
    this.el = battle.el;
    this.selectedUnit = null;
    this.currentStep = 0;
    this.steps = [
      {
        id: 'step_intro',
        title: '敌方情报侦测',
        text: '敌人拥有 4 攻击力和 3 生命值。战术雷达已锁定敌方先遣队。',
        targetLane: 1,
        targetSide: 'enemy',
        action: 'tap_continue'
      },
      {
        id: 'step_select_unit',
        title: '选中我方特勤队员',
        text: '点击我方左侧突击队员，确认作战战位与攻击范围。',
        targetLane: 0,
        targetSide: 'ally',
        action: 'select_ally'
      },
      {
        id: 'step_move_or_target',
        title: '锁定攻击目标',
        text: '以队员攻击力 3 指向正前方敌方单位，发起战术突刺！',
        targetLane: 0,
        targetSide: 'enemy',
        action: 'attack_target'
      }
    ];

    this.initDom();
  }

  TacticalHud.prototype.initDom = function () {
    if (!this.el) return;

    // 清理既有 HUD
    this.el.querySelectorAll('.tactical-hud-el').forEach(n => n.remove());

    // 1. 顶部双方阵营与回合 HUD
    const turnHud = document.createElement('div');
    turnHud.className = 'tactical-turn-hud tactical-hud-el';
    turnHud.innerHTML = `
      <div class="faction-badge ally"><span>🟢</span> <span>伊桑 (指挥官)</span></div>
      <div class="round-pill" id="tactical-round-pill">回合 1</div>
      <div class="faction-badge enemy"><span>黑手党 (混沌守卫)</span> <span>🔴</span></div>
    `;
    this.el.appendChild(turnHud);

    // 2. 战术网格与槽位底盘容器
    this.lanesOverlay = document.createElement('div');
    this.lanesOverlay.className = 'tactical-lanes-overlay tactical-hud-el';
    this.el.appendChild(this.lanesOverlay);

    // 3. 大号手形指针
    this.handPointer = document.createElement('div');
    this.handPointer.className = 'tactical-hand-pointer tactical-hud-el';
    this.handPointer.textContent = '👆';
    this.handPointer.hidden = true;
    this.el.appendChild(this.handPointer);

    // 4. 单位属性面板 (Inspector)
    this.inspector = document.createElement('div');
    this.inspector.className = 'tactical-unit-inspector tactical-hud-el';
    this.inspector.hidden = true;
    this.el.appendChild(this.inspector);

    // 5. 教程肖像气泡
    this.bubble = document.createElement('div');
    this.bubble.className = 'tactical-tutorial-bubble tactical-hud-el';
    this.bubble.innerHTML = `
      <div class="tutor-bubble-box">
        <div class="tutor-title" id="tutor-step-title">战术指令</div>
        <div class="tutor-desc" id="tutor-step-desc">正在分析战术网格…</div>
        <div class="tutor-tap-hint" id="tutor-tap-hint">轻触继续 ▸</div>
      </div>
      <div class="tutor-avatar"></div>
    `;
    this.el.appendChild(this.bubble);

    this.bindEvents();
    this.renderStep(0);
  };

  TacticalHud.prototype.renderStep = function (stepIdx) {
    this.currentStep = stepIdx;
    const step = this.steps[stepIdx];
    if (!step) {
      if (this.bubble) this.bubble.hidden = true;
      if (this.handPointer) this.handPointer.hidden = true;
      return;
    }

    if (this.bubble) {
      this.bubble.hidden = false;
      document.getElementById('tutor-step-title').textContent = step.title;
      document.getElementById('tutor-step-desc').textContent = step.text;
      document.getElementById('tutor-tap-hint').textContent = step.action === 'tap_continue' ? '轻触继续 ▸' : '遵照指引操作 ▸';
    }

    // 绘制高亮格与手形指引
    this.updateGridHighlights(step);
  };

  TacticalHud.prototype.updateGridHighlights = function (step) {
    if (!this.lanesOverlay) return;
    this.lanesOverlay.innerHTML = '';

    const b = this.battle;
    if (!b || !b.units) return;

    // 绘制三路底盘与单位底座
    b.units.forEach((u) => {
      if (!u.alive) return;
      const tp = b.spine.project(u.x, u.footY);
      const ring = document.createElement('div');
      ring.className = `unit-base-ring ${u.side} ${this.selectedUnit === u ? 'selected' : ''}`;
      ring.style.left = (tp.x / b.W * 100) + '%';
      ring.style.top = ((1 - tp.y / b.H) * 100) + '%';
      this.lanesOverlay.appendChild(ring);
    });

    // 教程高亮格定位
    if (step.targetSide === 'enemy') {
      const target = b.units.find(u => u.side === 'enemy' && u.lane === step.targetLane && u.alive) || b.units.find(u => u.side === 'enemy' && u.alive);
      if (target) {
        const tp = b.spine.project(target.x, target.footY);
        const tile = document.createElement('div');
        tile.className = 'tactical-tile highlight';
        tile.style.width = '90px';
        tile.style.height = '60px';
        tile.style.left = (tp.x / b.W * 100 - 4.5) + '%';
        tile.style.top = ((1 - tp.y / b.H) * 100 - 4) + '%';
        this.lanesOverlay.appendChild(tile);

        if (this.handPointer) {
          this.handPointer.hidden = false;
          this.handPointer.style.left = (tp.x / b.W * 100) + '%';
          this.handPointer.style.top = ((1 - tp.y / b.H) * 100 + 4) + '%';
        }
      }
    } else if (step.targetSide === 'ally') {
      const ally = b.units.find(u => u.side === 'ally' && u.lane === step.targetLane && u.alive) || b.units.find(u => u.side === 'ally' && u.alive);
      if (ally) {
        const tp = b.spine.project(ally.x, ally.footY);
        const tile = document.createElement('div');
        tile.className = 'tactical-tile highlight';
        tile.style.width = '90px';
        tile.style.height = '60px';
        tile.style.left = (tp.x / b.W * 100 - 4.5) + '%';
        tile.style.top = ((1 - tp.y / b.H) * 100 - 4) + '%';
        this.lanesOverlay.appendChild(tile);

        if (this.handPointer) {
          this.handPointer.hidden = false;
          this.handPointer.style.left = (tp.x / b.W * 100) + '%';
          this.handPointer.style.top = ((1 - tp.y / b.H) * 100 + 4) + '%';
        }
      }
    }
  };

  TacticalHud.prototype.bindEvents = function () {
    if (this.bubble) {
      this.bubble.onclick = (e) => {
        e.stopPropagation();
        this.advanceStep();
      };
    }

    if (this.el) {
      this.el.onclick = (e) => {
        const step = this.steps[this.currentStep];
        if (step && step.action === 'tap_continue') {
          this.advanceStep();
        } else if (step && step.action === 'select_ally') {
          const ally = this.battle.units.find(u => u.side === 'ally' && u.alive);
          if (ally) this.selectUnit(ally);
          this.advanceStep();
        } else if (step && step.action === 'attack_target') {
          const enemy = this.battle.units.find(u => u.side === 'enemy' && u.alive);
          const ally = this.selectedUnit || this.battle.units.find(u => u.side === 'ally' && u.alive);
          if (ally && enemy) {
            this.battle.damage(ally, enemy, ally.atk * 1.5);
            if (window.LGVFX) window.LGVFX.slash({ x: enemy.x, y: enemy.footY });
          }
          this.advanceStep();
        }
      };
    }
  };

  TacticalHud.prototype.selectUnit = function (unit) {
    this.selectedUnit = unit;
    if (!this.inspector) return;
    this.inspector.hidden = false;
    this.inspector.innerHTML = `
      <div class="unit-insp-name">${unit.name}</div>
      <div class="unit-insp-role">职业: ${unit.class === 'ranged' ? '远程射手' : '近战突击'}</div>
      <div class="unit-insp-stat"><span>攻击力:</span> <b>${unit.atk}</b></div>
      <div class="unit-insp-stat"><span>生命值:</span> <b>${unit.hp}/${unit.maxHp}</b></div>
      <div class="unit-insp-skill">状态: 阵型稳定 · 准备交火</div>
    `;
    this.updateGridHighlights(this.steps[this.currentStep] || {});
  };

  TacticalHud.prototype.advanceStep = function () {
    if (this.currentStep + 1 < this.steps.length) {
      this.renderStep(this.currentStep + 1);
    } else {
      // 教程结束，关闭气泡与指针，进入全速战术交火
      if (this.bubble) this.bubble.hidden = true;
      if (this.handPointer) this.handPointer.hidden = true;
      if (this.inspector) this.inspector.hidden = true;
    }
  };

  window.TacticalHud = TacticalHud;
})();
