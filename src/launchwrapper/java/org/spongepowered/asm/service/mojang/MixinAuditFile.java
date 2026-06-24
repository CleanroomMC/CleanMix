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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * A self-contained sink that mirrors mixin activity into a dedicated log file under {@code logs/}, separate from
 * the global game log. The file name and the disable-property are supplied by the owning service. A plain file
 * writer is used rather than a runtime Log4j2 appender (which would need awkward reflection).
 * <p>
 * The file is opened lazily on first write and flushed after every entry, so it survives a hard crash. All
 * methods are safe against I/O failure and never throw. Backs {@link MixinAuditTrailRecorder}, but is public so
 * a service may tee its own diagnostics (e.g. a class-load tracer) into the same file.
 */
public class MixinAuditFile {

    private static final String MESSAGE_FORMAT = "[%s] [%s/%s] [%s]: %s%s";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String fileName;
    private final String disableProperty;

    private Writer writer;
    private boolean opened;

    /**
     * @param fileName        the file to write under {@code logs/}, e.g. {@code "cleanmix.log"}
     * @param disableProperty a system property which, when set to {@code "false"}, disables the file entirely
     */
    public MixinAuditFile(String fileName, String disableProperty) {
        this.fileName = fileName;
        this.disableProperty = disableProperty;
    }

    private void open() {
        this.opened = true;
        if ("false".equalsIgnoreCase(System.getProperty(this.disableProperty))) {
            return;
        }
        try {
            File dir = new File("logs");
            dir.mkdirs();
            this.writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(new File(dir, this.fileName), false), StandardCharsets.UTF_8));
            Runtime.getRuntime().addShutdownHook(new Thread(this::close, this.fileName + "/LogCloser"));
        } catch (Throwable t) {
            System.err.println("Unable to open logs/" + this.fileName + ": " + t);
        }
    }

    /** Append a single entry, flushing immediately so the file survives a hard crash. Never throws. */
    public void write(Level level, String name, String message, Throwable t) {
        synchronized (this) {
            if (!this.opened) {
                this.open();
            }
            if (this.writer == null) {
                return;
            }
            try {
                this.writer.write(String.format(MESSAGE_FORMAT,
                        LocalTime.now().format(TIME),
                        Thread.currentThread().getName(),
                        level,
                        name,
                        message,
                        System.lineSeparator()
                ));
                if (t != null) {
                    PrintWriter pw = new PrintWriter(this.writer);
                    t.printStackTrace(pw);
                    pw.flush(); // flush only; closing would close the underlying writer
                }
                this.writer.flush();
            } catch (IOException ignored) { }
        }
    }

    private void close() {
        synchronized (this) {
            if (this.writer == null) {
                return;
            }
            try {
                this.writer.close();
            } catch (IOException ignored) { }
        }
    }

}
