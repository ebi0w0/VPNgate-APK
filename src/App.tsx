import React, { useState, useEffect, useMemo } from 'react';
import {
  Shield,
  Wifi,
  Zap,
  ArrowRight,
  Server,
  Download,
  Copy,
  Check,
  RefreshCw,
  ExternalLink,
  ChevronRight,
  Sliders,
  Terminal,
  FileCode,
  Info,
  CheckCircle2,
  Clock,
  Layers,
  Activity,
  Globe2,
  ChevronDown,
  X,
  Play,
  RotateCcw
} from 'lucide-react';

interface VpnServer {
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

interface FileEntry {
  path: string;
  name: string;
  content: string;
  size: number;
}

export default function App() {
  const [servers, setServers] = useState<VpnServer[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedCountry, setSelectedCountry] = useState<'ALL' | 'JP' | 'US'>('ALL');
  const [activeTab, setActiveTab] = useState<'simulator' | 'servers' | 'algorithm' | 'code' | 'build'>('simulator');
  const [lastUpdated, setLastUpdated] = useState<number>(Date.now());

  // In-simulator state
  const [simState, setSimState] = useState<'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'FAILED'>('DISCONNECTED');
  const [simConnectingCandidate, setSimConnectingCandidate] = useState(1);
  const [simCurrentServer, setSimCurrentServer] = useState<VpnServer | null>(null);
  const [simScreen, setSimScreen] = useState<'main' | 'list' | 'settings'>('main');
  const [simDetailServer, setSimDetailServer] = useState<VpnServer | null>(null);
  const [simLiveBytesIn, setSimLiveBytesIn] = useState(0);
  const [simLiveBytesOut, setSimLiveBytesOut] = useState(0);

  // Settings in simulator
  const [prefCountry, setPrefCountry] = useState<'AUTO' | 'JP' | 'US'>('AUTO');
  const [autoConnect, setAutoConnect] = useState(false);
  const [weeklySync, setWeeklySync] = useState(true);
  const [failoverTimeout, setFailoverTimeout] = useState(10);

  // Code Explorer state
  const [projectFiles, setProjectFiles] = useState<FileEntry[]>([]);
  const [selectedFilePath, setSelectedFilePath] = useState<string>('app/src/main/java/com/vpngate/tunnel/MainActivity.kt');
  const [copiedCode, setCopiedCode] = useState(false);

  // Fetch servers from backend proxy
  const fetchServers = async (forceRefresh = false) => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await fetch(`/api/vpngate/servers${forceRefresh ? '?refresh=true' : ''}`);
      if (!res.ok) throw new Error(`HTTP error ${res.status}`);
      const data = await res.json();
      if (data.success && Array.isArray(data.servers)) {
        setServers(data.servers);
        setLastUpdated(data.lastUpdated || Date.now());
        if (!simCurrentServer && data.servers.length > 0) {
          setSimCurrentServer(data.servers[0]);
        }
      } else {
        throw new Error(data.error || 'Failed to parse servers');
      }
    } catch (err: any) {
      console.error(err);
      setError(err.message || 'Unable to connect to VPN Gate API');
    } finally {
      setIsLoading(false);
    }
  };

  // Fetch project files for in-browser IDE explorer
  const fetchProjectFiles = async () => {
    try {
      const res = await fetch('/api/project/files');
      if (res.ok) {
        const data = await res.json();
        if (data.success) {
          setProjectFiles(data.files);
        }
      }
    } catch (err) {
      console.warn('Could not load project files:', err);
    }
  };

  useEffect(() => {
    fetchServers();
    fetchProjectFiles();
  }, []);

  // Filtered servers based on selector
  const filteredServers = useMemo(() => {
    if (selectedCountry === 'ALL') return servers;
    return servers.filter((s) => s.countryShort === selectedCountry);
  }, [servers, selectedCountry]);

  // Candidate pool for simulator based on preference
  const simCandidates = useMemo(() => {
    if (prefCountry === 'JP') return servers.filter((s) => s.countryShort === 'JP');
    if (prefCountry === 'US') return servers.filter((s) => s.countryShort === 'US');
    return servers;
  }, [servers, prefCountry]);

  // Update target server when candidates change
  useEffect(() => {
    if (simCandidates.length > 0 && (!simCurrentServer || !simCandidates.some(s => s.ip === simCurrentServer.ip))) {
      setSimCurrentServer(simCandidates[0]);
    }
  }, [simCandidates]);

  // Live byte counter simulation when connected
  useEffect(() => {
    if (simState !== 'CONNECTED') {
      setSimLiveBytesIn(0);
      setSimLiveBytesOut(0);
      return;
    }
    const interval = setInterval(() => {
      setSimLiveBytesIn((prev) => prev + Math.floor(Math.random() * 450000) + 120000);
      setSimLiveBytesOut((prev) => prev + Math.floor(Math.random() * 95000) + 25000);
    }, 1000);
    return () => clearInterval(interval);
  }, [simState]);

  // Simulator CONNECT / DISCONNECT trigger
  const handleSimToggle = () => {
    if (simState === 'CONNECTED' || simState === 'CONNECTING') {
      setSimState('DISCONNECTED');
      setSimConnectingCandidate(1);
    } else {
      setSimState('CONNECTING');
      setSimConnectingCandidate(1);

      // Pick top candidate
      const target = simCandidates[0] || servers[0];
      setSimCurrentServer(target);

      // Simulate connection negotiation handshake
      setTimeout(() => {
        setSimState('CONNECTED');
      }, 1600);
    }
  };

  const handleSimSelectServer = (server: VpnServer) => {
    setSimCurrentServer(server);
    setSimScreen('main');
    setSimState('CONNECTING');
    setTimeout(() => {
      setSimState('CONNECTED');
    }, 1400);
  };

  const selectedFileContent = useMemo(() => {
    const f = projectFiles.find((p) => p.path === selectedFilePath);
    return f ? f.content : '// Select a file to view source code';
  }, [projectFiles, selectedFilePath]);

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  const formatBytes = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return (
    <div className="min-h-screen bg-[#090A0B] text-[#EDEDED] font-sans antialiased flex flex-col selection:bg-neutral-700 selection:text-white">
      {/* Top Header */}
      <header className="border-b border-[#1E2023] bg-[#0E1012] sticky top-0 z-40 px-4 lg:px-8 py-3.5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-white text-black flex items-center justify-center font-bold text-sm tracking-wider">
            GT
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-sm font-semibold tracking-tight text-white">GateTunnel VPN</h1>
              <span className="text-[11px] font-mono text-neutral-400">Android Client v1.0.0</span>
            </div>
            <p className="text-[12px] text-neutral-500 hidden sm:block">
              Fastest VPN Gate discovery in Japan & United States · VpnService & WorkManager
            </p>
          </div>
        </div>

        {/* Global Action & Tabs */}
        <div className="flex items-center gap-2">
          {/* Navigation Tabs */}
          <div className="flex items-center bg-[#16181B] border border-[#23262A] rounded-lg p-0.5">
            <button
              onClick={() => setActiveTab('simulator')}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                activeTab === 'simulator' ? 'bg-[#23272C] text-white shadow-sm' : 'text-neutral-400 hover:text-neutral-200'
              }`}
            >
              📱 Android App
            </button>
            <button
              onClick={() => setActiveTab('servers')}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                activeTab === 'servers' ? 'bg-[#23272C] text-white shadow-sm' : 'text-neutral-400 hover:text-neutral-200'
              }`}
            >
              🌐 Live Nodes ({servers.length})
            </button>
            <button
              onClick={() => setActiveTab('algorithm')}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                activeTab === 'algorithm' ? 'bg-[#23272C] text-white shadow-sm' : 'text-neutral-400 hover:text-neutral-200'
              }`}
            >
              🧮 Ranking Formula
            </button>
            <button
              onClick={() => setActiveTab('code')}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                activeTab === 'code' ? 'bg-[#23272C] text-white shadow-sm' : 'text-neutral-400 hover:text-neutral-200'
              }`}
            >
              📦 Kotlin Code
            </button>
            <button
              onClick={() => setActiveTab('build')}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                activeTab === 'build' ? 'bg-[#23272C] text-white shadow-sm' : 'text-neutral-400 hover:text-neutral-200'
              }`}
            >
              🛠️ APK Guide
            </button>
          </div>

          {/* Download Project ZIP */}
          <a
            href="/api/project/download-zip"
            download="GateTunnel-Android-VPN-Source.zip"
            className="hidden md:flex items-center gap-1.5 bg-white text-black hover:bg-neutral-200 text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Download APK Source</span>
          </a>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl mx-auto w-full p-4 lg:p-8">
        {/* TAB 1: ANDROID SIMULATOR + METRICS */}
        {activeTab === 'simulator' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left: Pixel-Perfect Android Device Simulator */}
            <div className="lg:col-span-5 flex flex-col items-center">
              <div className="w-full max-w-[360px] bg-black border-[6px] border-[#22252A] rounded-[44px] shadow-2xl p-3 relative overflow-hidden ring-1 ring-white/10">
                {/* Speaker Grill & Camera Notch */}
                <div className="absolute top-4 left-1/2 -translate-x-1/2 w-24 h-4 bg-black rounded-full z-30 flex items-center justify-center">
                  <div className="w-3 h-3 rounded-full bg-[#1A1A1A] border border-neutral-800" />
                  <div className="w-8 h-1 rounded-full bg-[#202020] ml-2" />
                </div>

                {/* Android Screen Canvas */}
                <div className="bg-[#0C0D0E] text-white rounded-[34px] h-[680px] flex flex-col pt-7 pb-4 px-4 select-none relative overflow-hidden">
                  {/* Status Bar */}
                  <div className="flex items-center justify-between text-[11px] text-neutral-400 px-2 pb-2 font-mono">
                    <span>14:30</span>
                    <div className="flex items-center gap-1.5">
                      {simState === 'CONNECTED' && (
                        <div className="flex items-center gap-0.5 text-white bg-neutral-800/80 px-1 py-0.2 rounded text-[9px] font-bold">
                          VPN
                        </div>
                      )}
                      <Wifi className="w-3 h-3 text-neutral-300" />
                      <div className="w-4 h-2 border border-neutral-400 rounded-sm p-0.5 flex items-center">
                        <div className="w-full h-full bg-white rounded-xs" />
                      </div>
                    </div>
                  </div>

                  {/* Android Screens */}
                  {simScreen === 'main' && (
                    <div className="flex-1 flex flex-col justify-between py-2">
                      {/* Top Bar */}
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-lg tracking-wider text-white">VPN</span>
                        <div className="flex items-center gap-1">
                          <button
                            onClick={() => fetchServers(true)}
                            className="p-1.5 text-neutral-400 hover:text-white transition-colors"
                            title="Refresh"
                          >
                            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
                          </button>
                          <button
                            onClick={() => setSimScreen('list')}
                            className="p-1.5 text-neutral-400 hover:text-white transition-colors"
                            title="Server List"
                          >
                            <Server className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => setSimScreen('settings')}
                            className="p-1.5 text-neutral-400 hover:text-white transition-colors"
                            title="Settings"
                          >
                            <Sliders className="w-4 h-4" />
                          </button>
                        </div>
                      </div>

                      {/* Country Quick Selector */}
                      <div className="grid grid-cols-3 gap-1 bg-[#16181A] p-1 rounded-xl border border-[#222427] text-xs mt-3">
                        <button
                          onClick={() => setPrefCountry('AUTO')}
                          className={`py-1.5 px-2 rounded-lg font-medium transition-all ${
                            prefCountry === 'AUTO' ? 'bg-[#26282D] text-white shadow-sm' : 'text-neutral-400 hover:text-neutral-200'
                          }`}
                        >
                          ⚡ Fastest
                        </button>
                        <button
                          onClick={() => setPrefCountry('JP')}
                          className={`py-1.5 px-2 rounded-lg font-medium transition-all ${
                            prefCountry === 'JP' ? 'bg-[#26282D] text-white shadow-sm' : 'text-neutral-400 hover:text-neutral-200'
                          }`}
                        >
                          🇯🇵 Japan
                        </button>
                        <button
                          onClick={() => setPrefCountry('US')}
                          className={`py-1.5 px-2 rounded-lg font-medium transition-all ${
                            prefCountry === 'US' ? 'bg-[#26282D] text-white shadow-sm' : 'text-neutral-400 hover:text-neutral-200'
                          }`}
                        >
                          🇺🇸 USA
                        </button>
                      </div>

                      {/* Connection Status Label */}
                      <div className="flex flex-col items-center justify-center my-4">
                        <div className="flex items-center gap-2 mb-1">
                          <span
                            className={`w-2 h-2 rounded-full ${
                              simState === 'CONNECTED'
                                ? 'bg-white shadow-[0_0_8px_rgba(255,255,255,0.8)]'
                                : simState === 'CONNECTING'
                                ? 'bg-neutral-400 animate-pulse'
                                : 'bg-neutral-600'
                            }`}
                          />
                          <span
                            className={`text-sm font-medium ${
                              simState === 'CONNECTED' ? 'text-white' : 'text-neutral-400'
                            }`}
                          >
                            {simState === 'CONNECTED'
                              ? 'Connected'
                              : simState === 'CONNECTING'
                              ? 'Connecting…'
                              : 'Disconnected'}
                          </span>
                        </div>

                        {simState === 'CONNECTING' && (
                          <span className="text-[11px] text-neutral-500 font-mono">
                            Verifying tunnel handshake (1/5)
                          </span>
                        )}
                      </div>

                      {/* Large Central CONNECT Button */}
                      <div className="flex items-center justify-center my-6">
                        <div className="relative flex items-center justify-center">
                          {/* Outer pulse ring during connection */}
                          <div
                            className={`absolute w-48 h-48 rounded-full border transition-all duration-700 ${
                              simState === 'CONNECTED'
                                ? 'border-white/20 scale-105'
                                : simState === 'CONNECTING'
                                ? 'border-white/40 animate-ping'
                                : 'border-[#222427]'
                            }`}
                          />

                          {/* Inner touchable circle */}
                          <button
                            onClick={handleSimToggle}
                            className={`w-40 h-40 rounded-full flex flex-col items-center justify-center transition-all duration-300 z-10 active:scale-95 shadow-xl ${
                              simState === 'CONNECTED'
                                ? 'bg-white text-black font-extrabold border-2 border-white'
                                : 'bg-[#16181A] hover:bg-[#1E2024] text-white font-bold border border-[#2C2F33]'
                            }`}
                          >
                            <Shield
                              className={`w-8 h-8 mb-1.5 transition-transform ${
                                simState === 'CONNECTED' ? 'text-black scale-110' : 'text-neutral-400'
                              }`}
                            />
                            <span className="text-xs tracking-widest">
                              {simState === 'CONNECTED' ? 'DISCONNECT' : simState === 'CONNECTING' ? 'CANCEL' : 'CONNECT'}
                            </span>
                          </button>
                        </div>
                      </div>

                      {/* Active Server Info Card */}
                      {simCurrentServer ? (
                        <div
                          onClick={() => setSimDetailServer(simCurrentServer)}
                          className="bg-[#16181A] border border-[#222427] hover:border-neutral-700 rounded-2xl p-3.5 transition-colors cursor-pointer"
                        >
                          <div className="flex items-center justify-between mb-1.5">
                            <span className="text-xs font-semibold text-white">
                              {simCurrentServer.countryShort === 'JP' ? 'Japan • Tokyo' : 'United States • California'}
                            </span>
                            <span className="text-[10px] text-neutral-400 font-mono">
                              {simCurrentServer.proto.toUpperCase()} :{simCurrentServer.port}
                            </span>
                          </div>

                          <div className="text-[11px] text-neutral-400 mb-3 truncate font-mono">
                            {simCurrentServer.hostName}.opengw.net
                          </div>

                          <div className="grid grid-cols-2 gap-2 border-t border-[#222427] pt-2 text-center">
                            <div>
                              <div className="text-sm font-bold text-white">
                                {simCurrentServer.pingMs} ms
                              </div>
                              <div className="text-[10px] text-neutral-500">Latency</div>
                            </div>
                            <div className="border-l border-[#222427]">
                              <div className="text-sm font-bold text-white">
                                {Math.round(simCurrentServer.speedMbps)} Mbps
                              </div>
                              <div className="text-[10px] text-neutral-500">Throughput</div>
                            </div>
                          </div>

                          {simState === 'CONNECTED' && (
                            <div className="mt-2 pt-2 border-t border-[#222427] flex items-center justify-between text-[10px] text-neutral-400 font-mono">
                              <span>DL: {formatBytes(simLiveBytesIn)}</span>
                              <span>UL: {formatBytes(simLiveBytesOut)}</span>
                            </div>
                          )}
                        </div>
                      ) : (
                        <div className="bg-[#16181A] border border-[#222427] rounded-2xl p-4 text-center text-xs text-neutral-500">
                          Fetching fastest available nodes…
                        </div>
                      )}
                    </div>
                  )}

                  {/* Screen 2: Server List */}
                  {simScreen === 'list' && (
                    <div className="flex-1 flex flex-col overflow-hidden">
                      <div className="flex items-center justify-between pb-3 border-b border-[#222427]">
                        <button
                          onClick={() => setSimScreen('main')}
                          className="text-xs text-neutral-400 hover:text-white font-medium"
                        >
                          ← Back
                        </button>
                        <span className="text-xs font-bold text-white">Servers ({simCandidates.length})</span>
                        <span className="w-8" />
                      </div>

                      <div className="flex-1 overflow-y-auto py-2 space-y-2 pr-1">
                        {simCandidates.map((s) => {
                          const isActive = simCurrentServer?.ip === s.ip;
                          return (
                            <div
                              key={s.ip}
                              onClick={() => handleSimSelectServer(s)}
                              className={`p-2.5 rounded-xl border text-left cursor-pointer transition-all ${
                                isActive
                                  ? 'bg-[#202327] border-white text-white'
                                  : 'bg-[#16181A] border-[#222427] text-neutral-300 hover:border-neutral-600'
                              }`}
                            >
                              <div className="flex items-center justify-between">
                                <span className="text-xs font-semibold text-white">
                                  {s.countryShort === 'JP' ? '🇯🇵 Japan' : '🇺🇸 United States'}
                                </span>
                                <span className="text-[10px] font-mono text-neutral-400">
                                  Score: {s.rankScore}
                                </span>
                              </div>
                              <div className="text-[11px] text-neutral-400 truncate mt-0.5 font-mono">
                                {s.hostName}
                              </div>
                              <div className="flex items-center gap-3 mt-1.5 text-[11px] text-neutral-400">
                                <span className="font-semibold text-white">{s.pingMs} ms</span>
                                <span>·</span>
                                <span>{Math.round(s.speedMbps)} Mbps</span>
                                <span>·</span>
                                <span>{s.numVpnSessions} users</span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {/* Screen 3: Settings */}
                  {simScreen === 'settings' && (
                    <div className="flex-1 flex flex-col overflow-y-auto">
                      <div className="flex items-center justify-between pb-3 border-b border-[#222427] mb-3">
                        <button
                          onClick={() => setSimScreen('main')}
                          className="text-xs text-neutral-400 hover:text-white font-medium"
                        >
                          ← Back
                        </button>
                        <span className="text-xs font-bold text-white">Settings</span>
                        <span className="w-8" />
                      </div>

                      <div className="space-y-4 text-xs">
                        <div>
                          <span className="text-[10px] font-semibold text-neutral-500 tracking-wider">PREFERRED REGION</span>
                          <div className="bg-[#16181A] border border-[#222427] rounded-xl mt-1 divide-y divide-[#222427]">
                            <button
                              onClick={() => setPrefCountry('AUTO')}
                              className="w-full text-left p-2.5 flex items-center justify-between hover:bg-[#1E2024]"
                            >
                              <div>
                                <div className="font-medium text-white">Fastest Node</div>
                                <div className="text-[10px] text-neutral-400">Lowest latency between JP & US</div>
                              </div>
                              {prefCountry === 'AUTO' && <Check className="w-3.5 h-3.5 text-white" />}
                            </button>
                            <button
                              onClick={() => setPrefCountry('JP')}
                              className="w-full text-left p-2.5 flex items-center justify-between hover:bg-[#1E2024]"
                            >
                              <div>
                                <div className="font-medium text-white">🇯🇵 Japan Only</div>
                                <div className="text-[10px] text-neutral-400">Tokyo & Tsukuba cluster</div>
                              </div>
                              {prefCountry === 'JP' && <Check className="w-3.5 h-3.5 text-white" />}
                            </button>
                            <button
                              onClick={() => setPrefCountry('US')}
                              className="w-full text-left p-2.5 flex items-center justify-between hover:bg-[#1E2024]"
                            >
                              <div>
                                <div className="font-medium text-white">🇺🇸 United States Only</div>
                                <div className="text-[10px] text-neutral-400">US East & West cluster</div>
                              </div>
                              {prefCountry === 'US' && <Check className="w-3.5 h-3.5 text-white" />}
                            </button>
                          </div>
                        </div>

                        <div>
                          <span className="text-[10px] font-semibold text-neutral-500 tracking-wider">AUTOMATION</span>
                          <div className="bg-[#16181A] border border-[#222427] rounded-xl mt-1 divide-y divide-[#222427]">
                            <div className="p-2.5 flex items-center justify-between">
                              <div>
                                <div className="font-medium text-white">Auto-Connect on Launch</div>
                                <div className="text-[10px] text-neutral-400">Establish tunnel immediately</div>
                              </div>
                              <input
                                type="checkbox"
                                checked={autoConnect}
                                onChange={(e) => setAutoConnect(e.target.checked)}
                                className="accent-white"
                              />
                            </div>
                            <div className="p-2.5 flex items-center justify-between">
                              <div>
                                <div className="font-medium text-white">Weekly Background Sync</div>
                                <div className="text-[10px] text-neutral-400">WorkManager updates every 7d</div>
                              </div>
                              <input
                                type="checkbox"
                                checked={weeklySync}
                                onChange={(e) => setWeeklySync(e.target.checked)}
                                className="accent-white"
                              />
                            </div>
                          </div>
                        </div>

                        <div>
                          <span className="text-[10px] font-semibold text-neutral-500 tracking-wider">FAILOVER TIMEOUT</span>
                          <div className="bg-[#16181A] border border-[#222427] rounded-xl mt-1 p-2.5">
                            <div className="flex justify-between text-neutral-300 mb-1">
                              <span>Max wait before retry</span>
                              <span className="font-bold text-white">{failoverTimeout}s</span>
                            </div>
                            <input
                              type="range"
                              min="5"
                              max="30"
                              value={failoverTimeout}
                              onChange={(e) => setFailoverTimeout(parseInt(e.target.value, 10))}
                              className="w-full accent-white"
                            />
                          </div>
                        </div>

                        <div className="bg-[#16181A] border border-[#222427] rounded-xl p-3 text-[11px] text-neutral-400 space-y-1">
                          <div className="font-bold text-white text-xs">GateTunnel VPN for Android</div>
                          <div>University of Tsukuba VPN Gate API</div>
                          <div>Zero-leak IPv4 & IPv6 routing enabled</div>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Server Details Modal inside Simulator */}
                  {simDetailServer && (
                    <div className="absolute inset-0 bg-black/80 backdrop-blur-xs flex flex-col justify-end z-40 p-3">
                      <div className="bg-[#16181A] border border-neutral-700 rounded-2xl p-4 text-xs space-y-3">
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-sm text-white">Server Technical Info</span>
                          <button
                            onClick={() => setSimDetailServer(null)}
                            className="text-neutral-400 hover:text-white"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </div>

                        <div className="space-y-1.5 font-mono text-[11px]">
                          <div className="flex justify-between">
                            <span className="text-neutral-400">Public IP:</span>
                            <span className="text-white font-bold">{simDetailServer.ip}</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-neutral-400">Protocol:</span>
                            <span className="text-white">{simDetailServer.proto.toUpperCase()} :{simDetailServer.port}</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-neutral-400">Cipher:</span>
                            <span className="text-white">{simDetailServer.cipher}</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-neutral-400">Latency:</span>
                            <span className="text-white">{simDetailServer.pingMs} ms</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-neutral-400">Speed:</span>
                            <span className="text-white">{Math.round(simDetailServer.speedMbps)} Mbps</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-neutral-400">Composite Score:</span>
                            <span className="text-white font-bold">{simDetailServer.rankScore} / 100</span>
                          </div>
                        </div>

                        <div className="pt-2 border-t border-[#222427] flex gap-2">
                          <a
                            href={`/api/vpngate/server/${simDetailServer.ip}/config.ovpn`}
                            className="flex-1 bg-neutral-800 hover:bg-neutral-700 text-white text-center py-2 rounded-lg font-medium transition-colors"
                          >
                            Download .ovpn
                          </a>
                          <button
                            onClick={() => {
                              handleSimSelectServer(simDetailServer);
                              setSimDetailServer(null);
                            }}
                            className="flex-1 bg-white hover:bg-neutral-200 text-black font-semibold py-2 rounded-lg transition-colors"
                          >
                            Connect
                          </button>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Android Virtual Bottom Home Bar */}
                  <div className="pt-3 flex justify-center">
                    <div className="w-24 h-1 bg-neutral-600 rounded-full" />
                  </div>
                </div>
              </div>
            </div>

            {/* Right: Technical Summary & Key App Features */}
            <div className="lg:col-span-7 space-y-6">
              {/* Executive Architecture Card */}
              <div className="bg-[#101215] border border-[#202328] rounded-2xl p-6">
                <div className="flex items-center justify-between pb-4 border-b border-[#202328]">
                  <div>
                    <h2 className="text-base font-semibold text-white">Android VPN Architecture</h2>
                    <p className="text-xs text-neutral-400 mt-0.5">
                      Production Kotlin implementation strictly matching specifications
                    </p>
                  </div>
                  <span className="text-[11px] font-mono bg-neutral-800 border border-neutral-700 text-neutral-300 px-2.5 py-1 rounded-md">
                    Android 8.0 - 14 (API 26-34)
                  </span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-5 text-xs">
                  <div className="p-3.5 rounded-xl bg-[#16181C] border border-[#23262C]">
                    <div className="font-semibold text-white mb-1 flex items-center gap-1.5">
                      <Shield className="w-4 h-4 text-neutral-300" />
                      <span>Android VpnService</span>
                    </div>
                    <p className="text-neutral-400 leading-relaxed text-[11px]">
                      Creates virtual <code className="text-neutral-200 font-mono">tun0</code> interface via{' '}
                      <code className="text-neutral-200 font-mono">VpnService.Builder</code> routing IPv4 0.0.0.0/0 & IPv6 ::/0 with DNS leak prevention (1.1.1.1, 8.8.8.8).
                    </p>
                  </div>

                  <div className="p-3.5 rounded-xl bg-[#16181C] border border-[#23262C]">
                    <div className="font-semibold text-white mb-1 flex items-center gap-1.5">
                      <Clock className="w-4 h-4 text-neutral-300" />
                      <span>Weekly WorkManager Sync</span>
                    </div>
                    <p className="text-neutral-400 leading-relaxed text-[11px]">
                      Runs <code className="text-neutral-200 font-mono">PeriodicWorkRequest</code> every 7 days with network and battery constraints to refresh cached servers in Room DB.
                    </p>
                  </div>

                  <div className="p-3.5 rounded-xl bg-[#16181C] border border-[#23262C]">
                    <div className="font-semibold text-white mb-1 flex items-center gap-1.5">
                      <RotateCcw className="w-4 h-4 text-neutral-300" />
                      <span>Cascade Failover Logic</span>
                    </div>
                    <p className="text-neutral-400 leading-relaxed text-[11px]">
                      When Connect is pressed, selects highest-ranked node. If connection drops or times out within 10s, seamlessly retries candidate #2 through #5.
                    </p>
                  </div>

                  <div className="p-3.5 rounded-xl bg-[#16181C] border border-[#23262C]">
                    <div className="font-semibold text-white mb-1 flex items-center gap-1.5">
                      <Layers className="w-4 h-4 text-neutral-300" />
                      <span>Jetpack Compose Monochrome UI</span>
                    </div>
                    <p className="text-neutral-400 leading-relaxed text-[11px]">
                      Zero-pill minimal aesthetic using strictly Black, White, and Grey shades with native Android system typography.
                    </p>
                  </div>
                </div>
              </div>

              {/* Live Status & Quick Action Bar */}
              <div className="bg-[#101215] border border-[#202328] rounded-2xl p-6 flex flex-col sm:flex-row items-center justify-between gap-4">
                <div>
                  <div className="text-xs text-neutral-400">Current Discovery Status</div>
                  <div className="text-sm font-semibold text-white flex items-center gap-2 mt-0.5">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                    <span>{servers.length} Verified Servers Cached (JP: {servers.filter(s => s.countryShort === 'JP').length} · US: {servers.filter(s => s.countryShort === 'US').length})</span>
                  </div>
                </div>

                <div className="flex items-center gap-2 w-full sm:w-auto">
                  <button
                    onClick={() => setActiveTab('code')}
                    className="flex-1 sm:flex-none border border-neutral-700 hover:border-neutral-500 text-neutral-200 px-4 py-2 rounded-lg text-xs font-medium transition-colors"
                  >
                    View Source Files
                  </button>
                  <a
                    href="/api/project/download-zip"
                    className="flex-1 sm:flex-none bg-white hover:bg-neutral-200 text-black px-4 py-2 rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors"
                  >
                    <Download className="w-3.5 h-3.5" />
                    <span>Download Project .zip</span>
                  </a>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: LIVE VPN GATE SERVERS IN JAPAN & US */}
        {activeTab === 'servers' && (
          <div className="space-y-6">
            {/* Filter and stats row */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-[#101215] border border-[#202328] rounded-2xl p-5">
              <div>
                <h2 className="text-base font-semibold text-white">Live Discovered Servers</h2>
                <p className="text-xs text-neutral-400 mt-0.5">
                  Filtered to Japan (JP) and United States (US) only · Ranked by composite score
                </p>
              </div>

              <div className="flex items-center gap-2">
                <div className="flex bg-[#16181C] border border-[#23262C] rounded-lg p-1">
                  <button
                    onClick={() => setSelectedCountry('ALL')}
                    className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                      selectedCountry === 'ALL' ? 'bg-[#2A2E35] text-white' : 'text-neutral-400 hover:text-white'
                    }`}
                  >
                    All ({servers.length})
                  </button>
                  <button
                    onClick={() => setSelectedCountry('JP')}
                    className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                      selectedCountry === 'JP' ? 'bg-[#2A2E35] text-white' : 'text-neutral-400 hover:text-white'
                    }`}
                  >
                    🇯🇵 Japan ({servers.filter((s) => s.countryShort === 'JP').length})
                  </button>
                  <button
                    onClick={() => setSelectedCountry('US')}
                    className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                      selectedCountry === 'US' ? 'bg-[#2A2E35] text-white' : 'text-neutral-400 hover:text-white'
                    }`}
                  >
                    🇺🇸 USA ({servers.filter((s) => s.countryShort === 'US').length})
                  </button>
                </div>

                <button
                  onClick={() => fetchServers(true)}
                  disabled={isLoading}
                  className="p-2 border border-neutral-700 hover:border-neutral-500 rounded-lg text-neutral-300 hover:text-white transition-colors"
                  title="Force re-fetch from VPN Gate"
                >
                  <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
                </button>
              </div>
            </div>

            {/* Server Table */}
            <div className="bg-[#101215] border border-[#202328] rounded-2xl overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-[#16181C] border-b border-[#202328] text-neutral-400 text-[11px] font-mono uppercase tracking-wider">
                    <tr>
                      <th className="py-3 px-4">Rank / Node</th>
                      <th className="py-3 px-4">Location</th>
                      <th className="py-3 px-4">Latency</th>
                      <th className="py-3 px-4">Throughput</th>
                      <th className="py-3 px-4">Protocol</th>
                      <th className="py-3 px-4">Users / Load</th>
                      <th className="py-3 px-4">Composite Score</th>
                      <th className="py-3 px-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#1D2024]">
                    {filteredServers.map((server, idx) => (
                      <tr key={server.ip} className="hover:bg-[#15171A] transition-colors">
                        <td className="py-3 px-4 font-mono">
                          <div className="flex items-center gap-2">
                            <span className="w-5 text-neutral-500 font-bold">#{idx + 1}</span>
                            <div>
                              <div className="font-semibold text-white">{server.ip}</div>
                              <div className="text-[11px] text-neutral-500">{server.hostName}</div>
                            </div>
                          </div>
                        </td>
                        <td className="py-3 px-4">
                          <div className="flex items-center gap-1.5 font-medium text-white">
                            <span>{server.countryShort === 'JP' ? '🇯🇵' : '🇺🇸'}</span>
                            <span>{server.countryShort === 'JP' ? 'Japan' : 'United States'}</span>
                          </div>
                          <div className="text-[11px] text-neutral-500 truncate max-w-[140px]">
                            {server.operator || 'Tsukuba Open Node'}
                          </div>
                        </td>
                        <td className="py-3 px-4 font-mono font-semibold text-white">
                          <span
                            className={
                              server.pingMs < 20
                                ? 'text-emerald-400'
                                : server.pingMs < 50
                                ? 'text-neutral-200'
                                : 'text-neutral-400'
                            }
                          >
                            {server.pingMs} ms
                          </span>
                        </td>
                        <td className="py-3 px-4 font-mono text-white font-medium">
                          {Math.round(server.speedMbps)} Mbps
                        </td>
                        <td className="py-3 px-4 font-mono text-neutral-300">
                          {server.proto.toUpperCase()} :{server.port}
                        </td>
                        <td className="py-3 px-4 text-neutral-400">
                          <div>{server.numVpnSessions} active</div>
                          <div className="text-[10px] text-neutral-500 font-mono">
                            {(server.uptimeMs / (1000 * 3600 * 24)).toFixed(0)}d uptime
                          </div>
                        </td>
                        <td className="py-3 px-4 font-mono font-bold text-white">
                          <span className="px-2 py-0.5 rounded-md bg-neutral-800 border border-neutral-700">
                            {server.rankScore}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-right">
                          <div className="flex items-center justify-end gap-1.5">
                            <button
                              onClick={() => {
                                handleSimSelectServer(server);
                                setActiveTab('simulator');
                              }}
                              className="px-2.5 py-1 rounded-md bg-neutral-800 hover:bg-neutral-700 text-white font-medium text-xs transition-colors"
                            >
                              Test in App
                            </button>
                            <a
                              href={`/api/vpngate/server/${server.ip}/config.ovpn`}
                              title="Download .ovpn Profile"
                              className="p-1.5 rounded-md bg-neutral-800 hover:bg-neutral-700 text-neutral-300 hover:text-white transition-colors"
                            >
                              <Download className="w-3.5 h-3.5" />
                            </a>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: RANKING ALGORITHM BREAKDOWN */}
        {activeTab === 'algorithm' && (
          <div className="space-y-6 max-w-4xl mx-auto">
            <div className="bg-[#101215] border border-[#202328] rounded-2xl p-6">
              <h2 className="text-base font-semibold text-white mb-1">Server Ranking Formula</h2>
              <p className="text-xs text-neutral-400">
                Mathematical multi-factor scoring model ensuring low latency and high stability over raw peak speed.
              </p>

              <div className="mt-5 p-4 rounded-xl bg-[#090A0B] border border-[#202328] font-mono text-sm text-neutral-200">
                score = latency_score + throughput_score + uptime_score + availability_score - load_penalty
              </div>

              <div className="mt-6 space-y-4 text-xs">
                <div className="p-4 rounded-xl bg-[#16181C] border border-[#23262C] space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-white">1. Latency Score (Weight: 35%)</span>
                    <span className="font-mono text-neutral-400">0.0 to 35.0 pts</span>
                  </div>
                  <p className="text-neutral-400 leading-relaxed">
                    Evaluates ping responsiveness clamped between 5ms and 350ms. High priority for interactive browsing and tunnel responsiveness.
                  </p>
                  <code className="block font-mono text-[11px] text-neutral-300 bg-black/40 p-2 rounded-md">
                    latencyScore = (1.0 - ((clamp(pingMs, 5, 350) - 5) / 345)) * 35.0
                  </code>
                </div>

                <div className="p-4 rounded-xl bg-[#16181C] border border-[#23262C] space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-white">2. Throughput Score (Weight: 30%)</span>
                    <span className="font-mono text-neutral-400">0.0 to 30.0 pts</span>
                  </div>
                  <p className="text-neutral-400 leading-relaxed">
                    Logarithmic normalization of bandwidth up to 500 Mbps. Prevents a single unreliable 800Mbps server from dominating over a stable 150Mbps server.
                  </p>
                  <code className="block font-mono text-[11px] text-neutral-300 bg-black/40 p-2 rounded-md">
                    throughputScore = (ln(min(speedMbps, 500)) / ln(500)) * 30.0
                  </code>
                </div>

                <div className="p-4 rounded-xl bg-[#16181C] border border-[#23262C] space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-white">3. Uptime & Stability (Weight: 15%)</span>
                    <span className="font-mono text-neutral-400">0.0 to 15.0 pts</span>
                  </div>
                  <p className="text-neutral-400 leading-relaxed">
                    Measures continuous operational uptime, capped at 30 days (720 hours). Filters out transient or unstable volunteer nodes that reboot regularly.
                  </p>
                </div>

                <div className="p-4 rounded-xl bg-[#16181C] border border-[#23262C] space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-white">4. Load Penalty (Negative Weight: -10%)</span>
                    <span className="font-mono text-neutral-400">-0.0 to -10.0 pts</span>
                  </div>
                  <p className="text-neutral-400 leading-relaxed">
                    Penalizes servers with more than 150 concurrent active VPN sessions competing for local CPU and upstream link capacity.
                  </p>
                  <code className="block font-mono text-[11px] text-neutral-300 bg-black/40 p-2 rounded-md">
                    loadPenalty = (clamp(numVpnSessions, 0, 150) / 150.0) * 10.0
                  </code>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: KOTLIN CODE EXPLORER */}
        {activeTab === 'code' && (
          <div className="bg-[#101215] border border-[#202328] rounded-2xl overflow-hidden grid grid-cols-1 md:grid-cols-12 min-h-[620px]">
            {/* File Tree Sidebar */}
            <div className="md:col-span-4 border-r border-[#202328] p-4 bg-[#0D0F11]">
              <div className="flex items-center justify-between pb-3 border-b border-[#202328] mb-3">
                <div className="text-xs font-semibold text-white flex items-center gap-1.5">
                  <FileCode className="w-4 h-4 text-neutral-400" />
                  <span>Android Source Files</span>
                </div>
                <span className="text-[10px] font-mono text-neutral-500">{projectFiles.length} files</span>
              </div>

              <div className="space-y-1 text-xs overflow-y-auto max-h-[540px] pr-1">
                {projectFiles.map((file) => {
                  const isSelected = file.path === selectedFilePath;
                  return (
                    <button
                      key={file.path}
                      onClick={() => setSelectedFilePath(file.path)}
                      className={`w-full text-left px-2.5 py-1.5 rounded-lg flex items-center justify-between transition-colors ${
                        isSelected
                          ? 'bg-[#22262C] text-white font-medium'
                          : 'text-neutral-400 hover:text-neutral-200 hover:bg-[#16181B]'
                      }`}
                    >
                      <span className="truncate text-[11px] font-mono">{file.path}</span>
                      <span className="text-[9px] text-neutral-500 font-mono ml-1">
                        {(file.size / 1024).toFixed(0)}k
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Code Viewer */}
            <div className="md:col-span-8 flex flex-col bg-[#08090A]">
              <div className="flex items-center justify-between px-4 py-2.5 border-b border-[#202328] bg-[#0E1012]">
                <span className="text-xs font-mono text-neutral-300">{selectedFilePath}</span>
                <button
                  onClick={() => copyToClipboard(selectedFileContent)}
                  className="flex items-center gap-1.5 text-xs text-neutral-400 hover:text-white transition-colors"
                >
                  {copiedCode ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copiedCode ? 'Copied!' : 'Copy Code'}</span>
                </button>
              </div>

              <div className="p-4 overflow-x-auto flex-1 font-mono text-[12px] leading-relaxed text-neutral-200 bg-[#090A0B]">
                <pre className="whitespace-pre">{selectedFileContent}</pre>
              </div>
            </div>
          </div>
        )}

        {/* TAB 5: APK BUILD & INSTALLATION GUIDE */}
        {activeTab === 'build' && (
          <div className="space-y-6 max-w-4xl mx-auto">
            <div className="bg-[#101215] border border-[#202328] rounded-2xl p-6">
              <h2 className="text-base font-semibold text-white mb-1">Building the Installable Android APK</h2>
              <p className="text-xs text-neutral-400">
                Complete instructions for importing into Android Studio or compiling directly with Gradle CLI.
              </p>

              <div className="mt-6 space-y-6 text-xs">
                <div>
                  <h3 className="text-sm font-semibold text-white mb-2 flex items-center gap-2">
                    <span className="w-5 h-5 rounded-full bg-white text-black flex items-center justify-center text-[10px] font-bold">1</span>
                    Download the Complete Android Project
                  </h3>
                  <p className="text-neutral-400 mb-3">
                    Click the button below to download the entire project with Gradle wrapper, Room database, VpnService, and WorkManager:
                  </p>
                  <a
                    href="/api/project/download-zip"
                    className="inline-flex items-center gap-2 bg-white text-black font-semibold px-4 py-2.5 rounded-lg hover:bg-neutral-200 transition-colors"
                  >
                    <Download className="w-4 h-4" />
                    <span>Download GateTunnel-Android-VPN-Source.zip</span>
                  </a>
                </div>

                <div className="border-t border-[#202328] pt-5">
                  <h3 className="text-sm font-semibold text-white mb-2 flex items-center gap-2">
                    <span className="w-5 h-5 rounded-full bg-white text-black flex items-center justify-center text-[10px] font-bold">2</span>
                    Compile with Command Line
                  </h3>
                  <p className="text-neutral-400 mb-2">Unzip the archive and build debug or release APKs:</p>
                  <div className="p-3.5 bg-black rounded-xl border border-[#202328] font-mono text-[11px] text-neutral-300 space-y-2">
                    <div className="text-neutral-500"># 1. Unzip the downloaded project</div>
                    <div>unzip GateTunnel-Android-VPN-Source.zip</div>
                    <div>cd GateTunnelVPN</div>
                    <div className="text-neutral-500 mt-2"># 2. Build Debug APK (installs on any phone without signing)</div>
                    <div className="text-white font-bold">./gradlew assembleDebug</div>
                    <div className="text-neutral-500 mt-2"># Output location:</div>
                    <div className="text-emerald-400">app/build/outputs/apk/debug/app-debug.apk</div>
                  </div>
                </div>

                <div className="border-t border-[#202328] pt-5">
                  <h3 className="text-sm font-semibold text-white mb-2 flex items-center gap-2">
                    <span className="w-5 h-5 rounded-full bg-white text-black flex items-center justify-center text-[10px] font-bold">3</span>
                    Install Directly to Android Device via ADB
                  </h3>
                  <p className="text-neutral-400 mb-2">Connect your phone via USB with Developer Mode & USB Debugging enabled:</p>
                  <div className="p-3.5 bg-black rounded-xl border border-[#202328] font-mono text-[11px] text-neutral-300 space-y-1">
                    <div>adb install app/build/outputs/apk/debug/app-debug.apk</div>
                  </div>
                </div>

                <div className="border-t border-[#202328] pt-5">
                  <h3 className="text-sm font-semibold text-white mb-2 flex items-center gap-2">
                    <span className="w-5 h-5 rounded-full bg-white text-black flex items-center justify-center text-[10px] font-bold">4</span>
                    Signing for Google Play / Release Distribution
                  </h3>
                  <div className="p-3.5 bg-black rounded-xl border border-[#202328] font-mono text-[11px] text-neutral-300 space-y-2">
                    <div className="text-neutral-500"># Generate a 2048-bit RSA release keystore</div>
                    <div>keytool -genkey -v -keystore release.keystore -alias gatetunnel -keyalg RSA -keysize 2048 -validity 10000</div>
                    <div className="text-neutral-500 mt-2"># Build Release APK</div>
                    <div>./gradlew assembleRelease</div>
                    <div className="text-neutral-500 mt-2"># Sign with apksigner</div>
                    <div>apksigner sign --ks release.keystore --out GateTunnel-v1.0.0.apk app/build/outputs/apk/release/app-release-unsigned.apk</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
