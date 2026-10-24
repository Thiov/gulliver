package gulliver.client;

import java.util.Locale;

/**
 * Sizes as people say them: 2, 1½, ¼, ⅛, 1/32 where the number is exactly
 * such a fraction, a short decimal (0.31) otherwise.
 */
public final class SizeFormat {
    private SizeFormat() {}

    private static final int[] DENOMINATORS = {2, 3, 4, 5, 6, 8, 16, 32};

    public static String of(double size) {
        if (Double.isNaN(size) || Double.isInfinite(size)) return "?";
        if (size < 0.0D) return "-" + of(-size);
        long whole = (long) Math.floor(size + 1.0E-3D);
        double rest = size - whole;
        if (rest < 1.0E-3D) return Long.toString(whole);
        for (int q : DENOMINATORS) {
            int p = (int) Math.round(rest * q);
            if (p < 1 || p >= q || Math.abs(rest - (double) p / q) > 1.5E-3D) continue;
            int g = gcd(p, q);
            String fraction = fraction(p / g, q / g);
            if (whole == 0) return fraction;
            // A glyph sits right after the whole number (1½); a slash form needs a gap (1 1/16).
            return fraction.length() == 1 ? whole + fraction : whole + " " + fraction;
        }
        String text = String.format(Locale.ROOT, "%.2f", size);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') end--;
        if (end > 0 && text.charAt(end - 1) == '.') end--;
        return text.substring(0, end);
    }

    private static String fraction(int p, int q) {
        return switch (q * 10 + p) {
            case 21 -> "½";
            case 31 -> "⅓";
            case 32 -> "⅔";
            case 41 -> "¼";
            case 43 -> "¾";
            case 51 -> "⅕";
            case 52 -> "⅖";
            case 53 -> "⅗";
            case 54 -> "⅘";
            case 61 -> "⅙";
            case 65 -> "⅚";
            case 81 -> "⅛";
            case 83 -> "⅜";
            case 85 -> "⅝";
            case 87 -> "⅞";
            default -> p + "/" + q;
        };
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
