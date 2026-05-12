# If you installed Krypton — recovery guide

Honest assessment first: **this is bad but recoverable**. The malware in the sample I looked at lives entirely inside the JAR (no registry persistence, no scheduled tasks, no native drops). The actual loss is the session token, which is recoverable by signing out everywhere. You almost certainly do not need to reinstall Windows. There's a section at the end on how to handle the edge case where AV picks up something else.

## Order matters

Do these in order. The first step is time-sensitive — a session token in attacker hands is being used.

### 1. Kill the stolen session token (5 minutes)

Open these in a browser. Don't skip:

- `https://account.live.com/proofs/Manage` → review "Sign-in activity" for sessions you don't recognize.
- `https://account.microsoft.com/security` → "Sign out everywhere" (sometimes labelled "Sign me out of all devices"). This is the one that revokes the stolen token.
- Same page → change your Microsoft account password.
- Same page → turn on two-factor authentication if it's not already on. Use an authenticator app, not SMS if you have the choice.

### 2. Re-auth Minecraft Launcher

Open the launcher, sign out, sign back in. This proves the old token is dead (you'll be forced to re-auth properly) and gives you a fresh session bound to the new password.

### 3. Quarantine the JAR (don't just delete — save it for evidence)

```powershell
$quar = "$env:USERPROFILE\Desktop\_QUARANTINE_$(Get-Date -Format yyyyMMdd_HHmmss)"
New-Item -ItemType Directory -Force -Path $quar | Out-Null
Move-Item "$env:APPDATA\.minecraft\mods\*krypton*" $quar -ErrorAction SilentlyContinue
Move-Item "$env:APPDATA\.minecraft\mods\immediatelyfast*" $quar -ErrorAction SilentlyContinue  # the masquerade name
```

The second `Move-Item` is because the mod identifies itself as ImmediatelyFast in `fabric.mod.json`. If you "installed ImmediatelyFast" recently from a sketchy source, that file might actually be Krypton. Check the SHA-256 against the one in [`iocs.txt`](./iocs.txt). If it matches, it's the malicious build.

### 4. Check for execution traces

Quick grep through Minecraft logs — if `[User Tracker]` appears in your logs, the exfiltration definitely ran:

```powershell
$mclogs = "$env:APPDATA\.minecraft\logs"
Get-ChildItem $mclogs -Filter *.log* -Recurse |
  Select-String -Pattern "User Tracker|krypton-client|nxes|Registering user"
```

DNS cache check (only useful if you haven't rebooted since):

```powershell
ipconfig /displaydns | Select-String "krypton-client"
```

Either of these hitting is confirmation that the exfil ran successfully. It doesn't change what you do — you've already revoked the token in step 1 — but it tells you how serious the exposure was.

### 5. Defender Offline scan

Not just Quick scan. Offline scan boots into a clean recovery environment, which catches things that hide while Windows is running:

```powershell
Start-MpScan -ScanType FullScan          # full scan first, takes a while, can run in background
# Then, for a more thorough check that survives rootkits:
Start-MpWDOScan                          # this triggers a reboot into Defender Offline
```

If you want a second opinion (always smart, AVs disagree), grab ESET Online Scanner or Malwarebytes Free, run them once, uninstall when done.

## What to change besides the Microsoft account

- **Passwords saved in browsers on this machine** — if you logged into a hack-client-bundled Discord, store account, etc., assume those creds are also worth rotating. The Krypton JAR doesn't itself read browser data, but if you installed it from a download that included other binaries (a Discord installer, a "launcher", a crack), those could.
- **Discord** — revoke sessions at User Settings → Devices, and reset password if you use it on the same machine.
- **Any account where you reused the Microsoft account password.**

## "AV flagged something but I really don't want to reinstall Windows"

This is the question that scared you most, so here it is in detail.

First, separate the situations:

### Case A: AV only flagged the Krypton JAR (or files inside `.minecraft`)

You're fine. Delete the JAR (you already quarantined it), make sure it's gone from any backup copies (Downloads folder, Recycle Bin, OneDrive, mod manager caches), and stop. No reinstall needed. Windows isn't infected — the JVM was carrying a poisoned Java program, but the JVM itself is clean.

### Case B: AV flagged files in `%APPDATA%`, `%LOCALAPPDATA%`, `%TEMP%`, or `C:\ProgramData`

Now it's a bit more serious — that suggests something dropped to a Windows-local path. Step through these in order, only escalating if the previous one fails:

**B1. Cataloguing.** Find out exactly what got flagged and where it came from. Defender history:

```powershell
Get-MpThreat
Get-MpThreatDetection | Select-Object -Property ThreatID, Resources, InitialDetectionTime
```

If the path is somewhere a normal user could just delete (a file in `%APPDATA%`, a `.exe` in `%TEMP%`), and Defender has already quarantined it, that often is the end of the story. Reboot and re-scan to confirm.

**B2. Persistence sweep.** Anything that drops a binary will usually also wire itself into one of these. Check all of them:

```powershell
# Run keys
foreach ($k in @(
  "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run",
  "HKCU:\Software\Microsoft\Windows\CurrentVersion\RunOnce",
  "HKLM:\Software\Microsoft\Windows\CurrentVersion\Run",
  "HKLM:\Software\Microsoft\Windows\CurrentVersion\RunOnce",
  "HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Run"
)) {
  if (Test-Path $k) {
    Write-Host "=== $k ==="; Get-ItemProperty $k | Select-Object * -ExcludeProperty PS*
  }
}

# Startup folders
Get-ChildItem "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup"
Get-ChildItem "$env:ProgramData\Microsoft\Windows\Start Menu\Programs\Startup"

# Scheduled tasks touched in the last 30 days
Get-ScheduledTask | Get-ScheduledTaskInfo |
  Where-Object { $_.LastRunTime -gt (Get-Date).AddDays(-30) } |
  Sort-Object LastRunTime -Descending |
  Select-Object TaskName, LastRunTime, NextRunTime, LastTaskResult
```

The Sysinternals tool **Autoruns** (`https://learn.microsoft.com/sysinternals/downloads/autoruns`) shows everything that runs at boot/login in one view and is the right tool for this. Run it as admin, hit View → Hide Microsoft Entries, and look at what's left. Anything unsigned, randomly-named, or pointing at `%APPDATA%` / `%TEMP%` is suspect.

**B3. Multi-engine scan.** Don't rely on just one AV — they each miss different things. After Defender, run one of:

- Malwarebytes Free (one-time scan, then uninstall)
- ESET Online Scanner (no install, single-run)
- Kaspersky Virus Removal Tool (single-run)

If two independent engines say clean, that's pretty strong evidence.

**B4. Reset This PC, "Keep my files".** If you've done B1–B3 and you still have detections coming back, you have two non-nuclear options before a full reinstall:

```
Settings → System → Recovery → Reset this PC
  Choose: "Keep my files"
  Choose: "Local reinstall" (or Cloud, both work)
```

This wipes installed apps, drivers, and most user-installed services/scheduled tasks, but keeps your documents, photos, downloads, etc. From a malware-removal standpoint it's about 90% as good as a clean install for any normal user-mode malware. The remaining 10% is firmware-level / bootkit territory, which is exceptionally rare for Minecraft mod malware (it's a higher effort tier of attack and the Krypton author isn't on that tier — they're shipping a Java token grabber from a Discord server).

**B5. System Restore.** If you have a restore point from before you installed Krypton, rolling back is fast and reverses any system-level changes. Doesn't touch your documents. `rstrui.exe` to launch the wizard.

### Case C: AV flagged something in `C:\Windows\`, `\System32\`, or boot files

This is the only case where a clean install is genuinely the right call, and it's almost certainly not your situation. If you actually see this, before reinstalling: boot a Windows installation media to a recovery prompt and run `sfc /scannow` + `DISM /Online /Cleanup-Image /RestoreHealth`. If that comes back with unrepairable corruption then yes, clean install.

For the Krypton sample I analyzed, you will not end up in Case C. Krypton doesn't touch Windows files. If your AV is flagging things in System32 after running Krypton, something else came along for the ride — possibly from a different "cracked" thing you installed in the same session.

## What I'd actually do in your shoes

In rough priority:

1. Microsoft account: sign out everywhere, change password, 2FA on (5 min, do this first).
2. Quarantine the JAR. Defender full scan in the background.
3. Check the Minecraft logs grep — establishes whether the exfil actually ran.
4. Defender Offline scan once the regular full scan finishes.
5. If both come back clean, stop. You're done.
6. If something pops up: Autoruns + second-opinion scan, then decide if Reset-this-PC is needed.

Total time end to end is usually under an hour. A Windows reinstall takes a day and resets all your software setup, so save it for the rare case it's actually needed.

## Things worth NOT doing

- Don't re-run the JAR "just to see if it does anything visible". Every run sends a fresh token.
- Don't trust `_CLEANED` / `_SAFE` / `_FIXED` reuploads of hack clients from the same community. The author or anyone else can claim they removed the bad parts — verify hashes, decompile, or skip the tool entirely.
- Don't try to undo the exfil by deleting your account or making a new one with the same email — that doesn't help. The session token is per-session and is now dead after step 1; that's the only thing that matters.
- Don't ignore other accounts you logged into on the same Windows session while Krypton was running. If a separate piece of malware was bundled (this `_CLEANED` build doesn't appear to have one, but be cautious), it could have grabbed those too.
