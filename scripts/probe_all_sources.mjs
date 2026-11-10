import https from 'https';
import http from 'http';
import { URL } from 'url';

const sources = [
  // 视频源 (19 + 1)
  { type: '视频', id: 'huavod', name: '华视影院', url: 'https://huavod.com', referer: 'https://huavod.com/' },
  { 
    type: '视频', 
    id: 'huangguo', 
    name: '黄果短剧', 
    url: 'https://huangguoai.com/recommend', 
    referer: 'https://huangguoai.com/'
  },
  { type: '视频', id: '91porn', name: '91视频', url: 'https://h1014.sol148.com/index.php', referer: 'https://h1014.sol148.com/' },
  { type: '视频', id: '91dongman', name: '91动漫', url: 'https://91dongman.net', referer: 'https://91dongman.net/' },
  { type: '视频', id: 'beeg', name: 'Beeg', url: 'https://beeg.com/api/v6/index/main/0/pc', referer: 'https://beeg.com/' },
  { type: '视频', id: 'xvideos', name: 'XVideos', url: 'https://www.xvideos.com/new/1/', referer: 'https://www.xvideos.com/' },
  { type: '视频', id: 'missav', name: 'MissAV', url: 'https://missav.media', referer: 'https://missav.media/' },
  { type: '视频', id: 'kissjav', name: 'KissJAV', url: 'https://kissjav.li', referer: 'https://kissjav.li/' },
  { type: '视频', id: '85po', name: '85po', url: 'https://www.85po.com', referer: 'https://www.85po.com/' },
  { 
    type: '视频', 
    id: 'pornhub', 
    name: 'Pornhub', 
    url: 'https://jp.pornhub.com/video?o=mv', 
    headers: {
      'Cookie': 'age_verified=1; has_access=1; accessAgeDisclaimerPH=1; platform=mobile; bs=000000000000000022522ffa8ac94dcd; cookieConsent=3;'
    },
    referer: 'https://jp.pornhub.com/' 
  },
  { type: '视频', id: 'javnoni', name: 'JAV-NONI', url: 'https://javnoni.com', referer: 'https://javnoni.com/' },
  { type: '视频', id: 'javsb', name: 'JAVSB', url: 'https://jav.sb', headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' }, referer: 'https://jav.sb/' },
  { type: '视频', id: 'tokyomotion', name: 'TOKYO Motion', url: 'https://www.tokyomotion.net', referer: 'https://www.tokyomotion.net/' },
  { type: '视频', id: 'spankbang', name: 'SpankBang', url: 'https://www.spankbang.com', headers: { 'Cookie': 'age_pass=1; pg_interstitial_v5=1; player_quality=1080; backend_version=main' }, referer: 'https://www.spankbang.com/' },
  { type: '视频', id: 'xhamster', name: 'xHamster', url: 'https://xhamster.com', referer: 'https://xhamster.com/' },
  { type: '视频', id: 'eporner', name: 'EPORNER', url: 'https://www.eporner.com/api/v2/video/search/?query=asian&per_page=10', referer: 'https://www.eporner.com/' },
  { type: '视频', id: 'mrdouga', name: 'MRDOUGA', url: 'https://mrdouga.com', referer: 'https://mrdouga.com/' },
  { type: '视频', id: 'ohentai', name: 'OHentai', url: 'https://ohentai.org', headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' }, referer: 'https://ohentai.org/' },
  { type: '视频', id: 'xnxx', name: 'XNXX', url: 'https://www.xnxx.com/best', referer: 'https://www.xnxx.com/' },

  // 漫画源 (10)
  { type: '漫画', id: 'baozi', name: '包子漫画', url: 'https://cn.webmota.com', referer: 'https://cn.webmota.com/' },
  { type: '漫画', id: 'jm', name: '禁漫天堂', url: 'https://www.cdnhjk.net/categories/filter?o=mr', referer: 'https://www.cdnhjk.net/' },
  { type: '漫画', id: 'picacg', name: '哔咔漫画', url: 'https://picaapi.picacomic.com/categories', headers: { 'api-key': 'C69BAF41DA5ABD1FFEDC6D2FEA56B', 'app-channel': '3' }, referer: 'https://picaapi.picacomic.com/' },
  { type: '漫画', id: 'komiic', name: 'Komiic', url: 'https://komiic.com', referer: 'https://komiic.com/' },
  { type: '漫画', id: 'dongman91_comic', name: '91动漫漫画', url: 'https://91dongman.net/manga/tongrenzhi/', referer: 'https://91dongman.net/' },
  { type: '漫画', id: '8muses', name: '8Muses', url: 'https://8muses.io/popular', headers: { 'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36' }, referer: 'https://8muses.io/' },
  { type: '漫画', id: 'gedecomix', name: 'GEDE Comix', url: 'https://gedecomix.com', referer: 'https://gedecomix.com/' },
  { type: '漫画', id: 'copy', name: '拷贝漫画', url: 'https://api.2024manga.com/api/v3/comics?limit=10&platform=3', referer: 'https://api.2024manga.com/' },
  { type: '漫画', id: 'ikmmh', name: '爱看漫画', url: 'https://ymcdnyfqdapp.ikmmh.com', referer: 'https://ymcdnyfqdapp.ikmmh.com/' },
  { type: '漫画', id: 'ehentai', name: 'E-Hentai', url: 'https://e-hentai.org', referer: 'https://e-hentai.org/' },

  // 直播源 (3)
  { type: '直播', id: 'chaturbate', name: 'Chaturbate', url: 'https://chaturbate.com/api/public/affiliates/onlinerooms/?wm=9RhSM&client_ip=request_ip&gender=f&limit=5', referer: 'https://chaturbate.com/' },
  { type: '直播', id: 'stripchat', name: 'Stripchat', url: 'https://stripchat.com/api/front/v2/models?limit=5&primaryTag=girls', referer: 'https://stripchat.com/' },
  { type: '直播', id: 'hornystudents', name: 'HornyStudents', url: 'https://www.hornystudents.com/', referer: 'https://www.hornystudents.com/' },

  // 论坛源 (2)
  { type: '论坛', id: '91forum', name: '91社区自拍区', url: 'https://t924.sol140.com/forumdisplay.php?fid=4', referer: 'https://t924.sol140.com/' },
  { type: '论坛', id: 't66y', name: '草榴社区技术区', url: 'https://t66y.com/thread0806.php?fid=7', referer: 'https://t66y.com/' },
];

const agentOptions = { rejectUnauthorized: false };
const httpsAgent = new https.Agent(agentOptions);
const httpAgent = new http.Agent();

function probe(source) {
  return new Promise((resolve) => {
    const startTime = Date.now();
    const u = new URL(source.url);
    const isHttps = u.protocol === 'https:';
    const client = isHttps ? https : http;

    const reqHeaders = {
      'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
      'Accept': '*/*',
      'Referer': source.referer || `${u.protocol}//${u.host}/`,
      ...(source.headers || {})
    };

    const req = client.request(u, {
      method: source.method || 'GET',
      agent: isHttps ? httpsAgent : httpAgent,
      headers: reqHeaders,
      timeout: 12000,
    }, (res) => {
      let bytes = 0;
      let chunks = [];
      res.on('data', (c) => {
        bytes += c.length;
        if (chunks.length < 5) chunks.push(c);
      });
      res.on('end', () => {
        const durationMs = Date.now() - startTime;
        resolve({
          ...source,
          status: res.statusCode,
          durationMs,
          bytes,
          success: res.statusCode >= 200 && res.statusCode < 400,
          error: null,
        });
      });
    });

    req.on('timeout', () => {
      req.destroy();
      resolve({
        ...source,
        status: 0,
        durationMs: Date.now() - startTime,
        bytes: 0,
        success: false,
        error: 'TIMEOUT (12s)',
      });
    });

    req.on('error', (err) => {
      resolve({
        ...source,
        status: 0,
        durationMs: Date.now() - startTime,
        bytes: 0,
        success: false,
        error: err.message,
      });
    });

    if (source.body) {
      req.write(source.body);
    }
    req.end();
  });
}

async function main() {
  console.log(`\n======================================================`);
  console.log(`🚀 开始探测全量 ${sources.length} 个数据源连通性 (Permissive SSL 模拟)`);
  console.log(`======================================================\n`);

  const results = await Promise.all(sources.map(probe));

  let passed = 0;
  let failed = 0;

  for (const r of results) {
    const icon = r.success ? '✅ PASS' : '❌ FAIL';
    const statusStr = r.status ? `HTTP ${r.status}` : 'CONNECT_FAIL';
    const errStr = r.error ? ` [${r.error}]` : '';
    console.log(`${icon} [${r.type}] ${r.name.padEnd(12)} -> ${statusStr} | ${r.durationMs}ms | ${r.bytes} bytes${errStr}`);
    if (r.success) passed++; else failed++;
  }

  console.log(`\n------------------------------------------------------`);
  console.log(`探测汇总: 总计 ${sources.length} 个源 | 成功: ${passed} | 失败/异常: ${failed}`);
  console.log(`------------------------------------------------------\n`);
  
  if (failed > 0) {
    const failedSources = results.filter(r => !r.success);
    console.log(`需重点排查的异常源:`);
    for (const f of failedSources) {
      console.log(`- [${f.type}] ${f.name} (${f.id}): ${f.url} -> ${f.status || f.error}`);
    }
  }
}

main().catch(console.error);
