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
package io.helidon.metrics.api;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Abstracts the multiple data stores used for holding metrics information and the various ways of accessing and updating them.
 * <p>
 *     Between the required MicroProfile {@link MetricRegistry} API and useful or efficient ways to deal with metadata,
 *     metrics, and metric IDs, there is a bewildering set of method signatures that can update or query the data structures
 *     holding all this information. That, plus the type generality, makes for quite the class here.
 * </p>
 */
class MetricStore<M extends HelidonMetric> {

    private final ReadWriteLock lock = new ReentrantReadWriteLock(true);

    private final Map<MetricID, M> allMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<MetricID>> allMetricIDsByName = new ConcurrentHashMap<>();
    private final Map<String, Metadata> allMetadata = new ConcurrentHashMap<>(); // metric name -> metadata

    private volatile RegistrySettings registrySettings;
    private final Map<MetricType, BiFunction<String, Metadata, M>> metricFactories;
    private final AbstractRegistry.GaugeFactory.SupplierBased supplierBasedGaugeFactory;
    private final AbstractRegistry.GaugeFactory.FunctionBased functionBasedGaugeFactory;
    private final MetricRegistry.Type registryType;
    private final Class<M> metricClass;
    private final BiFunction<Metadata, Metric, M> toImpl;

    static <M extends HelidonMetric> MetricStore<M> create(RegistrySettings registrySettings,
                                                           Map<MetricType, BiFunction<String, Metadata, M>> metricFactories,
                                                           AbstractRegistry.GaugeFactory.SupplierBased supplierBasedGaugeFactory,
                                                           AbstractRegistry.GaugeFactory.FunctionBased functionBasedGaugeFactory,
                                                           MetricRegistry.Type registryType,
                                                           Class<M> metricClass,
                                                           BiFunction<Metadata, Metric, M> toImpl) {
        return new MetricStore<>(registrySettings,
                                 metricFactories,
                                 supplierBasedGaugeFactory,
                                 functionBasedGaugeFactory,
                                 registryType,
                                 metricClass,
                                 toImpl);

    }

    private MetricStore(RegistrySettings registrySettings,
                        Map<MetricType, BiFunction<String, Metadata, M>> metricFactories,
                        AbstractRegistry.GaugeFactory.SupplierBased supplierBasedGaugeFactory,
                        AbstractRegistry.GaugeFactory.FunctionBased functionBasedGaugeFactory,
                        MetricRegistry.Type registryType,
                        Class<M> metricClass,
                        BiFunction<Metadata, Metric, M> toImpl) {
        this.registrySettings = registrySettings;
        this.metricFactories = metricFactories;
        this.supplierBasedGaugeFactory = supplierBasedGaugeFactory;
        this.functionBasedGaugeFactory = functionBasedGaugeFactory;
        this.registryType = registryType;
        this.metricClass = metricClass;
        this.toImpl = toImpl;
    }

    void update(RegistrySettings registrySettings) {
        this.registrySettings = registrySettings;
    }

    <U extends Metric> U getOrRegisterMetric(MetricID metricID, Class<U> clazz) {
        return getOrRegisterMetric(metricID.getName(),
                                   clazz,
                                   () -> allMetrics.get(metricID),
                                   () -> metricID,
                                   () -> getConsistentMetadataLocked(metricID.getName(), MetricType.from(clazz)));
    }

    <U extends Metric> U getOrRegisterMetric(String metricName, Class<U> clazz, Tag... tags) {
        return getOrRegisterMetric(metricName,
                                   clazz,
                                   () -> getMetricLocked(metricName, tags),
                                   () -> new MetricID(metricName, tags),
                                   () -> getConsistentMetadataLocked(metricName, MetricType.from(clazz)));
    }

