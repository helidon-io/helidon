/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.metrics.api.spi.MetricFactory;

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
 *     Between the required MicroProfile {@link MetricRegistry} API and useful or efficient ways to deal with metadata,
 *     metrics, and metric IDs, there is a bewildering set of method signatures that can update or query the data structures
 *     holding all this information. That, plus the type generality, makes for quite the class here.
 * </p>
 */
class MetricStore {

    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

    private final Map<MetricID, HelidonMetric> allMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<MetricID>> allMetricIDsByName = new ConcurrentHashMap<>();
    private final Map<String, Metadata> allMetadata = new ConcurrentHashMap<>(); // metric name -> metadata

    private volatile RegistrySettings registrySettings;
    private final MetricFactory metricFactory;
    private final MetricFactory noOpMetricFactory = new NoOpMetricFactory();
    private final String scope;
    private final BiConsumer<MetricID, HelidonMetric> doRemove;

    static MetricStore create(RegistrySettings registrySettings,
                              MetricFactory metricFactory,
                              String scope,
                              BiConsumer<MetricID, HelidonMetric> doRemove) {
        return new MetricStore(registrySettings,
                               metricFactory,
                               scope,
                               doRemove);

    }

    private MetricStore(RegistrySettings registrySettings,
                        MetricFactory metricFactory,
                        String scope,
                        BiConsumer<MetricID, HelidonMetric> doRemove) {
        this.registrySettings = registrySettings;
        this.metricFactory = metricFactory;
        this.scope = scope;
        this.doRemove = doRemove;
    }

