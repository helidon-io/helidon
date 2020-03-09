/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.ProviderImpl.ChainConfigFilter;
import io.helidon.config.spi.ConfigFilter;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests {@link ChainConfigFilter}.
 */
public class ChainConfigFilterTest {

    @Test
    public void testEmptyConfigFilterList() {
        final String stringValue = "string value";
        ChainConfigFilter chain = new ChainConfigFilter();

        assertThat(chain.apply(ConfigKeyImpl.of("any"), stringValue), is(stringValue));
    }

    @Test
    public void testAddFilterAfterEnablingCache() {
        assertThrows(IllegalStateException.class, () -> {
            ChainConfigFilter chain = new ChainConfigFilter();
            chain.enableCaching();
            chain.addFilter((key, value) -> value);
        });
    }

    @Test
    public void testSingleConfigFilterList() {
        final String originalValue = "string value";
        final String newValue = "new value";

        ConfigFilter filter = (key, stringValue) -> {
            assertThat(stringValue, is(originalValue));
            return newValue;
        };
        ChainConfigFilter chain = new ChainConfigFilter();
        chain.addFilter(filter);

        assertThat(chain.apply(ConfigKeyImpl.of("any"), originalValue), is(newValue));
    }

    @Test
    public void testDoubleConfigFilterList() {
        final String key = "key";
        final String originalValue = "string value";
        final String secondValue = "second value";
        final String lastValue = "the last value";

        ConfigFilter first = (key1, stringValue) -> {
            assertThat(stringValue, is(originalValue));
            return secondValue;
        };
        ConfigFilter second = (key1, stringValue) -> {
            assertThat(stringValue, is(secondValue));
            return lastValue;
        };
        ChainConfigFilter chain = new ChainConfigFilter();
        chain.addFilter(first);
        chain.addFilter(second);

        assertThat(chain.apply(ConfigKeyImpl.of(key), originalValue), is(lastValue));
    }

    @Test
    public void testEmptyConfigFilterListWithConfig() {
        final String key = "app.key1";
        final String originalValue = "string value";
        final String defaultValue = "default value";

        Config config = Config.builder()
                .sources(ConfigSources.create(new HashMap<String,String>() {{
                    put(key, originalValue);
                }}))
                .build();

        assertThat(config.get(key).asString(), is(ConfigValues.simpleValue(originalValue)));
        assertThat(config.get("missing-key").asString().orElse(defaultValue), is(defaultValue));
    }

