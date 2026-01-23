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
package org.spongepowered.asm.launch.platform;

import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.util.Constants.ManifestAttributes;

/**
 * Default platform agent, handles the mixin manifest keys such as
 * <tt>MixinConfigs</tt> and <tt>MixinConnector</tt>.
 * CHANGED BY CLEANROOM:
 * Removed deprecated compatibility level, token providers manifest key support
 * Allow default agent to reject if no relevant manifest attributes are present
 */
public class MixinPlatformAgentDefault extends MixinPlatformAgentAbstract {

    private String mixinConfigs, connectorClass;

    @Override
    public AcceptResult accept(MixinPlatformManager manager, IContainerHandle handle) {
        this.mixinConfigs = this.handle.getAttribute(ManifestAttributes.MIXINCONFIGS);
        this.connectorClass = this.handle.getAttribute(ManifestAttributes.MIXINCONNECTOR);
        return this.mixinConfigs == null && this.connectorClass == null ? AcceptResult.REJECTED : super.accept(manager, handle);
    }

    @Override
    public void prepare() {
        if (this.mixinConfigs != null) {
            for (String config : this.mixinConfigs.split(",")) {
                this.manager.addConfig(config.trim(), this.handle);
            }
        }
        if (this.connectorClass != null) {
            this.manager.addConnector(this.connectorClass.trim());
        }
    }

}
