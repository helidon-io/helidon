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

import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.helidon.config.spi.ConfigNode.ObjectNode;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.config.ConfigTest.waitForAssert;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests {@link Config.Context} and {@link Config#context()}.
 */
public class ConfigContextTest {

    private static final String PROP1 = "prop1";
    private static final int TEST_DELAY_MS = 1;

    private static class TestContext {

        private final Config config;
        private final String key;
        private final String oldValue;
        private final String newValue;
        private final TestingConfigSource configSource;

        private TestContext(Map.Entry<String, String> entry) {
            String detachKey = entry.getKey();
            key = entry.getValue();

            int i = ((detachKey == null) || detachKey.isEmpty()) ? 1 : 2;
            int j = key.isEmpty() ? 1 : 2;
            oldValue = "oldVal_" + i + "_" + j;
            newValue = "newVal_" + i + "_" + j;

            configSource = TestingConfigSource.builder()
                    .objectNode(createSource("old"))
                    .testingPollingStrategy()
                    .build();

            Config cfg = Config.builder()
                    .sources(configSource)
                    .disableEnvironmentVariablesSource()
                    .disableSystemPropertiesSource()
                    .build();

            if (detachKey != null) {
                cfg = cfg.get(detachKey).detach();
            }
            config = cfg.get(key);
        }

        private void changeSource(String valuePrefix) throws InterruptedException {
            TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp will change.
            configSource.changeLoadedObjectNode(createSource(valuePrefix));
        }
    }
    
        public static Stream<Map.Entry<String,String>> initParams() {
        return Stream.of(
                new SimpleEntry<>(null, ""),
                new SimpleEntry<>(null, "sub"),
                new SimpleEntry<>("", ""),
                new SimpleEntry<>("", "sub"),
                new SimpleEntry<>("prefix", ""),
                new SimpleEntry<>("prefix", "sub")
        );
    }

    private static ObjectNode createSource(String valuePrefix) {
        ObjectNode.Builder builder = ObjectNode.builder();
        int i = 0;
        for (String detachKey : List.of("", "prefix")) {
            i++;
            int j = 0;
            for (String key : List.of("", "sub")) {
                j++;
                String realKey = concatKeys(concatKeys(detachKey, key), PROP1);
                builder.addValue(realKey, valuePrefix + "Val_" + i + "_" + j);
            }
        }
        return builder.build();
    }

    private static String concatKeys(String prefix, String suffix) {
        if (prefix.isEmpty()) {
            return suffix;
        } else {
            return suffix.isEmpty() ? prefix : (prefix + "." + suffix);
        }
    }

    @ParameterizedTest(name = "{index}: detachKey= {0}, key: {1}")
    @MethodSource("initParams")
    public void testContextReloadNoPollingSourceSame(Map.Entry<String,String> e) throws InterruptedException {
        TestContext c = new TestContext(e);
        // subscribe on changes
        ConfigChangeListener listener = new ConfigChangeListener();
        c.config.onChange(listener::onChange);

        // config contains old data
        assertThat(c.config.get(PROP1).asString().get(), is(c.oldValue));

        // RELOAD config
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure the timestamp changes.
        Config reloaded = c.config.context().reload();

        // new config -> new timestamp
        assertThat(reloaded.timestamp(), greaterThan(c.config.timestamp()));
        // config same -> old data
        assertThat(reloaded.get(PROP1).asString(), is(ConfigValues.simpleValue(c.oldValue)));
        // context references the last reloaded config
        assertConfigIsTheLast(c.config.context(), reloaded);

        // no other events
        assertThat(listener.get(500, false), is(nullValue()));
    }

    private static void assertConfigIsTheLast(Config.Context context, Config config) {
        assertThat(context.timestamp(), is(config.timestamp()));
        assertThat(context.last(), is(config));
        assertThat(context.last().key(), is(config.key()));
    }

    @ParameterizedTest(name = "{index}: detachKey= {0}, key: {1}")
    @MethodSource("initParams")
    public void testContextReloadNoPollingSourceChanged(Map.Entry<String,String> e) throws InterruptedException {
        TestContext c = new TestContext(e);
        // subscribe on changes
        ConfigChangeListener listener = new ConfigChangeListener();
        c.config.onChange(listener::onChange);

        // config contains old data
        assertThat(c.config.get(PROP1).asString().get(), is(c.oldValue));

        // CHANGE source
        c.changeSource("new");

        // RELOAD config
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure the timestamp changes.
        // wait until the change is propagated
        listener.get(500, true);
        Config reloaded = c.config.context().reload();

        // new config -> new timestamp
        assertThat(reloaded.timestamp(), greaterThan(c.config.timestamp()));
        // config same -> new data
        assertThat(reloaded.get(PROP1).asString(), is(ConfigValues.simpleValue(c.newValue)));
        // context references the last reloaded config
        assertConfigIsTheLast(c.config.context(), reloaded);

        // change event
        Config last1 = listener.get(500, true);
        assertThat(last1.key().toString(), is(c.key));
        assertThat(last1.get(PROP1).asString(), is(ConfigValues.simpleValue(c.newValue)));
    }

    @ParameterizedTest(name = "{index}: detachKey= {0}, key: {1}")
    @MethodSource("initParams")
    public void testWithPollingSourceSame(Map.Entry<String,String> e
        ) throws InterruptedException {
        TestContext c = new TestContext(e);
        // subscribe on changes
        ConfigChangeListener listener = new ConfigChangeListener();
        c.config.onChange(listener::onChange);

        // config contains old data
        assertThat(c.config.get(PROP1).asString().get(), is(c.oldValue));

        // CHANGE source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure timestamp changes.
        c.changeSource("old");

        // no other events
        assertThat(listener.get(500, false), is(nullValue()));

        // context references the last reloaded config
        assertConfigIsTheLast(c.config.context(), c.config);
    }

    @ParameterizedTest(name = "{index}: detachKey= {0}, key: {1}")
    @MethodSource("initParams")
    public void testWithPollingSourceChanged(Map.Entry<String,String> e) throws InterruptedException {
        TestContext c = new TestContext(e);// subscribe on changes
        ConfigChangeListener listener = new ConfigChangeListener();
        c.config.onChange(listener::onChange);

        // config contains old data
        assertThat(c.config.get(PROP1).asString().get(), is(c.oldValue));

        // CHANGE source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure time changes to trigger notification.
        c.changeSource("new");

        // change event
        Config last1 = listener.get(500, true);
        assertThat(last1.key().toString(), is(c.key));
        assertThat(last1.get(PROP1).asString(), is(ConfigValues.simpleValue(c.newValue)));

        // new config -> new timestamp
        assertThat(last1.timestamp(), greaterThan(c.config.timestamp()));
        // config same -> new data
        assertThat(last1.get(PROP1).asString(), is(ConfigValues.simpleValue(c.newValue)));
        // context references the last reloaded config
        assertConfigIsTheLast(c.config.context(), last1);
    }

    @ParameterizedTest(name = "{index}: detachKey= {0}, key: {1}")
    @MethodSource("initParams")
    public void testContextLastWithSourceChanged(Map.Entry<String,String> e) throws InterruptedException {
        TestContext c = new TestContext(e);// subscribe on changes
        // config contains old data
        assertThat(c.config.get(PROP1).asString().get(), is(c.oldValue));

        // CHANGE source
        TimeUnit.MILLISECONDS.sleep(TEST_DELAY_MS); // Make sure time changes to trigger notification.
        c.changeSource("new");

        //wait for a new configuration is loaded
        waitForAssert(() -> c.config.context().last().get(PROP1).asString().asOptional(), is(ConfigValues.simpleValue(c.newValue).asOptional()));

        Config last1 = c.config.context().last();

        assertThat(last1.key().toString(), is(c.key));
        assertThat(last1.get(PROP1).asString(), is(ConfigValues.simpleValue(c.newValue)));

        // new config -> new timestamp
        assertThat(last1.timestamp(), greaterThan(c.config.timestamp()));
        // config same -> new data
        assertThat(last1.get(PROP1).asString(), is(ConfigValues.simpleValue(c.newValue)));
        // context references the last reloaded config
        assertConfigIsTheLast(c.config.context(), last1);
    }

    @ParameterizedTest(name = "{index}: detachKey= {0}, key: {1}")
    @MethodSource("initParams")
    public void testContextCurrentData(Map.Entry<String,String> e) {
        TestContext c = new TestContext(e);// subscribe on changes
        assertThat(c.config.context().timestamp(), is(c.config.timestamp()));
        assertThat(c.config.context().last(), is(c.config));
        assertThat(c.config.context().last().key(), is(c.config.key()));
    }

}
