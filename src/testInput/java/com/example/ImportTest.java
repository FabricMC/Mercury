/*
 * Copyright (c) 2018 Cadix Development (https://www.cadixdev.org)
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package example;

import com.example.other.AnotherClass;
import static com.example.pkg.Constants.*;
import com.example.other.OtherClass;

public class ImportTest {

    public void test() {
        var otherClass = new OtherClass();
        var anotherClass = new AnotherClass();
    }

}
