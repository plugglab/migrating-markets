package studio.plugglab.migratingmarkets.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Small helpers for sounds and titles, tolerant of bad config values. */
public final class Feedback {

    private Feedback() {
    }

    /**
     * Plays a sound by its namespaced key (e.g. "minecraft:entity.villager.yes").
     * Uses the String overload so it works the same on every 1.21.x build
     * (org.bukkit.Sound stopped being an enum in later 1.21 releases).
     * Blank/invalid keys are silently ignored.
     */
    public static void play(Player player, String soundKey) {
        play(player, soundKey, 1.0f, 1.0f);
    }

    public static void play(Player player, String soundKey, float volume, float pitch) {
        if (player == null || soundKey == null || soundKey.isBlank()) return;
        try {
            player.playSound(player.getLocation(), soundKey.trim().toLowerCase(), volume, pitch);
        } catch (RuntimeException ignored) {
            // invalid key in config — never break gameplay over a sound
        }
    }

    public static void title(Player player, Component main, Component sub) {
        player.showTitle(Title.title(main, sub, Title.Times.times(
                Duration.ofMillis(400), Duration.ofMillis(3000), Duration.ofMillis(800))));
    }
}
