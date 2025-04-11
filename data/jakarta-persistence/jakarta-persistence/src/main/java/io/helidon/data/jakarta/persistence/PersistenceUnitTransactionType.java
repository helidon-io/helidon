/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.jakarta.persistence;

/**
 * Temporary replacement of Jakarta Persistence 3.2 {@code PersistenceUnitTransactionType} class
 * while Helidon depends on Jakarta Persistence 3.1.
 *
 * @deprecated Will be replaced by Jakarta Persistence 3.2 API
 */
@Deprecated
public enum PersistenceUnitTransactionType {

    /**
     * Transaction management via JTA.
     */
    JTA,

    /**
     * Resource-local transaction management.
     */
    RESOURCE_LOCAL

}
