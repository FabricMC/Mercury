/*
 * Copyright (c) 2018 Cadix Development (https://www.cadixdev.org)
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.cadixdev.mercury.remapper.mixin;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnotationValueCapturingClassVisitor extends ClassVisitor {
    private final Map<String, Object> annotationValues = new HashMap<>();

    AnnotationValueCapturingClassVisitor(int api) {
        super(api);
    }

    public Map<String, Object> getAnnotationValues() {
        return annotationValues;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        annotationValues.clear();
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodVisitor(this.api, methodVisitor) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                AnnotationVisitor annotationVisitor = super.visitAnnotation(descriptor, visible);
                return new AnnotationCapturingVisitor(api, annotationVisitor, annotationValues);
            }
        };
    }

    private static class AnnotationCapturingVisitor extends AnnotationVisitor {
        protected final Map<String, Object> annotationValues;

        protected AnnotationCapturingVisitor(int api, AnnotationVisitor annotationVisitor, Map<String, Object> annotationValues) {
            super(api, annotationVisitor);
            this.annotationValues = annotationValues;
        }

        @Override
        public void visit(String name, Object value) {
            annotationValues.put(name, value);
            super.visit(name, value);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            super.visitEnum(name, descriptor, value);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            return super.visitAnnotation(name, descriptor);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            AnnotationVisitor arrayAnnotationVisitor = super.visitArray(name);
            List<Object> arrayValues = new ArrayList<>();
            this.annotationValues.put(name, arrayValues);

            return new AnnotationCapturingVisitor(api, arrayAnnotationVisitor, new HashMap<>()) {
                @Override
                public void visitEnd() {
                    arrayValues.addAll(this.annotationValues.values());
                    super.visitEnd();
                }
            };
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }
}
