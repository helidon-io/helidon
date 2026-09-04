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

package io.helidon.quic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ResultRole;
import org.openjdk.jmh.results.SampleTimeResult;
import org.openjdk.jmh.results.ScalarResult;
import org.openjdk.jmh.util.SampleBuffer;

/**
 * Collects the sequence-accounted delivery and delivered-packet latency results produced by an open-loop QUIC ingress
 * iteration.
 */
public final class QuicOpenLoopMetricsProfiler implements InternalProfiler {
    @Override
    public String getDescription() {
        return "Sequence-accounted open-loop QUIC ingress delivery and latency";
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        QuicEndpointIngressJmhBenchmark.clearCompletedOpenLoopWindow();
    }

    @Override
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams,
                                                       IterationParams iterationParams,
                                                       IterationResult result) {
        QuicEndpointIngressJmhBenchmark.CompletedOpenLoopWindow window =
                QuicEndpointIngressJmhBenchmark.takeCompletedOpenLoopWindow();
        if (window == null) {
            return List.of();
        }

        long accepted = 0;
        long received = 0;
        long routed = 0;
        long late = 0;
        SampleBuffer latency = new SampleBuffer();
        for (int wordIndex = 0; wordIndex < window.receivedBits.length(); wordIndex++) {
            long acceptedWord = window.acceptedBits[wordIndex];
            long receivedWord = window.receivedBits.get(wordIndex);
            accepted += Long.bitCount(acceptedWord);
            received += Long.bitCount(receivedWord);
            long routedWord = acceptedWord & receivedWord;
            routed += Long.bitCount(routedWord);
            while (routedWord != 0) {
                int bit = Long.numberOfTrailingZeros(routedWord);
                int sequence = wordIndex * Long.SIZE + bit;
                long latencyNanos = window.latencyNanos[sequence];
                if (latencyNanos <= 0) {
                    throw new IllegalStateException("Open-loop datagram has no recorded latency: " + sequence);
                }
                latency.add(latencyNanos);
                if (latencyNanos > window.lateNanos) {
                    late++;
                }
                routedWord &= routedWord - 1;
            }
            long receivedWithoutAcceptance = receivedWord & ~acceptedWord;
            if (receivedWithoutAcceptance != 0) {
                throw new IllegalStateException("Open-loop receiver observed a datagram not accepted by the sender");
            }
        }
        if (accepted != window.acceptedPackets) {
            throw new IllegalStateException("Open-loop accepted-packet accounting mismatch: expected="
                                                    + window.acceptedPackets + ", observed=" + accepted);
        }
        if (received != window.uniqueReceivedPackets) {
            throw new IllegalStateException("Open-loop received-packet accounting mismatch: expected="
                                                    + window.uniqueReceivedPackets + ", observed=" + received);
        }
        if (window.attemptedPackets + window.schedulerSkippedPackets != window.plannedPackets) {
            throw new IllegalStateException("Open-loop pacing accounting does not equal the planned packet count");
        }
        if (window.acceptedPackets + window.socketRejectedPackets != window.attemptedPackets) {
            throw new IllegalStateException("Open-loop socket accounting does not equal the attempted packet count");
        }
        if (window.duplicatePackets != 0 || window.crossWindowPackets != 0 || window.outsideWindowPackets != 0) {
            throw new IllegalStateException("Open-loop delivery window was contaminated: duplicates="
                                                    + window.duplicatePackets + ", crossWindow="
                                                    + window.crossWindowPackets + ", outsideWindow="
                                                    + window.outsideWindowPackets);
        }

        long missing = accepted - routed;
        List<Result> results = new ArrayList<>();
        add(results, "openloop.planned", window.plannedPackets, "packets");
        add(results, "openloop.attempted", window.attemptedPackets, "packets");
        add(results, "openloop.schedulerSkipped", window.schedulerSkippedPackets, "packets");
        add(results, "openloop.socketRejected", window.socketRejectedPackets, "packets");
        add(results, "openloop.accepted", accepted, "packets");
        add(results, "openloop.routed", routed, "packets");
        add(results, "openloop.missingAtDrainDeadline", missing, "packets");
        add(results, "openloop.late", late, "packets");
        add(results, "openloop.duplicates", window.duplicatePackets, "packets");
        add(results, "openloop.crossWindow", window.crossWindowPackets, "packets");
        add(results, "openloop.outsideWindow", window.outsideWindowPackets, "packets");
        add(results, "openloop.drainArrivals", window.drainArrivals, "packets");
        add(results,
            "openloop.acceptedBytes",
            Math.multiplyExact(accepted, window.packetSize),
            "bytes");
        add(results, "openloop.accepted.pps", rate(accepted, window.offerNanos), "packets/s");
        add(results, "openloop.routed.pps", rate(routed, window.offerNanos), "packets/s");
        add(results,
            "openloop.accepted.Gbit",
            gigabitsPerSecond(accepted, window.packetSize, window.offerNanos),
            "Gbit/s");
        add(results,
            "openloop.routed.Gbit",
            gigabitsPerSecond(routed, window.packetSize, window.offerNanos),
            "Gbit/s");
        add(results,
            "openloop.schedulerSkip.ratio",
            ratio(window.schedulerSkippedPackets, window.plannedPackets),
            "ratio");
        add(results,
            "openloop.socketReject.ratio",
            ratio(window.socketRejectedPackets, window.attemptedPackets),
            "ratio");
        add(results, "openloop.missing.ratio", ratio(missing, accepted), "ratio");
        add(results, "openloop.late.ratio", ratio(late, routed), "ratio");
        add(results,
            "openloop.maxSchedulerStall",
            (double) window.maxSchedulerStallNanos / TimeUnit.MICROSECONDS.toNanos(1),
            "us");
        add(results,
            "openloop.maxResidualScheduleLag",
            (double) window.maxResidualScheduleLagNanos / TimeUnit.MICROSECONDS.toNanos(1),
            "us");
        add(results, "openloop.senderCount", window.senderCount, "senders");
        add(results, "openloop.sender.accepted.min", window.minAcceptedBySender, "packets");
        add(results, "openloop.sender.accepted.max", window.maxAcceptedBySender, "packets");
        add(results,
            "openloop.sender.schedulerSkip.ratio.min",
            window.minSchedulerSkipRatioBySender,
            "ratio");
        add(results,
            "openloop.sender.schedulerSkip.ratio.max",
            window.maxSchedulerSkipRatioBySender,
            "ratio");
        add(results,
            "openloop.sender.completionSkew",
            (double) window.senderCompletionSkewNanos / TimeUnit.MICROSECONDS.toNanos(1),
            "us");
        add(results, "openloop.connectionIdLength", window.connectionIdLength, "bytes");
        add(results, "openloop.receiverSocketBuffer", window.receiverSocketBufferBytes, "bytes");
        results.add(new ScalarResult("openloop.senderSocketBuffer.min",
                                     window.minSenderSocketBufferBytes,
                                     "bytes",
                                     AggregationPolicy.MIN));
        results.add(new ScalarResult("openloop.senderSocketBuffer.max",
                                     window.maxSenderSocketBufferBytes,
                                     "bytes",
                                     AggregationPolicy.MAX));
        add(results,
            "openloop.offerWindow",
            (double) window.offerNanos / TimeUnit.MILLISECONDS.toNanos(1),
            "ms");
        add(results,
            "openloop.drainWindow",
            (double) window.drainNanos / TimeUnit.MILLISECONDS.toNanos(1),
            "ms");
        results.add(new SampleTimeResult(ResultRole.SECONDARY,
                                         "openloop.deliveredLatency",
                                         latency,
                                         TimeUnit.NANOSECONDS));
        return results;
    }

    private static void add(List<Result> results, String label, double score, String unit) {
        results.add(new ScalarResult(label, score, unit, AggregationPolicy.AVG));
    }

    private static double rate(long packets, long durationNanos) {
        return (double) packets * TimeUnit.SECONDS.toNanos(1) / durationNanos;
    }

    private static double gigabitsPerSecond(long packets, int packetSize, long durationNanos) {
        return (double) packets * packetSize * Byte.SIZE / durationNanos;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }
}
