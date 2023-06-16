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
package io.helidon.metrics.microprofile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

class MpMetricRegistry implements MetricRegistry {

    public static final String MP_APPLICATION_TAG_NAME = "mp_app";

    public static final String MP_SCOPE_TAG_NAME = "mp_scope";

    private final String scope;
    private final MeterRegistry meterRegistry;
    private final Map<String, Metadata> metadata = new ConcurrentHashMap<>();
    private final Map<MetricID, MpMetric<?>> metricsById = new ConcurrentHashMap<>();
    private final Map<String, List<MetricID>> metricIdsByName = new ConcurrentHashMap<>();
    private final Map<String, List<MpMetric<?>>> metricsByName = new ConcurrentHashMap<>();

    private final ReentrantLock accessLock = new ReentrantLock();

    /**
     * Creates a new {@link MetricRegistry} with the given scope, delegating to the
     * given {@link io.micrometer.core.instrument.MeterRegistry}.
     *
     * @param scope scope for the metric registry
     * @param meterRegistry meter registry to which to delegate
     * @return new {@code MetricRegistry}
     */
    static MpMetricRegistry create(String scope, MeterRegistry meterRegistry) {
        return new MpMetricRegistry(scope, meterRegistry);
    }

    private MpMetricRegistry(String scope, MeterRegistry meterRegistry) {
        this.scope = scope;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Counter counter(String s) {
        return getOrCreateAndStoreMetric(Meter.Type.COUNTER,
                                         MpCounter::new,
                                         meterRegistry::counter,
                                         s);
    }

    @Override
    public Counter counter(String s, Tag... tags) {
        return getOrCreateAndStoreMetric(Meter.Type.COUNTER,
                                         MpCounter::new,
                                         meterRegistry::counter,
                                         s,
                                         tags);
    }

    @Override
    public Counter counter(MetricID metricID) {
        return getOrCreateAndStoreMetric(Meter.Type.COUNTER,
                                         MpCounter::new,
                                         meterRegistry::counter,
                                         metricID.getName(),
                                         metricID.getTagsAsArray());
    }

    @Override
    public Counter counter(Metadata metadata) {
        return getOrCreateAndStoreMetric(Meter.Type.COUNTER,
                                         MpCounter::new,
                                         meterRegistry::counter,
                                         validatedMetadata(metadata));
    }

    @Override
    public Counter counter(Metadata metadata, Tag... tags) {
        return getOrCreateAndStoreMetric(Meter.Type.COUNTER,
                                         MpCounter::new,
                                         meterRegistry::counter,
                                         validatedMetadata(metadata),
                                         tags);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(String s, T t, Function<T, R> function, Tag... tags) {
        return null;
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(MetricID metricID, T t, Function<T, R> function) {
        return null;
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(Metadata metadata, T t, Function<T, R> function, Tag... tags) {
        return null;
    }

    @Override
    public <T extends Number> Gauge<T> gauge(String s, Supplier<T> supplier, Tag... tags) {
        return null;
    }

    @Override
    public <T extends Number> Gauge<T> gauge(MetricID metricID, Supplier<T> supplier) {
        return null;
    }

    @Override
    public <T extends Number> Gauge<T> gauge(Metadata metadata, Supplier<T> supplier, Tag... tags) {
        return null;
    }

    @Override
    public Histogram histogram(String s) {
        return null;
    }

    @Override
    public Histogram histogram(String s, Tag... tags) {
        return null;
    }

    @Override
    public Histogram histogram(MetricID metricID) {
        return null;
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return null;
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        return null;
    }

    @Override
    public Timer timer(String s) {
        return null;
    }

    @Override
    public Timer timer(String s, Tag... tags) {
        return null;
    }

    @Override
    public Timer timer(MetricID metricID) {
        return null;
    }

    @Override
    public Timer timer(Metadata metadata) {
        return null;
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        return null;
    }

    @Override
    public Metric getMetric(MetricID metricID) {
        return metricsById.get(metricID);
    }

    @Override
    public <T extends Metric> T getMetric(MetricID metricID, Class<T> aClass) {
        return aClass.cast(metricsById.get(metricID));
    }

    @Override
    public Counter getCounter(MetricID metricID) {
        return (Counter) metricsById.get(metricID);
    }

    @Override
    public Gauge<?> getGauge(MetricID metricID) {
        return (Gauge<?>) metricsById.get(metricID);
    }

    @Override
    public Histogram getHistogram(MetricID metricID) {
        return (Histogram) metricsById.get(metricID);
    }

    @Override
    public Timer getTimer(MetricID metricID) {
        return (Timer) metricsById.get(metricID);
    }

    @Override
    public Metadata getMetadata(String s) {
        return metadata.get(s);
    }

    @Override
    public boolean remove(String s) {
        return access(() -> {
            boolean removeResult = false;
            // Capture the list of IDs first and then iterate over that because the remove method updates the list from the map.
            List<MetricID> doomedMetricIds = new ArrayList<>(metricIdsByName.get(s));
            for (MetricID metricId : doomedMetricIds) {
                removeResult |= remove(metricId);
            }
            return removeResult;


        });
    }

    @Override
    public boolean remove(MetricID metricID) {
        return access(() -> {
            MpMetric<?> doomedMpMetric = metricsById.remove(metricID);
            boolean removeResult = doomedMpMetric != null;
            List<MetricID> idsByName = metricIdsByName.get(metricID.getName());
            if (idsByName != null) {
                idsByName.remove(metricID);
            }
            meterRegistry.remove(doomedMpMetric.delegate());
            return removeResult;
        });
    }

    @Override
    public void removeMatching(MetricFilter metricFilter) {
        List<MetricID> doomedMetricIds = new ArrayList<>();
        access(() -> {
            metricsById.forEach((id, metric) -> {
                if (metricFilter.matches(id, metric)) {
                    doomedMetricIds.add(id);
                }
            });
            for (MetricID doomedMetricId : doomedMetricIds) {
                remove(doomedMetricId);
            }
        });
    }

    @Override
    public SortedSet<String> getNames() {
        return new TreeSet<>(metadata.keySet());
    }

    @Override
    public SortedSet<MetricID> getMetricIDs() {
        return new TreeSet<>(metricsById.keySet());
    }

    @Override
    public SortedMap<MetricID, Gauge> getGauges() {
        return null;
    }

    @Override
    public SortedMap<MetricID, Gauge> getGauges(MetricFilter metricFilter) {
        return null;
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters() {
        return getCounters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters(MetricFilter metricFilter) {
        return getMetrics(Counter.class, metricFilter);
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms() {
        return null;
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms(MetricFilter metricFilter) {
        return null;
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers() {
        return null;
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers(MetricFilter metricFilter) {
        return null;
    }

    @Override
    public SortedMap<MetricID, Metric> getMetrics(MetricFilter metricFilter) {
        return null;
    }

    @Override
    public <T extends Metric> SortedMap<MetricID, T> getMetrics(Class<T> aClass, MetricFilter metricFilter) {
        return metricsById.entrySet()
                .stream()
                .filter(e -> aClass.isInstance(e.getValue()))
                .filter(e -> metricFilter.matches(e.getKey(), e.getValue()))
                .collect(TreeMap::new,
                         (tm, entry) -> tm.put(entry.getKey(), aClass.cast(entry.getValue())),
                         TreeMap::putAll);
    }

    @Override
    public Map<MetricID, Metric> getMetrics() {
        return Collections.unmodifiableMap(metricsById);
    }

    @Override
    public Map<String, Metadata> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    @Override
    public String getScope() {
        return scope;
    }

    <M extends MpMetric<?>, T extends Meter> M getOrCreateAndStoreMetric(Meter.Type type,
                                                                         Function<T, M> metricFactory,
                                                                         BiFunction<String,
                                                                                 Iterable<io.micrometer.core.instrument.Tag>,
                                                                                 T> meterFactory,
                                                                         String name,
                                                                         Tag... tags) {
        return getOrCreateAndStoreMetric(type,
                                         metricFactory,
                                         meterFactory,
                                         validatedMetadata(name),
                                         tags);
    }

    <M extends MpMetric<?>, T extends Meter> M getOrCreateAndStoreMetric(Meter.Type type,
                                                                         Function<T, M> metricFactory,
                                                                         BiFunction<String,
                                                                                 Iterable<io.micrometer.core.instrument.Tag>,
                                                                                 T> meterFactory,
                                                                         Metadata validMetadata,
                                                                         Tag... tags) {

        /*
         * From the metadata create a candidate MpMetricID, validate it (to make sure the tag names are consistent with any
         * previously-registered metrics with the same name and that the user did not specify any reserved tags), and then
         * augment the inner meter ID with the scope tag and, if an app name is specified via config, the app name tag.
         */
        MpMetricId mpMetricId = validAugmentedMpMetricId(validMetadata, type, tags);
        return access(() -> {
            MpMetric<?> result = metricsById.get(mpMetricId);
            if (result != null) {
                return (M) result;
            }

            T delegate = meterFactory.apply(mpMetricId.name(), MpTags.fromMp(mpMetricId.getTags()));

            M newMetric = metricFactory.apply(delegate);
            storeMetadataIfAbsent(validMetadata);
            metricsById.put(mpMetricId, newMetric);
            metricsById.put(mpMetricId, newMetric);
            metricIdsByName.computeIfAbsent(validMetadata.getName(), n -> new ArrayList<>()).add(mpMetricId);
            metricsByName.computeIfAbsent(validMetadata.getName(), n -> new ArrayList<>()).add(newMetric);
            return newMetric;
        });
    }

    private void storeMetadataIfAbsent(Metadata validatedMetadata) {
        metadata.putIfAbsent(validatedMetadata.getName(), validatedMetadata);
    }

    /**
     * Returns a validated {@link io.helidon.metrics.microprofile.MpMetricId} derived from the provided metadata, tags,
     * and meter Type, further augmented with MicroProfile-supported additional tags (app name, scope).
     *
     * @param proposedMetadata {@link Metadata} to use in populating the {@code MpMetricId}
     * @param meterType the non-MP metric meterType
     * @param tags tags to use in preparing the metric ID
     * @return validated {@code MpMetricId}
     */
    private MpMetricId validAugmentedMpMetricId(Metadata proposedMetadata, Meter.Type meterType, Tag... tags) {
        MpMetricId metricId = new MpMetricId(proposedMetadata.getName(),
                                             tags,
                                             automaticTags(),
                                             proposedMetadata.getUnit(),
                                             proposedMetadata.getDescription(),
                                             meterType);
        checkTagNameSetConsistencyWithStoredIds(metricId);
        MpTags.checkForReservedTags(metricId.getTags().keySet());
        return metricId;
    }

    private Tag[] automaticTags() {
        List<Tag> result = new ArrayList<>();
        result.add(new Tag(MpMetricRegistry.MP_SCOPE_TAG_NAME, scope));
        String mpAppValue = MpTags.mpAppValue();
        if (mpAppValue != null && !mpAppValue.isBlank()) {
            result.add(new Tag(MpMetricRegistry.MP_APPLICATION_TAG_NAME, mpAppValue));
        }
        return result.toArray(new Tag[0]);
    }



    /**
     * Returns a {@link org.eclipse.microprofile.metrics.Metadata} derived from the specified name, validated for consistency
     * against any previously-registered metadata under the same name.
     *
     * @param name name associated with the metadata
     * @return valid {@code Metadata} derived from the name
     */
    private Metadata validatedMetadata(String name) {
        return validatedMetadata(Metadata.builder()
                .withName(name)
                .build());
    }

    /**
     * Returns the provided {@link org.eclipse.microprofile.metrics.Metadata} once validated for consistency against any
     * previously-registered metadata with the same name.
     *
     * @param proposedMetadata candidate {@code Metadata} to validate
     * @return validated {@code Metadata}
     */
    private Metadata validatedMetadata(Metadata proposedMetadata) {
        Metadata storedMetadata = metadata.get(proposedMetadata.getName());
        if (storedMetadata == null) {
            return proposedMetadata;
        }
        if (!isConsistentMetadata(storedMetadata, proposedMetadata)) {
            throw new IllegalArgumentException(String.format(
                    "Supplied metadata %s is inconsistent with previously-registered metadata %s",
                    proposedMetadata,
                    storedMetadata));
        }
        return storedMetadata;
    }


    /**
     * Returns whether the two metadata instances are consistent with each other.
     *
     * @param a one {@code Metadata} instance
     * @param b the other {@code Metadata} instance
     * @return {@code true} if the two instances contain consistent metadata; {@code false} otherwise
     */
    boolean isConsistentMetadata(Metadata a, Metadata b) {
        return a.equals(b);
    }

    MpMetricId consistentMpMetricId(Metadata metadata, Meter.Type type, Tag... tags) {
        MpTags.checkForReservedTags(tags);
        MpMetricId id = new MpMetricId(metadata.getName(),
                       tags,
                       automaticTags(),
                       metadata.getUnit(),
                       metadata.getDescription(),
                       type);
        MpTags.checkTagNameSetConsistency(id, metricIdsByName.get(metadata.getName()));
        return id;
    }

    /**
     * Checks that the tag names in the provided ID are consistent with the tag names in any previously-registered ID
     * with the same name; throws {@code IllegalArgumentException} if inconsistent.
     *
     * @param mpMetricId metric ID with tags to check
     */
    MpMetricId checkTagNameSetConsistencyWithStoredIds(MpMetricId mpMetricId) {
        MpTags.checkTagNameSetConsistency(mpMetricId, metricIdsByName.get(mpMetricId.getName()));
        return mpMetricId;
    }

    private <T> T access(Callable<T> work) {
        accessLock.lock();
        try {
            return work.call();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            accessLock.unlock();
        }
    }

    private void access(Runnable work) {
        accessLock.lock();
        try {
            work.run();
        } finally {
            accessLock.unlock();
        }
    }
}
