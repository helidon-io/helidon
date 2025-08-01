/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.common.Errors;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.SystemTagsManager;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Implementation of {@link org.eclipse.microprofile.metrics.MetricRegistry} which layers on top of the Helidon neutral
 * metrics API (and, therefore, whatever runtime implementation of that API is available).
 */
class Registry implements MetricRegistry {

    private static final System.Logger LOGGER = System.getLogger(Registry.class.getName());
    private static final Tag[] NO_TAGS = new Tag[0];
    private final MeterRegistry meterRegistry;
    private final String scope;

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<MetricID, HelidonMetric<?>> metricsById = new HashMap<>();
    private final Map<Meter, HelidonMetric<?>> metricsByDelegate = new HashMap<>();
    private final Map<String, InfoPerName> infoByName = new HashMap<>();

    private Registry(String scope, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.scope = scope;
    }

    static Registry create(String scope, MeterRegistry meterRegistry) {
        return new Registry(scope, meterRegistry);
    }

    static Metadata metadata(Meter meter) {

        MetadataBuilder builder = Metadata.builder().withName(meter.id().name());
        meter.baseUnit().ifPresent(builder::withUnit);
        meter.description().ifPresent(builder::withDescription);

        return builder.build();
    }

    protected static String sanitizeUnit(String unit) {
        return unit != null && !unit.equals(MetricUnits.NONE)
                ? unit
                : null;
    }

    /**
     * Returns an iterable of Helidon {@link io.helidon.metrics.api.Tag} including global tags, any app tag, and a scope
     * tag (if metrics is so configured to add a scope tag).
     *
     * @param scope scope of the meter
     * @param tags  explicitly-defined tags from the application code
     * @return iterable ot Helidon tags
     */
    protected static Iterable<io.helidon.metrics.api.Tag> validatedAllTags(String scope, Tag[] tags) {
        if (tags != null && tags.length > 0) {
            List<String> tagNames = Arrays.stream(tags).map(Tag::getTagName).toList();
            Collection<String> reservedTagNamesUsed = SystemTagsManager.instance().reservedTagNamesUsed(tagNames);
            if (!reservedTagNamesUsed.isEmpty()) {
                throw new IllegalArgumentException("Illegal use of reserved tag name(s): " + reservedTagNamesUsed);
            }
        }
        return toHelidonTags(SystemTagsManager.instance().withScopeTag(iterableEntries(tags), scope));
    }

    @Override
    public Counter counter(String name) {
        return counter(name, NO_TAGS);
    }

    @Override
    public Counter counter(String name, Tag... tags) {
        return Objects.requireNonNullElseGet(getCounter(new MetricID(name, tags)),
                                             () -> createCounter(metadata(name), tags));
    }

    @Override
    public Counter counter(MetricID metricID) {
        return Objects.requireNonNullElseGet(getCounter(metricID),
                                             () -> createCounter(metadata(metricID), metricID.getTagsAsArray()));
    }

    @Override
    public Counter counter(Metadata metadata) {
        return counter(metadata, NO_TAGS);
    }

