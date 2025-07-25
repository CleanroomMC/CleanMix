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

package org.spongepowered.asm.mixin.build

import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

abstract class ValidateLicenseHeadersTasks extends SourceTask {
    @Input
    TextResource header

    @TaskAction
    void run() {
        def headerStr = header.asString()
        def javaSources = getSource().matching(pattern -> pattern.include("**/*.java")).files

        def failedFiles = []

        for (def source : javaSources) {
            if (!source.text.startsWith(headerStr)) {
                failedFiles.add(source.name)
                logger.warn("File {} does not have the correct license header", source.path)
            }
        }

        if (!failedFiles.isEmpty()) {
            throw new RuntimeException("License header validation failed for the following files:\n" +
                                       failedFiles.join("\n"))
        }
    }
}
