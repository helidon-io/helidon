/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.health.checks;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Formatter;
import java.util.Locale;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.health.HealthCheckException;
import io.helidon.health.common.BuiltInHealthCheck;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * A health check that verifies whether the server is running out of disk space. This health check will
 * check whether the usage of the disk associated with a specific path exceeds a given threshold. If it does,
 * then the health check will fail.
 * <p>
 * Also, by default, it will check the root path {@code /}. These defaults can be modified using the
 * {@value CONFIG_KEY_PATH} property (default {@value DEFAULT_PATH}), and the {@value CONFIG_KEY_THRESHOLD_PERCENT}
 * property (default {@value DEFAULT_THRESHOLD}, virtually 100). The threshold should be set to a percent, such as 50 for 50% or
 * 99 for 99%. If disk usage
 * exceeds this threshold, then the health check will fail.
 * </p>
 * <p>
 * Unless ephemeral disk space is being used, it is often not sufficient to simply restart a server in the event
 * that that health check fails.
 * </p>
 * <p>
 * This health check is automatically created and registered through CDI.
 *</p>
 * <p>
 * This health check can be referred to in properties as {@code diskSpace}. So for example, to exclude this
 * health check from being exposed, use {@code helidon.health.exclude: diskSpace}.
 * </p>
 */
@Liveness
@ApplicationScoped // this will be ignored if not within CDI
@BuiltInHealthCheck
public final class DiskSpaceHealthCheck implements HealthCheck {
    /**
     * Default path on the file system the health check will be executed for.
     * If you need to check a different path (e.g. application runtime disks are not mounted the same
     * directory as application path), use
     * {@link io.helidon.health.checks.DiskSpaceHealthCheck.Builder#path(java.nio.file.Path)}.
     * When running within a MicroProfile server, you can configure path using a configuration key
     * {@value #CONFIG_KEY_PATH}
     * Defaults to {@value}
     */
    public static final String DEFAULT_PATH = ".";
    /**
     * Default threshold percent, when this check starts reporting
     * {@link org.eclipse.microprofile.health.HealthCheckResponse.State#DOWN}.
     */
    public static final double DEFAULT_THRESHOLD = 99.999;
    /**
     * Configuration key for path, when configured through Microprofile config.
     */
    public static final String CONFIG_KEY_PATH = "helidon.health.diskSpace.path";
    /**
     * Configuration key for threshold percent, when configured through Microprofile config.
     */
    public static final String CONFIG_KEY_THRESHOLD_PERCENT = "helidon.health.diskSpace.thresholdPercent";

    private static final long KB = 1024;
    private static final long MB = 1024 * KB;
    private static final long GB = 1024 * MB;
    private static final long TB = 1024 * GB;
    private static final long PB = 1024 * TB;

    private final double thresholdPercent;
    private final FileStore fileStore;

    // unit tests
    DiskSpaceHealthCheck(FileStore fileStore, double thresholdPercent) {
        this.fileStore = fileStore;
        this.thresholdPercent = thresholdPercent;
    }

    @Inject
    DiskSpaceHealthCheck(
            @ConfigProperty(name = CONFIG_KEY_PATH, defaultValue = DEFAULT_PATH) File path,
            @ConfigProperty(name = CONFIG_KEY_THRESHOLD_PERCENT, defaultValue = "99.999") double thresholdPercent
    ) {
        try {
            this.fileStore = Files.getFileStore(path.toPath());
        } catch (IOException e) {
            throw new HealthCheckException("Failed to obtain file store for path " + path.getAbsolutePath(), e);
        }
        this.thresholdPercent = thresholdPercent;
    }

    private DiskSpaceHealthCheck(Builder builder) {
        try {
            this.fileStore = Files.getFileStore(builder.path);
        } catch (IOException e) {
            throw new HealthCheckException("Failed to obtain file store for path " + builder.path.toAbsolutePath(), e);
        }
        this.thresholdPercent = builder.threshold;
    }

    static String format(long bytes) {
        //Formatter ensures that returned delimiter will be always the same
        Formatter formatter = new Formatter(Locale.US);
        if (bytes >= PB) {
            return formatter.format("%.2f PB", bytes / (double) PB).toString();
        } else if (bytes >= TB) {
            return formatter.format("%.2f TB", bytes / (double) TB).toString();
        } else if (bytes >= GB) {
            return formatter.format("%.2f GB", bytes / (double) GB).toString();
        } else if (bytes >= MB) {
            return formatter.format("%.2f MB", bytes / (double) MB).toString();
        } else if (bytes >= KB) {
            return formatter.format("%.2f KB", bytes / (double) KB).toString();
        } else {
            return bytes + " bytes";
        }
    }

    /**
     * A new fluent API builder to configure this health check.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new disk space health check to use, using defaults for all configurable values.
     *
     * @return a new health check to register with
     *         {@link io.helidon.health.HealthSupport.Builder#add(org.eclipse.microprofile.health.HealthCheck...)}
     * @see #DEFAULT_PATH
     * @see #DEFAULT_THRESHOLD
     */
    public static DiskSpaceHealthCheck create() {
        return builder().build();
    }

    @Override
    public HealthCheckResponse call() {
        long diskFreeInBytes;
        long totalInBytes;
        try {
            diskFreeInBytes = fileStore.getUsableSpace();
            totalInBytes = fileStore.getTotalSpace();
        } catch (IOException e) {
            throw new HealthCheckException("Failed to obtain disk space data", e);
        }

        long usedInBytes = totalInBytes - diskFreeInBytes;
        long threshold = (long) ((thresholdPercent / 100) * totalInBytes);

        //Formatter ensures that returned delimiter will be always the same
        Formatter formatter = new Formatter(Locale.US);

        return HealthCheckResponse.named("diskSpace")
                .state(threshold >= usedInBytes)
                .withData("percentFree", formatter.format("%.2f%%", 100 * ((double) diskFreeInBytes / totalInBytes)).toString())
                .withData("free", DiskSpaceHealthCheck.format(diskFreeInBytes))
                .withData("freeBytes", diskFreeInBytes)
                .withData("total", DiskSpaceHealthCheck.format(totalInBytes))
                .withData("totalBytes", totalInBytes)
                .build();
    }

    /**
     * Fluent API builder for {@link io.helidon.health.checks.DiskSpaceHealthCheck}.
     */
    public static final class Builder implements io.helidon.common.Builder<DiskSpaceHealthCheck> {
        private Path path = Paths.get(DEFAULT_PATH);
        private double threshold = DEFAULT_THRESHOLD;

        private Builder() {
        }

        @Override
        public DiskSpaceHealthCheck build() {
            return new DiskSpaceHealthCheck(this);
        }

        /**
         * Path on the file system to find a file system.
         *
         * @param path path to use
         * @return updated builder instance
         * @see #path(java.nio.file.Path)
         */
        public Builder path(String path) {
            this.path = Paths.get(path);
            return this;
        }

        /**
         * Path on the file system to find a file system.
         *
         * @param path path to use
         * @return updated builder instance
         */
        public Builder path(Path path) {
            this.path = path;
            return this;
        }

        /**
         * Threshold percent. When disk is fuller than this percentage, health is switched to down.
         *
         * @param threshold percentage
         * @return updated builder instance
         */
        public Builder thresholdPercent(double threshold) {
            this.threshold = threshold;
            return this;
        }
    }
}
