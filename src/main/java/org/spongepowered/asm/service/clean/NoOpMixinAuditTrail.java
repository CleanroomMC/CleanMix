package org.spongepowered.asm.service.clean;

import org.spongepowered.asm.service.IMixinAuditTrail;

public class NoOpMixinAuditTrail implements IMixinAuditTrail {

    public static final IMixinAuditTrail INSTANCE = new NoOpMixinAuditTrail();

    private NoOpMixinAuditTrail() { }

    @Override
    public void onApply(String className, String mixinName) { }

    @Override
    public void onPostProcess(String className) { }

    @Override
    public void onGenerate(String className, String generatorName) { }

}
