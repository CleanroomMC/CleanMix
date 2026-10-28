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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.service.MixinService;

/**
 * Edits of one mixin, from {@link MixinEdits#of}. Each call adds to the
 * edits and returns this, so they chain.
 */
public final class MixinEdit implements IMixinEditor {

    private final String mixin;

    private final int priority;

    private final Set<String> cancelledTargets = new HashSet<String>();

    private final List<Consumer<ClassNode>> edits = new ArrayList<Consumer<ClassNode>>();

    private boolean cancelled;

    MixinEdit(String mixin, int priority) {
        this.mixin = mixin;
        this.priority = priority;
    }

    /**
     * Cancel the mixin. It does not load, or stops applying when it already
     * has.
     *
     * @return this
     */
    public MixinEdit cancel() {
        this.cancelled = true;
        return this;
    }

    /**
     * Cancel the mixin for some of its targets
     *
     * @param targetClassNames target class names, such as
     *      {@code net.minecraft.client.Minecraft}
     * @return this
     */
    public MixinEdit cancel(String... targetClassNames) {
        for (String targetClassName : targetClassNames) {
            this.cancelledTargets.add(targetClassName.replace('/', '.'));
        }
        return this;
    }

    /**
     * Edit methods of the mixin. Without a descriptor every overload of the
     * name is edited.
     *
     * @param method method name as written in the mixin, optionally followed
     *      by its descriptor, such as {@code onTick(I)V}
     * @param edit edit to make to each matching method
     * @return this
     */
    public MixinEdit method(String method, Consumer<MethodEdit> edit) {
        int descStart = method.indexOf('(');
        String name = descStart < 0 ? method : method.substring(0, descStart);
        String desc = descStart < 0 ? null : method.substring(descStart);
        return this.edit(classNode -> {
            boolean found = false;
            for (Iterator<MethodNode> iter = classNode.methods.iterator(); iter.hasNext();) {
                MethodNode node = iter.next();
                if (node.name.equals(name) && (desc == null || node.desc.equals(desc))) {
                    found = true;
                    MethodEdit methodEdit = new MethodEdit(node);
                    edit.accept(methodEdit);
                    if (methodEdit.isRemoved()) {
                        iter.remove();
                    }
                }
            }
            this.warnIfMissing(found, method);
        });
    }

    /**
     * Edit a field of the mixin
     *
     * @param field field name, optionally followed by its descriptor, such as
     *      {@code count:I}
     * @param edit edit to make to the field
     * @return this
     */
    public MixinEdit field(String field, Consumer<FieldEdit> edit) {
        int descStart = field.indexOf(':');
        String name = descStart < 0 ? field : field.substring(0, descStart);
        String desc = descStart < 0 ? null : field.substring(descStart + 1);
        return this.edit(classNode -> {
            boolean found = false;
            for (Iterator<FieldNode> iter = classNode.fields.iterator(); iter.hasNext();) {
                FieldNode node = iter.next();
                if (node.name.equals(name) && (desc == null || node.desc.equals(desc))) {
                    found = true;
                    FieldEdit fieldEdit = new FieldEdit(node);
                    edit.accept(fieldEdit);
                    if (fieldEdit.isRemoved()) {
                        iter.remove();
                    }
                }
            }
            this.warnIfMissing(found, field);
        });
    }

    /**
     * Edit the mixin class directly
     *
     * @param edit edit to make
     * @return this
     */
    public MixinEdit edit(Consumer<ClassNode> edit) {
        this.edits.add(edit);
        return this;
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public boolean shouldLoad(String mixinClassName) {
        return !this.cancelled || !this.mixin.equals(mixinClassName);
    }

    @Override
    public boolean shouldApply(String mixinClassName, String targetClassName) {
        return !this.mixin.equals(mixinClassName) || !this.cancelled && !this.cancelledTargets.contains(targetClassName);
    }

    @Override
    public void edit(String mixinClassName, ClassNode mixin) {
        if (this.mixin.equals(mixinClassName)) {
            for (Consumer<ClassNode> edit : this.edits) {
                edit.accept(mixin);
            }
        }
    }

    @Override
    public String toString() {
        return String.format("MixinEdits.of(%s, %d)", this.mixin, this.priority);
    }

    private void warnIfMissing(boolean found, String member) {
        if (!found) {
            MixinService.getService().getLogger("mixin").warn("Mixin {} has no member {} to edit", this.mixin, member);
        }
    }

}
