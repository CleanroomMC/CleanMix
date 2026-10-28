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
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.util.Annotations;

/**
 * A method of a mixin being edited, see {@link MixinEdit#method}
 */
public final class MethodEdit {

    private final MethodNode method;

    private boolean removed;

    MethodEdit(MethodNode method) {
        this.method = method;
    }

    /**
     * Get the method itself, to edit anything else about it
     *
     * @return the method
     */
    public MethodNode node() {
        return this.method;
    }

    /**
     * Remove the method from the mixin. Removing an injector handler or an
     * overwrite stops it from applying.
     */
    public void remove() {
        this.removed = true;
    }

    /**
     * Get the injector annotation of the method, whichever injector it is
     *
     * @return injector annotation
     */
    public AnnotationEdit injector() {
        for (Class<? extends Annotation> type : InjectionInfo.getRegisteredAnnotations()) {
            AnnotationNode injector = Annotations.getVisible(this.method, type);
            if (injector != null) {
                return AnnotationEdit.of(injector);
            }
        }
        throw new IllegalStateException(this.method.name + this.method.desc + " is not an injector");
    }

    /**
     * Get an annotation of the method, such as {@code @Shadow}
     *
     * @param type annotation type
     * @return annotation
     */
    public AnnotationEdit annotation(Class<? extends Annotation> type) {
        AnnotationNode annotation = Annotations.getVisible(this.method, type);
        if (annotation == null) {
            annotation = Annotations.getInvisible(this.method, type);
        }
        if (annotation == null) {
            throw new IllegalStateException(this.method.name + this.method.desc + " has no @" + type.getSimpleName());
        }
        return AnnotationEdit.of(annotation);
    }

    boolean isRemoved() {
        return this.removed;
    }

}
