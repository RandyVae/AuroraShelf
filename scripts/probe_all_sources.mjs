import crypto from 'crypto';
import fs from 'fs';
import http from 'http';
import https from 'https';
import path from 'path';
import { fileURLToPath, URL } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PROJECT_ROOT = path.resolve(__dirname, '..');

function loadLocalSecrets() {
  const secrets = {};
  const propsPath = path.join(PROJECT_ROOT, 'source-secrets.properties');
  if (fs.existsSync(propsPath)) {
    try {
      const content = fs.readFileSync(propsPath, 'utf8');
      for (const rawLine of content.split(/\r?\n/)) {
        const line = rawLine.trim();
        if (!line || line.startsWith('#')) continue;
        const eqIdx = line.indexOf('=');
        if (eqIdx <= 0) continue;
        const key = line.slice(0, eqIdx).trim();
        const val = line.slice(eqIdx + 1).trim();
        if (key && val) {
          secrets[key] = val;
        }
      }
    } catch (_) {
      // Ignore unreadable local properties file
    }
  }
  for (const key of [
    'PICACG_API_KEY',
    'PICACG_SIGNATURE_SECRET',
    'JM_TOKEN_SECRET',
    'JM_TOKEN_SECRET_2',
    'JM_DATA_SECRET',
  ]) {
    if (process.env[key] && process.env[key].trim()) {
      secrets[key] = process.env[key].trim();
    }
  }
  return secrets;
}

function buildPicacgHeaders(secrets, requestPath = 'categories', method = 'GET') {
  const apiKey = secrets.PICACG_API_KEY || '';
  const sigSecret = secrets.PICACG_SIGNATURE_SECRET || '';
  if (!apiKey) {
    return { headers: { 'app-channel': '3' }, hasApiKey: false };
  }
  const ts = String(Math.floor(Date.now() / 1000));
  const nonce = crypto.randomBytes(16).toString('hex');
  const headers = {
    'api-key': apiKey,
    'accept': 'application/vnd.picacomic.com.v1+json',
    'app-channel': '3',
    'time': ts,
    'nonce': nonce,
    'app-version': '2.2.1.3.3.4',
    'app-uuid': 'defaultUuid',
    'app-platform': 'android',
    'app-build-version': '45',
    'image-quality': 'original',
  };
  if (sigSecret) {
    const raw = `${requestPath}${ts}${nonce}${method}${apiKey}`.toLowerCase();
    headers.signature = crypto.createHmac('sha256', sigSecret).update(raw).digest('hex');
  }
  return { headers, hasApiKey: true };
}

function buildJmHeaders(secrets) {
  const tokenSecret = secrets.JM_TOKEN_SECRET || '';
  if (!tokenSecret) {
    return { headers: { Cookie: 'AVS=1', 'X-Requested-With': 'com.JMComic3.app' }, hasSecret: false };
  }
  const ts = String(Math.floor(Date.now() / 1000));
  const token = crypto.createHash('md5').update(`${ts}${tokenSecret}`).digest('hex');
  return {
    headers: {
      token,
      tokenparam: `${ts},2.1.2`,
      Cookie: 'AVS=1',
      'X-Requested-With': 'com.JMComic3.app',
    },
    hasSecret: true,
  };
}

const localSecrets = loadLocalSecrets();
const picacgCfg = buildPicacgHeaders(localSecrets, 'categories', 'GET');
const jmCfg = buildJmHeaders(localSecrets);

