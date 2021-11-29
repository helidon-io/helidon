/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import io.helidon.metrics.MetricImpl.Units;

import org.eclipse.microprofile.metrics.MetricID;

/**
 * Abstraction for a Prometheus metric name, offering various formats of output as required by the Prometheus format.
 */
class PrometheusName {

    private final String prometheusTags;

    private final MetricImpl metricImpl;
    private final MetricID metricID;
    private final String prometheusNameWithUnits;
    private final String prometheusName;
    private final String prometheusUnit;
    private final Units units;

    static PrometheusName create(MetricImpl metricImpl, MetricID metricID) {
        return new PrometheusName(metricImpl, metricID, metricImpl.getUnits());
    }

    static PrometheusName create(MetricImpl metricImpl, MetricID metricID, Units units) {
        return new PrometheusName(metricImpl, metricID, units);
    }

    private PrometheusName(MetricImpl metricImpl, MetricID metricID, Units units) {
        this.metricImpl = metricImpl;
        this.metricID = metricID;
        this.units = units;
        prometheusName = MetricImpl.prometheusClean(metricID.getName(), metricImpl.registryType() + "_");
        this.prometheusTags = metricImpl.prometheusTags(metricID.getTags());
        prometheusNameWithUnits = nameUnits(units);

        prometheusUnit = units
                .getPrometheusUnit()
                .orElse("");
    }

    Units units() {
        return units;
    }

    /**
     * Returns the Prometheus metric name (registry type + metric name) + units.
     *
     * @return name with units
     */
    String nameUnits() {
        return prometheusNameWithUnits;
    }

    String nameUnits(Units units) {
        return metricImpl.prometheusNameWithUnits(metricID.getName(), units.getPrometheusUnit());
    }

    /**
     * Returns the Prometheus metric name (registry type + metric name) + statistic type + units.
     *
     * @param statName the statistics name (e.g., "mean") to include in the name expression
     * @return name with stat name with units
     */
    String nameStatUnits(String statName) {
        return nameStat(statName) + (prometheusUnit.isBlank() ? "" :  "_" + prometheusUnit);
    }

    String nameStat(String statName) {
        return prometheusName + "_" + statName;
    }

    String nameStatTags(String statName) {
        return nameStat(statName) + prometheusTags;
    }

    /**
     * Returns the Prometheus metric name (registry type + metric name) + units + suffix (e.g., "count") + tags.
     *
     * @param nameSuffix suffix to add to the name (after the units)
     * @return name with units with suffix with tags
     */
    String nameUnitsSuffixTags(String nameSuffix) {
        return prometheusNameWithUnits + "_" + nameSuffix + prometheusTags;
    }

    /**
     * Returns the Prometheus format for the tags.
     *
     * @return tags in Prometheus format "{tag=value,tag=value,...}"
     */
    String prometheusTags() {
        return prometheusTags;
    }
}
