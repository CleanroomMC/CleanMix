package org.spongepowered.asm.obfuscation.mapping.remap;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IRemapper;
import org.spongepowered.asm.service.MixinService;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Srg2McpRemapper implements IRemapper {

    /**
     * This is the default GradleStart-injected system property which tells us
     * the location of the srg-to-mcp mappings
     */
    private static final String DEFAULT_RESOURCE_PATH_PROPERTY = "net.minecraftforge.gradle.GradleStart.srg.srg-mcp";
    /**
     * The default environment name to map <em>from</em>
     */
    private static final String DEFAULT_MAPPING_ENV = "searge";
    /**
     * Loaded srgs, stored as a mapping of filename to mappings. Global cache so
     * that we only need to load each mapping file once.
     */
    private static final Map<String, Map<String, String>> srgs = new HashMap<>();
    /**
     * The loaded mappings, retrieved from {@link #srgs} by filename
     */
    private final Map<String, String> mappings;
    /**
     * Cache of transformed descriptor mappings.
     */
    private final Map<String, String> descCache = new HashMap<>();
    private static final ILogger logger = MixinService.getService().getLogger("Srg2McpRemapper");

    public Srg2McpRemapper(MixinEnvironment env) {
        String resource = getResource(env);
        this.mappings = loadSrgs(resource);
        logger.info("Initialized. Loaded {} mappings from {}", this.mappings.size(), resource);
    }

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        return this.mappings.getOrDefault(name, name);
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        return this.mappings.getOrDefault(name, name);
    }

    @Override
    public String map(String typeName) {
        return typeName;
    }

    @Override
    public String unmap(String typeName) {
        return typeName;
    }

    @Override
    public String mapDesc(String desc) {
        String remapped = this.descCache.get(desc);
        if (remapped == null) {
            remapped = desc;
            for (Map.Entry<String, String> entry : this.mappings.entrySet()) {
                remapped = remapped.replace(entry.getKey(), entry.getValue());
            }
            this.descCache.put(desc, remapped);
        }
        return remapped;
    }

    @Override
    public String unmapDesc(String desc) {
        throw new UnsupportedOperationException();
    }

    private static Map<String, String> loadSrgs(String fileName) {
        if (srgs.containsKey(fileName)) {
            return srgs.get(fileName);
        }

        final Map<String, String> map = new HashMap<>();
        srgs.put(fileName, map);

        File file = new File(fileName);
        if (!file.isFile()) {
            return map;
        }

        try {
            Files.readLines(file, Charsets.UTF_8, new LineProcessor<Object>() {

                @Override
                public Object getResult() {
                    return null;
                }

                @Override
                public boolean processLine(String line) throws IOException {
                    if (Strings.isNullOrEmpty(line) || line.startsWith("#")) {
                        return true;
                    }
                    int fromPos = 0, toPos = 0;
                    if ((toPos = line.startsWith("MD: ") ? 2 : line.startsWith("FD: ") ? 1 : 0) > 0) {
                        String[] entries = line.substring(4).split(" ", 4);
                        map.put(
                                entries[fromPos].substring(entries[fromPos].lastIndexOf('/') + 1),
                                entries[toPos].substring(entries[toPos].lastIndexOf('/') + 1)
                        );
                    }
                    return true;
                }
            });
        } catch (IOException ex) {
            logger.warn("Could not read input SRG file: {}", fileName);
            logger.catching(ex);
        }

        return map;
    }

    private static String getResource(MixinEnvironment env) {
        String resource = env.getOptionValue(MixinEnvironment.Option.REFMAP_REMAP_RESOURCE);
        return Strings.isNullOrEmpty(resource) ? System.getProperty(DEFAULT_RESOURCE_PATH_PROPERTY) : resource;
    }

    private static String getMappingEnv(MixinEnvironment env) {
        String resource = env.getOptionValue(MixinEnvironment.Option.REFMAP_REMAP_SOURCE_ENV);
        return Strings.isNullOrEmpty(resource) ? DEFAULT_MAPPING_ENV : resource;
    }

}
