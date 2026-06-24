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

import com.google.common.collect.Sets;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.service.ILegacyClassTransformer;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinService;

import java.util.*;

/**
 * Generic LaunchWrapper {@link ITransformerProvider}. The set of re-entrant transformers to exclude from the
 * delegation chain is supplied by the owning service (loader-specific) rather than hard-coded here, keeping
 * this class loader-agnostic.
 */
final class TransformerProvider implements ITransformerProvider {

    /**
     * Forge's re-entrant transformers, which must never process meta class data when bytecode is fetched for a
     * mixin target, else resolving the target re-enters their pipeline. Other re-entrants are detected and
     * excluded automatically at runtime via the re-entrance lock.
     */
    private static final Set<String> REENTRANT_EXCLUSIONS = Sets.newHashSet(
            "net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer",
            "cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer",
            "net.minecraftforge.fml.common.asm.transformers.TerminalTransformer",
            "cpw.mods.fml.common.asm.transformers.TerminalTransformer"
    );

    private final Set<String> excludeTransformers;
    private List<ILegacyClassTransformer> delegatedTransformers;

    TransformerProvider(Set<String> initialExclusions) {
        this.excludeTransformers = new HashSet<>(initialExclusions);
    }

    TransformerProvider() {
        this(REENTRANT_EXCLUSIONS);
    }

    void refreshDelegatedTransformers() {
        this.delegatedTransformers = null;
    }

    @Override
    public Collection<ITransformer> getTransformers() {
        List<IClassTransformer> transformers = Launch.classLoader.getTransformers();
        List<ITransformer> result = new ArrayList<>(transformers.size());
        for (IClassTransformer transformer : transformers) {
            if (transformer instanceof ITransformer) {
                result.add((ITransformer) transformer);
            } else {
                result.add(new LegacyTransformerHandle(transformer));
            }
        }
        return result;
    }

    @Override
    public List<ITransformer> getDelegatedTransformers() {
        return Collections.unmodifiableList(this.getDelegatedLegacyTransformers());
    }

    @Override
    public void addTransformerExclusion(String name) {
        this.excludeTransformers.add(name);
        this.delegatedTransformers = null;
    }

    private List<ILegacyClassTransformer> getDelegatedLegacyTransformers() {
        if (this.delegatedTransformers == null) {
            this.buildTransformerDelegationList();
        }
        return this.delegatedTransformers;
    }

    /**
     * Builds the transformer list applied to loaded mixin bytecode. Generating it requires inspecting each
     * transformer by name (to cope with FML-style wrapping), so it is built once per environment and cached.
     */
    private void buildTransformerDelegationList() {
        ILogger logger = MixinService.getService().getLogger("TransformerProvider");
        logger.debug("Rebuilding transformer delegation list:");
        this.delegatedTransformers = new ArrayList<>();
        for (ITransformer transformer : this.getTransformers()) {
            if (!(transformer instanceof ILegacyClassTransformer)) {
                continue;
            }
            ILegacyClassTransformer legacyTransformer = (ILegacyClassTransformer) transformer;
            String transformerName = legacyTransformer.getName();
            boolean include = true;
            for (String excludeClass : this.excludeTransformers) {
                if (transformerName.contains(excludeClass)) {
                    include = false;
                    break;
                }
            }
            if (include && !legacyTransformer.isDelegationExcluded()) {
                logger.debug("  Adding:    {}", transformerName);
                this.delegatedTransformers.add(legacyTransformer);
            } else {
                logger.debug("  Excluding: {}", transformerName);
            }
        }
        logger.debug("Transformer delegation list created with {} entries", this.delegatedTransformers.size());
    }

}
