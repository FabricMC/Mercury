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
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MixinRemapperVisitor extends ASTVisitor {
    final RewriteContext context;
    final RemapperAdapter remapper;

    final ClassNode valueCaptureNode;
    final ClassVisitor classVisitor;

    public MixinRemapperVisitor(RewriteContext context, TrEnvironment trEnvironment) {
        this.context = context;
        this.remapper = new RemapperAdapter(trEnvironment);

        this.valueCaptureNode = new ClassNode();
        this.classVisitor = new SoftTargetMixinClassVisitor(new CommonData(trEnvironment), valueCaptureNode);
    }

    @Override
    public boolean visit(final MethodDeclaration node) {
        final IMethodBinding binding = node.resolveBinding();
        final ITypeBinding declaringClass = binding.getDeclaringClass();

        if (checkGracefully(declaringClass)) {
            return true;
        }

        IAnnotationBinding mixinAnnotation = getMixinAnnotation(declaringClass);

        if (mixinAnnotation == null) {
            return true;
        }

        classVisitor.visit(Opcodes.V17, 0, getType(declaringClass).getInternalName(), null, null, null);
        visitAnnotation(mixinAnnotation, classVisitor.visitAnnotation(Annotation.MIXIN, true));
        valueCaptureNode.methods.clear();
        MethodVisitor methodVisitor = classVisitor.visitMethod(0, binding.getName(), SimpleRemapperVisitor.methodDesc(binding), null, null);

        if (methodVisitor == null) {
            return true;
        }

        MethodNode methodNode = valueCaptureNode.methods.get(0);

        for (int i = 0; i < binding.getAnnotations().length; i++) {
            if (methodNode.visibleAnnotations != null) {
                methodNode.visibleAnnotations.clear();
            }

            final IAnnotationBinding annotation = binding.getAnnotations()[i];

            if (annotation.getAllMemberValuePairs().length == 0) {
                // No values to remap
                continue;
            }

            AnnotationVisitor annotationVisitor = methodVisitor.visitAnnotation(getTypeDescriptor(annotation.getAnnotationType()), true);
            visitAnnotation(annotation, annotationVisitor);
            AnnotationNode remappedAnnoationNode = methodNode.visibleAnnotations.get(0);

            AnnotationNode unmappedAnnotationNode = new AnnotationNode(getTypeDescriptor(annotation.getAnnotationType()));
            visitAnnotation(annotation, unmappedAnnotationNode);

            Map<String, Object> remappedValues = valueListToMap(remappedAnnoationNode.values);
            Map<String, Object> unmappedValues = valueListToMap(unmappedAnnotationNode.values);

            if (!(node.modifiers().get(i) instanceof NormalAnnotation originalAnnotation)) {
                continue;
            }

            remapNormalAnnotation(originalAnnotation, remappedValues, unmappedValues);
        }

        return true;
    }

    private void remapNormalAnnotation(NormalAnnotation originalAnnotation, Map<String, Object> remappedValues, Map<String, Object> unmappedValues) {
        for (final Object raw : originalAnnotation.values()) {
            final MemberValuePair pair = (MemberValuePair) raw;
            final Expression expression = pair.getValue();
            final String name = pair.getName().getIdentifier();

            remapAnnotationValue(name, expression, remappedValues, unmappedValues);
        }
    }

    private void remapAnnotationValue(String name, Expression expression, Map<String, Object> remappedValues, Map<String, Object> unmappedValues) {
        final AST ast = this.context.getCompilationUnit().getAST();

        List<?> remappedValue = forceList(Objects.requireNonNull(remappedValues.get(name), "Remapped value: " + name));
        List<?> unmappedValue = forceList(Objects.requireNonNull(unmappedValues.get(name), "Unmapped value: " + name));

        if (Objects.equals(remappedValue, unmappedValue)) {
            // Nothing to do here as it didn't change
            return;
        }

        if (remappedValue.stream().allMatch(o -> o instanceof String)) {
            List<String> stringValues = remappedValue.stream().map(o -> (String) o).toList();

            if (expression instanceof StringLiteral || expression instanceof InfixExpression) {
                replaceExpression(ast, this.context, expression, stringValues.get(0));
            } else if (expression instanceof ArrayInitializer array) {
                for (int j = 0; j < array.expressions().size(); j++) {
                    final StringLiteral original = (StringLiteral) array.expressions().get(j);
                    replaceExpression(ast, this.context, original, stringValues.get(j));
                }
            }
        } else if (remappedValue.stream().allMatch(o -> o instanceof AnnotationNode)) {
            List<AnnotationNode> annotationValues = remappedValue.stream().map(o -> (AnnotationNode) o).toList();
            List<AnnotationNode> unmappedAnnotationValues = unmappedValue.stream().map(o -> (AnnotationNode) o).toList();

            if (expression instanceof NormalAnnotation normalAnnotation) {
                List<Object> remappedInnerValues = annotationValues.get(0).values;
                List<Object> unmappedInnerValues = unmappedAnnotationValues.get(0).values;
                remapNormalAnnotation(normalAnnotation, valueListToMap(remappedInnerValues), valueListToMap(unmappedInnerValues));
            } else if (expression instanceof ArrayInitializer array) {
                for (int j = 0; j < array.expressions().size(); j++) {
                    final NormalAnnotation original = (NormalAnnotation) array.expressions().get(j);
                    remapNormalAnnotation(original, valueListToMap(annotationValues.get(j).values), valueListToMap(unmappedAnnotationValues.get(j).values));
                }
            }
        }
    }

    @Override
    public boolean visit(final TypeDeclaration node) {
        // TODO remap string @Mixin targets
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
            Type type = getType(typeBinding);
            visitor.visit(name, type);
        } else if (value instanceof IVariableBinding variableBinding) { // Enum
            visitor.visitEnum(name, getTypeDescriptor(variableBinding.getDeclaringClass()), variableBinding.getName());
        } else if (value instanceof IAnnotationBinding annotationBinding) {
            AnnotationVisitor innerAnnotationVisitor = visitor.visitAnnotation(name, getTypeDescriptor(annotationBinding.getAnnotationType()));
            visitAnnotation(annotationBinding, innerAnnotationVisitor);
            innerAnnotationVisitor.visitEnd();
        } else if (value instanceof Object[] array) { // Array
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

    public static Map<String, Object> valueListToMap(List<Object> list) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < list.size(); i += 2) {
            map.put((String) list.get(i), list.get(i + 1));
        }
        return map;
    }

    public static List<?> forceList(Object object) {
        if (object instanceof List<?> list) {
            return list;
        } else {
            return List.of(object);
        }
    }

    public boolean checkGracefully(final ITypeBinding binding) {
        return context.getMercury().isGracefulClasspathChecks() && binding.getBinaryName() == null;
    }
}
