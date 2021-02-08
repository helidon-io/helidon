/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.microprofile.cloud.common;

import java.util.Optional;
import java.util.logging.Logger;

import javax.enterprise.inject.spi.CDI;

import io.helidon.microprofile.cdi.Main;

/**
 * Contains common functionality to be extended by every cloud implementations.
 *
 * @param <T> the delegate type
 */
public abstract class CommonCloudFunction<T> {

    /**
     * First time is invoked it initializes Helidon and creates a delegated instance.
     * Next invocations will reuse the same instance.
     *
     * @return the implementation of specific cloud interface
     */
    @SuppressWarnings("unchecked")
    protected T delegate() {
        return (T) LazyHelidonInitializer.DELEGATE;
    }

    /**
     * Avoids Helidon is initialized in case CommonCloudFunction is never used.
     *
     */
    private static class LazyHelidonInitializer {

        private static final Logger LOGGER = Logger.getLogger(LazyHelidonInitializer.class.getName());
        private static final Object DELEGATE;

        static {
            Main.main(new String[0]);
            LOGGER.fine(() -> "Helidon is started");
            Optional<Object> optional = CDI.current().select(CloudFunctionHolder.class).get().cloudFunction();
            if (optional.isPresent()) {
                DELEGATE = optional.get();
            } else {
                throw new IllegalStateException("No class annotated with @CloudFunction was found");
            }
            LOGGER.fine(() -> "Delegate is " + DELEGATE);
        }

    }

}
