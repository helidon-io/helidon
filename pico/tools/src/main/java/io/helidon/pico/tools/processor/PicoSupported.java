/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.tools.processor;

import java.util.Objects;

import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.types.TypeName;

/**
 * Centralized utility to help callers determine what is and is not supported.
 */
public class PicoSupported {

    private PicoSupported() {
    }

    /**
     * Returns true if the targetElement can be supported within the pico model as an injection point target.
     *
     * @param throwIfNotSupported   should an exception be thrown if not supported
     * @param logger                the optional logger to use if not supported
     * @param serviceType           the enclosing service type
     * @param targetElement         the target element description
     * @param isPrivate             is the target element private
     * @param isStatic              is the target element static
     * @return true if the target supported pico injection, false otherwise (assuming not throwIfNotSupported)
     */
    public static boolean isSupportedInjectionPoint(boolean throwIfNotSupported,
                                      System.Logger logger,
                                      TypeName serviceType,
                                      Object targetElement,
                                      boolean isPrivate,
                                      boolean isStatic) {
        boolean supported = (!isPrivate && !isStatic);
        if (!supported) {
            String message = "static and private injection points are not supported: " + serviceType + "." + targetElement;
            if (Objects.nonNull(logger)) {
                System.Logger.Level level = (throwIfNotSupported) ? System.Logger.Level.ERROR : System.Logger.Level.WARNING;
                logger.log(level, message);
            }
            if (throwIfNotSupported) {
                throw new ToolsException(message);
            }
        }
        return supported;
    }

}
