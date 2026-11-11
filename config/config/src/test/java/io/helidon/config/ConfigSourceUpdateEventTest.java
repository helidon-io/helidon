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
package io.helidon.config;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.spi.ChangeEventType;
import io.helidon.config.spi.ConfigContent.NodeContent;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;

import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.config.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigSourceUpdateEventTest {
    private static final String EVENT_NAME = "io.helidon.ConfigSourceUpdate";
    private static final String SECRET = "secret-key-value-location-message";

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void comparesRawReloadsWhenInitialLoadMapsNodes(boolean parserMapping) throws Exception {
        var initial = ObjectNode.builder().addValue("mapped", "key=initial").build();
        var changed = ObjectNode.builder().addValue("mapped", "key=changed").build();
        var builder = TestingConfigSource.builder().objectNode(initial).testingPollingStrategy();
        if (parserMapping) {
            builder.parserMapping(key -> key.name().equals("mapped")
                    ? Optional.of(ConfigParsers.properties()) : Optional.empty());
        } else {
            builder.mediaTypeMapping(key -> key.name().equals("mapped")
                    ? Optional.of(MediaTypes.TEXT_PROPERTIES) : Optional.empty());
        }
        var source = builder.build();
        var runtime = new ConfigSourceRuntimeImpl(new BuilderImpl.ConfigContextImpl(Runnable::run,
                                                                                   List.of(ConfigParsers.properties())),
                                                  source);
        try (var recording = new Recording()) {
            recording.enable(EVENT_NAME);
            recording.start();
            var loaded = runtime.load().orElseThrow();
            assertThat(((ObjectNode) loaded.get("mapped")).get("key"), valueNode("initial"));
            var notifications = new ArrayList<ObjectNode>();
            runtime.onChange((_, node) -> notifications.add((ObjectNode) node));
            source.changeLoadedObjectNode(ObjectNode.builder().addValue("mapped", "key=initial").build());
            source.changeLoadedObjectNode(changed);
            source.changeLoadedObjectNode(ObjectNode.builder().addValue("mapped", "key=changed").build());
            runtime.stopChangeSupport();

            assertThat(notifications.size(), is(3));
            assertThat(notifications.get(0).get("mapped"), valueNode("key=initial"));
            assertThat(notifications.get(1).get("mapped"), valueNode("key=changed"));
            assertThat(notifications.get(2).get("mapped"), valueNode("key=changed"));
            assertThat(events(recording, TestingConfigSource.class).stream().map(it -> it.getString("outcome")).toList(),
                       contains("INITIAL", "CHANGED"));
        } finally {
            runtime.stopChangeSupport();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void observesInitialMappingFailureAndRecovery(boolean parserMapping) throws Exception {
        var failMapping = new AtomicBoolean(true);
        var builder = TestingConfigSource.builder()
                .objectNode(ObjectNode.builder().addValue("mapped", "key=initial").build());
        if (parserMapping) {
            builder.parserMapping(key -> {
                if (failMapping.get()) {
                    throw new ConfigException(SECRET);
                }
                return key.name().equals("mapped") ? Optional.of(ConfigParsers.properties()) : Optional.empty();
            });
        } else {
            builder.mediaTypeMapping(key -> {
                if (failMapping.get()) {
                    throw new ConfigException(SECRET);
                }
                return key.name().equals("mapped") ? Optional.of(MediaTypes.TEXT_PROPERTIES) : Optional.empty();
            });
        }
        var source = builder.build();
        var runtime = new ConfigSourceRuntimeImpl(new BuilderImpl.ConfigContextImpl(Runnable::run,
                                                                                   List.of(ConfigParsers.properties())),
                                                  source);
        try (var recording = new Recording()) {
            recording.enable(EVENT_NAME);
            recording.start();
            assertThrows(ConfigException.class, runtime::load);
            assertThrows(ConfigException.class, runtime::load);
            failMapping.set(false);
            var loaded = runtime.load().orElseThrow();
            assertThat(((ObjectNode) loaded.get("mapped")).get("key"), valueNode("initial"));

            var events = events(recording, TestingConfigSource.class);
            assertThat(events.stream().map(it -> it.getString("outcome")).toList(), contains("FAILED", "RECOVERED"));
            assertThat(events.getFirst().getString("exceptionClass"), is(ConfigException.class.getName()));
            for (var event : events) {
                assertThat(event.toString().contains(SECRET), is(false));
            }
        }
    }

    @Test
    void observesCompletedLoadsAndSuppressesNoopsAndRepeatedFailures() throws Exception {
        var source = new TestSource(true);
        var runtime = runtime(source);
        try (var recording = new Recording()) {
            recording.enable(EVENT_NAME);
            recording.start();
            runtime.load();
            runtime.onChange((_, _) -> { });
            source.poll(); // unchanged stamp
            source.modified = true;
            source.data = data("initial");
            source.poll(); // equal content despite a changed stamp
            source.data = data("second");
            source.poll();
            source.failure = new ConfigException(SECRET);
            assertThrows(ConfigException.class, source::poll);
            assertThrows(ConfigException.class, source::poll);
            source.modified = false;
            source.poll(); // a successful stamp check does not prove that a failed load recovered
            source.modified = true;
            source.failure = null;
            source.poll(); // recovery with the same content
            source.data = Optional.empty();
            source.poll();
            source.poll(); // repeated deletion
            source.data = data("third");
            source.poll();
            runtime.stopChangeSupport();

            var events = events(recording);
            assertThat(events.stream().map(it -> it.getString("outcome")).toList(),
                       contains("INITIAL", "CHANGED", "FAILED", "RECOVERED", "DELETED", "CHANGED"));
            assertThat(events.get(2).getString("exceptionClass"), is(ConfigException.class.getName()));
            for (var event : events) {
                assertThat(event.getString("componentName"), nullValue());
                assertThat(event.getString("sourceClass"), is(TestSource.class.getName()));
                assertThat(event.getStackTrace(), nullValue());
                assertThat(event.toString().contains(SECRET), is(false));
            }
            assertThat(source.stopped, is(true));
        } finally {
            runtime.stopChangeSupport();
        }
    }

    @Test
    void comparesWideObjectsRegardlessOfMemberOrderWithoutSuppressingNotifications() throws Exception {
        var source = new TestSource(true);
        source.data = wideData(false, "key-1023", "original");
        var runtime = runtime(source);
        try (var recording = new Recording()) {
            recording.enable(EVENT_NAME);
            recording.start();
            runtime.load();
            var notifications = new ArrayList<ObjectNode>();
            runtime.onChange((_, node) -> notifications.add((ObjectNode) node));
            source.modified = true;
            source.data = wideData(true, "key-1023", "original");
            source.poll();
            source.data = wideData(true, "key-1023", "changed");
            source.poll();
            source.data = wideData(false, "key-1023", "changed");
            source.poll();
            source.data = wideData(false, "replacement", "changed");
            source.poll();
            runtime.stopChangeSupport();

            assertThat(notifications.size(), is(4));
            assertThat(events(recording).stream().map(it -> it.getString("outcome")).toList(),
                       contains("INITIAL", "CHANGED", "CHANGED"));
        } finally {
            runtime.stopChangeSupport();
        }
    }

    @Test
    void observesMandatoryAbsenceAndRecoveryWithoutChangingNotifications() throws Exception {
        var source = new TestSource(false);
        var runtime = runtime(source);
        try (var recording = new Recording()) {
            recording.enable(EVENT_NAME);
            recording.start();
            runtime.load();
            var notifications = new ArrayList<ObjectNode>();
            runtime.onChange((_, node) -> notifications.add((ObjectNode) node));
            source.modified = true;
            source.data = Optional.empty();
            assertThat(source.poll(), is(ChangeEventType.DELETED));
            source.poll();
            assertThat(notifications, empty());
            source.data = data("initial");
            source.poll();
            assertThat(notifications.size(), is(1));
            runtime.stopChangeSupport();
            assertThat(events(recording).stream().map(it -> it.getString("outcome")).toList(),
                       contains("INITIAL", "UNAVAILABLE", "RECOVERED"));
        } finally {
            runtime.stopChangeSupport();
        }
    }

    @Test
    void observesFailedInitialLoadAndMandatoryMissingInitialLoad() throws Exception {
        var source = new TestSource(false);
        source.failure = new ConfigException(SECRET);
        var runtime = runtime(source);
        try (var recording = new Recording()) {
            recording.enable(EVENT_NAME);
            recording.start();
            assertThrows(ConfigException.class, runtime::load);
            assertThrows(ConfigException.class, runtime::load);
            source.failure = null;
            source.data = Optional.empty();
            assertThrows(ConfigException.class, runtime::load);
            source.data = data("restored");
            runtime.load();
            assertThat(events(recording).stream().map(it -> it.getString("outcome")).toList(),
                       contains("FAILED", "UNAVAILABLE", "RECOVERED"));
        }
    }

    @Test
    void observesPollCheckFailureAndUnchangedRecovery() throws Exception {
        var source = new TestSource(true);
        var runtime = runtime(source);
        try (var recording = new Recording()) {
            recording.enable(EVENT_NAME);
            recording.start();
            runtime.load();
            runtime.onChange((_, _) -> { });
            source.checkFailure = new ConfigException(SECRET);
            assertThrows(ConfigException.class, source::poll);
            assertThrows(ConfigException.class, source::poll);
            source.checkFailure = null;
            assertThat(source.poll(), is(ChangeEventType.UNCHANGED));
            runtime.stopChangeSupport();
            assertThat(events(recording).stream().map(it -> it.getString("outcome")).toList(),
                       contains("INITIAL", "FAILED", "RECOVERED"));
        } finally {
            runtime.stopChangeSupport();
        }
    }

    @Test
    void enablingAfterInitialLoadRetainsObservationState() throws Exception {
        var source = new TestSource(true);
        var runtime = runtime(source);
        try (var recording = new Recording()) {
            recording.disable(EVENT_NAME);
            recording.start();
            runtime.load();
            runtime.onChange((_, _) -> { });
            source.modified = true;
            source.data = data("changed-while-disabled");
            source.poll();
            recording.enable(EVENT_NAME);
            source.data = data("changed-while-disabled");
            source.poll(); // same contents as the last disabled observation
            source.data = data("changed-while-enabled");
            source.poll();
            runtime.stopChangeSupport();
            assertThat(events(recording).stream().map(it -> it.getString("outcome")).toList(), contains("CHANGED"));
        } finally {
            runtime.stopChangeSupport();
        }
    }

    @Test
    void enablingAfterDisabledFailureObservesRecovery() throws Exception {
        var source = new TestSource(true);
        var runtime = runtime(source);
        try (var recording = new Recording()) {
            recording.disable(EVENT_NAME);
            recording.start();
            runtime.load();
            runtime.onChange((_, _) -> { });
            source.modified = true;
            source.failure = new ConfigException(SECRET);
            assertThrows(ConfigException.class, source::poll);
            recording.enable(EVENT_NAME);
            source.failure = null;
            source.poll();
            runtime.stopChangeSupport();
            assertThat(events(recording).stream().map(it -> it.getString("outcome")).toList(), contains("RECOVERED"));
        } finally {
            runtime.stopChangeSupport();
        }
    }

    @Test
    void exposesDefaultsAndHonorsDisabledRecordingSetting() throws Exception {
        var type = EventType.getEventType(ConfigSourceUpdateEvent.class);
        assertThat(type.getName(), is(EVENT_NAME));
        assertThat(type.getCategoryNames(), contains("Helidon", "Configuration"));
        assertThat(type.getSettingDescriptors().stream()
                           .filter(it -> it.getName().equals("threshold"))
                           .map(it -> it.getDefaultValue()).toList(), contains("0 ms"));
        assertThat(type.getSettingDescriptors().stream()
                           .filter(it -> it.getName().equals("enabled"))
                           .map(it -> it.getDefaultValue()).toList(), contains("true"));
        try (var recording = new Recording()) {
            recording.disable(EVENT_NAME);
            recording.start();
            runtime(new TestSource(true)).load();
            assertThat(events(recording), empty());
        }
    }

    private static Optional<NodeContent> data(String value) {
        return Optional.of(NodeContent.builder()
                                   .node(ObjectNode.builder().addValue(SECRET, value + SECRET).build())
                                   .stamp(value)
                                   .build());
    }

    private static Optional<NodeContent> wideData(boolean reverse, String lastKey, String lastValue) {
        var node = ObjectNode.builder();
        for (int i = 0; i < 1024; i++) {
            int index = reverse ? 1023 - i : i;
            node.addValue(index == 1023 ? lastKey : "key-" + index,
                          index == 1023 ? lastValue : "value-" + index);
        }
        return Optional.of(NodeContent.builder().node(node.build()).stamp("wide").build());
    }

    private List<RecordedEvent> events(Recording recording) throws Exception {
        return events(recording, TestSource.class);
    }

    private List<RecordedEvent> events(Recording recording, Class<?> sourceClass) throws Exception {
        recording.stop();
        Path file = directory.resolve("config.jfr");
        recording.dump(file);
        // Source class identifies this test's producers; synchronous polling and stop complete them before the dump.
        return RecordingFile.readAllEvents(file).stream()
                .filter(it -> it.getEventType().getName().equals(EVENT_NAME))
                .filter(it -> sourceClass.getName().equals(it.getString("sourceClass")))
                .toList();
    }

    private ConfigSourceRuntimeImpl runtime(TestSource source) {
        return new ConfigSourceRuntimeImpl(new BuilderImpl.ConfigContextImpl(Runnable::run, List.of()), source);
    }

    private static final class TestSource implements NodeConfigSource, PollableSource<String>, PollingStrategy {
        private final boolean optional;
        private Optional<NodeContent> data = data("initial");
        private ConfigException failure;
        private ConfigException checkFailure;
        private boolean modified;
        private boolean stopped;
        private Polled listener;

        private TestSource(boolean optional) {
            this.optional = optional;
        }

        @Override
        public Optional<NodeContent> load() {
            if (failure != null) {
                throw failure;
            }
            return data;
        }

        @Override
        public boolean optional() {
            return optional;
        }

        @Override
        public String description() {
            return SECRET;
        }

        @Override
        public boolean isModified(String stamp) {
            if (checkFailure != null) {
                throw checkFailure;
            }
            return modified;
        }

        @Override
        public Optional<PollingStrategy> pollingStrategy() {
            return Optional.of(this);
        }

        @Override
        public void start(Polled listener) {
            this.listener = listener;
        }

        @Override
        public void stop() {
            stopped = true;
        }

        private ChangeEventType poll() {
            return listener.poll(Instant.EPOCH);
        }
    }
}
