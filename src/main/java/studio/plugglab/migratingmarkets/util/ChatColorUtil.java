package studio.plugglab.migratingmarkets.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Component.text("§7foo") does NOT parse legacy color codes — it treats
 * them as literal characters, which is why chat kept showing raw
 * section-sign junk. Anywhere a colored string needs to become a real
 * Component (or get merged into one), it must go through here instead.
 */
public final class ChatColorUtil {

    private ChatColorUtil() {
    }

    /** Parses a string using '&' codes (as used in config.yml) into a real Component. */
    public static Component ampersand(String raw) {
        if (raw == null) return Component.empty();
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    /** Parses a string using '§' codes into a real Component. */
    public static Component section(String raw) {
        if (raw == null) return Component.empty();
        return LegacyComponentSerializer.legacySection().deserialize(raw);
    }

    /** Strips all color/formatting, leaving plain text (safe to concatenate into other strings). */
    public static String stripToPlain(String ampersandCoded) {
        return PlainTextComponentSerializer.plainText().serialize(ampersand(ampersandCoded));
    }
}
