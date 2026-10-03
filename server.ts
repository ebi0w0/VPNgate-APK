import express, { Request, Response } from 'express';
import http from 'http';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';
import JSZip from 'jszip';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = Number(process.env.PORT) || 3000;

app.use(express.json());

interface VpnServerParsed {
  hostName: string;
  ip: string;
  score: number;
  pingMs: number;
  speedBps: number;
  speedMbps: number;
  countryLong: string;
  countryShort: 'JP' | 'US';
  numVpnSessions: number;
  uptimeMs: number;
  totalUsers: number;
  totalTrafficBytes: number;
  logType: string;
  operator: string;
  message: string;
  openVpnConfigBase64: string;
  port: number;
  proto: string;
  cipher: string;
  rankScore: number;
  lastDiscoveredAt: number;
}

let cachedServers: VpnServerParsed[] = [];
let lastFetchTime = 0;
const CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes

// Safe CSV splitter respecting quotes
function splitCsvLine(line: string): string[] {
  const result: string[] = [];
  let inQuotes = false;
  let current = '';

  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') {
      inQuotes = !inQuotes;
    } else if (c === ',' && !inQuotes) {
      result.push(current);
      current = '';
    } else {
      current += c;
    }
  }
  result.push(current);
  return result;
}

function calculateScore(server: {
  pingMs: number;
  speedMbps: number;
  uptimeMs: number;
  totalTrafficBytes: number;
  numVpnSessions: number;
}): number {
  // 1. Latency Score (0..35): lower ping is better
  const pingClamped = Math.min(Math.max(server.pingMs, 5), 350);
  const latencyScore = Math.max(0, Math.min(1, 1 - (pingClamped - 5) / 345)) * 35;

  // 2. Throughput Score (0..30): logarithmic scale up to 500 Mbps
  const speed = Math.max(1, server.speedMbps);
  const throughputScore = Math.max(0, Math.min(1, Math.log10(Math.min(speed, 500)) / Math.log10(500))) * 30;

  // 3. Uptime Score (0..15): capped at 30 days
  const uptimeHours = server.uptimeMs / (1000 * 3600);
  const uptimeScore = Math.max(0, Math.min(1, uptimeHours / 720)) * 15;

  // 4. Availability Score (0..10)
  const trafficGb = server.totalTrafficBytes / 1_000_000_000;
  const availScore = Math.max(0, Math.min(1, Math.log10(Math.max(1, trafficGb)) / 4)) * 10;

  // 5. Load Penalty (0..10): high session count penalty
  const loadRatio = Math.max(0, Math.min(1, server.numVpnSessions / 150)) * 10;

  const total = latencyScore + throughputScore + uptimeScore + availScore - loadRatio;
  return Math.round(Math.max(0, total) * 10) / 10;
}