const sources = [
  // 视频源 (18)
  { type: '视频', id: 'huavod', name: '华视影院', url: 'https://huavod.com', referer: 'https://huavod.com/' },
  { type: '视频', id: 'huangguo', name: '黄果短剧', url: 'https://huangguoai.com/recommend', referer: 'https://huangguoai.com/' },
  { type: '视频', id: '91porn', name: '91视频', url: 'https://h1014.sol148.com/index.php', referer: 'https://h1014.sol148.com/' },
  { type: '视频', id: '91dongman', name: '91动漫', url: 'https://91dongman.net', referer: 'https://91dongman.net/' },
  { type: '视频', id: 'beeg', name: 'Beeg', url: 'https://beeg.com/api/v6/index/main/0/pc', referer: 'https://beeg.com/' },
  {
    type: '视频',
    id: '134x',
    name: '134X',
    url: 'https://134x.com/_serverFn/7cd2fdc202217221aea24ce98c8911133744658e2b912656f645ea53849d6f17?payload=%7B%22t%22%3A%7B%22t%22%3A10%2C%22i%22%3A0%2C%22p%22%3A%7B%22k%22%3A%5B%22data%22%5D%2C%22v%22%3A%5B%7B%22t%22%3A10%2C%22i%22%3A1%2C%22p%22%3A%7B%22k%22%3A%5B%22page%22%5D%2C%22v%22%3A%5B%7B%22t%22%3A0%2C%22s%22%3A1%7D%5D%7D%2C%22o%22%3A0%7D%5D%7D%2C%22o%22%3A0%7D%2C%22f%22%3A127%2C%22m%22%3A%5B%5D%7D',
    headers: {
      'Accept': 'application/json',
      'Sec-Fetch-Site': 'same-origin',
      'Sec-Fetch-Mode': 'cors',
      'Sec-Fetch-Dest': 'empty',
      'x-tsr-serverFn': 'true',
    },
    referer: 'https://134x.com/',
  },
  { type: '视频', id: 'xvideos', name: 'XVideos', url: 'https://www.xvideos.com/new/1/', referer: 'https://www.xvideos.com/' },
  { type: '视频', id: 'missav', name: 'MissAV', url: 'https://missav.media', referer: 'https://missav.media/', expectedAntiBot: true },
  { type: '视频', id: 'kissjav', name: 'KissJAV', url: 'https://kissjav.li', referer: 'https://kissjav.li/' },
  { type: '视频', id: '85po', name: '85po', url: 'https://www.85po.com/ja/latest-updates/', referer: 'https://www.85po.com/', expectedAntiBot: true },
  {
    type: '视频',
    id: 'pornhub',
    name: 'Pornhub',
    url: 'https://jp.pornhub.com/video?o=mv',
    headers: {
      'Cookie': 'age_verified=1; has_access=1; accessAgeDisclaimerPH=1; platform=mobile; bs=000000000000000022522ffa8ac94dcd; cookieConsent=3;',
    },
    referer: 'https://jp.pornhub.com/',
  },
  { type: '视频', id: 'tokyomotion', name: 'TOKYO Motion', url: 'https://www.tokyomotion.net', referer: 'https://www.tokyomotion.net/' },
  {
    type: '视频',
    id: 'spankbang',
    name: 'SpankBang',
    url: 'https://www.spankbang.com',
    headers: { 'Cookie': 'age_pass=1; pg_interstitial_v5=1; player_quality=1080; backend_version=main' },
    referer: 'https://www.spankbang.com/',
    expectedAntiBot: true,
  },
  { type: '视频', id: 'xhamster', name: 'xHamster', url: 'https://xhamster.com', referer: 'https://xhamster.com/', expectedAntiBot: true },
  { type: '视频', id: 'eporner', name: 'EPORNER', url: 'https://www.eporner.com/api/v2/video/search/?query=asian&per_page=10', referer: 'https://www.eporner.com/' },
  { type: '视频', id: 'mrdouga', name: 'MRDOUGA', url: 'https://mrdouga.com', referer: 'https://mrdouga.com/' },
  {
    type: '视频',
    id: 'ohentai',
    name: 'OHentai',
    url: 'https://ohentai.org',
    headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' },
    referer: 'https://ohentai.org/',
  },
  { type: '视频', id: 'xnxx', name: 'XNXX', url: 'https://www.xnxx.com/best', referer: 'https://www.xnxx.com/' },

  // 漫画源 (10)
  { type: '漫画', id: 'baozi', name: '包子漫画', url: 'https://cn.webmota.com', referer: 'https://cn.webmota.com/' },
  {
    type: '漫画',
    id: 'jm',
    name: '禁漫天堂',
    url: 'https://www.cdngwc.cc/categories/filter?o=mr',
    fallbackUrls: [
      'https://www.cdngwc.net/categories/filter?o=mr',
      'https://www.cdngwc.club/categories/filter?o=mr',
      'https://www.cdnhjk.net/categories/filter?o=mr',
    ],
    headers: jmCfg.headers,
    referer: 'https://www.cdngwc.cc/',
    expectedAntiBot: true,
    requiresProtocolSecret: true,
    missingLocalSecret: !jmCfg.hasSecret,
  },
  {
    type: '漫画',
    id: 'picacg',
    name: '哔咔漫画',
    url: 'https://picaapi.picacomic.com/categories',
    headers: picacgCfg.headers,
    referer: 'https://picaapi.picacomic.com/',
    requiresAuthentication: true,
    missingLocalSecret: !picacgCfg.hasApiKey,
  },
  { type: '漫画', id: 'komiic', name: 'Komiic', url: 'https://komiic.com', referer: 'https://komiic.com/' },
  { type: '漫画', id: 'dongman91_comic', name: '91动漫漫画', url: 'https://91dongman.net/manga/tongrenzhi/', referer: 'https://91dongman.net/' },
  {
    type: '漫画',
    id: '8muses',
    name: '8Muses',
    url: 'https://8muses.io/popular',
    headers: { 'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36' },
    referer: 'https://8muses.io/',
    expectedAntiBot: true,
  },
  { type: '漫画', id: 'gedecomix', name: 'GEDE Comix', url: 'https://gedecomix.com', referer: 'https://gedecomix.com/' },
  {
    type: '漫画',
    id: 'copy',
    name: '拷贝漫画',
    url: 'https://api.mangacopy.com/api/v3/comics?limit=10&platform=3',
    fallbackUrls: ['https://api.2024manga.com/api/v3/comics?limit=10&platform=3'],
    referer: 'https://api.mangacopy.com/',
  },
  { type: '漫画', id: 'ikmmh', name: '爱看漫画', url: 'https://ymcdnyfqdapp.ikmmh.com', referer: 'https://ymcdnyfqdapp.ikmmh.com/', expectedAntiBot: true },
  { type: '漫画', id: 'ehentai', name: 'E-Hentai', url: 'https://e-hentai.org', referer: 'https://e-hentai.org/' },

  // 直播源 (2 — 与客户端 Stripchat + HornyStudents 对齐)
  { type: '直播', id: 'stripchat', name: 'Stripchat', url: 'https://stripchat.com/api/front/v2/models?limit=5&primaryTag=girls', referer: 'https://stripchat.com/' },
  { type: '直播', id: 'hornystudents', name: 'HornyStudents', url: 'https://www.hornystudents.com/', referer: 'https://www.hornystudents.com/' },

  // 论坛源 (2 个主站覆盖 7 个版块)
  { type: '论坛', id: '91forum', name: '91社区自拍区', url: 'https://t924.sol140.com/forumdisplay.php?fid=4', referer: 'https://t924.sol140.com/' },
  { type: '论坛', id: 't66y', name: '草榴社区技术区', url: 'https://t66y.com/thread0806.php?fid=7', referer: 'https://t66y.com/' },
];