    <U extends Metric> U getOrRegisterMetric(Metadata newMetadata, Class<U> clazz, Tag... tags) {
        return writeAccess(() -> {
            M metric = getMetricLocked(newMetadata.getName(), tags);
            if (metric == null) {
                Metadata metadataToUse = newMetadata.getTypeRaw().equals(MetricType.INVALID)
                        ? Metadata.builder(newMetadata).withType(MetricType.from(clazz)).build()
                        : newMetadata;
                Metadata metadata = getConsistentMetadataLocked(metadataToUse);
                metric = registerMetricLocked(new MetricID(metadata.getName(), tags),
                                              createEnabledAwareMetric(clazz, metadata));
            } else {
                enforceConsistentMetadata(metric.metadata(), newMetadata);
            }
            return toType(metric, clazz);
        });
    }

    <T, R extends Number> Gauge<R> getOrRegisterGauge(String name, T object, Function<T, R> func, Tag... tags) {
        return getOrRegisterGauge(() -> getMetricLocked(name, tags),
                                  () -> getConsistentMetadataLocked(name, MetricType.GAUGE),
                                  () -> new MetricID(name, tags),
                                  (Metadata metadata) -> functionBasedGaugeFactory.createGauge(metadata,
                                                                                    object,
                                                                                    func));
    }

    <R extends Number> Gauge<R> getOrRegisterGauge(String name, Supplier<R> valueSupplier, Tag... tags) {
        return getOrRegisterGauge(() -> getMetricLocked(name, tags),
                                  () -> getConsistentMetadataLocked(name, MetricType.GAUGE),
                                  () -> new MetricID(name, tags),
                                  (Metadata metadata) -> supplierBasedGaugeFactory.createGauge(metadata,
                                                                                    valueSupplier));
    }

    <T, R extends Number> Gauge<R> getOrRegisterGauge(Metadata newMetadata,
                                                      T object,
                                                      Function<T, R> valueFunction,
                                                      Tag... tags) {
        return getOrRegisterGauge(() -> getMetricLocked(newMetadata.getName(), tags),
                                  () -> getConsistentMetadataLocked(newMetadata),
                                  () -> new MetricID(newMetadata.getName(), tags),
                                  (Metadata metadata) -> functionBasedGaugeFactory.createGauge(metadata,
                                                                                    object,
                                                                                    valueFunction));
    }

    <R extends Number> Gauge<R> getOrRegisterGauge(Metadata newMetadata,
                                                   Supplier<R> valueSupplier,
                                                   Tag... tags) {
        String metricName = newMetadata.getName();
        return getOrRegisterGauge(() -> getMetricLocked(metricName, tags),
                                  () -> getConsistentMetadataLocked(newMetadata),
                                  () -> new MetricID(metricName, tags),
                                  (Metadata metadata) -> supplierBasedGaugeFactory.createGauge(metadata,
                                                                                               valueSupplier));
    }

    <T, R extends Number> Gauge<R> getOrRegisterGauge(MetricID metricID, T object, Function<T, R> valueFunction) {
        return getOrRegisterGauge(() -> allMetrics.get(metricID),
                                  () -> allMetadata.get(metricID.getName()),
                                  () -> metricID,
                                  (Metadata metadata) -> functionBasedGaugeFactory.createGauge(metadata,
                                                                                               object,
                                                                                               valueFunction));
    }

    <R extends Number> Gauge<R> getOrRegisterGauge(MetricID metricID, Supplier<R> valueSupplier) {
        return getOrRegisterGauge(() -> allMetrics.get(metricID),
                                  () -> allMetadata.get(metricID.getName()),
                                  () -> metricID,
                                  (Metadata metadata) -> supplierBasedGaugeFactory.createGauge(metadata,
                                                                                               valueSupplier));
    }

    private <R extends Number> Gauge<R> getOrRegisterGauge(Supplier<M> metricFinder,
                                                           Supplier<Metadata> metadataFinder,
                                                           Supplier<MetricID> metricIDSupplier,
                                                           Function<Metadata, Gauge<R>> gaugeFactory) {
        return writeAccess(() -> {
            M metric = metricFinder.get();
            if (metric == null) {
                Metadata metadata = metadataFinder.get();
                metric = registerMetricLocked(metricIDSupplier.get(),
                                              createEnabledAwareGauge(metadata, gaugeFactory));
            }
            return (Gauge<R>) metric;
        });
    }

