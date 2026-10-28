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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Edits and cancels mixins of other configurations, for example to fix a
 * conflict with another mod or to make its injector optional:
 *
 * <pre>{@code
 * MixinEdits.of("com.example.mixin.FooMixin")
 *         .cancel("net.minecraft.client.Minecraft")
 *         .method("onRender", m -> m.injector().set("require", 0).at(0).set("ordinal", 2))
 *         .field("cache", FieldEdit::remove)
 *         .method("onTick", MethodEdit::remove);
 * }</pre>
 *
 * <p>Edits reach every target the mixin has not been applied to yet, so make
 * them from a config plugin's {@code onLoad} or earlier. Use the
 * {@code @Merged(mixin::method)} selector to inject into a method another
 * mixin merged.</p>
 */
public final class MixinEdits {

    private static final ILogger logger = MixinService.getService().getLogger("mixin");

    private static final List<IMixinEditor> editors = new CopyOnWriteArrayList<IMixinEditor>();

    private MixinEdits() {
    }

    /**
     * Start editing a mixin at the default priority
     *
     * @param mixinClassName mixin class name, such as
     *      {@code com.example.mixin.FooMixin}
     * @return new edits of the mixin
     */
    public static MixinEdit of(String mixinClassName) {
        return MixinEdits.of(mixinClassName, IMixinConfig.DEFAULT_PRIORITY);
    }

    /**
     * Start editing a mixin
     *
     * @param mixinClassName mixin class name
     * @param priority priority of the edits, see
     *      {@link IMixinEditor#getPriority}
     * @return new edits of the mixin
     */
    public static MixinEdit of(String mixinClassName, int priority) {
        String name = mixinClassName.replace('/', '.');
        ClassInfo info = ClassInfo.fromCache(name);
        IMixinInfo mixin = info != null ? info.getMixinInfo() : null;
        if (mixin != null) {
            for (String target : mixin.getTargetClasses()) {
                if (Mixins.getMixinsForClass(target).contains(mixin)) {
                    MixinEdits.logger.warn("Mixin {} has already applied to {}, edits made to it now will not reach it", name, target);
                }
            }
        }
        MixinEdit edit = new MixinEdit(name, priority);
        MixinEdits.register(edit);
        return edit;
    }

    /**
     * Register an editor that sees every mixin
     *
     * @param editor editor to register
     */
    public static synchronized void register(IMixinEditor editor) {
        int index = MixinEdits.editors.size();
        while (index > 0 && MixinEdits.editors.get(index - 1).getPriority() > editor.getPriority()) {
            index--;
        }
        MixinEdits.editors.add(index, editor);
    }

    /**
     * Called by the transformer before a mixin loads
     *
     * @param mixinClassName mixin class name
     * @return false if the mixin is cancelled
     */
    public static boolean shouldLoad(String mixinClassName) {
        for (IMixinEditor editor : MixinEdits.editors) {
            if (!editor.shouldLoad(mixinClassName)) {
                MixinEdits.logger.info("Mixin {} was cancelled by {}", mixinClassName, editor);
                return false;
            }
        }
        return true;
    }

    /**
     * Called by the transformer before a mixin applies to a target
     *
     * @param mixinClassName mixin class name
     * @param targetClassName target class name
     * @return false if the mixin is cancelled for this target
     */
    public static boolean shouldApply(String mixinClassName, String targetClassName) {
        for (IMixinEditor editor : MixinEdits.editors) {
            if (!editor.shouldApply(mixinClassName, targetClassName)) {
                MixinEdits.logger.info("Mixin {} was cancelled for {} by {}", mixinClassName, targetClassName, editor);
                return false;
            }
        }
        return true;
    }

    /**
     * Called by the transformer for each copy of a mixin class it reads
     *
     * @param mixinClassName mixin class name
     * @param mixin copy of the mixin class
     */
    public static void edit(String mixinClassName, ClassNode mixin) {
        for (IMixinEditor editor : MixinEdits.editors) {
            editor.edit(mixinClassName, mixin);
        }
    }

}
