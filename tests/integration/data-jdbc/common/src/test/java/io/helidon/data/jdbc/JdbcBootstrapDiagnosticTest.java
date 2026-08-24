/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;
import io.helidon.data.jdbc.tests.support.SensitiveFailureAssertions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies portable bootstrap configuration diagnostics without activating a datasource.
 */
class JdbcBootstrapDiagnosticTest {
    private static final String SOURCE_NAME = "source";

    /**
     * Proves bootstrap script diagnostics identify the script role, configured
     * source keys, and supported resource keys when more than one source is
     * configured.
     */
    @Test
    void rejectsBootstrapScriptWithMultipleResourceSourceKeys() {
        DataException dropFailure = assertThrows(DataException.class,
                                                 () -> factoryFromConfig(Map.of(
                                                         "data.persistence-units.jdbc.0.drop-script.path",
                                                         "drop.sql",
                                                         "data.persistence-units.jdbc.0.drop-script.resource-path",
                                                         "db/drop.sql"))
                                                         .services());
        DataException initFailure = assertThrows(DataException.class,
                                                 () -> factoryFromConfig(Map.of(
                                                         "data.persistence-units.jdbc.0.init-script.path",
                                                         "init.sql",
                                                         "data.persistence-units.jdbc.0.init-script.content-plain",
                                                         "SELECT 1"))
                                                         .services());

        assertFailureContains(dropFailure,
                              "JDBC persistence unit configuration has invalid value for 'drop-script'.",
                              "Configure exactly one resource source key for 'drop-script'.",
                              "Configured keys are 'path' and 'resource-path'.",
                              "Supported resource keys are 'path', 'resource-path', 'content-plain', and 'content'.");
        assertFailureContains(initFailure,
                              "JDBC persistence unit configuration has invalid value for 'init-script'.",
                              "Configure exactly one resource source key for 'init-script'.",
                              "Configured keys are 'path' and 'content-plain'.",
                              "Supported resource keys are 'path', 'resource-path', 'content-plain', and 'content'.");
    }

    /**
     * Proves bootstrap script diagnostics list supported resource keys when a
     * script configuration does not contain any supported source key.
     */
    @Test
    void rejectsBootstrapScriptWithoutSupportedResourceSourceKey() {
        DataException failure = assertThrows(DataException.class,
                                             () -> factoryFromConfig(Map.of(
                                                     "data.persistence-units.jdbc.0.init-script.unsupported-key",
                                                     "init.sql"))
                                                     .services());

        assertFailureContains(failure,
                              "JDBC persistence unit configuration has invalid value for 'init-script'.",
                              "Configure 'init-script' as a resource object.",
                              "Supported resource keys are 'path', 'resource-path', 'content-plain', and 'content'.");
    }

    /**
     * Proves missing filesystem bootstrap resources are reported as existing
     * file requirements for the exact script resource key.
     *
     * @param directory temporary directory
     */
    @Test
    void reportsMissingFilesystemBootstrapScriptPath(@TempDir Path directory) {
        Path missing = directory.resolve("missing-drop.sql");
        DataException failure = assertThrows(DataException.class,
                                             () -> factoryFromConfig(Map.of(
                                                     "data.persistence-units.jdbc.0.drop-script.path",
                                                     missing.toString()))
                                                     .services());

        assertFailureContains(failure,
                              "JDBC persistence unit configuration could not load the file drop script",
                              "configured by 'drop-script.path'.",
                              "Ensure the filesystem path points to an existing file readable by "
                                      + "the application process.");
        SensitiveFailureAssertions.assertNoSecrets(failure, missing.toString());
    }

    /**
     * Proves missing classpath bootstrap resources are reported as classpath
     * requirements for the exact script resource key.
     */
    @Test
    void reportsMissingClasspathBootstrapScriptResource() {
        String missing = "db/missing-init-script.sql";
        DataException failure = assertThrows(DataException.class,
                                             () -> factoryFromConfig(Map.of(
                                                     "data.persistence-units.jdbc.0.init-script.resource-path",
                                                     missing))
                                                     .services());

        assertFailureContains(failure,
                              "JDBC persistence unit configuration could not load the classpath init script",
                              "configured by 'init-script.resource-path'.",
                              "Ensure the classpath resource exists in the application runtime classpath.");
        SensitiveFailureAssertions.assertNoSecrets(failure, missing);
    }

    /**
     * Proves URI bootstrap resources are rejected with the script role instead
     * of flowing into the common resource factory.
     */
    @Test
    void rejectsUriBootstrapScriptResource() {
        DataException failure = assertThrows(DataException.class,
                                             () -> factoryFromConfig(Map.of(
                                                     "data.persistence-units.jdbc.0.drop-script.uri",
                                                     "file:///private/drop.sql"))
                                                     .services());

        assertFailureContains(failure,
                              "JDBC persistence unit configuration does not support a URI value for 'drop-script'.");
    }

    private static JdbcPersistenceUnitFactory factoryFromConfig(Map<String, String> configValues) {
        Map<String, String> values = new HashMap<>(configValues);
        values.put("data.persistence-units.jdbc.0.data-source", SOURCE_NAME);
        Config config = Config.just(ConfigSources.create(values));
        return new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("Bootstrap validation must precede datasource resolution.");
                },
                () -> config,
                new JdbcTransactionConnectionManager());
    }

    private static void assertFailureContains(Throwable failure, String... expectedMessages) {
        for (String expectedMessage : expectedMessages) {
            assertThat(failure.getMessage(), containsString(expectedMessage));
        }
    }
}
