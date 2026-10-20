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

package io.helidon.service.test.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class ShutdownOrderTest {
    @Test
    void emptyOptionalFactoryCanUseRetainedConsumerDuringCleanup() {
        var state = new GraphState();
        var config = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(ShutdownOrderTest_EventSink__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownOrderTest_EmptyFactory__ServiceDescriptor.INSTANCE)
                .putServiceInstance(ShutdownOrderTest_GraphState__ServiceDescriptor.INSTANCE, state)
                .build();
        var manager = ServiceRegistryManager.create(config);
        try {
            var sink = manager.registry().get(EventSink.class);
            assertThat("the factory supplies no injected dependency", sink.plugin.isEmpty(), is(true));
            var factory = manager.registry().get(EmptyFactory.class);
            factory.sink = factory.sinkSupplier.get();
            assertThat("empty factory retains the sink before shutdown", factory.sink, notNullValue());

            manager.shutdown();
            manager.shutdown();

            assertThat("empty factory keeps its run-level priority and each cleanup runs once",
                       state.events,
                       contains("sink used", "empty factory stopped", "sink stopped"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void unselectedServiceInstanceProviderCanUseRetainedConsumerDuringCleanup() {
        var state = new GraphState();
        var config = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(ShutdownOrderTest_InstanceSink__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownOrderTest_SelectedPlugin__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownOrderTest_UnselectedPlugin__ServiceDescriptor.INSTANCE)
                .putServiceInstance(ShutdownOrderTest_GraphState__ServiceDescriptor.INSTANCE, state)
                .build();
        var manager = ServiceRegistryManager.create(config);
        try {
            manager.registry().get(InstanceSink.class);
            var unselected = manager.registry().get(UnselectedPlugin.class);
            unselected.sink = unselected.sinkSupplier.get();
            assertThat("unselected provider retains the consumer before shutdown", unselected.sink, notNullValue());

            manager.shutdown();
            manager.shutdown();

            assertThat("only the selected provider constrains shutdown and each cleanup runs once",
                       state.events,
                       contains("instance sink used", "unselected plugin stopped", "selected plugin used",
                                "instance sink stopped", "selected plugin stopped"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void inheritedInjectionTracksDependenciesOfEachConcreteOwner() {
        var state = new GraphState();
        var config = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(ShutdownOrderTest_ChainA__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownOrderTest_ChainB__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownOrderTest_ChainC__ServiceDescriptor.INSTANCE)
                .putServiceInstance(ShutdownOrderTest_GraphState__ServiceDescriptor.INSTANCE, state)
                .build();
        var manager = ServiceRegistryManager.create(config);
        try {
            var first = (SharedChain) manager.registry().get(ChainContract.class);
            assertThat("highest-weight service starts the chain", first.label, is("A"));
            var second = (SharedChain) first.next.orElseThrow();
            assertThat("the inherited field is bound for A", second.label, is("B"));
            var third = (SharedChain) second.next.orElseThrow();
            assertThat("the same inherited field is bound for B", third.label, is("C"));
            assertThat("the final service has no next dependency", third.next.isEmpty(), is(true));

            manager.shutdown();

            assertThat("shared injection metadata must not merge owner-specific dependency edges",
                       state.events,
                       contains("A", "B", "C"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void perInstanceServiceCanUseItsDrivingServiceDuringCleanup() {
        var state = new GraphState();
        var config = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(ShutdownOrderTest_InstanceDriver__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownOrderTest_InstanceConsumer__ServiceDescriptor.INSTANCE)
                .putServiceInstance(ShutdownOrderTest_GraphState__ServiceDescriptor.INSTANCE, state)
                .build();
        var manager = ServiceRegistryManager.create(config);
        try {
            manager.registry().get(InstanceConsumer.class);

            manager.shutdown();

            assertThat("the actual driving service remains usable until its consumer is destroyed",
                       state.events,
                       contains("driver used", "consumer stopped", "driver stopped"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void explicitLowRunLevelCanUseImplicitDependencyDuringCleanup() {
        var config = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(ShutdownOrderTest_LowWriter__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownOrderTest_Connection__ServiceDescriptor.INSTANCE)
                .build();
        var manager = ServiceRegistryManager.create(config);
        try {
            var writer = manager.registry().get(LowWriter.class);

            manager.shutdown();

            assertThat(writer.connection.events,
                       contains("connection started", "write", "low writer stopped", "connection stopped"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void dependencyOrderTakesPrecedenceOverSameRunLevelWeights() {
        var config = ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .addServiceDescriptor(ShutdownOrderTest_WeightedWriter__ServiceDescriptor.INSTANCE)
                .addServiceDescriptor(ShutdownOrderTest_WeightedConnection__ServiceDescriptor.INSTANCE)
                .build();
        var manager = ServiceRegistryManager.create(config);
        try {
            var writer = manager.registry().get(WeightedWriter.class);

            manager.shutdown();

            assertThat(writer.connection.events, contains("write", "writer stopped", "connection stopped"));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void partialStartupPreservesTransitiveDependenciesOutsideSelectedRunLevels() {
        for (boolean explicitRunLevels : List.of(false, true)) {
            var state = new GraphState();
            var config = ServiceRegistryConfig.builder()
                    .discoverServices(false)
                    .discoverServicesFromServiceLoader(false)
                    .maxRunLevel(Service.RunLevel.NORMAL - 1)
                    .addServiceDescriptor(ShutdownOrderTest_GraphRoot__ServiceDescriptor.INSTANCE)
                    .addServiceDescriptor(ShutdownOrderTest_GraphLeft__ServiceDescriptor.INSTANCE)
                    .addServiceDescriptor(ShutdownOrderTest_GraphRight__ServiceDescriptor.INSTANCE)
                    .addServiceDescriptor(ShutdownOrderTest_GraphConnection__ServiceDescriptor.INSTANCE)
                    .addServiceDescriptor(ShutdownOrderTest_Unused__ServiceDescriptor.INSTANCE)
                    .putServiceInstance(ShutdownOrderTest_GraphState__ServiceDescriptor.INSTANCE, state);
            if (explicitRunLevels) {
                config.maxRunLevel(Service.RunLevel.NORMAL + 3)
                        .runLevels(List.of(Service.RunLevel.NORMAL - 1));
            }
            var manager = ServiceRegistryManager.start(config.build());
            try {
                assertThat("only the selected root and its dependencies were constructed", state.constructions, is(4));
                assertThat("excluded service was not constructed", state.unusedConstructions, is(0));

                manager.shutdown();
                manager.shutdown();

                assertThat("both branches must clean up before their shared connection",
                           state.events,
                           contains("root stopped", "left stopped", "right stopped", "connection stopped"));
                assertThat("shutdown must not construct excluded services", state.unusedConstructions, is(0));
                assertThat("excluded service has no cleanup callback", state.unusedCallbacks, is(0));
            } finally {
                manager.shutdown();
            }
        }
    }

    @Test
    void dependentCanUseDependencyDuringCleanup() {
        for (int i = 0; i < 50; i++) {
            var config = ServiceRegistryConfig.builder()
                    .discoverServices(false)
                    .discoverServicesFromServiceLoader(false)
                    .addServiceDescriptor(ShutdownOrderTest_Writer__ServiceDescriptor.INSTANCE)
                    .addServiceDescriptor(ShutdownOrderTest_Connection__ServiceDescriptor.INSTANCE)
                    .build();
            var manager = ServiceRegistryManager.create(config);
            try {
                var writer = manager.registry().get(Writer.class);
                assertThat(writer.connection.events, contains("connection started", "writer started"));

                manager.shutdown();
                manager.shutdown();

                assertThat("cleanup must write before the connection stops, exactly once",
                           writer.connection.events,
                           contains("connection started", "writer started", "write", "writer stopped", "connection stopped"));
            } finally {
                manager.shutdown();
            }
        }
    }

    interface GraphBranch {
        void use();
    }

    interface ChainContract {
        void use();
    }

    interface DriverContract {
        void use();
    }

    interface Plugin {
        void use();
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL)
    static class EventSink {
        private final GraphState state;
        private final Optional<Plugin> plugin;
        private boolean open = true;

        @Service.Inject
        EventSink(GraphState state, Optional<Plugin> plugin) {
            this.state = state;
            this.plugin = plugin;
        }

        void use() {
            assertThat("sink remains usable during empty factory cleanup", open, is(true));
            state.events.add("sink used");
        }

        @Service.PreDestroy
        void stop() {
            open = false;
            state.events.add("sink stopped");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 1)
    static class EmptyFactory implements Service.ServicesFactory<Plugin> {
        private final GraphState state;
        private final Supplier<EventSink> sinkSupplier;
        private EventSink sink;

        @Service.Inject
        EmptyFactory(GraphState state, Supplier<EventSink> sinkSupplier) {
            this.state = state;
            this.sinkSupplier = sinkSupplier;
        }

        @Override
        public List<Service.QualifiedInstance<Plugin>> services() {
            return List.of();
        }

        @Service.PreDestroy
        void stop() {
            if (sink != null) {
                sink.use();
            }
            state.events.add("empty factory stopped");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL)
    static class InstanceSink {
        private final GraphState state;
        private final Plugin plugin;
        private boolean open = true;

        @Service.Inject
        InstanceSink(GraphState state, ServiceInstance<Plugin> plugin) {
            this.state = state;
            this.plugin = plugin.get();
        }

        void use() {
            assertThat("consumer remains usable during unselected provider cleanup", open, is(true));
            state.events.add("instance sink used");
        }

        @Service.PreDestroy
        void stop() {
            plugin.use();
            open = false;
            state.events.add("instance sink stopped");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 2)
    @Weight(100)
    static class SelectedPlugin implements Plugin {
        private final GraphState state;
        private boolean open = true;

        @Service.Inject
        SelectedPlugin(GraphState state) {
            this.state = state;
        }

        @Override
        public void use() {
            assertThat("selected provider remains usable during consumer cleanup", open, is(true));
            state.events.add("selected plugin used");
        }

        @Service.PreDestroy
        void stop() {
            open = false;
            state.events.add("selected plugin stopped");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 1)
    @Weight(1)
    static class UnselectedPlugin implements Plugin {
        private final GraphState state;
        private final Supplier<InstanceSink> sinkSupplier;
        private InstanceSink sink;

        @Service.Inject
        UnselectedPlugin(GraphState state, Supplier<InstanceSink> sinkSupplier) {
            this.state = state;
            this.sinkSupplier = sinkSupplier;
        }

        @Override
        public void use() {
            throw new AssertionError("the lower-weight provider must not be injected");
        }

        @Service.PreDestroy
        void stop() {
            if (sink != null) {
                sink.use();
            }
            state.events.add("unselected plugin stopped");
        }
    }

    @Service.Singleton
    static class Connection {
        private final List<String> events = new ArrayList<>();
        private boolean open;

        @Service.PostConstruct
        void start() {
            open = true;
            events.add("connection started");
        }

        void write() {
            assertThat("connection is usable during dependent cleanup", open, is(true));
            events.add("write");
        }

        @Service.PreDestroy
        void stop() {
            open = false;
            events.add("connection stopped");
        }
    }

    @Service.Singleton
    static class Writer {
        private final Connection connection;

        @Service.Inject
        Writer(Connection connection) {
            this.connection = connection;
        }

        @Service.PostConstruct
        void start() {
            connection.events.add("writer started");
        }

        @Service.PreDestroy
        void stop() {
            connection.write();
            connection.events.add("writer stopped");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL - 1)
    static class LowWriter {
        private final Connection connection;

        @Service.Inject
        LowWriter(Connection connection) {
            this.connection = connection;
        }

        @Service.PreDestroy
        void stop() {
            connection.write();
            connection.events.add("low writer stopped");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL)
    @Weight(1)
    static class WeightedConnection {
        private final List<String> events = new ArrayList<>();
        private boolean open = true;

        void write() {
            assertThat("same-level dependency remains usable", open, is(true));
            events.add("write");
        }

        @Service.PreDestroy
        void stop() {
            open = false;
            events.add("connection stopped");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL)
    @Weight(100)
    static class WeightedWriter {
        private final WeightedConnection connection;

        @Service.Inject
        WeightedWriter(WeightedConnection connection) {
            this.connection = connection;
        }

        @Service.PreDestroy
        void stop() {
            connection.write();
            connection.events.add("writer stopped");
        }
    }

    @Service.Singleton
    static class GraphState {
        private final List<String> events = new ArrayList<>();
        private int constructions;
        private int unusedConstructions;
        private int unusedCallbacks;
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL - 1)
    static class GraphRoot {
        private final GraphState state;
        private final GraphLeft left;
        private final List<GraphBranch> branches;

        @Service.Inject
        GraphRoot(GraphState state, Optional<GraphLeft> left, List<GraphBranch> branches) {
            this.state = state;
            this.left = left.orElseThrow();
            this.branches = branches;
            state.constructions++;
        }

        @Service.PreDestroy
        void stop() {
            left.use();
            assertThat("list injection contains the other branch", branches.size(), is(1));
            branches.forEach(GraphBranch::use);
            state.events.add("root stopped");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 2)
    static class GraphLeft {
        private final GraphConnection connection;
        private boolean open = true;

        @Service.Inject
        GraphLeft(GraphConnection connection) {
            this.connection = connection;
            connection.state.constructions++;
        }

        void use() {
            assertThat("optional dependency remains usable", open, is(true));
            connection.use();
        }

        @Service.PreDestroy
        void stop() {
            connection.use();
            open = false;
            connection.state.events.add("left stopped");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 1)
    static class GraphRight implements GraphBranch {
        private final GraphConnection connection;
        private boolean open = true;

        @Service.Inject
        GraphRight(GraphConnection connection) {
            this.connection = connection;
            connection.state.constructions++;
        }

        @Override
        public void use() {
            assertThat("list dependency remains usable", open, is(true));
            connection.use();
        }

        @Service.PreDestroy
        void stop() {
            connection.use();
            open = false;
            connection.state.events.add("right stopped");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 3)
    static class GraphConnection {
        private final GraphState state;
        private boolean open = true;

        @Service.Inject
        GraphConnection(GraphState state) {
            this.state = state;
            state.constructions++;
        }

        void use() {
            assertThat("transitive shared dependency remains usable", open, is(true));
        }

        @Service.PreDestroy
        void stop() {
            open = false;
            state.events.add("connection stopped");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 3)
    static class Unused {
        private final GraphState state;

        @Service.Inject
        Unused(GraphState state) {
            this.state = state;
            state.unusedConstructions++;
        }

        @Service.PreDestroy
        void stop() {
            state.unusedCallbacks++;
        }
    }

    abstract static class SharedChain implements ChainContract {
        private final String label;

        @Service.Inject
        Optional<ChainContract> next;
        @Service.Inject
        GraphState state;

        private boolean open = true;

        SharedChain(String label) {
            this.label = label;
        }

        @Override
        public void use() {
            assertThat("inherited dependency " + label + " remains usable", open, is(true));
        }

        @Service.PreDestroy
        void stop() {
            next.ifPresent(ChainContract::use);
            open = false;
            state.events.add(label);
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL - 1)
    @Weight(3)
    static class ChainA extends SharedChain {
        ChainA() {
            super("A");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL)
    @Weight(2)
    static class ChainB extends SharedChain {
        ChainB() {
            super("B");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 1)
    @Weight(1)
    static class ChainC extends SharedChain {
        ChainC() {
            super("C");
        }
    }

    @Service.Singleton
    @Service.RunLevel(Service.RunLevel.NORMAL + 1)
    static class InstanceDriver implements DriverContract {
        private final GraphState state;
        private boolean open = true;

        @Service.Inject
        InstanceDriver(GraphState state) {
            this.state = state;
        }

        @Override
        public void use() {
            assertThat("driving service remains usable", open, is(true));
            state.events.add("driver used");
        }

        @Service.PreDestroy
        void stop() {
            open = false;
            state.events.add("driver stopped");
        }
    }

    @Service.Singleton
    @Service.PerInstance(DriverContract.class)
    @Service.RunLevel(Service.RunLevel.NORMAL - 1)
    static class InstanceConsumer {
        private final DriverContract driver;
        private final GraphState state;

        @Service.Inject
        InstanceConsumer(DriverContract driver, GraphState state) {
            this.driver = driver;
            this.state = state;
        }

        @Service.PreDestroy
        void stop() {
            driver.use();
            state.events.add("consumer stopped");
        }
    }
}
