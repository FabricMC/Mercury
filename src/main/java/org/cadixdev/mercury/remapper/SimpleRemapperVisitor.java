/*
 * Copyright (c) 2018 Cadix Development (https://www.cadixdev.org)
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.cadixdev.mercury.remapper;

import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrMethod;
import org.cadixdev.mercury.RewriteContext;
import org.cadixdev.mercury.util.GracefulCheck;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Remaps only methods, fields, and parameters.
 */
class SimpleRemapperVisitor extends ASTVisitor {

    private static final String LVT_NAMES_PROPERTY = "org.cadixdev.mercury.lvtNames";
    private static final String LOCAL_VARIABLE_NAME_PROPERTY = "org.cadixdev.mercury.localVariableName";
    private static final String NEW_PARAM_NAMES_PROPERTY = "org.cadixdev.mercury.newParamNames";

    final RewriteContext context;
    final RemapperAdapter remapper;

    SimpleRemapperVisitor(RewriteContext context, boolean javadoc, TrEnvironment trEnvironment) {
        super(javadoc);
        this.context = context;
        this.remapper = new RemapperAdapter(trEnvironment);
    }

    final void updateIdentifier(SimpleName node, String newName) {
        if (!node.getIdentifier().equals(newName) && !node.isVar()) {
            this.context.createASTRewrite().set(node, SimpleName.IDENTIFIER_PROPERTY, newName, null);
        }
    }

    private void remapMethod(SimpleName node, IMethodBinding binding) {
        ITypeBinding declaringClass = binding.getDeclaringClass();
        if (GracefulCheck.checkGracefully(this.context, declaringClass)) {
            return;
        }

        if (binding.isConstructor()) {
            updateIdentifier(node, remapper.mapClass(declaringClass.getBinaryName()));
        } else {
            String name = remapper.mapMethodName(declaringClass.getBinaryName(), binding.getName(), methodDesc(binding));
            updateIdentifier(node, name);
        }
    }

    private void remapField(SimpleName node, IVariableBinding binding) {
        if (!binding.isField()) {
            if (binding.isParameter()) {
                remapParameter(node, binding);
            } else {
                checkLocalVariable(node, binding);
            }

            return;
        }

        ITypeBinding declaringClass = binding.getDeclaringClass();
        if (declaringClass == null) {
            return;
        }

        String name = remapper.mapFieldName(declaringClass.getBinaryName(), binding.getName(), fieldDesc(binding));
        updateIdentifier(node, name);
    }

    public static String methodDesc(IMethodBinding binding) {
        ITypeBinding[] parameterBindings = binding.getParameterTypes();
        StringBuilder signature = new StringBuilder("(");

        for (ITypeBinding parameterBinding : parameterBindings) {
            if (!parameterBinding.isPrimitive()) {
                signature.append("L");
            }

            signature.append(parameterBinding.getBinaryName().replace(".", "/"));

            if (!parameterBinding.isPrimitive()) {
                signature.append(";");
            }
        }

        signature.append(")");

        if (!binding.getReturnType().isPrimitive()) {
            signature.append("L");
        }

        signature.append(binding.getReturnType().getBinaryName().replace(".", "/"));

        if (!binding.getReturnType().isPrimitive()) {
            signature.append(";");
        }

        return signature.toString();
    }

    public String fieldDesc(IVariableBinding binding) {
        StringBuilder signature = new StringBuilder();

        if (!binding.getType().isPrimitive()) {
            signature.append("L");
        }

        signature.append(binding.getType().getBinaryName().replace(".", "/"));

        if (!binding.getType().isPrimitive()) {
            signature.append(";");
        }

        return signature.toString();
    }

    private void remapParameter(SimpleName node, IVariableBinding binding) {
        IMethodBinding declaringMethod = binding.getDeclaringMethod();
        if (declaringMethod == null) {
            return;
        }

        int index = -1;

        ASTNode n = context.getCompilationUnit().findDeclaringNode(declaringMethod);

        if (n instanceof MethodDeclaration) {
            MethodDeclaration methodDeclaration = (MethodDeclaration) n;

            @SuppressWarnings("unchecked")
            List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();

            for (int i = 0; i < parameters.size(); i++) {
                if (binding.equals(parameters.get(i).resolveBinding())) {
                    index = i;
                }
            }
        }

        if (index == -1) {
            return;
        }

        final ITypeBinding declaringClass = declaringMethod.getDeclaringClass();
        if (declaringClass == null) {
            return;
        }

        String newName = remapper.mapMethodArg(declaringClass.getBinaryName(), declaringMethod.getName(), methodDesc(declaringMethod), index, null);
        if (newName != null) {
            updateIdentifier(node, newName);
        }
    }

