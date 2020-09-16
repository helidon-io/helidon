/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specify a class that is intended to use as a cloud function.
 *
 * A cloud function is a class that implements one of these interfaces:
 * - com.amazonaws.services.lambda.runtime.RequestHandler
 * - com.amazonaws.services.lambda.runtime.RequestStreamHandler
 * - com.google.cloud.functions.BackgroundFunction
 * - com.google.cloud.functions.HttpFunction
 *
 * Only one cloud function will be loaded. If Helidon finds more than one, it will load the function
 * having the {@link #value()} equals to the property {@link CloudFunctionCdiExtension#CLOUD_FUNCTION}
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CloudFunction {

    /**
     * Discriminates this CloudFunction from others. This allows to deploy an application having more than
     * one classes annotated with @CloudFunction instead of having multiple Maven modules for each one.
     * @return the value of the CloudFuntion
     */
    String value() default "";

}
