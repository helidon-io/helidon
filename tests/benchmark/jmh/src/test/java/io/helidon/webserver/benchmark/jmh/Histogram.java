/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;

public class Histogram {
    private static final String REGRESSION_BAR = "▒";
    private static final String PROGRESSION_BAR = "█";

    final List<Benchmark> benchmarks = new ArrayList<>();
    private int maxLengthLabel;

    public static Histogram create(Collection<RunResult> results, Map<String, BaseLine> baseLineMap) {
        Histogram histogram = new Histogram();
        for (var result : results) {
            String benchmarkName = result.getParams().getBenchmark();
            BaseLine baseLine = baseLineMap.computeIfAbsent(benchmarkName, s -> BaseLine.create(result));

            String scoreUnit = result.getPrimaryResult().getScoreUnit();
            String baseLineUnit = baseLine.getPrimaryMetric().getScoreUnit();
            if (!scoreUnit.equals(baseLineUnit)) {
                throw new IllegalArgumentException(benchmarkName + " baseline unit " + baseLineUnit
                                                           + " differs from current unit " + scoreUnit
                                                           + "; regenerate the baseline with webserver.jmh.resetBaseline=true");
            }
            double baseLineScore = baseLine.getPrimaryMetric().getScore();
            double resultScore = result.getPrimaryResult().getScore();
            histogram.add(baseLine.simpleBenchmarkName(), baseLineScore, resultScore, result.getParams().getMode(), scoreUnit);
        }
        return histogram;
    }

    public Histogram add(String label, double baseLineScore, double currentScore, Mode mode, String scoreUnit) {
        this.benchmarks.add(new Benchmark(this, label, baseLineScore, currentScore, mode, scoreUnit));
        this.maxLengthLabel = Math.max(this.maxLengthLabel, label.length());
        return this;
    }

    public String render(Benchmark b) {
        StringJoiner joiner = new StringJoiner("\n");
        double maxVal = Math.max(b.baseLineScore, b.currentScore);
        int baseLinePercentage = Math.toIntExact(Math.round(b.baseLineScore * 100 / maxVal));
        int currPercentage = Math.toIntExact(Math.round(b.currentScore * 100 / maxVal));
        String bar = b.regression() ? REGRESSION_BAR : PROGRESSION_BAR;
        joiner.add(String.format("==================== %s", b));
        joiner.add(String.format("%" + maxLengthLabel + "s %s %.3f %s",
                                 "Baseline",
                                 bar.repeat(baseLinePercentage),
                                 b.baseLineScore,
                                 b.scoreUnit));
        joiner.add(String.format("%" + maxLengthLabel + "s %s %.3f %s",
                                 "Current",
                                 bar.repeat(currPercentage),
                                 b.currentScore,
                                 b.scoreUnit));
        return joiner.toString();
    }

    record Benchmark(Histogram histogram, String name, double baseLineScore, double currentScore, Mode mode, String scoreUnit) {
        @Override
        public String toString() {
            char sign = regression() ? '-' : '+';
            double maxScore = Math.max(currentScore(), baseLineScore());
            double minScore = Math.min(currentScore(), baseLineScore());
            double singlePercent = maxScore / 100;
            return String.format("%s (%s%.2f%%)", name(), sign, (maxScore - minScore) / singlePercent);
        }

        boolean higherIsBetter() {
            return switch (mode) {
                case Throughput -> true;
                case AverageTime, SampleTime, SingleShotTime -> false;
                case All -> throw new IllegalArgumentException("A benchmark result must have a concrete mode");
            };
        }

        boolean regression() {
            return higherIsBetter() ? currentScore < baseLineScore : currentScore > baseLineScore;
        }

        String render() {
            return histogram.render(this);
        }
    }
}
