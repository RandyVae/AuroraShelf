/* Animated Spine stage for UI screens (hub, detail, gacha reveal). */
(function () {
  'use strict';

  function SpineStage(canvas, opts) {
    this.canvas = canvas;
    this.opts = Object.assign({ xRatio: 0.5, yRatio: 0.04, heightRatio: 0.96, flip: false }, opts || {});
    this.field = null;
    this.view = null;
    this.unitName = null;
    this.raf = 0;
    this.active = false;
    this.loading = false;
    this.loop = this.loop.bind(this);
    this.resize = this.resize.bind(this);
    window.addEventListener('resize', this.resize);
  }

  SpineStage.prototype.ensure = function () {
    if (!this.field) this.field = new window.SpineField(this.canvas);
    return this.field;
  };

  SpineStage.prototype.resize = function () {
    if (!this.active || !this.field) return;
    this.field.fit();
    this.place();
  };

  SpineStage.prototype.place = function () {
    if (!this.view || !this.field) return;
    const W = this.field.W, H = this.field.H;
    const heightPx = Math.max(160, this.opts.heightRatio * H);
    const scaleMult = this.opts.scaleMultiplier || 1.0;
    const scale = (heightPx / (this.view.data.height || 900)) * scaleMult;
    this.view.scale = scale;
    this.view.baseScale = scale;
    this.view.skeleton.scaleX = scale * (this.opts.flip ? -1 : 1);
    this.view.skeleton.scaleY = scale;
    this.view.skeleton.x = W * this.opts.xRatio;
    this.view.skeleton.y = H * this.opts.yRatio;
    this.view.x = this.view.skeleton.x;
    this.view.y = this.view.skeleton.y;
  };

  SpineStage.prototype.show = async function (unitName, opts) {
    if (!unitName) return false;
    if (opts) Object.assign(this.opts, opts);
    if (this.active && this.unitName === unitName && this.view) {
      this.resize();
      if (opts && opts.anim) this.setAnim(opts.anim, opts.loop !== false);
      return true;
    }
    const field = this.ensure();
    field.fit();
    const prev = this.view;
    if (prev) { field.remove(prev); this.view = null; }
    this.unitName = unitName;
    this.canvas.hidden = false;
    try {
      field.queue(unitName);
      await field.loadAll();
      if (this.unitName !== unitName) return false;
      this.view = field.build(unitName, { heightPx: 200, flip: this.opts.flip });
      if (opts && opts.anim) this.setAnim(opts.anim, opts.loop !== false);
      this.place();
      this.start();
      return true;
    } catch (err) {
      console.warn('SpineStage failed for', unitName, err);
      this.canvas.hidden = true;
      return false;
    }
  };

  SpineStage.prototype.setAnim = function (name, loop) {
    if (!this.view || !this.field) return false;
    return this.field.setAnim(this.view, name, loop);
  };

  SpineStage.prototype.playAction = function (name) {
    if (!this.view || !this.field) return false;
    return this.field.setAnim(this.view, name, false);
  };

  SpineStage.prototype.resumeIdle = function () {
    if (!this.view || !this.field) return;
    this.field.resumeIdle(this.view);
  };

  SpineStage.prototype.loop = function (now) {
    if (!this.active || !this.field) return;
    const dt = Math.min(0.05, Math.max(0, (now - (this.last || now)) / 1000));
    this.last = now;
    this.field.update(dt);
    this.field.draw();
    this.raf = requestAnimationFrame(this.loop);
  };

  SpineStage.prototype.start = function () {
    if (this.active) return;
    this.active = true;
    this.last = 0;
    this.resize();
    this.raf = requestAnimationFrame(this.loop);
  };

  SpineStage.prototype.stop = function () {
    this.active = false;
    cancelAnimationFrame(this.raf);
    this.raf = 0;
  };

  window.SpineStage = SpineStage;
})();
