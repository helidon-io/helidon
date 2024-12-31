/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject;

import io.helidon.service.registry.Service;

final class DescribeTypes {
    private DescribeTypes() {
    }

    /**
     * A greeting that needs to be described separately.
     */
    @Service.Describe
    @Service.Contract
    interface DescribedContract {
        String sayHello();
    }

    /**
     * A non-service implementation of the greeting.
     * It is instantiated manually and passed to the registry manager config.
     */
    static class DescribedContractImpl implements DescribedContract {
        @Override
        public String sayHello() {
            return "Hello World!";
        }
    }

    /**
     * A singleton service that injects the described greeting.
     *
     * @param myContract myContract
     */
    @Service.Singleton
    record DescribedReceiver(DescribedContract myContract) {
    }

}
