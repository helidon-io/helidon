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
package io.helidon.microprofile.cloud.azurefunctions;

import java.util.logging.Logger;

import javax.enterprise.inject.spi.CDI;

import io.helidon.microprofile.cloud.common.CommonCloudFunction;

import com.microsoft.azure.functions.ExecutionContext;

/**
 * Every Azure function must extend this class.
 * In case you need to create more Azure functions you will need to create new classes extending this one.
 *
 * @param <I> input of the function
 * @param <O> output of the function
 */
public abstract class AzureCloudFunction<I, O> extends CommonCloudFunction<AzureEmptyFunction> {

    private static final Logger LOGGER = Logger.getLogger(AzureCloudFunction.class.getName());

    /**
     * This method must be executed inside the Azure function.
     * It will initialize Helidon and will delegate in {@link #execute(Object, ExecutionContext)}
     *
     * @param input the input of the function
     * @param context context the Azure context
     * @return the output of the function
     */
    protected O handleRequest(I input, ExecutionContext context) {
        // Initialize Helidon
        AzureEmptyFunction emptyFunction = delegate();
        LOGGER.fine(() -> emptyFunction.getClass() + " is ignored");
        // Get instance of AzureCloudFunction with injections
        AzureCloudFunction<I, O> injected = CDI.current().select(getClass()).get();
        return injected.execute(input, context);
    }

    /**
     * To be implemented with the user business logic. Every injection in this class
     * will be accessible inside this method.
     *
     * @param input the input of the function
     * @param context the Azure context
     * @return the output of the function
     */
    protected abstract O execute(I input, ExecutionContext context);

}
