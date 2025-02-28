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
import org.cadixdev.mercury.RewriteContext;
import org.cadixdev.mercury.SourceRewriter;
import org.cadixdev.mercury.remapper.mixin.MixinRemapperVisitor;

import java.util.Objects;

public final class MercuryRemapper implements SourceRewriter {

    public static SourceRewriter create(TrEnvironment mappings) {
        return new MercuryRemapper(mappings, false, true);
    }

    public static SourceRewriter create(TrEnvironment mappings, boolean javadoc) {
        return new MercuryRemapper(mappings, false, javadoc);
    }

    public static SourceRewriter createSimple(TrEnvironment mappings) {
        return new MercuryRemapper(mappings, true, true);
    }

    public static SourceRewriter createSimple(TrEnvironment mappings, boolean javadoc) {
        return new MercuryRemapper(mappings, true, javadoc);
    }

    private final TrEnvironment trEnvironment;
    private final boolean simple;
    private final boolean javadoc;

    private MercuryRemapper(TrEnvironment trEnvironment, boolean simple, boolean javadoc) {
        this.trEnvironment = Objects.requireNonNull(trEnvironment, "trEnvironment");
        this.simple = simple;
        this.javadoc = javadoc;
    }

    @Override
    public int getFlags() {
        return FLAG_RESOLVE_BINDINGS;
    }

    @Override
    public void rewrite(RewriteContext context) {
        context.getCompilationUnit().accept(this.simple ?
                new SimpleRemapperVisitor(context, this.javadoc, this.trEnvironment) :
                new RemapperVisitor(context, this.javadoc, this.trEnvironment));

        // TODO add an option to turn this on/off
        context.getCompilationUnit().accept(new MixinRemapperVisitor(context, this.trEnvironment));
    }

}
