/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;
import io.helidon.common.configurable.spi.ExecutorServiceSupplierObserver;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Manages metrics for each Helidon-created thread pool.
 */
public class ExecutorServiceMetricsObserver implements ExecutorServiceSupplierObserver {

    private static final Logger LOGGER = Logger.getLogger(ExecutorServiceMetricsObserver.class.getName());

    private static final String METRIC_NAME_PREFIX = "executor-service.";

    // metrics we register for each ThreadPoolExecutor which a supplier creates (other than thread-per-task executors)
    private static final List<GaugeFactory<?, ThreadPoolExecutor>> THREAD_POOL_EXECUTOR_METRIC_FACTORIES = List.of(
            GaugeFactory.create(MetadataTemplates.ACTIVE_COUNT_METADATA, ThreadPoolExecutor::getActiveCount),
            GaugeFactory.create(MetadataTemplates.COMPLETED_TASK_COUNT_METADATA, ThreadPoolExecutor::getCompletedTaskCount),
            GaugeFactory.create(MetadataTemplates.POOL_SIZE_METADATA, ThreadPoolExecutor::getPoolSize),
            GaugeFactory.create(MetadataTemplates.LARGEST_POOL_SIZE_METADATA, ThreadPoolExecutor::getLargestPoolSize),
            GaugeFactory.create(MetadataTemplates.TASK_COUNT_METADATA, ThreadPoolExecutor::getTaskCount),
            GaugeFactory.create(MetadataTemplates.QUEUE_REMAINING_CAPACITY_METADATA,
                                (ThreadPoolExecutor tpe) -> tpe.getQueue().remainingCapacity()),
            GaugeFactory.create(MetadataTemplates.QUEUE_SIZE_METADATA,
                                (ThreadPoolExecutor tpe) -> tpe.getQueue().size())
            );

