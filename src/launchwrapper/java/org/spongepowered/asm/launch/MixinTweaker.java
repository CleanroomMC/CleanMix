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

import java.io.File;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.launch.platform.CommandLineOptions;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;

/**
 * TweakClass for running mixins in production. Being a tweaker ensures that we
 * get injected into the AppClassLoader but does mean that we will need to
 * inject the FML coremod by hand if running under FML.
 */
public class MixinTweaker implements ITweaker {
    private static final Pattern fileNameFilter = Pattern.compile("[^a-zA-Z0-9]*");
    private static final boolean isCleanroom = IClassTransformer.class.getClassLoader() instanceof LaunchClassLoader;

    /**
     * Hello world
     */
    public MixinTweaker() {
        if (!isCleanroom) {
            MixinBootstrap.start();
        }
    }

    /* (non-Javadoc)
     * @see net.minecraft.launchwrapper.ITweaker#acceptOptions(java.util.List,
     *      java.io.File, java.io.File, java.lang.String)
     */
    @Override
    public final void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        if (!isCleanroom) {
            MixinBootstrap.doInit(CommandLineOptions.ofArgs(args));
        }
    }

    /* (non-Javadoc)
     * @see net.minecraft.launchwrapper.ITweaker#injectIntoClassLoader(
     *      net.minecraft.launchwrapper.LaunchClassLoader)
     */
    @Override
    @SuppressWarnings("unchecked")
    public final void injectIntoClassLoader(LaunchClassLoader classLoader) {
        if (!isCleanroom) {
            MixinBootstrap.inject();
        }
        if (Launch.blackboard.containsKey("MixinConfigs"))
            Mixins.addConfigurations(((Set<String>)Launch.blackboard.get("MixinConfigs")).toArray(new String[0]));
        Config.getAllConfigs().forEach((s, config) -> {
            URL url = Launch.classLoader.getResource(s);
            if (url != null) {
                String filename;
                if (url.toString().startsWith("jar")) {
                    try {
                        JarURLConnection connection = (JarURLConnection) url.openConnection();
                        URI jarLocation = connection.getJarFileURL().toURI();
                        filename = new File(jarLocation).getName();
                        filename = filename.substring(0, filename.length() - 4);

                        config.decorate(FabricUtil.KEY_MOD_ID, fileNameFilter.matcher(filename).replaceAll("_"));
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    /* (non-Javadoc)
     * @see net.minecraft.launchwrapper.ITweaker#getLaunchTarget()
     */
    @Override
    public String getLaunchTarget() {
        return MixinBootstrap.getPlatform().getLaunchTarget();
    }

    /* (non-Javadoc)
     * @see net.minecraft.launchwrapper.ITweaker#getLaunchArguments()
     */
    @Override
    public String[] getLaunchArguments() {
        return new String[]{};
    }
    
}
