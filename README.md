# LunaGC 7.0.0

A private server for the **CN Genshin Impact 7.0.0** client. A fork of girluh's
[LunaGC](https://github.com/girluh/LunaGC), itself a fork of Grasscutter, reworked
for the 7.0 protocol and the CN launcher's login chain.

Very WIP — expect broken things. What is implemented is listed below; everything
else is not.

## Note from the maintainer

This is a fork from girluh's [LunaGC](https://github.com/girluh/LunaGC). VERY WIP, so expect many bugs.

Features and functionality of the ps is not guaranteed, try it yourself to see what works and what doesnt (Most are broken).
This is possibly the only public PS with updated mob and gadget spawns! (Up to Version 5.4)

Contribute if you want/can...

# What works

- **The CN 7.0 login chain end to end**: the dispatch server serves the CN 7.0
  SDK's session routes and a `RegionInfo` with the right `game_biz`, so the
  launcher's own login flow reaches the game server instead of stalling on
  "无法连接网络" or "账号或密码错误".
- **The beginner quest chain**. Questing is on by default (see below) and the
  opening chain runs: Paimon's talk, gliding and stamina unlocks, the first
  statue, and the element gain, which is handled by `ExecChangeSkillDepot`
  rather than being skipped. A brand-new account gets the chain; an account that
  already played without it does not replay cutscenes.
- **The "client is damaged" crash is fixed.** The patched image is swapped into
  its slot in-process — the patch crate moves it aside when the DLL is mapped and
  restores it when the game exits — so the on-disk file the client's anti-cheat
  (`Astrolabe`) checks is the pristine one while the check runs. This applies to
  both swapped slots: the anti-cheat proxy (`Astrolabe.dll`) and the passport SDK
  (`AccountPlatNative.dll`). Before this, every session died at ~100 seconds with
  a WER crash in that DLL.
- **Unhandled packets are logged, not silently dropped.** Any request the server
  has no handler for is dumped at INFO with its opcode name, its number, its size
  and its decoded fields — this is the discovery mechanism for the features
  above, and `task dev` collects the list for you (see below).
- **Daily commissions.** The daily-task loop is wired to the real 7.0.0 opcodes
  and the client accepts the three notifies it needs: `DailyTaskDataNotify`,
  `WorldOwnerDailyTaskNotify` and `DailyTaskProgressNotify`. This one is worth a
  note, because it was silently dead and the monitor is what found it: the
  messages were ported from a 6.6 fork with CmdIds guessed against a 6.7 dump,
  and the 7.0.0 client has no message registered for any of them, so it rejected
  every packet with `PACKET_INVALID_CMD_ID` and dropped it whole. Commissions
  appeared to do nothing, with no server-side error at all. The client's own
  telemetry named the bad ids (`CmdId=24670`, `28030`, `24983`), the real values
  are `7522` / `1939` / `23872`, and they now come from `PacketOpcodes` rather
  than literals, so they cannot drift out of sync with the opcode table again.
  The message *field numbers* are still reasoned, not harvested: only
  `TakeDailyTaskScoreRewardReq/Rsp` exist among the generated protos, so a wrong
  field now reads as garbage instead of the packet vanishing — a strictly better
  failure to debug. See the harvest item under "What does not work".
- **Artifact shop** — every official 5-star piece, rolled fresh per purchase.
  Configurable; see the table at the bottom.
- Updated mob and gadget spawns up to version 5.4, drops, the inbox, widgets, and
  the weekly boss.

# What does not work (yet)

The honest list, because "WIP" in the header is doing real work here. These are
known-broken, not unknown:

- **Daily commissions are field-guesses.** The CmdIds are correct 7.0.0 values
  now (see above), but the message *layouts* — which field number holds the task
  id, the progress, the reward id — are reasoned by hand, not read from the
  client. Only `TakeDailyTaskScoreRewardReq/Rsp` have generated protos in-tree.
  A wrong field shows up as garbage in the commission panel instead of the
  packet vanishing silently, which is debuggable rather than invisible, but it
  is still a guess. Fixing this needs a real proto harvest (below).
- **Proto harvesting is the single biggest unblock.** `debug/harvest-opcodes.txt`
  already collects every opcode the client sends that nothing handles. The
  opcodes are easy; the *schemas* are the missing half, and they gate roughly
  every feature listed in the harvest list — the daily-task layouts above are
  just the one that bit hardest.
- **Anything not in "What works".** If it is not listed above, assume it does
  nothing. The client reaching for real SDK gateways
  (`ConnectGateFailure`, `SafeConnect failed`) in `debug/telemetry.log` is
  expected on a private server and is not a bug here.
- **Combat ability noise.** `AbilityInstErrorTrue/False` lines and a recurring
  `UnhandledCombatType: COMBAT_SPECIAL_MOTION_INFO typeVal=19` appear in the
  client log at a low rate. Not crash-grade, and not yet investigated.

# Requirements

- **[Java 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)**
  or newer, on `PATH`.
- **MongoDB**. `task serve` expects the Docker container `luna-mongo` — create it
  once:
  ```
  docker run -d --name luna-mongo -p 27017:27017 mongo
  ```
  A locally-installed `mongod` on `127.0.0.1:27017` works just as well; only the
  task convenience commands assume the container name.
- **[NodeJS](https://nodejs.org/) 20** — only for handbook generation. Skip it
  and pass `-PskipHandbook=1` if you do not want the handbook.
- **[Rust](https://rust-lang.org/learn/get-started/) + Cargo** — only for the
  client patch. The patch crate needs the **nightly** toolchain and the `windows`
  crate, both of which `cargo` fetches.
- **The CN game version 7.0.0** (`YuanShen.exe`). Other regions and other
  versions will not connect.
- **[Go Task](https://taskfile.dev/)** — the runner every command below goes
  through. On Windows, `winget install Task.Task` or `scoop install task`.

# First-time setup

```
git clone --recurse-submodules https://github.com/capyb2222/LunaGC.git
```

If `patch/` is empty you forgot the flag — run `git submodule update --init`, or
clone [animegamepatch](https://github.com/capyb2222/animegamepatch) into `patch/`
yourself.

Then:

1. **Resources.** Download [LunaGC-Resources](https://github.com/capyb2222/LunaGC-Resources)
   and extract it into a `resources/` folder inside the repository. Without it the
   server starts but has no text maps, no drop tables and no quest scripts.
2. **`.env`.** Copy `.env.example` to `.env`. Everything in it is optional —
   `GAME_PATH` can stay empty and the game directory is read from the miHoYo
   launcher registry. Set it only if the client lives outside the launcher.
3. **Build.** `task build` makes both the patch DLL and the server jar. See below.
4. **Patch the client.** `task patch`. See below.
5. **Start the server.** `task serve`.

# Building

```
task build
```

This runs both:

| Task | What it does |
| --- | --- |
| `task build:patch` | `cargo build --release` in `patch/` → `patch/target/release/ext.dll` |
| `task build:jar` | `gradlew jar` → `LunaGC-7.0.0.jar` in the repo root |

`task build:jar` is timestamp-based, not checksum-based: the proto sources are
tens of thousands of generated files and hashing them all takes longer than the
incremental build would. Both tasks are no-ops when nothing changed, so
`task patch` (which depends on `build:patch`) does not rebuild the DLL every
time.

To skip handbook generation, build the jar by hand:

```
.\gradlew.bat jar -PskipHandbook=1 --console=plain
```

The handbook is generated as `GM Handbook.txt` / `handbook.html` in the repo
root; both are gitignored.

# Running the server

```
task serve          # start MongoDB's container check + the server, detached
task serve:status   # is MongoDB up, is the dispatch server listening, is the game server up
task serve:stop     # stop the server (MongoDB keeps running)
task serve:logs     # page through start_stdout.log
```

The server writes `start_stdout.log` and `start_stderr.log` in the repo root and
survives the shell you started it from — it is a detached process, not a child of
the task runner.

Extra server arguments pass through with `-ServerArgs`. The useful one is
`-debug`, which turns on DEBUG logging without packet spam:

```
task serve -ServerArgs "-debug"       # DEBUG logging, no packets
task serve -ServerArgs "-debug all"   # DEBUG logging + every packet
```

Quest progress is only logged at DEBUG. If you want to follow a quest chain in
the log, `-debug` is required; `task dev` sets it for you.

**Create an account before you log in.** `task serve` opens a visible console
window for the server — type into it:

```
account create <name> <uid>
```

There is no web panel by default. If you closed that window, the same command
works from any shell that has the server's stdin; the console is the easy option.

# Patching the client

```
task patch          # build the DLL if stale, then install it into the client
task patch:status   # what is patched right now, and are the backups intact
task patch:reset    # restore the pristine client files from their backups
```

`task patch` does the whole job: it builds `ext.dll` if the sources changed, then
installs the proxy over `Astrolabe.dll` in `YuanShen_Data\Plugins`, rewrites the
40 dispatch URLs inside `AccountPlatNative.dll` and swaps its passport key for
the server's. It keeps `.lunagc-bak` backups next to each patched file so
`task patch:reset` can undo it exactly. Run `task patch:status` whenever anything
about the client looks wrong — it prints the whole state and exits non-zero on a
problem.

You only need Rust if you are changing the patch itself. The built DLL is
committed-adjacent and `task patch` builds it when the sources change.

**While the game is running, `task patch:status` reads the session state, not
the install state.** Both swapped slots hold their pristine image on disk for
the whole session — that is the mechanism that fixes "the client is damaged" —
so the anti-cheat slot reads `stock`, the passport key reads `miHoYo key`, and
each live copy reads `LIVE`. The exit code is 0 and the output names the running
PID. Only after the game exits do those same readings mean the install is
unpatched. `task dev` checks for a running client before it checks the patch, so
it reports the session rather than blocking on the slots.

### Manual install (if you cannot use the task)

1. Build `patch/target/release/ext.dll` with `cargo build --release` in `patch/`.
2. Back up `YuanShen_Data\Plugins\Astrolabe.dll` somewhere safe.
3. Copy `ext.dll` over `Astrolabe.dll` in that same folder.

The URL rewrites and the passport key inside `AccountPlatNative.dll` are not
optional — without them the client reaches the launcher's real passport server
and login fails with "账号或密码错误". `task patch` is the supported way to apply
all three.

### If the game crashed or was force-closed

The patch crate swaps a patched image into its slot while the game runs and moves
it back when the process detaches. **A game that dies mid-session — a crash, a
`taskkill /F`, a power loss — leaves that live copy stranded in the slot**, which
means the file the next launch loads is the pristine one and the client is
unpatched without anyone telling you.

`task patch:status` catches this, and `task dev` fixes it automatically (see
below). From the command line, just run `task patch` again — it detects the
stranded copy and re-deploys.

# Developer debug sessions — `task dev`

```
task dev            # server in -debug mode + patch check + client + the monitor
task dev:stop       # close the game and wait for the session report
task dev:status     # is a session being recorded, and by which process
task dev:report     # print the latest session report
```

`task dev` is the one command for debugging a play session. It starts the server
with `-debug` (reusing one that is already listening, and saying so if it was not
started with `-debug`), checks the patch state and refuses to launch if the client
is not launch-ready, launches `YuanShen.exe`, then exits and leaves a **hidden,
detached monitor process** in charge. The monitor outlives the terminal that ran
the task; it stops when the game does.

Pass `-SkipPatchCheck` to launch with the client as-is (`task dev -SkipPatchCheck`),
useful after a manual `task patch`.

## What the monitor records

The monitor watches the game process and the server log until the game exits,
then writes a report. Everything lands in `debug/` (gitignored):

| File | Contents |
| --- | --- |
| `report.md` | The session summary — read this first |
| `report-<yyyyMMdd-HHmmss>.md` | A timestamped copy, so old sessions are not overwritten |
| `session.json` | The same data as machine-readable fields |
| `all.log` | Every server line from this session, nothing before it |
| `error.log` | Server errors and exceptions only |
| `unhandled.log` | Every packet that arrived with no handler |
| `harvest-opcodes.txt` | The deduplicated opcode list — the work list |
| `telemetry.log` | Client-uploaded telemetry the server received |
| `quest.log` | Quest accepts, completions and finishes |
| `index.txt` | Line counts per bucket |
| `autofix.log` | What the auto-fix did, if it ran |

The report's headline verdict is one of:

- **`CRASHED`** — the game exited with a non-zero code, or Windows Error
  Reporting logged a new crash for `YuanShen.exe` during the session, or a new
  dump appeared in `%LOCALAPPDATA%\CrashDumps`.
- **`ERRORS`** — no crash, but the server logged genuine errors.
- **`CLEAN`** — neither.

The report also carries the game's **exit code as an eight-digit hex**
(`0x0000002A`, not `-42`), the **WER event count against a baseline taken when the
session started** — so a machine with a long history of old crashes does not
report a false `CRASHED` — and the number of new crash dumps.

## Crash and error detection, and how it triggers a fix

The monitor is built around one observation: a private server's most common
silent failure is a packet the server does not handle. So the monitor separates
three things that a naive `grep ERROR` conflates:

1. **Real server errors** — `ERROR`, `Exception`, `SEVERE` in the server's own
   log → `error.log` and the report's error count.
2. **Client telemetry** — the client uploads JSON bodies containing `SuperDebug`,
   `"error_code"`, `WarningAlarm`, `PACKET_HEAD_MAGIC_ERROR`. These say "error"
   and trip a naive filter, but they are the client talking about itself, not a
   server fault. They go to `telemetry.log` only, and are explicitly excluded
   from `error.log`.
3. **Unhandled packets** — the `... arrived and nothing handles it - N bytes -
   fields {...}` lines the server emits at INFO for any opcode with no handler →
   `unhandled.log`, deduplicated into `harvest-opcodes.txt`.

`harvest-opcodes.txt` is the actionable part. It is a sorted, deduplicated list of
every opcode the client sent that the server ignored, so a session that walks
through a broken feature hands you the exact list of handlers to write. This is
how the beginner-quest chain and the login chain were found.

The **auto-fix** is deliberately narrow: if the session ended with a patched image
stranded in a slot (the "client is damaged next launch" condition), the monitor
re-runs the patch deploy itself and logs it to `autofix.log`. Everything else is
reported as evidence for you, not guessed at.

## Reading a session

```
task dev:stop       # closes the game; the monitor writes the report and exits
task dev:report     # prints debug/report.md
```

A typical loop: `task dev`, play until something breaks, `task dev:stop`, read the
verdict, then:

- `CRASHED` → check the exit code and `debug/error.log`; if the crash is in the
  client, `debug/telemetry.log` often holds the client's own account of it.
- `ERRORS` → `debug/error.log` for the stack, `debug/all.log` for the surrounding
  lines.
- a feature that silently did nothing → `debug/harvest-opcodes.txt`; if the
  feature's request is there, the handler is missing or wrong, and the logged
  field values are the request's actual contents.

`debug/all.log` starts exactly at the session boundary — the offset is recorded
once the server is up but before the game is launched, and the monitor seeks
there rather than asking `Get-Content` for "the end of the file", which is
wherever that cmdlet happens to run. The server's startup banner lands above the
boundary on purpose; everything the session itself produced is below it, and
nothing written while the monitor process was still coming up is lost.

## Bucket reset warnings

Each session starts by erasing the buckets from the previous one, so `error.log`
is *this* session's errors and not a rerun of the last session's stack traces.
The erase is verified, not assumed: it opens each bucket exclusively and
truncates it, then checks the file's length. A length that is still non-zero
means a handle is still holding the bucket open — the usual cause is a monitor
from an earlier session that never closed its writers, which is what happens
when a session is killed at the terminal instead of ending through the game
exiting.

When the erase is refused, the monitor **moves the bucket aside** to
`<bucket>.prev-session-<HHmmss>` and starts a fresh one, rather than leaving the
session to append onto a previous session's tail. The timestamp matters: two
sessions in a row that both wedge the same bucket would otherwise overwrite each
other's moved-aside file, and the second session's note would point at contents
that were no longer what it described.

If the bucket can be neither erased nor moved — a handle held with an exclusive
share, which is how the pre-fix writers opened their files — it is reported and
**skipped for the session**: the monitor marks that bucket dead, writes nothing
to it, and its lines never reach this session's counts. That is why a session
whose `all.log` was wedged still has a complete `error.log` and `unhandled.log`,
and why the verdict does not come back `ERRORS` on the strength of an old
`error.log` full of someone else's stack traces.

Both outcomes are surfaced in three places:

- a **`## bucket reset warnings`** section in `report.md`, placed above the file
  list it qualifies, which is where to look when a session's buckets look wrong;
- the **`resetNotes`** array in `session.json`, one entry per affected bucket,
  naming the file it was moved to or why it could not be moved;
- a **`[STALE]`** tag next to that bucket's line count in `index.txt`, marking a
  count that is accurate about the file but not about the session.

The moved-aside files are gitignored alongside the rest of `debug/`. Delete them
once you have read them; keeping them is only useful when two sessions need
comparing.

## Questing must stay enabled

`server.game.gameOptions.questing.enabled` defaults to **`true`** and should stay
that way. With it off, `PacketQuestListNotify` and `PacketFinishedParentQuestNotify`
drop every quest that is not already `FINISHED`, and a brand-new account has no
saved quest state at all — the quest log opens empty and the beginner chain never
appears. The old README told you to set it to false; that is wrong for this fork.

Turning it back on for an account that only ever played with it off is safe:
there is nothing unfinished stored, so nothing replays. The dangerous direction
is off **after** on, on an account that has saved progress — that is what replays
cutscenes.

`useEncryption` and `useInRouting` should both be `false`; they already are by
default.

# Artifact shop

Every official 5-star artifact piece - 290 of them, the five slots of all 62 released sets - is on
sale in the general goods store (Blanche's *Second Life*, next to the fountain in Mondstadt).

Each purchase rolls the piece fresh rather than handing over a fixed copy, the way an artifact
domain does: the main stat is drawn from the slot's real pool and the substats from the game's own
affix table, so every number printed on the piece is one the game would print. It arrives at +20
with nine substat rolls on it, and the odds are weighted towards CRIT Rate, CRIT DMG, ATK%,
Elemental Mastery and the DMG bonuses, and towards the top end of each roll. Buying several at once
gives you that many separately rolled pieces.

Tune it under `server.game.gameOptions.artifactShop` in `config.json`:

| Option | Default | What it does |
| --- | --- | --- |
| `enabled` | `true` | Turns the listing off entirely. |
| `shopId` | `1004` | Which shop carries it. `1001` is Paimon's Bargains, straight off the shop menu. |
| `costMora` / `costPrimogems` | `20000` / `0` | Price per piece. |
| `costItemId` / `costItemCount` | `0` / `0` | An item to charge on top of the currencies. |
| `buyLimit` | `0` | Purchases per piece per player. `0` is unlimited. |
| `artifactLevel` | `20` | The upgrade level pieces arrive at, `0`-`20`. |
| `critWeight` | `8` | Weight multiplier for CRIT Rate and CRIT DMG. `1` rolls them as the game does. |
| `damageWeight` | `3` | Weight multiplier for ATK%, Elemental Mastery and the DMG bonuses. |
| `highRollBias` | `3` | How hard each stat leans towards the best of its four values. `0` rolls evenly. |

Setting the last three to `1`, `1` and `0` gives you plain, unweighted domain rolls.

# Troubleshooting

- **"the client is damaged"** — the client's integrity check saw a patched file it
  does not expect. Run `task patch:status`. If the passport slot or the
  anti-cheat slot reads `stock`, `PARTIAL`, `STALE` or shows a stranded live copy,
  run `task patch` and relaunch. This was a recurring crash at ~100 seconds into a
  session; the in-process swap in the patch crate is what fixed it, and a
  stranded copy from a force-closed game is the one way it comes back.
- **"账号或密码错误" (account or password error)** — the passport key inside
  `AccountPlatNative.dll` was not swapped for the server's. Run `task patch`.
- **"无法连接网络" (cannot connect to network)** — the dispatch server is down, or
  the URLs in the client still point at the real ones. Check `task serve:status`
  and `task patch:status`.
- **Quest log is empty / no beginner quest** — `questing.enabled` is off. It
  defaults to on; see the section above. Also confirm the server is running with
  `-debug`, since quest progress is only logged at DEBUG.
- **No account / login refused** — create one first. The server has no web panel
  by default; use the console command `account create <name> <uid>`.
- **MongoDB connection timeout** — check the service. On Windows, `Win+R` →
  `services.msc` → look for the MongoDB Server entry and start it. On Linux,
  `systemctl status mongod` and `systemctl start mongod`; if that gives error 14,
  fix the ownership of the data directory and the socket
  (`sudo chown -R mongodb:mongodb /var/lib/mongodb` and
  `sudo chown mongodb:mongodb /tmp/mongodb-27017.sock`) and try again.
- **Windy** — put your `.luac` files in `C:\Windy` (create the folder if it does
  not exist).
- **A feature silently does nothing** — run `task dev`, reproduce it,
  `task dev:stop`, and look at `debug/harvest-opcodes.txt`. If the feature's
  request name is there, the server received it and had no handler for it.

# Repository layout

```
patch/          Rust cdylib -> ext.dll, the client patch (git submodule)
src/            the server
  src/generated/  generated protobuf sources; do not edit by hand
tools/          every task command lives here as its own .ps1
debug/          `task dev` session output (gitignored)
resources/      LunaGC-Resources, extracted here (gitignored)
Taskfile.yml    the task definitions
.env            machine-local settings, copied from .env.example (gitignored)
```

Everything in `tools/` that the Taskfile invokes is named without a leading
underscore; the `_`-prefixed files are ad-hoc analysis scratch and are gitignored.

# Credit

girluh's [LunaGC](https://github.com/girluh/LunaGC)

kitkat's [patch](https://github.com/capyb2222/animegamepatch)

Terax for nt
