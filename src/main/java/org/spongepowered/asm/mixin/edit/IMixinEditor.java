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

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;

/**
 * Sees every mixin, register one with {@link MixinEdits#register}. Use
 * {@link MixinEdits#of} instead when the mixins to change are known by name.
 */
public interface IMixinEditor {

    /**
     * Editors run from the lowest priority to the highest, like mixins
     * apply, so a higher priority editor sees and overrides the changes of
     * lower ones.
     *
     * @return priority of this editor
     */
    default int getPriority() {
        return IMixinConfig.DEFAULT_PRIORITY;
    }

    /**
     * Decides whether a mixin loads at all. A mixin that does not load is
     * never read, so this also drops mixins that would fail to load.
     *
     * @param mixinClassName mixin class name, such as
     *      {@code com.example.mixin.FooMixin}
     * @return false to cancel the mixin
     */
    default boolean shouldLoad(String mixinClassName) {
        return true;
    }

    /**
     * Decides whether a mixin applies to one of its targets. Runs after the
     * config plugin of the mixin has accepted the target.
     *
     * @param mixinClassName mixin class name
     * @param targetClassName target class name, such as
     *      {@code net.minecraft.client.Minecraft}
     * @return false to skip this target
     */
    default boolean shouldApply(String mixinClassName, String targetClassName) {
        return true;
    }

    /**
     * Changes a copy of a mixin class. Mixin reads a copy to validate the
     * mixin when it loads, and a fresh one each time the mixin applies to a
     * target, so this runs once for each copy. That also means an edit
     * registered by a later config plugin still reaches targets which have
     * not loaded yet.
     *
     * <p>Every part of the class can change. The priority of the mixin is read
     * before any edit, and its targets are read from the copy made when it
     * loads.</p>
     *
     * @param mixinClassName mixin class name
     * @param mixin copy of the mixin class
     */
    default void edit(String mixinClassName, ClassNode mixin) {
    }

}
