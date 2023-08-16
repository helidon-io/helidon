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

package io.helidon.webserver.benchmark.jmh;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.openjdk.jmh.results.RunResult;

public class Histogram {
    private static final String REGRESSION_BAR = "▒";
    private static final String PROGRESSION_BAR = "█";

    final List<Benchmark> benchmarks = new ArrayList<>();
    private double minVal;
    private double maxVal;
    private int maxLengthLabel;

    public static Histogram create(Collection<RunResult> results, Map<String, BaseLine> baseLineMap) {
        Histogram histogram = new Histogram();
        for (var result : results) {
            String benchmarkName = result.getParams().getBenchmark();
            BaseLine baseLine = baseLineMap.computeIfAbsent(benchmarkName, s -> BaseLine.create(result));

            double baseLineScore = baseLine.getPrimaryMetric().getScore();
            double resultScore = result.getPrimaryResult().getScore();
            histogram.add(baseLine.simpleBenchmarkName(), baseLineScore, resultScore);
        }
        return histogram;
    }

    public Histogram add(String label, double baseLineScore, double currentScore) {
        this.benchmarks.add(new Benchmark(this, label, baseLineScore, currentScore));
        recalculateMax(label, baseLineScore, currentScore);
        return this;
    }

    public String render(Benchmark b) {
        StringJoiner joiner = new StringJoiner("\n");
        int baseLinePercentage = Math.toIntExact(Math.round(b.baseLineScore * 100 / maxVal));
        int currPercentage = Math.toIntExact(Math.round(b.currentScore * 100 / maxVal));
        String bar = currPercentage >= baseLinePercentage ? PROGRESSION_BAR : REGRESSION_BAR;
        joiner.add(String.format("==================== %s", b));
        joiner.add(String.format("%" + maxLengthLabel + "s %s %.3f ops/s",
                                 "Baseline",
                                 bar.repeat(baseLinePercentage),
                                 b.baseLineScore));
        joiner.add(String.format("%" + maxLengthLabel + "s %s %.3f ops/s",
                                 "Current",
                                 bar.repeat(currPercentage),
                                 b.currentScore));
        return joiner.toString();
    }

    private static String simpleBenchmarkName(String benchmarkFqdn) {
        String[] fqdn = benchmarkFqdn.split("\\.");
        return String.format("%s.%s", fqdn[fqdn.length - 2], fqdn[fqdn.length - 1]);
    }

    private void recalculateMax(String label, double... scores) {
        for (double score : scores) {
            this.maxVal = Math.max(this.maxVal, score);
            this.minVal = Math.min(this.minVal, score);
            this.maxLengthLabel = Math.max(this.maxLengthLabel, label.length());
        }
    }

    record Benchmark(Histogram histogram, String name, double baseLineScore, double currentScore) {
        @Override
        public String toString() {
            char sign = currentScore() >= baseLineScore() ? '+' : '-';
            double maxOps = Math.max(currentScore(), baseLineScore());
            double minOps = Math.min(currentScore(), baseLineScore());
            double singlePercent = maxOps / 100;
            return String.format("%s (%s%.2f%%)", name(), sign, (maxOps - minOps) / singlePercent);
        }

        String render() {
            return histogram.render(this);
        }
    }
}
