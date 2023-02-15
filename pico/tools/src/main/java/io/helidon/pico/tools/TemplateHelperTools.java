/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;

/**
 * Tools to assist with {@link CustomAnnotationTemplateCreator}'s.
 */
public interface TemplateHelperTools {

    /**
     * Convenience method to help with the typical/generic case where the the request + the provided generatedType
     * is injected into the supplied template to produce the response.
     * <p>
     * The substitutions are as follows:
     * <ul>
     * <li> substitutions.put("generatedSticker", {generated-sticker});
     * <li> substitutions.put("generatedTypeName", req.getGeneratedTypeName().getName());
     * <li> substitutions.put("annoTypeName", TypeNameImpl.toName(req.getAnnoType()));
     * <li> substitutions.put("packageName", req.getGeneratedTypeName().getPackageName());
     * <li> substitutions.put("className", req.getGeneratedTypeName().getClassName());
     * <li> substitutions.put("enclosingClassTypeName", req.getEnclosingClassType().getName());
     * <li> substitutions.put("enclosingClassAnnotations", req.getEnclosingClassAnnotations());
     * <li> substitutions.put("basicServiceInfo", req.getBasicServiceInfo());
     * <li> substitutions.put("weight", DefaultServiceInfo.weightOf(req.getBasicServiceInfo());
     * <li> substitutions.put("enclosingClassTypeName.packageName", req.getEnclosingClassType().getPackageName());
     * <li> substitutions.put("enclosingClassTypeName.className", req.getEnclosingClassType().getClassName());
     * <li> substitutions.put("elementKind", req.getElementKind());
     * <li> substitutions.put("elementName", req.getElementName());
     * <li> substitutions.put("elementAccess", req.getElementAccess());
     * <li> substitutions.put("elementIsStatic", req.isElementStatic());
     * <li> substitutions.put("elementEnclosingTypeName", req.getElementEnclosingType().getName());
     * <li> substitutions.put("elementEnclosingTypeName.packageName", req.getElementEnclosingType().getPackageName());
     * <li> substitutions.put("elementEnclosingTypeName.className", req.getElementEnclosingType().getClassName());
     * <li> substitutions.put("elementArgs", req.getElementArgs());
     * <li> substitutions.put("elementArgs-declaration", req.getElementArgs());
     * </ul>
     *
     * @param request           the request, as populated as indicated above
     * @param generatedType     the type that should be code generated
     * @param codeGenTemplateSupplier  the resolver strategy to apply - example {@link #supplyUsingLiteralTemplate(CharSequence)}
     * @param propertiesFn      optionally, this function the planned properties will be passed providing an override mechanism
     *
     * @return the response, or empty if the template was not resolvable
     */
    Optional<CustomAnnotationTemplateResponse> produceStandardCodeGenResponse(
            CustomAnnotationTemplateRequest request,
            TypeName generatedType,
            Supplier<? extends CharSequence> codeGenTemplateSupplier,
            Function<Map<String, Object>, Map<String, Object>> propertiesFn);

//    /**
//     * Convenience method for generating a simple before-and-after interceptor/decorator.
//     * <p/>
//     * Note that the implementation of this method uses reflection to determine the methods that are generated.
//     * <p/>
//     * For an interface of :
//     * <pre>
//     *     public interface World {
//     *         String getName();
//     *     }
//     * </pre>
//     * If the {@link CustomAnnotationTemplateCreator} responds as follows:
//     * <pre>
//     *     public class ExtensibleInterceptorProducer implements CustomAnnotationTemplateProducer {
//     *
//     *     static final Class<? extends Annotation> annoType = Singleton.class;
//     *     static final Class<World> interceptedClass1 = World.class;
//     *
//     *     @Override
//     *     public Set<Class<? extends Annotation>> getAnnoTypes() {
//     *         return Collections.singleton(annoType);
//     *     }
//     *
//     *     @Override
//     *     public CustomAnnotationTemplateProducerResponse produce(CustomAnnotationTemplateProducerRequest request,
//     *                                                             TemplateHelperTools tools) {
//     *         TypeName generatedType = TypeNameImpl
//     *                 .toName(request.getEnclosingClassType().getPackageName(), interceptedClass1.getSimpleName() + "Interceptor");
//     *         return tools.produceNamedBasicInterceptorDelegationResponse(request, generatedType, interceptedClass1, null, null);
//     *     }
//     * </pre>
//     * Then the following code will be produced:
//     * <pre>
//     * @Singleton
//     * @Weight(100.001)
//     * public class WorldInterceptor<T extends World> implements World, TypeInterceptor<T> {
//     *
//     *     private final Provider<T> delegate;
//     *     private final TypeInterceptor<T> interceptor;
//     *
//     *     @Inject
//     *     ExpectedWorldInterceptor(Provider < T > delegate, @ Named ( " World ") Optional<TypeInterceptor<T>> interceptor) {
//     *         this.delegate = delegate;
//     *         this.interceptor = interceptor.isPresent() ? interceptor.get().interceptorFor(delegate) : null;
//     *     }
//     *
//     *     @Override
//     *     public TypeInterceptor<T> interceptorFor(Provider<T> delegate) {
//     *         return interceptor;
//     *     }
//     *
//     *     @Override
//     *     public Provider<T> providerFor(Provider<T> delegate, String methodName, Object... methodArgs) {
//     *         return delegate;
//     *     }
//     *
//     *     // --- begin intercepted methods of world interface
//     *
//     *     @Override
//     *     public String getName() {
//     *         if (Objects.isNull(interceptor)) {
//     *             return delegate.get().getName();
//     *         } else {
//     *             Provider<T> delegate = interceptor.providerFor(this.delegate, "getName");
//     *             interceptor.beforeCall(delegate, "getName");
//     *             Throwable t = null;
//     *             String result = null;
//     *             try {
//     *                 result = delegate.get().getName();
//     *             } catch (Throwable t1) {
//     *                 t = t1;
//     *             } finally {
//     *                 if (Objects.isNull(t)) {
//     *                     interceptor.afterCall(delegate, result, "getName");
//     *                     return result;
//     *                 } else {
//     *                     RuntimeException re = interceptor.afterFailedCall(t, delegate, "getName");
//     *                     throw re;
//     *                 }
//     *             }
//     *         }
//     *     }
//     *
//     *     // --- end intercepted methods of world interface
//     * }
//     * </pre>
//     *
//     * @param request           the request
//     * @param generatedType     the type that should be code generated
//     * @param contractIntercepted the contract to be intercepted
//     * @param propertiesFn      optionally, given this function the planned properties will be passed providing an override mechanism
//     * @param errOut            optionally, the print stream to log to for any errors, or null for quiet
//     *
//     * @return the response, or null if the type cannot be intercepted
//     *
//     * @see io.helidon.pico.TypeInterceptor
//     */
//    CustomAnnotationTemplateResponse produceNamedBasicInterceptorDelegationCodeGenResponse(
//            CustomAnnotationTemplateRequest request,
//            TypeName generatedType,
//            Class<?> contractIntercepted,
//            Function<Map<String, Object>, Map<String, Object>> propertiesFn,
//            PrintStream errOut);

    /**
     * Returns the default resolver that will rely on a resource lookup of resources/pico/{templateProfile}/{templateName}.
     * Note: This will only work for non-module based usages, and therefore is not recommended for general use.
     *
     * @param templateProfile   the template profile to apply (must be exported by the spi provider module; "default" is reserved for internal use)
     * @param templateName      the template name
     *
     * @return the generic resource based resolver
     */
    Supplier<CharSequence> supplyFromResources(
            String templateProfile,
            String templateName);

    /**
     * Returns a literal resolver to the static template provided.
     *
     * @param template the resolved template
     *
     * @return the resolver that directly returns the template
     */
    Supplier<CharSequence> supplyUsingLiteralTemplate(
            CharSequence template);

}
