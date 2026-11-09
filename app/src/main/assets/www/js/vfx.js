/* Combat Visual Effects & Feedback System (VFX) - AAA Tactical Upgrade
 * Handles crescent slashes, golden cross slashes, plasma projectiles, muzzle flashes,
 * hit sparks, camera shake, cinematic skill spotlights, ambient dust particles & Apple/Android haptics.
 */
(function () {
  'use strict';

  function VFXManager() {
    this.container = null;
    this.canvas = null;
    this.ctx = null;
    this.particles = [];
    this.ambientParticles = [];
    this.animId = null;
    this.lastTime = 0;
  }

  VFXManager.prototype.init = function (container) {
    this.container = container;
    this.clearAll();

    // Canvas overlay for high-performance particle streams & atmospheric embers
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

    // 战场环境微尘粒子流 (24 颗柔和发光的纳米以太浮游微粒)
    this.ambientParticles = [];
    for (let i = 0; i < 24; i++) {
      this.ambientParticles.push({
        x: Math.random() * 100,
        y: Math.random() * 100,
        vx: (Math.random() - 0.5) * 1.5,
        vy: -0.8 - Math.random() * 2.2,
        size: 1.2 + Math.random() * 2.0,
        alpha: 0.15 + Math.random() * 0.45,
        pulseSpeed: 1.5 + Math.random() * 2.0,
        phase: Math.random() * Math.PI * 2,
        color: i % 3 === 0 ? 'rgba(56, 189, 248, ' : (i % 3 === 1 ? 'rgba(251, 191, 36, ' : 'rgba(236, 72, 153, ')
      });
    }

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

  VFXManager.prototype.haptic = function (style) {
    if (window.NativeBridge && window.NativeBridge.haptic) {
      try { window.NativeBridge.haptic(style); } catch (e) {}
    } else if (window.GameNativeBridge && window.GameNativeBridge.haptic) {
      try { window.GameNativeBridge.haptic(style); } catch (e) {}
    }
  };

  VFXManager.prototype.loop = function (now) {
    const dt = Math.min(0.1, (now - this.lastTime) / 1000);
    this.lastTime = now;

    if (this.ctx && this.canvas) {
      const w = this.canvas.width;
      const h = this.canvas.height;
      this.ctx.clearRect(0, 0, w, h);

      // 1. 战场环境微尘粒子渲染
      if (!this.isReduceMotion() && this.ambientParticles.length) {
        for (let i = 0; i < this.ambientParticles.length; i++) {
          const ap = this.ambientParticles[i];
          ap.y += ap.vy * dt * 8;
          ap.x += Math.sin(now * 0.001 * ap.pulseSpeed + ap.phase) * dt * 4;
          if (ap.y < -5) {
            ap.y = 105;
            ap.x = Math.random() * 100;
          }
          const pulseAlpha = ap.alpha * (0.6 + 0.4 * Math.sin(now * 0.002 * ap.pulseSpeed + ap.phase));
          this.ctx.save();
          this.ctx.fillStyle = ap.color + pulseAlpha + ')';
          this.ctx.shadowBlur = ap.size * 3;
          this.ctx.shadowColor = ap.color + '0.8)';
          this.ctx.beginPath();
          this.ctx.arc(ap.x * (w / 100), ap.y * (h / 100), ap.size * (w / 1000), 0, Math.PI * 2);
          this.ctx.fill();
          this.ctx.restore();
        }
      }

      // 2. 战斗爆发粒子 (Hit Sparks & Bursts)
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
        this.ctx.shadowBlur = (p.size || 3) * 2;
        this.ctx.shadowColor = p.color || '#fff';
        if (p.shape === 'square') {
          const s = (p.size || 3) * (w / 1000) * 1.5;
          this.ctx.fillRect(p.x * (w / 100) - s / 2, p.y * (h / 100) - s / 2, s, s);
        } else {
          this.ctx.beginPath();
          this.ctx.arc(p.x * (w / 100), p.y * (h / 100), (p.size || 3) * (w / 1000), 0, Math.PI * 2);
          this.ctx.fill();
        }
        this.ctx.restore();
      }
    }

    this.animId = requestAnimationFrame(this.loop);
  };

  // 1. 近战高能光刃斩击 (弧光月牙斩 & 暴击金色十字斩)
  VFXManager.prototype.slash = function (targetPos, isCrit, opts) {
    if (!this.container) return;
    opts = opts || {};
    const side = opts.side || 'ally';

    if (isCrit) {
      // 暴击双重金色十字交叉斩
      const el = document.createElement('div');
      el.className = 'vfx-cross-slash';
      el.style.left = targetPos.x + '%';
      el.style.top = targetPos.y + '%';
      this.container.appendChild(el);
      this.cameraPunch(7);
      this.haptic('heavy');
      setTimeout(() => { if (el.parentNode) el.parentNode.removeChild(el); }, 380);
    } else {
      // 普通高能月牙弧光斩 (我方青蓝 / 敌方猩红)
      const el = document.createElement('div');
      el.className = 'vfx-slash' + (side === 'enemy' ? ' vfx-enemy-slash' : '');
      el.style.left = targetPos.x + '%';
      el.style.top = targetPos.y + '%';
      this.container.appendChild(el);
      this.haptic('light');
      setTimeout(() => { if (el.parentNode) el.parentNode.removeChild(el); }, 280);
    }
  };

  // 2. 枪口火花 (Muzzle Flash)
  VFXManager.prototype.muzzleFlash = function (pos, color) {
    if (!this.container || this.isReduceMotion()) return;
    const muzzle = document.createElement('div');
    muzzle.className = 'vfx-muzzle-flash';
    muzzle.style.setProperty('--muzzle-color', color || '#38bdf8');
    muzzle.style.left = pos.x + '%';
    muzzle.style.top = pos.y + '%';
    this.container.appendChild(muzzle);
    setTimeout(() => { if (muzzle.parentNode) muzzle.parentNode.removeChild(muzzle); }, 150);
  };

  // 3. 远程能量投射物 (Projectile Streak with Plasma Trail & Muzzle)
  VFXManager.prototype.projectile = function (srcPos, dstPos, color, onHit) {
    if (!this.container) {
      if (onHit) onHit();
      return;
    }
    const boltColor = color || '#38bdf8';
    this.muzzleFlash(srcPos, boltColor);

    if (this.isReduceMotion()) {
      if (onHit) onHit();
      return;
    }

    const bolt = document.createElement('div');
    bolt.className = 'vfx-projectile';
    bolt.style.setProperty('--bolt-color', boltColor);
    bolt.style.left = srcPos.x + '%';
    bolt.style.top = srcPos.y + '%';
    this.container.appendChild(bolt);

    const dx = dstPos.x - srcPos.x;
    const dy = dstPos.y - srcPos.y;
    const angle = Math.atan2(dy, dx) * (180 / Math.PI);
    bolt.style.transform = 'translate(-50%, -50%) rotate(' + angle + 'deg)';

    const duration = 220; // ms
    const startTime = performance.now();

    const animateBolt = (now) => {
      const progress = Math.min(1, (now - startTime) / duration);
      // 弹性加速飞行曲线
      const easeProgress = progress * (2 - progress);
      const curX = srcPos.x + dx * easeProgress;
      const curY = srcPos.y + dy * easeProgress;
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

  // 4. 受击爆鸣火花与扩散震波
  VFXManager.prototype.hitSpark = function (pos, isCrit) {
    if (isCrit && !this.isReduceMotion()) {
      this.cameraPunch(6);
    }

    // 冲击扩散环
    if (this.container && !this.isReduceMotion()) {
      const ring = document.createElement('div');
      ring.className = 'vfx-hit-ring' + (isCrit ? ' vfx-crit-ring' : '');
      ring.style.left = pos.x + '%';
      ring.style.top = pos.y + '%';
      this.container.appendChild(ring);
      setTimeout(() => { if (ring.parentNode) ring.parentNode.removeChild(ring); }, 300);
    }

    if (this.isReduceMotion()) return;

    const count = isCrit ? 22 : 10;
    const colors = isCrit ? ['#fbbf24', '#f59e0b', '#ffffff'] : ['#f43f5e', '#fb7185', '#ffffff'];
    for (let i = 0; i < count; i++) {
      const angle = Math.random() * Math.PI * 2;
      const speed = 18 + Math.random() * (isCrit ? 42 : 24);
      this.particles.push({
        x: pos.x,
        y: pos.y,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed - (isCrit ? 15 : 8),
        gravity: 50,
        size: isCrit ? 4.5 : 2.8,
        life: 0.35 + Math.random() * 0.25,
        maxLife: 0.6,
        color: colors[Math.floor(Math.random() * colors.length)]
      });
    }
  };

  // 5. 战术大招 / 技能特写聚焦 (Cinematic Skill Spotlight & Banner)
  VFXManager.prototype.skillCutIn = function (unitOrName, spec) {
    if (!this.container) return;
    const unitName = (typeof unitOrName === 'string')
      ? unitOrName
      : ((unitOrName && unitOrName.name) || '特勤战术指令');
    const skillName = (spec && (spec.name || spec.title)) || (typeof unitOrName === 'string' ? unitOrName : '极限超频爆发');

    // 1. 全屏聚焦暗角与聚光灯 (Spotlight Vignette)
    const vignette = document.createElement('div');
    vignette.className = 'vfx-spotlight-vignette';
    this.container.appendChild(vignette);

    // 2. 战术全息横幅 (Hologram Banner)
    const banner = document.createElement('div');
    banner.className = 'vfx-skill-banner';
    banner.innerHTML = `
      <div class="vsb-tag">TACTICAL OVERCLOCK</div>
      <div class="vsb-title">${skillName}</div>
      <div class="vsb-sub">${unitName} · 战术指令激活</div>
    `;
    this.container.appendChild(banner);

    // 3. 屏幕微震与触感
    this.cameraPunch(5);
    this.haptic('medium');

    setTimeout(() => {
      if (vignette.parentNode) vignette.parentNode.removeChild(vignette);
      if (banner.parentNode) banner.parentNode.removeChild(banner);
    }, 450);
  };

  // 6. 治疗光环与以太护盾
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

  // 7. 异常状态表现 (冰冻结晶 / 剧毒迷雾)
  VFXManager.prototype.freezeStatus = function (pos) {
    if (!this.container) return;
    const ice = document.createElement('div');
    ice.className = 'vfx-freeze-ice';
    ice.style.left = pos.x + '%';
    ice.style.top = pos.y + '%';
    this.container.appendChild(ice);
    setTimeout(() => { if (ice.parentNode) ice.parentNode.removeChild(ice); }, 750);
  };

  // 8. 屏幕震击 (Camera Punch)
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

  // 9. 胜利彩带与大爆发
  VFXManager.prototype.victoryBurst = function () {
    if (!this.container || this.isReduceMotion()) return;
    const colors = ['#f59e0b', '#ec4899', '#38bdf8', '#10b981', '#a855f7'];
    for (let i = 0; i < 55; i++) {
      this.particles.push({
        x: 50 + (Math.random() - 0.5) * 40,
        y: 45 + (Math.random() - 0.5) * 20,
        vx: (Math.random() - 0.5) * 75,
        vy: -25 - Math.random() * 55,
        gravity: 60,
        size: 4 + Math.random() * 3.5,
        life: 1.3 + Math.random() * 0.9,
        maxLife: 2.2,
        color: colors[Math.floor(Math.random() * colors.length)]
      });
    }
  };

  // 10. 单位战败/被击倒数字化解构粒子流 (Cybernetic Dissolve FX)
  VFXManager.prototype.deathDissolve = function (pos, side) {
    if (!this.container || this.isReduceMotion()) return;
    const isAlly = side === 'ally';
    const colors = isAlly ? ['#38bdf8', '#0284c7', '#ffffff'] : ['#f43f5e', '#ec4899', '#c084fc', '#ffffff'];
    const count = 32;
    for (let i = 0; i < count; i++) {
      const angle = (Math.PI * 1.5) + (Math.random() - 0.5) * 1.8;
      const speed = 25 + Math.random() * 45;
      this.particles.push({
        x: pos.x + (Math.random() - 0.5) * 5,
        y: pos.y + (Math.random() - 0.5) * 8,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        gravity: -16,
        size: 3.2 + Math.random() * 2.8,
        life: 0.65 + Math.random() * 0.45,
        maxLife: 1.1,
        color: colors[Math.floor(Math.random() * colors.length)],
        shape: 'square'
      });
    }

    const wave = document.createElement('div');
    wave.className = 'vfx-dissolve-wave ' + (isAlly ? 'ally' : 'enemy');
    wave.style.left = pos.x + '%';
    wave.style.top = pos.y + '%';
    this.container.appendChild(wave);
    setTimeout(() => { if (wave.parentNode) wave.parentNode.removeChild(wave); }, 450);
  };

  // 11. 全屏高能强光爆闪 (High-Energy Screen Flash)
  VFXManager.prototype.screenFlash = function (color, duration) {
    if (!this.container || this.isReduceMotion()) return;
    const flash = document.createElement('div');
    flash.className = 'vfx-screen-flash';
    if (color) flash.style.backgroundColor = color;
    this.container.appendChild(flash);
    const dur = duration || 120;
    setTimeout(() => { if (flash.parentNode) flash.parentNode.removeChild(flash); }, dur);
  };

  VFXManager.prototype.clearAll = function () {
    this.particles = [];
    if (this.animId) {
      cancelAnimationFrame(this.animId);
      this.animId = null;
    }
    if (this.container) {
      const existing = this.container.querySelectorAll(
        '.vfx-slash, .vfx-cross-slash, .vfx-muzzle-flash, .vfx-projectile, .vfx-hit-ring, ' +
        '.vfx-spotlight-vignette, .vfx-skill-banner, .vfx-heal-aura, .vfx-shield-bubble, .vfx-freeze-ice, ' +
        '.vfx-dissolve-wave, .vfx-screen-flash'
      );
      existing.forEach((e) => { if (e.parentNode) e.parentNode.removeChild(e); });
    }
  };

  window.LGVFX = new VFXManager();
})();
