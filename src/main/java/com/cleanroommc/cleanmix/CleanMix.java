package com.cleanroommc.cleanmix;

import com.cleanroommc.cleanmix.logging.Log4jLogger;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.service.IMixinService;

public class CleanMix {

    private static final ILogger logger = new Log4jLogger("CleanMix");

    private static CleanMixService service;

    public static IMixinService service(String side) {
        if (service == null) {
            service = new CleanMixService(side);
        }
        return service;
    }

    public static ILogger logger() {
        return logger;
    }

    public static ILogger logger(String name) {
        return new Log4jLogger(name);
    }

}
