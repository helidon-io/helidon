/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MetricsConfig;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Abstracts the multiple data stores used for holding metrics information and the various ways of accessing and updating them.
 * <p>
 * Between the required MicroProfile {@link MetricRegistry} API and useful or efficient ways to deal with metadata,
 * metrics, and metric IDs, there is a bewildering set of method signatures that can update or query the data structures
 * holding all this information. That, plus the type generality, makes for quite the class here.
 * </p>
 */
class MetricStore {

    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

    private final Map<MetricID, HelidonMetric<?>> allMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<MetricID>> allMetricIDsByName = new ConcurrentHashMap<>();
    private final Map<String, Metadata> allMetadata = new ConcurrentHashMap<>(); // metric name -> metadata
    private final Map<String, Set<String>> tagNameSets = new HashMap<>(); // metric name -> tag names
    private final Map<String, Class<? extends Metric>> metricTypes = new HashMap<>(); // metric name -> base type of the metric
    private final MetricFactory metricFactory;
    private final String scope;
    private final Consumer<HelidonMetric<?>> doRemove;
    private volatile MetricsConfig metricsConfig;

    private MetricStore(MetricsConfig metricsConfig,
                        MetricFactory metricFactory,
                        String scope,
                        Consumer<HelidonMetric<?>> doRemove) {
        this.metricsConfig = metricsConfig;
        this.metricFactory = metricFactory;
        this.scope = scope;
        this.doRemove = doRemove;
    }

    static MetricStore create(MetricsConfig metricsConfig,
                              MetricFactory metricFactory,
                              String scope,
                              Consumer<HelidonMetric<?>> doRemove) {
        return new MetricStore(metricsConfig,
                               metricFactory,
                               scope,
                               doRemove);

    }

    void update(MetricsConfig metricsConfig) {
        this.metricsConfig = metricsConfig;
    }

    <U extends Metric> U getOrRegisterMetric(MetricID metricID, Class<U> clazz) {
        return getOrRegisterMetric(metricID.getName(),
                                   clazz,
                                   () -> allMetrics.get(metricID),
                                   () -> metricID,
                                   () -> getConsistentMetadataLocked(metricID.getName()));
    }

    <U extends Metric> U getOrRegisterMetric(String metricName, Class<U> clazz, Tag... tags) {
        return getOrRegisterMetric(metricName,
                                   clazz,
                                   () -> getMetricLocked(metricName, tags),
                                   () -> new MetricID(metricName, tags),
                                   () -> getConsistentMetadataLocked(metricName),
                                   tags);
    }

    <U extends Metric, M extends Meter> U getOrRegisterMetric(Metadata newMetadata, Class<U> clazz, Tag... tags) {
        return getOrRegisterMetric(() -> createEnabledAwareMetric(clazz, newMetadata, tags),
                                   newMetadata,
                                   clazz,
                                   tags);
    }

    <U extends Metric> U getOrRegisterMetric(Supplier<HelidonMetric<?>> metricFactory,
                                             Metadata newMetadata,
                                             Class<U> clazz,
                                             Tag... tags) {
        Class<? extends Metric> newBaseType = baseMetricClass(clazz);
        return writeAccess(() -> {
            enforceConsistentType(newMetadata.getName(), clazz);
            MetricID newMetricID = new MetricID(newMetadata.getName(), tags);
            checkOrStoreTagNames(newMetricID.getName(),
                                 newMetricID.getTags().keySet());
            checkOrStoreMetadata(newMetadata);
            HelidonMetric<?> metric = getMetricLocked(newMetadata.getName(), tags);
            if (metric == null) {
                getConsistentMetadataLocked(newMetadata);
                metric = registerMetricLocked(newMetricID,
                                              metricFactory.get());
                return clazz.cast(metric);
            }
            ensureConsistentMetricTypes(metric, newBaseType, () -> newMetricID);
            return clazz.cast(metric);
        });
    }

    <R extends Number> Gauge<R> getOrRegisterGauge(String name, Supplier<R> valueSupplier, Tag... tags) {
        return getOrRegisterGauge(() -> getMetricLocked(name, tags),
                                  () -> getConsistentMetadataLocked(name),
                                  () -> new MetricID(name, tags),
                                  (Metadata metadata) -> metricFactory.gauge(scope,
                                                                             metadata,
                                                                             valueSupplier,
                                                                             tags));
    }

    <R extends Number> Gauge<R> getOrRegisterGauge(Metadata newMetadata,
                                                   Supplier<R> valueSupplier,
                                                   Tag... tags) {
        String metricName = newMetadata.getName();
        return getOrRegisterGauge(() -> getMetricLocked(metricName, tags),
                                  () -> getConsistentMetadataLocked(newMetadata),
                                  () -> new MetricID(metricName, tags),
                                  (Metadata metadata) -> metricFactory.gauge(scope,
                                                                             newMetadata,
                                                                             valueSupplier,
                                                                             tags));
    }

