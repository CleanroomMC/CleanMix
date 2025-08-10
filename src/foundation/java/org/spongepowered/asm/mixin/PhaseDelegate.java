package org.spongepowered.asm.mixin;

import org.spongepowered.asm.mixin.MixinEnvironment;

import static org.spongepowered.asm.mixin.MixinEnvironment.Phase.*;

public class PhaseDelegate {
    public static void goToNextPhase() {
        MixinEnvironment.Phase current = MixinEnvironment.getCurrentEnvironment().getPhase();
        switch (current.ordinal) {
            case 0 -> MixinEnvironment.gotoPhase(INIT);
            case 1 -> MixinEnvironment.gotoPhase(DEFAULT);
            case 2 -> MixinEnvironment.gotoPhase(MOD);
        }
    }
}
