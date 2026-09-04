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

package io.helidon.tests.benchmark;

import java.util.Collection;

import org.openjdk.jmh.results.Aggregator;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ResultRole;
import org.openjdk.jmh.util.ListStatistics;
import org.openjdk.jmh.util.Statistics;

final class OptionalScalarResult extends Result<OptionalScalarResult> {
    private static final long serialVersionUID = 1L;

    OptionalScalarResult(String label,
                         double score,
                         String unit,
                         AggregationPolicy policy) {
        this(label, of(score), unit, policy);
    }

    private OptionalScalarResult(String label,
                                 Statistics statistics,
                                 String unit,
                                 AggregationPolicy policy) {
        super(ResultRole.SECONDARY, label, statistics, unit, policy);
    }

    @Override
    protected Aggregator<OptionalScalarResult> getThreadAggregator() {
        return new OptionalScalarResultAggregator();
    }

    @Override
    protected Aggregator<OptionalScalarResult> getIterationAggregator() {
        return new OptionalScalarResultAggregator();
    }

    @Override
    protected OptionalScalarResult getZeroResult() {
        return null;
    }

    private static final class OptionalScalarResultAggregator implements Aggregator<OptionalScalarResult> {
        @Override
        public OptionalScalarResult aggregate(Collection<OptionalScalarResult> results) {
            OptionalScalarResult first = results.iterator().next();
            ListStatistics statistics = new ListStatistics();
            for (OptionalScalarResult result : results) {
                statistics.addValue(result.getScore());
            }
            return new OptionalScalarResult(first.label,
                                            statistics,
                                            first.unit,
                                            first.policy);
        }
    }
}
