package com.lucky.luckyblock;

import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

/**
 * Async update checker using GitHub Releases API.
 *
 * Checks: https://api.github.com/repos/plugglab/LuckyEngine/releases/latest
 * Compares the tag_name field against the current plugin version.
 * Download link: https://modrinth.com/plugin/luckyengine
 *
 * Admins are notified via JoinListener if an update is found.
 * Can be disabled with update-checker.enabled: false in config.yml
 */
public class UpdateChecker {

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/plugglab/migmarkets/releases/latest";

    public static final String DOWNLOAD_URL =
            "https://modrinth.com/plugin/migratingmarkets";

    private final LuckyBlockPlugin plugin;
    private String latestVersion  = null;
    private boolean updateAvailable = false;

    public UpdateChecker(LuckyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void check() {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_API_URL).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Accept",     "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "LuckyEngine-UpdateChecker/"
                        + plugin.getDescription().getVersion());

                if (conn.getResponseCode() != 200) {
                    plugin.getLogger().info("Update check skipped (HTTP " + conn.getResponseCode() + ").");
                    return;
                }

                String json;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    json = reader.lines().collect(Collectors.joining());
                }
                conn.disconnect();

                // Parse tag_name from JSON without an external library
                // GitHub returns: {...,"tag_name":"2.0",...}
                latestVersion = parseTagName(json);
                if (latestVersion == null) {
                    plugin.getLogger().info("Could not parse latest release tag.");
                    return;
                }

                String current = plugin.getDescription().getVersion();
                if (!current.equalsIgnoreCase(latestVersion)) {
                    updateAvailable = true;
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getLogger().warning(
                                "╔═════════════════════════════════════════╗\n"
                              + "║  LuckyEngine Update Available!          ║\n"
                              + "║  Current : v" + padRight(current, 29) + "║\n"
                              + "║  Latest  : v" + padRight(latestVersion, 29) + "║\n"
                              + "║  Download: " + padRight(DOWNLOAD_URL, 30) + "║\n"
                              + "╚═════════════════════════════════════════╝"));
                } else {
                    plugin.getLogger().info("LuckyEngine is up to date (v" + current + ").");
                }

            } catch (Exception e) {
                plugin.getLogger().info("Update check failed: " + e.getMessage());
            }
        });
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isUpdateAvailable() { return updateAvailable; }
    public String  getLatestVersion()  { return latestVersion; }

    /** Formatted message sent to admins on login. */
    public String getAdminNotice() {
        if (!updateAvailable || latestVersion == null) return null;
        String current = plugin.getDescription().getVersion();
        return "§6[LuckyEngine] §eNew version available! "
                + "§7v" + current + " §8→ §av" + latestVersion
                + " §8| §b" + DOWNLOAD_URL;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts tag_name value from GitHub API JSON response.
     * Looks for: "tag_name":"<value>"
     */
    private String parseTagName(String json) {
        String key = "\"tag_name\":\"";
        int start  = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end).trim();
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
