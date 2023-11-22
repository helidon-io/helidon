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
package io.helidon.metrics.systemmeters;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.spi.MetersProvider;

/**
 * Provider for the built-in system meters.
 */
public class SystemMetersProvider implements MetersProvider {

    private static final String BYTES = "bytes";
    private static final String SECONDS = "seconds";
    private static final String SCOPE = Meter.Scope.BASE;
    private static final Metadata MEMORY_USED_HEAP = Metadata.builder()
            .withName("memory.usedHeap")
            .withDescription("Displays the amount of used heap memory in bytes.")
            .withUnit(BYTES)
            .build();
    private static final Metadata MEMORY_COMMITTED_HEAP = Metadata.builder()
            .withName("memory.committedHeap")
            .withDescription(
                    "Displays the amount of memory in bytes that is "
                            + "committed for the Java virtual "
                            + "machine to use. This amount of memory is "
                            + "guaranteed for the Java virtual "
                            + "machine to use.")
            .withUnit(BYTES)
            .build();
    private static final Metadata MEMORY_MAX_HEAP = Metadata.builder()
            .withName("memory.maxHeap")
            .withDescription(
                    "Displays the maximum amount of heap memory in bytes that can"
                            + " be used for "
                            + "memory management. This attribute displays -1 if "
                            + "the maximum heap "
                            + "memory size is undefined. This amount of memory is "
                            + "not guaranteed to be "
                            + "available for memory management if it is greater "
                            + "than the amount of "
                            + "committed memory. The Java virtual machine may fail"
                            + " to allocate memory "
                            + "even if the amount of used memory does not exceed "
                            + "this maximum size.")
            .withUnit(BYTES)
            .build();
    private static final Metadata JVM_UPTIME = Metadata.builder()
            .withName("jvm.uptime")
            .withDescription(
                    "Displays the start time of the Java virtual machine in "
                            + "seconds. This "
                            + "attribute displays the approximate time when the Java "
                            + "virtual machine "
                            + "started.")
            .withUnit(SECONDS)
            .build();
    private static final Metadata THREAD_COUNT = Metadata.builder()
            .withName("thread.count")
            .withDescription("Displays the current number of live threads including both "
                                     + "daemon and nondaemon threads")
            .build();
    private static final Metadata THREAD_DAEMON_COUNT = Metadata.builder()
            .withName("thread.daemon.count")
            .withDescription("Displays the current number of live daemon threads.")
            .build();
    private static final Metadata THREAD_MAX_COUNT = Metadata.builder()
            .withName("thread.max.count")
            .withDescription("Displays the peak live thread count since the Java "
                                     + "virtual machine started or "
                                     + "peak was reset. This includes daemon and "
                                     + "non-daemon threads.")
            .build();
    private static final Metadata CL_LOADED_COUNT = Metadata.builder()
            .withName("classloader.loadedClasses.count")
            .withDescription("Displays the number of classes that are currently loaded in "
                                     + "the Java virtual machine.")
            .build();
    private static final Metadata CL_LOADED_TOTAL = Metadata.builder()
            .withName("classloader.loadedClasses.total")
            .withDescription("Displays the total number of classes that have been loaded "
                                     + "since the Java virtual machine has started execution.")
            .build();
    private static final Metadata CL_UNLOADED_COUNT = Metadata.builder()
            .withName("classloader.unloadedClasses.total")
            .withDescription("Displays the total number of classes unloaded since the Java "
                                     + "virtual machine has started execution.")
            .build();
    private static final Metadata OS_AVAILABLE_CPU = Metadata.builder()
            .withName("cpu.availableProcessors")
            .withDescription("Displays the number of processors available to the Java "
                                     + "virtual machine. This "
                                     + "value may change during a particular invocation of"
                                     + " the virtual machine.")
            .build();
    private static final Metadata OS_LOAD_AVERAGE = Metadata.builder()
            .withName("cpu.systemLoadAverage")
            .withDescription("Displays the system load average for the last minute. The "
                                     + "system load average "
                                     + "is the sum of the number of runnable entities "
                                     + "queued to the available "
                                     + "processors and the number of runnable entities "
                                     + "running on the available "
                                     + "processors averaged over a period of time. The way "
                                     + "in which the load average "
                                     + "is calculated is operating system specific but is "
                                     + "typically a damped timedependent "
                                     + "average. If the load average is not available, a "
                                     + "negative value is "
                                     + "displayed. This attribute is designed to provide a "
                                     + "hint about the system load "
                                     + "and may be queried frequently. The load average may"
                                     + " be unavailable on some "
                                     + "platforms where it is expensive to implement this "
                                     + "method.")
            .build();
    private static final Metadata GC_TIME = Metadata.builder()
            .withName("gc.time")
            .withDescription(
                    "Displays the approximate accumulated collection elapsed time in seconds. "
                            + "This attribute displays -1 if the collection elapsed time is undefined for this "
                            + "collector. The Java virtual machine implementation may use a high resolution "
                            + "timer to measure the elapsed time. This attribute may display the same value "
                            + "even if the collection count has been incremented if the collection elapsed "
                            + "time is very short.")
            .withUnit("seconds")
            .build();
    private static final Metadata GC_COUNT = Metadata.builder()
            .withName("gc.total")
            .withDescription(
                    "Displays the total number of collections that have occurred. This attribute lists "
                            + "-1 if the collection count is undefined for this collector.")
            .build();

    private MetricsFactory metricsFactory;

