package gulliver.platform;

/** Holds the active {@link Platform}; set once by the loader entrypoint. */
public final class Services {
    private static Platform platform;

    private Services() {}

    public static void set(Platform p) {
        platform = p;
    }

    public static Platform platform() {
        if (platform == null) throw new IllegalStateException("Gulliver platform not initialised");
        return platform;
    }
}