    <R extends Number> Gauge<R> getOrRegisterGauge(MetricID metricID, Supplier<R> valueSupplier) {
        return getOrRegisterGauge(() -> allMetrics.get(metricID),
                                  () -> allMetadata.get(metricID.getName()),
                                  () -> metricID,
                                  (Metadata metadata) -> metricFactory.gauge(scope,
                                                                             metadata,
                                                                             valueSupplier));
    }

    boolean remove(MetricID metricID) {
        return writeAccess(() -> {
            final List<MetricID> metricIDsForName = allMetricIDsByName.get(metricID.getName());
            if (metricIDsForName == null) {
                return false;
            } else {
                metricIDsForName.remove(metricID);
                if (metricIDsForName.isEmpty()) {
                    allMetricIDsByName.remove(metricID.getName());
                    allMetadata.remove(metricID.getName());
                    tagNameSets.remove(metricID.getName());
                    metricTypes.remove(metricID.getName());
                }
                HelidonMetric<?> doomedMetric = allMetrics.remove(metricID);
                if (doomedMetric != null) {
                    doomedMetric.markAsDeleted();
                }
                doRemove.accept(doomedMetric);
                return doomedMetric != null;
            }
        });
    }

    boolean remove(String name) {
        return writeAccess(() -> {
            final List<MetricID> doomedMetricsIDs = allMetricIDsByName.get(name);
            if (doomedMetricsIDs == null) {
                return false;
            }
            boolean result = false;
            for (MetricID metricID : doomedMetricsIDs) {
                HelidonMetric<?> doomedMetric = allMetrics.get(metricID);
                if (doomedMetric != null) {
                    doomedMetric.markAsDeleted();
                    result |= allMetrics.remove(metricID) != null;
                    doRemove.accept(doomedMetric);
                }
            }
            allMetricIDsByName.remove(name);
            allMetadata.remove(name);
            tagNameSets.remove(name);
            metricTypes.remove(name);
            return result;
        });

    }

    void removeMatching(MetricFilter filter) {
        writeAccess(() -> {
            try {
                allMetrics.entrySet()
                        .stream()
                        .filter(entry -> filter.matches(entry.getKey(), entry.getValue()))
                        .forEach(this::removeLocked);
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Error removing using filter " + filter, e);
            }
        });
    }

    SortedSet<String> getNames() {
        return new TreeSet<>(allMetricIDsByName.keySet());
    }

    SortedSet<MetricID> getMetricIDs() {
        return new TreeSet<>(allMetrics.keySet());
    }

    <V> SortedMap<MetricID, V> getSortedMetrics(MetricFilter filter, Class<V> metricClass) {
        Map<MetricID, V> collected = allMetrics.entrySet()
                .stream()
                .filter(it -> metricClass.isAssignableFrom(it.getValue().getClass()))
                .filter(it -> filter.matches(it.getKey(), it.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, it -> metricClass.cast(it.getValue())));

        return new TreeMap<>(collected);
    }

    HelidonMetric<?> metric(MetricID metricID) {
        return allMetrics.get(metricID);
    }

    Map<String, Metadata> metadata() {
        return allMetadata;
    }

    Metadata metadata(String metricName) {
        return allMetadata.get(metricName);
    }

    Map<MetricID, HelidonMetric<?>> metrics() {
        return allMetrics;
    }

    List<MetricID> metricIDs(String metricName) {
        return new ArrayList<>(allMetricIDsByName.get(metricName));
    }

    Stream<MetricInstance> stream() {
        return allMetrics.entrySet()
                .stream()
                .filter(entry -> metricsConfig.isMeterEnabled(entry.getKey().getName(), scope))
                .map(it -> new MetricInstance(it.getKey(), it.getValue()));
    }

    private static void enforceConsistentTagNames(String metricName, Set<String> existingTagNames, Set<String> newTagNames) {
        if (!existingTagNames.equals(newTagNames)) {
            throw new IllegalArgumentException(String.format(
                    "New tag names %s for metric %s conflict with existing tag names %s",
                    newTagNames,
                    metricName,
                    existingTagNames));
        }
    }

    private static void enforceConsistentMetadata(Metadata existingMetadata, Metadata newMetadata) {
        if (!metadataMatches(existingMetadata, newMetadata)) {
            throw new IllegalArgumentException("New metadata conflicts with existing metadata with the same name; existing: "
                                                       + existingMetadata + ", new: "
                                                       + newMetadata);
        }
    }