    /**
     * Check if a local variable needs to be renamed because it conflicts with a new parameter name. This will also
     * attempt to check cases where local variables are defined in lambda expressions.
     *
     * @param node The local variable node to check
     * @param binding The variable binding corresponding to the local variable name
     */
    private void checkLocalVariable(SimpleName node, IVariableBinding binding) {
        final ASTNode bindingNode = this.context.getCompilationUnit().findDeclaringNode(binding);
        if (this.context.getMercury().isGracefulClasspathChecks() && bindingNode == null) {
            return;
        }

        final String localVariableName = (String) bindingNode.getProperty(LOCAL_VARIABLE_NAME_PROPERTY);
        if (localVariableName != null) {
            updateIdentifier(node, localVariableName);
            return;
        }

        IMethodBinding declaringMethod = binding.getDeclaringMethod();
        if (declaringMethod == null) {
            return;
        }

        if (declaringMethod.getDeclaringMember() != null) {
            // lambda method
            final LambdaExpression lambdaExpr = getLambdaMethodDeclaration(declaringMethod);
            if (lambdaExpr == null) {
                return;
            }

            // Climb out of declaring stack until we find a method which isn't a lambda
            IMethodBinding outerMethod = declaringMethod;
            while (outerMethod.getDeclaringMember() instanceof IMethodBinding) {
                outerMethod = (IMethodBinding) outerMethod.getDeclaringMember();
            }
            if (outerMethod == declaringMethod) {
                // lookup failed, nothing we can do
                return;
            }
            final ASTNode n = this.context.getCompilationUnit().findDeclaringNode(outerMethod);
            if (!(n instanceof MethodDeclaration)) {
                return;
            }
            final MethodDeclaration outerDeclaration = (MethodDeclaration) n;

            ASTNode body = lambdaExpr.getBody();
            // might be an expression
            if (!(body instanceof Block)) {
                body = null;
            }
            this.checkLocalVariableWithMappings(node, bindingNode, outerMethod, outerDeclaration, declaringMethod, (Block) body);
        } else {
            final ASTNode n = context.getCompilationUnit().findDeclaringNode(declaringMethod);
            if (!(n instanceof MethodDeclaration)) {
                return;
            }
            final MethodDeclaration methodDeclaration = (MethodDeclaration) n;

            this.checkLocalVariableWithMappings(node, bindingNode, declaringMethod, methodDeclaration, declaringMethod, methodDeclaration.getBody());
        }
    }

    /**
     * Using the given mappings and bindings, check if there are mappings for the method and if any of them conflict
     * with the given local variable name.
     *
     * @param node The local variable name to check
     * @param bindingNode The binding of the local variable declaration
     * @param binding The binding of the mapped method to check
     * @param declaration The declaration node of the mapped method to check
     * @param blockDeclaringMethod The method binding of the method which defines the {@code block}
     * @param body The method body to check for local variables
     */
    private void checkLocalVariableWithMappings(
            SimpleName node,
            ASTNode bindingNode,
            IMethodBinding binding,
            MethodDeclaration declaration,
            IMethodBinding blockDeclaringMethod,
            Block body
    ) {
        final ITypeBinding declaringClass = binding.getDeclaringClass();
        final TrMethod method = remapper.getMethod(declaringClass.getBinaryName(), binding.getName(), methodDesc(binding));

        if (method == null) {
            return;
        }

        final Set<String> newParamNames = newParamNames(declaration, method);
        checkLocalVariableForConflicts(node, bindingNode, blockDeclaringMethod, body, newParamNames);
    }

    /**
     * Check the method's body defined by {@code methodDeclaration} to collect all local variable names in order to
     * find a suitable replacement name for {@code node} if it clashes with a name in {@code newParamNames}.
     *
     * @param node The local variable node to check
     * @param bindingNode The binding of the local variable declaration
     * @param blockDeclaringMethod The method binding of the method which defines the {@code block}
     * @param block The method body implementation to collect local variable names from
     * @param newParamNames The set of parameter names after mapping
     */
    private void checkLocalVariableForConflicts(
            SimpleName node,
            ASTNode bindingNode,
            IMethodBinding blockDeclaringMethod,
            Block block,
            Set<String> newParamNames
    ) {
        final String name = node.getIdentifier();
        if (!newParamNames.contains(name)) {
            return;
        }

        // the new param name will screw up this local variable
        final Set<String> localVariableNames = collectLocalVariableNames(blockDeclaringMethod, block);
        int counter = 1;
        String newName = name + counter;
        while (localVariableNames.contains(newName) || newParamNames.contains(newName)) {
            counter++;
            newName = name + counter;
        }

        localVariableNames.add(newName);
        bindingNode.setProperty(LOCAL_VARIABLE_NAME_PROPERTY, newName);
        updateIdentifier(node, newName);
    }

