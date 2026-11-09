/* Spine 4.2 renderer wrapper: one WebGL canvas, many skeletons. */
(function () {
  'use strict';

  function SpineField(canvas) {
    this.canvas = canvas;
    this.units = [];
    this._queued = {};
    try {
      this.ctx = new spine.ManagedWebGLRenderingContext(canvas, { alpha: true, premultipliedAlpha: true });
      this.renderer = new spine.SceneRenderer(canvas, this.ctx);
      this.am = new spine.AssetManager(this.ctx);
      this.fit();
    } catch (e) {
      console.warn('SpineField WebGL init failed:', e);
      this.ctx = null;
      this.renderer = null;
      this.am = null;
    }
  }

  SpineField.prototype.fit = function () {
    const canvas = this.canvas;
    const rect = canvas.getBoundingClientRect();
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const w = Math.max(320, Math.round((rect.width || canvas.width) * dpr));
    const h = Math.max(240, Math.round((rect.height || canvas.height) * dpr));
    if (canvas.width !== w) canvas.width = w;
    if (canvas.height !== h) canvas.height = h;
    this.W = w; this.H = h;
    if (!this.renderer) return;
    const cam = this.renderer.camera;
    cam.viewportWidth = w;
    cam.viewportHeight = h;
    this.renderer.resize(spine.ResizeMode.Expand);
    cam.position.set(w / 2, h / 2, 0);
    if (cam.update) cam.update();
  };

  SpineField.prototype.resolveUnitPaths = function (unitName) {
    const manifest = window.ORIGINAL_ASSETS_MANIFEST;
    const origUnits = (manifest && manifest.units) || {};
    const overrides = (manifest && manifest.unitOverrides) || {};
    const targetKey = overrides[unitName] || unitName;

    // 1. 若在原創資產清單中明確註冊
    if (origUnits[targetKey]) {
      const info = origUnits[targetKey];
      return {
        unit: targetKey,
        atlas: info.atlas || ('assets/units_original/' + targetKey + '/' + targetKey + '.atlas'),
        json: info.skeleton || ('assets/units_original/' + targetKey + '/skeleton.json')
      };
    }

    // 2. 原創命名規範后綴優先定位
    if (targetKey.endsWith('_original')) {
      const base = 'assets/units_original/' + targetKey + '/';
      return {
        unit: targetKey,
        atlas: base + targetKey + '.atlas',
        json: base + 'skeleton.json'
      };
    }

    // 3. 安全回退至歷史存量目錄
    const base = 'assets/units/' + targetKey + '/';
    return {
      unit: targetKey,
      atlas: base + targetKey + '.atlas',
      json: base + 'skeleton.json'
    };
  };

  SpineField.prototype.queue = function (unitName) {
    if (!this.am) return;
    this._queued = this._queued || {};
    if (this._queued[unitName]) return;
    const resolved = this.resolveUnitPaths(unitName);
    this._queued[unitName] = { atlas: resolved.atlas, json: resolved.json, resolvedUnit: resolved.unit };
    this.am.loadTextureAtlas(this._queued[unitName].atlas);
    this.am.loadText(this._queued[unitName].json);
  };

  SpineField.prototype.loadAll = function () {
    return this.am ? this.am.loadAll() : Promise.resolve();
  };

  SpineField.prototype.build = function (unitName, opts) {
    if (!this.am) return null;
    opts = opts || {};
    const meta = (this._queued || {})[unitName];
    if (!meta) throw new Error('unit not queued: ' + unitName);
    const loader = new spine.AtlasAttachmentLoader(this.am.require(meta.atlas));
    const json = new spine.SkeletonJson(loader);
    const data = json.readSkeletonData(this.am.require(meta.json));
    const skel = new spine.Skeleton(data);
    skel.setToSetupPose();
    // Some atlases bind stray hair meshes to the FLOOR root bone; they render as
    // detached ribbons (visible pink blocks) instead of following the head.
    for (const slot of skel.slots) {
      if (slot.boneData && slot.boneData.name === 'FLOOR' && slot.attachment) slot.attachment = null;
    }
    const stateData = new spine.AnimationStateData(data);
    stateData.defaultMix = 0.22; // 220ms 平滑骨骼混合过渡，彻底消除动作生硬抽搐与单帧硬切
    const state = new spine.AnimationState(stateData);
    const idle = data.animations.find((a) => a.name === 'idle') ? 'idle' : (data.animations[0] && data.animations[0].name);
    if (idle) state.setAnimation(0, idle, true);
    const wantH = opts.heightPx || 180;
    const scale = wantH / (data.height || 900);
    skel.scaleX = scale * (opts.flip ? -1 : 1);
    skel.scaleY = scale;
    const view = {
      unitName, data, skeleton: skel, state, scale,
      baseScale: scale,
      x: opts.x || 0, y: opts.y || 0, flip: !!opts.flip,
      animationUntil: 0, alpha: 1, hitFlash: 0,
      squashTimer: 0, squashDur: 0.16, targetSquashX: 1, targetSquashY: 1,
    };
    this.position(view, view.x, view.y);
    this.units.push(view);
    return view;
  };

  SpineField.prototype.position = function (view, x, footY) {
    if (!view || !view.skeleton) return;
    view.x = x; view.y = footY;
    view.skeleton.x = x;
    view.skeleton.y = footY;
  };

  SpineField.prototype.screenToWorld = function (sx, sy) {
    if (!this.renderer) return { x: sx, y: sy, z: 0 };
    const v = new spine.Vector3();
    v.x = sx; v.y = sy; v.z = 0;
    this.renderer.camera.screenToWorld(v, this.W, this.H);
    return v;
  };

  SpineField.prototype.setAnim = function (view, name, loop, holdSeconds) {
    if (!view || !view.data) return false;
    let anim = view.data.animations.find((a) => a.name === name);
    if (!anim) {
      // 动作不存在时降级回退至 idle，避免静默失败
      anim = view.data.animations.find((a) => a.name === 'idle') || view.data.animations[0];
      if (!anim) return false;
      name = anim.name;
    }
    view.state.setAnimation(0, name, loop !== false);
    if (loop === false) {
      const idleAnim = view.data.animations.find((a) => a.name === 'idle') || view.data.animations[0];
      if (idleAnim) {
        view.state.addAnimation(0, idleAnim.name, true, 0);
      }
    }
    return true;
  };

  SpineField.prototype.resumeIdle = function (view) {
    if (!view || !view.data) return;
    const idle = view.data.animations.find((a) => a.name === 'idle') ? 'idle' : (view.data.animations[0] && view.data.animations[0].name);
    if (idle) this.setAnim(view, idle, true);
  };

  SpineField.prototype.remove = function (view) {
    const i = this.units.indexOf(view);
    if (i >= 0) this.units.splice(i, 1);
  };

  SpineField.prototype.clear = function () {
    if (this.units) {
      this.units.forEach((u) => {
        try { if (u && u.state) u.state.clearTracks(); } catch (_) {}
      });
      this.units = [];
    }
    if (this.ctx && this.ctx.gl) {
      try {
        this.ctx.gl.clearColor(0, 0, 0, 0);
        this.ctx.gl.clear(this.ctx.gl.COLOR_BUFFER_BIT);
      } catch (_) {}
    }
  };

  SpineField.prototype.project = function (x, y) {
    if (!this.renderer) return { x, y: this.H ? this.H - y : y, z: 0 };
    const v = new spine.Vector3();
    v.x = x; v.y = y; v.z = 0;
    this.renderer.camera.worldToScreen(v, this.W, this.H);
    return v;
  };

  SpineField.prototype.squash = function (view, sqX, sqY, duration) {
    if (!view || !view.skeleton) return;
    view.targetSquashX = sqX;
    view.targetSquashY = sqY;
    view.squashDur = Math.max(0.06, duration || 0.16);
    view.squashTimer = view.squashDur;
  };

  SpineField.prototype.update = function (dt) {
    if (!this.units) return;
    const isReduce = !!(
      (typeof APP !== 'undefined' && APP.state && APP.state.settings && APP.state.settings.reduceMotion) ||
      (typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches)
    );
    for (const v of this.units) {
      if (!v || !v.state || !v.skeleton) continue;
      // 开启 Reduce Motion 时，时间步长极小化固定待机帧，彻底避免骨骼大幅晃动
      const effDt = isReduce ? dt * 0.1 : dt;
      v.state.update(effDt);
      v.state.apply(v.skeleton);
      v.skeleton.updateWorldTransform(spine.Physics.update);
      if (v.animationUntil && performance.now() > v.animationUntil) {
        v.animationUntil = 0;
        this.resumeIdle(v);
      }
      // 骨骼弹性形变 (Squash & Stretch) 物理质感阻尼回弹与翻转锁死
      if (!isReduce && v.squashTimer > 0) {
        v.squashTimer = Math.max(0, v.squashTimer - dt);
        const progress = v.squashTimer / v.squashDur;
        const factor = Math.sin(progress * Math.PI);
        const curSquashX = 1 + (v.targetSquashX - 1) * factor;
        const curSquashY = 1 + (v.targetSquashY - 1) * factor;
        v.skeleton.scaleX = (v.baseScale || v.scale) * (v.flip ? -1 : 1) * curSquashX;
        v.skeleton.scaleY = (v.baseScale || v.scale) * curSquashY;
      } else {
        v.skeleton.scaleX = (v.baseScale || v.scale) * (v.flip ? -1 : 1);
        v.skeleton.scaleY = (v.baseScale || v.scale);
      }
      // 受击高能闪白衰减
      if (v.hitFlash > 0) {
        v.hitFlash = Math.max(0, v.hitFlash - dt);
        const flashLevel = 1 + (v.hitFlash / 0.08) * 1.8;
        v.skeleton.color.r = flashLevel;
        v.skeleton.color.g = flashLevel;
        v.skeleton.color.b = flashLevel;
      } else if (v.skeleton.color.r !== 1) {
        v.skeleton.color.r = 1;
        v.skeleton.color.g = 1;
        v.skeleton.color.b = 1;
      }
    }
  };

  SpineField.prototype.draw = function () {
    if (!this.renderer) return;
    this.renderer.begin();
    for (const v of this.units) {
      if (!v || !v.skeleton) continue;
      v.skeleton.color.a = v.alpha;
      this.renderer.drawSkeleton(v.skeleton, true);
    }
    this.renderer.end();
  };

  window.SpineField = SpineField;
})();
