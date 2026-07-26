package org.spongepowered.asm.service.clean;

public interface ICleanMixinTransformer {

    /**
     * Allow preparation of registered and not yet consumed configurations.
     *
     * <p>Cheap and safe to call redundantly: it returns immediately if nothing is pending.</p>
     */
    void refresh();

}
