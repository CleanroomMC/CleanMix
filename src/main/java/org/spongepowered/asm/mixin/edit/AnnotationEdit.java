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
package org.spongepowered.asm.mixin.edit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.objectweb.asm.tree.AnnotationNode;
import org.spongepowered.asm.util.Annotations;

/**
 * An annotation being edited, from {@link MethodEdit} and {@link FieldEdit}.
 * Values are read and written as they are stored in bytecode, so classes are
 * {@link org.objectweb.asm.Type}s and arrays are lists. Arrays are converted
 * to lists when set.
 */
public final class AnnotationEdit {

    private final AnnotationNode annotation;

    private AnnotationEdit(AnnotationNode annotation) {
        this.annotation = annotation;
    }

    /**
     * Edit any annotation, for example one found by
     * {@link MixinEdit#edit(java.util.function.Consumer)}
     *
     * @param annotation annotation to edit
     * @return edit of the annotation
     */
    public static AnnotationEdit of(AnnotationNode annotation) {
        return new AnnotationEdit(annotation);
    }

    /**
     * Get the annotation itself
     *
     * @return the annotation
     */
    public AnnotationNode node() {
        return this.annotation;
    }

    /**
     * Get a value of the annotation
     *
     * @param key key of the value
     * @param <T> type of the value
     * @return the value, or null when it is not set
     */
    public <T> T get(String key) {
        return Annotations.getValue(this.annotation, key);
    }

    /**
     * Set a value of the annotation
     *
     * @param key key of the value
     * @param value new value
     * @return this
     */
    public AnnotationEdit set(String key, Object value) {
        Annotations.setValue(this.annotation, key, value instanceof Object[] ? new ArrayList<Object>(Arrays.asList((Object[]) value)) : value);
        return this;
    }

    /**
     * Get one of the {@code @At} annotations of an injector
     *
     * @param index index in {@code at}, 0 for injectors that take one
     * @return the {@code @At} annotation
     */
    public AnnotationEdit at(int index) {
        return this.nested("at", index);
    }

    /**
     * Get an annotation nested in this one
     *
     * @param key key of the nested annotation or annotation array
     * @param index index in the array, 0 for a single annotation
     * @return the nested annotation
     */
    public AnnotationEdit nested(String key, int index) {
        Object value = this.get(key);
        if (value instanceof List) {
            List<?> values = (List<?>) value;
            value = index < values.size() ? values.get(index) : null;
        } else if (index != 0) {
            value = null;
        }
        if (!(value instanceof AnnotationNode)) {
            throw new IllegalArgumentException("@" + Annotations.getSimpleName(this.annotation) + " has no annotation at " + key + "[" + index + "]");
        }
        return AnnotationEdit.of((AnnotationNode) value);
    }

}
