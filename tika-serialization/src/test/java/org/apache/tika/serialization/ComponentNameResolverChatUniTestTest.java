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
package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.ComponentInfo;
import org.apache.tika.config.loader.ComponentRegistry;
import org.apache.tika.config.loader.ConfigurableTestParser;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;

public class ComponentNameResolverChatUniTestTest {

    @Test
    public void parserUsesCompactFormat() {
        assertTrue(ComponentNameResolver.usesCompactFormat(Parser.class));
    }

    @Test
    public void unrelatedClassDoesNotUseCompactFormat() {
        assertFalse(ComponentNameResolver.usesCompactFormat(String.class));
    }

    @Test
    public void nullTypeIsRejected() {
        assertThrows(NullPointerException.class,
            () -> ComponentNameResolver.usesCompactFormat(null));
    }
    /**
     * Verifies that the unregistered-component message matches the current registry state.
     */
    @Test
    public void unregisteredMessageMatchesRegistryState() {
        boolean knownComponentIsRegistered =
            ComponentNameResolver.hasComponent("configurable-test-parser");

        ClassNotFoundException exception = assertThrows(ClassNotFoundException.class,
            () -> ComponentNameResolver.resolveClass(
                "unknown-component-for-mutation-test",
                getClass().getClassLoader()));

        String message = exception.getMessage();

        if (knownComponentIsRegistered) {
            assertTrue(message.contains("registered component(s):"), message);
            assertTrue(message.contains("configurable-test-parser"), message);
            assertFalse(message.contains("No components are currently registered"), message);
        } else {
            assertTrue(message.contains("No components are currently registered"), message);
            assertTrue(message.contains("no META-INF/tika/*.idx files were found"), message);
            assertFalse(message.contains("0 registered component(s):"), message);
        }
    }

    /**
     * Verifies the three documented context-key resolution strategies: explicit key,
     * interface auto-detection and fallback to the component class.
     */
    @Test
    public void determineContextKeyUsesDocumentedResolutionOrder() {
        ComponentInfo explicit =
            new ComponentInfo(String.class, false, MetadataFilter.class);
        ComponentInfo autoDetected =
            new ComponentInfo(ParserDecorator.class, false);
        ComponentInfo fallback =
            new ComponentInfo(String.class, false);

        assertEquals(
            MetadataFilter.class,
            ComponentNameResolver.determineContextKey(explicit));
        assertEquals(
            Parser.class,
            ComponentNameResolver.determineContextKey(autoDetected));
        assertEquals(
            String.class,
            ComponentNameResolver.determineContextKey(fallback));
    }

    /**
     * Verifies that safe wire context keys are allow-listed while parser execution
     * remains blocked.
     */
    @Test
    public void wireContextKeysRespectSecurityClassification() {
        assertTrue(ComponentNameResolver.isWireInstantiable(MetadataFilter.class));
        assertFalse(ComponentNameResolver.isWireInstantiable(Parser.class));

        assertFalse(ComponentNameResolver.isWireBlocked(MetadataFilter.class));
        assertTrue(ComponentNameResolver.isWireBlocked(Parser.class));
        assertFalse(ComponentNameResolver.isWireBlocked(String.class));
    }

    /**
     * Verifies that the public context-key sets expose the expected classifications.
     */
    @Test
    public void contextKeySetsExposeExpectedEntries() {
        assertTrue(ComponentNameResolver.getContextKeyInterfaces().contains(Parser.class));
        assertTrue(ComponentNameResolver
            .getWireInstantiableContextKeys()
            .contains(MetadataFilter.class));
        assertTrue(ComponentNameResolver
            .getWireBlockedContextKeys()
            .contains(Parser.class));
    }

    @Test
    public void registeredComponentsCanBeQueried() throws Exception {
        ComponentRegistry registry =
            new ComponentRegistry("parsers", getClass().getClassLoader());
        ComponentNameResolver.registerRegistry("chatunitest-parsers", registry);

        assertTrue(ComponentNameResolver.hasComponent("configurable-test-parser"));
        assertFalse(ComponentNameResolver.hasComponent(
            "unknown-component-for-registry-test"));

        assertTrue(ComponentNameResolver
            .getComponentInfo("configurable-test-parser")
            .isPresent());
        assertTrue(ComponentNameResolver
            .getComponentInfo("unknown-component-for-registry-test")
            .isEmpty());

        assertTrue(ComponentNameResolver.hasImplementationsOf(Parser.class));
        assertFalse(ComponentNameResolver.hasImplementationsOf(String.class));

        assertEquals(Parser.class,
            ComponentNameResolver.getContextKey(ConfigurableTestParser.class));
        assertNull(ComponentNameResolver.getContextKey(String.class));
    }

    @Test
    public void registeredComponentMessageUsesCorrectSeparators() throws Exception {
        ComponentRegistry registry =
            new ComponentRegistry("parsers", getClass().getClassLoader());
        ComponentNameResolver.registerRegistry("chatunitest-message-parsers", registry);

        ClassNotFoundException exception = assertThrows(ClassNotFoundException.class,
            () -> ComponentNameResolver.resolveClass(
                "unknown-component-for-list-format-test",
                getClass().getClassLoader()));

        String message = exception.getMessage();
        String marker = "registered component(s): ";
        int listStart = message.indexOf(marker);
        int listEnd = message.indexOf(". Arbitrary class names", listStart);

        assertTrue(listStart >= 0, message);
        assertTrue(listEnd > listStart, message);

        String componentList =
            message.substring(listStart + marker.length(), listEnd);

        assertFalse(componentList.startsWith(","), message);
        assertTrue(componentList.contains(", "), message);
        assertTrue(componentList.contains("configurable-test-parser"), message);
    }
}