    void update(RegistrySettings registrySettings) {
        this.registrySettings = registrySettings;
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

    <U extends Metric> U getOrRegisterMetric(Metadata newMetadata, Class<U> clazz, Tag... tags) {
        Class<? extends Metric> newBaseType = baseMetricClass(clazz);
        return writeAccess(() -> {
            HelidonMetric metric = getMetricLocked(newMetadata.getName(), tags);
            if (metric == null) {
                MetricID newMetricID = new MetricID(newMetadata.getName(), tags);
                ensureTagNamesConsistent(newMetricID);
                getConsistentMetadataLocked(newMetadata);
                metric = registerMetricLocked(newMetricID,
                                              createEnabledAwareMetric(clazz, newMetadata, tags));
                return clazz.cast(metric);
            }
            ensureConsistentMetricTypes(metric, newBaseType, () -> new MetricID(newMetadata.getName(), tags));
            enforceConsistentMetadata(metric.metadata(), newMetadata);
            return clazz.cast(metric);
        });
    }

    <T, R extends Number> Gauge<R> getOrRegisterGauge(String name, T object, Function<T, R> func, Tag... tags) {
        return getOrRegisterGauge(() -> getMetricLocked(name, tags),
                                  () -> getConsistentMetadataLocked(name),
                                  () -> new MetricID(name, tags),
                                  (Metadata metadata) -> metricFactory.gauge(scope, metadata, object, func, tags));
    }

    <R extends Number> Gauge<R> getOrRegisterGauge(String name, Supplier<R> valueSupplier, Tag... tags) {
        return getOrRegisterGauge(() -> getMetricLocked(name, tags),
                                  () -> getConsistentMetadataLocked(name),
                                  () -> new MetricID(name, tags),
                                  (Metadata metadata) -> metricFactory.gauge(scope, metadata, valueSupplier, tags));
    }

    <T, R extends Number> Gauge<R> getOrRegisterGauge(Metadata newMetadata,
                                                      T object,
                                                      Function<T, R> valueFunction,
                                                      Tag... tags) {
        return getOrRegisterGauge(() -> getMetricLocked(newMetadata.getName(), tags),
                                  () -> getConsistentMetadataLocked(newMetadata),
                                  () -> new MetricID(newMetadata.getName(), tags),
                                  (Metadata metadata) -> metricFactory.gauge(scope, newMetadata, object, valueFunction, tags));
    }

    <R extends Number> Gauge<R> getOrRegisterGauge(Metadata newMetadata,
                                                   Supplier<R> valueSupplier,
                                                   Tag... tags) {
        String metricName = newMetadata.getName();
        return getOrRegisterGauge(() -> getMetricLocked(metricName, tags),
                                  () -> getConsistentMetadataLocked(newMetadata),
                                  () -> new MetricID(metricName, tags),
                                  (Metadata metadata) -> metricFactory.gauge(scope, newMetadata, valueSupplier, tags));
    }

    <T, R extends Number> Gauge<R> getOrRegisterGauge(MetricID metricID, T object, Function<T, R> valueFunction) {
        return getOrRegisterGauge(() -> allMetrics.get(metricID),
                                  () -> allMetadata.get(metricID.getName()),
                                  () -> metricID,
                                  (Metadata metadata) -> metricFactory.gauge(scope, metadata, object, valueFunction));
    }

    <R extends Number> Gauge<R> getOrRegisterGauge(MetricID metricID, Supplier<R> valueSupplier) {
        return getOrRegisterGauge(() -> allMetrics.get(metricID),
                                  () -> allMetadata.get(metricID.getName()),
                                  () -> metricID,
                                  (Metadata metadata) -> metricFactory.gauge(scope, metadata, valueSupplier));
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

    private <R extends Number> Gauge<R> getOrRegisterGauge(Supplier<HelidonMetric> metricFinder,
                                                           Supplier<Metadata> metadataFinder,
                                                           Supplier<MetricID> metricIDSupplier,
                                                           Function<Metadata, Gauge<R>> gaugeFactory) {
        return writeAccess(() -> {
            HelidonMetric metric = metricFinder.get();
            if (metric == null) {
                Metadata metadata = metadataFinder.get();
                metric = registerMetricLocked(metricIDSupplier.get(),
                                              createEnabledAwareGauge(metadata, gaugeFactory));
            }
            return (Gauge<R>) metric;
        });
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
                }
                HelidonMetric doomedMetric = allMetrics.remove(metricID);
                if (doomedMetric != null) {
                    doomedMetric.markAsDeleted();
                }
                doRemove.accept(metricID, doomedMetric);
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
                HelidonMetric metric = allMetrics.get(metricID);
                if (metric != null) {
                    metric.markAsDeleted();
                    result |= allMetrics.remove(metricID) != null;
                    doRemove.accept(metricID, metric);
                }
            }
            allMetricIDsByName.remove(name);
            allMetadata.remove(name);

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

    MetricsForMetadata metadataWithIDs(String metricName) {
        return readAccess(() -> {
                              Metadata metadata = allMetadata.get(metricName);
                              List<MetricID> metricIDs = allMetricIDsByName.get(metricName);
                              return (metadata == null || metricIDs == null || metricIDs.isEmpty())
                                      ? null
                                      : new MetricsForMetadata(metadata, metricIDs);
                          }
        );
    }

    HelidonMetric metric(MetricID metricID) {
        return allMetrics.get(metricID);
    }

    Map<String, Metadata> metadata() {
        return allMetadata;
    }

    Metadata metadata(String metricName) {
        return allMetadata.get(metricName);
    }

    Map<MetricID, HelidonMetric> metrics() {
        return allMetrics;
    }

    /**
     * Returns the metric ID and metric matching the specified name which either has no tags or was the first metric with that
     * name registered.
     *
     * @param metricName metric name to find
     * @return matching metric; null if no metric is registered with the specified name
     */
    MetricInstance untaggedOrFirstMetricInstance(String metricName) {
        return readAccess(() -> {
            List<MetricID> metricIDs = allMetricIDsByName.get(metricName);
            if (metricIDs == null || metricIDs.isEmpty()) {
                return null;
            }
            MetricID metricID = null;
            for (MetricID candidate : metricIDs) {
                if (metricID == null || candidate.getTags().isEmpty()) {
                    metricID = candidate;
                }
            }
            return new MetricInstance(metricID, allMetrics.get(metricID));
        });
    }

    List<MetricInstance> metricsWithIDs(String metricName) {
        return readAccess(() -> {
            List<MetricID> metricIDs = allMetricIDsByName.get(metricName);
            if (metricIDs == null) {
                return Collections.emptyList();
            }
            List<MetricInstance> result = new ArrayList<>();
            for (MetricID metricID : metricIDs) {
                result.add(new MetricInstance(metricID, allMetrics.get(metricID)));
            }
            return result;
        });
    }

    List<MetricID> metricIDs(String metricName) {
        return new ArrayList<>(allMetricIDsByName.get(metricName));
    }

    Stream<MetricInstance> stream() {
        return allMetrics.entrySet()
                .stream()
                .filter(entry -> registrySettings.isMetricEnabled(entry.getKey().getName()))
                .map(it -> new MetricInstance(it.getKey(), it.getValue()));
    }

    private void removeLocked(Map.Entry<MetricID, HelidonMetric> entry) {
        remove(entry.getKey());
    }

    private <U extends Metric> U getOrRegisterMetric(String metricName,
                                                     Class<U> clazz,
                                                     Supplier<HelidonMetric> metricFactory,
                                                     Supplier<MetricID> metricIDFactory,
                                                     Supplier<Metadata> metadataFactory,
                                                     Tag... tags) {
        Class<? extends Metric> newBaseType = baseMetricClass(clazz);
        return writeAccess(() -> {
            HelidonMetric metric = metricFactory.get();
            MetricID newMetricID = metricIDFactory.get();
            if (metric == null) {
                    ensureTagNamesConsistent(newMetricID);
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

    private HelidonMetric getMetricLocked(String metricName, Tag... tags) {
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

    private HelidonMetric registerMetricLocked(MetricID metricID, HelidonMetric metric) {
        allMetrics.put(metricID, metric);
        allMetricIDsByName
                .computeIfAbsent(metricID.getName(), k -> new ArrayList<>())
                .add(metricID);
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
            enforceConsistentMetadata(metadata, newMetadata);
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
        allMetadata.put(metadata.getName(), metadata);
        return metadata;
    }

    private void ensureTagNamesConsistent(MetricID existingID, MetricID newID) {
        Set<String> existingTagNames = existingID.getTags().keySet();
        Set<String> newTagNames = newID.getTags().keySet();
        if (!existingTagNames.equals(newTagNames)) {
            throw new IllegalArgumentException("Inconsistent tag names between two metrics with the same name '"
                                                       + existingID.getName() + "'; previously-registered tag names: "
                                               + existingTagNames + ", proposed tag names: " + newTagNames);
        }
    }

    private void ensureTagNamesConsistent(MetricID newID) {
        // See if there is a matching metric using only the name; if so, make sure
        // the tag names for the two metric IDs are the same. We
        // only need to check the first same-named metric because
        // any others would have already passed this check before being added.
        List<MetricID> sameNamedMetricIDs = allMetricIDsByName.get(newID.getName());
        if (sameNamedMetricIDs != null && !sameNamedMetricIDs.isEmpty()) {
            ensureTagNamesConsistent(sameNamedMetricIDs.get(0), newID);
        }
    }

    private void ensureConsistentMetricTypes(HelidonMetric existingMetric,
                                             Class<? extends Metric> newBaseType,
                                             Supplier<MetricID> metricIDSupplier) {
        if (!baseMetricClass(existingMetric.getClass()).isAssignableFrom(newBaseType)) {
            MetricID tempID = metricIDSupplier.get();
            throw new IllegalArgumentException(
                    "Attempt to register new metric of type " + newBaseType.getName()
                            + " when previously-registered metric "
                            + tempID.getName()
                            + Arrays.asList(tempID.getTagsAsArray())
                            + " is of incompatible type " + existingMetric.getClass());
        }
    }

    private <U extends Metric> HelidonMetric createEnabledAwareMetric(Class<U> clazz, Metadata metadata, Tag... tags) {
        String metricName = metadata.getName();
        Class<? extends Metric> baseClass = baseMetricClass(clazz);
        Metric result;
        MetricFactory factoryToUse = registrySettings.isMetricEnabled(metricName) ? metricFactory : noOpMetricFactory;
        if (baseClass.isAssignableFrom(Counter.class)) {
            result = factoryToUse.counter(scope, metadata, tags);
        } else if (baseClass.isAssignableFrom(Histogram.class)) {
            result = factoryToUse.summary(scope, metadata, tags);
        } else if (baseClass.isAssignableFrom(Timer.class)) {
            result = factoryToUse.timer(scope, metadata, tags);
        } else {
            throw new IllegalArgumentException("Cannot identify correct metric type for " + clazz.getName());
        }
        return (HelidonMetric) result;
    }

    private <R extends Number> HelidonMetric createEnabledAwareGauge(Metadata metadata,
                                                                     Function<Metadata, Gauge<R>> gaugeFactory) {
        String metricName = metadata.getName();
        return (HelidonMetric) (registrySettings.isMetricEnabled(metricName)
                ? gaugeFactory.apply(metadata)
                : noOpMetricFactory.gauge(scope, metadata, null));
    }

//    private <T extends HelidonMetric, U extends Metric> U toType(T m1, Class<U> clazz) {
//        MetricType type1 = toType(m1);
//        MetricType type2 = MetricType.from(clazz);
//        if (type1 == type2) {
//            return clazz.cast(m1);
//        }
//        throw new IllegalArgumentException("Metric types " + type1.toString()
//                                                   + " and " + type2.toString() + " do not match");
//    }
//
//    private MetricType toType(Metric metric) {
//        Class<? extends Metric> clazz = toMetricClass(metric);
//        return MetricType.from(clazz == null ? metric.getClass() : clazz);
//    }

    private static <T extends Metric> Class<? extends Metric> toMetricClass(T metric) {
        // Find subtype of Metric, needed for user-defined metrics
        Class<?> clazz = metric.getClass();
        do {
            Optional<Class<?>> optionalClass = Arrays.stream(clazz.getInterfaces())
                    .filter(Metric.class::isAssignableFrom)
                    .findFirst();
            if (optionalClass.isPresent()) {
                clazz = optionalClass.get();
                break;
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        return (Class<? extends Metric>) clazz;
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

    private static boolean tagsMatch(Tag[] tags, Map<String, String> tagMap) {
        Map<String, String> newTags = new TreeMap<>();
        for (Tag tag : tags) {
            newTags.put(tag.getTagName(), tag.getTagValue());
        }
        return newTags.equals(tagMap);
    }

    private static void enforceConsistentMetadata(Metadata existingMetadata, Metadata newMetadata) {
        if (!metadataMatches(existingMetadata, newMetadata)) {
            throw new IllegalArgumentException("New metadata conflicts with existing metadata with the same name; existing: "
                                                       + existingMetadata + ", new: "
                                                       + newMetadata);
        }
    }

    static <T extends Metadata, U extends Metadata> boolean metadataMatches(T a, U b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getName().equals(b.getName())
                && Objects.equals(a.getDescription(), b.getDescription())
                && Objects.equals(a.getUnit(), b.getUnit());
    }
}
