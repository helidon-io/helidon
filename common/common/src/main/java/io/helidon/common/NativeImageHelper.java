/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.common;

import java.util.Optional;

/**
 * Utility for GraalVM native image.
 */
public final class NativeImageHelper {
    private static final String SYS_PROP_IMAGE_CODE = "org.graalvm.nativeimage.imagecode";
    private static final String BUILD_TIME = "buildtime";
    private static final String RUNTIME = "runtime";

    private NativeImageHelper() {
    }

    /**
     * Check whether we are in native image runtime.
     *
     * @return {@code true} if we are in native image runtime at the time this method is called
     */
    public static boolean isRuntime() {
        return property()
                .map(RUNTIME::equals)
                .orElse(false);
    }

    /**
     * Check whether we are in native image build time.
     *
     * @return {@code true} if we are in native image build time at the time this method is called
     */
    public static boolean isBuildTime() {
        return property()
                .map(BUILD_TIME::equals)
                .orElse(false);
    }

    /**
     * Check whether we are in native image environment (either build time or runtime).
     *
     * @return {@code true} if we are in native image environment at the time this method is called
     */
    public static boolean isNativeImage() {
        return property().isPresent();
    }

    private static Optional<String> property() {
        return Optional.ofNullable(System.getProperty(SYS_PROP_IMAGE_CODE));
    }
}
