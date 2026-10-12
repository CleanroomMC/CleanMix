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
package org.spongepowered.asm.launch;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.net.URL;

/**
 * Stops the subsystem from being defined <em>by</em> LaunchWrapper's
 * {@link LaunchClassLoader} instead of by the classloader which owns the mixin
 * jar.
 *
 * <p>Whoever touches {@link MixinBootstrap} first decides which classloader
 * defines the entire subsystem. Tweakers are safe since LaunchWrapper
 * excludes a tweaker's package before it loads the tweaker.
 * If a coremod boots the subsystem before the container which owns it has
 * installed its exclusions, every mixin class so far is defined by
 * {@link LaunchClassLoader}. The container then excludes the subsystem packages
 * and gets a second, disjoint copy of the subsystem on the parent loader.</p>
 *
 * <p>This is reachable in a development environment, where the mixin jar is on
 * the parent's classpath and the order of <tt>fml.coreMods.load</tt> decides
 * which coremod is constructed first.</p>
 */
public final class LaunchClassLoaderIsolation {

    /**
     * Resource used to check that the parent loader sees the same mixin jar as
     * LaunchClassLoader before anything is delegated to it
     */
    private static final String PROBE_RESOURCE = "org/spongepowered/asm/mixin/MixinEnvironment.class";

    /**
     * Packages which must never be defined by LaunchClassLoader
     */
    private static final String[] EXCLUSIONS = {
        "org.spongepowered.asm.launch.",
        "org.spongepowered.asm.service.",
        "org.spongepowered.asm.mixin.",
        "org.spongepowered.asm.logging.",
        "org.spongepowered.asm.util.",
        "org.spongepowered.asm.lib.",
        "org.objectweb.asm."
    };

    /**
     * True once the exclusions have been installed, or once we have decided not
     * to install them.
     */
    private static boolean applied;

    private LaunchClassLoaderIsolation() {}

    /**
     * If this class was defined by LaunchWrapper's class loader, exclude the
     * subsystem packages from it so that everything loaded from here on is
     * defined by the parent loader instead. Idempotent, and a no-op when the
     * subsystem is already in the right place.
     */
    public static void apply() {
        if (applied) {
            return;
        }
        applied = true;

        try {
            LaunchClassLoader launchClassLoader = Launch.classLoader;
            if (launchClassLoader == null || launchClassLoader != LaunchClassLoaderIsolation.class.getClassLoader()) {
                // Not defined by LaunchClassLoader, nothing to correct
                return;
            }

            // LaunchWrapper's own classes are always excluded, so this is the parent loader
            ClassLoader parent = Launch.class.getClassLoader();
            URL parentMixin = parent == null ? null : parent.getResource(PROBE_RESOURCE);
            URL ownMixin = launchClassLoader.findResource(PROBE_RESOURCE);
            if (parentMixin == null || ownMixin == null || !parentMixin.toExternalForm().equals(ownMixin.toExternalForm())) {
                // The parent cannot see this mixin jar, delegating to it would only break things
                return;
            }

            for (String exclusion : EXCLUSIONS) {
                launchClassLoader.addClassLoaderExclusion(exclusion);
            }
        } catch (Throwable ex) {
            // A layout we don't recognize, carry on regardless
        }
    }

}
