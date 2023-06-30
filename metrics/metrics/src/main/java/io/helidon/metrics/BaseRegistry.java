/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import io.helidon.metrics.api.BaseMetricsSettings;
import io.helidon.metrics.api.MetricsSettings;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Registry for base metrics as required by Microprofile metrics specification.
 *
 * <ul>
 * <li>All "General JVM Stats" are supported (section 4.1. of the spec).</li>
 * <li>All "Thread JVM Stats" are supported (section 4.2. of the spec).</li>
 * <li>NONE of "Thread Pool Stats" are supported (section 4.3. of the spec) - Vendor specific approach.</li>
 * <li>All "ClassLoading JVM Stats" are supported (section 4.4. of the spec).</li>
 * <li>Available Processors and System Load Average (where available from JVM) metrics from "Operating System"
 * (section 4.5 of the spec).</li>
 * </ul>
 *
 * Each metric can be disabled using {@link BaseMetricsSettings.Builder#enableBaseMetric(String, boolean)} or by using the
 * equivalent configuration property
 * {@code helidon.metrics.base.${metric_name}.enabled=false}. Further, to suppress
 * all base metrics use {@link BaseMetricsSettings.Builder#enabled(boolean)} or set the equivalent config property
 * {@code {{@value BaseMetricsSettings.Builder#}}metrics.base.enabled=false}.
 */
final class BaseRegistry extends Registry {

    private static final Tag[] NO_TAGS = new Tag[0];

    private static final Metadata MEMORY_USED_HEAP = Metadata.builder()
            .withName("memory.usedHeap")
            .withDescription("Displays the amount of used heap memory in bytes.")
            .withUnit(MetricUnits.BYTES)
            .build();

    private static final Metadata MEMORY_COMMITTED_HEAP = Metadata.builder()
            .withName("memory.committedHeap")
            .withDescription(
                    "Displays the amount of memory in bytes that is "
                            + "committed for the Java virtual "
                            + "machine to use. This amount of memory is "
                            + "guaranteed for the Java virtual "
                            + "machine to use.")
            .withUnit(MetricUnits.BYTES)
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
            .withUnit(MetricUnits.BYTES)
            .build();

    private static final Metadata JVM_UPTIME = Metadata.builder()
            .withName("jvm.uptime")
            .withDescription(
                    "Displays the start time of the Java virtual machine in "
                            + "milliseconds. This "
                            + "attribute displays the approximate time when the Java "
                            + "virtual machine "
                            + "started.")
            .withUnit(MetricUnits.SECONDS)
            .build();

    private static final Metadata THREAD_COUNT = Metadata.builder()
            .withName("thread.count")
            .withDescription("Displays the current number of live threads including both "
                            + "daemon and nondaemon threads")
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata THREAD_DAEMON_COUNT = Metadata.builder()
            .withName("thread.daemon.count")
            .withDescription("Displays the current number of live daemon threads.")
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata THREAD_MAX_COUNT = Metadata.builder()
            .withName("thread.max.count")
            .withDescription("Displays the peak live thread count since the Java "
                            + "virtual machine started or "
                            + "peak was reset. This includes daemon and "
                            + "non-daemon threads.")
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata CL_LOADED_COUNT = Metadata.builder()
            .withName("classloader.loadedClasses.count")
            .withDescription("Displays the number of classes that are currently loaded in "
                            + "the Java virtual machine.")
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata CL_LOADED_TOTAL = Metadata.builder()
            .withName("classloader.loadedClasses.total")
            .withDescription("Displays the total number of classes that have been loaded "
                            + "since the Java virtual machine has started execution.")
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata CL_UNLOADED_COUNT = Metadata.builder()
            .withName("classloader.unloadedClasses.total")
            .withDescription("Displays the total number of classes unloaded since the Java "
                            + "virtual machine has started execution.")
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata OS_AVAILABLE_CPU = Metadata.builder()
            .withName("cpu.availableProcessors")
            .withDescription("Displays the number of processors available to the Java "
                            + "virtual machine. This "
                            + "value may change during a particular invocation of"
                            + " the virtual machine.")
            .withUnit(MetricUnits.NONE)
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
            .withUnit(MetricUnits.NONE)
            .build();

    private final MetricsSettings metricsSettings;

    private BaseRegistry(MetricsSettings metricsSettings) {
        super(Registry.BASE_SCOPE, metricsSettings.registrySettings(Registry.BASE_SCOPE));
        this.metricsSettings = metricsSettings;
    }

    public static Registry create(MetricsSettings metricsSettings) {

        BaseRegistry result = new BaseRegistry(metricsSettings);

        if (!metricsSettings.baseMetricsSettings().isEnabled()) {
            return result;
        }
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        // load all base metrics
        register(result, MEMORY_USED_HEAP, memoryBean.getHeapMemoryUsage(), MemoryUsage::getUsed);
        register(result, MEMORY_COMMITTED_HEAP, memoryBean.getHeapMemoryUsage(), MemoryUsage::getCommitted);
        register(result, MEMORY_MAX_HEAP, memoryBean.getHeapMemoryUsage(), MemoryUsage::getMax);

        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        register(result, JVM_UPTIME, runtimeBean, RuntimeMXBean::getUptime);

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        register(result, THREAD_COUNT, threadBean, ThreadMXBean::getThreadCount);
        register(result, THREAD_DAEMON_COUNT, threadBean, ThreadMXBean::getDaemonThreadCount);
        register(result, THREAD_MAX_COUNT, threadBean, ThreadMXBean::getPeakThreadCount);

        ClassLoadingMXBean clBean = ManagementFactory.getClassLoadingMXBean();
        register(result, CL_LOADED_COUNT, clBean, ClassLoadingMXBean::getLoadedClassCount);
        registerFunctionalCounter(result, CL_LOADED_TOTAL, clBean, ClassLoadingMXBean::getTotalLoadedClassCount);
        registerFunctionalCounter(result, CL_UNLOADED_COUNT, clBean, ClassLoadingMXBean::getUnloadedClassCount);

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        register(result, OS_AVAILABLE_CPU, osBean, OperatingSystemMXBean::getAvailableProcessors);
        register(result, OS_LOAD_AVERAGE, osBean, OperatingSystemMXBean::getSystemLoadAverage);

        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String poolName = gcBean.getName();
            registerFunctionalCounter(result,
                                      gcCountMeta(),
                                      gcBean,
                                      GarbageCollectorMXBean::getCollectionCount,
                                      new Tag("name", poolName));
            // Express the GC time in seconds.
            registerFunctionalCounter(result,
                                      gcTimeMeta(),
                                      gcBean, bean -> bean.getCollectionTime() / 1000.0D,
                                      new Tag("name", poolName));
        }

        return result;
    }

    private static Metadata gcTimeMeta() {
        return Metadata.builder()
                .withName("gc.time")
                .withDescription(
                            "Displays the approximate accumulated collection elapsed time in milliseconds. "
                                    + "This attribute displays -1 if the collection elapsed time is undefined for this "
                                    + "collector. The Java virtual machine implementation may use a high resolution "
                                    + "timer to measure the elapsed time. This attribute may display the same value "
                                    + "even if the collection count has been incremented if the collection elapsed "
                                    + "time is very short.")
                .withUnit(MetricUnits.SECONDS)
                .build();
    }

    private static Metadata gcCountMeta() {
        return Metadata.builder()
                .withName("gc.total")
                .withDescription(
                            "Displays the total number of collections that have occurred. This attribute lists "
                                    + "-1 if the collection count is undefined for this collector.")
                .withUnit(MetricUnits.NONE)
                .build();
    }

    private static <T, R extends Number>  void register(BaseRegistry registry,
                                                        Metadata meta,
                                                        T object,
                                                        Function<T, R> func,
                                                        Tag... tags) {
        if (registry.metricsSettings.baseMetricsSettings().isBaseMetricEnabled(meta.getName())
                && registry.metricsSettings.isMetricEnabled(Registry.BASE_SCOPE, meta.getName())) {
            registry.gauge(meta, object, func, tags);
        }
    }

    private static <T, R extends Number>  void register(BaseRegistry registry, Metadata meta, T object, Function<T, R> func) {
        register(registry, meta, object, func, NO_TAGS);
    }

    private static <T> void registerFunctionalCounter(BaseRegistry registry,
                                                      Metadata meta,
                                                      T object,
                                                      ToDoubleFunction<T> func,
                                                      Tag... tags) {
        if (registry.metricsSettings.baseMetricsSettings().isBaseMetricEnabled(meta.getName())
                && registry.metricsSettings.isMetricEnabled(Registry.BASE_SCOPE, meta.getName())) {
            registry.counter(meta, object, func, tags);
        }
    }

}