    <U extends Metric> U register(Metadata metadata, U metric, Tag... tags) {
        return writeAccess(() -> {
            final String metricName = metadata.getName();
            getConsistentMetadataLocked(metadata);
            registerMetricLocked(new MetricID(metricName, tags), toImpl.apply(metadata, metric));
            return metric;
        });
    }

    <U extends Metric> U register(String name, U metric) {
        return writeAccess(() -> {
            Metadata metadata = getConsistentMetadataLocked(name, toType(metric));
            registerMetricLocked(new MetricID(name), toImpl.apply(metadata, metric));
            return metric;
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
                M doomedMetric = allMetrics.remove(metricID);
                if (doomedMetric != null) {
                    doomedMetric.markAsDeleted();
                }
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
                M metric = allMetrics.get(metricID);
                if (metric != null) {
                    metric.markAsDeleted();
                    result |= allMetrics.remove(metricID) != null;
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

    Map.Entry<Metadata, List<MetricID>> metadataWithIDs(String metricName) {
        return readAccess(() -> {
                              Metadata metadata = allMetadata.get(metricName);
                              List<MetricID> metricIDs = allMetricIDsByName.get(metricName);
                              return (metadata == null || metricIDs == null || metricIDs.isEmpty())
                                      ? null
                                      : new AbstractMap.SimpleEntry<>(metadata, metricIDs);
                          }
        );
    }

    M metric(MetricID metricID) {
        return allMetrics.get(metricID);
    }

    Map<String, Metadata> metadata() {
        return allMetadata;
    }

    Metadata metadata(String metricName) {
        return allMetadata.get(metricName);
    }

    Map<MetricID, M> metrics() {
        return allMetrics;
    }

    /**
     * Returns the metric ID and metric matching the specified name which either has no tags or was the first metric with that
     * name registered.
     *
     * @param metricName metric name to find
     * @return matching metric; null if no metric is registered with the specified name
     */
    Map.Entry<MetricID, M> untaggedOrFirstMetricWithID(String metricName) {
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
            return new AbstractMap.SimpleImmutableEntry<>(metricID, allMetrics.get(metricID));
        });
    }

    List<Map.Entry<MetricID, M>> metricsWithIDs(String metricName) {
        return readAccess(() -> {
            List<MetricID> metricIDs = allMetricIDsByName.get(metricName);
            if (metricIDs == null) {
                return Collections.emptyList();
            }
            List<Map.Entry<MetricID, M>> result = new ArrayList<>();
            for (MetricID metricID : metricIDs) {
                result.add(new AbstractMap.SimpleImmutableEntry<>(metricID, allMetrics.get(metricID)));
            }
            return result;
        });
    }

    List<MetricID> metricIDs(String metricName) {
        return new ArrayList<>(allMetricIDsByName.get(metricName));
    }

    Stream<Map.Entry<MetricID, M>> stream() {
        return allMetrics.entrySet().stream().filter(entry -> registrySettings.isMetricEnabled(entry.getKey().getName()));
    }

    private void removeLocked(Map.Entry<MetricID, M> entry) {
        remove(entry.getKey());
    }

    private <U extends Metric> U getOrRegisterMetric(String metricName,
                                                     Class<U> clazz,
                                                     Supplier<M> metricFactory,
                                                     Supplier<MetricID> metricIDFactory,
                                                     Supplier<Metadata> metadataFactory) {
        return writeAccess(() -> {
            M metric = metricFactory.get();
            if (metric == null) {
                try {
                    MetricType metricType = MetricType.from(clazz);
                    Metadata metadata = metadataFactory.get();
                    if (metadata == null) {
                        metadata = registerMetadataLocked(metricName, metricType);
                    }
                    metric = registerMetricLocked(metricIDFactory.get(),
                                                  createEnabledAwareMetric(clazz, metadata));
                } catch (Exception e) {
                    throw new RuntimeException("Error attempting to register new metric " + metricIDFactory.get(), e);
                }
            }
            return toType(metric, clazz);
        });
    }

    private M getMetricLocked(String metricName, Tag... tags) {
        List<MetricID> metricIDsForName = allMetricIDsByName.get(metricName);
        if (metricIDsForName == null) {
            return null;
        }
        for (MetricID metricID : metricIDsForName) {
            if (metricID.getName().equals(metricName) && Arrays.equals(metricID.getTagsAsArray(), tags)) {
                return allMetrics.get(metricID);
            }
        }
        return null;
    }

    private <T extends M> T registerMetricLocked(MetricID metricID, T metric) {
        allMetrics.put(metricID, metric);
        allMetricIDsByName
                .computeIfAbsent(metricID.getName(), k -> new ArrayList<>())
                .add(metricID);
        return metric;
    }

    private Metadata getConsistentMetadataLocked(String metricName, MetricType metricType) {
        Metadata result = allMetadata.get(metricName);
        if (result != null) {
            if (result.getTypeRaw() != metricType) {
                throw new IllegalArgumentException("Existing metadata has type "
                                                           + result.getType()
                                                           + " but "
                                                           + metricType
                                                           + " was requested");
            }
        } else {
            result = registerMetadataLocked(metricName, metricType);
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

    private Metadata registerMetadataLocked(String metricName, MetricType metricType) {
        return registerMetadataLocked(Metadata.builder()
                .withName(metricName)
                .withType(metricType)
                .withUnit(MetricUnits.NONE)
                .build());
    }

    private Metadata registerMetadataLocked(Metadata metadata) {
        allMetadata.put(metadata.getName(), metadata);
        return metadata;
    }

    private <U extends Metric> M createEnabledAwareMetric(Class<U> clazz, Metadata metadata) {
        String metricName = metadata.getName();
        MetricType metricType = MetricType.from(clazz);
        return registrySettings.isMetricEnabled(metricName)
                ? metricFactories.get(MetricType.from(clazz)).apply(registryType.getName(), metadata)
                : metricClass.cast(Proxy.newProxyInstance(
                        metricClass.getClassLoader(),
                        new Class<?>[] {clazz, metricClass},
                        new DisabledMetricInvocationHandler(metricType, metricName, metadata)));
    }

    private <R extends Number> M createEnabledAwareGauge(Metadata metadata, Function<Metadata, Gauge<R>> gaugeFactory) {
        String metricName = metadata.getName();
        return metricClass.cast(registrySettings.isMetricEnabled(metricName)
                                        ? gaugeFactory.apply(metadata)
                                        : Proxy.newProxyInstance(
                                                metricClass.getClassLoader(),
                                                new Class<?>[] {Gauge.class, metricClass},
                                                new DisabledMetricInvocationHandler(MetricType.GAUGE, metricName, metadata)));
    }

    private static class DisabledMetricInvocationHandler implements InvocationHandler {

        private final NoOpMetric delegate;

        DisabledMetricInvocationHandler(MetricType metricType, String metricName, Metadata metadata) {
            delegate = NoOpMetricRegistry.noOpMetricFactories().get(metricType).apply(metricName, metadata);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return method.invoke(delegate, args);
        }
    }

    private <T extends M, U extends Metric> U toType(T m1, Class<U> clazz) {
        MetricType type1 = toType(m1);
        MetricType type2 = MetricType.from(clazz);
        if (type1 == type2) {
            return clazz.cast(m1);
        }
        throw new IllegalArgumentException("Metric types " + type1.toString()
                                                   + " and " + type2.toString() + " do not match");
    }

    private MetricType toType(Metric metric) {
        Class<? extends Metric> clazz = toMetricClass(metric);
        return MetricType.from(clazz == null ? metric.getClass() : clazz);
    }

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
                && a.getTypeRaw().equals(b.getTypeRaw())
                && a.getDisplayName().equals(b.getDisplayName())
                && Objects.equals(a.getDescription(), b.getDescription())
                && Objects.equals(a.getUnit(), b.getUnit());
    }
}
