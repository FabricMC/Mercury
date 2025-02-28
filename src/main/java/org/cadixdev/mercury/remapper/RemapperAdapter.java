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

import net.fabricmc.tinyremapper.api.TrClass;
import net.fabricmc.tinyremapper.api.TrEnvironment;
import net.fabricmc.tinyremapper.api.TrMethod;
import net.fabricmc.tinyremapper.api.TrRemapper;
import org.jetbrains.annotations.Nullable;

public record RemapperAdapter(TrEnvironment trEnvironment) {
    public TrRemapper remapper() {
        return trEnvironment.getRemapper();
    }

    @Nullable
    public TrClass getClass(String name) {
        return trEnvironment.getClass(name.replace('.', '/'));
    }

    @Nullable
    public TrMethod getMethod(String owner, String name, String desc) {
        TrClass trClass = getClass(owner);

        if (trClass == null) {
            return null;
        }

        return trClass.resolveMethod(name, desc);
    }

    public String mapClass(String name) {
        return remapper().map(name.replace(".", "/")).replace("/", ".");
    }

    public String mapMethodName(final String owner, final String name, final String descriptor) {
        return remapper().mapMethodName(owner.replace(".", "/"), name, descriptor);
    }

    public String mapFieldName(final String owner, final String name, final String descriptor) {
        return remapper().mapFieldName(owner.replace(".", "/"), name, descriptor);
    }

    public String mapMethodArg(String methodOwner, String methodName, String methodDesc, int lvIndex, String name) {
        return remapper().mapMethodArg(methodOwner.replace(".", "/"), methodName, methodDesc, lvIndex, name);
    }

    // Returns the package of the class, e.g "com.example"
    public String getDeobfuscatedPackage(TrClass trClass) {
        String fullName = mapClass(trClass.getName());
        if (fullName.indexOf('.') == -1) {
            return "";
        }
        return fullName.substring(0, fullName.lastIndexOf('.'));
    }

    public String mapSimpleDeobfuscatedName(String name) {
        TrClass trClass = getClass(name);

        if (trClass == null) {
            return toSimpleName(name);
        }

        return getSimpleDeobfuscatedName(trClass);
    }

    // Returns the name of the class without the package or without the outer class
    public String getSimpleDeobfuscatedName(TrClass trClass) {
        String name = mapClass(trClass.getName());
        return toSimpleName(name);
    }

    private String toSimpleName(String name) {
        String fullName = name.substring(name.lastIndexOf('.') + 1);
        return fullName.contains("$") ? fullName.substring(fullName.lastIndexOf('$') + 1) : fullName;
    }

    public String getFullDeobfuscatedName(TrClass trClass) {
        return mapClass(trClass.getName()).replace('/', '.');
    }

    public String getSimpleObfuscatedName(TrClass trClass) {
        String fullName = trClass.getName();
        return fullName.substring(fullName.lastIndexOf('/') + 1);
    }
}
