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

import java.lang.annotation.Annotation;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldNode;
import org.spongepowered.asm.util.Annotations;

/**
 * A field of a mixin being edited, see {@link MixinEdit#field}
 */
public final class FieldEdit {

    private final FieldNode field;

    private boolean removed;

    FieldEdit(FieldNode field) {
        this.field = field;
    }

    /**
     * Get the field itself, to edit anything else about it
     *
     * @return the field
     */
    public FieldNode node() {
        return this.field;
    }

    /**
     * Remove the field from the mixin
     */
    public void remove() {
        this.removed = true;
    }

    /**
     * Get an annotation of the field, such as {@code @Shadow}
     *
     * @param type annotation type
     * @return annotation
     */
    public AnnotationEdit annotation(Class<? extends Annotation> type) {
        AnnotationNode annotation = Annotations.getVisible(this.field, type);
        if (annotation == null) {
            annotation = Annotations.getInvisible(this.field, type);
        }
        if (annotation == null) {
            throw new IllegalStateException(this.field.name + ":" + this.field.desc + " has no @" + type.getSimpleName());
        }
        return AnnotationEdit.of(annotation);
    }

    boolean isRemoved() {
        return this.removed;
    }

}
