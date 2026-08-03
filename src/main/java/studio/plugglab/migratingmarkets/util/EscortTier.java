package studio.plugglab.migratingmarkets.util;

public class EscortTier {
    public final int threshold;
    public final double discount;
    public final String title;

    public EscortTier(int threshold, double discount, String title) {
        this.threshold = threshold;
        this.discount = discount;
        this.title = title;
    }
}
