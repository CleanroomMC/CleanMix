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

import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.IAdviceProvider;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IFeatureValidator;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;
import org.spongepowered.asm.service.clean.ICleanMixinService;
import org.spongepowered.asm.service.clean.NoOpMixinAuditTrail;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractMixinServiceLaunchWrapper extends MixinServiceAbstract implements ICleanMixinService {

    private static final String PROXY = MixinServiceAbstract.MIXIN_PACKAGE + "transformer.Proxy";
    private static final String STATE_TWEAKER = MixinServiceAbstract.MIXIN_PACKAGE + "EnvironmentStateTweaker";

    private final ClassProvider classProvider = new ClassProvider();
    private final TransformerProvider transformerProvider = new TransformerProvider();
    private final ClassLoaderUtil classLoaderUtil = new ClassLoaderUtil(Launch.classLoader);
    private final BytecodeProvider bytecodeProvider = new BytecodeProvider(transformerProvider, this.getReEntranceLock(), classLoaderUtil);

    private MixinAuditFile auditLog;
    private boolean auditLogResolved;
    private IMixinAuditTrail auditTrail;

    /** Resolve a container to its owner mod id via the loader's mod registry, or {@code null} if unknown. */
    protected abstract String resolveSourceId(URI source);

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public MixinEnvironment.Phase getInitialPhase() {
        if (isDevelopment()) {
            System.setProperty("mixin.env.remapRefMap", "true");
        }
        return MixinEnvironment.Phase.PREINIT;
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_8;
    }

    @Override
    public void offer(IMixinInternal internal) {
        super.offer(internal);
        // MixinProcessor#refresh offers a "Refresh" internal when configs are (re-)selected late.
        // Rebuild the delegated transformer list so transformers registered since are picked up.
        if ("Refresh".equals(internal.toString())) {
            this.transformerProvider.refreshDelegatedTransformers();
        }
    }

    @Override
    public void beginPhase() {
        Launch.classLoader.registerTransformer(PROXY);
        // The mixin transformer must never be in the delegated chain BytecodeProvider applies when fetching a
        // target class's bytecode, else resolving a mixin target re-enters the pipeline and StackOverflows.
        this.transformerProvider.addTransformerExclusion(PROXY);
    }

    @Override
    public void init() {
        GlobalProperties.<List<String>>get(Blackboard.TWEAK_CLASSES_KEY).add(STATE_TWEAKER);
    }

    @Override
    public IClassProvider getClassProvider() {
        return classProvider;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return bytecodeProvider;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return transformerProvider;
    }

    @Override
    public IClassTracker getClassTracker() {
        return classLoaderUtil;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        if (this.auditTrail == null) {
            MixinAuditFile log = auditLog();
            this.auditTrail = log != null ? new MixinAuditTrailRecorder(log, getName() + "/Audit") : NoOpMixinAuditTrail.INSTANCE;
        }
        return this.auditTrail;
    }

    @Override
    public IFeatureValidator getFeatureValidator() {
        return IFeatureValidator.ALLOW_ALL;
    }

    @Override
    public IAdviceProvider getAdviceProvider() {
        return IAdviceProvider.GENERIC;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.emptyList();
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        try {
            URI uri = this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            return new ContainerHandleURI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return Launch.classLoader.getResourceAsStream(name);
    }

    @Override
    public URL getResource(String name) {
        return Launch.classLoader.findResource(name);
    }

    @Override
    protected ILogger createLogger(String name) {
        return this.auditLog() == null ? new LoggerAdapterLog4j2(name) : new Log4j2AuditingAdapter(name, this.auditLog());
    }

    /**
     * A config's source id is its canonical owner (mod) id. Resolves the container via the loader's mod
     * registry ({@link #resolveSourceId(URI)}), falling back to the jar's base name (extension + trailing
     * version stripped) for libraries/dev classpath dirs that declare no mod.
     */
    @Override
    public String getSourceId(URI source) {
        if (source == null) {
            return null;
        }
        String resolved = resolveSourceId(source);
        if (resolved != null) {
            return resolved;
        }
        String path = source.getPath();
        if (path == null) {
            return null;
        }
        int lastSlash = path.lastIndexOf('/');
        return jarBaseName(lastSlash >= 0 ? path.substring(lastSlash + 1) : path);
    }

    private static String jarBaseName(String name) {
        if (name.endsWith(".jar")) {
            name = name.substring(0, name.length() - ".jar".length());
        }
        int i = name.length();
        while (i > 0 && (Character.isDigit(name.charAt(i - 1)) || name.charAt(i - 1) == '.')) {
            i--;
        }
        if (i == 0 || i == name.length()) {
            return name;
        }
        if (name.charAt(i - 1) == '-') {
            i--;
        }
        return name.substring(0, i);
    }

    /** Whether this is a dev (deobfuscated) launch. Default detects Gradle's {@code GradleStart}. */
    protected boolean isDevelopment() {
        return System.getProperty("sun.java.command", "").contains("GradleStart");
    }

    /**
     * Override to enable a dedicated mixin log + audit trail: return a {@link MixinAuditFile} (e.g.
     * {@code new MixinAuditFile("cleanmix.log", "cleanmix.auditTrail")}) and {@link #getAuditTrail()}
     * will record apply/post-process/generate events into it. Default {@code null} (disabled). Resolved once.
     */
    protected MixinAuditFile createAuditLog() {
        return null;
    }

    /**
     * The resolved audit log, or {@code null} when disabled. A subclass may tee its own diagnostics (e.g. a
     * class-load tracer) into the same file rather than opening a second one.
     */
    protected final MixinAuditFile getAuditLog() {
        return auditLog();
    }

    private MixinAuditFile auditLog() {
        if (!this.auditLogResolved) {
            this.auditLog = createAuditLog();
            this.auditLogResolved = true;
        }
        return this.auditLog;
    }

}
