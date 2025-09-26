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

