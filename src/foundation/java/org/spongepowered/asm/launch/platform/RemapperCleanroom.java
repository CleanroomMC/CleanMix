package org.spongepowered.asm.launch.platform;

import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.commons.Remapper;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.extensibility.IRemapper;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.ObfuscationUtil;

import java.util.function.Function;

@SuppressWarnings("unchecked")
public class RemapperCleanroom implements IRemapper, ObfuscationUtil.IClassRemapper {

    private final ILogger logger = MixinService.getService().getLogger("mixin");
    private final Remapper remapper = (Remapper) Launch.blackboard.get("fml.deobf.instance");
    private final Function<String, String> mdUnmap = (Function<String, String>) Launch.blackboard.get("fml.deobf.unmap");

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        this.logger.debug("{} is remapping method {}{} for {}", this, name, desc, owner);
        String newName = this.remapper.mapMethodName(owner, name, desc);
        if (!newName.equals(name)) {
            return newName;
        }
        String obfOwner = this.unmap(owner);
        String obfDesc = this.unmapDesc(desc);
        this.logger.debug("{} is remapping obfuscated method {}{} for {}", this, name, obfDesc, obfOwner);
        return this.remapper.mapMethodName(obfOwner, name, obfDesc);
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        this.logger.debug("{} is remapping field {}{} for {}", this, name, desc, owner);
        String newName = this.remapper.mapFieldName(owner, name, desc);
        if (!newName.equals(name)) {
            return newName;
        }
        String obfOwner = this.unmap(owner);
        String obfDesc = this.unmapDesc(desc);
        this.logger.debug("{} is remapping obfuscated field {}{} for {}", this, name, obfDesc, obfOwner);
        return this.remapper.mapFieldName(obfOwner, name, obfDesc);
    }

    @Override
    public String map(String typeName) {
        this.logger.debug("{} is remapping class {}", this, typeName);
        return this.remapper.map(typeName);
    }

    @Override
    public String unmap(String typeName) {
        return mdUnmap.apply(typeName);
    }

    @Override
    public String mapDesc(String desc) {
        return this.remapper.mapDesc(desc);
    }

    @Override
    public String unmapDesc(String desc) {
        String newDesc = ObfuscationUtil.unmapDescriptor(desc, this);
        return newDesc != null ? newDesc : desc;
    }

}
