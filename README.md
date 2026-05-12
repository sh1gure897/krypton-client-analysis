# Krypton Client — what's actually in the JAR

A short writeup on the Krypton Minecraft client (the one circulating on DonutSMP forks and that the author pulled down on 2026-05-13 after admitting it was bad). The Discord notice called it a "keylogger". That's not quite right. What it actually does is grab your Minecraft session token and ship it to a server the author controls. Different mechanism, same outcome: someone else can log in as you.

This repo is the static analysis plus a recovery guide if you ran it.

Sample looked at:

```
filename : krypton-1_21_1-v15_CLEANED.jar
size     : 620,716 bytes
SHA-256  : dffd8a921863a5ceb26ee7cf5b1e8094463287c12ea04d191373ba11c82923a0
MD5      : fae8b6c19556e0eccae78e0e725addd1
```

The `_CLEANED` suffix is misleading. Someone stripped the nested JARs (`META-INF/jars/`) and an obfuscated main class (`q3r.d4526l.f1a7.kUXevhI`) before reposting, but the session-token grabber is **still in there**, in `skid/gypsyy/DonutBBC.java`. So treat this build as compromised regardless of the filename.

## TL;DR — 日本語

これは Krypton Minecraft クライアントの静的解析結果と、もし入れてしまった人向けの復旧ガイドです。Discord の告知では「キーロガー」と書かれていますが実際は違って、**ログインから 5 秒おきに Minecraft セッショントークン (UUID・ユーザー名・サーバー名・DonutSMP の所持金/kill/death 込み) を `https://krypton-client.store/` に POST する**仕様です。トークンを握られると Microsoft アカウントのパスワードが分からなくても Minecraft にログインされるため、入れた人はまず Microsoft アカウントから全セッションを切ってください。手順は [`if-you-installed-it.md`](./if-you-installed-it.md) にあります。技術的な内訳は [`analysis.md`](./analysis.md)、AV/ファイアウォール向けの IOC は [`iocs.txt`](./iocs.txt) を見てください。

## What it does

The mod looks and acts like a normal hack client (DonutSMP-oriented — auction sniper, base finders, render mods, etc.). What's bolted on is a quiet user-tracking thread that starts the moment the mod loads:

1. Sets up a `ScheduledExecutorService` that fires every 5 seconds (`DonutBBC.java:69-77`).
2. Once you're in a world, it grabs:
   - your Mojang/Microsoft session token (`mc.method_1548().method_1674()` → `getSession().getSessionId()`),
   - UUID,
   - username,
   - current server,
   - your DonutSMP stats (money, playtime, kills, deaths) — fetched via a hardcoded leaked DonutSMP API bearer.
3. POSTs the whole thing as JSON to `https://krypton-client.store/` with `User-Agent: nxes`.
4. On JVM shutdown it sends a "logout" ping to the same endpoint.

There's no user opt-in. It's not behind a menu toggle. The thread starts in the constructor of the main mod class.

## What it is *not*

Not a keylogger. The `KeyboardMixin` and `MouseMixin` only dispatch in-process events to the hack-client's binding system — they don't write anywhere or send anything off-host. No `java.awt.Robot`, no `Toolkit.getDefaultToolkit().getSystemEventQueue()`, no native DLL drop. So the Discord PSA was wrong about the mechanism, but the impact (account takeover) is comparable, so the advice to revoke sessions / change credentials still stands.

Also worth flagging: `fabric.mod.json` lies about its identity. The metadata claims this is **ImmediatelyFast by RK_01** (a real, popular Modrinth mod), but the entrypoint is `skid.gypsyy.Main`. Pure masquerade.

## Indicators (full list in [`iocs.txt`](./iocs.txt))

```
Domain       https://krypton-client.store/
User-Agent   nxes
Package      skid.gypsyy.*
Mod ID       claims "immediatelyfast" but entrypoint is skid.gypsyy.Main
SHA-256      dffd8a921863a5ceb26ee7cf5b1e8094463287c12ea04d191373ba11c82923a0
```

## If you ran it

Read [`if-you-installed-it.md`](./if-you-installed-it.md). Short version:

1. Revoke all Microsoft account sessions **right now** (this kills the stolen token).
2. Change password + enable 2FA.
3. Sign out of Minecraft Launcher and back in.
4. Quarantine the JAR; full-system AV scan.

Reset-this-PC is overkill in most cases — see the remediation doc for when it's actually warranted vs when deleting the JAR is enough.

## Files

| File | What's in it |
|---|---|
| [`analysis.md`](./analysis.md) | Class-by-class writeup of the malicious bits, with annotated source excerpts |
| [`if-you-installed-it.md`](./if-you-installed-it.md) | Step-by-step recovery, including how to avoid a Windows reinstall |
| [`iocs.txt`](./iocs.txt) | Indicators in copy-pasteable form (hashes, domains, UA strings) |
| [`evidence/`](./evidence) | The actual decompiled malicious classes for reference |

## Reproducing the analysis

Anyone can re-decompile and verify:

```sh
# Get a sample matching the SHA-256 above. Do not run a Minecraft instance with it loaded.
mkdir analysis && cd analysis
cp /path/to/krypton-1_21_1-v15_CLEANED.jar sample.jar
jar xf sample.jar
curl -LO https://github.com/leibnitz27/cfr/releases/download/0.152/cfr-0.152.jar
java -jar cfr-0.152.jar sample.jar --outputdir decompiled --silent true

# The smoking gun:
grep -nE "krypton-client|sessionToken|method_1674" decompiled/skid/gypsyy/DonutBBC.java
```

## Disclaimer

I'm a Minecraft player, not a malware analyst. This is what I found reading the decompiled code — no dynamic analysis, no sandboxing, no network capture. If you spot something I got wrong, open an issue.

Treat anything in this repo as informational. Don't redistribute the malicious JAR. Don't run it.

このマルウェアが配布されたサーバー: https://discord.gg/AXVgAA7Eq5
オーナー曰く数日後にマルウェアを取り除いた安定板を配布するらしいです。
