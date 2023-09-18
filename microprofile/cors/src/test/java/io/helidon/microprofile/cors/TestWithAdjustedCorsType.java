/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddExtension;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@AddBean(CrossOriginTest.CorsResource0.class)
@AddExtension(TestWithAdjustedCorsType.AugmentingExtension.class)
class TestWithAdjustedCorsType extends BaseCrossOriginTest {

    @Inject
    private WebTarget webTarget;

    @Test
    void checkAdjustedResource() {
        // The extension below will have augmented the AnnotatedType (adding a synthetic annotation).
        // Weld delivers a different concrete subtype of AnnotatedType to the CORS extension as a result.
        // Make sure the CORS request still works correctly.

        Response response = webTarget.path("/cors0")
                .request()
                .header("Origin", "http://foo.com")
                .header("Host", "here.com")
                .buildGet()
                .invoke();

        assertThat("Status from simple CORS request", response.getStatus(), is(200));
    }

    public static class AugmentingExtension implements Extension {

        void processAnnotatedType(@Observes ProcessAnnotatedType<?> pat) {
            // Single out the CORS-controlled resource.
            if (pat.getAnnotatedType().getJavaClass().getName().equals(CrossOriginTest.CorsResource0.class.getName())) {
                pat.configureAnnotatedType()
                        .add(AugmentingAnnotation.Literal.getInstance());
            }
        }
    }

    @Target(ElementType.TYPE)
    static @interface AugmentingAnnotation {
            class Literal extends AnnotationLiteral<AugmentingAnnotation> implements AugmentingAnnotation {

            private static final long serialVersionUID = 1L;

            private static final Literal INSTANCE=new Literal();

            static Literal getInstance(){
            return INSTANCE;
            }

            private Literal(){
            }
        }
    }
}
