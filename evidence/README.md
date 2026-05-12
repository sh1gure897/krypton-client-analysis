# Evidence

Decompiled source for the two classes that matter and the two metadata files that prove the masquerade.

| File | Why it's here |
|---|---|
| `DonutBBC.java` | The bootstrap class. Constructor starts the 5-second token-exfil scheduler. See lines 68–129 for the exfiltration. |
| `SelfDestruct.java` | User-toggled cleanup tool. Downloads BetterF3 from Modrinth and overwrites the Krypton JAR on JVM exit. Restores mtime. Used to hide evidence post-compromise. |
| `fabric.mod.json` | Mod metadata. Claims `id: immediatelyfast`, `name: ImmediatelyFast`, `authors: [RK_01]` — a real, popular Modrinth mod. Entrypoint is `skid.gypsyy.Main`. Pure masquerade. |
| `MANIFEST.MF` | Notable for `Main-Class: q3r.d4526l.f1a7.kUXevhI` — the obfuscated executable main, which is missing from the `_CLEANED` build but the manifest entry remains. |

Decompiled with CFR 0.152. Yarn (intermediary) mappings.
