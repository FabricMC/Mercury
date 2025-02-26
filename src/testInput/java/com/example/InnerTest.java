/*
 * Copyright (c) 2018 Cadix Development (https://www.cadixdev.org)
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package com.example;

public class InnerTest {
    public static class Inner {
        public static class InnerInner {
            public static final Inner instance = new Inner();
        }
    }

    public static Inner inner() {
        return Inner.InnerInner.instance;
    }
}
