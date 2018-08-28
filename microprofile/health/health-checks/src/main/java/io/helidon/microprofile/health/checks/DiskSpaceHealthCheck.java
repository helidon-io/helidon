/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.health.checks;

import java.io.File;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

/**
 * A health check that verifies whether the server is running out of disk space. This health check will
 * check whether the usage of the disk associated with a specific path exceeds a given threshold. If it does,
 * then the health check will fail.
 *
 * By default, this health check has a threshold of 100%, meaning that it will never fail the threshold check.
 * Also, by defaut, it will check the root path "/". These defaults can be modified using the
 * {@code healthCheck.diskSpace.path} property, and the {@code healthCheck.diskSpace.thresholdPercent}
 * property. The threshold should be set to a fraction, such as .50 for 50% or .99 for 99%. If disk usage
 * exceeds this threshold, then the health check will fail.
 *
 * Unless ephemeral disk space is being used, it is often not sufficient to simply restart a server in the event
 * that that health check fails.
 *
 * This health check is automatically created and registered through CDI.
 *
 * This health check can be referred to in properties as "diskSpace". So for example, to exclude this
 * health check from being exposed, use "helidon.health.exclude: diskSpace".
 */
@Health
@ApplicationScoped
public final class DiskSpaceHealthCheck implements HealthCheck {
    private static final long KB = 1024;
    private static final long MB = 1024 * KB;
    private static final long GB = 1024 * MB;
    private static final long TB = 1024 * GB;
    private static final long PB = 1024 * TB;

    private final File path;
    private final double thresholdPercent;

    @Inject
    DiskSpaceHealthCheck(
            @ConfigProperty(name = "helidon.health.diskSpace.path", defaultValue = "/") File path,
            @ConfigProperty(name = "helidon.health.diskSpace.thresholdPercent", defaultValue = "99.999") double thresholdPercent
    ) {
        this.path = path;
        this.thresholdPercent = thresholdPercent;
    }

    static String format(long bytes) {
        if (bytes >= PB) {
            return String.format("%.2f PB", bytes / (double) PB);
        } else if (bytes >= TB) {
            return String.format("%.2f TB", bytes / (double) TB);
        } else if (bytes >= GB) {
            return String.format("%.2f GB", bytes / (double) GB);
        } else if (bytes >= MB) {
            return String.format("%.2f MB", bytes / (double) MB);
        } else if (bytes >= KB) {
            return String.format("%.2f KB", bytes / (double) KB);
        } else {
            return bytes + " bytes";
        }
    }

    @Override
    public HealthCheckResponse call() {
        long diskFreeInBytes = this.path.getUsableSpace();
        long totalInBytes = this.path.getTotalSpace();
        long usedInBytes = totalInBytes - diskFreeInBytes;
        long threshold = (long) ((thresholdPercent / 100) * totalInBytes);

        return HealthCheckResponse.named("diskSpace")
                .state(threshold >= usedInBytes)
                .withData("percentFree", String.format("%.2f%%", 100 * ((double) diskFreeInBytes / totalInBytes)))
                .withData("free", DiskSpaceHealthCheck.format(diskFreeInBytes))
                .withData("freeBytes", diskFreeInBytes)
                .withData("total", DiskSpaceHealthCheck.format(totalInBytes))
                .withData("totalBytes", totalInBytes)
                .build();
    }
}
