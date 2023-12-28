package com.cleanroommc.cleanmix;

import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment.CompatibilityLevel;
import org.spongepowered.asm.mixin.MixinEnvironment.Phase;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.util.ReEntranceLock;

import java.io.InputStream;
import java.util.Collection;

final class CleanMixService implements IMixinService {

    private static final ILogger logger = CleanMix.logger();

    private final String side;
    private final ReEntranceLock lock;

    CleanMixService(String side) {
        this.side = side;
        this.lock = new ReEntranceLock(1);
    }

    @Override
    public String getName() {
        return "CleanMix";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void prepare() {

    }

    @Override
    public Phase getInitialPhase() {
        return Phase.DEFAULT;
    }

    @Override
    public void offer(IMixinInternal internal) {

    }

    @Override
    public void init() {

    }

    @Override
    public void beginPhase() {

    }

    @Override
    public void checkEnv(Object bootSource) {

    }

    @Override
    public ReEntranceLock getReEntranceLock() {
        return lock;
    }

    @Override
    public IClassProvider getClassProvider() {
        return null;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return null;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return null;
    }

    @Override
    public IClassTracker getClassTracker() {
        return null;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return null;
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return null;
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return null;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return null;
    }

    @Override
    public String getSideName() {
        return side;
    }

    @Override
    public CompatibilityLevel getMinCompatibilityLevel() {
        return null;
    }

    @Override
    public CompatibilityLevel getMaxCompatibilityLevel() {
        return null;
    }

    @Override
    public ILogger getLogger(String name) {
        return CleanMix.logger(name);
    }

}
