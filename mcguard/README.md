# McGuard (plugin)

A lightweight, **event-based** server-side anticheat for the whole Bukkit family.
One jar runs on **CraftBukkit, Spigot, Paper, Purpur, Pufferfish, Folia**, and
hybrids (**Arclight, Mohist**). No mixins, no packet interception, no NMS —
players join with an unmodified client and nothing to break on the next update.

---

## What it is — and honestly what it is not

McGuard reacts to Bukkit events (move, attack, break, place) and reads
**server-authoritative** state — position deltas, server velocity, server fall
distance, ping. It does **not** simulate the player's movement packet-by-packet
the way Grim or Vulcan do.

**So be clear-eyed about scope:** McGuard will not match a packet-simulation
anticheat on subtle movement cheats. What it *does* do is catch the cheats the
overwhelming majority of servers actually see — flight, speed, reach, kill-aura,
auto-clicker, nuker, timer, fast-place — cheaply and stably, with lag
compensation to keep legitimate high-ping players safe, and with zero
maintenance across Minecraft/Forge/Paper patch versions. Run it standalone on a
small/medium server, or alongside a packet AC as a cheap second opinion and
audit log.

Every default is tuned to **under-flag**. A false ban on a real player hurts a
server more than a cheater surviving another 30 seconds.

---

## The 13 checks

| Check | Signal | Strength on Bukkit |
|-------|--------|--------------------|
| **Speed** | horizontal blocks/tick past a modifier-aware envelope, streak-gated | strong |
| **Flight** | sustained airtime without a legitimate source | strong |
| **NoFall** | large server-observed descent while server fall distance stayed ~0 | heuristic |
| **InvalidMove** | teleport-sized delta with no teleport grace | strong |
| **Reach** | eye-to-hitbox distance beyond vanilla + ping bonus | strong |
| **KillAura** | look-vs-target angle no legit aim reaches, 3-hit streak | strong |
| **AutoClicker** | CPS ceiling **and** click-rhythm regularity (std-dev) | strong |
| **Timer** | actions/second above the tick-rate ceiling | strong |
| **NoSlow** | full speed while eating/drinking/blocking/bow-drawing | good (Paper), shield-only (Spigot) |
| **FastPlace** | blocks placed/second past a human rate | strong |
| **FastBreak** | block broken faster than an optimised legit setup could | approximate |
| **Nuker** | many blocks/second | strong |
| **X-Ray** | valuable-ore ratio implausibly high over a large sample | statistical |

Weaker checks (NoFall, FastBreak, X-Ray) are inherent limits of a pure
server-side AC — the client is a black box. They are worth keeping as
suspect-surfacing signals, best paired with an ore-obfuscation plugin for X-Ray.

---

## Cross-platform design

- **Compiled against the Paper API** (a superset of Spigot/Bukkit), so all
  event types resolve. Paper-only calls are guarded so the jar loads on plain
  Spigot too.
- **Folia-safe.** `folia-supported: true`. Per-player state is a
  `ConcurrentHashMap`; each player's events run on their own region thread;
  punishment happens inline on that thread; the only shared-state write (the ban
  list) is routed through the global region scheduler.
- **Graceful degradation.** `isHandRaised()` (for NoSlow) is resolved
  reflectively and falls back to shield-only where absent. `getPing()` is
  wrapped so a platform without it simply disables lag compensation instead of
  erroring. The startup line prints which platform and item-use API were
  detected.

---

## Lag compensation

The single biggest source of anticheat false positives is a laggy player whose
signals look like a cheat. McGuard reads each player's ping and, above a
configurable threshold, widens reach tolerance and the kill-aura angle
proportionally. On by default; tune `latency.pingThresholdMs`.

---

## Commands & permissions

`/mcguard` (aliases `/mcg`, `/anticheat`) — requires `mcguard.admin`:

- `status` — everyone currently carrying a violation level
- `vl <player>` — per-check breakdown for one player
- `reset <player>` — clear a player's violations
- `exempt <player>` — toggle exemption (testing / trusted staff)
- `checks` — list every check and its description
- `reload` — re-read `config.yml`

| Permission | Default | Meaning |
|------------|---------|---------|
| `mcguard.admin` | op | run commands, receive violation alerts |
| `mcguard.bypass` | false | exempt the holder from every check |

---

## Install

1. Drop `mcguard-<version>.jar` into your server's `plugins/` folder.
2. Start the server once — `plugins/McGuard/config.yml` is generated.
3. Watch `/mcguard status` during normal play and light PvP.
4. Tune thresholds, then optionally raise `violations.banEnabled` to `true`.

Requires Java 17+ and Minecraft 1.20.x on any Bukkit-based server.

Flags are appended to `plugins/McGuard/violations.log` as CSV
(`timestamp,player,uuid,check,vl,detail`) for external dashboards.

---

## Before publishing / going live

- **Test on your own server first** with `violations.banEnabled: false`. These
  thresholds are a starting point, not gospel.
- If you already run a packet-based anticheat (Grim, Vulcan), enable only the
  checks it does not cover, or run McGuard purely for its audit log — do not
  stack two auto-banners on the same signal.
- Set a real license and issue-tracker URL before uploading to
  CurseForge/Modrinth.

## Build from source

```
./gradlew build            # -> build/libs/mcguard-<version>.jar
./gradlew installToServer  # also copies the jar into ../../server/plugins
```
