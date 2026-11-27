(function () {
    'use strict';

    const SECTION_META = {
        video: { label: '视频', color: 'var(--sec-video)' },
        comics: { label: '漫画', color: 'var(--sec-comics)' },
        live: { label: '直播', color: 'var(--sec-live)' },
        forum: { label: '社区论坛', color: 'var(--sec-forum)' },
        game: { label: '内置游戏', color: 'var(--sec-game)' },
        search: { label: '搜索', color: 'var(--sec-search)' },
        library: { label: '资料库 / 我的', color: 'var(--sec-library)' },
    };

    const SUBSECTION_LABELS = {
        video_browse: '视频 · 浏览与选片',
        video_play: '视频 · 播放器观影',
        comic_browse: '漫画 · 书架与详情',
        comic_read: '漫画 · 阅读器看漫',
        live_browse: '直播 · 频道浏览',
        live_watch: '直播 · 直播间观看',
        forum_browse: '论坛 · 列表浏览',
        forum_read: '论坛 · 帖子阅读',
        game_play: '内置游戏 · 游玩',
        search: '全站搜索',
        library: '资料库 / 设置 / 账号',
    };

    const PLATFORM_LABELS = {
        ios: 'iPhone (iOS)',
        ipados: 'iPad (iPadOS)',
        android: 'Android',
    };

    const CHANNEL_LABELS = {
        testflight: 'TestFlight',
        ota: 'iOS OTA',
        apk: 'Android APK',
        simulator: '模拟器',
        debug: 'Debug',
        unknown: '未知',
    };

    const FORM_LABELS = {
        phone: '手机',
        tablet: '平板',
        foldable: '折叠屏',
        desktop: '桌面端',
        unknown: '未知形态',
    };

    const VIEW_TITLES = {
        overview: '总览仪表盘',
        installations: '安装与设备',
        sections: '板块时长分析',
        distribution: '分发与账号',
    };

    const state = {
        authenticated: false,
        username: 'admin',
        activeView: 'overview',
        activeSubtab: 'accounts',
        range: '7d',
        platform: 'all',
        includeSim: '1',
        channel: 'all',
        sort: 'last_seen',
        search: '',
        trendMetric: 'dwell_sec',
        selectedSection: 'video',
        selectedInstallId: null,
        overviewData: null,
        installationsData: null,
        sectionsData: null,
        distributionData: null,
        refreshTimer: null,
    };

    function el(id) {
        return document.getElementById(id);
    }

    function escapeHtml(raw) {
        const s = String(raw == null ? '' : raw);
        return s
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatDuration(sec) {
        const total = Math.max(0, Math.round(Number(sec) || 0));
        if (total < 60) return total + ' 秒';
        const hours = Math.floor(total / 3600);
        const mins = Math.floor((total % 3600) / 60);
        const remSec = total % 60;
        if (hours > 0) {
            return hours + ' 小时 ' + mins + ' 分';
        }
        if (mins >= 10 || remSec === 0) {
            return mins + ' 分';
        }
        return mins + ' 分 ' + remSec + ' 秒';
    }

    function formatCompactDuration(sec) {
        const total = Math.max(0, Math.round(Number(sec) || 0));
        if (total < 60) return total + 's';
        if (total < 3600) return Math.round(total / 60) + 'm';
        return (total / 3600).toFixed(1) + 'h';
    }

    function sectionLabel(sec) {
        return (SECTION_META[sec] && SECTION_META[sec].label) || sec || '—';
    }

    function sectionColor(sec) {
        return (SECTION_META[sec] && SECTION_META[sec].color) || 'var(--accent)';
    }

    function applyAppearancePreferences() {
        const savedTheme = localStorage.getItem('netgrey_admin_theme') || 'system';
        const savedContrast = localStorage.getItem('netgrey_admin_contrast') || 'standard';
        document.documentElement.setAttribute('data-theme', savedTheme);
        document.documentElement.setAttribute('data-contrast', savedContrast);

        const seg = el('themeSegmented');
        if (seg) {
            seg.querySelectorAll('button').forEach((btn) => {
                btn.classList.toggle('active', btn.dataset.themeVal === savedTheme);
            });
        }
        const contrastBtn = el('contrastToggleBtn');
        if (contrastBtn) {
            contrastBtn.setAttribute('aria-checked', savedContrast === 'high' ? 'true' : 'false');
        }
    }

    function setThemeMode(mode) {
        localStorage.setItem('netgrey_admin_theme', mode);
        applyAppearancePreferences();
    }

    function toggleContrastMode() {
        const cur = localStorage.getItem('netgrey_admin_contrast') || 'standard';
        localStorage.setItem('netgrey_admin_contrast', cur === 'high' ? 'standard' : 'high');
        applyAppearancePreferences();
    }

    async function apiFetch(url, options) {
        const opts = Object.assign({ credentials: 'same-origin', cache: 'no-store' }, options || {});
        const resp = await fetch(url, opts);
        let data = {};
        try {
            data = await resp.json();
        } catch (_) {
            data = {};
        }
        if (resp.status === 401 && url !== '/api/admin/login' && url !== '/api/admin/me') {
            handleUnauthorized();
            throw new Error(data.error || '登录会话已失效，请重新登录');
        }
        return { ok: resp.ok, status: resp.status, data };
    }

    function handleUnauthorized() {
        state.authenticated = false;
        stopAutoRefresh();
        closeDrawer();
        el('appShell').hidden = true;
        el('loginView').hidden = false;
    }

    function showGlobalError(msg) {
        const banner = el('globalErrorBanner');
        if (!banner) return;
        if (!msg) {
            banner.hidden = true;
            banner.textContent = '';
            return;
        }
        banner.textContent = msg;
        banner.hidden = false;
    }

    function startAutoRefresh() {
        stopAutoRefresh();
        state.refreshTimer = setInterval(() => {
            if (state.authenticated && !document.hidden) {
                loadActiveViewData(true);
            }
        }, 60000);
    }

    function stopAutoRefresh() {
        if (state.refreshTimer) {
            clearInterval(state.refreshTimer);
            state.refreshTimer = null;
        }
    }

    function showTooltip(evt, htmlText) {
        const tip = el('chartTooltip');
        if (!tip) return;
        tip.innerHTML = htmlText;
        tip.hidden = false;
        const pad = 12;
        const x = Math.min(window.innerWidth - 250, evt.clientX + pad);
        const y = Math.max(12, evt.clientY - 36);
        tip.style.left = x + 'px';
        tip.style.top = y + 'px';
    }

    function hideTooltip() {
        const tip = el('chartTooltip');
        if (tip) tip.hidden = true;
    }

    function commonQueryParams() {
        const qs = new URLSearchParams({
            range: state.range,
            platform: state.platform,
            include_sim: state.includeSim,
        });
        return qs.toString();
    }

    async function checkSessionAndBoot() {
        try {
            const res = await apiFetch('/api/admin/me');
            if (res.ok && res.data && res.data.authenticated) {
                enterDashboard(res.data.username || 'admin');
                return;
            }
        } catch (_) {}
        el('loginView').hidden = false;
        el('appShell').hidden = true;
    }

    function enterDashboard(username) {
        state.authenticated = true;
        state.username = username || 'admin';
        el('adminUsernameLabel').textContent = state.username;
        el('loginView').hidden = true;
        el('appShell').hidden = false;
        switchView(state.activeView);
        startAutoRefresh();
    }

    function switchView(viewName) {
        state.activeView = viewName;
        el('viewHeading').textContent = VIEW_TITLES[viewName] || '管理后台';
        document.querySelectorAll('.nav-item').forEach((btn) => {
            const active = btn.dataset.view === viewName;
            btn.classList.toggle('active', active);
            btn.setAttribute('aria-current', active ? 'page' : 'false');
        });
        document.querySelectorAll('.mobile-nav-item').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.view === viewName);
        });
        el('viewOverview').hidden = viewName !== 'overview';
        el('viewInstallations').hidden = viewName !== 'installations';
        el('viewSections').hidden = viewName !== 'sections';
        el('viewDistribution').hidden = viewName !== 'distribution';
        loadActiveViewData(false);
    }

    async function loadActiveViewData(silent) {
        if (!state.authenticated) return;
        if (!silent) showGlobalError('');
        try {
            if (state.activeView === 'overview') {
                await loadOverview();
            } else if (state.activeView === 'installations') {
                await loadInstallations();
            } else if (state.activeView === 'sections') {
                await loadSections();
            } else if (state.activeView === 'distribution') {
                await loadDistribution();
            }
            el('lastUpdatedBadge').textContent = '同步于 ' + new Date().toLocaleTimeString('zh-HK', { hour12: false });
        } catch (err) {
            showGlobalError(err.message || '数据加载失败，请检查网络连接');
        }
    }

    async function loadOverview() {
        const res = await apiFetch('/api/admin/overview?' + commonQueryParams());
        if (!res.ok || !res.data.ok) throw new Error(res.data.error || '总览数据加载失败');
        state.overviewData = res.data;
        renderOverview(res.data);
    }

    function renderOverview(data) {
        const kpis = data.kpis || {};
        const pCounts = data.platform_counts || {};
        el('navInstallCount').textContent = String(kpis.total_installs || 0);

        const kpiCards = [
            {
                label: '累计安装设备',
                value: String(kpis.total_installs || 0),
                sub: 'iOS ' + (pCounts.ios || 0) + ' · iPad ' + (pCounts.ipados || 0) + ' · Android ' + (pCounts.android || 0),
                badge: '+' + (kpis.new_installs || 0) + ' 区间新增',
                badgeClass: 'info',
            },
            {
                label: '当前在线设备',
                value: String(kpis.online_now || 0),
                sub: '5 分钟内有心跳或活跃上报',
                badge: (kpis.online_now || 0) > 0 ? '实时在线' : '暂无在线',
                badgeClass: (kpis.online_now || 0) > 0 ? 'success' : '',
            },
            {
                label: '活跃设备 (D / W / M)',
                value: (kpis.dau || 0) + ' / ' + (kpis.wau || 0) + ' / ' + (kpis.mau || 0),
                sub: '区间活跃 ' + (kpis.active_devices || 0) + ' 台 · 会话 ' + (kpis.session_count || 0) + ' 次',
            },
            {
                label: '区间软件总停留',
                value: formatDuration(kpis.total_dwell_sec || 0),
                sub: '平均每次停留 ' + formatDuration(kpis.avg_session_sec || 0),
            },
            {
                label: 'OTA 与 TestFlight',
                value: (kpis.ota_udid_devices || 0) + ' 台 / ' + (kpis.testflight_invites || 0) + ' 人',
                sub: '已登记 UDID 设备 / TF 邀请记录',
            },
            {
                label: '已注册用户账号',
                value: String(kpis.registered_accounts || 0),
                sub: 'PocketBase 账号系统实有用户',
            },
        ];

        el('overviewKpis').innerHTML = kpiCards.map((c) => {
            const badgeHtml = c.badge ? `<span class="pill ${escapeHtml(c.badgeClass || '')}">${escapeHtml(c.badge)}</span>` : '';
            return `
                <div class="kpi-card">
                    <div class="kpi-label"><span>${escapeHtml(c.label)}</span>${badgeHtml}</div>
                    <div class="kpi-value">${escapeHtml(c.value)}</div>
                    <div class="kpi-sub">${escapeHtml(c.sub)}</div>
                </div>
            `;
        }).join('');

        renderTrendChart(el('trendChartContainer'), data.daily_trend || [], state.trendMetric);
        renderSectionsBars(el('sectionsOverviewContainer'), data.sections_breakdown || []);
        renderPlatformDonut(el('platformDonutContainer'), data.platform_counts || {}, data.form_counts || {});
        renderVersionDistribution(el('versionDistContainer'), data.version_distribution || [], data.latest_release || {});
        renderHourlyChart(el('hourlyChartContainer'), data.hourly_distribution || []);
    }

    function renderTrendChart(container, series, metricKey) {
        if (!series.length) {
            container.innerHTML = '<div class="empty-state">当前时间范围暂无活跃趋势数据</div>';
            return;
        }
        const width = 560;
        const height = 210;
        const pad = { top: 18, right: 18, bottom: 32, left: 46 };
        const plotW = width - pad.left - pad.right;
        const plotH = height - pad.top - pad.bottom;

        const values = series.map((d) => Number(d[metricKey] || 0));
        const maxVal = Math.max(1, ...values);

        const coords = series.map((d, idx) => {
            const x = series.length === 1
                ? pad.left + plotW / 2
                : pad.left + (idx / (series.length - 1)) * plotW;
            const y = pad.top + plotH - (Number(d[metricKey] || 0) / maxVal) * plotH;
            return { x, y, d };
        });

        const linePath = coords.map((p, i) => (i === 0 ? 'M' : 'L') + p.x.toFixed(1) + ',' + p.y.toFixed(1)).join(' ');
        const areaPath = coords.length > 1
            ? `${linePath} L${coords[coords.length - 1].x.toFixed(1)},${(pad.top + plotH).toFixed(1)} L${coords[0].x.toFixed(1)},${(pad.top + plotH).toFixed(1)} Z`
            : '';

        const yTicks = [0, 0.5, 1].map((ratio) => {
            const val = Math.round(maxVal * ratio);
            const y = pad.top + plotH - ratio * plotH;
            const lbl = metricKey === 'dwell_sec' ? formatCompactDuration(val) : String(val);
            return `
                <line x1="${pad.left}" y1="${y.toFixed(1)}" x2="${width - pad.right}" y2="${y.toFixed(1)}" stroke="var(--border-hairline)" stroke-dasharray="3 3"/>
                <text x="${pad.left - 8}" y="${(y + 4).toFixed(1)}" text-anchor="end" fill="var(--text-tertiary)" font-size="11">${escapeHtml(lbl)}</text>
            `;
        }).join('');

        const labelStep = Math.max(1, Math.ceil(series.length / 6));
        const xLabels = coords
            .filter((_, i) => i % labelStep === 0 || i === coords.length - 1)
            .map((p) => `<text x="${p.x.toFixed(1)}" y="${height - 8}" text-anchor="middle" fill="var(--text-tertiary)" font-size="11">${escapeHtml(p.d.day.slice(5))}</text>`)
            .join('');

        const dots = coords.map((p, i) => `
            <circle class="trend-pt" data-idx="${i}" cx="${p.x.toFixed(1)}" cy="${p.y.toFixed(1)}" r="4.5" fill="var(--accent)" stroke="var(--bg-surface)" stroke-width="2" style="cursor:pointer"/>
        `).join('');

        container.innerHTML = `
            <svg viewBox="0 0 ${width} ${height}" width="100%" height="${height}" role="img" aria-label="每日趋势折线图">
                ${yTicks}
                ${areaPath ? `<path d="${areaPath}" fill="var(--accent-soft)"/>` : ''}
                ${coords.length > 1 ? `<path d="${linePath}" fill="none" stroke="var(--accent)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>` : ''}
                ${dots}
                ${xLabels}
            </svg>
        `;

        container.querySelectorAll('.trend-pt').forEach((node) => {
            const idx = Number(node.dataset.idx);
            const item = series[idx];
            if (!item) return;
            const text = `<strong>${escapeHtml(item.day)}</strong><br>停留：${escapeHtml(formatDuration(item.dwell_sec))}<br>活跃设备：${item.active_devices} 台 · 会话：${item.sessions} 次`;
            node.addEventListener('mouseenter', (e) => showTooltip(e, text));
            node.addEventListener('mouseleave', hideTooltip);
        });
    }

    function renderSectionsBars(container, list) {
        const maxDwell = Math.max(1, ...list.map((x) => Number(x.dwell_sec || 0)));
        const hasAny = list.some((x) => x.dwell_sec > 0);
        if (!hasAny) {
            container.innerHTML = '<div class="empty-state">暂无板块停留数据（客户端启动并浏览板块后自动汇总）</div>';
            return;
        }
        container.innerHTML = `<div class="bar-list">${list.map((item) => {
            const pct = Math.max(2, Math.round((item.dwell_sec / maxDwell) * 100));
            return `
                <button type="button" class="bar-row" data-jump-section="${escapeHtml(item.section)}">
                    <div class="bar-row-head">
                        <span class="bar-row-name">
                            <span class="dot-swatch" style="background:${sectionColor(item.section)}"></span>
                            <span>${escapeHtml(sectionLabel(item.section))}</span>
                        </span>
                        <span class="bar-row-val">${escapeHtml(formatDuration(item.dwell_sec))} · ${item.share_pct}% · ${item.active_devices} 台</span>
                    </div>
                    <div class="bar-track">
                        <div class="bar-fill" style="width:${item.dwell_sec > 0 ? pct : 0}%; background:${sectionColor(item.section)}"></div>
                    </div>
                </button>
            `;
        }).join('')}</div>`;

        container.querySelectorAll('[data-jump-section]').forEach((btn) => {
            btn.addEventListener('click', () => {
                state.selectedSection = btn.dataset.jumpSection;
                switchView('sections');
            });
        });
    }

    function renderPlatformDonut(container, pCounts, formCounts) {
        const entries = [
            { key: 'ios', label: 'iPhone (iOS)', count: pCounts.ios || 0, color: 'var(--sec-video)' },
            { key: 'ipados', label: 'iPad (iPadOS)', count: pCounts.ipados || 0, color: 'var(--sec-comics)' },
            { key: 'android', label: 'Android', count: pCounts.android || 0, color: 'var(--sec-forum)' },
        ];
        const total = entries.reduce((a, b) => a + b.count, 0);
        if (total === 0) {
            container.innerHTML = '<div class="empty-state">暂无已上报设备</div>';
            return;
        }
        const radius = 48;
        const circumference = 2 * Math.PI * radius;
        let offset = 0;
        const arcs = entries.filter((e) => e.count > 0).map((e) => {
            const len = (e.count / total) * circumference;
            const arc = `<circle cx="64" cy="64" r="${radius}" fill="none" stroke="${e.color}" stroke-width="18" stroke-dasharray="${len.toFixed(2)} ${circumference.toFixed(2)}" stroke-dashoffset="${(-offset).toFixed(2)}" transform="rotate(-90 64 64)"/>`;
            offset += len;
            return arc;
        }).join('');

        const formSummary = Object.entries(formCounts)
            .map(([k, v]) => `${FORM_LABELS[k] || k} ${v}`)
            .join(' · ');

        container.innerHTML = `
            <div class="donut-wrap">
                <svg width="128" height="128" viewBox="0 0 128 128" role="img" aria-label="平台安装占比圆环图">
                    <circle cx="64" cy="64" r="${radius}" fill="none" stroke="var(--bg-surface-secondary)" stroke-width="18"/>
                    ${arcs}
                    <text x="64" y="61" text-anchor="middle" fill="var(--text-primary)" font-size="18" font-weight="700">${total}</text>
                    <text x="64" y="77" text-anchor="middle" fill="var(--text-tertiary)" font-size="11">总设备</text>
                </svg>
                <div class="legend-list">
                    ${entries.map((e) => `
                        <div class="legend-item">
                            <span class="legend-left"><span class="dot-swatch" style="background:${e.color}"></span>${escapeHtml(e.label)}</span>
                            <strong class="tabular">${e.count} (${Math.round((e.count / total) * 100)}%)</strong>
                        </div>
                    `).join('')}
                    <div class="cell-secondary" style="margin-top:6px">设备形态：${escapeHtml(formSummary || '—')}</div>
                </div>
            </div>
        `;
    }

    function renderVersionDistribution(container, versions, latestRelease) {
        const latestVer = latestRelease && latestRelease.version ? latestRelease.version : '';
        el('latestVersionSubtitle').textContent = latestVer ? ('当前线上最新版本：v' + latestVer) : '各客户端版本分布';
        if (!versions.length) {
            container.innerHTML = '<div class="empty-state">暂无版本分布数据</div>';
            return;
        }
        const total = Math.max(1, versions.reduce((a, b) => a + b.count, 0));
        container.innerHTML = `<div class="bar-list">${versions.slice(0, 8).map((v) => {
            const pct = Math.round((v.count / total) * 100);
            const isLatest = latestVer && v.version.startsWith('v' + latestVer);
            const color = isLatest ? 'var(--success)' : 'var(--info)';
            return `
                <div style="display:flex; flex-direction:column; gap:4px;">
                    <div class="bar-row-head">
                        <span class="bar-row-name">${escapeHtml(v.version)} ${isLatest ? '<span class="pill success">最新</span>' : ''}</span>
                        <span class="bar-row-val">${v.count} 台 (${pct}%)</span>
                    </div>
                    <div class="bar-track"><div class="bar-fill" style="width:${Math.max(3, pct)}%; background:${color}"></div></div>
                </div>
            `;
        }).join('')}</div>`;
    }

    function renderHourlyChart(container, hours) {
        const maxDwell = Math.max(1, ...hours.map((h) => Number(h.dwell_sec || 0)));
        if (!hours.some((h) => h.dwell_sec > 0)) {
            container.innerHTML = '<div class="empty-state">暂无时段分布数据</div>';
            return;
        }
        const w = 360;
        const h = 175;
        const pad = { top: 14, right: 8, bottom: 26, left: 8 };
        const plotW = w - pad.left - pad.right;
        const plotH = h - pad.top - pad.bottom;
        const barW = plotW / 24;

        const bars = hours.map((item) => {
            const ratio = item.dwell_sec / maxDwell;
            const bh = Math.max(item.dwell_sec > 0 ? 4 : 2, ratio * plotH);
            const x = pad.left + item.hour * barW + 1.5;
            const y = pad.top + plotH - bh;
            return `<rect class="hour-bar" data-hour="${item.hour}" x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${Math.max(4, barW - 3).toFixed(1)}" height="${bh.toFixed(1)}" rx="3" fill="${item.dwell_sec > 0 ? 'var(--accent)' : 'var(--bg-surface-secondary)'}" style="cursor:pointer"/>`;
        }).join('');

        const labels = [0, 6, 12, 18, 23].map((hr) => {
            const x = pad.left + hr * barW + barW / 2;
            return `<text x="${x.toFixed(1)}" y="${h - 6}" text-anchor="middle" fill="var(--text-tertiary)" font-size="10">${String(hr).padStart(2, '0')}时</text>`;
        }).join('');

        container.innerHTML = `
            <svg viewBox="0 0 ${w} ${h}" width="100%" height="${h}" role="img" aria-label="24小时香港时间活跃柱状图">
                ${bars}
                ${labels}
            </svg>
        `;

        container.querySelectorAll('.hour-bar').forEach((node) => {
            const hr = Number(node.dataset.hour);
            const item = hours[hr];
            if (!item) return;
            node.addEventListener('mouseenter', (e) => {
                showTooltip(e, `<strong>${String(hr).padStart(2, '0')}:00 – ${String(hr).padStart(2, '0')}:59 HKT</strong><br>停留：${escapeHtml(formatDuration(item.dwell_sec))}<br>活跃设备：${item.active_devices} 台`);
            });
            node.addEventListener('mouseleave', hideTooltip);
        });
    }

    async function loadInstallations() {
        const qs = new URLSearchParams({
            range: state.range,
            platform: state.platform,
            channel: state.channel,
            include_sim: state.includeSim,
            search: state.search,
            sort: state.sort,
            order: 'desc',
        });
        const res = await apiFetch('/api/admin/installations?' + qs.toString());
        if (!res.ok || !res.data.ok) throw new Error(res.data.error || '设备列表加载失败');
        state.installationsData = res.data;
        renderInstallations(res.data);
    }

    function renderInstallations(data) {
        const items = data.items || [];
        el('navInstallCount').textContent = String(data.total || items.length || 0);
        el('installationsCountBar').textContent = `共筛选出 ${items.length} 台设备 · 点击任意行查看详情与编辑备注名`;

        const tbody = el('installationsTbody');
        if (!items.length) {
            tbody.innerHTML = '<tr><td colspan="8"><div class="empty-state">暂无符合条件的设备记录</div></td></tr>';
            return;
        }

        tbody.innerHTML = items.map((d) => {
            const displayTitle = d.alias || d.device_model || d.install_id;
            const subLine = d.alias
                ? `${d.device_model || '未知型号'} · ${d.install_id.slice(0, 12)}`
                : d.install_id.slice(0, 18);
            const acctText = d.account_email
                ? (d.account_name ? `${d.account_name} (${d.account_email})` : d.account_email)
                : '匿名使用';
            const channelClass = (d.install_channel === 'simulator' || d.install_channel === 'debug') ? 'warning' : 'success';
            const topSec = d.top_section
                ? `<span class="pill" style="border:1px solid ${sectionColor(d.top_section)}; color:var(--text-primary)">${escapeHtml(sectionLabel(d.top_section))}</span>`
                : '<span class="cell-secondary">—</span>';

            return `
                <tr class="clickable" data-install-id="${escapeHtml(d.install_id)}">
                    <td>
                        <div class="cell-primary">
                            <span class="${d.is_online ? 'online-dot' : 'offline-dot'}" title="${d.is_online ? '5分钟内在线' : '离线'}"></span>
                            <span>${escapeHtml(displayTitle)}</span>
                        </div>
                        <div class="cell-secondary">${escapeHtml(subLine)}</div>
                    </td>
                    <td>
                        <div><strong>${escapeHtml(PLATFORM_LABELS[d.platform] || d.platform)}</strong> · ${escapeHtml(FORM_LABELS[d.device_form] || d.device_form)}</div>
                        <div class="cell-secondary">v${escapeHtml(d.app_version || '?')} (${d.app_build || 0}) · ${escapeHtml(d.os_version || '')}</div>
                    </td>
                    <td>
                        <div><span class="pill ${channelClass}">${escapeHtml(CHANNEL_LABELS[d.install_channel] || d.install_channel)}</span></div>
                        <div class="cell-secondary">${escapeHtml(acctText)}</div>
                    </td>
                    <td class="tabular">${escapeHtml(d.first_seen_str || '—')}</td>
                    <td class="tabular">
                        <div>${escapeHtml(d.last_seen_str || '—')}</div>
                        <div class="cell-secondary">${d.is_online ? '当前在线' : ''}</div>
                    </td>
                    <td class="num">
                        <strong>${escapeHtml(formatDuration(d.range_dwell_sec))}</strong>
                        <div class="cell-secondary">${d.range_sessions} 次</div>
                    </td>
                    <td class="num">
                        <strong>${escapeHtml(formatDuration(d.total_dwell_sec))}</strong>
                        <div class="cell-secondary">${d.total_sessions} 次</div>
                    </td>
                    <td>${topSec}</td>
                </tr>
            `;
        }).join('');

        tbody.querySelectorAll('tr[data-install-id]').forEach((row) => {
            row.addEventListener('click', () => openDeviceDrawer(row.dataset.installId));
        });
    }

    async function openDeviceDrawer(installId) {
        state.selectedInstallId = installId;
        el('drawerBackdrop').hidden = false;
        el('deviceDrawer').hidden = false;
        el('drawerTitle').textContent = '加载设备画像…';
        el('drawerSubtitle').textContent = installId;
        el('drawerBody').innerHTML = '<div class="empty-state">正在读取设备历史与板块明细…</div>';

        try {
            const res = await apiFetch(`/api/admin/installations/${encodeURIComponent(installId)}?range=${encodeURIComponent(state.range)}`);
            if (!res.ok || !res.data.ok) throw new Error(res.data.error || '加载详情失败');
            renderDeviceDrawer(res.data);
        } catch (err) {
            el('drawerBody').innerHTML = `<div class="inline-alert error">${escapeHtml(err.message)}</div>`;
        }
    }

    function renderDeviceDrawer(data) {
        const inst = data.installation || {};
        el('drawerTitle').textContent = inst.alias || inst.device_model || inst.install_id;
        el('drawerSubtitle').textContent = `Install ID: ${inst.install_id} · ${inst.is_online ? '当前在线' : '离线'}`;

        const secTotals = data.section_totals || [];
        const maxSecDwell = Math.max(1, ...secTotals.map((s) => s.dwell_sec || 0));
        const subSections = data.sub_sections || [];
        const sessions = data.sessions || [];
        const daily = data.daily_trend || [];

        const secBarsHtml = secTotals.length
            ? `<div class="bar-list">${secTotals.map((s) => {
                const pct = Math.max(3, Math.round((s.dwell_sec / maxSecDwell) * 100));
                return `
                    <div>
                        <div class="bar-row-head">
                            <span class="bar-row-name"><span class="dot-swatch" style="background:${sectionColor(s.section)}"></span>${escapeHtml(sectionLabel(s.section))}</span>
                            <span class="bar-row-val">${escapeHtml(formatDuration(s.dwell_sec))}</span>
                        </div>
                        <div class="bar-track"><div class="bar-fill" style="width:${pct}%; background:${sectionColor(s.section)}"></div></div>
                    </div>
                `;
            }).join('')}</div>`
            : '<div class="empty-state">当前时间范围无板块停留记录</div>';

        const subHtml = subSections.length
            ? `<div class="timeline-tags" style="margin-top:8px;">${subSections.map((sb) => `<span class="pill">${escapeHtml(SUBSECTION_LABELS[sb.sub_section] || sb.sub_section)}: ${escapeHtml(formatDuration(sb.dwell_sec))}</span>`).join('')}</div>`
            : '';

        const dailyHtml = daily.length
            ? `<div class="bar-list">${daily.slice(-10).map((d) => {
                const maxD = Math.max(1, ...daily.map((x) => x.dwell_sec || 0));
                const pct = Math.max(3, Math.round((d.dwell_sec / maxD) * 100));
                return `
                    <div>
                        <div class="bar-row-head">
                            <span class="tabular">${escapeHtml(d.day)}</span>
                            <span class="bar-row-val">${escapeHtml(formatDuration(d.dwell_sec))} · ${d.sessions} 次</span>
                        </div>
                        <div class="bar-track"><div class="bar-fill" style="width:${pct}%; background:var(--info)"></div></div>
                    </div>
                `;
            }).join('')}</div>`
            : '<div class="empty-state">暂无每日汇总</div>';

        const sessionsHtml = sessions.length
            ? `<div class="timeline-list">${sessions.map((s) => {
                const tags = Object.entries(s.sections || {})
                    .sort((a, b) => b[1] - a[1])
                    .map(([k, v]) => `<span class="pill">${escapeHtml(sectionLabel(k))} ${escapeHtml(formatDuration(v))}</span>`)
                    .join('');
                return `
                    <div class="timeline-item">
                        <div class="timeline-head">
                            <span>${escapeHtml(s.started_str)} – ${escapeHtml(s.ended_str)}</span>
                            <span>${escapeHtml(formatDuration(s.dwell_sec))} ${s.is_active ? '<span class="pill success">进行中</span>' : ''}</span>
                        </div>
                        <div class="cell-secondary">版本 v${escapeHtml(s.app_version || '?')} (${s.app_build || 0})</div>
                        ${tags ? `<div class="timeline-tags">${tags}</div>` : ''}
                    </div>
                `;
            }).join('')}</div>`
            : '<div class="empty-state">暂无会话记录</div>';

        el('drawerBody').innerHTML = `
            <section>
                <h3 class="detail-section-title">管理员备注</h3>
                <form id="deviceMetaForm" class="stack-form">
                    <div class="field-group">
                        <label for="metaAliasInput" class="field-label">备注名（例如：Randy 主力 iPhone）</label>
                        <input id="metaAliasInput" type="text" class="text-input" maxlength="64" value="${escapeHtml(inst.alias || '')}" placeholder="设置易识别的人名或设备备注">
                    </div>
                    <div class="field-group">
                        <label for="metaNotesInput" class="field-label">备注说明</label>
                        <textarea id="metaNotesInput" class="textarea-input" maxlength="500" placeholder="记录使用人角色、测试设备归属等">${escapeHtml(inst.notes || '')}</textarea>
                    </div>
                    <div id="metaSaveAlert" class="inline-alert" hidden></div>
                    <button type="submit" class="btn primary">保存备注信息</button>
                </form>
            </section>

            <section>
                <h3 class="detail-section-title">设备画像</h3>
                <div class="meta-kv-grid">
                    <div><div class="meta-kv-label">平台与形态</div><div class="meta-kv-value">${escapeHtml(PLATFORM_LABELS[inst.platform] || inst.platform)} · ${escapeHtml(FORM_LABELS[inst.device_form] || inst.device_form)}</div></div>
                    <div><div class="meta-kv-label">设备型号 / 系统</div><div class="meta-kv-value">${escapeHtml(inst.device_model || '—')} · ${escapeHtml(inst.os_version || '—')}</div></div>
                    <div><div class="meta-kv-label">客户端版本</div><div class="meta-kv-value">v${escapeHtml(inst.app_version || '?')} (Build ${inst.app_build || 0})</div></div>
                    <div><div class="meta-kv-label">安装渠道</div><div class="meta-kv-value">${escapeHtml(CHANNEL_LABELS[inst.install_channel] || inst.install_channel)}</div></div>
                    <div><div class="meta-kv-label">关联登录账号</div><div class="meta-kv-value">${escapeHtml(inst.account_email || '未登录（匿名）')}</div></div>
                    <div><div class="meta-kv-label">语言 / 时区 / IP</div><div class="meta-kv-value">${escapeHtml(inst.locale || '—')} · ${escapeHtml(inst.timezone || '—')} · ${escapeHtml(inst.last_ip || '—')}</div></div>
                    <div><div class="meta-kv-label">首次安装上报</div><div class="meta-kv-value tabular">${escapeHtml(inst.first_seen_str || '—')}</div></div>
                    <div><div class="meta-kv-label">最近活跃 / 总停留</div><div class="meta-kv-value tabular">${escapeHtml(inst.last_seen_str || '—')} · ${escapeHtml(formatDuration(inst.total_dwell_sec || 0))}</div></div>
                </div>
            </section>

            <section>
                <h3 class="detail-section-title">各板块与子行为停留（当前筛选范围）</h3>
                ${secBarsHtml}
                ${subHtml}
            </section>

            <section>
                <h3 class="detail-section-title">每日停留时长趋势</h3>
                ${dailyHtml}
            </section>

            <section>
                <h3 class="detail-section-title">最近会话时间线（最多 50 条）</h3>
                ${sessionsHtml}
            </section>
        `;

        el('deviceMetaForm').addEventListener('submit', async (evt) => {
            evt.preventDefault();
            const alias = el('metaAliasInput').value;
            const notes = el('metaNotesInput').value;
            const alertEl = el('metaSaveAlert');
            alertEl.hidden = true;
            try {
                const saveRes = await apiFetch(`/api/admin/installations/${encodeURIComponent(inst.install_id)}/meta`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ alias, notes }),
                });
                if (!saveRes.ok || !saveRes.data.ok) {
                    throw new Error(saveRes.data.error || '保存失败');
                }
                alertEl.className = 'inline-alert success';
                alertEl.textContent = '备注已保存';
                alertEl.hidden = false;
                el('drawerTitle').textContent = alias.trim() || inst.device_model || inst.install_id;
                if (state.activeView === 'installations') loadInstallations();
                if (state.activeView === 'sections') loadSections();
            } catch (err) {
                alertEl.className = 'inline-alert error';
                alertEl.textContent = err.message || '保存失败';
                alertEl.hidden = false;
            }
        });
    }

    function closeDrawer() {
        el('drawerBackdrop').hidden = true;
        el('deviceDrawer').hidden = true;
        state.selectedInstallId = null;
    }

    async function loadSections() {
        const res = await apiFetch('/api/admin/sections?' + commonQueryParams());
        if (!res.ok || !res.data.ok) throw new Error(res.data.error || '板块分析加载失败');
        state.sectionsData = res.data;
        renderSectionsReport(res.data);
    }

    function renderSectionsReport(data) {
        const sections = data.sections || [];
        const grid = el('sectionCardsGrid');
        if (!sections.some((s) => s.section === state.selectedSection)) {
            state.selectedSection = (sections[0] && sections[0].section) || 'video';
        }

        grid.innerHTML = sections.map((s) => {
            const active = s.section === state.selectedSection;
            return `
                <button type="button" class="sec-card ${active ? 'active' : ''}" data-sec-key="${escapeHtml(s.section)}">
                    <div class="sec-card-top">
                        <span><span class="dot-swatch" style="background:${sectionColor(s.section)}; margin-right:6px;"></span>${escapeHtml(sectionLabel(s.section))}</span>
                        <span class="pill">${s.share_pct}%</span>
                    </div>
                    <div class="sec-card-val">${escapeHtml(formatDuration(s.dwell_sec))}</div>
                    <div class="sec-card-meta">${s.active_devices} 台活跃 · ${s.visits} 次访问 · 人均 ${escapeHtml(formatDuration(s.avg_per_device_sec))}</div>
                </button>
            `;
        }).join('');

        grid.querySelectorAll('[data-sec-key]').forEach((btn) => {
            btn.addEventListener('click', () => {
                state.selectedSection = btn.dataset.secKey;
                renderSectionsReport(state.sectionsData);
            });
        });

        const selected = sections.find((s) => s.section === state.selectedSection) || sections[0];
        if (!selected) return;

        el('selectedSectionTitle').textContent = `${sectionLabel(selected.section)} · 子行为与每日趋势`;
        el('selectedSectionSubtitle').textContent = `总停留 ${formatDuration(selected.dwell_sec)} · 活跃设备 ${selected.active_devices} 台`;

        const subs = selected.sub_sections || [];
        const maxSub = Math.max(1, ...subs.map((x) => x.dwell_sec || 0));
        el('subSectionBreakdownContainer').innerHTML = subs.length
            ? subs.map((sb) => {
                const pct = Math.max(3, Math.round((sb.dwell_sec / maxSub) * 100));
                return `
                    <div>
                        <div class="bar-row-head">
                            <span class="bar-row-name">${escapeHtml(SUBSECTION_LABELS[sb.sub_section] || sb.sub_section)}</span>
                            <span class="bar-row-val">${escapeHtml(formatDuration(sb.dwell_sec))} · ${sb.visits} 次 · ${sb.active_devices} 台</span>
                        </div>
                        <div class="bar-track"><div class="bar-fill" style="width:${pct}%; background:${sectionColor(selected.section)}"></div></div>
                    </div>
                `;
            }).join('')
            : '<div class="empty-state">该板块暂无子行为明细</div>';

        renderTrendChart(
            el('sectionTrendChartContainer'),
            (selected.daily_trend || []).map((d) => ({
                day: d.day,
                dwell_sec: d.dwell_sec,
                active_devices: selected.active_devices,
                sessions: selected.visits,
            })),
            'dwell_sec'
        );

        const topBody = el('sectionTopDevicesTbody');
        const tops = selected.top_devices || [];
        topBody.innerHTML = tops.length
            ? tops.map((row) => `
                <tr class="clickable" data-install-id="${escapeHtml(row.install_id)}">
                    <td>
                        <div class="cell-primary">${escapeHtml(row.alias || row.device_model || row.install_id)}</div>
                        <div class="cell-secondary">${escapeHtml(row.account_email || row.install_id.slice(0, 16))}</div>
                    </td>
                    <td>${escapeHtml(PLATFORM_LABELS[row.platform] || row.platform)}</td>
                    <td class="num">${row.visits}</td>
                    <td class="num"><strong>${escapeHtml(formatDuration(row.dwell_sec))}</strong></td>
                </tr>
            `).join('')
            : '<tr><td colspan="4"><div class="empty-state">暂无该板块设备停留数据</div></td></tr>';

        topBody.querySelectorAll('tr[data-install-id]').forEach((row) => {
            row.addEventListener('click', () => openDeviceDrawer(row.dataset.installId));
        });
    }

    async function loadDistribution() {
        const res = await apiFetch('/api/admin/distribution');
        if (!res.ok || !res.data.ok) throw new Error(res.data.error || '分发与账号数据加载失败');
        state.distributionData = res.data;
        renderDistribution(res.data);
    }

    function renderDistribution(data) {
        const accounts = data.accounts || [];
        const otaDevices = data.ota_devices || [];
        const invites = data.testflight_invites || [];
        const dlSummary = data.download_summary_30d || [];
        const dlRecent = data.recent_download_events || [];

        el('distSubPanelAccounts').innerHTML = `
            <div class="card-header">
                <div>
                    <h2 class="card-title">PocketBase 注册账号（${accounts.length} 人）</h2>
                    <p class="card-subtitle">只读同步服务器账号数据库并关联各账号绑定设备的累计使用停留</p>
                </div>
            </div>
            <div class="table-wrap">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>邮箱</th>
                            <th>昵称</th>
                            <th>验证状态</th>
                            <th class="num">关联设备</th>
                            <th class="num">累计停留</th>
                            <th>注册时间</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${accounts.length ? accounts.map((a) => `
                            <tr>
                                <td><strong>${escapeHtml(a.email)}</strong></td>
                                <td>${escapeHtml(a.name || '—')}</td>
                                <td><span class="pill ${a.verified ? 'success' : 'warning'}">${a.verified ? '已验证' : '未验证'}</span></td>
                                <td class="num">${a.device_count} 台</td>
                                <td class="num"><strong>${escapeHtml(formatDuration(a.total_dwell_sec))}</strong></td>
                                <td class="tabular">${escapeHtml((a.created || '').slice(0, 19))}</td>
                            </tr>
                        `).join('') : '<tr><td colspan="6"><div class="empty-state">暂无注册账号</div></td></tr>'}
                    </tbody>
                </table>
            </div>
        `;

        el('distSubPanelOta').innerHTML = `
            <div class="card-header">
                <div>
                    <h2 class="card-title">iOS OTA 已登记设备 UDID（${otaDevices.length} 台）</h2>
                    <p class="card-subtitle">与当前 IPA 签名内嵌描述文件 provisioned_udids.json 实时比对签入状态</p>
                </div>
            </div>
            <div class="table-wrap">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>设备名称 / 型号</th>
                            <th>UDID</th>
                            <th>系统版本</th>
                            <th>Apple 名录</th>
                            <th>当前 IPA 已签入</th>
                            <th>登记时间</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${otaDevices.length ? otaDevices.map((d) => `
                            <tr>
                                <td>
                                    <div class="cell-primary">${escapeHtml(d.device_name || d.product || 'iOS Device')}</div>
                                    <div class="cell-secondary">${escapeHtml(d.product || '')}</div>
                                </td>
                                <td class="tabular" style="font-family:monospace; font-size:12px;">${escapeHtml(d.udid)}</td>
                                <td>${escapeHtml(d.version || '—')}</td>
                                <td><span class="pill ${d.apple_registered ? 'success' : 'warning'}">${d.apple_registered ? '已登记' : '待登记'}</span></td>
                                <td><span class="pill ${d.provisioned_in_ipa ? 'success' : 'warning'}">${d.provisioned_in_ipa ? '已签入可直装' : '待重签打包'}</span></td>
                                <td class="tabular">${escapeHtml(d.last_seen || d.registered_at || '—')}</td>
                            </tr>
                        `).join('') : '<tr><td colspan="6"><div class="empty-state">暂无 UDID 设备记录</div></td></tr>'}
                    </tbody>
                </table>
            </div>
        `;

        el('distSubPanelTf').innerHTML = `
            <div class="card-header">
                <div>
                    <h2 class="card-title">TestFlight 内部测试邀请记录（${invites.length} 条）</h2>
                    <p class="card-subtitle">实时追踪 Apple 团队邀请与自动拉入 Internal 测试群组状态</p>
                </div>
            </div>
            <div class="table-wrap">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>Apple ID 邮箱</th>
                            <th>状态</th>
                            <th>最新状态说明</th>
                            <th class="num">重试</th>
                            <th>更新时间</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${invites.length ? invites.map((inv) => {
                            const cls = inv.status === 'added_to_group' ? 'success' : (inv.status === 'error' ? 'danger' : 'warning');
                            return `
                                <tr>
                                    <td><strong>${escapeHtml(inv.email)}</strong></td>
                                    <td><span class="pill ${cls}">${escapeHtml(inv.status)}</span></td>
                                    <td style="max-width:340px;">${escapeHtml(inv.message || '')}</td>
                                    <td class="num">${inv.retry_count}</td>
                                    <td class="tabular">${escapeHtml(inv.updated_at || inv.created_at || '—')}</td>
                                </tr>
                            `;
                        }).join('') : '<tr><td colspan="5"><div class="empty-state">暂无 TestFlight 邀请记录</div></td></tr>'}
                    </tbody>
                </table>
            </div>
        `;

        const DL_LABELS = {
            page_view: '下载页访问',
            android_apk_click: '点击下载 Android APK',
            ios_ota_install_click: '点击 iOS OTA 直装',
            ios_udid_enroll_click: '申请 UDID 授权描述文件',
            ios_testflight_submit: '提交 TestFlight 邀请',
        };

        el('downloadFunnelCard').innerHTML = `
            <div class="card-header">
                <div>
                    <h2 class="card-title">近 30 天下载页行为漏斗</h2>
                    <p class="card-subtitle">来自 https://bananarey.us 下载引导页的匿名点击统计</p>
                </div>
            </div>
            ${dlSummary.length ? `
                <div class="bar-list" style="margin-bottom:16px;">
                    ${dlSummary.map((r) => `
                        <div class="bar-row-head">
                            <span>${escapeHtml(DL_LABELS[r.event_type] || r.event_type)} (${escapeHtml(r.platform || 'web')})</span>
                            <strong class="tabular">${r.cnt} 次</strong>
                        </div>
                    `).join('')}
                </div>
            ` : '<div class="empty-state">暂无下载页点击事件</div>'}
            <h3 class="detail-section-title">最近下载页动作</h3>
            <div class="table-wrap">
                <table class="data-table">
                    <thead><tr><th>事件</th><th>平台</th><th>版本</th><th>时间</th></tr></thead>
                    <tbody>
                        ${dlRecent.length ? dlRecent.slice(0, 12).map((ev) => `
                            <tr>
                                <td>${escapeHtml(DL_LABELS[ev.event_type] || ev.event_type)}</td>
                                <td>${escapeHtml(ev.platform || '—')}</td>
                                <td>${escapeHtml(ev.app_version || '—')}</td>
                                <td class="tabular">${escapeHtml(ev.created_str)}</td>
                            </tr>
                        `).join('') : '<tr><td colspan="4"><div class="empty-state">暂无记录</div></td></tr>'}
                    </tbody>
                </table>
            </div>
        `;
    }

    function switchDistSubtab(key) {
        state.activeSubtab = key;
        document.querySelectorAll('#distSubtabs .subtab-btn').forEach((btn) => {
            const active = btn.dataset.subtab === key;
            btn.classList.toggle('active', active);
            btn.setAttribute('aria-selected', active ? 'true' : 'false');
        });
        el('distSubPanelAccounts').hidden = key !== 'accounts';
        el('distSubPanelOta').hidden = key !== 'ota';
        el('distSubPanelTf').hidden = key !== 'tf';
        el('distSubPanelFunnel').hidden = key !== 'funnel';
    }

    function bindEvents() {
        applyAppearancePreferences();

        el('loginForm').addEventListener('submit', async (evt) => {
            evt.preventDefault();
            const username = el('loginUsername').value.trim() || 'admin';
            const password = el('loginPassword').value;
            const errBox = el('loginError');
            const submitBtn = el('loginSubmitBtn');
            errBox.hidden = true;
            submitBtn.disabled = true;
            submitBtn.textContent = '正在验证…';
            try {
                const res = await apiFetch('/api/admin/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username, password }),
                });
                if (!res.ok || !res.data.ok) {
                    errBox.textContent = (res.data && res.data.error) || '登录失败';
                    errBox.hidden = false;
                    return;
                }
                el('loginPassword').value = '';
                enterDashboard(res.data.username || username);
            } catch (err) {
                errBox.textContent = err.message || '网络异常，请重试';
                errBox.hidden = false;
            } finally {
                submitBtn.disabled = false;
                submitBtn.textContent = '登录控制台';
            }
        });

        el('loginThemeToggle').addEventListener('click', () => {
            const cur = localStorage.getItem('netgrey_admin_theme') || 'system';
            const next = cur === 'dark' ? 'light' : 'dark';
            setThemeMode(next);
        });

        el('loginContrastToggle').addEventListener('click', toggleContrastMode);
        el('contrastToggleBtn').addEventListener('click', toggleContrastMode);

        el('themeSegmented').querySelectorAll('button').forEach((btn) => {
            btn.addEventListener('click', () => setThemeMode(btn.dataset.themeVal));
        });

        document.querySelectorAll('.nav-item, .mobile-nav-item').forEach((btn) => {
            btn.addEventListener('click', () => switchView(btn.dataset.view));
        });

        el('rangeFilter').querySelectorAll('button').forEach((btn) => {
            btn.addEventListener('click', () => {
                state.range = btn.dataset.range;
                el('rangeFilter').querySelectorAll('button').forEach((b) => b.classList.toggle('active', b === btn));
                loadActiveViewData(false);
            });
        });

        el('platformFilter').addEventListener('change', (e) => {
            state.platform = e.target.value;
            loadActiveViewData(false);
        });

        el('simFilter').addEventListener('change', (e) => {
            state.includeSim = e.target.value;
            loadActiveViewData(false);
        });

        el('trendMetricToggle').querySelectorAll('button').forEach((btn) => {
            btn.addEventListener('click', () => {
                state.trendMetric = btn.dataset.metric;
                el('trendMetricToggle').querySelectorAll('button').forEach((b) => b.classList.toggle('active', b === btn));
                if (state.overviewData) {
                    renderTrendChart(el('trendChartContainer'), state.overviewData.daily_trend || [], state.trendMetric);
                }
            });
        });

        let searchTimer = null;
        el('installSearchInput').addEventListener('input', (e) => {
            state.search = e.target.value;
            clearTimeout(searchTimer);
            searchTimer = setTimeout(() => {
                if (state.activeView === 'installations') loadInstallations();
            }, 250);
        });

        el('channelFilter').addEventListener('change', (e) => {
            state.channel = e.target.value;
            loadInstallations();
        });

        el('installSortSelect').addEventListener('change', (e) => {
            state.sort = e.target.value;
            loadInstallations();
        });

        el('refreshBtn').addEventListener('click', () => loadActiveViewData(false));

        el('exportCsvBtn').addEventListener('click', () => {
            const url = `/api/admin/export.csv?range=${encodeURIComponent(state.range)}&include_sim=${encodeURIComponent(state.includeSim)}`;
            window.location.href = url;
        });

        document.querySelectorAll('#distSubtabs .subtab-btn').forEach((btn) => {
            btn.addEventListener('click', () => switchDistSubtab(btn.dataset.subtab));
        });

        async function doLogout() {
            try {
                await apiFetch('/api/admin/logout', { method: 'POST' });
            } catch (_) {}
            handleUnauthorized();
        }
        el('logoutBtn').addEventListener('click', doLogout);
        el('securityLogoutBtn').addEventListener('click', doLogout);

        el('changePasswordForm').addEventListener('submit', async (evt) => {
            evt.preventDefault();
            const oldPassword = el('oldPasswordInput').value;
            const newPassword = el('newPasswordInput').value;
            const confirmPassword = el('confirmPasswordInput').value;
            const msgBox = el('changePwdMessage');
            msgBox.hidden = true;
            if (newPassword !== confirmPassword) {
                msgBox.className = 'inline-alert error';
                msgBox.textContent = '两次输入的新密码不一致';
                msgBox.hidden = false;
                return;
            }
            try {
                const res = await apiFetch('/api/admin/change-password', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ old_password: oldPassword, new_password: newPassword }),
                });
                if (!res.ok || !res.data.ok) {
                    throw new Error(res.data.error || '修改密码失败');
                }
                msgBox.className = 'inline-alert success';
                msgBox.textContent = res.data.message || '密码修改成功';
                msgBox.hidden = false;
                el('oldPasswordInput').value = '';
                el('newPasswordInput').value = '';
                el('confirmPasswordInput').value = '';
            } catch (err) {
                msgBox.className = 'inline-alert error';
                msgBox.textContent = err.message || '修改失败';
                msgBox.hidden = false;
            }
        });

        el('drawerCloseBtn').addEventListener('click', closeDrawer);
        el('drawerBackdrop').addEventListener('click', closeDrawer);
        document.addEventListener('keydown', (evt) => {
            if (evt.key === 'Escape' && !el('deviceDrawer').hidden) {
                closeDrawer();
            }
        });
    }

    document.addEventListener('DOMContentLoaded', () => {
        bindEvents();
        checkSessionAndBoot();
    });
})();