    private static <T extends Metadata, U extends Metadata> boolean metadataMatches(T a, U b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        // Try to merge description and units.
        return a.getName().equals(b.getName())
                && Objects.equals(a.getDescription(), b.getDescription())
                && Objects.equals(a.getUnit(), b.getUnit());
    }

    private static Class<? extends Metric> baseMetricClass(Class<?> clazz) {

        for (Class<? extends Metric> baseClass : RegistryFactory.METRIC_TYPES) {
            if (baseClass.isAssignableFrom(clazz)) {
                return baseClass;
            }
        }
        throw new IllegalArgumentException("Unable to map metric type "
                                                   + clazz.getName()
                                                   + " to one of "
                                                   + RegistryFactory.METRIC_TYPES);
    }

    private static boolean tagsMatch(Tag[] tags, Map<String, String> tagMap) {
        Map<String, String> newTags = new TreeMap<>();
        for (Tag tag : tags) {
            newTags.put(tag.getTagName(), tag.getTagValue());
        }
        return newTags.equals(tagMap);
    }

    /**
     * If tag names are already associated with the metric name, throws an exception if the existing and proposed tag name sets
     * are inconsistent; if there are no tag names stored for this name, store the proposed ones.
     *
     * @param metricName metrics name
     * @param tagNames   tag names to validate
     * @return the {@link Set} of tag names
     * @throws java.lang.IllegalArgumentException if tag names have been registered for this name which are inconsistent with
     *                                            the proposed tag names
     */
    private Set<String> checkOrStoreTagNames(String metricName, Set<String> tagNames) {

        Set<String> currentTagNames = tagNameSets.get(metricName);
        if (currentTagNames == null) {
            return tagNameSets.put(metricName, tagNames);
        }
        enforceConsistentTagNames(metricName, currentTagNames, tagNames);
        return tagNames;
    }

    /**
     * If metadata is already associated with the metadata name, throws an exception if the existing and proposed metadata are
     * inconsistent; if there is no existing metadata stored for this name, stores it.
     *
     * @param candidateMetadata proposed metadata
     * @return the metadata
     * @throws java.lang.IllegalArgumentException if metadata has been registered for this name which is inconsistent with
     *                                            the proposed metadata
     */
    private Metadata checkOrStoreMetadata(Metadata candidateMetadata) {
        Metadata currentMetadata = allMetadata.get(candidateMetadata.getName());
        if (currentMetadata == null) {
            return allMetadata.put(candidateMetadata.getName(), candidateMetadata);
        }
        enforceConsistentMetadata(currentMetadata, candidateMetadata);
        return candidateMetadata;
    }

    private void enforceConsistentType(String metricName, Class<? extends Metric> newType) {
        Class<? extends Metric> metricType = metricTypes.get(metricName);
        if (metricType != null) {
            if (!metricType.isAssignableFrom(newType)) {
                throw new IllegalArgumentException(String.format(
                        "Attempt to register metric %s of type %s but the name is already associated with a metric of type %s",
                        metricName,
                        newType.getName(),
                        metricType.getName()));
            }
        }
    }

    private <R extends Number> Gauge<R> getOrRegisterGauge(Supplier<HelidonMetric<?>> metricFinder,
                                                           Supplier<Metadata> metadataFinder,
                                                           Supplier<MetricID> metricIDSupplier,
                                                           Function<Metadata, Gauge<R>> gaugeFactory) {
        return writeAccess(() -> {
            Metadata metadata = metadataFinder.get();
            enforceConsistentType(metadata.getName(), Gauge.class);
            HelidonMetric<?> metric = metricFinder.get();
            if (metric == null) {
                metric = registerMetricLocked(metricIDSupplier.get(),
                                              createEnabledAwareGauge(metadata, gaugeFactory));
            }
            return (Gauge<R>) metric;
        });
    }

    private void removeLocked(Map.Entry<MetricID, HelidonMetric<?>> entry) {
        remove(entry.getKey());
    }

