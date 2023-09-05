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
package io.helidon.integrations.oci.metrics;

import java.util.Map;

import io.helidon.metrics.api.Meter;

abstract class UnitConverter {

    private final String baseUnits;
    private final Map<String, Double> conversions;

    static StorageUnitConverter storageUnitConverter() {
        return new StorageUnitConverter();
    }

    static TimeUnitConverter timeUnitConverter() {
        return new TimeUnitConverter();
    }

    UnitConverter(String baseUnits, Map<String, Double> conversions) {
        this.baseUnits = baseUnits;
        this.conversions = conversions;
    }

    String baseUnits() {
        return baseUnits;
    }

    double convert(String metricUnits, double value) {
        return conversions.get(metricUnits) * value;
    }

    boolean handles(String metricUnits) {
        return metricUnits != null && conversions.containsKey(metricUnits);
    }

    private static class StorageUnitConverter extends UnitConverter {

        private static final Map<String, Double> CONVERSIONS = Map.of(
                Meter.BaseUnits.BITS, 1.0 / 8.0,
                Meter.BaseUnits.KILOBITS, 1.0D / 8.0 * 1000.0,
                Meter.BaseUnits.MEGABITS, 1.0 / 8.0 * 1000.0 * 1000.0,
                Meter.BaseUnits.GIGABITS, 1.0 / 8.0 * 1000.0 * 1000.0 * 1000.0,
                Meter.BaseUnits.BYTES, 1.0,
                Meter.BaseUnits.KILOBYTES, 1000.0,
                Meter.BaseUnits.MEGABYTES, 1000.0 * 1000.0,
                Meter.BaseUnits.GIGABYTES, 1000.0 * 1000.0 * 1000.0
        );

        StorageUnitConverter() {
            super(Meter.BaseUnits.BYTES, CONVERSIONS);
        }
    }

    private static class TimeUnitConverter extends UnitConverter {

        private static final Map<String, Double> CONVERSIONS = Map.of(
                Meter.BaseUnits.NANOSECONDS, 1.0 / 1000.0 / 1000.0 / 1000.0,
                Meter.BaseUnits.MICROSECONDS, 1.0 / 1000.0 / 1000.0,
                Meter.BaseUnits.MILLISECONDS, 1.0 / 1000.0,
                Meter.BaseUnits.SECONDS, 1.0,
                Meter.BaseUnits.MINUTES, 60.0,
                Meter.BaseUnits.HOURS, 60.0 * 60.0,
                Meter.BaseUnits.DAYS, 60.0 * 60.0 * 24.0
        );

        TimeUnitConverter() {
            super(Meter.BaseUnits.SECONDS, CONVERSIONS);
        }
    }
}
