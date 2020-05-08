/*
 * Copyright (c) 2018 Minecrell (https://github.com/Minecrell)
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

class OverrideChild extends OverrideParent<String> {

    @Override
    public String get() {
        return "Hello, World!";
    }

    @Override
    public String fetch() {
        return "Hello, World!";
    }

    @Override
    public void set(final String abc) {
    }

}
