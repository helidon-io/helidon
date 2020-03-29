/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.config.objectmapping;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Supplier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation used to customize behaviour of JavaBean deserialization support.
 * <p>
 * The first option for generic Config to JavaBean deserialization works just with class with no-parameter constructor.
 * Each JavaBean property value is then set by value mapped from appropriate configuration node.
 * Each public setter method and public non-final fields are taken as JavaBean properties that will be set.
 * The deserialization process is applied recursively on each property.
 * <p>
 * Use {@link Transient} annotation to exclude setter or field from set of processed JavaBean properties.
 * <p>
 * By default JavaBean property name is used as config key to {@link io.helidon.config.Config#get(String) get} configuration node.
 * The config key can be customized by {@link #key()} attribute. Use the annotation on public setter or public field.
 * Annotation on method has precedence over annotation used on field. The second one is ignored.
 * <p>
 * If the appropriate configuration node does not exist it is possible to specify default value:
 * <ul>
 * <li>{@link #withDefaultSupplier()} - instance of supplier class is used to get default value of target type; or</li>
 * <li>{@link #withDefault()} - default value in {@code String} form that will be mapped to target type
 * by associated config mapping function</li>
 * </ul>
 * In case of both <i>default</i> attributes are set the {@code withDefaultSupplier} is used
 * and {@code withDefault} is ignored.
 * <pre><code>
 * public class AppConfig {
 *     private String greeting;
 *     private int pageSize;
 *     private List{@literal <Integer>} range;
 *
 *     public AppConfig() { // {@literal <1>}
 *     }
 *
 *     public void setGreeting(String greeting) { // {@literal <2>}
 *         this.greeting = greeting;
 *     }
 *
 *     {@literal @}Value(key = "page-size", withDefault = "10") // {@literal <3>}
 *     public void setPageSize(int pageSize) {
 *         this.pageSize = pageSize;
 *     }
 *
 *     {@literal @}Value(withDefaultSupplier = DefaultRangeSupplier.class) // {@literal <4>}
 *     public void setRange(List{@literal <Integer>} basicRange) {
 *         this.range = basicRange;
 *     }
 *
 *     //...
 *
 *     public static class DefaultRangeSupplier // {@literal <5>}
 *                 implements Supplier{@literal <List<Integer>>} {
 *         {@literal @}Override
 *         public List{@literal <Integer>} get() {
 *             return List.of(0, 10);
 *         }
 *     }
 * }
 * </code></pre>
 * <ol>
 * <li>public no-parameter constructor;</li>
 * <li>property {@code greeting} is not customized; will be set from config node with {@code greeting} key, if exists;</li>
 * <li>property {@code pageSize} customizes key of config node to {@code page-size};
 * if the config node does not exist, value {@code "10"} will be mapped to {@code int};
 * </li>
 * <li>property {@code range} will be set from config node with same {@code range} key;
 * if the config node does not exist, {@code DefaultRangeSupplier} instance will be used to get default value;
 * </li>
 * <li>{@code DefaultRangeSupplier} is used to supply {@code List<Integer>} value.</li>
 * </ol>
 * <p>
 * The second option is to provide factory public static method {@code from} with parameters set from configuration.
 * Or public "factory" constructor with parameters can be used too.
 * <pre><code>
 * public class AppConfig {
 *     private final String greeting;
 *     private final int pageSize;
 *     private final List{@literal <Integer>} basicRange;
 *
 *     private AppConfig(String greeting, int pageSize, List{@literal <Integer>} basicRange) {
 *         this.greeting = greeting;
 *         this.pageSize = pageSize;
 *         this.basicRange = basicRange;
 *     }
 *
 *     //...
 *
 *     // FACTORY METHOD
 *     public static AppConfig create({@literal @}Value(key = "greeting", withDefault = "Hi")
 *                                  String greeting,
 *                                  {@literal @}Value(key = "page-size", withDefault = "10")
 *                                  int pageSize,
 *                                  {@literal @}Value(key = "basic-range",
 *                                          withDefaultSupplier = DefaultBasicRangeSupplier.class)
 *                                  List{@literal <Integer>} basicRange) {
 *         return new AppConfig(greeting, pageSize, basicRange);
 *     }
 * }
 * </code></pre>
 * <p>
 * The third option is to provide Builder accessible by public static {@code builder()} method.
 * The Builder instances is initialized via public setters or fields, similar to the first deserialization option.
 * Finally, Builder has {@code build()} method that creates new instances of a bean.
 * <pre><code>
 * public class AppConfig {
 *     private final String greeting;
 *     private final int pageSize;
 *     private final List{@literal <Integer>} basicRange;
 *
 *     private AppConfig(String greeting, int pageSize, List{@literal <Integer>} basicRange) {
 *         this.greeting = greeting;
 *         this.pageSize = pageSize;
 *         this.basicRange = basicRange;
 *     }
 *
 *     // BUILDER METHOD
 *     public static Builder builder() {
 *         return new Builder();
 *     }
 *
 *     public static class Builder {
 *         private String greeting;
 *         private int pageSize;
 *         private List{@literal <Integer>} basicRange;
 *
 *         private Builder() {
 *         }
 *
 *         {@literal @}Value(withDefault = "Hi")
 *         public void setGreeting(String greeting) {
 *             this.greeting = greeting;
 *         }
 *
 *         {@literal @}Value(key = "page-size", withDefault = "10")
 *         public void setPageSize(int pageSize) {
 *             this.pageSize = pageSize;
 *         }
 *
 *         {@literal @}Value(key = "basic-range",
 *                 withDefaultSupplier = DefaultBasicRangeSupplier.class)
 *         public void setBasicRange(List{@literal <Integer>} basicRange) {
 *             this.basicRange = basicRange;
 *         }
 *
 *         // BUILD METHOD
 *         public AppConfig build() {
 *             return new AppConfig(greeting, pageSize, basicRange);
 *         }
 *     }
 * }
 * </code></pre>
 * <p>
 * Configuration example:
 * <pre>{@code
 * {
 *     "app": {
 *         "greeting": "Hello",
 *         "page-size": 20,
 *         "range": [ -20, 20 ]
 *     }
 * }
 * }</pre>
 * Getting {@code app} config node as {@code AppConfig} instance:
 * <pre>{@code
 * AppConfig appConfig = config.get("app").as(AppConfig.class);
 * assert appConfig.getGreeting().equals("Hello");
 * assert appConfig.getPageSize() == 20;
 * assert appConfig.getRange().get(0) == -20;
 * assert appConfig.getRange().get(1) == 20;
 * }</pre>
 * In this case default values where not used because JSON contains all expected nodes.
 * <p>
 * The annotation cannot be applied on same JavaBean property together with {@link Transient}.
 *
 * @see Transient
 */
@Retention(RUNTIME)
@Target({METHOD, FIELD, PARAMETER})
public @interface Value {

    /**
     * Specifies a key of configuration node to be used to set JavaBean property value from.
     * <p>
     * If not specified original JavaBean property name is used.
     *
     * @return config property key
     */
    String key() default "";

    /**
     * Specifies default value in form of single String value
     * that will be used to set JavaBean property value in case configuration does not contain a config node
     * of appropriate config key.
     * <p>
     * In case {@link #withDefaultSupplier} is also used current value is ignored.
     *
     * @return single default value that will be converted into target type
     */
    String withDefault() default None.VALUE;

    /**
     * Specifies supplier of default value
     * that will be used to set JavaBean property value in case configuration does not contain config node
     * of appropriate config key.
     * <p>
     * Default value is used in case appropriate config value is not set.
     * In case {@link #withDefault} is also used this one has higher priority and will be used.
     *
     * @return supplier that will provide default value in target type
     */
    Class<? extends Supplier<?>> withDefaultSupplier() default None.class;

    /**
     * Class that represents not-set default values.
     */
    interface None extends Supplier<Void> {
        String VALUE = "io.helidon.config:default=null";

        @Override
        default Void get() {
            return null;
        }
    }

}
