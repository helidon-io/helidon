/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import java.io.File;
import java.io.IOException;

/**
 * Common functionality for integration tests.
 */
public abstract class AbstractGraphQLIT
        extends AbstractGraphQLTest {

    protected String indexFileName = null;
    protected File indexFile = null;
    protected Context defaultContext;

    private static SeContainer container;

    @BeforeAll
    public static void initialize() {
        container = SeContainerInitializer.newInstance().initialize();
    }

    @AfterAll
    public static void teardown() {
        container.close();
    }

    @BeforeEach
    public void setupTest() throws IOException {
        System.clearProperty(JandexUtils.PROP_INDEX_FILE);
        indexFileName = getTempIndexFile();
        indexFile = null;
        defaultContext = ExecutionContext.getDefaultContext();
    }

    @AfterEach
    public void teardownTest() {
        if (indexFile != null) {
            indexFile.delete();
        }
    }
}
