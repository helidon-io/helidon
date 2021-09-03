Development Guidelines
------------

This document provides a list of rules and best practices followed by project Helidon.
Please follow these rules when contributing to the project, when refactoring existing code and when
reviewing changes done by others.

Some of these rules are enforced by checkstyle, some are checked during code reviews.

**Exceptions to these rules should be documented clearly.**

# General coding rules
1. Use unchecked Throwables - descendants of RuntimeException in API
    1. Never use RuntimeException directly - always create a descendant appropriate for your module, or
        use an existing exception declared in the module
    2. Our APIs should never throw a checked exception unless enforced by implemented/extended interface - e.g. when
        we implement a java.io.Closeable, we must declare the checked exception. 
1. Usage of `null` is discouraged and should not exist in any public APIs of Helidon
    1. If a method accepts a `null`, refactor it to a different approach
        - a setter: create a method to remove the field value rather than setting a `null` value 
            (such as `host(String)` to set a host, and `unsetHost()` to revert to default value)
        - other methods:
            - if there is a low number of combinations (up to 2), create another method without the parameter
            - otherwise create a parameter object that uses a builder to configure optional parameters
        - never use `java.util.Optional` as a parameter type
    2. If a method would return `null`, return `java.util.Optional` instead


# Package and module structure
1. We use flat package structure
    1. Each module (maven and jigsaw) has a single implementation package
        1. Each maven jar packaging module used outside of testing is also a jigsaw module 
    2. Module may have an additional package "spi" for classes related to service provider interface (extensibility)
    3. Unit testing is enabled through package local access (not public!)
    4. Be aware that any public class and its public methods are part of Helidon API and will require careful maintenance
    5. Do not rely on java module system (JPMS/Jigsaw) to enforce visibility
    6. If a set of classes seems to require a separate package, it is a good candidate for a separate module
        1. Example: _there could be a package for each "abac" module in "abac" security provider. Even though these modules 
            are mostly very small, these were extracted to standalone modules, not to break the rule of flat package 
            structure. In general this helps enforce the rule of separation of concerns - if you feel you need a new package, 
            in most cases you are putting together different concerns in a single module._
2. Naming conventions of maven modules, maven module directories and package names are connected:
    1. Directories: name of the directory is module name (referred to as ${module_name} further in this document)
        1. For pom packaging, the module is a "project module" (considering modules that serve as aggregators for sub-modules 
            into a common reactor)
    2. Maven coordinates:
        1. Group id: io.helidon.${project_module}* - such as io.helidon.webserver; io.helidon.microprofile.config
        2. Artifact id: helidon-${module_name}(-project)? - such as "helidon-security", "helicon-security-project", 
            where project modules use the suffix "-project"
        3. Version: always inherited
    3. Package names:
        1. io.helidon.${project_module}*.${module_name} - e.g. io.helidon.security, io.helidon.security.providers.common

# Configuration and programmatic API
1. **Everything that can be done using config, must be possible using programmatic approach through builders**
    1. Exceptions:
        1. CDI components configured from a CDI Extension
1. Everything that can be done using builders should be possible also using configuration, 
        except for cases that would mandate usage of reflection 
        (such an exception may be configuration of Routing in WebServer - nevertheless we still may support 
            it (emphasis on "may" rather than "should"), or configuration of security for Jersey resources)
2. When accepting config as a parameter, we should expect the config is located on the node that contains our configuration
    (such as in ServerConfiguration in WebServer)
3. Config keys:
    1. Use lower case words separated by dashes 
        (e.g. "token-endpoint-uri", NOT "tokenEndpointUri")
    2. May be nested in a tree structure (e.g. outbound-token.name, outbound-token.algorithm)
    3. The following properties may be used by a component:
        1. Required: component will fail to build when such a configuration property is missing
        2. Default: component has a well defined and documented default value for such a property
        3. Optional: component behaves in a well defined and documented manner if such a property is not 
            configured (e.g. a component may expect tracing endpoint - if not defined, tracing may be disabled)         
        

Example: [io.helidon.security.providers.oidc.common.OidcConfig](security/providers/oidc-common/src/main/java/io/helidon/security/providers/oidc/common/OidcConfig.java)

# Getters and Setters
1. We do not use the verb, e.g. when a property "port" exists, the following methods are used:  
    1. port(int newPort)
    2. int port()
2. Boolean depends on how well understood the method is
    1. Default is without a verb (e.g. authenticate(boolean atn), boolean authenticate())
    2. If this would be ambiguous, we can use verb to clear the meaning (e.g. isAuthenticated() or shouldAuthenticate())      

Example: [io.helidon.security.providers.oidc.common.OidcConfig](security/providers/oidc-common/src/main/java/io/helidon/security/providers/oidc/common/OidcConfig.java) 

# Fluent API
1. We use fluent API where applicable
    1. In builders (see builders section below)
    2. When using control methods (such as Server server = Server.create().start())
    
# Builders
1. We use builders to create instances that need any parameters for construction
    1. **This implies that there are no public API classes that would use public constructors**
    2. Allowed exceptions to this rule:
        1. Integration APIs that follow rules of integrated solution, e.g. Jersey SecurityFeature
        2. APIs that must be capable of reflection instantiation by tools that only support 
            public constructors
        3. Exceptions with constructors for string, and string and a throwable