const REDIRECT_STATUSES = new Set([301, 302, 303, 307, 308]);
const MAX_REDIRECTS = 3;
const httpsAgent = new https.Agent({ rejectUnauthorized: false });
const httpAgent = new http.Agent();

function requestWithRedirects(targetUrl, source, redirectCount = 0) {
  return new Promise((resolve) => {
    let u;
    try {
      u = new URL(targetUrl);
    } catch (err) {
      resolve({ status: 0, bytes: 0, preview: '', finalUrl: targetUrl, redirects: redirectCount, error: `Invalid URL: ${err.message}` });
      return;
    }

    const isHttps = u.protocol === 'https:';
    const client = isHttps ? https : http;
    const method = redirectCount > 0 && source.method === 'POST' ? 'GET' : (source.method || 'GET');

    const reqHeaders = {
      'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1',
      'Accept': '*/*',
      'Referer': source.referer || `${u.protocol}//${u.host}/`,
      ...(source.headers || {}),
    };

    const req = client.request(
      u,
      {
        method,
        agent: isHttps ? httpsAgent : httpAgent,
        headers: reqHeaders,
        timeout: 12000,
      },
      (res) => {
        const code = res.statusCode || 0;
        const loc = res.headers.location;

        if (REDIRECT_STATUSES.has(code) && loc) {
          res.resume();
          if (redirectCount >= MAX_REDIRECTS) {
            resolve({
              status: code,
              bytes: 0,
              preview: '',
              finalUrl: u.toString(),
              redirects: redirectCount,
              error: `Exceeded max redirects (${MAX_REDIRECTS})`,
            });
            return;
          }
          let nextUrl;
          try {
            nextUrl = new URL(loc, u).toString();
          } catch (err) {
            resolve({
              status: code,
              bytes: 0,
              preview: '',
              finalUrl: u.toString(),
              redirects: redirectCount,
              error: `Invalid redirect Location: ${err.message}`,
            });
            return;
          }
          requestWithRedirects(nextUrl, source, redirectCount + 1).then(resolve);
          return;
        }

        let bytes = 0;
        const chunks = [];
        res.on('data', (c) => {
          bytes += c.length;
          if (chunks.length < 5) chunks.push(c);
        });
        res.on('end', () => {
          const preview = Buffer.concat(chunks).toString('utf8').trim();
          resolve({
            status: code,
            bytes,
            preview,
            finalUrl: u.toString(),
            redirects: redirectCount,
            error: null,
          });
        });
      },
    );

    req.on('timeout', () => {
      req.destroy();
      resolve({
        status: 0,
        bytes: 0,
        preview: '',
        finalUrl: targetUrl,
        redirects: redirectCount,
        error: 'TIMEOUT (12s)',
      });
    });

    req.on('error', (err) => {
      resolve({
        status: 0,
        bytes: 0,
        preview: '',
        finalUrl: targetUrl,
        redirects: redirectCount,
        error: err.message,
      });
    });

    if (method === 'POST' && source.body) {
      req.write(source.body);
    }
    req.end();
  });
}

