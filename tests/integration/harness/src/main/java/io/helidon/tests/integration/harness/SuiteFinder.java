/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.tests.integration.harness;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.suite.api.Suite;

/**
 * A {@link TestExecutionListener} that identifies {@link Suite}.
 */
public class SuiteFinder implements TestExecutionListener {

    private static final Map<String, SuiteContext> INFOS = new HashMap<>();

    /**
     * Get a suite info for a test id.
     *
     * @param id test id
     * @return suite info
     */
    public static Optional<SuiteContext> findSuite(String id) {
        return Optional.ofNullable(INFOS.get(id))
                .flatMap(suiteContext -> {
                    String suiteId = suiteContext.suiteId();
                    if (!suiteId.equals(id)) {
                        return Optional.of(INFOS.get(suiteId));
                    }
                    return Optional.of(suiteContext);
                });
    }

    /**
     * Get a suite info for an extension context.
     *
     * @param context extension context
     * @return suite info
     */
    public static Optional<SuiteContext> findSuite(ExtensionContext context) {
        String suiteId = context.getParent().map(ExtensionContext::getUniqueId).orElse("?");
        return findSuite(suiteId);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        Class<?> clazz = suiteClass(testIdentifier);
        String id = testIdentifier.getUniqueId();
        if (clazz != null) {
            INFOS.put(id, new SuiteContext(clazz, id));
        } else {
            testIdentifier.getParentId().ifPresent(parentId -> {
                SuiteContext suiteContext = INFOS.get(parentId);
                if (suiteContext != null) {
                    INFOS.put(id, suiteContext);
                }
            });
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        String id = testIdentifier.getUniqueId();
        SuiteContext suiteContext = INFOS.get(testIdentifier.getUniqueId());
        if (suiteContext != null) {
            if (suiteContext.suiteId().equals(id)) {
                suiteContext.future().toCompletableFuture().complete(null);
            }
        }
    }

    private static Class<?> suiteClass(TestIdentifier testIdentifier) {
        return testIdentifier.getSource()
                .filter(ClassSource.class::isInstance)
                .map(ClassSource.class::cast)
                .map(ClassSource::getJavaClass)
                .filter(source -> source.getAnnotation(Suite.class) != null)
                .orElse(null);
    }
}
