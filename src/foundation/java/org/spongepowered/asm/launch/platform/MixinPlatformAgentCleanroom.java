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

import java.util.Collection;
import java.util.List;

import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IRemapper;
import org.spongepowered.asm.service.outlands.MixinServiceFoundation;
import org.spongepowered.asm.util.Constants;

import net.minecraft.launchwrapper.Launch;

/**
 * Platform agent for use under FML and LaunchWrapper.
 * 
 * <p>When FML is present we scan containers for the manifest entries which are
 * inhibited by the tweaker, in particular the <tt>FMLCorePlugin</tt> and
 * <tt>FMLCorePluginContainsFMLMod</tt> entries. This is required because FML
 * performs no further processing of containers if they contain a tweaker!</p>
 */
public class MixinPlatformAgentCleanroom extends MixinPlatformAgentAbstract implements IMixinPlatformServiceAgent {


    @Override
    public AcceptResult accept(MixinPlatformManager manager, IContainerHandle handle) {
        
        if (!(handle instanceof ContainerHandleURI) || super.accept(manager, handle) != AcceptResult.ACCEPTED) {
            return AcceptResult.REJECTED;
        }

        return AcceptResult.ACCEPTED;
    }

    @Override
    public String getPhaseProvider() {
        return MixinPlatformAgentCleanroom.class.getName() + "$PhaseProvider";
    }


    /* (non-Javadoc)
     * @see org.spongepowered.asm.launch.platform.IMixinPlatformServiceAgent
     *      #init()
     */
    @Override
    public void init() {
          this.injectRemapper();
    }

    private void injectRemapper() {
        try {
            MixinPlatformAgentAbstract.logger.debug("Creating FML remapper adapter: {}", RemapperCleanroom.class.getName());
            IRemapper remapper = new RemapperCleanroom();
            MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
        } catch (Exception ex) {
            MixinPlatformAgentAbstract.logger.debug("Failed instancing FML remapper adapter, things will probably go horribly for notch-obf'd mods!");
        }
    }

    /* (non-Javadoc)
     * @see org.spongepowered.asm.launch.platform.MixinPlatformAgentAbstract
     *      #getSideName()
     */
    @Override
    public String getSideName() {
        String side = (String) Launch.blackboard.get("fml.side");
        if (side.equals("server")) {
            return Constants.SIDE_SERVER;
        } else if (side.equals("client")) {
            return Constants.SIDE_CLIENT;
        }

        return null;
    }

    /* (non-Javadoc)
     * @see org.spongepowered.asm.launch.platform.IMixinPlatformServiceAgent
     *      #getMixinContainers()
     */
    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return null;
    }

}
