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
package org.spongepowered.tools.agent;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.logging.Level;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.throwables.MixinApplyError;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.IHotSwap;
import org.spongepowered.asm.mixin.transformer.throwables.MixinReloadException;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.service.ServiceNotAvailableError;
import org.spongepowered.asm.transformers.MixinClassReader;
import org.spongepowered.asm.util.asm.ASM;

/**
 * An agent that re-transforms a mixin's target classes if the mixin has been
 * redefined. Basically this agent enables hot-swapping of mixins.
 */
public class MixinAgent implements IHotSwap {

    /**
     * Class file transformer that re-transforms mixin's target classes if the
     * mixin has been redefined.
     */
    class Transformer implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain domain, byte[] classfileBuffer)
                throws IllegalClassFormatException {
            if (classBeingRedefined == null) {
                return null;
            }
            
            byte[] mixinBytecode = MixinAgent.classLoader.getFakeMixinBytecode(classBeingRedefined);
            if (mixinBytecode != null) {
                ClassNode classNode = new ClassNode(ASM.API_VERSION);
                ClassReader cr = new MixinClassReader(classfileBuffer, className);
                cr.accept(classNode, ClassReader.EXPAND_FRAMES);
                
                if (!this.reloadMixin(className, classNode)) {
                    return MixinAgent.ERROR_BYTECODE;
                }
                return mixinBytecode;
            }
            
            try {
                MixinAgent.log(Level.INFO, "Redefining class {}", className);
                return MixinAgent.this.classTransformer.transformClassBytes(null, className.replace('/', '.'), classfileBuffer);
            } catch (Throwable th) {
                MixinAgent.log(Level.ERROR, "Error while re-transforming class {}", className, th);
                return MixinAgent.ERROR_BYTECODE;
            }
        }

        private boolean reloadMixin(String className, ClassNode classNode) {
            MixinAgent.log(Level.INFO, "Redefining mixin {}", className);
            try {
                MixinAgent.this.classTransformer.reload(className.replace('/', '.'), classNode, this::reApplyMixins);
                return true;
            } catch (MixinReloadException e) {
                MixinAgent.log(Level.ERROR, "Mixin {} cannot be reloaded, needs a restart to be applied: {} ", e.getMixinInfo(), e.getMessage());
            } catch (Throwable th) {
                // catch everything as otherwise it is ignored
                MixinAgent.log(Level.ERROR, "Error while reloading mixin {}", className, th);
            }
            return false;
        }

        /**
         * Re-apply mixins to the targets that have already been loaded.
         * 
         * @param targets Target classes to re-transform
         */
        private void reApplyMixins(List<String> targets) {
            IMixinService service = MixinService.getService();
            Set<String> loadedClasses = new HashSet<>();
            for (Class<?> loadedClass : MixinAgent.instrumentation.getAllLoadedClasses()) {
                loadedClasses.add(loadedClass.getName());
            }

            List<ClassDefinition> definitions = new ArrayList<>();
            for (String target : targets) {
                String targetName = target.replace('/', '.');
                if (!loadedClasses.remove(targetName)) {
                    continue;
                }
                byte[] targetBytecode = MixinAgent.classLoader.getOriginalTargetBytecode(targetName);
                if (targetBytecode == null) {
                    MixinAgent.log(Level.WARN, "Skipping loaded target class {} because its original bytecode is not registered", targetName);
                    continue;
                }

                MixinAgent.log(Level.DEBUG, "Re-transforming target class {}", targetName);
                try {
                    Class<?> targetClass = service.getClassProvider().findClass(targetName, false);
                    targetBytecode = MixinAgent.this.classTransformer.transformClassBytes(null, targetName, targetBytecode);
                    definitions.add(new ClassDefinition(targetClass, targetBytecode));
                } catch (ClassNotFoundException ex) {
                    throw new MixinApplyError("Could not find loaded target class " + targetName, ex);
                }
            }

            if (!definitions.isEmpty()) {
                try {
                    MixinAgent.instrumentation.redefineClasses(definitions.toArray(new ClassDefinition[0]));
                } catch (ClassNotFoundException | UnmodifiableClassException ex) {
                    throw new MixinApplyError("Could not redefine mixin target classes", ex);
                }
            }
        }
    }

    /**
     * Bytecode that signals an error. When returned from the class file
     * transformer this causes a class file format exception and indicates in
     * the ide that somethings went wrong.
     */
    public static final byte[] ERROR_BYTECODE = new byte[]{1};

    /**
     * Class loader used to load mixin classes
     */
    static final MixinAgentClassLoader classLoader = new MixinAgentClassLoader();

    /**
     * Instance used to register the transformer
     */
    static Instrumentation instrumentation = null;

    /**
     * Instances of all agents
     */
    private static List<MixinAgent> agents = new ArrayList<MixinAgent>();

    /**
     * MixinTransformer instance to use to transform the mixin's target classes
     */
    final IMixinTransformer classTransformer;

    /**
     * Constructs an agent from a class transformer in which it will use to
     * transform mixin's target class.
     *
     * @param classTransformer Class transformer that will transform a mixin's
     *                         target class
     */
    public MixinAgent(IMixinTransformer classTransformer) {
        this.classTransformer = classTransformer;
        MixinAgent.agents.add(this);
        if (MixinAgent.instrumentation != null) {
            this.initTransformer();
        }
    }

    private void initTransformer() {
        MixinAgent.instrumentation.addTransformer(new Transformer(), true);
    }

    @Override
    public void registerMixinClass(String name) {
        MixinAgent.classLoader.addMixinClass(name);
    }

    @Override
    public void registerTargetClass(String name, ClassNode classNode) {
        MixinAgent.classLoader.addTargetClass(name, classNode);
    }

    /**
     * Sets the instrumentation instance so that the mixin agents can redefine
     * mixins.
     *
     * @param instrumentation Instance to use to redefine the mixins
     */
    public static void init(Instrumentation instrumentation) {
        MixinAgent.instrumentation = instrumentation;
        if (!MixinAgent.instrumentation.isRedefineClassesSupported()) {
            MixinAgent.log(Level.ERROR, "The instrumentation doesn't support re-definition of classes");
        }
        for (MixinAgent agent : MixinAgent.agents) {
            agent.initTransformer();
        }
    }

    /**
     * Initialize the java agent
     *
     * <p>This will be called automatically if the jar is in a -javaagent java
     * command line argument</p>
     *
     * @param arg Ignored
     * @param instrumentation Instance to use to transform the mixins
     */
    public static void premain(String arg, Instrumentation instrumentation) {
        System.setProperty("mixin.hotSwap", "true");
        MixinAgent.init(instrumentation);
    }

    /**
     * Initialize the java agent
     *
     * <p>This will be called automatically if the java agent is loaded after
     * JVVM startup</p>
     *
     * @param arg Ignored
     * @param instrumentation Instance to use to re-define the mixins
     */
    public static void agentmain(String arg, Instrumentation instrumentation) {
        MixinAgent.init(instrumentation);
    }

    /**
     * Wrapper for logger since we can't access the log abstraction in premain
     * 
     * @param level the logging level
     * @param message the message to log
     * @param params parameters to the message
     */
    public static void log(Level level, String message, Object... params) {
        try {
            MixinService.getService().getLogger("mixin.agent").log(level, message, params);
        } catch (ServiceNotAvailableError err) {
            System.err.printf("MixinAgent: %s: %s", level.name(), String.format(message, params));
        }
    }

}
