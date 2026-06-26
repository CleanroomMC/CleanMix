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
package org.spongepowered.asm.service.mojang;

import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.logging.LoggerAdapterAbstract;

public class Log4j2AuditingAdapter extends LoggerAdapterLog4j2 {

    private final MixinAuditFile file;

    public Log4j2AuditingAdapter(String name, MixinAuditFile file) {
        super(name);
        this.file = file;
    }

    @Override
    public void catching(Level level, Throwable t) {
        this.logger.catching(LEVELS[level.ordinal()], t);
        this.file.write(level, this.getId(), "Catching " + t, t);
    }

    @Override
    public void catching(Throwable t) {
        this.logger.catching(t);
        this.file.write(Level.WARN, this.getId(), "Catching " + t, t);
    }

    @Override
    public void log(Level level, String message, Object... params) {
        this.logger.log(LEVELS[level.ordinal()], message, params);
        LoggerAdapterAbstract.FormattedMessage formatted = new LoggerAdapterAbstract.FormattedMessage(message, params);
        this.file.write(level, this.getId(), formatted.getMessage(), formatted.getThrowable());
    }

    @Override
    public void log(Level level, String message, Throwable t) {
        this.logger.log(LEVELS[level.ordinal()], message, t);
        this.file.write(level, this.getId(), message, t);
    }

    @Override
    public <T extends Throwable> T throwing(T t) {
        this.file.write(Level.WARN, this.getId(), "Throwing " + t, t);
        return this.logger.throwing(t);
    }

}
