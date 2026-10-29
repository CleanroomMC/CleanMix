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
package org.spongepowered.asm.mixin.injection.selectors.dynamic;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.selectors.ElementNode;
import org.spongepowered.asm.mixin.injection.selectors.ISelectorContext;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelector;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelectorDynamic;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelectorDynamic.SelectorId;
import org.spongepowered.asm.mixin.injection.selectors.InvalidSelectorException;
import org.spongepowered.asm.mixin.injection.selectors.MatchResult;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.asm.MethodNodeEx;

/**
 * Selects a method that another mixin merged into the target, by the name it
 * has in that mixin. Injector handlers are renamed when they are merged, this
 * selector finds them regardless, as well as overwrites and other merged
 * methods.
 *
 * <pre>{@code @Inject(method = "@Merged(com.example.mixin.FooMixin::onTick)", at = @At("HEAD"))}</pre>
 *
 * <p>A descriptor may follow the name to pick one of several overloads, as in
 * {@code @Merged(com.example.mixin.FooMixin::onTick(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V)}.
 * Nothing is selected when the other mixin is absent, so the injector's
 * {@code require} decides whether that is an error.</p>
 *
 * <p>The name is remapped like any other target, and a method matches by the
 * name as written or as remapped. A handler keeps its name, and an overwrite
 * of an obfuscated method is found by its mapped name.</p>
 */
@SelectorId("Merged")
public final class DynamicSelectorMerged implements ITargetSelectorDynamic {

    private final String mixin;

    private final String member;

    private final MemberInfo method;

    private final MemberInfo remapped;

    private DynamicSelectorMerged(String mixin, String member, MemberInfo method, MemberInfo remapped) {
        this.mixin = mixin;
        this.member = member;
        this.method = method;
        this.remapped = remapped;
    }

    public static DynamicSelectorMerged parse(String input, ISelectorContext context) throws InvalidSelectorException {
        int separator = input.indexOf("::");
        if (separator < 1 || separator + 2 == input.length()) {
            throw new InvalidSelectorException("Expected @Merged(mixin::method) but found @Merged(" + input + ") in " + context);
        }
        String member = input.substring(separator + 2);
        return new DynamicSelectorMerged(input.substring(0, separator).replace('/', '.'), member, MemberInfo.parse(member, null),
                MemberInfo.parse(context.remap(member), null));
    }

    /**
     * Get the method as written, without the mixin, such as {@code onTick}
     *
     * @return the method
     */
    public String getMember() {
        return this.member;
    }

    @Override
    public ITargetSelector next() {
        return null;
    }

    @Override
    public ITargetSelector configure(Configure request, String... args) {
        return this;
    }

    @Override
    public ITargetSelector validate() throws InvalidSelectorException {
        return this;
    }

    @Override
    public ITargetSelector attach(ISelectorContext context) throws InvalidSelectorException {
        if (context.getMixin() != null && this.mixin.equals(context.getMixin().getClassName())) {
            throw new InvalidSelectorException(this + " in " + context + " selects a method of its own mixin");
        }
        return this;
    }

    @Override
    public int getMinMatchCount() {
        return 0;
    }

    @Override
    public int getMaxMatchCount() {
        return 1;
    }

    @Override
    public <TNode> MatchResult match(ElementNode<TNode> node) {
        MethodNode method = node.getMethod();
        if (!(method instanceof MethodNodeEx)) {
            return MatchResult.NONE;
        }
        AnnotationNode merged = Annotations.getVisible(method, MixinMerged.class);
        if (merged == null || !this.mixin.equals(Annotations.<String>getValue(merged, "mixin"))) {
            return MatchResult.NONE;
        }
        String name = ((MethodNodeEx) method).getOriginalName();
        return this.method.matches(null, name, method.desc).isExactMatch() || this.remapped.matches(null, name, method.desc).isExactMatch()
                ? MatchResult.EXACT_MATCH : MatchResult.NONE;
    }

    @Override
    public String toString() {
        return "@Merged(" + this.mixin + "::" + this.member + ")";
    }

}
