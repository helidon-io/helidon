/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *  
 */

package io.helidon.lra.coordinator.client;

/**
 * Exception in communication with coordinator.
 */
public class CoordinatorConnectionException extends RuntimeException {
    private int status;

    /**
     * Creates exception describing an error in communication with coordinator.
     *
     * @param message description of the error
     * @param status  http status which should be reported to LRA method based on this error
     */
    public CoordinatorConnectionException(String message, int status) {
        super(message);
        this.status = status;
    }

    /**
     * Creates exception describing an error in communication with coordinator.
     *
     * @param message description of the error
     * @param cause   parent exception
     * @param status  http status which should be reported to LRA method based on this error
     */
    public CoordinatorConnectionException(String message, Throwable cause, int status) {
        super(message, cause);
        this.status = status;
    }

    /**
     * Status which should be reported to LRA method based on this error.
     *
     * @return http status
     */
    public int status() {
        return status;
    }
}
