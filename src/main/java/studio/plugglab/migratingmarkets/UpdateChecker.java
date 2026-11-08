package studio.plugglab.migratingmarkets;

import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Async update checker (GitHub Releases API) — the same one shipped in
 * v1.0. Restored for 2.1.0: the copy under util/ had been pasted from
 * another plugin (wrong package/classes) and couldn't compile, so it was
 * dropped. Changes vs 1.0: can be disabled with update-checker.enabled,
 * and only reports an update when the release is actually NEWER (1.0
 * flagged any different tag, e.g. "v1.0" vs "1.0.0").
 */
public class UpdateChecker {

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/plugglab/migrating-markets/releases/latest";

    public static final String DOWNLOAD_URL = "https://modrinth.com/plugin/migratingmarkets";

    private final MigratingMarkets plugin;
    // Written on the async thread, read on the main thread.
    private volatile String latestVersion;
    private volatile boolean updateAvailable;

    public UpdateChecker(MigratingMarkets plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation") // getDescription() is fine for a Bukkit-style plugin.yml
    public void check() {
        String current = plugin.getDescription().getVersion();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) URI.create(GITHUB_API_URL).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "MigratingMarkets-UpdateChecker/" + current);

                if (conn.getResponseCode() != 200) {
                    return; // no releases yet / rate limited — stay quiet
                }
                String json;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    json = reader.lines().collect(Collectors.joining());
                }
                String latest = parseTagName(json);
                if (latest == null) return;
                latestVersion = latest;
                updateAvailable = isNewer(latest, current);
                if (updateAvailable) {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().warning(
                            "A new version of MigratingMarkets is available: v" + stripV(latest)
                                    + " (you have v" + current + "). Download: " + DOWNLOAD_URL));
                }
            } catch (Exception e) {
                plugin.getLogger().fine("Update check failed: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    /** Message sent to admins on join, or null if there's nothing to report. */
    @SuppressWarnings("deprecation")
    public String getAdminNotice() {
        if (!updateAvailable || latestVersion == null) return null;
        return "§6[MigratingMarkets] §eNew version available! §7v" + plugin.getDescription().getVersion()
                + " §8→ §av" + stripV(latestVersion) + " §8| §b" + DOWNLOAD_URL;
    }

    private static String parseTagName(String json) {
        String key = "\"tag_name\":";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start = json.indexOf('"', start + key.length());
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end).trim();
    }

    private static String stripV(String v) {
        return v.startsWith("v") || v.startsWith("V") ? v.substring(1) : v;
    }

    /** Numeric dotted-version comparison ("2.1.0" vs "v2.2"); non-numeric parts count as 0. */
    static boolean isNewer(String latest, String current) {
        String[] a = stripV(latest).split("[.\\-+]");
        String[] b = stripV(current).split("[.\\-+]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? parse(a[i]) : 0;
            int y = i < b.length ? parse(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
