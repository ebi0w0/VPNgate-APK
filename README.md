# Android VPN Architecture

Lala la la la

![Android 8.0 - 14](https://img.shields.io/badge/Android-8.0%20--%2014%20(API%2026--34)-black?style=flat-square&logo=android&logoColor=white)

---

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>🛡️ Android VpnService</h3>
      Creates virtual <code>tun0</code> interface via <code>VpnService.Builder</code> routing IPv4 <code>0.0.0.0/0</code> &amp; IPv6 <code>::/0</code> with DNS leak prevention (<code>1.1.1.1</code>, <code>8.8.8.8</code>).
    </td>
    <td width="50%" valign="top">
      <h3>🕒 Weekly WorkManager Sync</h3>
      Runs <code>PeriodicWorkRequest</code> every 7 days with network and battery constraints to refresh cached servers in Room DB.
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>🔁 Cascade Failover Logic</h3>
      When Connect is pressed, selects highest-ranked node. If connection drops or times out within 10s, seamlessly retries candidate #2 through #5.
    </td>
    <td width="50%" valign="top">
      <h3>🎨 Jetpack Compose Monochrome UI</h3>
      Zero-pill minimal aesthetic using strictly Black, White, and Grey shades with native Android system typography.
    </td>
  </tr>
</table>

---

**Current Discovery Status**

🟢 **54 Verified Servers Cached** (JP: 52 · US: 2)
