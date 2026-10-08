/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.config.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;

public class ComponentRegistryMutationTest {
    @TempDir
    Path tempDir;
    private URLClassLoader loaderWithIndex(String content) throws IOException {
        Path tikaDir = tempDir.resolve("META-INF").resolve("tika");
        Files.createDirectories(tikaDir);
        Files.write(tikaDir.resolve("mutation-test-index.idx"),
            content.getBytes(StandardCharsets.UTF_8));
        return new URLClassLoader(new URL[]{tempDir.toUri().toURL()},
            ComponentRegistryMutationTest.class.getClassLoader());
    }
    @Test
    public void testHasComponentReturnsFalseForUnknownName() throws Exception {
        try (URLClassLoader loader = loaderWithIndex("known=java.lang.String\n")) {
            ComponentRegistry registry = new ComponentRegistry("mutation-test-index", loader);

            assertTrue(registry.hasComponent("known"));
            assertFalse(registry.hasComponent("does-not-exist"));
        }
    }
    @Test
    public void testGetFriendlyName() throws Exception {
        try (URLClassLoader loader = loaderWithIndex(
            "string-comp=java.lang.String\nlist-comp=java.util.ArrayList\n")) {
            ComponentRegistry registry = new ComponentRegistry("mutation-test-index", loader);

            assertEquals("string-comp", registry.getFriendlyName(String.class));
            assertEquals("list-comp", registry.getFriendlyName(ArrayList.class));
            assertNull(registry.getFriendlyName(Integer.class));
        }
    }
    @Test
    public void testDefaultAndKeySuffixes() throws Exception {
        String index = "# a comment line\n"
            + "a=java.lang.String:default\n"
            + "b=java.util.ArrayList\n"
            + "c=java.lang.Integer:key=java.lang.Number\n"
            + "d=java.lang.Long:key=java.lang.Number:default\n";
        try (URLClassLoader loader = loaderWithIndex(index)) {
            ComponentRegistry registry = new ComponentRegistry("mutation-test-index", loader);

            Map<String, ComponentInfo> defaults = registry.getDefaultComponents();
            assertEquals(Set.of("a", "d"), defaults.keySet());
            assertThrows(UnsupportedOperationException.class,
                () -> defaults.put("x", defaults.get("a")));

            Map<String, ComponentInfo> all = registry.getAllComponents();
            assertEquals(4, all.size());

            assertTrue(all.get("a").isDefault());
            assertFalse(all.get("b").isDefault());
            assertFalse(all.get("c").isDefault());
            assertTrue(all.get("d").isDefault());

            assertEquals(String.class, all.get("a").componentClass());

            assertNull(all.get("a").contextKey());
            assertNull(all.get("b").contextKey());
            assertEquals(Number.class, all.get("c").contextKey());
            assertEquals(Number.class, all.get("d").contextKey());
        }
    }
    @Test
    public void testInvalidSuffixReportsLineNumber() throws Exception {
        String index = "# a comment\n"
            + "valid=java.lang.String\n"
            + "bad=java.lang.Integer:bogus\n";
        try (URLClassLoader loader = loaderWithIndex(index)) {
            TikaConfigException e = assertThrows(TikaConfigException.class,
                () -> new ComponentRegistry("mutation-test-index", loader));

            assertTrue(e.getMessage().contains("line 3: unknown suffix 'bogus'"),
                "Unexpected message: " + e.getMessage());
        }
    }
}
