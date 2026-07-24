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
package org.spongepowered.tools.obfuscation;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.spongepowered.asm.mixin.CleanroomUtil;
import org.spongepowered.asm.mixin.Compatibility;
import org.spongepowered.asm.util.Constants;
import org.spongepowered.tools.obfuscation.mirror.AnnotationHandle;
import org.spongepowered.tools.obfuscation.mirror.TypeUtils;

import com.google.gson.GsonBuilder;

/**
 * Collects CleanMix compatibility metadata and writes it to the class output.
 */
final class CompatibilityManager {

    private final AnnotatedMixins ap;
    private final Map<String, String> compatibilities = new HashMap<String, String>();

    private boolean written;

    CompatibilityManager(AnnotatedMixins ap) {
        this.ap = ap;
    }

    void registerMixin(TypeElement mixin) {
        String className = TypeUtils.getInternalName(mixin).replace('/', '.');
        String classVersion = this.getVersion(mixin, CleanroomUtil.getVersionString(CleanroomUtil.COMPATIBILITY_LATEST));
        this.compatibilities.put(className, classVersion);

        for (Element member : mixin.getEnclosedElements()) {
            AnnotationHandle annotation = AnnotationHandle.of(member, Compatibility.class);
            if (!annotation.exists()) {
                continue;
            }

            String version = this.getVersion(member, classVersion);
            if (member.getKind() == ElementKind.METHOD || member.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement method = (ExecutableElement) member;
                String name = member.getKind() == ElementKind.CONSTRUCTOR ? Constants.CTOR : method.getSimpleName().toString();
                this.compatibilities.put(CleanroomUtil.getMethodCompatibilityKey(className, name, TypeUtils.getDescriptor(method)), version);
            } else if (member.getKind() == ElementKind.FIELD || member.getKind() == ElementKind.ENUM_CONSTANT) {
                VariableElement field = (VariableElement) member;
                this.compatibilities.put(CleanroomUtil.getFieldCompatibilityKey(className, field.getSimpleName().toString(),
                        TypeUtils.getInternalName(field)), version);
            }
        }
    }

    private String getVersion(Element element, String defaultVersion) {
        AnnotationHandle annotation = AnnotationHandle.of(element, Compatibility.class);
        if (!annotation.exists()) {
            return defaultVersion;
        }

        String version = annotation.<String>getValue();
        try {
            return CleanroomUtil.getVersionString(CleanroomUtil.parseCompatibility(version));
        } catch (IllegalArgumentException ex) {
            this.ap.printMessage(Kind.ERROR, ex.getMessage(), element, annotation.asMirror());
            return defaultVersion;
        }
    }

    void write() {
        if (this.written || this.compatibilities.isEmpty()) {
            return;
        }
        this.written = true;

        Writer writer = null;
        try {
            FileObject output = this.ap.getProcessingEnvironment().getFiler().createResource(
                    StandardLocation.CLASS_OUTPUT, "", CleanroomUtil.COMPATIBILITY_RESOURCE);
            writer = output.openWriter();
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(this.compatibilities, writer);
        } catch (IOException ex) {
            this.ap.printMessage(Kind.ERROR, "Cannot write CleanMix compatibility metadata: " + ex.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ex) {
                }
            }
        }
    }

}
