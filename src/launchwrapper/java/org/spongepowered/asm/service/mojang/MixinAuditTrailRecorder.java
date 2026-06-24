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

import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.service.IMixinAuditTrail;

/**
 * An {@link IMixinAuditTrail} that records mixin apply/post-process/generate events into a dedicated
 * {@link MixinAuditFile}. A service enables it by returning a {@link MixinAuditFile} from
 * {@link AbstractMixinServiceLaunchWrapper#createAuditLog()}.
 */
public class MixinAuditTrailRecorder implements IMixinAuditTrail {

    private final MixinAuditFile file;
    private final String name;

    /**
     * @param file the audit log to write events to
     * @param name the audit tag prefixed to each entry, e.g. {@code "CleanMix/Audit"}
     */
    public MixinAuditTrailRecorder(MixinAuditFile file, String name) {
        this.file = file;
        this.name = name;
    }

    @Override
    public void onApply(String className, String mixinName) {
        this.file.write(Level.INFO, this.name, "APPLY " + mixinName + " -> " + className, null);
    }

    @Override
    public void onPostProcess(String className) {
        this.file.write(Level.INFO, this.name, "POSTPROCESS " + className, null);
    }

    @Override
    public void onGenerate(String className, String generatorName) {
        this.file.write(Level.INFO, this.name, "GENERATE " + className + " (by " + generatorName + ")", null);
    }

}
