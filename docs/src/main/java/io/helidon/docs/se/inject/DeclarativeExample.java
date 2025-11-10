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

package io.helidon.docs.se.inject;

import java.io.IOException;
import java.util.Map;

import io.helidon.common.Default;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.config.Configuration;
import io.helidon.faulttolerance.Ft;
import io.helidon.http.HeaderNames;
import io.helidon.http.Http;
import io.helidon.logging.common.LogConfig;
import io.helidon.scheduling.Scheduling;
import io.helidon.service.registry.Binding;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.validation.Validation;
import io.helidon.validation.ValidatorContext;
import io.helidon.validation.ValidatorResponse;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;
import io.helidon.webclient.api.RestClient;
import io.helidon.webserver.http.RestServer;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

@SuppressWarnings("deprecation")
public class DeclarativeExample {
    private DeclarativeExample() {
    }

    // tag::snippet_3[]
    @RestClient.Endpoint("${greet-service.client.uri:http://localhost:8080}")
    @RestClient.Header(name = HeaderNames.USER_AGENT_NAME, value = "my-client")
    interface GreetClient {
        @Http.GET
        @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
        JsonObject getDefaultMessageHandler();
    }
    // end::snippet_3[]

    // tag::snippet_1[]
    @Service.GenerateBinding // generated binding to bypass discovery and runtime binding
    public static class Main {
        public static void main(String[] args) {
            // configure logging
            LogConfig.configureRuntime();

            // start the "container"
            ServiceRegistryManager.start(ApplicationBinding.create());
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @RestServer.Endpoint // identifies this class as a server endpoint
    @Http.Path("/greet") // serve this endpoint on /greet context root (path)
    @Service.Singleton   // a singleton service (single instance within a service registry)
    static class GreetEndpoint {
        private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
        private final String greeting;

        // inject app.greeting configuration value, use "Hello" if not configured
        GreetEndpoint(@Configuration.Value("app.greeting") @Default.Value("Hello") String greeting) {
            this.greeting = greeting;
        }

        @Http.GET   // HTTP GET endpoint
        @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE) // produces entity of application/json media type
        public JsonObject getDefaultMessageHandler() {
            // build the JSON object (requires `helidon-http-media-jsonp` on classpath)
            return JSON.createObjectBuilder()
                    .add("message", greeting + " World!")
                    .build();
        }
    }
    // end::snippet_2[]

    // tag::snippet_4[]
    @Service.Singleton
    static class AlgorithmService {
        @Ft.Fallback(value = "fallbackAlgorithm", applyOn = IOException.class)
        String algorithm() throws IOException {
            // may throw an exception
            return "some-algorithm";
        }

        // method that would be called if #algorithm fails with an IOException
        String fallbackAlgorithm()  {
            return "default";
        }
    }
    // end::snippet_4[]

    // tag::snippet_5[]
    @Service.Singleton
    static class CacheService {
        @Scheduling.FixedRate("PT5S")
        void checkCache()  {
            // do something every 5 seconds
        }
    }
    // end::snippet_5[]


    @Validation.Validated
    record MyType(@Validation.String.Pattern(".*valid.*") @Validation.NotNull String validString,
                  @Validation.Integer.Min(42) int validInt) {
    }

    // tag::snippet_7[]
    @Service.Singleton
    static class ValidatedService {
        @Validation.String.NotBlank // validates the response
        String process(@Validation.Valid @Validation.NotNull MyType myType) {
            // result of the logic
            return "some result";
        }
    }
    // end::snippet_7[]

    // tag::snippet_8[]
    @Validation.NotNull
    @Validation.String.NotBlank
    public @interface NonNullNotBlank {
    }
    // end::snippet_8[]

    // tag::snippet_9[]
    @Validation.NotNull // will add not-null constraint as well
    @Validation.Constraint
    public @interface CustomConstraint {
    }
    // end::snippet_9[]

    // tag::snippet_10[]
    @Service.Singleton
    @Service.NamedByType(CustomConstraint.class)
    static class CustomConstraintValidatorProvider implements ConstraintValidatorProvider {
        @Override
        public ConstraintValidator create(TypeName typeName, Annotation constraintAnnotation) {
            // we could Validation the type here, but we don't need to - depends on constraint
            return new CustomValidator(constraintAnnotation);
        }

        private static class CustomValidator implements ConstraintValidator {
            private final Annotation annotation;

            private CustomValidator(Annotation annotation) {
                this.annotation = annotation;
            }

            @Override
            public ValidatorResponse check(ValidatorContext context, Object value) {
                if (value == null) {
                    // we leave the `not-null` Validation to the "meta-annotation" on CustomConstraint
                    return ValidatorResponse.create();
                }

                // if string, and the value is "good", it is OK
                if (value instanceof String str) {
                    if (str.equals("good")) {
                        return ValidatorResponse.create();
                    }
                }

                return ValidatorResponse.create(annotation, "Must be \"good\" string", value);
            }
        }
    }
    // end::snippet_10[]


    private static class ApplicationBinding {
        static Binding create() {
            return null;
        }
    }
}