    /**
     * Constructs a new instance for service loading.
     *
     * @deprecated
     */
    @Deprecated
    public SystemMetersProvider() {
    }

    @Override
    public Collection<Meter.Builder<?, ?>> meterBuilders(MetricsFactory metricsFactory) {
        this.metricsFactory = metricsFactory; // save at the instance level for ease of access
        return prepareMeterBuilders();
    }

    /**
     * Returns a function to invoke a function on a main bean to get a sub-bean, then invoke a function on
     * the sub-bean to retrieve a value.
     *
     * @param getSubBeanFn function to get the subbean from the main bean
     * @param valueFn      function to get the value from the subbean
     * @param <M>          type of the main bean
     * @param <S>          type of the subbean
     * @param <N>          subtype of {@link java.lang.Number} returned by the value-obtaining function applied to the subbean
     * @return a function for retrieving the value given the main bean
     */
    private static <M, S, N extends Number> Function<M, N> typedFn(Function<M, S> getSubBeanFn, Function<S, N> valueFn) {
        return main -> valueFn.apply(getSubBeanFn.apply(main));
    }

    private <B extends Meter.Builder<B, M>,
            M extends Meter>
    Collection<Meter.Builder<?, ?>> prepareMeterBuilders() {
        Collection<Meter.Builder<?, ?>> result = new ArrayList<>();

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        // load all base metrics
        registerGauge(result,
                      MEMORY_USED_HEAP,
                      memoryBean,
                      typedFn(MemoryMXBean::getHeapMemoryUsage, MemoryUsage::getUsed));
        registerGauge(result,
                      MEMORY_COMMITTED_HEAP,
                      memoryBean,
                      typedFn(MemoryMXBean::getHeapMemoryUsage, MemoryUsage::getCommitted));
        registerGauge(result,
                      MEMORY_MAX_HEAP,
                      memoryBean,
                      typedFn(MemoryMXBean::getHeapMemoryUsage, MemoryUsage::getMax));

        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        registerGauge(result, JVM_UPTIME, runtimeBean, rtBean -> rtBean.getUptime() / 1000.0D);

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        registerGauge(result, THREAD_COUNT, threadBean, ThreadMXBean::getThreadCount);
        registerGauge(result, THREAD_DAEMON_COUNT, threadBean, ThreadMXBean::getDaemonThreadCount);
        registerGauge(result, THREAD_MAX_COUNT, threadBean, ThreadMXBean::getPeakThreadCount);

        ClassLoadingMXBean clBean = ManagementFactory.getClassLoadingMXBean();
        registerGauge(result, CL_LOADED_COUNT, clBean, ClassLoadingMXBean::getLoadedClassCount);
        registerFunctionalCounter(result, CL_LOADED_TOTAL, clBean, ClassLoadingMXBean::getTotalLoadedClassCount);
        registerFunctionalCounter(result, CL_UNLOADED_COUNT, clBean, ClassLoadingMXBean::getUnloadedClassCount);

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        registerGauge(result, OS_AVAILABLE_CPU, osBean, OperatingSystemMXBean::getAvailableProcessors);
        registerGauge(result, OS_LOAD_AVERAGE, osBean, OperatingSystemMXBean::getSystemLoadAverage);

        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String poolName = gcBean.getName();
            registerFunctionalCounter(result,
                          GC_COUNT,
                          gcBean,
                          GarbageCollectorMXBean::getCollectionCount,
                          Tag.create("name", poolName));
            // Express the GC time in seconds.
            registerFunctionalCounter(result,
                          GC_TIME,
                          gcBean,
                          bean -> (long) (bean.getCollectionTime() / 1000.0D),
                          Tag.create("name", poolName));
        }
        return result;
    }

    private <T, R extends Number> void registerGauge(Collection<Meter.Builder<?, ?>> result,
                                                     Metadata metadata,
                                                     T object,
                                                     Function<T, R> fn,
                                                     Tag... tags) {
            result.add(metricsFactory.gaugeBuilder(metadata.name, object, obj -> fn.apply(obj).doubleValue())
                               .scope(SCOPE)
                               .description(metadata.description)
                               .baseUnit(metadata.baseUnit)
                               .tags(Arrays.asList(tags)));
    }

    private <T> void registerFunctionalCounter(Collection<Meter.Builder<?, ?>> result,
                                               Metadata metadata,
                                               T object,
                                               Function<T, Long> fn,
                                               Tag... tags) {
        result.add(metricsFactory.functionalCounterBuilder(metadata.name, object, fn)
                           .scope(SCOPE)
                           .description(metadata.description)
                           .baseUnit(metadata.baseUnit)
                           .tags(Arrays.asList(tags)));
    }

    private static class Metadata {

        private final String name;
        private final String description;
        private final String baseUnit;

        private Metadata(Builder builder) {
            name = builder.name;
            description = builder.description;
            baseUnit = builder.baseUnit;
        }

        static Builder builder() {
            return new Builder();
        }

        private static class Builder implements io.helidon.common.Builder<Builder, SystemMetersProvider.Metadata> {

            private String name;
            private String description;
            private String baseUnit;

            @Override
            public SystemMetersProvider.Metadata build() {
                return new SystemMetersProvider.Metadata(this);
            }

            Builder withName(String name) {
                this.name = name;
                return this;
            }

            Builder withDescription(String description) {
                this.description = description;
                return this;

            }

            Builder withUnit(String unit) {
                baseUnit = unit;
                return this;
            }
        }
    }
}
