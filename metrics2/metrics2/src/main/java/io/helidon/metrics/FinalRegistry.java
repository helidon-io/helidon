/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.stream.Stream;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * A registry that cannot be modified.
 */
final class FinalRegistry extends Registry {

    private static final Tag[] EMPTY_TAGS = new Tag[0];

    private final Registry delegate;

    private FinalRegistry(Registry delegate) {
        super(delegate.registryType());
        this.delegate = delegate;
    }

    public static Registry create(Registry registry) {
        return new FinalRegistry(registry);
    }

    @Override
    public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
        throw cannotRegister(name);
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric) throws IllegalArgumentException {
        throw cannotRegister(metadata.getName());
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric, Tag... tags) throws IllegalArgumentException {
        throw cannotRegister(metadata.getName());
    }

    @Override
    public Counter counter(String name) {
        return counter(name, EMPTY_TAGS);
    }

    @Override
    public Counter counter(Metadata metadata) {
        return counter(metadata.getName(), EMPTY_TAGS);
    }

    @Override
    public Counter counter(Metadata metadata, Tag... tags) {
        return counter(metadata.getName(), tags);
    }

    @Override
    public Counter counter(String name, Tag... tags) {
        return delegate.getOptionalMetric(name, HelidonCounter.class, tags)
                .orElseThrow(() -> cannotRegister(name));
    }

    @Override
    public Histogram histogram(String name) {
        return histogram(name, EMPTY_TAGS);
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return histogram(metadata.getName(), EMPTY_TAGS);
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        return histogram(metadata.getName(), tags);
    }

    @Override
    public Histogram histogram(String name, Tag... tags) {
        return delegate.getOptionalMetric(name, HelidonHistogram.class, tags)
                .orElseThrow(() -> cannotRegister(name));
    }

    @Override
    public Meter meter(String name) {
        return meter(name, EMPTY_TAGS);
    }

    @Override
    public Meter meter(Metadata metadata) {
        return meter(metadata.getName(), EMPTY_TAGS);
    }

    @Override
    public Meter meter(Metadata metadata, Tag... tags) {
        return meter(metadata.getName(), tags);
    }

    @Override
    public Meter meter(String name, Tag... tags) {
        return delegate.meter(name, tags);
    }

    @Override
    public Timer timer(String name) {
        return timer(name, EMPTY_TAGS);
    }

    @Override
    public Timer timer(Metadata metadata) {
        return timer(metadata.getName(), EMPTY_TAGS);
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        return timer(metadata.getName(), tags);
    }

    @Override
    public Timer timer(String name, Tag... tags) {
        return delegate.getOptionalMetric(name, HelidonTimer.class, tags)
                .orElseThrow(() -> cannotRegister(name));
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name) {
        return concurrentGauge(name, EMPTY_TAGS);
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata) {
        return concurrentGauge(metadata.getName(), EMPTY_TAGS);
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata, Tag... tags) {
        return concurrentGauge(metadata.getName(), tags);
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name, Tag... tags) {
        return delegate.getOptionalMetric(name, HelidonConcurrentGauge.class, tags)
                .orElseThrow(() -> cannotRegister(name));
    }

    @Override
    public boolean remove(String name) {
        throw cannotDelete(name);
    }

    @Override
    public boolean remove(MetricID metricID) {
        throw cannotDelete(metricID.getName());
    }

    @Override
    public void removeMatching(MetricFilter filter) {
        throw cannotDelete("Matching a filter: " + filter);
    }

    @Override
    public SortedSet<String> getNames() {
        return delegate.getNames();
    }

    @Override
    public SortedSet<MetricID> getMetricIDs() {
        return delegate.getMetricIDs();
    }

    @Override
    public SortedMap<MetricID, Gauge> getGauges() {
        return delegate.getGauges();
    }

    @Override
    public SortedMap<MetricID, Gauge> getGauges(MetricFilter filter) {
        return delegate.getGauges(filter);
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters() {
        return delegate.getCounters();
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters(MetricFilter filter) {
        return delegate.getCounters(filter);
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms() {
        return delegate.getHistograms();
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms(MetricFilter filter) {
        return delegate.getHistograms(filter);
    }

    @Override
    public SortedMap<MetricID, Meter> getMeters() {
        return delegate.getMeters();
    }

    @Override
    public SortedMap<MetricID, Meter> getMeters(MetricFilter filter) {
        return delegate.getMeters(filter);
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers() {
        return delegate.getTimers();
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers(MetricFilter filter) {
        return delegate.getTimers(filter);
    }

    @Override
    public SortedMap<MetricID, ConcurrentGauge> getConcurrentGauges() {
        return delegate.getConcurrentGauges();
    }

    @Override
    public SortedMap<MetricID, ConcurrentGauge> getConcurrentGauges(MetricFilter filter) {
        return delegate.getConcurrentGauges(filter);
    }

    @Override
    public Map<String, Metadata> getMetadata() {
        return delegate.getMetadata();
    }

    @Override
    public Map<MetricID, Metric> getMetrics() {
        return delegate.getMetrics();
    }

    @Override
    public Stream<Map.Entry<MetricID, HelidonMetric>> stream() {
        return delegate.stream();
    }

    @Override
    public String type() {
        return delegate.type();
    }

    @Override
    public boolean empty() {
        return delegate.empty();
    }

    @Override
    Type registryType() {
        return delegate.registryType();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    private UnsupportedOperationException cannotDelete(String name) {
        return new UnsupportedOperationException("Cannot delete a metric \""
                                                         + name
                                                         + "\" from final registry of type: "
                                                         + type());
    }

    private UnsupportedOperationException cannotRegister(String name) {
        return new UnsupportedOperationException("Cannot register a metric \""
                                                         + name
                                                         + "\" to a final registry of type: "
                                                         + type());
    }
}
