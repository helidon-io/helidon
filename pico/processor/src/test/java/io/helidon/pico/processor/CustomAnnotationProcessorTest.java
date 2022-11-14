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

package io.helidon.pico.processor;

import java.util.Collections;
import java.util.Set;

import javax.lang.model.element.ElementKind;

import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerResponse;
import io.helidon.pico.processor.spi.ExtensibleGetTemplateProducer;
import io.helidon.pico.processor.spi.impl.DefaultTemplateHelperTools;
import io.helidon.pico.processor.spi.impl.DefaultTemplateProducerRequest;
import io.helidon.pico.processor.testsubjects.BasicEndpoint;
import io.helidon.pico.processor.testsubjects.ExtendedHello;
import io.helidon.pico.processor.testsubjects.ExtendedHelloImpl;
import io.helidon.pico.processor.testsubjects.ExtensibleGET;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.types.DefaultTypedElementName;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.testsubjects.hello.World;
import io.helidon.pico.testsubjects.hello.WorldImpl;
import io.helidon.pico.tools.processor.TypeTools;

import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.types.DefaultTypeName.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CustomAnnotationProcessorTest {

    @Test
    public void annotationSupported() {
        CustomAnnotationProcessor processor = new CustomAnnotationProcessor();
        assertEquals("[interface io.helidon.pico.processor.testsubjects.ExtensibleGET, interface jakarta.inject.Singleton]",
                     processor.getAnnoTypes().toString());
    }


    @Test
    public void extensibleGET() {
        CustomAnnotationProcessor processor = new CustomAnnotationProcessor();

        DefaultTemplateProducerRequest req = DefaultTemplateProducerRequest
                .builder(create(ExtensibleGET.class))
//                .generatedType(TypeNameImpl.toName(ExpectedTemplateProduced_BasicEndpoint_ExtensibleGET_itWorks.class))
                .enclosingClassType(create(BasicEndpoint.class))
                .elementKind(ElementKind.METHOD)
                .elementName("itWorks")
                .elementType(create(BasicEndpoint.class))
                .elementArgs(Collections.singletonList(
                        DefaultTypedElementName
                            .create(create(String.class), null, "header",
                                    TypeTools.createAnnotationAndValueListFromAnnotations(BasicEndpoint.class.getAnnotations()))))
                .build();
        assertTrue(req.isFilerEnabled());
        DefaultTemplateHelperTools tools = new DefaultTemplateHelperTools(ExtensibleGetTemplateProducer.class);

        Set<CustomAnnotationTemplateProducer> producers = processor.getProducersForType(req.getAnnoType());
        assertNotNull(producers);
        assertEquals(1, producers.size());

        CustomAnnotationTemplateProducerResponse res = processor.process(producers.iterator().next(), req, tools);
        assertNotNull(res);
        assertEquals(ExtensibleGET.class.getName(), res.getAnnoType().name());
        assertEquals("{io.helidon.pico.processor.testsubjects.BasicEndpoint_ExtensibleGET_itWorks=package io.helidon"
                             + ".pico.processor.testsubjects;\n"
                             + "\n"
                             + "import io.helidon.common.Weight;\n"
                             + "import io.helidon.common.Weighted;\n"
                             + "\n"
                             + "import jakarta.inject.Inject;\n"
                             + "import jakarta.inject.Named;\n"
                             + "import jakarta.inject.Provider;\n"
                             + "import jakarta.inject.Singleton;\n"
                             + "\n"
                             + "@javax.annotation.processing.Generated({\"provider=oracle\", \"generator=io.helidon"
                             + ".pico.processor.spi.ExtensibleGetTemplateProducer\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "@Singleton\n"
                             + "@Named(\"io.helidon.pico.processor.testsubjects.ExtensibleGET\")\n"
                             + "@Weight(100.0)\n"
                             + "public class BasicEndpoint_ExtensibleGET_itWorks {\n"
                             + "    private final Provider<BasicEndpoint> target;\n"
                             + "\n"
                             + "    @Inject\n"
                             + "    BasicEndpoint_ExtensibleGET_itWorks(Provider<BasicEndpoint> target) {\n"
                             + "        this.target = target;\n"
                             + "    }\n"
                             + "\n"
                             + "    public Provider<BasicEndpoint> getBasicEndpoint() {\n"
                             + "        return target;\n"
                             + "    }\n"
                             + "\n"
                             + "}\n"
                             + "}",
                     res.getGeneratedJavaCode().toString());
    }

    @Test
    public void extensibleInterceptorForWorld() {
        CustomAnnotationProcessor processor = new CustomAnnotationProcessor();

        DefaultTemplateProducerRequest req = DefaultTemplateProducerRequest
                .builder(create(Singleton.class))
                .basicServiceInfo(DefaultServiceInfo.builder()
                                          .contractImplemented(World.class.getName())
                                          .build())
                .enclosingClassType(create(WorldImpl.class))
                .elementKind(ElementKind.CLASS)
                .elementName(WorldImpl.class.getName())
                .elementType(create(Class.class))
                .elementArgs(Collections.emptyList())
                .elementAccess(InjectionPointInfo.Access.PUBLIC)
                .build();
        assertTrue(req.isFilerEnabled());
        DefaultTemplateHelperTools tools = new DefaultTemplateHelperTools(ExtensibleGetTemplateProducer.class);

        Set<CustomAnnotationTemplateProducer> producers = processor.getProducersForType(req.getAnnoType());
        assertNotNull(producers);
        assertEquals(1, producers.size());

        CustomAnnotationTemplateProducerResponse res = processor.process(producers.iterator().next(), req, tools);
        assertNotNull(res);
        assertEquals(Singleton.class.getName(), res.getAnnoType().name());
        assertEquals("{io.helidon.pico.testsubjects.hello.WorldInterceptor=package io.helidon.pico.testsubjects"
                             + ".hello;\n"
                             + "\n"
                             + "import java.util.Objects;\n"
                             + "import java.util.Optional;\n"
                             + "\n"
                             + "import io.helidon.common.Weight;\n"
                             + "import io.helidon.pico.spi.ext.TypeInterceptor;\n"
                             + "\n"
                             + "import jakarta.annotation.Generated;\n"
                             + "import jakarta.inject.Inject;\n"
                             + "import jakarta.inject.Named;\n"
                             + "import jakarta.inject.Provider;\n"
                             + "import jakarta.inject.Singleton;\n"
                             + "\n"
                             + "@Generated({\"provider=oracle\", \"generator=io.helidon.pico.processor.spi"
                             + ".ExtensibleGetTemplateProducer\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "@Singleton\n"
                             + "@Weight(100.001)\n"
                             + "public class WorldInterceptor<T extends io.helidon.pico.testsubjects.hello.World> "
                             + "implements io.helidon.pico.testsubjects.hello.World, TypeInterceptor<T> {\n"
                             + "\n"
                             + "    private final Provider<T> delegate;\n"
                             + "    private final TypeInterceptor<T> interceptor;\n"
                             + "\n"
                             + "    @Inject\n"
                             + "    WorldInterceptor(Provider<T> delegate, @Named(\"io.helidon.pico.testsubjects"
                             + ".hello.WorldInterceptor\") Optional<TypeInterceptor<T>> interceptor) {\n"
                             + "        this.delegate = delegate;\n"
                             + "        this.interceptor = interceptor.isPresent() ? interceptor.get().interceptorFor"
                             + "(delegate) : null;\n"
                             + "    }\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public TypeInterceptor<T> interceptorFor(Provider<T> delegate) {\n"
                             + "        return interceptor;\n"
                             + "    }\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public Provider<T> providerFor(Provider<T> delegate, String methodName, Object..."
                             + " methodArgs) {\n"
                             + "        return delegate;\n"
                             + "    }\n"
                             + "\n"
                             + "    // --- begin intercepted methods of io.helidon.pico.testsubjects.hello.World ---\n"
                             + "    @Override\n"
                             + "    public java.lang.String getName()  {\n"
                             + "        if (Objects.isNull(interceptor)) {\n"
                             + "            return delegate.get().getName();\n"
                             + "        } else {\n"
                             + "            Provider<T> delegate = interceptor.providerFor(this.delegate, "
                             + "\"getName\");\n"
                             + "            interceptor.beforeCall(delegate, \"getName\");\n"
                             + "            Throwable t = null;\n"
                             + "            String result = null;\n"
                             + "            try {\n"
                             + "                result = delegate.get().getName();\n"
                             + "            } catch (Throwable t1) {\n"
                             + "                t = t1;\n"
                             + "            } finally {\n"
                             + "                if (Objects.isNull(t)) {\n"
                             + "                    interceptor.afterCall(delegate, result, \"getName\");\n"
                             + "                    return result;\n"
                             + "                } else {\n"
                             + "                    RuntimeException re = interceptor.afterFailedCall(t, delegate, "
                             + "\"getName\");\n"
                             + "                    \n"
                             + "                    throw re;\n"
                             + "                }\n"
                             + "            }\n"
                             + "        }\n"
                             + "    }\n"
                             + "\n"
                             + "\n"
                             + "    // --- end intercepted methods of io.helidon.pico.testsubjects.hello.World ---\n"
                             + "\n"
                             + "}\n"
                             + "}",
                     res.getGeneratedJavaCode().toString());
    }

    @Test
    public void extensibleInterceptorForExtendedHello() {
        CustomAnnotationProcessor processor = new CustomAnnotationProcessor();

        DefaultTemplateProducerRequest req = DefaultTemplateProducerRequest
                .builder(create(Singleton.class))
                .basicServiceInfo(DefaultServiceInfo.builder()
                                          .contractImplemented(ExtendedHello.class.getName())
                                          .build())
                .enclosingClassType(create(ExtendedHelloImpl.class))
                .elementKind(ElementKind.CLASS)
                .elementName(ExtendedHelloImpl.class.getName())
                .elementType(create(Class.class))
                .elementArgs(Collections.emptyList())
                .elementAccess(InjectionPointInfo.Access.PUBLIC)
                .build();
        assertTrue(req.isFilerEnabled());
        DefaultTemplateHelperTools tools = new DefaultTemplateHelperTools(ExtensibleGetTemplateProducer.class);

        Set<CustomAnnotationTemplateProducer> producers = processor.getProducersForType(req.getAnnoType());
        assertNotNull(producers);
        assertEquals(1, producers.size());

        CustomAnnotationTemplateProducerResponse res = processor.process(producers.iterator().next(), req, tools);
        assertNotNull(res);
        assertEquals(Singleton.class.getName(), res.getAnnoType().name());
        assertEquals("{io.helidon.pico.processor.testsubjects.ExtendedHelloInterceptor=package io.helidon.pico"
                             + ".processor.testsubjects;\n"
                             + "\n"
                             + "import java.util.Objects;\n"
                             + "import java.util.Optional;\n"
                             + "\n"
                             + "import io.helidon.common.Weight;\n"
                             + "import io.helidon.pico.spi.ext.TypeInterceptor;\n"
                             + "\n"
                             + "import jakarta.annotation.Generated;\n"
                             + "import jakarta.inject.Inject;\n"
                             + "import jakarta.inject.Named;\n"
                             + "import jakarta.inject.Provider;\n"
                             + "import jakarta.inject.Singleton;\n"
                             + "\n"
                             + "@Generated({\"provider=oracle\", \"generator=io.helidon.pico.processor.spi"
                             + ".ExtensibleGetTemplateProducer\", \"ver=1.0-SNAPSHOT\"})\n"
                             + "@Singleton\n"
                             + "@Weight(100.001)\n"
                             + "public class ExtendedHelloInterceptor<T extends io.helidon.pico.processor"
                             + ".testsubjects.ExtendedHello> implements io.helidon.pico.processor.testsubjects"
                             + ".ExtendedHello, TypeInterceptor<T> {\n"
                             + "\n"
                             + "    private final Provider<T> delegate;\n"
                             + "    private final TypeInterceptor<T> interceptor;\n"
                             + "\n"
                             + "    @Inject\n"
                             + "    ExtendedHelloInterceptor(Provider<T> delegate, @Named(\"io.helidon.pico.processor"
                             + ".testsubjects.ExtendedHelloInterceptor\") Optional<TypeInterceptor<T>> interceptor) {\n"
                             + "        this.delegate = delegate;\n"
                             + "        this.interceptor = interceptor.isPresent() ? interceptor.get().interceptorFor"
                             + "(delegate) : null;\n"
                             + "    }\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public TypeInterceptor<T> interceptorFor(Provider<T> delegate) {\n"
                             + "        return interceptor;\n"
                             + "    }\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public Provider<T> providerFor(Provider<T> delegate, String methodName, Object..."
                             + " methodArgs) {\n"
                             + "        return delegate;\n"
                             + "    }\n"
                             + "\n"
                             + "    // --- begin intercepted methods of io.helidon.pico.processor.testsubjects"
                             + ".ExtendedHello ---\n"
                             + "    @Override\n"
                             + "    public java.lang.String sayHello(java.lang.String p0, boolean p1) throws java.io"
                             + ".IOException, java.lang.RuntimeException {\n"
                             + "        if (Objects.isNull(interceptor)) {\n"
                             + "            return delegate.get().sayHello(p0, p1);\n"
                             + "        } else {\n"
                             + "            Provider<T> delegate = interceptor.providerFor(this.delegate, "
                             + "\"sayHello\");\n"
                             + "            interceptor.beforeCall(delegate, \"sayHello\", p0, p1);\n"
                             + "            Throwable t = null;\n"
                             + "            String result = null;\n"
                             + "            try {\n"
                             + "                result = delegate.get().sayHello(p0, p1);\n"
                             + "            } catch (Throwable t1) {\n"
                             + "                t = t1;\n"
                             + "            } finally {\n"
                             + "                if (Objects.isNull(t)) {\n"
                             + "                    interceptor.afterCall(delegate, result, \"sayHello\");\n"
                             + "                    return result;\n"
                             + "                } else {\n"
                             + "                    RuntimeException re = interceptor.afterFailedCall(t, delegate, "
                             + "\"sayHello\", p0, p1);\n"
                             + "                    if (t instanceof java.io.IOException) {\n"
                             + "                        throw (java.io.IOException) t;\n"
                             + "                    } if (t instanceof java.lang.RuntimeException) {\n"
                             + "                        throw (java.lang.RuntimeException) t;\n"
                             + "                    } \n"
                             + "                    throw re;\n"
                             + "                }\n"
                             + "            }\n"
                             + "        }\n"
                             + "    }\n"
                             + "\n"
                             + "    @Override\n"
                             + "    public void sayHello()  {\n"
                             + "        if (Objects.isNull(interceptor)) {\n"
                             + "            delegate.get().sayHello();\n"
                             + "        } else {\n"
                             + "            Provider<T> delegate = interceptor.providerFor(this.delegate, "
                             + "\"sayHello\");\n"
                             + "            interceptor.beforeCall(delegate, \"sayHello\");\n"
                             + "            Throwable t = null;\n"
                             + "            try {\n"
                             + "                delegate.get().sayHello();\n"
                             + "            } catch (Throwable t1) {\n"
                             + "                t = t1;\n"
                             + "            } finally {\n"
                             + "                if (Objects.isNull(t)) {\n"
                             + "                    interceptor.afterCall(delegate, result, \"sayHello\");\n"
                             + "                } else {\n"
                             + "                    RuntimeException re = interceptor.afterFailedCall(t, delegate, "
                             + "\"sayHello\");\n"
                             + "                    \n"
                             + "                    throw re;\n"
                             + "                }\n"
                             + "            }\n"
                             + "        }\n"
                             + "    }\n"
                             + "\n"
                             + "\n"
                             + "    // --- end intercepted methods of io.helidon.pico.processor.testsubjects"
                             + ".ExtendedHello ---\n"
                             + "\n"
                             + "}\n"
                             + "}",
                     res.getGeneratedJavaCode().toString());
    }

}