function classifyOutcome(source, resp) {
  let jsonAppFailure = null;
  if (resp.preview.startsWith('{') || resp.preview.startsWith('[')) {
    try {
      const parsed = JSON.parse(resp.preview);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed) && parsed.code !== undefined) {
        const codeNum = Number(parsed.code);
        if (Number.isFinite(codeNum) && codeNum !== 200 && codeNum !== 0) {
          jsonAppFailure = `API code=${codeNum}${parsed.errorMsg || parsed.msg || parsed.message ? `: ${parsed.errorMsg || parsed.msg || parsed.message}` : ''}`;
        }
      }
    } catch (_) {
      // Truncated preview chunk; ignore JSON parse failure
    }
  }

  if (resp.status >= 200 && resp.status < 300 && !jsonAppFailure) {
    return { tier: 'PASS', note: resp.redirects > 0 ? `${resp.redirects} redirect(s)` : '', error: null };
  }

  if (source.requiresProtocolSecret && source.missingLocalSecret && jsonAppFailure && /code=401/.test(jsonAppFailure)) {
    return { tier: 'WARN', note: 'reachable, local JM_TOKEN_SECRET not configured', error: jsonAppFailure };
  }

  if (source.requiresAuthentication && [400, 401, 403].includes(resp.status)) {
    const reason = source.missingLocalSecret
      ? 'reachable, local PICACG_API_KEY not configured'
      : 'reachable, user login token required';
    return { tier: 'WARN', note: reason, error: jsonAppFailure };
  }

  const looksLikeWafChallenge =
    resp.status === 403 &&
    resp.bytes > 0 &&
    (source.expectedAntiBot || /cloudflare|just a moment|attention required|cf-ray|challenge/i.test(resp.preview));

  if (looksLikeWafChallenge) {
    return { tier: 'WARN', note: 'reachable, anti-bot/WAF 403 challenge', error: null };
  }

  return {
    tier: 'FAIL',
    note: '',
    error: resp.error || jsonAppFailure || (resp.status ? `Unexpected HTTP ${resp.status}` : 'CONNECT_FAIL'),
  };
}