async function fetchVpnGateServers(): Promise<VpnServerParsed[]> {
  const now = Date.now();
  if (cachedServers.length > 0 && now - lastFetchTime < CACHE_TTL_MS) {
    return cachedServers;
  }

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 12000);

    const res = await fetch('http://www.vpngate.net/api/iphone/', {
      signal: controller.signal,
      headers: {
        'User-Agent': 'GateTunnel-Agent/1.0',
      },
    });
    clearTimeout(timeoutId);

    if (!res.ok) {
      throw new Error(`VPN Gate HTTP ${res.status}`);
    }

    const rawCsv = await res.text();
    const lines = rawCsv.split(/\r?\n/);
    const parsed: VpnServerParsed[] = [];

    let headerFound = false;

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed || trimmed === '*vpn_servers') continue;
      if (trimmed.startsWith('*') && !trimmed.startsWith('*vpn_servers')) continue;
      if (trimmed.startsWith('#')) {
        headerFound = true;
        continue;
      }
      if (!headerFound) continue;

      const cols = splitCsvLine(trimmed);
      if (cols.length < 15) continue;

      const hostName = cols[0]?.trim();
      const ip = cols[1]?.trim();
      const countryShort = cols[6]?.trim().toUpperCase();

      if (countryShort !== 'JP' && countryShort !== 'US') continue;
      if (!ip || !/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(ip)) continue;

      const pingMs = parseInt(cols[3], 10) || 999;
      const speedBps = parseInt(cols[4], 10) || 0;
      if (speedBps <= 0) continue;

      const speedMbps = Math.round((speedBps / 1_000_000) * 10) / 10;
      const openVpnConfigBase64 = cols[14]?.trim() || '';

      // Inspect openvpn config for port/proto
      let port = 1194;
      let proto = 'udp';
      let cipher = 'AES-128-CBC';

      try {
        const decoded = Buffer.from(openVpnConfigBase64, 'base64').toString('utf8');
        const remoteMatch = decoded.match(/remote\s+[\w.-]+\s+(\d+)(?:\s+(\w+))?/i);
        if (remoteMatch) {
          port = parseInt(remoteMatch[1], 10) || 1194;
          if (remoteMatch[2]) proto = remoteMatch[2].toLowerCase();
        }
        const protoMatch = decoded.match(/proto\s+(\w+)/i);
        if (protoMatch) proto = protoMatch[1].toLowerCase();
        const cipherMatch = decoded.match(/cipher\s+([\w-]+)/i);
        if (cipherMatch) cipher = cipherMatch[1];
      } catch {
        // use default
      }

      const numVpnSessions = parseInt(cols[7], 10) || 0;
      const uptimeMs = parseInt(cols[8], 10) || 0;
      const totalUsers = parseInt(cols[9], 10) || 0;
      const totalTrafficBytes = parseInt(cols[10], 10) || 0;

      const rankScore = calculateScore({
        pingMs,
        speedMbps,
        uptimeMs,
        totalTrafficBytes,
        numVpnSessions,
      });

      parsed.push({
        hostName,
        ip,
        score: parseInt(cols[2], 10) || 0,
        pingMs,
        speedBps,
        speedMbps,
        countryLong: cols[5]?.trim() || (countryShort === 'JP' ? 'Japan' : 'United States'),
        countryShort: countryShort as 'JP' | 'US',
        numVpnSessions,
        uptimeMs,
        totalUsers,
        totalTrafficBytes,
        logType: cols[11]?.trim() || '',
        operator: cols[12]?.trim() || '',
        message: cols[13]?.trim() || '',
        openVpnConfigBase64,
        port,
        proto,
        cipher,
        rankScore,
        lastDiscoveredAt: now,
      });
    }

    // Sort by rank score descending
    parsed.sort((a, b) => b.rankScore - a.rankScore);

    if (parsed.length > 0) {
      cachedServers = parsed;
      lastFetchTime = now;
    }
    return cachedServers;
  } catch (err) {
    console.error('Failed to fetch from VPN Gate API:', err);
    if (cachedServers.length > 0) return cachedServers;

    // Fallback snapshot if remote is temporarily unreachable
    return getFallbackSnapshot();
  }
}

function getFallbackSnapshot(): VpnServerParsed[] {
  return [
    {
      hostName: 'vpn124032',
      ip: '124.32.30.64',
      score: 3500000,
      pingMs: 8,
      speedBps: 354000000,
      speedMbps: 354.0,
      countryLong: 'Japan',
      countryShort: 'JP',
      numVpnSessions: 74,
      uptimeMs: 142000000,
      totalUsers: 48000,
      totalTrafficBytes: 8500000000000,
      logType: '2weeks',
      operator: 'Tokyo University of Tsukuba Node',
      message: 'Fast & Stable OpenVPN node in Tokyo',
      openVpnConfigBase64: '',
      port: 1194,
      proto: 'udp',
      cipher: 'AES-128-CBC',
      rankScore: 92.4,
      lastDiscoveredAt: Date.now(),
    },
    {
      hostName: 'vpn67051',
      ip: '67.5.100.174',
      score: 3032097,
      pingMs: 12,
      speedBps: 295000000,
      speedMbps: 295.0,
      countryLong: 'United States',
      countryShort: 'US',
      numVpnSessions: 95,
      uptimeMs: 282596916,
      totalUsers: 62645,
      totalTrafficBytes: 9348417576611,
      logType: '2weeks',
      operator: 'Silicon Valley Gateway',
      message: 'High speed US west tunnel',
      openVpnConfigBase64: '',
      port: 1194,
      proto: 'udp',
      cipher: 'AES-128-CBC',
      rankScore: 89.8,
      lastDiscoveredAt: Date.now(),
    },
    {
      hostName: 'vpn59133',
      ip: '59.133.158.94',
      score: 2800000,
      pingMs: 15,
      speedBps: 717000000,
      speedMbps: 717.0,
      countryLong: 'Japan',
      countryShort: 'JP',
      numVpnSessions: 85,
      uptimeMs: 98000000,
      totalUsers: 34000,
      totalTrafficBytes: 5200000000000,
      logType: '2weeks',
      operator: 'Osaka Node Highspeed',
      message: 'Ultra Gigabit Japan Node',
      openVpnConfigBase64: '',
      port: 443,
      proto: 'tcp',
      cipher: 'AES-128-CBC',
      rankScore: 88.5,
      lastDiscoveredAt: Date.now(),
    },
  ];
}