2. Class or interface using a builder (let's call ours "FooBar" for the purpose of this document)
    1. Must have:   
        1. Hidden constructor (private or protected) - this is to allow us to switch to interface if needed
        2. method _public static Builder builder()_             
        3. all fields obtained from builder declared as final (immutable)
    2. May have:
        1. method _public static Builder builder(?)_ - builder with mandatory or very commonly used parameters
            - there should be a very small number of "builder(*)" methods - up to two per class
        1. Factory method _public static FooBar create()_ that is implemented as "return builder().build()"
        2. Factory method _public static create(io.helidon.config.Config config)_ that is implemented as 
            "return builder().config(config).build()"
        2. Other factory methods that build specific (predefined) instances, such as "fail(String cause)", 
            "success(Subject subject)" etc. - **these methods MUST use builder to build the instance internally**
        3. An internal class named "Builder" that is building instances of the containing class
            1. it is allowed to have the builder as a top level class, in such a case the name must reflect the class it is 
                building (e.g. FooBarBuilder) 
3. Builder class
    1. Must have:
        1. Implements "io.helidon.common.Builder<FooBar>"
        2. All methods configuring the builder return a builder instance with updated configuration
        3. Validation done either on setters or in method build() (latest) - e.g. we should fail to
            build an instance if the configuration is not correct
    2. May have:
        1. May accept other classes that are built using a builder, either directly, or as Supplier<T> 
            (as builder implements Supplier, this allows us to pass a builder to such a method, as well as a nice lambda)
            
Example: [io.helidon.security.providers.oidc.common.OidcConfig](security/providers/oidc-common/src/main/java/io/helidon/security/oidc/common/OidcConfig.java)

# JPMS
1. Each java module that is released has a `module-info.java`
2. Provided services of released modules are declared ONLY in `module-info.java`, `META-INF/services` is generated  
        automatically by a Maven plugin. `META-INF/services` in sources of released modules will fail the build
3. Javadoc is using modules, so do not forget to add javadoc to `module-info.java`
    
# Testing

We use JUnit 5 with Hamcrest assertions.

The Hamcrest assertion API differs a lot from JUnit assertion API and when both are used,
 the tests are hard to read.
 
We have chosen the Hamcrest approach:
- assertThat(actualValue, assertion(expectedValue))

Main import: `import static org.hamcrest.MatcherAssert.assertThat;` - the main method to be used

Most commonly used assertions are available in `org.hamcrest.CoreMatchers`:
1. is() - assertion of equality: `assertThat(value, is(true)))`
2. notNullValue() - assertion of a non-null value: `assertThat(value, notNullValue())`
3. nullValue() - assertion of null value: `assertThat(value, nullValue())`
4. startsWith(String) - assertion that string is prefixed: `assertThat(value, startWith("a"))`
5. endsWith(String) - assertion that string is suffixed: `assertThat(value, endsWith("b"))`

For other nice assertions see the CoreMatchers class or google ;)

The following assertions may be used from JUnit 5:
1. `import static org.junit.jupiter.api.Assertions.assertAll;` - doing multiple assertions at once (more than one may fail)
2. `import static org.junit.jupiter.api.Assertions.assertThrows;` - asserting that an expression throws an exception


**Example of why we have this rule:**

Original assertion:
```java
assertTrue(ex.getMessage().contains("'" + config.key() + "'"));
```
The output of the test was:
`Expected: true, actual: false`

Refactored assertion:
```java
assertThat(ex.getMessage(), containsString("'" + config.key() + "'"));
```

New output:
```
Expected: a string containing "'list-1'"
but: was "Requested value for configuration key 'list-1.1' is not present in the configuration."
```

# Maven
1. All third party versions are managed
    1. Plugins
    2. Dependencies (`dependencies/pom.xml`)
2. Adding a new third party dependency (or upgrading to a newer version) requires an 
    internal process to be carried out. In such cases a delay is to be expected when merging.
3. There is no need to use a version when referencing other modules, as all modules are managed in bom/pom.xml, and that is
    part of the maven tree
4. In pom.xml of a module, always define a "name" tag and follow naming conventions already used
    1. For project module: Helidon ${module_name} Project
    2. For java module: Helidon ${module_name}
    3. The name should be "reactor" friendly - it should not overflow
    4. The name is a name, not a sentence - it does not have to be grammatically correct 
5. All java modules that are expected to be used by our users MUST be defined in our [bom pom](bom/pom.xml)
6. Bundles may be created, though we still must give our users the freedom to pick and choose modules directly
    1. Avoid bundling third party dependencies that may bring unexpected libraries in (e.g. Google Login provider)
    2. $root/bundles - SE bundles (groupId: io.helidon.bundles)
    3. microprofile/bundles - MP bundles
    4. Bundles are for end users, not for internal use
7. Java EE components and Microprofile specifications should be in "provided" scope unless you are implementing
    the spec itself
    1. Analyze the dependencies of your module and choose the correct maven scope and module-info.java dependency declaration
    2. Mapping to module-info.java
        1. compile -> requires
        2. optional -> requires static
        3. provided -> requires
        4. runtime -> "requires" or "requires static" depending on requirements
                    note that "requires static" only works if the module is required by any other module used, otherwise
                    it does not end up on module path even if it is on the class path 
    3. Use transitive in module-info.java for your dependencies that are part of public API of the module
8. Carefully choose scope for dependencies on other helidon modules (e.g. microprofile extensions should have
    helidon microprofile in "provided" scope)