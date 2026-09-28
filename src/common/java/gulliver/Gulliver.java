package gulliver;

import gulliver.common.GulliverConfig;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent mod core. Each loader's entrypoint installs its
 * {@link gulliver.platform.Platform} and then calls {@link #init()}.
 */
public final class Gulliver {
    public static final String MOD_ID = "gulliver";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Gulliver() {}

    public static Identifier id(String path) {
        //#if MC >= 1.21
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
        //#else
        //$$ return new Identifier(MOD_ID, path);
        //#endif
    }

    /** Common setup shared by every loader. Runs once, on both sides. */
    public static void init() {
        LOGGER.info("Gulliver initializing on {}", gulliver.platform.Services.platform().loaderName());
        GulliverConfig.load();
    }
}
