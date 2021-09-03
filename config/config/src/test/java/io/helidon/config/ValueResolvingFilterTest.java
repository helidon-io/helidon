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

package io.helidon.config;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link ValueResolvingFilter}.
 */
public class ValueResolvingFilterTest {

    @Test
    public void testValueResolving() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "message", "${greeting} ${name}!",
                                "greeting", "Hallo",
                                "name", "Joachim"
                        )))
                .addFilter(ConfigFilters.valueResolving())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        assertThat(config.get("message").asString().get(), is("Hallo Joachim!"));
    }

    @Test
    public void testValueResolvingDottedReference() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "message", "${greeting.german} ${name}!",
                                "greeting.german", "Hallo",
                                "name", "Joachim"
                        )))
                .addFilter(ConfigFilters.valueResolving())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        assertThat(config.get("message").asString().get(), is("Hallo Joachim!"));
    }

    @Test
    public void testValueResolvingTransitive() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "message", "${template}",
                                "template", "${greeting} ${name}!",
                                "name", "Joachim",
                                "greeting", "Hallo"
                        )))
                .addFilter(ConfigFilters.valueResolving())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        assertThat(config.get("message").asString().get(), is("Hallo Joachim!"));
    }

    @Test
    public void testValueResolvingBackslashed() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "message", "${template}",
                                "template", "${greeting} \\${name}!",
                                "name", "Joachim",
                                "greeting", "Hallo"
                        )))
                .addFilter(ConfigFilters.valueResolving())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableValueResolving()
                .build();

        assertThat(config.get("message").asString().get(), is("Hallo ${name}!"));
    }

    @Test
    public void testValueResolvingBackslashIgnored() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "message", "${greeting} \\ ${name}!",
                                "name", "Joachim",
                                "greeting", "Hallo"
                        )))
                .addFilter(ConfigFilters.valueResolving())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        assertThat(config.get("message").asString().get(), is("Hallo \\ Joachim!"));
    }

    @Test
    public void testValueResolvingBackslashIgnored2() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "message", "${template}",
                                "template", "${greeting} \\ ${name}!",
                                "name", "Joachim",
                                "greeting", "Hallo"
                        )))
                .addFilter(ConfigFilters.valueResolving())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        assertThat(config.get("message").asString().get(), is("Hallo \\ Joachim!"));
    }

    private static class LoopTestResult {
        private IllegalStateException ex = null;
        private String message = null;
    }

    @Test
    public void testValueResolvingInfiniteLoop() throws InterruptedException, ExecutionException, TimeoutException {
        // Run the config.get in a FutureTask so the test can time out the get if
        // the recursive lookup was not correctly detected. That way the test
        // explicitly fails rather than hanging the test run as the loop runs and runs.
        final FutureTask<LoopTestResult> shouldNotRecurse =
            new FutureTask<LoopTestResult> ( () -> {
                LoopTestResult result = new LoopTestResult();
                Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "message", "There ${template}",
                                "template", "and back again ${message}"
                        )))
                .addFilter(ConfigFilters.valueResolving())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

                try {
                    // The following should trigger the exception.
                    result.message = config.get("message").asString().get();
                } catch (IllegalStateException ex) {
                    // We expect this.
                    result.ex = ex;
                };
                return result;
            }
            );
        // Either JDK9 or later or the config implementation should prevent
        // the config.get from timing out and should throw an exception instead.
        shouldNotRecurse.run();
        LoopTestResult result = shouldNotRecurse.get(2, TimeUnit.SECONDS);
        assertThat(result.message, nullValue());
        assertThat(result.ex, notNullValue());
        assertThat(result.ex.getMessage(), startsWith("Recursive update"));


    }

    @Test
    public void testValueResolvingMissingReferenceIgnored() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "wrong", "${missing}"
                        )))
                .addFilter(ConfigFilters.valueResolving())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        assertThat(config.get("wrong").asString().get(), is("${missing}"));
    }

    @Test
    public void testValueResolvingMissingReferenceNoFilter() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "wrong", "${missing}"
                        )))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        assertThat(config.get("wrong").asString().get(), is("${missing}"));
    }

    @Test
    public void testValueResolvingMissingReferenceFails() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "wrong", "${missing}"
                        )))
                .addFilter(ConfigFilters.valueResolving()
                                   .failOnMissingReference(true)
                                   .build())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            config.get("wrong").asString().get();
        });
        assertThat(ex.getMessage(), startsWith(String.format(ValueResolvingFilter.MISSING_REFERENCE_ERROR, "wrong")));
        assertThat(ex.getCause(), instanceOf(MissingValueException.class));
    }

    @Test
    public void testValueResolvingMissingReferenceOKViaNoArgsCtor() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "wrong", "${missing}"
                        )))
                .addFilter(new ValueResolvingFilter())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        assertThat(config.get("wrong").asString().get(), is("${missing}"));
    }

    @Test
    public void testValueResolvingMissingReferenceFailsViaNoArgsCtor() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "wrong", "${missing}"
                        )))
                .addFilter(ConfigFilters.valueResolving().failOnMissingReference(true).build())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableFilterServices()
                .build();

        ConfigException ex = assertThrows(ConfigException.class, () -> {
            config.get("wrong").asString().get();
        });
        assertThat(ex.getMessage(), startsWith(String.format(ValueResolvingFilter.MISSING_REFERENCE_ERROR, "wrong")));
        assertThat(ex.getCause(), instanceOf(MissingValueException.class));
    }

    @Test
    public void testValueResolvingMissingReferenceFailsViaServiceLoader() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "wrong", "${missing}",
                                ConfigFilters.ValueResolvingBuilder.FAIL_ON_MISSING_REFERENCE_KEY_NAME, "true"
                        )))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("wrong").asString().asOptional(), is(Optional.of("${missing}")));
    }

    @Test
    public void testValueResolvingMissingReferenceOKViaServiceLoader() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "wrong", "${missing}"
                        )))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("wrong").asString().get(), is("${missing}"));
    }

    @Test
    public void testValueResolvingSatisfiedReferenceViaServiceLoader() {
        Config config = Config.builder(
                ConfigSources.create(
                        Map.of(
                                "correct", "${refc}",
                                "refc", "answer"
                        )))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("correct").asString().get(), is("answer"));
    }
}
