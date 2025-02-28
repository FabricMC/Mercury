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

import net.fabricmc.mappingio.format.srg.JamFileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemappingTests {

    // Mercury contains the following tests:
    // 1. Simple remaps
    //    This test is used to verify that Mercury can remap simple things:
    //      - Mercury can remap simple classes, fields, and methods
    //      - Mercury will remove package declarations when remapping to the
    //        root package (GH-11)
    //      - Mercury will remap Javadoc references (GH-6)
    // 2. Parameter remaps
    //    This test is used to verify that Mercury can remap parameter names:
    //      - In source code
    //      - In Javadoc references
    //      - Within lambda expressions and anonymous classes
    // 3. Method overriding and generics
    //    This test is used to verify that Mercury can handle child classes
    //    overriding methods from their parents:
    //      - Mercury will remap methods with their return type raised (GH-14) (currently disabled)
    //      - Mercury can handle generic return types, and parameters (GH-8) (currently disabled)
    // 4. Eclipse Bugs
    //      - https://bugs.eclipse.org/bugs/show_bug.cgi?id=511958 (currently disabled)
    //      - https://bugs.eclipse.org/bugs/show_bug.cgi?id=564263 (currently disabled)
    // 5. Anonymous class remapping
    //    This test verifies we can handle remapping cases for different anonymous class remapping
    //    combinations (GH-31).
    // 6. Import remapping tests (GH-28)

    @ParameterizedTest
    @ValueSource(strings = {
            // - Test 1
            "Core.java",
            "JavadocTest.java",
            "NameQualifiedTest.java",
            // - Test 2
            // "ParameterTest.java",
            // - Test 3
            // "OverrideChild.java",
            // "OverrideParent.java",
            // - Test 4
            // "eclipse/X.java",
            // "eclipse/Test.java",
            // - Test 5
            "anon/Anon.java",
            // - Test 6
            "net/example/ImportTestNew.java",
            "net/example/newother/AnotherClass.java",
            "net/example/newother/OtherClass.java",
            "net/example/pkg/Util.java",
            // - Test 7
            "com/example/InnerTest.java",
            // - Test 8
            "Bridge.Java"
    })
    void remap(String file) throws Exception {
        final Path tempDir = Files.createTempDirectory("mercury-test");
        final Path in = tempDir.resolve("a");
        final Path out = tempDir.resolve("b");
        Files.createDirectories(in);
        Files.createDirectories(out);

        // Copy our test classes to the temporary directory
        // - Test 1
        this.copy(in, "test/test/Javadocs.java");
        this.copy(in, "test/ObfClass.java");
        this.copy(in, "NonNull.java");
        this.copy(in, "JavadocTest.java");
        this.copy(in, "NameQualifiedTest.java");
        // - Test 2
        this.copy(in, "ParameterTest.java");
        // - Test 3
        //this.copy(in, "OverrideChild.java");
        //this.copy(in, "OverrideParent.java");
        // - Test 4
        //this.copy(in, "eclipse/X.java");
        //this.copy(in, "eclipse/Test.java");
        // - Test 5
        this.copy(in, "anon/Test.java");
        // - Test 6
        this.copy(in, "com/example/ImportTest.java");
        this.copy(in, "com/example/other/AnotherClass.java");
        this.copy(in, "com/example/other/OtherClass.java");
        this.copy(in, "com/example/pkg/Constants.java");
        // - Test 7
        this.copy(in, "com/example/InnerTest.java");
        // - Test 8
        this.copy(in, "Bridge.java");

        // Load our test mappings
        MemoryMappingTree mappingTree = new MemoryMappingTree();

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(RemappingTests.class.getResourceAsStream("/test.jam")))) {
            JamFileReader.read(bufferedReader, mappingTree);
        }

        TinyRemapper tinyRemapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createMappingProvider(mappingTree, "source", "target"))
                .propagateBridges(TinyRemapper.LinkedMethodPropagation.COMPATIBLE)
                .build();

        tinyRemapper.readInputs(Paths.get("build/classes/java/testInput"));

        // Run Mercury
        final Mercury mercury = new Mercury();
        mercury.setSourceCompatibility(JavaCore.VERSION_11);
        mercury.getProcessors().add(MercuryRemapper.create(tinyRemapper.getEnvironment()));
        mercury.setFlexibleAnonymousClassMemberLookups(true);
        mercury.rewrite(in, out);

        // Check that the output is as expected
        // - Test 1
        this.verify(out, file);

        // Delete the directory
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);

        tinyRemapper.finish();
    }

    void copy(final Path dir, final String file) throws IOException {
        final Path path = dir.resolve(file);

        // Make sure the parent directory exists
        Files.createDirectories(path.getParent());

        // Copy the file to the file system
        Files.copy(
                Objects.requireNonNull(RemappingTests.class.getClassLoader().getResourceAsStream(file), file),
                path,
                StandardCopyOption.REPLACE_EXISTING
        );

        // Finally verify the file exists, to prevent issues later on
        assertTrue(Files.exists(path), file + " failed to copy!");
    }

    void verify(final Path dir, final String file) throws IOException {
        final Path path = dir.resolve(file);

        // First check the path exists
        assertTrue(Files.exists(path), path + " doesn't exists!");

        // Check the file matches the expected output
        final String expected;
        try (final InputStream in = RemappingTests.class.getResourceAsStream("/b/" + file)) {
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        final String actual = new String(Files.readAllBytes(path));
        assertEquals(expected, actual, "Remapped code for " + file + " does not match expected");
    }

}
