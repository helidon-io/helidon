/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

import io.helidon.config.Config;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricType;
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
 * Each metric can be disabled by using the following configuration property:
 * {@code helidon.metrics.base.${metric_name}.enabled=false}. Further, to suppress
 * all base metrics set {@code helidon.metrics.base.enabled=false}.
 */
final class BaseRegistry extends Registry {

    private static final String CONFIG_METRIC_ENABLED_BASE = "base.";
    static final String BASE_ENABLED_KEY = CONFIG_METRIC_ENABLED_BASE + "enabled";

    private static final Metadata MEMORY_USED_HEAP = Metadata.builder()
            .withName("memory.usedHeap")
            .withDisplayName("Used Heap Memory")
            .withDescription("Displays the amount of used heap memory in bytes.")
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.BYTES)
            .build();

    private static final Metadata MEMORY_COMMITTED_HEAP = Metadata.builder()
            .withName("memory.committedHeap")
            .withDisplayName("Committed Heap Memory")
            .withDescription(
                    "Displays the amount of memory in bytes that is "
                            + "committed for the Java virtual "
                            + "machine to use. This amount of memory is "
                            + "guaranteed for the Java virtual "
                            + "machine to use.")
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.BYTES)
            .build();

    private static final Metadata MEMORY_MAX_HEAP = Metadata.builder()
            .withName("memory.maxHeap")
            .withDisplayName("Max Heap Memory")
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
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.BYTES)
            .build();

    private static final Metadata JVM_UPTIME = Metadata.builder()
            .withName("jvm.uptime")
            .withDisplayName("JVM Uptime")
            .withDescription(
                    "Displays the start time of the Java virtual machine in "
                            + "milliseconds. This "
                            + "attribute displays the approximate time when the Java "
                            + "virtual machine "
                            + "started.")
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.MILLISECONDS)
            .build();

    private static final Metadata THREAD_COUNT = Metadata.builder()
            .withName("thread.count")
            .withDisplayName("Thread Count")
            .withDescription("Displays the current number of live threads including both "
                            + "daemon and nondaemon threads")
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata THREAD_DAEMON_COUNT = Metadata.builder()
            .withName("thread.daemon.count")
            .withDisplayName("Daemon Thread Count")
            .withDescription("Displays the current number of live daemon threads.")
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata THREAD_MAX_COUNT = Metadata.builder()
            .withName("thread.max.count")
            .withDisplayName("Peak Thread Count")
            .withDescription("Displays the peak live thread count since the Java "
                            + "virtual machine started or "
                            + "peak was reset. This includes daemon and "
                            + "non-daemon threads.")
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata CL_LOADED_COUNT = Metadata.builder()
            .withName("classloader.loadedClasses.count")
            .withDisplayName("Current Loaded Class Count")
            .withDescription("Displays the number of classes that are currently loaded in "
                            + "the Java virtual machine.")
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata CL_LOADED_TOTAL = Metadata.builder()
            .withName("classloader.loadedClasses.total")
            .withDisplayName("Total Loaded Class Count")
            .withDescription("Displays the total number of classes that have been loaded "
                            + "since the Java virtual machine has started execution.")
            .withType(MetricType.COUNTER)
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata CL_UNLOADED_COUNT = Metadata.builder()
            .withName("classloader.unloadedClasses.total")
            .withDisplayName("Total Unloaded Class Count")
            .withDescription("Displays the total number of classes unloaded since the Java "
                            + "virtual machine has started execution.")
            .withType(MetricType.COUNTER)
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata OS_AVAILABLE_CPU = Metadata.builder()
            .withName("cpu.availableProcessors")
            .withDisplayName("Available Processors")
            .withDescription("Displays the number of processors available to the Java "
                            + "virtual machine. This "
                            + "value may change during a particular invocation of"
                            + " the virtual machine.")
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.NONE)
            .build();

    private static final Metadata OS_LOAD_AVERAGE = Metadata.builder()
            .withName("cpu.systemLoadAverage")
            .withDisplayName("System Load Average")
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
            .withType(MetricType.GAUGE)
            .withUnit(MetricUnits.NONE)
            .build();

    private final Config config;

    private BaseRegistry(Config config) {
        super(Type.BASE);
        this.config = config;
    }

    public static Registry create(Config config) {

        BaseRegistry result = new BaseRegistry(config);

        if (!config.get(BASE_ENABLED_KEY).asBoolean().orElse(true)) {
            return result;
        }
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        // load all base metrics
        register(result, MEMORY_USED_HEAP, (Gauge<Long>) () -> memoryBean.getHeapMemoryUsage().getUsed());
        register(result, MEMORY_COMMITTED_HEAP, (Gauge<Long>) () -> memoryBean.getHeapMemoryUsage().getCommitted());
        register(result, MEMORY_MAX_HEAP, (Gauge<Long>) () -> memoryBean.getHeapMemoryUsage().getMax());

        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        register(result, JVM_UPTIME, (Gauge<Long>) runtimeBean::getUptime);

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        register(result, THREAD_COUNT, (Gauge<Integer>) threadBean::getThreadCount);
        register(result, THREAD_DAEMON_COUNT, (Gauge<Integer>) threadBean::getDaemonThreadCount);
        register(result, THREAD_MAX_COUNT, (Gauge<Integer>) threadBean::getPeakThreadCount);

        ClassLoadingMXBean clBean = ManagementFactory.getClassLoadingMXBean();
        register(result, CL_LOADED_COUNT, (Gauge<Integer>) clBean::getLoadedClassCount);
        register(result, CL_LOADED_TOTAL, (SimpleCounter) clBean::getTotalLoadedClassCount);
        register(result, CL_UNLOADED_COUNT, (SimpleCounter) clBean::getUnloadedClassCount);

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        register(result, OS_AVAILABLE_CPU, (Gauge<Integer>) osBean::getAvailableProcessors);
        register(result, OS_LOAD_AVERAGE, (Gauge<Double>) osBean::getSystemLoadAverage);

        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String poolName = gcBean.getName();
            register(result, gcCountMeta(), (SimpleCounter) gcBean::getCollectionCount,
                    new Tag("name", poolName));
            register(result, gcTimeMeta(), (Gauge<Long>) gcBean::getCollectionTime,
                    new Tag("name", poolName));
        }

        return result;
    }

    private static Metadata gcTimeMeta() {
        return Metadata.builder()
                .withName("gc.time")
                .withDisplayName("Garbage Collection Time")
                .withDescription(
                            "Displays the approximate accumulated collection elapsed time in milliseconds. "
                                    + "This attribute displays -1 if the collection elapsed time is undefined for this "
                                    + "collector. The Java virtual machine implementation may use a high resolution "
                                    + "timer to measure the elapsed time. This attribute may display the same value "
                                    + "even if the collection count has been incremented if the collection elapsed "
                                    + "time is very short.")
                .withType(MetricType.GAUGE)
                .withUnit(MetricUnits.MILLISECONDS)
                .build();
    }

    private static Metadata gcCountMeta() {
        return Metadata.builder()
                .withName("gc.total")
                .withDisplayName("Garbage Collection Count")
                .withDescription(
                            "Displays the total number of collections that have occurred. This attribute lists "
                                    + "-1 if the collection count is undefined for this collector.")
                .withType(MetricType.COUNTER)
                .withUnit(MetricUnits.NONE)
                .build();
    }

    private static void register(BaseRegistry registry, Metadata meta, Metric metric, Tag... tags) {
        if (registry.config.get(CONFIG_METRIC_ENABLED_BASE + meta.getName() + ".enabled").asBoolean().orElse(true)) {
            registry.register(meta, metric, tags);
        }
    }

    @FunctionalInterface
    private interface SimpleCounter extends Counter {
        @Override
        default void inc() {
            throw new IllegalStateException("Cannot increase a system counter");
        }

        @Override
        default void inc(long n) {
            throw new IllegalStateException("Cannot increase a system counter");
        }
    }
}
