package org.spongepowered.asm.service.cleanroom;

import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;

public class ModContainerHandle extends ContainerHandleURI {

    private final String id;

    public ModContainerHandle(URI uri, String id) {
        super(uri);
        if (id == null) {
            String fileName = Paths.get(uri).getFileName().toString();
            fileName = fileName.indexOf('.') != -1 ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            this.id = fileName;
        } else {
            this.id = id;
        }
    }

    public ModContainerHandle(File file, String id) {
        this(file.toURI(), id);
    }

    public ModContainerHandle(URI uri) {
        this(uri, null);
    }

    public ModContainerHandle(File file) {
        this(file.toURI(), null);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return String.format("ModContainerHandle(%s|%s)", this.getId(), this.getURI());
    }

}

