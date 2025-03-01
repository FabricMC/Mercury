/*
 * Copyright (c) 2018 Cadix Development (https://www.cadixdev.org)
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package mixin;

import Core;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Core.class)
public class SimpleMixin {
    @Inject(method = "firstName()Ljava/lang/String;", at = @At(value = "INVOKE", target = "LCore;firstName()Ljava/lang/String;"))
    private static void inject(CallbackInfoReturnable<String> cir) {
    }
}