    /**
     * Find the declaration of the actual method block for the lambda method
     *
     * @param declaringMethod The method binding for the lambda method to check
     * @return The {@link MethodDeclaration} corresponding to the code block of the lambda implementation
     */
    private LambdaExpression getLambdaMethodDeclaration(IMethodBinding declaringMethod) {
        final ASTNode node = this.context.getCompilationUnit().findDeclaringNode(declaringMethod.getKey());
        if (node instanceof LambdaExpression) {
            return (LambdaExpression) node;
        }
        return null;
    }

    /**
     * Read the method body of {@code methodDeclaration} and return the set of local variable names defined inside of
     * it. The set is cached on the {@code methodDeclaration} so it is only computed once.
     *
     * @param blockDeclaringMethod The method binding of the method which defines the {@code block}
     * @param block The method body implementation to check.
     * @return The set of local variable names defined in the method body.
     */
    private Set<String> collectLocalVariableNames(IMethodBinding blockDeclaringMethod, Block block) {
        if (block == null) {
            return Collections.emptySet();
        }

        Set<String> result = checkProperty(LVT_NAMES_PROPERTY, block);
        if (result != null) {
            return result;
        }
        result = new HashSet<>();
        block.setProperty(LVT_NAMES_PROPERTY, result);

        final IVariableBinding[] synthLocals = blockDeclaringMethod.getSyntheticOuterLocals();
        for (final IVariableBinding synthLocal : synthLocals) {
            final String name = synthLocal.getName();
            if (name.startsWith("val$")) {
                result.add(name.substring(4));
            }
        }

        @SuppressWarnings("unchecked")
        final List<ASTNode> statements = (List<ASTNode>) block.statements();
        for (final ASTNode statement : statements) {
            if (!(statement instanceof VariableDeclaration)) {
                continue;
            }
            final VariableDeclaration declaration = (VariableDeclaration) statement;
            result.add(declaration.getName().getIdentifier());
        }

        return result;
    }

    /**
     * Check the parameter names defined by the {@code methodDeclaration} and apply any mappings based on the given
     * {@code mapping}. Return the list of params post-remap.
     *
     * @param methodDeclaration The method declaration to check the parameter names on.
     * @param method The method to use to determine the new parameter names
     * @return The set of parameter names after remapping them with {@code mapping}.
     */
    private Set<String> newParamNames(MethodDeclaration methodDeclaration, TrMethod method) {
        Set<String> result = checkProperty(NEW_PARAM_NAMES_PROPERTY, methodDeclaration);
        if (result != null) {
            return result;
        }
        result = new HashSet<>();
        methodDeclaration.setProperty(NEW_PARAM_NAMES_PROPERTY, result);

        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
        for (int i = 0; i < parameters.size(); i++) {
            final String paramMapping = remapper.mapMethodArg(method.getOwner().getName(), method.getName(), method.getDesc(), i, parameters.get(i).getName().getIdentifier());
            result.add(paramMapping);
        }

        return result;
    }

    /**
     * Check if the given node contains a {@code Set<String>} property named {@code propName} and return it if so.
     * Returns {@code null} if not.
     *
     * @param propName The name of the property
     * @param node The node to check the property on
     * @return The set stored on the node or {@code null} if empty
     */
    private static Set<String> checkProperty(String propName, ASTNode node) {
        if (node == null) {
            return null;
        }
        final Object value = node.getProperty(propName);
        if (value instanceof Set) {
            @SuppressWarnings("unchecked") final Set<String> result = (Set<String>) value;
            return result;
        }
        return null;
    }

    protected void visit(SimpleName node, IBinding binding) {
        switch (binding.getKind()) {
            case IBinding.METHOD:
                remapMethod(node, ((IMethodBinding) binding).getMethodDeclaration());
                break;
            case IBinding.VARIABLE:
                remapField(node, ((IVariableBinding) binding).getVariableDeclaration());
                break;
        }
    }

    @Override
    public final boolean visit(SimpleName node) {
        IBinding binding = node.resolveBinding();
        if (binding != null) {
            visit(node, binding);
        }
        return false;
    }
}
