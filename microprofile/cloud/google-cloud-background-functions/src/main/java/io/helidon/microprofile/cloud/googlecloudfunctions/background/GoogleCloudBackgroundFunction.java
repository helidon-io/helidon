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
package io.helidon.microprofile.cloud.googlecloudfunctions.background;

import java.util.logging.Logger;

import io.helidon.microprofile.cloud.common.CommonCloudFunction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.functions.RawBackgroundFunction;

/**
 * Helidon Google Cloud Function implementation of com.google.cloud.functions.RawBackgroundFunction.
 * This is the class that should be specified as entry point in gcloud
 *
 * @param <T> the type of the event
 */
public class GoogleCloudBackgroundFunction<T> extends CommonCloudFunction<BackgroundFunction<T>> implements RawBackgroundFunction {

    private static final Logger LOGGER = Logger.getLogger(GoogleCloudBackgroundFunction.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void accept(String event, Context context) throws Exception {
        LOGGER.fine(() -> "Event: " + event);
        T type = MAPPER.readValue(event, new TypeReference<T>(){});
        delegate().accept(type, context);
    }

}