    private final LazyValue<MetricRegistry> registry = LazyValue
            .create(() -> io.helidon.metrics.api.RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.VENDOR));

    /**
     * Creates a new instance of the observer.
     */
    public ExecutorServiceMetricsObserver() {
    }

    @Override
    public SupplierObserverContext registerSupplier(Supplier<? extends ExecutorService> supplier,
                                                    int supplierIndex,
                                                    String supplierCategory) {
        SupplierInfo supplierInfo = new SupplierInfo(supplierCategory,
                                                     supplierIndex);
        LOGGER.log(Level.FINE, () -> String.format("Metrics thread pool supplier registration: %s", supplierInfo));
        return supplierInfo.context();
    }

    @Override
    public SupplierObserverContext registerSupplier(Supplier<? extends ExecutorService> supplier,
                                                                                int supplierIndex,
                                                                                String supplierCategory,
                                                                                List<MethodInvocation> methodInvocations) {
        SupplierInfo supplierInfo = new SupplierInfoWithMethods(supplierCategory,
                                                                supplierIndex,
                                                                methodInvocations);
        LOGGER.log(Level.FINE, () -> String.format("Metrics thread pool supplier registration: %s", supplierInfo));
        return supplierInfo.context();
    }

    private class MetricsObserverContext implements ExecutorServiceSupplierObserver.SupplierObserverContext {

        private final SupplierInfo supplierInfo;
        private final Set<MetricID> metricsIDs = new HashSet<>();

        private MetricsObserverContext(SupplierInfo supplierInfo) {
            this.supplierInfo = supplierInfo;
        }

        @Override
        public void registerExecutorService(ExecutorService executorService, int index) {
            LOGGER.log(Level.FINE, String.format("Registering executor service %s:%d for supplier %s%d",
                                                 executorService,
                                                 index,
                                                 supplierInfo.supplierCategory(),
                                                 supplierInfo.supplierIndex()));

            if (executorService instanceof ThreadPoolExecutor) {
                registerMetrics((ThreadPoolExecutor) executorService, index);
            } else if (supplierInfo instanceof SupplierInfoWithMethods) {
                registerMetrics(executorService, ((SupplierInfoWithMethods) supplierInfo).methodInvocations(), index);
            }
        }

        private void registerMetrics(ThreadPoolExecutor threadPoolExecutor, int index) {
            THREAD_POOL_EXECUTOR_METRIC_FACTORIES.forEach(factory -> factory
                    .registerGauge(registry.get(),
                                   supplierInfo.supplierCategory(),
                                   supplierInfo.supplierIndex(),
                                   threadPoolExecutor,
                                   index,
                                   metricsIDs));
        }

        private void registerMetrics(ExecutorService executorService, List<MethodInvocation> methodInvocations, int index) {
            methodInvocations.forEach(mi -> {
                Metadata metadata = Metadata.builder()
                        .withName(METRIC_NAME_PREFIX + mi.displayName())
                        .withDescription(mi.description())
                        .withType(MetricType.GAUGE)
                        .withUnit(MetricUnits.NONE)
                        .build();
                Tag[] tags = tags(supplierInfo.supplierCategory(),
                                  supplierInfo.supplierIndex(),
                                  index);
                registry.get().register(metadata, (Gauge<Object>) () -> {
                    try {
                        return mi.method().invoke(executorService);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }, tags);
                metricsIDs.add(new MetricID(metadata.getName(), tags));
            });
        }

        @Override
        public void unregisterExecutorService(ExecutorService executorService) {
            metricsIDs.forEach(metricID -> registry.get().remove(metricID));
        }
    }

    /**
     * Information about an executor service supplier.
     */
    private class SupplierInfo {

        private final MetricsObserverContext context;
        private final String supplierCategory;
        private final int supplierIndex;

        private SupplierInfo(String supplierCategory, int supplierIndex) {
            this.supplierCategory = supplierCategory;
            this.supplierIndex = supplierIndex;
            context = new MetricsObserverContext(this);
        }

        String supplierCategory() {
            return supplierCategory;
        }

        int supplierIndex() {
            return supplierIndex;
        }

        private MetricsObserverContext context() {
            return context;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", SupplierInfo.class.getSimpleName() + "[", "]")
                    .add("supplierCategory='" + supplierCategory + "'")
                    .add("supplierIndex=" + supplierIndex)
                    .toString();
        }
    }

    /**
     * Information about an executor service supplier that provides its own mechanisms for retrieving metrics.
     * <p>
     *     This is for supporting Loom's {@code ThreadPerTaskExecutorService} when it is available without requiring it to be
     *     present at compile or runtime.
     * </p>
     */
    private class SupplierInfoWithMethods extends SupplierInfo {

        private final List<MethodInvocation> methodInvocations;

        private SupplierInfoWithMethods(String supplierCategory,
                                        int supplierIndex,
                                        List<MethodInvocation> methodInvocations) {
            super(supplierCategory, supplierIndex);
            this.methodInvocations = methodInvocations;
        }

        private List<MethodInvocation> methodInvocations() {
            return methodInvocations;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", SupplierInfoWithMethods.class.getSimpleName() + "[", "]")
                    .add("supplierCategory='" + supplierCategory() + "'")
                    .add("supplierIndex=" + supplierIndex())
                    .add("methodInvocations=" + methodInvocations)
                    .toString();
        }
    }

    private static class MetadataTemplates {

        private static final Metadata ACTIVE_COUNT_METADATA = Metadata.builder()
                .withName(METRIC_NAME_PREFIX + "active-count")
                .withDescription("Active count")
                .withType(MetricType.GAUGE)
                .withUnit(MetricUnits.NONE)
                .build();

        private static final Metadata COMPLETED_TASK_COUNT_METADATA = Metadata.builder()
                .withName(METRIC_NAME_PREFIX + "completed-task-count")
                .withDescription("Completed task count")
                .withType(MetricType.GAUGE)
                .withUnit(MetricUnits.NONE)
                .build();

        private static final Metadata POOL_SIZE_METADATA = Metadata.builder()
                .withName(METRIC_NAME_PREFIX + "pool-size")
                .withDescription("Pool size")
                .withType(MetricType.GAUGE)
                .withUnit(MetricUnits.NONE)
                .build();
        private static final Metadata LARGEST_POOL_SIZE_METADATA = Metadata.builder()
                .withName(METRIC_NAME_PREFIX + "largest-pool-size")
                .withDescription("Largest pool size")
                .withType(MetricType.GAUGE)
                .withUnit(MetricUnits.NONE)
                .build();

        private static final Metadata TASK_COUNT_METADATA = Metadata.builder()
                .withName(METRIC_NAME_PREFIX + "task-count")
                .withDescription("Task count")
                .withType(MetricType.GAUGE)
                .withUnit(MetricUnits.NONE)
                .build();

        private static final Metadata QUEUE_REMAINING_CAPACITY_METADATA = Metadata.builder()
                .withName(METRIC_NAME_PREFIX + "queue.remaining-capacity")
                .withDescription("Queue remaining capacity")
                .withType(MetricType.GAUGE)
                .withUnit(MetricUnits.NONE)
                .build();

        private static final Metadata QUEUE_SIZE_METADATA = Metadata.builder()
                .withName(METRIC_NAME_PREFIX + "queue.size")
                .withDescription("Queue size")
                .withType(MetricType.GAUGE)
                .withUnit(MetricUnits.NONE)
                .build();

    }

    /**
     * Factory which creates a {@code Gauge} that queries a particular method of an executor service.
     *
     * @param <T> type of the gauge
     */
    private static class GaugeFactory<T, E extends ExecutorService> {

        /**
         * Creates a gauge factory using template metadata (we have to adjust the name) and a function on the executor service
         * type which returns the value the gauge wraps.
         *
         * @param templateMetadata template metadata for the metric
         * @param valueFunction function which is a method invocation on the type of executor service to retrieve the gauge value
         * @param <T> type of the gauge
         * @return the new gauge factory
         */
        private static <T, E extends ExecutorService> GaugeFactory<T, E> create(
                Metadata templateMetadata,
                Function<E, T> valueFunction) {
            return new GaugeFactory<>(templateMetadata, valueFunction);
        }
        private final Metadata templateMetadata;
        private final Function<E, T> valueFunction;

        private GaugeFactory(Metadata templateMetadata, Function<E, T> valueFunction) {
            this.templateMetadata = templateMetadata;
            this.valueFunction = valueFunction;
        }

        private Gauge<T> registerGauge(MetricRegistry registry,
                                       String supplierCategory,
                                       int supplierIndex,
                                       E executor,
                                       int index,
                                       Set<MetricID> metricIDs) {
            Tag[] tags = tags(supplierCategory, supplierIndex, index);
            metricIDs.add(new MetricID(templateMetadata.getName(), tags));
            return registry.register(templateMetadata, () -> valueFunction.apply(executor), tags);
        }
    }

    private static Tag[] tags(String supplierCategory, int supplierIndex, int index) {
        return new Tag[] {
                new Tag("supplierCategory", supplierCategory),
                new Tag("supplierIndex", Integer.toString(supplierIndex)),
                new Tag("poolIndex", Integer.toString(index))
        };
    }
}