    @Override
    public Counter counter(Metadata metadata, Tag... tags) {
        return Objects.requireNonNullElseGet(getCounter(metricID(metadata, tags)),
                                             () -> createCounter(metadata, tags));
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(String name, T object, Function<T, R> func, Tag... tags) {
        return Objects.requireNonNullElseGet(getGauge(new MetricID(name, tags)),
                                             () -> createGauge(metadata(name), object, func, tags));
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(MetricID metricID, T object, Function<T, R> func) {
        return Objects.requireNonNullElseGet(getGauge(metricID),
                                             () -> createGauge(
                                                     metadata(metricID),
                                                     object,
                                                     func,
                                                     metricID.getTagsAsArray()));
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(Metadata metadata, T object, Function<T, R> func, Tag... tags) {
        return Objects.requireNonNullElseGet(getGauge(metricID(metadata, tags)),
                                             () -> createGauge(metadata, object, func, tags));
    }

    @Override
    public <T extends Number> Gauge<T> gauge(String name, Supplier<T> supplier, Tag... tags) {
        return Objects.requireNonNullElseGet(getGauge(new MetricID(name, tags)),
                                             () -> createGauge(metadata(name), supplier, tags));
    }

    @Override
    public <T extends Number> Gauge<T> gauge(MetricID metricID, Supplier<T> supplier) {
        return Objects.requireNonNullElseGet(getGauge(metricID),
                                             () -> createGauge(metadata(metricID),
                                                               supplier,
                                                               metricID.getTagsAsArray()));
    }

    @Override
    public <T extends Number> Gauge<T> gauge(Metadata metadata, Supplier<T> supplier, Tag... tags) {
        return Objects.requireNonNullElseGet(getGauge(metricID(metadata, tags)),
                                             () -> createGauge(metadata, supplier, tags));
    }

    @Override
    public Histogram histogram(String name) {
        return histogram(metadata(name), NO_TAGS);
    }

    @Override
    public Histogram histogram(String name, Tag... tags) {
        return Objects.requireNonNullElseGet(getHistogram(new MetricID(name, tags)),
                                             () -> createHistogram(metadata(name), tags));
    }

    @Override
    public Histogram histogram(MetricID metricID) {
        return Objects.requireNonNullElseGet(getHistogram(metricID),
                                             () -> createHistogram(metadata(metricID),
                                                                   metricID.getTagsAsArray()));
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return histogram(metadata, NO_TAGS);
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        return Objects.requireNonNullElseGet(getHistogram(metricID(metadata, tags)),
                                             () -> createHistogram(metadata, tags));
    }

    @Override
    public Timer timer(String name) {
        return Objects.requireNonNullElseGet(getTimer(new MetricID(name)),
                                             () -> createTimer(metadata(name), NO_TAGS));
    }

    @Override
    public Timer timer(String name, Tag... tags) {
        return Objects.requireNonNullElseGet(getTimer(new MetricID(name, tags)),
                                             () -> createTimer(metadata(name), tags));
    }

    @Override
    public Timer timer(MetricID metricID) {
        return Objects.requireNonNullElseGet(getTimer(metricID),
                                             () -> createTimer(metadata(metricID),
                                                               metricID.getTagsAsArray()));
    }

    @Override
    public Timer timer(Metadata metadata) {
        return Objects.requireNonNullElseGet(getTimer(metricID(metadata)),
                                             () -> createTimer(metadata, NO_TAGS));
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        return Objects.requireNonNullElseGet(getTimer(metricID(metadata, tags)),
                                             () -> createTimer(metadata, tags));
    }

    @Override
    public Metric getMetric(MetricID metricID) {
        return metricsById.get(metricID);
    }

    @Override
    public <T extends Metric> T getMetric(MetricID metricID, Class<T> asType) {
        return asType.cast(getMetric(metricID));
    }

    @Override
    public Counter getCounter(MetricID metricID) {
        return getMetric(metricID, Counter.class);
    }

    @Override
    public Gauge getGauge(MetricID metricID) {
        return getMetric(metricID, Gauge.class);
    }

    @Override
    public Histogram getHistogram(MetricID metricID) {
        return getMetric(metricID, Histogram.class);
    }

    @Override
    public Timer getTimer(MetricID metricID) {
        return getMetric(metricID, Timer.class);
    }

    @Override
    public Metadata getMetadata(String name) {
        InfoPerName info = infoByName.get(name);
        return info == null ? null : info.metadata;
    }

    @Override
    public boolean remove(String name) {
        lock.lock();
        try {
            return removeMatchingWithResult((id, metric) -> id.getName().equals(name));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(MetricID metricID) {
        lock.lock();
        try {
            return removeViaDelegate(metricID);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeMatching(MetricFilter filter) {
        removeMatchingWithResult(filter);
    }

    @Override
    public SortedSet<String> getNames() {
        return new TreeSet<>(infoByName.keySet());
    }

    @Override
    public SortedSet<MetricID> getMetricIDs() {
        return new TreeSet<>(metricsById.keySet());
    }

    @Override
    public SortedMap<MetricID, Gauge> getGauges() {
        return getMetrics(Gauge.class, MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Gauge> getGauges(MetricFilter filter) {
        return getMetrics(Gauge.class, filter);
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters() {
        return getMetrics(Counter.class, MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters(MetricFilter filter) {
        return getMetrics(Counter.class, filter);
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms() {
        return getMetrics(Histogram.class, MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms(MetricFilter filter) {
        return getMetrics(Histogram.class, filter);
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers() {
        return getMetrics(Timer.class, MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers(MetricFilter filter) {
        return getMetrics(Timer.class, filter);
    }

    @Override
    public SortedMap<MetricID, Metric> getMetrics(MetricFilter filter) {
        lock.lock();
        try {
            return metricsById.entrySet().stream().filter(entry -> filter.matches(entry.getKey(), entry.getValue()))
                    .collect(TreeMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), TreeMap::putAll);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T extends Metric> SortedMap<MetricID, T> getMetrics(Class<T> ofType, MetricFilter filter) {
        lock.lock();
        try {
            return metricsById.entrySet().stream().filter(entry -> ofType.isInstance(entry.getValue()))
                    .filter(entry -> filter.matches(entry.getKey(), entry.getValue()))
                    .collect(TreeMap::new, (map, entry) -> map.put(entry.getKey(), (T) entry.getValue()), TreeMap::putAll);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<MetricID, Metric> getMetrics() {
        return access(() -> Map.copyOf(metricsById));
    }

    @Override
    public Map<String, Metadata> getMetadata() {
        return access(() -> {
            var result = new HashMap<String, Metadata>();
            infoByName.forEach((key, value) -> result.put(key, value.metadata()));
            return result;
        });
    }

    @Override
    public String getScope() {
        return scope;
    }

    HelidonMetric<?> onMeterAdded(Meter meter) {

        String name = meter.id().name();

        // Do some validation before we accept the registration.

        Errors.Collector collector = Errors.collector();

        MetricID newMetricID = metricIDWithoutSystemTags(meter.id());

        lock.lock();

        try {
            InfoPerName existingInfo = infoByName.get(name);
            if (existingInfo != null) {
                existingInfo.validate(collector, newMetricID, meter);
            }

            HelidonMetric<?> existingMetric = metricsById.get(newMetricID);
            if (existingMetric != null) {

                // This is a bit odd. We've been notified that a new meter was created by the neutral metrics implementation,
                // but we seem to already have the corresponding metric in place. (This *can* happen if the metric or all metrics
                // are disabled.)
                //
                // Validate the new meter against the existing metric anyway but report a warning about the duplicate
                // registration attempt.

                collector.warn(String.format("unexpected attempted re-registration of metric %s by meter %s",
                                             newMetricID,
                                             meter));
                validateMetric(collector, newMetricID, existingMetric, meter);
            }

            if (collector.hasFatal()) {
                throw new IllegalArgumentException("Attempt to register a meter incompatible with previously-registered "
                                                           + "metrics: " + collector.collect());
            }

            Metadata newMetadata = existingInfo == null ? metadata(meter) : null;

            HelidonMetric<?> newMetric = metric(collector, existingInfo == null ? newMetadata : existingInfo.metadata, meter);

            // Now update the data structures if the meter is enabled.
            if (meterRegistry.isMeterEnabled(meter.id().name(), meter.id().tagsMap(), meter.scope())) {
                InfoPerName info = infoByName.computeIfAbsent(newMetricID.getName(),
                                                              n -> InfoPerName.create(newMetadata, newMetricID));

                // Inside info, metric IDs are stored in a set, so adding the first ID again does no harm.
                info.add(newMetricID);
                metricsById.put(newMetricID, newMetric);
                metricsByDelegate.put(meter, newMetric);
            }

            collector.collect().log(LOGGER);

            return newMetric;
        } finally {
            lock.unlock();
        }
    }

    void onMeterRemoved(Meter meter) {

        Errors.Collector collector = Errors.collector();

        HelidonMetric<?> metric = metricsByDelegate.get(meter);
        if (metric == null) {
            collector.warn(meter, "Unable to find corresponding metric to remove upon removal of meter");
        } else {
            lock.lock();
            try {
                metric.markAsDeleted();
                String metricName = meter.id().name();
                MetricID metricID = metricIDWithoutSystemTags(collector, meter.id());

                InfoPerName info = infoByName.get(metricName);
                if (info == null) {
                    collector.warn(String.format("Unable to locate info for name %s", metricName));
                } else {
                    info.remove(metricID);
                    if (info.metricIDs.isEmpty()) {
                        infoByName.remove(metricName);
                    }
                }

                if (metricsById.remove(metricID) == null) {
                    collector.warn("could not find metric by ID " + metricID);
                }

                if (metricsByDelegate.remove(meter) == null) {
                    collector.warn("could not find metric by meter");
                }
            } finally {
                lock.unlock();
            }
        }
    }

    void clear() {
        lock.lock();
        try {
            infoByName.clear();
            metricsByDelegate.clear();
            metricsById.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Converts an iterable of map entries (representing tag names and values) into an iterable of Helidon tags.
     *
     * @param entriesIterable iterable of map entries
     * @return iterable of {@link io.helidon.metrics.api.Tag}
     */
    private static Iterable<io.helidon.metrics.api.Tag> toHelidonTags(Iterable<Map.Entry<String, String>> entriesIterable) {
        List<io.helidon.metrics.api.Tag> result = new ArrayList<>();
        entriesIterable.forEach(entry -> result.add(io.helidon.metrics.api.Tag.create(entry.getKey(), entry.getValue())));
        return result;
    }

    private static Iterable<Map.Entry<String, String>> iterableEntries(Tag... tags) {
        if (tags == null) {
            return Set.of();
        }
        List<Map.Entry<String, String>> result = new ArrayList<>();
        for (Tag tag : tags) {
            result.add(new AbstractMap.SimpleEntry<>(tag.getTagName(), tag.getTagValue()));
        }
        return result;
    }

    private static MetricID metricID(Metadata metadata, Tag... tags) {
        return new MetricID(metadata.getName(), tags);
    }

    private static Metadata metadata(String name) {
        return Metadata.builder().withName(name).build();
    }

    private static Metadata metadata(MetricID metricId) {
        return metadata(metricId.getName());
    }

    private static void validateMetric(Errors.Collector collector,
                                       MetricID metricID,
                                       HelidonMetric<?> existingMetric,
                                       Meter meter) {

        /*
         Watch out for a corner case. If metrics are disabled then we get notification of a new meter when we might not expect
         it. Functional counters in the Helidon metrics API are converted to gauges in the Helidon implementation of the MP
         metrics API, so a simple type comparison will fail in that case.
         */
        if (meter instanceof FunctionalCounter
                && io.helidon.metrics.api.Gauge.class.isAssignableFrom(existingMetric.delegateType())) {
            return;
        }
        if (!existingMetric.delegateType().isInstance(meter)) {
            collector.fatal(String.format("existing metric %s is compatible with type %s but new meter is %s",
                                          metricID,
                                          existingMetric.delegateType(),
                                          meter.getClass().getName()));
        }
    }

    private static Map<String, String> tagsWithoutSystemOrScopeTags(Iterable<io.helidon.metrics.api.Tag> tags) {
        Map<String, String> result = new TreeMap<>();

        SystemTagsManager.instance().withoutSystemOrScopeTags(tags).forEach(t -> result.put(t.key(), t.value()));

        return result;
    }

    private boolean isMeterEnabled(Meter meter) {
        return meterRegistry.isMeterEnabled(meter.id().name(), meter.id().tagsMap(), meter.scope());
    }

    private static Tag[] tags(Map<String, String> tags) {
        var result = new ArrayList<Tag>();
        tags.forEach((key, value) -> result.add(new Tag(key, value)));
        return result.toArray(new Tag[0]);
    }

    private HelidonCounter createCounter(Metadata metadata, Tag... tags) {
        return createCounter(io.helidon.metrics.api.Counter.builder(metadata.getName())
                                     .scope(scope)
                                     .description(metadata.getDescription())
                                     .baseUnit(sanitizeUnit(metadata.getUnit()))
                                     .tags(validatedAllTags(scope, tags)));
    }

    private HelidonCounter createCounter(io.helidon.metrics.api.Counter.Builder counterBuilder) {
        return createMeter(counterBuilder, HelidonCounter::create);
    }

    @SuppressWarnings("unchecked")
    private <N extends Number, T> HelidonGauge<N> createGauge(Metadata metadata, T object, Function<T, N> func, Tag... tags) {
        return (HelidonGauge<N>) createGauge(io.helidon.metrics.api.Gauge.builder(metadata.getName(),
                                                                                  (Supplier<? extends N>) () -> func
                                                                                          .apply(object))
                                                     .scope(scope)
                                                     .description(metadata.getDescription())
                                                     .tags(validatedAllTags(scope, tags))
                                                     .baseUnit(sanitizeUnit(metadata.getUnit())));

    }

    private <N extends Number> HelidonGauge<N> createGauge(Metadata metadata, Supplier<N> supplier, Tag... tags) {
        return createGauge(io.helidon.metrics.api.Gauge.builder(metadata.getName(),
                                                                supplier)
                                   .scope(scope)
                                   .description(metadata.getDescription())
                                   .tags(validatedAllTags(scope, tags))
                                   .baseUnit(sanitizeUnit(metadata.getUnit())));

    }

    @SuppressWarnings("unchecked")
    private <N extends Number> HelidonGauge<N> createGauge(io.helidon.metrics.api.Gauge.Builder<N> gBuilder) {
        return createMeter(gBuilder, HelidonGauge::create);
    }

    private HelidonHistogram createHistogram(Metadata metadata, Tag... tags) {
        return createHistogram(DistributionSummary.builder(metadata.getName())
                                       .scope(scope)
                                       .description(metadata.getDescription())
                                       .baseUnit(sanitizeUnit(metadata.getUnit()))
                                       .tags(validatedAllTags(scope, tags)));

    }

    private HelidonHistogram createHistogram(io.helidon.metrics.api.DistributionSummary.Builder sBuilder) {
        return createMeter(DistributionCustomizations.apply(sBuilder), HelidonHistogram::create);
    }

    private HelidonTimer createTimer(Metadata metadata, Tag... tags) {
        return createTimer(io.helidon.metrics.api.Timer.builder(metadata.getName())
                                   .scope(scope)
                                   .description(metadata.getDescription())
                                   .baseUnit(sanitizeUnit(metadata.getUnit()))
                                   .tags(validatedAllTags(scope, tags)));
    }

    private HelidonTimer createTimer(io.helidon.metrics.api.Timer.Builder tBuilder) {
        return createMeter(DistributionCustomizations.apply(tBuilder), d -> HelidonTimer.create(meterRegistry, d));
    }

    private <HM extends HelidonMetric<M>,
            M extends Meter,
            B extends Meter.Builder<B, M>> HM createMeter(B builder,
                                                          Function<M, HM> factory) {
        M delegate = meterRegistry.getOrCreate(builder);
        // Disabled metrics are not in the data structures supporting our registry, so we cannot find those via metricsByDelegate.
        // Instead just create a new wrapper around the delegate.
        return isMeterEnabled(delegate)
                ? (HM) metricsByDelegate.get(delegate)
                : factory.apply(delegate);
    }

    private boolean removeMatchingWithResult(MetricFilter filter) {
        boolean result = false;
        lock.lock();
        try {
            for (Map.Entry<MetricID, Metric> entry : getMetrics(filter).entrySet()) {
                result |= remove(entry.getKey());
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    private boolean removeViaDelegate(MetricID metricId) {
        lock.lock();
        try {
            HelidonMetric<?> helidonMetric = metricsById.get(metricId);
            return helidonMetric != null
                    && helidonMetric.removeViaDelegate(meterRegistry);
        } finally {
            lock.unlock();
        }
    }

    private HelidonMetric<?> metric(Errors.Collector collector, Metadata metadata, Meter meter) {
        if (meter instanceof io.helidon.metrics.api.Counter counter) {
            return HelidonCounter.create(scope, metadata, counter);
        }
        if (meter instanceof DistributionSummary summary) {
            return HelidonHistogram.create(scope, metadata, summary);
        }
        if (meter instanceof FunctionalCounter fCounter) {
            return HelidonGauge.create(scope, metadata, fCounter);
        }
        if (meter instanceof io.helidon.metrics.api.Gauge gauge) {
            return HelidonGauge.create(scope, metadata, gauge);
        }
        if (meter instanceof io.helidon.metrics.api.Timer timer) {
            return HelidonTimer.create(meterRegistry, scope, metadata, timer);
        }
        collector.warn(String.format("Unrecognized type for new meter %s; unable to create MP metric for it", meter));
        return null;
    }

    private MetricID metricIDWithoutSystemTags(Errors.Collector collector, Meter.Id meterId) {
        Map<String, String> tagsWithoutScope = tagsWithoutSystemOrScopeTags(meterId.tags());

        Collection<String> reservedTagNamesUsed = SystemTagsManager.instance().reservedTagNamesUsed(tagsWithoutScope.keySet());

        if (!reservedTagNamesUsed.isEmpty()) {
            collector.fatal(reservedTagNamesUsed, "illegal use of reserved tag names");
        }
        return new MetricID(meterId.name(), tags(tagsWithoutScope));
    }

    private MetricID metricIDWithoutSystemTags(Meter.Id meterId) {
        Map<String, String> tagsWithoutScope = tagsWithoutSystemOrScopeTags(meterId.tags());
        return new MetricID(meterId.name(), tags(tagsWithoutScope));
    }

    static class InfoPerName {

        private final Metadata metadata;
        private final Set<MetricID> metricIDs = new HashSet<>();
        private final Set<String> tagNames = new HashSet<>();

        private InfoPerName(Metadata metadata, MetricID metricID) {
            this.metadata = metadata;

            /*
             Use the first metric ID presented to initialize the list of tag names for this name.
             */
            tagNames.addAll(metricID.getTags().keySet());
        }

        static InfoPerName create(Metadata metadata, MetricID metricID) {
            return new InfoPerName(metadata, metricID);
        }

        void add(MetricID metricID) {
            metricIDs.add(metricID);
        }

        boolean remove(MetricID metricID) {
            metricIDs.remove(metricID);
            return metricIDs.isEmpty();
        }

        Metadata metadata() {
            return metadata;
        }

        void validate(Errors.Collector collector, MetricID metricID, Meter meter) {
            validateMetadata(collector, metadata, meter);
            validateTagNames(collector, metricID);
        }

        private void validateMetadata(Errors.Collector collector, Metadata existingMetadata, Meter meter) {
            meter.description() // new description is present
                    .filter(Predicate.not(String::isBlank)) // and is non-blank
                    .filter(d -> !d.equals(existingMetadata.description()
                                                   .orElse(d))) // and differs from existing description if any
                    .ifPresent(d -> collector.fatal(String.format("metadata description: old='%s', proposed='%s'",
                                                                  existingMetadata.getDescription(),
                                                                  d)));

            meter.baseUnit() // new unit is present
                    .filter(Predicate.not(String::isBlank)) // and is non-blank
                    .filter(u -> !u.equals(existingMetadata.unit().orElse(u)))// and differs from existing unit if any
                    .ifPresent(u -> collector.fatal(String.format("metadataunit: old='%s', proposed='%s'",
                                                                  existingMetadata.getUnit(),
                                                                  u)));
        }

        private void validateTagNames(Errors.Collector collector, MetricID metricID) {
            Set<String> newTagNames = metricID.getTags().keySet();
            if (!tagNames.equals(newTagNames)) {
                collector.fatal(String.format("new tag names %s are inconsistent with existing tag names %s",
                                              metricID.getTags().keySet(),
                                              tagNames));
            }
        }
    }

    private <T> T access(Callable<T> callable) {
        lock.lock();
        try {
            return callable.call();
        } catch (Exception ex) {
            throw new RuntimeException("Exception during locked data access", ex);
        } finally {
            lock.unlock();
        }
    }
}
