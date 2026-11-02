/* Combat Visual Effects & Feedback System (VFX)
 * Handles slashes, projectile streaks, hit sparks, crit screen impacts, heal/shield auras,
 * elemental statuses (ice, poison), cut-in flashes, and accessibility (reduceMotion).
 */
(function () {
  'use strict';

  function VFXManager() {
    this.container = null;
    this.canvas = null;
    this.ctx = null;
    this.particles = [];
    this.animId = null;
    this.lastTime = 0;
  }

  VFXManager.prototype.init = function (container) {
    this.container = container;
    this.clearAll();

    // Canvas overlay for high-performance particle streams
    let canvas = container.querySelector('.vfx-canvas');
    if (!canvas) {
      canvas = document.createElement('canvas');
      canvas.className = 'vfx-canvas';
      canvas.style.position = 'absolute';
      canvas.style.inset = '0';
      canvas.style.width = '100%';
      canvas.style.height = '100%';
      canvas.style.pointerEvents = 'none';
      canvas.style.zIndex = '8';
      container.appendChild(canvas);
    }
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.resize();

    this.loop = this.loop.bind(this);
    this.lastTime = performance.now();
    this.animId = requestAnimationFrame(this.loop);
  };

  VFXManager.prototype.resize = function () {
    if (!this.canvas || !this.container) return;
    const rect = this.container.getBoundingClientRect();
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const w = Math.max(320, Math.round(rect.width * dpr));
    const h = Math.max(240, Math.round(rect.height * dpr));
    if (this.canvas.width !== w || this.canvas.height !== h) {
      this.canvas.width = w;
      this.canvas.height = h;
    }
  };

  VFXManager.prototype.isReduceMotion = function () {
    return !!(
      (typeof APP !== 'undefined' && APP.state && APP.state.settings && APP.state.settings.reduceMotion) ||
      (typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches)
    );
  };

  VFXManager.prototype.loop = function (now) {
    const dt = Math.min(0.1, (now - this.lastTime) / 1000);
    this.lastTime = now;

    if (this.ctx && this.canvas) {
      this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
      const w = this.canvas.width;
      const h = this.canvas.height;

      for (let i = this.particles.length - 1; i >= 0; i--) {
        const p = this.particles[i];
        p.life -= dt;
        if (p.life <= 0) {
          this.particles.splice(i, 1);
          continue;
        }
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.vy += (p.gravity || 0) * dt;

        const alpha = Math.max(0, p.life / p.maxLife);
        this.ctx.save();
        this.ctx.globalAlpha = alpha;
        this.ctx.fillStyle = p.color || '#fff';
        this.ctx.beginPath();
        this.ctx.arc(p.x * (w / 100), p.y * (h / 100), (p.size || 3) * (w / 1000), 0, Math.PI * 2);
        this.ctx.fill();
        this.ctx.restore();
      }
    }

    this.animId = requestAnimationFrame(this.loop);
  };

  // 1. 近战弧光斩击
  VFXManager.prototype.slash = function (targetPos, isCrit) {
    if (!this.container) return;
    const el = document.createElement('div');
    el.className = 'vfx-slash' + (isCrit ? ' vfx-crit' : '');
    el.style.left = targetPos.x + '%';
    el.style.top = targetPos.y + '%';
    this.container.appendChild(el);
    setTimeout(() => { if (el.parentNode) el.parentNode.removeChild(el); }, 350);
  };

  // 2. 远程能量投射物 (Projectile Streak)
  VFXManager.prototype.projectile = function (srcPos, dstPos, color, onHit) {
    if (!this.container) {
      if (onHit) onHit();
      return;
    }
    if (this.isReduceMotion()) {
      // 减少动效模式跳过飞行弹道直接触发受击
      if (onHit) onHit();
      return;
    }

    const bolt = document.createElement('div');
    bolt.className = 'vfx-projectile';
    bolt.style.setProperty('--bolt-color', color || '#38bdf8');
    bolt.style.left = srcPos.x + '%';
    bolt.style.top = srcPos.y + '%';
    this.container.appendChild(bolt);

    const dx = dstPos.x - srcPos.x;
    const dy = dstPos.y - srcPos.y;
    const angle = Math.atan2(dy, dx) * (180 / Math.PI);
    bolt.style.transform = 'translate(-50%, -50%) rotate(' + angle + 'deg)';

    const duration = 240; // ms
    const startTime = performance.now();

    const animateBolt = (now) => {
      const progress = Math.min(1, (now - startTime) / duration);
      const curX = srcPos.x + dx * progress;
      const curY = srcPos.y + dy * progress;
      bolt.style.left = curX + '%';
      bolt.style.top = curY + '%';

      if (progress < 1) {
        requestAnimationFrame(animateBolt);
      } else {
        if (bolt.parentNode) bolt.parentNode.removeChild(bolt);
        if (onHit) onHit();
      }
    };
    requestAnimationFrame(animateBolt);
  };

  // 3. 受击火花与暴击震屏
  VFXManager.prototype.hitSpark = function (pos, isCrit) {
    if (isCrit && !this.isReduceMotion()) {
      this.cameraPunch(4);
    }
    if (this.isReduceMotion()) return;

    const count = isCrit ? 16 : 8;
    const color = isCrit ? '#f59e0b' : '#f43f5e';
    for (let i = 0; i < count; i++) {
      const angle = Math.random() * Math.PI * 2;
      const speed = 15 + Math.random() * (isCrit ? 35 : 20);
      this.particles.push({
        x: pos.x,
        y: pos.y,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        gravity: 40,
        size: isCrit ? 4.5 : 3,
        life: 0.35 + Math.random() * 0.2,
        maxLife: 0.55,
        color: color
      });
    }
  };

  // 4. 技能强光与爆发 (Skill Flash / Speed Lines)
  VFXManager.prototype.skillCutIn = function (unit, spec) {
    if (!this.container) return;
    const el = document.createElement('div');
    el.className = 'vfx-skill-flash';
    if (!this.isReduceMotion()) {
      el.classList.add('vfx-speedlines');
    }
    this.container.appendChild(el);
    setTimeout(() => { if (el.parentNode) el.parentNode.removeChild(el); }, 400);
  };

  // 5. 治疗与护盾光环
  VFXManager.prototype.healAura = function (pos) {
    if (!this.container) return;
    const ring = document.createElement('div');
    ring.className = 'vfx-heal-aura';
    ring.style.left = pos.x + '%';
    ring.style.top = pos.y + '%';
    this.container.appendChild(ring);
    setTimeout(() => { if (ring.parentNode) ring.parentNode.removeChild(ring); }, 600);
  };

  VFXManager.prototype.shieldBarrier = function (pos) {
    if (!this.container) return;
    const bubble = document.createElement('div');
    bubble.className = 'vfx-shield-bubble';
    bubble.style.left = pos.x + '%';
    bubble.style.top = pos.y + '%';
    this.container.appendChild(bubble);
    setTimeout(() => { if (bubble.parentNode) bubble.parentNode.removeChild(bubble); }, 650);
  };

  // 6. 异常状态表现 (冰冻结晶 / 剧毒迷雾)
  VFXManager.prototype.freezeStatus = function (pos) {
    if (!this.container) return;
    const ice = document.createElement('div');
    ice.className = 'vfx-freeze-ice';
    ice.style.left = pos.x + '%';
    ice.style.top = pos.y + '%';
    this.container.appendChild(ice);
    setTimeout(() => { if (ice.parentNode) ice.parentNode.removeChild(ice); }, 750);
  };

  // 7. 屏幕震击 (Camera Punch)
  VFXManager.prototype.cameraPunch = function (intensity) {
    if (!this.container || this.isReduceMotion()) return;
    const amt = intensity || 5;
    const el = this.container;
    el.style.transform = `translate(${(Math.random() - 0.5) * amt * 2}px, ${(Math.random() - 0.5) * amt * 2}px)`;
    setTimeout(() => {
      el.style.transform = `translate(${(Math.random() - 0.5) * amt}px, ${(Math.random() - 0.5) * amt}px)`;
      setTimeout(() => { el.style.transform = 'none'; }, 80);
    }, 60);
  };

  // 8. 胜利彩带与爆发
  VFXManager.prototype.victoryBurst = function () {
    if (!this.container) return;
    if (this.isReduceMotion()) return;
    const colors = ['#f59e0b', '#ec4899', '#3b82f6', '#10b981', '#a855f7'];
    for (let i = 0; i < 45; i++) {
      this.particles.push({
        x: 50 + (Math.random() - 0.5) * 30,
        y: 40 + (Math.random() - 0.5) * 20,
        vx: (Math.random() - 0.5) * 60,
        vy: -20 - Math.random() * 45,
        gravity: 55,
        size: 4 + Math.random() * 3,
        life: 1.2 + Math.random() * 0.8,
        maxLife: 2.0,
        color: colors[Math.floor(Math.random() * colors.length)]
      });
    }
  };

  VFXManager.prototype.clearAll = function () {
    this.particles = [];
    if (this.animId) {
      cancelAnimationFrame(this.animId);
      this.animId = null;
    }
    if (this.container) {
      const existing = this.container.querySelectorAll('.vfx-slash, .vfx-projectile, .vfx-skill-flash, .vfx-heal-aura, .vfx-shield-bubble, .vfx-freeze-ice');
      existing.forEach((e) => { if (e.parentNode) e.parentNode.removeChild(e); });
    }
  };

  window.LGVFX = new VFXManager();
})();