async function probe(source) {
  const startTime = Date.now();
  const candidates = [source.url, ...(source.fallbackUrls || [])];
  let bestResp = null;
  let bestOutcome = null;

  for (const candidateUrl of candidates) {
    const resp = await requestWithRedirects(candidateUrl, {
      ...source,
      referer: `${new URL(candidateUrl).origin}/`,
    });
    const outcome = classifyOutcome(source, resp);
    bestResp = resp;
    bestOutcome = outcome;
    if (outcome.tier === 'PASS' || outcome.tier === 'WARN') {
      break;
    }
  }

  return {
    ...source,
    status: bestResp.status,
    finalUrl: bestResp.finalUrl,
    redirects: bestResp.redirects,
    durationMs: Date.now() - startTime,
    bytes: bestResp.bytes,
    tier: bestOutcome.tier,
    note: bestOutcome.note,
    error: bestOutcome.error,
  };
}

async function main() {
  console.log(`\n======================================================================`);
  console.log(`开始探测全量 ${sources.length} 个数据源连通性 (最多跟随 ${MAX_REDIRECTS} 次重定向)`);
  console.log(`======================================================================\n`);

  const results = await Promise.all(sources.map(probe));

  let passed = 0;
  let warned = 0;
  let failed = 0;

  for (const r of results) {
    let icon = 'FAIL';
    if (r.tier === 'PASS') {
      icon = 'PASS';
      passed += 1;
    } else if (r.tier === 'WARN') {
      icon = 'WARN';
      warned += 1;
    } else {
      icon = 'FAIL';
      failed += 1;
    }

    const statusStr = r.status ? `HTTP ${r.status}` : 'CONNECT_FAIL';
    const noteStr = r.note ? ` (${r.note})` : '';
    const errStr = r.error && r.tier === 'FAIL' ? ` [${r.error}]` : '';
    console.log(`[${icon}] [${r.type}] ${r.name.padEnd(14)} -> ${statusStr}${noteStr} | ${r.durationMs}ms | ${r.bytes} bytes${errStr}`);
  }

  console.log(`\n----------------------------------------------------------------------`);
  console.log(`探测汇总: 总计 ${sources.length} | PASS: ${passed} | WARN / RESTRICTED: ${warned} | FAIL: ${failed}`);
  console.log(`----------------------------------------------------------------------\n`);

  if (warned > 0) {
    console.log(`受限 / 需凭据或反爬挑战源 (WARN / RESTRICTED):`);
    for (const w of results.filter((r) => r.tier === 'WARN')) {
      console.log(`- [${w.type}] ${w.name} (${w.id}): HTTP ${w.status} - ${w.note}`);
    }
    console.log('');
  }

  if (failed > 0) {
    console.log(`异常阻断源 (FAIL):`);
    for (const f of results.filter((r) => r.tier === 'FAIL')) {
      console.log(`- [${f.type}] ${f.name} (${f.id}): ${f.finalUrl} -> ${f.error || f.status}`);
    }
    console.log('');
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
