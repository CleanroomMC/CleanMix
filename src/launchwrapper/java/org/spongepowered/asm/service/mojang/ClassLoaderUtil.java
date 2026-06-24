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
package org.spongepowered.asm.service.mojang;

import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.service.IClassTracker;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Reflects into {@link LaunchClassLoader} to back the mixin {@link IClassTracker}. Nothing is written except
 * inserting entries into the invalid-classes set (to keep mixin "classes" from being classloaded).
 * Everything else is read-only validation.
 */
final class ClassLoaderUtil implements IClassTracker {

    private static final String CACHED_CLASSES_FIELD = "cachedClasses";
    private static final String INVALID_CLASSES_FIELD = "invalidClasses";
    private static final String CLASS_LOADER_EXCEPTIONS_FIELD = "classLoaderExceptions";
    private static final String TRANSFORMER_EXCEPTIONS_FIELD = "transformerExceptions";

    private final Map<String, Class<?>> cachedClasses;
    private final Set<String> invalidClasses;
    private final Set<String> classLoaderExceptions;
    private final Set<String> transformerExceptions;

    ClassLoaderUtil(LaunchClassLoader classLoader) {
        this.cachedClasses = getField(classLoader, CACHED_CLASSES_FIELD);
        this.invalidClasses = getField(classLoader, INVALID_CLASSES_FIELD);
        this.classLoaderExceptions = getField(classLoader, CLASS_LOADER_EXCEPTIONS_FIELD);
        this.transformerExceptions = getField(classLoader, TRANSFORMER_EXCEPTIONS_FIELD);
    }

    @Override
    public boolean isClassLoaded(String name) {
        return this.cachedClasses != null && this.cachedClasses.containsKey(name);
    }

    @Override
    public String getClassRestrictions(String className) {
        String restrictions = "";
        if (this.isClassClassLoaderExcluded(className, null)) {
            restrictions = "PACKAGE_CLASSLOADER_EXCLUSION";
        }
        if (this.isClassTransformerExcluded(className, null)) {
            restrictions = (!restrictions.isEmpty() ? restrictions + "," : "") + "PACKAGE_TRANSFORMER_EXCLUSION";
        }
        return restrictions;
    }

    @Override
    public void registerInvalidClass(String name) {
        if (this.invalidClasses != null) {
            this.invalidClasses.add(name);
        }
    }

    /** Whether the name or transformedName appears in either exclusion list. */
    boolean isClassExcluded(String name, String transformedName) {
        return this.isClassClassLoaderExcluded(name, transformedName) || this.isClassTransformerExcluded(name, transformedName);
    }

    private boolean isClassClassLoaderExcluded(String name, String transformedName) {
        for (final String exception : this.getClassLoaderExceptions()) {
            if ((transformedName != null && transformedName.startsWith(exception)) || name.startsWith(exception)) {
                return true;
            }
        }
        return false;
    }

    private boolean isClassTransformerExcluded(String name, String transformedName) {
        for (final String exception : this.getTransformerExceptions()) {
            if ((transformedName != null && transformedName.startsWith(exception)) || name.startsWith(exception)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> getClassLoaderExceptions() {
        return this.classLoaderExceptions != null ? this.classLoaderExceptions : Collections.emptySet();
    }

    private Set<String> getTransformerExceptions() {
        return this.transformerExceptions != null ? this.transformerExceptions : Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(LaunchClassLoader classLoader, String fieldName) {
        try {
            Field field = LaunchClassLoader.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(classLoader);
        } catch (Exception ex) {
            return null;
        }
    }

}
