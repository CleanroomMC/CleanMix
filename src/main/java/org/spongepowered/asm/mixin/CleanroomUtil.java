/*
 * This file is part of Mixin, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.asm.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.injection.selectors.ISelectorContext;
import org.spongepowered.asm.mixin.refmap.IMixinContext;
import org.spongepowered.asm.util.VersionNumber;

public final class CleanroomUtil {

    public enum Loader {

        MIXIN_BOOTER,
        CLEANROOM;

    }

    /**
     * Config decoration containing the owning mod id.
     */
    public static final String KEY_MOD_ID = "cleanmix-id";

    /**
     * Config decoration containing one of the compatibility constants below.
     */
    public static final String KEY_COMPATIBILITY = "cleanroom-compat";

    // CleanMix compatibility boundaries, (major * 1000 + minor) * 1000 + patch

    /** CleanMix compatibility version 0.1.0, the baseline. */
    public static final int COMPATIBILITY_0_1_0 = 1000;

    /** CleanMix compatibility version 0.6.0. */
    public static final int COMPATIBILITY_0_6_0 = 6000;

    /** Baseline used by MixinBooter 10.7, Cleanroom 0.5.17, and older versions. */
    public static final int COMPATIBILITY_OLD = COMPATIBILITY_0_1_0;

    /** Latest CleanMix compatibility version. */
    public static final int COMPATIBILITY_LATEST = COMPATIBILITY_0_6_0;

    /** Last MixinBooter version using old compatibility. */
    public static final VersionNumber MIXIN_BOOTER_10_7 = VersionNumber.parse("10.7");

    /** First MixinBooter version using latest compatibility. */
    public static final VersionNumber MIXIN_BOOTER_11_0 = VersionNumber.parse("11.0");

    /** Last Cleanroom version using old compatibility. */
    public static final VersionNumber CLEANROOM_0_5_17 = VersionNumber.parse("0.5.17");

    /** First Cleanroom version using latest compatibility. */
    public static final VersionNumber CLEANROOM_0_6_0 = VersionNumber.parse("0.6.0");

    /**
     * Resolve the compatibility behaviour for the supplied host version.
     * Unknown or malformed versions are treated as old.
     *
     * @param loader host which registered the mixin config
     * @param version host version, normally the owning mod's minimum dependency
     * @return old or latest compatibility
     */
    public static int getCompatibility(Loader loader, String version) {
        if (loader == null) {
            return COMPATIBILITY_OLD;
        }
        VersionNumber parsed = VersionNumber.parse(version);
        VersionNumber firstLatest = loader == Loader.MIXIN_BOOTER ? MIXIN_BOOTER_11_0 : CLEANROOM_0_6_0;
        return parsed.compareTo(firstLatest) >= 0 ? COMPATIBILITY_0_6_0 : COMPATIBILITY_0_1_0;
    }

    /**
     * Decorate a config with its owning mod and compatibility level.
     *
     * @param config mixin config to decorate
     * @param modId owning mod id
     * @param loader host which registered the config
     * @param version host version, normally the owning mod's minimum dependency
     */
    public static void decorate(IMixinConfig config, String modId, Loader loader, String version) {
        if (!config.hasDecoration(KEY_MOD_ID) && modId != null) {
            config.decorate(KEY_MOD_ID, modId);
        }
        if (!config.hasDecoration(KEY_COMPATIBILITY)) {
            config.decorate(KEY_COMPATIBILITY, getCompatibility(loader, version));
        }
    }

    public static String getModId(IMixinConfig config) {
        return getModId(config, config.getCleanSourceId() == null ? "(unknown)" : config.getCleanSourceId());
    }

    public static String getModId(IMixinConfig config, String defaultValue) {
        return getDecoration(config, KEY_MOD_ID, defaultValue);
    }

    public static String getModId(ISelectorContext context) {
        IMixinConfig config = getConfig(context);
        return getModId(config, config.getCleanSourceId() == null ? "(unknown)" : config.getCleanSourceId());
    }

    public static int getCompatibility(ISelectorContext context) {
        return getCompatibility(getConfig(context));
    }

    public static int getCompatibility(IMixinContext context) {
        return getCompatibility(context.getMixin().getConfig());
    }

    public static int getCompatibility(IMixinConfig config) {
        return getDecoration(config, KEY_COMPATIBILITY, COMPATIBILITY_OLD);
    }

    private static IMixinConfig getConfig(ISelectorContext context) {
        return context.getMixin().getMixin().getConfig();
    }

    private static <T> T getDecoration(IMixinConfig config, String key, T defaultValue) {
        if (config.hasDecoration(key)) {
            return config.getDecoration(key);
        } else {
            return defaultValue;
        }
    }

    private CleanroomUtil() {
    }
}
