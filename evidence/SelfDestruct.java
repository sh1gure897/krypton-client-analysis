/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sun.jna.Memory
 *  net.minecraft.class_2561
 */
package skid.gypsyy.module.modules.client;

import com.sun.jna.Memory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import net.minecraft.class_2561;
import skid.gypsyy.DonutBBC;
import skid.gypsyy.gui.ClickGUI;
import skid.gypsyy.module.Category;
import skid.gypsyy.module.Module;
import skid.gypsyy.module.setting.BooleanSetting;
import skid.gypsyy.module.setting.Setting;
import skid.gypsyy.module.setting.StringSetting;
import skid.gypsyy.utils.EncryptedString;
import skid.gypsyy.utils.Utils;

public final class SelfDestruct
extends Module {
    public static boolean isActive = false;
    public static boolean hasSelfDestructed = false;
    private final BooleanSetting replaceMod = new BooleanSetting(EncryptedString.of("Replace Mod"), true).setDescription(EncryptedString.of("Replaces the mod with the specified JAR file"));
    private final BooleanSetting saveLastModified = new BooleanSetting(EncryptedString.of("Save Last Modified"), true).setDescription(EncryptedString.of("Saves the last modified date after self destruct"));
    private final StringSetting replaceUrl = new StringSetting(EncryptedString.of("Replace URL"), "https://cdn.modrinth.com/data/8shC1gFX/versions/sXO3idkS/BetterF3-11.0.1-Fabric-1.21.jar");

    public SelfDestruct() {
        super(EncryptedString.of("Self Destruct"), EncryptedString.of("Removes the client from your game |Credits to Argon for deletion|"), -1, Category.CLIENT);
        this.addsettings(this.replaceMod, this.saveLastModified, this.replaceUrl);
    }

    @Override
    public void onEnable() {
        isActive = true;
        hasSelfDestructed = true;
        try {
            Thread.sleep(100L);
        }
        catch (InterruptedException interruptedException) {
            // empty catch block
        }
        DonutBBC.INSTANCE.getModuleManager().getModuleByClass(skid.gypsyy.module.modules.client.DonutBBC.class).toggle(false);
        this.toggle(false);
        DonutBBC.INSTANCE.getConfigManager().shutdown();
        if (this.mc.field_1755 instanceof ClickGUI) {
            DonutBBC.INSTANCE.shouldPreventClose = false;
            this.mc.field_1755.method_25419();
        }
        if (this.replaceMod.getValue()) {
            this.scheduleModReplacement();
        }
        for (Module module : DonutBBC.INSTANCE.getModuleManager().c()) {
            module.toggle(false);
            module.setName(null);
            module.setDescription(null);
            for (Setting setting : module.getSettings()) {
                setting.getDescription(null);
                setting.setDescription(null);
                if (!(setting instanceof StringSetting)) continue;
                ((StringSetting)setting).setValue(null);
            }
            module.getSettings().clear();
        }
        if (this.saveLastModified.getValue()) {
            DonutBBC.INSTANCE.resetModifiedDate();
        }
        Thread memoryCleanupThread = new Thread(() -> {
            Runtime runtime = Runtime.getRuntime();
            for (int i = 0; i <= 10; ++i) {
                runtime.gc();
                try {
                    Thread.sleep(100 * i);
                    Memory.purge();
                    Memory.disposeAll();
                    continue;
                }
                catch (InterruptedException interruptedException) {
                    continue;
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        }, "MemoryCleanup");
        memoryCleanupThread.setDaemon(true);
        memoryCleanupThread.start();
        if (this.mc.field_1724 != null) {
            this.mc.field_1724.method_43496((class_2561)class_2561.method_43470((String)"\u00a7c\u00a7l[SelfDestruct] \u00a7rClient has been cleared. Game will continue running."));
        }
    }

    private void scheduleModReplacement() {
        Thread replacementThread = new Thread(() -> {
            try {
                String downloadUrl = this.replaceUrl.getValue();
                File currentJar = Utils.getCurrentJarPath();
                if (currentJar == null || !currentJar.exists() || !currentJar.isFile()) {
                    return;
                }
                File tempFile = new File(currentJar.getParentFile(), currentJar.getName() + ".tmp");
                if (this.downloadModFile(downloadUrl, tempFile)) {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            Thread.sleep(500L);
                            Files.move(tempFile.toPath(), currentJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                    }, "ModReplacementHook"));
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }, "ModReplacementDownload");
        replacementThread.setDaemon(true);
        replacementThread.start();
    }

    private boolean downloadModFile(String downloadUrl, File targetFile) {
        try {
            URL url = new URL(downloadUrl);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                connection.disconnect();
                return false;
            }
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(targetFile);){
                int bytesRead;
                byte[] buffer = new byte[8192];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
            connection.disconnect();
            return true;
        }
        catch (Exception var14) {
            return false;
        }
    }
}

