package org.spongepowered.asm.service.mojang;

import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

public class Blackboard implements IGlobalPropertyService {

    public static final GlobalProperties.Keys TWEAKS_KEY = GlobalProperties.Keys.of("Tweaks");
    public static final GlobalProperties.Keys TWEAK_CLASSES_KEY = GlobalProperties.Keys.of("TweakClasses");

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @Override
    public <T> T getProperty(IPropertyKey key) {
        return (T) Launch.blackboard.get(key.toString());
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        Launch.blackboard.put(key.toString(), value);
    }

    @Override
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        Object value = Launch.blackboard.get(key.toString());
        return value != null ? (T) value : defaultValue;
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        return getProperty(key).toString();
    }

    private static final class Key implements IPropertyKey {

        private final String key;

        private Key(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return this.key;
        }
    }

}
