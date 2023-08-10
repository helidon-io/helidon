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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.annotation.JsonbVisibility;
import org.eclipse.yasson.FieldAccessStrategy;
import org.eclipse.yasson.YassonConfig;
import org.openjdk.jmh.results.RunResult;

@JsonbVisibility(FieldAccessStrategy.class)
public class BaseLine {

    private static final Jsonb JSON_B = JsonbBuilder.newBuilder()
            .withConfig(new JsonbConfig()
                                .setProperty(YassonConfig.FORMATTING, true)
                                .withNullValues(true).withAdapters())
            .build();

    private String benchmark;
    private PrimaryMetric primaryMetric;

    public BaseLine() {
    }

    public BaseLine(String benchmark, double score, String scoreUnit) {
        this.benchmark = benchmark;
        this.primaryMetric = new PrimaryMetric(score, scoreUnit);
    }

    public static BaseLine create(RunResult runResult) {
        return new BaseLine(
                runResult.getParams().getBenchmark(),
                runResult.getPrimaryResult().getScore(),
                runResult.getPrimaryResult().getScoreUnit()
        );
    }

    public static void save(Path file, Map<String, BaseLine> baseLineMap) throws IOException {
        OutputStream outputStream = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        JSON_B.toJson(baseLineMap.values(), outputStream);
        outputStream.flush();
        outputStream.close();
    }

    public static Map<String, BaseLine> load(Path file) throws IOException {
        List<BaseLine> baseLines = JSON_B.fromJson(Files.newInputStream(file, StandardOpenOption.READ),
                                                   new ArrayList<BaseLine>() {
                                                   }.getClass().getGenericSuperclass());
        return baseLines.stream().collect(Collectors.toMap(BaseLine::getBenchmark, Function.identity()));
    }

    public String getBenchmark() {
        return benchmark;
    }

    public String simpleBenchmarkName() {
        String[] fqdn = getBenchmark().split("\\.");
        return String.format("%s.%s", fqdn[fqdn.length - 2], fqdn[fqdn.length - 1]);
    }

    public PrimaryMetric getPrimaryMetric() {
        return primaryMetric;
    }

    @JsonbVisibility(FieldAccessStrategy.class)
    public static class PrimaryMetric {
        private double score;
        private String scoreUnit;

        public PrimaryMetric() {
        }

        public PrimaryMetric(double score, String scoreUnit) {
            this.score = score;
            this.scoreUnit = scoreUnit;
        }

        public double getScore() {
            return score;
        }

        public String getScoreUnit() {
            return scoreUnit;
        }
    }
}
