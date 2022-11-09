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

package io.helidon.pico;

/**
 * A general purpose exception.
 *
 * @see PicoServiceProviderException
 * @see InjectionException
 * @see InvocationException
 */
public class PicoException extends RuntimeException {

    /**
     * A general purpose exception from Pico.
     *
     * @param msg the message
     */
    public PicoException(String msg) {
        super(msg);
    }

    /**
     * A general purpose exception from Pico.
     *
     * @param msg             the message
     * @param cause           the root cause
     */
    public PicoException(String msg,
                         Throwable cause) {
        super(msg, cause);
    }

}
