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
package org.spongepowered.asm.service.clean;

import java.net.URI;
import java.net.URL;

/**
 * CleanMix extension methods for IMixinService instances
 */
public interface ICleanMixinService {

    /**
     * Get a resource from the appropriate classloader, this is
     * delegated via the service so that the service can choose the correct
     * classloader from which to obtain the resource.
     *
     * @param name resource path
     * @return resource or null if resource not found
     */
    URL getResource(String name);

    /**
     * Resolve a suitable string source identifier for the
     * {@link org.spongepowered.asm.launch.platform.container.ContainerHandleURI} to take in
     *
     * @param source uri path of source
     * @return suitable string source identifier or null if it cannot be resolved
     */
    String getSourceId(URI source);

}
