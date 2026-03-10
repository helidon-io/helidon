/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.codegen.classmodel;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import io.helidon.common.types.AnnotationProperty;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import org.hamcrest.Matcher;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ClassModelTest {

    @Test
    void testInnerClass() throws IOException {
        StringWriter sw = new StringWriter();

        ClassModel.builder()
                .packageName("com.acme")
                .name("WithInner")
                .addInnerClass(InnerClass.builder()
                        .name("Inner")
                        .isStatic(true)
                        .build())
                .addMethod(Method.builder()
                        .name("test")
                        .returnType(TypeName.create("com.acme.WithInner.Inner"))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
                package com.acme;
                
                public class WithInner {
                
                    public WithInner.Inner test() {
                    }
                
                    public static class Inner {
                
                    }
                
                }
                """));
    }

    @Test
    void testAtSignInText() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("Zavinac")
                .addMethod(Method.builder()
                        .name("create")
                        .addContent("service(")
                        .addContent(TypeNames.LIST)
                        .addContent(".of(\"@default\")")
                        .addContent(", ")
                        .addContent(TypeNames.OPTIONAL)
                        .addContent(".class")
                        .addContent(");")
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
            package com.acme;
            
            import java.util.List;
            import java.util.Optional;
            
            public class Zavinac {
            
                public void create() {
                    service(List.of("@default"), Optional.class);
                }
            
            }
            """));
    }

    @Test
    void testVarargExplicit() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("Vararg")
                .addMethod(Method.builder()
                        .name("create")
                        .addParameter(p -> p.name("name")
                                .type(String.class)
                                .vararg(true))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
            package com.acme;
            
            public class Vararg {
            
                public void create(String... name) {
                }
            
            }
            """));
    }

    @Test
    void testVarargTypeName() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("Vararg")
                .addMethod(Method.builder()
                        .name("create")
                        .addParameter(p -> p.name("name")
                                .type(TypeName.builder()
                                        .type(String[].class)
                                        .vararg(true)
                                        .build()))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
            package com.acme;
            
            public class Vararg {
            
                public void create(String... name) {
                }
            
            }
            """));
    }

    @Test
    void testCommonTypesAnnotation() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addAnnotation(io.helidon.common.types.Annotation.builder()
                        .typeName(TypeName.create("com.acme.Colors"))
                        .putProperty("category", AnnotationProperty.create("all"))
                        .putProperty("value", AnnotationProperty.create(List.of("red", "blue", "green")))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
              package com.acme;
              
              @Colors(category = "all", value = {"red", "blue", "green"})
              public class MyClass {
              
              }
              """));
    }

    @Test
    void testTypeAnnotation() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addAnnotation(Annotation.builder()
                        .type(TypeName.create("com.acme.Colors"))
                        .addParameter(annotationParameter("value", List.of("red", "blue", "green")))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
              package com.acme;
              
              @Colors({"red", "blue", "green"})
              public class MyClass {
              
              }
              """));
    }

    @Test
    void testValueListOneElement() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addAnnotation(Annotation.builder()
                        .type(TypeName.create("com.acme.Colors"))
                        .addParameter(annotationParameter("value", List.of("red")))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
              package com.acme;
              
              @Colors("red")
              public class MyClass {
              
              }
              """));
    }

    @Test
    void testNamedPropListOneElement() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addAnnotation(Annotation.builder()
                        .type(TypeName.create("com.acme.Colors"))
                        .addParameter(annotationParameter("colors", List.of("red")))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
              package com.acme;
              
              @Colors(colors = "red")
              public class MyClass {
              
              }
              """));
    }

    @Test
    void testNamedPropListOneComplexElement() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addAnnotation(Annotation.builder()
                        .type(TypeName.create("com.acme.Colors"))
                        .addParameter(annotationParameter("colors", List.of(
                                Annotation.builder()
                                        .type(TypeName.create("com.acme.Name"))
                                        .addParameter(annotationParameter("value", "red"))
                                        .build())))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
              package com.acme;
              
              @Colors(colors = {@Name("red")})
              public class MyClass {
              
              }
              """));
    }

    @Test
    void testMethodAnnotation() throws IOException {
        var m = Method.builder()
                .name("method1")
                .addAnnotation(Annotation.builder()
                        .type(TypeName.create("com.acme.Colors"))
                        .addParameter(annotationParameter("value", List.of("red", "blue", "green")))
                        .build())
                .build();

        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addMethod(m)
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
            package com.acme;
            
            public class MyClass {
            
                @Colors({"red", "blue", "green"})
                public void method1() {
                }
            
            }
            """));
    }

    @Test
    void testMultilineTypeAnnotation() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addAnnotation(Annotation.builder()
                        .type(TypeName.create("com.acme.Colors"))
                        .addParameter(annotationParameter("value", List.of("beige", "blue", "brown", "burgundy", "coral",
                                "cyan", "green", "grey", "lavender", "mauve", "orange", "pink", "purple", "red", "yellow")))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
              package com.acme;
              
              @Colors({
                      "beige",
                      "blue",
                      "brown",
                      "burgundy",
                      "coral",
                      "cyan",
                      "green",
                      "grey",
                      "lavender",
                      "mauve",
                      "orange",
                      "pink",
                      "purple",
                      "red",
                      "yellow"
                  })
              public class MyClass {
              
              }
              """));
    }

    @Test
    void testMultilineMethodAnnotation() throws IOException {
        var m = Method.builder()
                .name("method1")
                .addAnnotation(Annotation.builder()
                        .type(TypeName.create("com.acme.Colors"))
                        .addParameter(annotationParameter("value", List.of("beige", "blue", "brown", "burgundy", "coral",
                                "cyan", "green", "grey", "lavender", "mauve", "orange", "pink", "purple", "red")))
                        .build())
                .build();

        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addMethod(m)
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
            package com.acme;
            
            public class MyClass {
            
                @Colors({
                        "beige",
                        "blue",
                        "brown",
                        "burgundy",
                        "coral",
                        "cyan",
                        "green",
                        "grey",
                        "lavender",
                        "mauve",
                        "orange",
                        "pink",
                        "purple",
                        "red"
                    })
                public void method1() {
                }
            
            }
            """));
    }

    @Test
    void testSingleLineNestedTypeAnnotations() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addAnnotation(Annotation.builder()
                        .type(TypeName.create("com.acme.Users"))
                        .addParameter(annotationParameter("users", List.of(
                                Annotation.builder()
                                        .type(TypeName.create("com.acme.User"))
                                        .addParameter(annotationParameter("uid", "user1"))
                                        .addParameter(annotationParameter("name", "User1"))
                                        .build(),
                                Annotation.builder()
                                        .type(TypeName.create("com.acme.User"))
                                        .addParameter(annotationParameter("uid", "user2"))
                                        .addParameter(annotationParameter("name", "User2"))
                                        .build())))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
            package com.acme;
            
            @Users(users = {@User(uid = "user1", name = "User1"), @User(uid = "user2", name = "User2")})
            public class MyClass {
            
            }
            """));
    }

    @Test
    void testNestedTypeAnnotations() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addAnnotation(Annotation.builder()
                        .type(TypeName.create("com.acme.Users"))
                        .addParameter(annotationParameter("users", List.of(
                                        Annotation.builder()
                                                .type(TypeName.create("com.acme.User"))
                                                .addParameter(annotationParameter("uid", "user1"))
                                                .addParameter(annotationParameter("name", "User1"))
                                                .addParameter(annotationParameter("roles", List.of("role1", "role2")))
                                                .build(),
                                        Annotation.builder()
                                                .type(TypeName.create("com.acme.User"))
                                                .addParameter(annotationParameter("uid", "user2"))
                                                .addParameter(annotationParameter("name", "User2"))
                                                .addParameter(annotationParameter("roles", List.of("role1", "role2")))
                                                .build())))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
            package com.acme;
            
            @Users(
                users = {
                    @User(uid = "user1", name = "User1", roles = {"role1", "role2"}),
                    @User(uid = "user2", name = "User2", roles = {"role1", "role2"})
                })
            public class MyClass {
            
            }
            """));
    }

    @Test
    void testNestedConstantValues() throws IOException {
        var sw = new StringWriter();
        var constantTypeName = TypeName.create("com.example.Uids");
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addAnnotation(Annotation.builder()
                        .type(TypeName.create("com.acme.Users"))
                        .addParameter(annotationParameter("users", List.of(
                                        Annotation.builder()
                                                .type(TypeName.create("com.acme.User"))
                                                .addParameter(annotationParameter("uid",
                                                        AnnotationProperty.create("user1", constantTypeName, "USER1")))
                                                .addParameter(annotationParameter("name", "User1"))
                                                .addParameter(annotationParameter("roles", List.of("role1", "role2")))
                                                .build(),
                                        Annotation.builder()
                                                .type(TypeName.create("com.acme.User"))
                                                .addParameter(annotationParameter("uid", "user2"))
                                                .addParameter(annotationParameter("name", "User2"))
                                                .addParameter(annotationParameter("roles", List.of("role1", "role2")))
                                                .build())))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
            package com.acme;
            
            import com.example.Uids;
            
            @Users(
                users = {
                    @User(uid = Uids.USER1, name = "User1", roles = {"role1", "role2"}),
                    @User(uid = "user2", name = "User2", roles = {"role1", "role2"})
                })
            public class MyClass {
            
            }
            """));
    }

    @Test
    void testNestedTypeAnnotationsListOneElement() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addAnnotation(Annotation.builder()
                        .type(TypeName.create("com.acme.Users"))
                        .addParameter(annotationParameter("users", List.of(
                                Annotation.builder()
                                        .type(TypeName.create("com.acme.User"))
                                        .addParameter(annotationParameter("uid", "user1"))
                                        .addParameter(annotationParameter("name", "User1"))
                                        .addParameter(annotationParameter("roles", List.of("longrole1", "longrole2", "longrole3",
                                                "longrole4", "longrole5", "longrole6", "longrole7", "longrole8", "longrole9",
                                                "longrole10")))
                                        .build())))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
            package com.acme;
            
            @Users(
                users = {
                    @User(
                        uid = "user1",
                        name = "User1",
                        roles = {
                            "longrole1",
                            "longrole2",
                            "longrole3",
                            "longrole4",
                            "longrole5",
                            "longrole6",
                            "longrole7",
                            "longrole8",
                            "longrole9",
                            "longrole10"
                        })
                })
            public class MyClass {
            
            }
            """));
    }

    @Test
    void testMultilineNestedTypeAnnotations() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addAnnotation(Annotation.builder()
                        .type(TypeName.create("com.acme.Users"))
                        .addParameter(annotationParameter("users", List.of(
                                Annotation.builder()
                                        .type(TypeName.create("com.acme.User"))
                                        .addParameter(annotationParameter("uid", "user1"))
                                        .addParameter(annotationParameter("name", "User1"))
                                        .addParameter(annotationParameter("roles",
                                                List.of("longrole1", "longrole2", "longrole3", "longrole4", "longrole5",
                                                        "longrole6", "longrole7", "longrole8", "longrole9", "longrole10")))
                                        .build(),
                                Annotation.builder()
                                        .type(TypeName.create("com.acme.User"))
                                        .addParameter(annotationParameter("uid", "user2"))
                                        .addParameter(annotationParameter("name", "User2"))
                                        .addParameter(annotationParameter("roles",
                                                List.of("longrole1", "longrole2", "longrole3", "longrole4", "longrole5",
                                                        "longrole6", "longrole7", "longrole8", "longrole9", "longrole10")))
                                        .build())))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
            package com.acme;
            
            @Users(
                users = {
                    @User(
                        uid = "user1",
                        name = "User1",
                        roles = {
                            "longrole1",
                            "longrole2",
                            "longrole3",
                            "longrole4",
                            "longrole5",
                            "longrole6",
                            "longrole7",
                            "longrole8",
                            "longrole9",
                            "longrole10"
                        }),
                    @User(
                        uid = "user2",
                        name = "User2",
                        roles = {
                            "longrole1",
                            "longrole2",
                            "longrole3",
                            "longrole4",
                            "longrole5",
                            "longrole6",
                            "longrole7",
                            "longrole8",
                            "longrole9",
                            "longrole10"
                        })
                })
            public class MyClass {
            
            }
            """));
    }


    @Test
    void testAnnotationNumberValues() throws IOException {
        var sw = new StringWriter();
        ClassModel.builder()
                .packageName("com.acme")
                .name("MyClass")
                .addAnnotation(io.helidon.codegen.classmodel.Annotation.builder()
                        .type(TypeName.create("com.acme.Numbers"))
                        .addParameter(annotationParameter("longValue", 1L))
                        .addParameter(annotationParameter("floatValue", 1.0F))
                        .addParameter(annotationParameter("doubleValue", 1.0D))
                        .addParameter(annotationParameter("byteValue", (byte) 1))
                        .addParameter(annotationParameter("shortValue", (short) 1))
                        .addParameter(annotationParameter("intValue", 1))
                        .build())
                .build()
                .write(sw);

        assertThat(sw.toString(), isSource("""
              package com.acme;
              
              @Numbers(
                  longValue = 1L,
                  floatValue = 1.0F,
                  doubleValue = 1.0D,
                  byteValue = (byte) 1,
                  shortValue = (short) 1,
                  intValue = 1)
              public class MyClass {
              
              }
              """));
    }

    private static AnnotationParameter annotationParameter(String name, Object value) {
        return AnnotationParameter.builder()
                .name(name)
                .value(value)
                .build();
    }

    private static Matcher<String> isSource(@Language("java") String code) {
        return is(code);
    }
}