    private <U extends Metric> U getOrRegisterMetric(String metricName,
                                                     Class<U> clazz,
                                                     Supplier<HelidonMetric<?>> metricFactory,
                                                     Supplier<MetricID> metricIDFactory,
                                                     Supplier<Metadata> metadataFactory,
                                                     Tag... tags) {
        Class<? extends Metric> newBaseType = baseMetricClass(clazz);
        return writeAccess(() -> {
            enforceConsistentType(metricName, clazz);
            HelidonMetric<?> metric = metricFactory.get();
            MetricID newMetricID = metricIDFactory.get();
            checkOrStoreTagNames(newMetricID.getName(),
                                 newMetricID.getTags().keySet());
            if (metric == null) {
                Metadata metadata = metadataFactory.get();
                if (metadata == null) {
                    metadata = registerMetadataLocked(metricName);
                }
                metric = registerMetricLocked(newMetricID,
                                              createEnabledAwareMetric(clazz, metadata, tags));
            } else {
                ensureConsistentMetricTypes(metric, newBaseType, metricIDFactory);
                Metadata existingMetadata = metadataFactory.get();
                if (existingMetadata == null) {
                    throw new IllegalStateException("Could not find existing metadata under name "
                                                            + metricName + " for existing metric " + metricIDFactory.get());
                }
            }
            return clazz.cast(metric);
        });
    }

    private HelidonMetric<?> getMetricLocked(String metricName, Tag... tags) {
        List<MetricID> metricIDsForName = allMetricIDsByName.get(metricName);
        if (metricIDsForName == null) {
            return null;
        }
        for (MetricID metricID : metricIDsForName) {
            if (metricID.getName().equals(metricName) && tagsMatch(tags, metricID.getTags())) {
                return allMetrics.get(metricID);
            }
        }
        return null;
    }

    private HelidonMetric<?> registerMetricLocked(MetricID metricID, HelidonMetric<?> metric) {
        if (metricsConfig.isMeterEnabled(metricID.getName(), scope)) {
            allMetrics.put(metricID, metric);
            allMetricIDsByName
                    .computeIfAbsent(metricID.getName(), k -> new ArrayList<>())
                    .add(metricID);
        }
        return metric;
    }

    private Metadata getConsistentMetadataLocked(String metricName) {
        Metadata result = allMetadata.get(metricName);
        if (result == null) {
            result = registerMetadataLocked(metricName);
        }
        return result;
    }

    private Metadata getConsistentMetadataLocked(Metadata newMetadata) {
        Metadata metadata = allMetadata.get(newMetadata.getName());
        if (metadata != null) {
            checkOrStoreMetadata(newMetadata);
        } else {
            registerMetadataLocked(newMetadata);
        }
        return newMetadata;
    }

    private Metadata registerMetadataLocked(String metricName) {
        return registerMetadataLocked(Metadata.builder()
                                              .withName(metricName)
                                              .withUnit(MetricUnits.NONE)
                                              .build());
    }

    private Metadata registerMetadataLocked(Metadata metadata) {
        // At least for now, store the metadata even if the metric name in this scope is disabled by configuration.
        // This lets us do consistency checks across like-named metrics.
        checkOrStoreMetadata(metadata);
        allMetadata.put(metadata.getName(), metadata);
        return metadata;
    }

    private void ensureConsistentMetricTypes(HelidonMetric<?> existingMetric,
                                             Class<? extends Metric> newBaseType,
                                             Supplier<MetricID> metricIDSupplier) {
        if (!baseMetricClass(existingMetric.getClass()).isAssignableFrom(newBaseType)) {
            MetricID tempID = metricIDSupplier.get();
            throw new IllegalArgumentException(
                    "Attempt to register new metric of type " + newBaseType.getName()
                            + " when previously-registered metric "
                            + tempID.getName()
                            + Arrays.asList(tempID.getTagsAsArray())
                            + " is of incompatible type " + baseMetricClass(existingMetric.getClass()));
        }
    }

    private <U extends Metric> HelidonMetric<?> createEnabledAwareMetric(Class<U> clazz, Metadata metadata, Tag... tags) {
        Class<? extends Metric> baseClass = baseMetricClass(clazz);
        Metric result;
        if (baseClass.isAssignableFrom(Counter.class)) {
            result = metricFactory.counter(scope, metadata, tags);
        } else if (baseClass.isAssignableFrom(Histogram.class)) {
            result = metricFactory.summary(scope, metadata, tags);
        } else if (baseClass.isAssignableFrom(Timer.class)) {
            result = metricFactory.timer(scope, metadata, tags);
        } else {
            throw new IllegalArgumentException("Cannot identify correct metric type for " + clazz.getName());
        }
        return (HelidonMetric<?>) result;
    }

    private <R extends Number> HelidonMetric<?> createEnabledAwareGauge(Metadata metadata,
                                                                        Function<Metadata, Gauge<R>> gaugeFactory) {
        return (HelidonMetric<?>) (gaugeFactory.apply(metadata));
    }

    private <S> S readAccess(Callable<S> action) {
        return access(lock.readLock(), action);
    }

    private <S> S writeAccess(Callable<S> action) {
        return access(lock.writeLock(), action);
    }

    private <S> S access(Lock lock, Callable<S> action) {
        lock.lock();
        try {
            return action.call();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }
}