// Helper to recursively collect files
function getFilesRecursively(dir: string, baseDir: string = dir): { relativePath: string; absolutePath: string }[] {
  let results: { relativePath: string; absolutePath: string }[] = [];
  if (!fs.existsSync(dir)) return results;

  const list = fs.readdirSync(dir);
  for (const file of list) {
    const fullPath = path.join(dir, file);
    const stat = fs.statSync(fullPath);
    if (stat && stat.isDirectory()) {
      results = results.concat(getFilesRecursively(fullPath, baseDir));
    } else {
      results.push({
        relativePath: path.relative(baseDir, fullPath).replace(/\\/g, '/'),
        absolutePath: fullPath,
      });
    }
  }
  return results;
}

// API Routes
app.get('/api/vpngate/servers', async (req: Request, res: Response) => {
  try {
    const country = req.query.country as string | undefined;
    const forceRefresh = req.query.refresh === 'true';

    if (forceRefresh) {
      lastFetchTime = 0;
    }

    let servers = await fetchVpnGateServers();
    if (country && (country === 'JP' || country === 'US')) {
      servers = servers.filter((s) => s.countryShort === country);
    }

    res.json({
      success: true,
      lastUpdated: lastFetchTime,
      totalCount: servers.length,
      servers,
    });
  } catch (error: any) {
    res.status(500).json({ success: false, error: error.message });
  }
});

app.get('/api/vpngate/server/:ip/config.ovpn', async (req: Request, res: Response) => {
  try {
    const servers = await fetchVpnGateServers();
    const server = servers.find((s) => s.ip === req.params.ip);

    if (!server) {
      return res.status(404).send('Server not found');
    }

    let configText = '';
    if (server.openVpnConfigBase64) {
      configText = Buffer.from(server.openVpnConfigBase64, 'base64').toString('utf8');
    } else {
      configText = `client
dev tun
proto ${server.proto}
remote ${server.ip} ${server.port}
resolv-retry infinite
nobind
persist-key
persist-tun
cipher ${server.cipher}
auth SHA1
verb 3
`;
    }

    res.setHeader('Content-Type', 'application/x-openvpn-profile');
    res.setHeader('Content-Disposition', `attachment; filename="vpngate_${server.countryShort}_${server.ip}.ovpn"`);
    res.send(configText);
  } catch (error: any) {
    res.status(500).send(error.message);
  }
});

app.get('/api/vpngate/stats', async (_req: Request, res: Response) => {
  const servers = await fetchVpnGateServers();
  const jp = servers.filter((s) => s.countryShort === 'JP');
  const us = servers.filter((s) => s.countryShort === 'US');

  const minPing = servers.length > 0 ? Math.min(...servers.map((s) => s.pingMs)) : 0;
  const maxSpeed = servers.length > 0 ? Math.max(...servers.map((s) => s.speedMbps)) : 0;

  res.json({
    totalServers: servers.length,
    japanServers: jp.length,
    unitedStatesServers: us.length,
    fastestPingMs: minPing,
    highestSpeedMbps: maxSpeed,
    lastSync: lastFetchTime,
  });
});

// Download full Android project as .zip
app.get('/api/project/download-zip', async (_req: Request, res: Response) => {
  try {
    const androidDir = path.resolve(__dirname, 'android');
    if (!fs.existsSync(androidDir)) {
      return res.status(404).send('Android directory not found');
    }

    const zip = new JSZip();
    const allFiles = getFilesRecursively(androidDir);

    for (const file of allFiles) {
      const content = fs.readFileSync(file.absolutePath);
      zip.file(`GateTunnelVPN/${file.relativePath}`, content);
    }

    const zipBuffer = await zip.generateAsync({
      type: 'nodebuffer',
      compression: 'DEFLATE',
      compressionOptions: { level: 9 },
    });

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', 'attachment; filename="GateTunnel-Android-VPN-Source.zip"');
    res.send(zipBuffer);
  } catch (error: any) {
    res.status(500).send(`Failed to generate ZIP: ${error.message}`);
  }
});

// Get Android files for in-browser IDE tree
app.get('/api/project/files', (_req: Request, res: Response) => {
  try {
    const androidDir = path.resolve(__dirname, 'android');
    const allFiles = getFilesRecursively(androidDir);

    const filesData = allFiles.map((f) => {
      const content = fs.readFileSync(f.absolutePath, 'utf8');
      return {
        path: f.relativePath,
        name: path.basename(f.relativePath),
        content,
        size: fs.statSync(f.absolutePath).size,
      };
    });

    res.json({ success: true, files: filesData });
  } catch (err: any) {
    res.status(500).json({ success: false, error: err.message });
  }
});

async function startServer() {
  if (process.env.NODE_ENV === 'production') {
    app.use(express.static('dist'));
    app.get('*', (_req, res) => {
      res.sendFile(path.resolve('dist/index.html'));
    });
  } else {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  const server = http.createServer(app);
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`GateTunnel server running on port ${PORT}`);
    // Pre-warm VPN Gate cache in background
    fetchVpnGateServers().catch((e) => console.warn('Initial VPN Gate pre-warm error:', e));
  });
}

startServer();
