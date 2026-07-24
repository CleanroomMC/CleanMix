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

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigSource;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.selectors.ISelectorContext;
import org.spongepowered.asm.mixin.refmap.IMixinContext;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.service.clean.ICleanMixinService;
import org.spongepowered.asm.util.asm.MethodNodeEx;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public final class CleanroomUtil {

    /** Annotation-processor output containing compatibility by mixin and member. */
    public static final String COMPATIBILITY_RESOURCE = "cleanmix_version_compatibility.json";

    /**
     * Config decoration containing the owning mod id.
     */
    public static final String KEY_MOD_ID = "cleanmix-id";


    // CleanMix compatibility boundaries, (major * 1000 + minor) * 1000 + patch

    /** CleanMix compatibility version 0.1.0, the baseline. */
    public static final int COMPATIBILITY_0_1_0 = 1000;

    /** CleanMix compatibility version 0.6.0. */
    public static final int COMPATIBILITY_0_6_0 = 6000;

    /** Fallback for mixins built without CleanMix compatibility metadata. */
    public static final int COMPATIBILITY_OLD = COMPATIBILITY_0_1_0;

    /** Latest CleanMix compatibility version. */
    public static final int COMPATIBILITY_LATEST = COMPATIBILITY_0_6_0;

    private static final Map<String, Integer> EMPTY_COMPATIBILITIES = Collections.<String, Integer>emptyMap();
    private static final Map<String, Map<String, Integer>> COMPATIBILITIES_CACHE = new HashMap<String, Map<String, Integer>>();

    /**
     * Convert a CleanMix semantic version to its integer compatibility value.
     *
     * @param version version in major.minor.patch form
     * @return encoded compatibility version
     */
    public static int parseCompatibility(String version) {
        if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+")) {
            throw new IllegalArgumentException("Invalid CleanMix compatibility version '" + version + "', expected major.minor.patch");
        }

        String[] parts = version.split("\\.");
        int major = parseCompatibilityPart(version, parts[0]);
        int minor = parseCompatibilityPart(version, parts[1]);
        int patch = parseCompatibilityPart(version, parts[2]);
        return (major * 1000 + minor) * 1000 + patch;
    }

    private static int parseCompatibilityPart(String version, String part) {
        int value;
        try {
            value = Integer.parseInt(part);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid CleanMix compatibility version '" + version + "'", ex);
        }
        if (value > 999) {
            throw new IllegalArgumentException("Invalid CleanMix compatibility version '" + version + "', each part must be at most 999");
        }
        return value;
    }

    /**
     * Convert an encoded compatibility value to major.minor.patch form.
     *
     * @param compatibility encoded compatibility version
     * @return semantic version string
     */
    public static String getVersionString(int compatibility) {
        if (compatibility < 0) {
            throw new IllegalArgumentException("Compatibility version cannot be negative");
        }
        return String.format("%d.%d.%d", compatibility / 1000000, compatibility / 1000 % 1000, compatibility % 1000);
    }

    public static String getMethodCompatibilityKey(String className, String name, String desc) {
        return className + "::" + name + desc;
    }

    public static String getFieldCompatibilityKey(String className, String name, String desc) {
        return className + "::" + name + ":" + desc;
    }

    /**
     * Decorate a config with its owning mod.
     *
     * @param config mixin config to decorate
     * @param modId owning mod id
     */
    public static void decorate(IMixinConfig config, String modId) {
        if (!config.hasDecoration(KEY_MOD_ID) && modId != null) {
            config.decorate(KEY_MOD_ID, modId);
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
        return getCompatibility(context.getMixin().getMixin(), context.getMethod());
    }

    public static int getCompatibility(IMixinContext context) {
        return getCompatibility(context.getMixin(), null);
    }

    public static int getCompatibility(IMixinContext context, MethodNode method) {
        return getCompatibility(context.getMixin(), method);
    }

    public static int getCompatibility(IMixinContext context, FieldNode field) {
        return getCompatibility(context.getMixin(), field);
    }

    public static int getCompatibility(IMixinInfo mixin) {
        return getCompatibility(mixin, null);
    }

    private static int getCompatibility(IMixinInfo mixin, Object member) {
        String className = mixin.getClassName();
        Map<String, Integer> compatibilities = getCompatibilities(mixin);
        Integer compatibility = null;
        if (member instanceof MethodNode) {
            MethodNode method = (MethodNode) member;
            compatibility = compatibilities.get(getMethodCompatibilityKey(className, MethodNodeEx.getName(method), method.desc));
        } else if (member instanceof FieldNode) {
            FieldNode field = (FieldNode) member;
            compatibility = compatibilities.get(getFieldCompatibilityKey(className, field.name, field.desc));
        }
        if (compatibility == null) {
            compatibility = compatibilities.get(className);
        }
        return compatibility != null ? compatibility : COMPATIBILITY_OLD;
    }

    private static Map<String, Integer> getCompatibilities(IMixinInfo mixin) {
        URL metadataResource = findCompatibilityResource(mixin.getConfig().getSource());
        if (metadataResource == null) {
            metadataResource = findCompatibilityResource(mixin.getClassName());
        }
        if (metadataResource == null) {
            return EMPTY_COMPATIBILITIES;
        }
        String cacheKey = metadataResource.toExternalForm();
        Map<String, Integer> cached = COMPATIBILITIES_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Map<String, Integer> loaded = loadCompatibilities(metadataResource);
        COMPATIBILITIES_CACHE.put(cacheKey, loaded);
        return loaded;
    }

    private static URL findCompatibilityResource(IMixinConfigSource source) {
        if (!(source instanceof ContainerHandleURI)) {
            return null;
        }
        try {
            URI sourceUri = ((ContainerHandleURI) source).getURI();
            if (sourceUri == null) {
                return null;
            }
            if ("jar".equals(sourceUri.getScheme())) {
                return new URL(sourceUri.toURL(), COMPATIBILITY_RESOURCE);
            }
            if ("file".equals(sourceUri.getScheme())) {
                Path sourcePath = Paths.get(sourceUri);
                if (Files.isDirectory(sourcePath)) {
                    return sourcePath.resolve(COMPATIBILITY_RESOURCE).toUri().toURL();
                }
                return new URL("jar:" + sourceUri.toURL().toExternalForm() + "!/" + COMPATIBILITY_RESOURCE);
            }
        } catch (Exception ex) {
        }
        return null;
    }

    private static URL findCompatibilityResource(String className) {
        String classResourceName = className.replace('.', '/') + ".class";
        try {
            IMixinService service = MixinService.getService();
            URL classResource = service instanceof ICleanMixinService
                    ? ((ICleanMixinService) service).getResource(classResourceName)
                    : Thread.currentThread().getContextClassLoader().getResource(classResourceName);
            if (classResource == null) {
                return null;
            }
            if ("jar".equals(classResource.getProtocol())) {
                URL jar = ((JarURLConnection) classResource.openConnection()).getJarFileURL();
                return new URL("jar:" + jar.toExternalForm() + "!/" + COMPATIBILITY_RESOURCE);
            }
            if ("file".equals(classResource.getProtocol())) {
                Path root = Paths.get(classResource.toURI());
                for (String ignored : classResourceName.split("/")) {
                    root = root.getParent();
                }
                Path metadata = root.resolve(COMPATIBILITY_RESOURCE);
                return Files.isRegularFile(metadata) ? metadata.toUri().toURL() : null;
            }
            String external = classResource.toExternalForm();
            int classPathPos = external.lastIndexOf(classResourceName);
            return classPathPos >= 0 ? new URL(external.substring(0, classPathPos) + COMPATIBILITY_RESOURCE) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static Map<String, Integer> loadCompatibilities(URL resource) {
        Reader reader = null;
        try {
            InputStream input = resource.openStream();
            reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            Map<String, String> versions = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() { }.getType());
            if (versions == null || versions.isEmpty()) {
                return EMPTY_COMPATIBILITIES;
            }
            Map<String, Integer> compatibilities = new HashMap<String, Integer>();
            for (Map.Entry<String, String> entry : versions.entrySet()) {
                try {
                    compatibilities.put(entry.getKey(), parseCompatibility(entry.getValue()));
                } catch (IllegalArgumentException ex) {
                    MixinService.getService().getLogger("CleanMix").warn("Ignoring invalid compatibility '{}' for '{}' in {}",
                            entry.getValue(), entry.getKey(), resource);
                }
            }
            return compatibilities.isEmpty() ? EMPTY_COMPATIBILITIES : compatibilities;
        } catch (FileNotFoundException ex) {
            return EMPTY_COMPATIBILITIES;
        } catch (Exception ex) {
            MixinService.getService().getLogger("CleanMix").warn("Unable to read CleanMix compatibility metadata from {}", resource, ex);
            return EMPTY_COMPATIBILITIES;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ex) {
                    // Ignore close failure
                }
            }
        }
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
