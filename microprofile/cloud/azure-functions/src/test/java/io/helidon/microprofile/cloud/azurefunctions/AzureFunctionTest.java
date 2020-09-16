/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class AzureFunctionTest {

    @Test
    public void sample() {
        String message = "This is a test";
        int expected = message.hashCode();
        HttpRequestMessage<Optional<String>> request = Mockito.mock(HttpRequestMessage.class);
        ExecutionContext context = Mockito.mock(ExecutionContext.class);
        Optional<String> optional = Optional.of(message);
        Mockito.when(request.getBody()).thenReturn(optional);
        int result = new SampleFunction().execute(request, context);
        assertEquals(expected, result);
    }

}
