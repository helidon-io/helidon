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

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.Timer;

/**
 * A registry that cannot be modified.
 */
final class FinalRegistry extends Registry {
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
    public <T extends Metric> T register(String name, T metric, Metadata metadata) throws IllegalArgumentException {
        throw cannotRegister(name);
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric) throws IllegalArgumentException {
        throw cannotRegister(metadata.getName());
    }

    @Override
    public Counter counter(String name) {
        return delegate.getMetric(name)
                .map(Counter.class::cast)
                .orElseThrow(() -> cannotRegister(name));
    }

    @Override
    public Counter counter(Metadata metadata) {
        return counter(metadata.getName());
    }

    @Override
    public Histogram histogram(String name) {
        return delegate.getMetric(name)
                .map(Histogram.class::cast)
                .orElseThrow(() -> cannotRegister(name));
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return histogram(metadata.getName());
    }

    @Override
    public Meter meter(String name) {
        return delegate.getMetric(name)
                .map(Meter.class::cast)
                .orElseThrow(() -> cannotRegister(name));
    }

    @Override
    public Meter meter(Metadata metadata) {
        return meter(metadata.getName());
    }

    @Override
    public Timer timer(String name) {
        return delegate.getMetric(name)
                .map(Timer.class::cast)
                .orElseThrow(() -> cannotRegister(name));
    }

    @Override
    public Timer timer(Metadata metadata) {
        return timer(metadata.getName());
    }

    @Override
    public boolean remove(String name) {
        throw cannotDelete(name);
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
    public SortedMap<String, Gauge> getGauges() {
        return delegate.getGauges();
    }

    @Override
    public SortedMap<String, Gauge> getGauges(MetricFilter filter) {
        return delegate.getGauges(filter);
    }

    @Override
    public SortedMap<String, Counter> getCounters() {
        return delegate.getCounters();
    }

    @Override
    public SortedMap<String, Counter> getCounters(MetricFilter filter) {
        return delegate.getCounters(filter);
    }

    @Override
    public SortedMap<String, Histogram> getHistograms() {
        return delegate.getHistograms();
    }

    @Override
    public SortedMap<String, Histogram> getHistograms(MetricFilter filter) {
        return delegate.getHistograms(filter);
    }

    @Override
    public SortedMap<String, Meter> getMeters() {
        return delegate.getMeters();
    }

    @Override
    public SortedMap<String, Meter> getMeters(MetricFilter filter) {
        return delegate.getMeters(filter);
    }

    @Override
    public SortedMap<String, Timer> getTimers() {
        return delegate.getTimers();
    }

    @Override
    public SortedMap<String, Timer> getTimers(MetricFilter filter) {
        return delegate.getTimers(filter);
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return delegate.getMetrics();
    }

    @Override
    public Map<String, Metadata> getMetadata() {
        return delegate.getMetadata();
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