    @Test
    public void testSingleConfigFilterListWithConfig() {
        final String key = "app.key1";
        final String originalValue = "string value";
        final String newValue = "new value";
        final String defaultValue = "default value";

        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of(key, originalValue)))
                .addFilter(new AssertingFilter.Provider(
                        key,
                        originalValue,
                        () -> newValue))
                .build();

        assertThat(config.get(key).asString(), is(ConfigValues.simpleValue(newValue)));
        assertThat(config.get("missing-key").asString().orElse(defaultValue), is(defaultValue));
    }

    /**
     * The "quad" tests make sure that a sequence of 1, 2, or 3 AssertingFilters
     * yield the correct intermediate and final results.
     * <p>
     * The AssertingFilter maps a particular key to a new value and makes sure that
     * the current value as reported by the Config instance is the old value it was
     * given during set-up. (This checks the order in which the filters are applied.)
     * The class also make sure that during {@code init} the Config reports the
     * expected value resulting from applying <em>all</em> filters, not just
     * the filters that were added before the current one.
     */
    private static final class Quad {
        static final String key = "app.key1";
        static final String originalValue = "string value";
        static final String secondValue = "second value";
        static final String thirdValue = "third value";
        static final String lastValue = "the last value";
        static final String defaultValue = "default value";
        static final String referenceKey = "app.key-reference";
        static final String referenceValue = "$app.key1";

        static final AssertingFilter.Provider firstFilter = new AssertingFilter.Provider(
                        key,
                        originalValue,
                        () -> secondValue);
        static final AssertingFilter.Provider secondFilter = new AssertingFilter.Provider(
                        key,
                        secondValue,
                        () -> thirdValue);
        static final AssertingFilter.Provider thirdFilter = new AssertingFilter.Provider(
                        key,
                        thirdValue,
                        () -> lastValue);
    }

    @Test
    public void testQuadrupleConfigFilterListWithConfig() {
        runQuadTests(true); // with caching
    }

    @Test
    public void testQuadrupleConfigFilterListWithConfigWithoutCache() {
        runQuadTests(false); // without caching
    }

    private void runQuadTests(boolean useCaching) {
        runQuadTest(useCaching, Quad.secondValue, Quad.firstFilter);
        runQuadTest(useCaching, Quad.thirdValue, Quad.firstFilter, Quad.secondFilter);
        runQuadTest(useCaching, Quad.lastValue, Quad.firstFilter, Quad.secondFilter, Quad.thirdFilter);
    }

    @SafeVarargs
    private final void runQuadTest(boolean useCaching, String expectedValue,
            AssertingFilter.Provider... filterProviders) {
        Config.Builder builder = Config.builder()
                .sources(ConfigSources.create(Map.of(
                        Quad.key, Quad.originalValue, Quad.referenceKey, Quad.referenceValue)));
        if (! useCaching) {
            builder.disableCaching();
        }

        AssertingFilter.Provider lastProvider = null;

        for (AssertingFilter.Provider filterProvider : filterProviders) {
            builder.addFilter(filterProvider);
            lastProvider = filterProvider;
        }
        if (lastProvider == null) {
            fail("Attempt to run 'quad' test with no AssertingFilters");
            return; // suppresses warning about possible null dereference in next line
        }

        for (AssertingFilter.Provider filterProvider : filterProviders) {
            filterProvider.setExpectedValueDuringInit(lastProvider.newValue());
        }
        builder.addFilter(ReferenceFilter::new);

        Config config = builder.build();

        assertThat(config.get(Quad.key).asString(), is(ConfigValues.simpleValue(expectedValue)));
        assertThat(config.get(Quad.referenceKey).asString(), is(ConfigValues.simpleValue(expectedValue)));
        assertThat(config.get("missing-key").asString().orElse(Quad.defaultValue), is(Quad.defaultValue));
    }

    @Test
    public void testValueCachedWithConfigCachingEnabled() {
        String key = "app.key1";
        String originalValue = "string value";

        AtomicInteger counter = new AtomicInteger();

        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of(key, originalValue)))
                .addFilter(new AssertingFilter.Provider(
                        key,
                        originalValue,
                        () -> originalValue + ":" + counter.incrementAndGet()))
                .build();

        //first call -> value cached cached
        assertThat(config.get(key).asString(), is(ConfigValues.simpleValue(originalValue + ":1")));
        assertThat(counter.get(), is(1));
        //second call <- used cached value
        assertThat(config.get(key).asString(), is(ConfigValues.simpleValue(originalValue + ":1")));
        assertThat(counter.get(), is(1));
    }

    @Test
    public void testValueCachedWithConfigCachingDisabled() {
        String key = "app.key1";
        String originalValue = "string value";

        AtomicInteger counter = new AtomicInteger();

        Config config = Config.builder()
                .sources(ConfigSources.create(Map.of(key, originalValue)))
                .addFilter(new AssertingFilter.Provider(
                        key,
                        originalValue,
                        () -> originalValue + ":" + counter.incrementAndGet()))
                .disableCaching()
                .build();

        //first call
        assertThat(config.get(key).asString(), is(ConfigValues.simpleValue(originalValue + ":1")));
        assertThat(counter.get(), is(1));
        //second call
        assertThat(config.get(key).asString(), is(ConfigValues.simpleValue(originalValue + ":2")));
        assertThat(counter.get(), is(2));
    }

    class ReferenceFilter implements ConfigFilter {

        private Config configRoot;

        ReferenceFilter(Config configRoot) {
            this.configRoot = configRoot;
        }

        @Override
        public String apply(Config.Key key, String stringValue) {
            if (stringValue.startsWith("$")) {
                String ref = stringValue.substring(1);
                return configRoot.get(ref).asString().get();
            }
            return stringValue;
        }
    }
}
