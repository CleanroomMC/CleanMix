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
package org.spongepowered.asm.mixin.transformer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import org.spongepowered.asm.mixin.transformer.MixinConfig.IListener;
import org.spongepowered.asm.util.Bytecode;

public enum MixinInheritanceTracker implements IListener {
    INSTANCE;

    private static final String OBJECT = "java/lang/Object";

    @Override
    public void onPrepare(MixinInfo mixin) {
    }

    @Override
    public void onInit(MixinInfo mixin) {
        this.register(mixin);
    }

    /**
     * Register the supplied mixin as a child of every mixin supertype it
     * inherits from, so that {@link #findOverrides} can find the methods it
     * overrides in those supertypes.
     *
     * <p>This must happen before <em>any</em> target class is transformed.
     *
     * <p>Validation (basically {@link #onInit}) is lazy and only runs when one
     * of the mixin's own targets is transformed, which for the usual
     * parent-child-mixin pattern is always <em>after</em> the parent
     * mixin's target; being the supertype of the child's target that has already
     * been transformed and injected into. Consumers would see overrides during the
     * injection and would see no children at all.
     *
     * <p>Registration only consults mixin metadata and never resolves a target class
     * so it is safe to do it eagerly during config preparation.
     *
     * <p>Calls for an already registered mixin are ignored.
     *
     * @param mixin mixin to register
     */
    synchronized void register(MixinInfo mixin) {
        if (!this.registered.add(mixin)) {
            return;
        }
        this.link(mixin);
    }

    /**
     * Re-link mixins whose supertype walk previously ran into a class which was
     * not known to be a mixin at the time. This only does something once
     * {@link ClassInfo} reports that it has replaced at least one placeholder.
     * Which happens when the parent mixin's own config is prepared
     * after the child mixin was registered.
     */
    synchronized void retryPending() {
        if (this.pending.isEmpty() || ClassInfo.getMixinUpgrades() == this.lastMixinUpgrades) {
            return;
        }
        this.lastMixinUpgrades = ClassInfo.getMixinUpgrades();
        List<MixinInfo> retry = new ArrayList<MixinInfo>(this.pending);
        this.pending.clear();
        for (MixinInfo mixin : retry) {
            this.unlink(mixin);
            this.link(mixin);
        }
    }

    /**
     * Remove a previously registered mixin.
     * Used when a mixin is discarded after it has been registered.
     *
     * @param mixin mixin to unregister
     */
    synchronized void unregister(MixinInfo mixin) {
        if (!this.registered.remove(mixin)) {
            return;
        }
        this.pending.remove(mixin);
        this.unlink(mixin);
    }

    private void link(MixinInfo mixin) {
        ClassInfo mixinInfo = mixin.getClassInfo();
        assert mixinInfo.isMixin(); //  The mixin should certainly be a mixin

        ClassInfo superType = mixinInfo.getSuperClass();
        for (; superType != null && superType.isMixin(); superType = superType.getSuperClass()) {
            List<MixinInfo> children = parentMixins.get(superType.getName());
            if (children == null) {
                parentMixins.put(superType.getName(), children = new CopyOnWriteArrayList<MixinInfo>());
            }
            children.add(mixin);
        }
        // The walk stopped at an ancestor which isn't a mixin
        if (superType != null && !MixinInheritanceTracker.OBJECT.equals(superType.getName())) {
            this.pending.add(mixin);
        }
    }

    private void unlink(MixinInfo mixin) {
        for (Iterator<List<MixinInfo>> iter = parentMixins.values().iterator(); iter.hasNext();) {
            List<MixinInfo> children = iter.next();
            children.remove(mixin);
            if (children.isEmpty()) {
                iter.remove();
            }
        }
    }

    public List<MethodNode> findOverrides(ClassInfo owner, String name, String desc) {
        return findOverrides(owner.getName(), name, desc);
    }

    public List<MethodNode> findOverrides(String owner, String name, String desc) {
        List<MixinInfo> children = parentMixins.get(owner);
        if (children == null) {
            return Collections.emptyList();
        }

        List<MethodNode> out = new ArrayList<MethodNode>(children.size());

        for (MixinInfo child : children) {
            ClassNode node = child.getClassNode(ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            MethodNode method = Bytecode.findMethod(node, name, desc);
            if (method == null || Bytecode.isStatic(method)) {
                continue;
            }

            switch (Bytecode.getVisibility(method)) {
            case PRIVATE:
                break;

            case PACKAGE:
                int ownerSplit = owner.lastIndexOf('/');
                int childSplit = node.name.lastIndexOf('/');
                //There is a reasonable chance mixins are in the same package, so it is viable that a package private method is overridden
                if (ownerSplit != childSplit || (ownerSplit > 0 && !owner.regionMatches(0, node.name, 0, ownerSplit + 1))) {
                    break;
                }

                out.add(method);
                break;
            default:
                out.add(method);
                break;
            }
        }

        return out.isEmpty() ? Collections.<MethodNode>emptyList() : out;
    }

    /**
     * Written during config preparation and read while injecting on whichever thread happens to be
     * transforming a target, so both levels have to tolerate concurrent iteration
     */
    private final Map<String, List<MixinInfo>> parentMixins = new ConcurrentHashMap<String, List<MixinInfo>>();

    private final Set<MixinInfo> registered = Collections.newSetFromMap(new IdentityHashMap<MixinInfo, Boolean>());

    /**
     * Mixins whose supertype walk ran into a class which was not known to be a mixin.
     *
     * @see #retryPending
     */
    private final Set<MixinInfo> pending = Collections.newSetFromMap(new IdentityHashMap<MixinInfo, Boolean>());

    private int lastMixinUpgrades = ClassInfo.getMixinUpgrades();
}