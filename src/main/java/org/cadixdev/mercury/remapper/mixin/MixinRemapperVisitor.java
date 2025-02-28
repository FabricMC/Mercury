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

import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.extension.mixin.common.data.Annotation;
import net.fabricmc.tinyremapper.extension.mixin.common.data.CommonData;
import net.fabricmc.tinyremapper.extension.mixin.soft.SoftTargetMixinClassVisitor;
import org.cadixdev.mercury.RewriteContext;
import org.cadixdev.mercury.remapper.RemapperAdapter;
import org.cadixdev.mercury.remapper.SimpleRemapperVisitor;
import org.eclipse.jdt.core.dom.*;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MixinRemapperVisitor extends ASTVisitor {
    final RewriteContext context;
    final RemapperAdapter remapper;

    final AnnotationValueCapturingClassVisitor valueCapture;
    final ClassVisitor classVisitor;

    public MixinRemapperVisitor(RewriteContext context, TrEnvironment trEnvironment) {
        this.context = context;
        this.remapper = new RemapperAdapter(trEnvironment);

        this.valueCapture = new AnnotationValueCapturingClassVisitor(Opcodes.ASM9);
        this.classVisitor = new SoftTargetMixinClassVisitor(new CommonData(trEnvironment), valueCapture);
    }

    @Override
    public boolean visit(final MethodDeclaration node) {
        final AST ast = this.context.getCompilationUnit().getAST();
        final IMethodBinding binding = node.resolveBinding();
        final ITypeBinding declaringClass = binding.getDeclaringClass();

        IAnnotationBinding mixinAnnotation = getMixinAnnotation(declaringClass);

        if (mixinAnnotation == null) {
            return true;
        }

        classVisitor.visit(Opcodes.V17, 0, getType(declaringClass).getInternalName(), null, getType(declaringClass.getSuperclass()).getInternalName(), null);

        // Visit the @Mixin annotation to populate the target classes
        visitAnnotation(mixinAnnotation, classVisitor.visitAnnotation(Annotation.MIXIN, true));

        MethodVisitor methodVisitor = classVisitor.visitMethod(0, binding.getName(), SimpleRemapperVisitor.methodDesc(binding), null, null);

        if (methodVisitor == null) {
            return true;
        }

        for (int i = 0; i < binding.getAnnotations().length; i++) {
            final IAnnotationBinding annotation = binding.getAnnotations()[i];
            AnnotationVisitor annotationVisitor = methodVisitor.visitAnnotation(getTypeDescriptor(annotation.getAnnotationType()), true);

            if (annotationVisitor == null) {
                continue;
            }

            visitAnnotation(annotation, annotationVisitor);

            Map<String, Object> remappedValues = valueCapture.getAnnotationValues();
            final NormalAnnotation originalAnnotation = (NormalAnnotation) node.modifiers().get(i);
            for (final Object raw : originalAnnotation.values()) {
                final MemberValuePair pair = (MemberValuePair) raw;
                String name = pair.getName().getIdentifier();

                if (!remappedValues.containsKey(name)) {
                    continue;
                }

                // TODO don't hard code this
                if (name.equals("method")) {
                    List<String> o = (List<String>) remappedValues.get(name);
                    replaceExpression(ast, context, pair.getValue(), o.get(0));
                }
            }
        }


        return true;
    }

    private static void replaceExpression(final AST ast, final RewriteContext context, final Expression original, final String replacement) {
        final StringLiteral replacementLiteral = ast.newStringLiteral();
        replacementLiteral.setLiteralValue(replacement);
        context.createASTRewrite().replace(original, replacementLiteral, null);
    }

    private void visitAnnotation(IAnnotationBinding annotation, AnnotationVisitor visitor) {
        for (IMemberValuePairBinding valuePair : annotation.getAllMemberValuePairs()) {
            String name = valuePair.getName();
            Object value = valuePair.getValue();
            visitAnnotationValue(name, value, visitor);
        }

        visitor.visitEnd();
    }

    private void visitAnnotationValue(String name, Object value, AnnotationVisitor visitor) {
        if (value instanceof ITypeBinding typeBinding) { // Class
            System.out.println();
            Type type = getType(typeBinding);
            visitor.visit(name, type);
        } else if (value instanceof IVariableBinding variableBinding) { // Enum
            // TODO
        } else if (value instanceof IAnnotationBinding annotationBinding) {
            // TODO
        } else if (value instanceof Object[] array) { // Array
            System.out.printf("Array: %s\n", name);
            AnnotationVisitor arrayVisitor = visitor.visitArray(name);

            for (Object o : array) {
                visitAnnotationValue(null, o, arrayVisitor);
            }

            arrayVisitor.visitEnd();
        } else { // Primitive, String
            visitor.visit(name, value);
        }
    }

    @Nullable
    private IAnnotationBinding getMixinAnnotation(ITypeBinding binding) {
        for (final IAnnotationBinding annotation : binding.getAnnotations()) {
            if (Objects.equals(Annotation.MIXIN, getTypeDescriptor(annotation.getAnnotationType()))) {
                return annotation;
            }
        }

        return null;
    }

    private static String getTypeDescriptor(ITypeBinding typeBinding) {
        return "L" + typeBinding.getBinaryName().replace('.', '/') + ";";
    }

    private static Type getType(ITypeBinding typeBinding) {
        return Type.getType(getTypeDescriptor(typeBinding));
    }
}
