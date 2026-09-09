/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.providers.micrometer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.tracer.common.SpanContext;

/**
 * Prometheus meter registry which detects collisions involving generated Prometheus sample names.
 *
 * <p>The Prometheus client validates collector names at registration, but the Micrometer collector used by the
 * Micrometer Prometheus registry does not describe the secondary names it generates. This registry accounts for those
 * names for the meter types Helidon creates.</p>
 */
class CollisionDetectingPrometheusMeterRegistry extends PrometheusMeterRegistry {

    private static final String CREATED_SUFFIX = "_created";
    private static final String TOTAL_SUFFIX = "_total";

    private final Lock reservationLock = new ReentrantLock();
    private final Map<String, Claim> claims = new HashMap<>();
    private final Map<Meter.Id, Reservation> reservations = new HashMap<>();

    CollisionDetectingPrometheusMeterRegistry(PrometheusConfig config) {
        super(config);
        config().onMeterRemoved(meter -> release(meter.getId()));
    }

    CollisionDetectingPrometheusMeterRegistry(PrometheusConfig config,
                                               PrometheusRegistry registry,
                                               Clock clock,
                                               SpanContext spanContext) {
        super(config, registry, clock, spanContext);
        config().onMeterRemoved(meter -> release(meter.getId()));
    }

    @Override
    public Counter newCounter(Meter.Id id) {
        return register(id, counterNames(id), () -> super.newCounter(id));
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id,
                                                      DistributionStatisticConfig distributionStatisticConfig,
                                                      double scale) {
        return register(id,
                        distributionNames(id, distributionStatisticConfig),
                        () -> super.newDistributionSummary(id, distributionStatisticConfig, scale));
    }

    @Override
    protected Timer newTimer(Meter.Id id,
                             DistributionStatisticConfig distributionStatisticConfig,
                             PauseDetector pauseDetector) {
        return register(id,
                        distributionNames(id, distributionStatisticConfig),
                        () -> super.newTimer(id, distributionStatisticConfig, pauseDetector));
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> valueFunction) {
        return register(id, gaugeNames(id), () -> super.newGauge(id, obj, valueFunction));
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return register(id, counterNames(id), () -> super.newFunctionCounter(id, obj, countFunction));
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id,
                                                 T obj,
                                                 ToLongFunction<T> countFunction,
                                                 ToDoubleFunction<T> totalTimeFunction,
                                                 TimeUnit totalTimeFunctionUnit) {
        return register(id,
                        functionTimerNames(id),
                        () -> super.newFunctionTimer(id,
                                                     obj,
                                                     countFunction,
                                                     totalTimeFunction,
                                                     totalTimeFunctionUnit));
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id,
                                             DistributionStatisticConfig distributionStatisticConfig) {
        return register(id,
                        distributionNames(id, distributionStatisticConfig),
                        () -> super.newLongTaskTimer(id, distributionStatisticConfig));
    }

    private static String owner(Meter.Id id) {
        return "'" + LegacyPrometheusMeterFilter.originalGaugeName(id.getName()) + "' (type=" + id.getType()
                + ", baseUnit=" + Objects.toString(id.getBaseUnit(), "<none>") + ")";
    }

    private Set<String> counterNames(Meter.Id id) {
        String conventionName = expositionName(id);
        String counterName = conventionName.endsWith(TOTAL_SUFFIX) ? conventionName : conventionName + TOTAL_SUFFIX;
        String baseName = counterName.substring(0, counterName.length() - TOTAL_SUFFIX.length());
        return Set.of(baseName, counterName, baseName + CREATED_SUFFIX);
    }

    private Set<String> gaugeNames(Meter.Id id) {
        String conventionName = expositionName(id);
        return id.getName().endsWith(".info")
                ? Set.of(conventionName, conventionName + "_info")
                : Set.of(conventionName);
    }

    private Set<String> distributionNames(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        String conventionName = expositionName(id);
        Set<String> result = new LinkedHashSet<>();
        result.add(conventionName);
        result.add(conventionName + "_count");
        result.add(conventionName + "_sum");
        result.add(conventionName + CREATED_SUFFIX);
        if (distributionStatisticConfig.isPublishingHistogram()) {
            result.add(conventionName + "_bucket");
        }
        result.add(conventionName + "_max");
        return Set.copyOf(result);
    }

    private Set<String> functionTimerNames(Meter.Id id) {
        String conventionName = expositionName(id);
        return Set.of(conventionName,
                      conventionName + "_count",
                      conventionName + "_sum",
                      conventionName + CREATED_SUFFIX);
    }

    private String expositionName(Meter.Id id) {
        return PrometheusNameSupport.expositionName(id, config().namingConvention());
    }

    private <M extends Meter> M register(Meter.Id id, Set<String> names, Supplier<M> registration) {
        Reservation reservation = reserve(id, names);
        try {
            M meter = registration.get();
            reservationLock.lock();
            try {
                reservations.put(id, reservation);
            } finally {
                reservationLock.unlock();
            }
            return meter;
        } catch (RuntimeException | Error e) {
            release(reservation);
            throw e;
        }
    }

    private Reservation reserve(Meter.Id id, Set<String> names) {
        String owner = owner(id);
        reservationLock.lock();
        try {
            for (String name : names) {
                Claim claim = claims.get(name);
                if (claim != null && !claim.owner.equals(owner)) {
                    throw new IllegalArgumentException("Prometheus metric name collision: '" + name
                                                               + "' is produced by both " + claim.owner + " and " + owner);
                }
            }
            names.forEach(name -> claims.compute(name, (ignored, claim) -> claim == null
                    ? new Claim(owner)
                    : claim.increment()));
            return new Reservation(names);
        } finally {
            reservationLock.unlock();
        }
    }

    private void release(Meter.Id id) {
        reservationLock.lock();
        try {
            Reservation reservation = reservations.remove(id);
            if (reservation != null) {
                releaseLocked(reservation);
            }
        } finally {
            reservationLock.unlock();
        }
    }

    private void release(Reservation reservation) {
        reservationLock.lock();
        try {
            releaseLocked(reservation);
        } finally {
            reservationLock.unlock();
        }
    }

    private void releaseLocked(Reservation reservation) {
        reservation.names.forEach(name -> claims.computeIfPresent(name, (ignored, claim) -> claim.decrement()));
    }

    private static final class Claim {
        private final String owner;
        private int count = 1;

        private Claim(String owner) {
            this.owner = owner;
        }

        private Claim increment() {
            count++;
            return this;
        }

        private Claim decrement() {
            return --count == 0 ? null : this;
        }
    }

    private static final class Reservation {
        private final Set<String> names;

        private Reservation(Set<String> names) {
            this.names = names;
        }
    }
}
