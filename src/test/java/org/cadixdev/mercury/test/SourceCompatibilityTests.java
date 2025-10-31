/*
 * Copyright (c) 2018 Cadix Development (https://www.cadixdev.org)
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.cadixdev.mercury.test;

import org.cadixdev.mercury.Mercury;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SourceCompatibilityTests {
	@Test
	void setSourceCompatibilityFromRelease() {
		Mercury mercury = new Mercury();

		mercury.setSourceCompatibilityFromRelease(6);
		Assertions.assertEquals(JavaCore.VERSION_1_6, mercury.getSourceCompatibility());

		mercury.setSourceCompatibilityFromRelease(8);
		Assertions.assertEquals(JavaCore.VERSION_1_8, mercury.getSourceCompatibility());

		mercury.setSourceCompatibilityFromRelease(11);
		Assertions.assertEquals(JavaCore.VERSION_11, mercury.getSourceCompatibility());

		mercury.setSourceCompatibilityFromRelease(17);
		Assertions.assertEquals(JavaCore.VERSION_17, mercury.getSourceCompatibility());

		mercury.setSourceCompatibilityFromRelease(99);
		Assertions.assertEquals(JavaCore.VERSION_24, mercury.getSourceCompatibility());
	}
}
