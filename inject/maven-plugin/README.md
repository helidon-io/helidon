Maven Plugin
---

A collection of maven plugins for Injection-based applications that provides several features including options to:

1. Validate the entirety of dependency graph across all modules. The <i>application-create</i> plugin would be applied in the same pom.xml
   that would otherwise assemble your application for deployment. This module would be expected to have compile-time references to
   each module that contributes to your application holistically

2. After the model has been validated, the <i>application-create</i> plugin will code-generate the service provider "Descriptors", "Modules", and "Application" into the
   final assembly that will be used at runtime to satisfy every injection point for the entire application without the need for reflection at runtime. This can be thought conceptually as a "linking phase" for your native application/image.

3. Creating Injection modules from external jars and packages using the <i>.

---

Q: Is this maven plugin required for your Injection-based application to work?

Answer 1: No, but it is recommended. Strictly speaking the main code generation occurs using the annotation processor, and the output from that processor is fully functional at runtime. However, without the use of this plugin your application's dependency model will not be validation nor will it be linked/bound/burned into your final application image.  It will still work fine and Injection will handle this case, but your application is not as optimal from a runtime performance perspective.

Answer 2: Yes, but only if you do not have access to the service implementation types, and are unable to apply the annotation processor on those types at compile time. Note, however, that Injection can still work without the maven processor in cases where you have possession to rebuild the service classes, but get the interfaces from an external module. In this later case, the <i>ExternalContracts</i> interfaces can be used on the service implementation classes. 

---

## Usage

The following are the maven Mojo's that are available to use. Each can be used for either the src/main or src/test. 

example usage:
```pom.xml
   <plugin>
       <groupId>io.helidon.inject</groupId>
       <artifactId>helidon-inject-maven-plugin</artifactId>
       <version>${helidon.version}</version>
       <executions>
           <execution>
               <goals>
                   <goal>external-module-create</goal>
               </goals>
           </execution>
           <execution>
               <id>compile</id>
               <phase>compile</phase>
               <goals>
                   <goal>application-create</goal>
               </goals>
           </execution>
       </executions>
       <configuration>
           <packageNames>
               <packageName>io.helidon.inject.examples.logger.common</packageName>
           </packageNames>
           <permittedProviderType>ALL</permittedProviderType>
       </configuration>
   </plugin>
```

### application-create
This goal is used to trigger the creation of the <i>Injection__Application</i> for your module (which typically is found in the final assembly jar module for your application). The usage of this also triggers the validation and integrity checks for your entire application's DI model, and will fail-fast at compilation time if any issue is detected (e.g., a non-Optional @Inject is found on a contract type having no concrete service implementations, etc.). Assuming there are no issues found during application creation - each of your service implementations will be listed inside the <i>injectionApplication</i>, and it will include the literal DI resolution plan for each of your services. This can also be very useful for visualization and debugging purposes besides being optimal from a runtime performance perspective.

Also note that Helidon Injection strives to ensure your application stays as deterministic as possible (as shown by the <i>Injection__Application/i> generated class). But when the <i>jakarta.inject.Provider</i> type is used within your application then some of that deterministic behavior goes away. This is due to how Provider<>'s work since the implementation for the Provider (i.e., your application logic) "owns" the behavior for what actual concrete type that are created by it, along with the scope/cardinality for those instances. These instances are then delivered (as potentially injectable services) into other dependent services.  In this way Helidon Injection is simply acting as a broker and delivery mechanism between your Provider<> implementation(s) and the consumer that are using those service instances as injection points, etc. This is not meant to scare or even dissuade you from using Provider<>, but merely to inform you that some of the deterministic behavior goes away under these circumstances using Provider instead of another Scope type like @Singleton. In many/most cases this is completely normal and acceptable. As a precaution, however, Helidon Injection chose to fail-fast at application creation time if your application is found to use jakarta.inject.Provider<T>. You will then need to provide a strategy/configuration in your pom.xml file to permit these types of usages. There are options to allow ALL providers (as shown in the above example), or the strategy can be dictated on a case-by-case basis. See the javadoc for [AbstractApplicationCreatorMojo](src/main/java/io/helidon/inject/maven/plugin/AbstractApplicationCreatorMojo.java) for details.

### external-module-create
This goal is used to trigger the creation of the supporting set of DI classes for an external jar/module - typically created without since it lacked having the Injection annotation processor during compilation. In this scenario, and to use this option then first be sure that the dependent module is a maven compile-time dependency in your module. After that then simply state the name of the package(s) to scan and produce the supporting DI classes (e.g., "io.helidon.inject.examples.logger.common" in the above example) in the pom.xml and then target/generated-sources/inject should be generated accordingly.

The example from above cover the basics for generation. There are one more advanced option that is available here that we'd like to cover. The below was taken from the [test-tck-jsr330 pom.xml](../tests/tck-jsr330/pom.xml):

```pom.xml
    <configuration>
        <compilerArgs>
            <arg>-Ahelidon.inject.autoAddNonContractInterfaces=true</arg>
        </compilerArgs>
        <packageNames>
            <packageName>org.atinject.tck.auto</packageName>
            <packageName>org.atinject.tck.auto.accessories</packageName>
        </packageNames>
        <supportsJsr330Strict>true</supportsJsr330Strict>
        <serviceTypeQualifiers>
            <serviceTypeQualifier>
                <serviceTypeName>org.atinject.tck.auto.accessories.SpareTire</serviceTypeName>
                <qualifiers>
                    <qualifier>
                        <qualifierTypeName>jakarta.inject.Named</qualifierTypeName>
                        <value>spare</value>
                    </qualifier>
                </qualifiers>
            </serviceTypeQualifier>
            <serviceTypeQualifier>
                <serviceTypeName>org.atinject.tck.auto.DriversSeat</serviceTypeName>
                <qualifiers>
                    <qualifier>
                        <qualifierTypeName>org.atinject.tck.auto.Drivers</qualifierTypeName>
                    </qualifier>
                </qualifiers>
            </serviceTypeQualifier>
        </serviceTypeQualifiers>
    </configuration>
```

Here we can see additional DI constructs, specifically two Qualifiers, are being augmented into the DI declaration model for the 3rd party jar. We can also see the option used to treat all service type interfaces as Contracts.

## TestApplication
When the maven plugin creates an application for <i>src/main/java</i> sources, a <i>Injection__Application</i> will be created for compile-time dependencies involved in the DI set of services. But when <i>src/test/java</i> sources are compiled, a <i>Injection__TestApplication</i> will be created for test-type dependencies involved in the test-side DI set of services of your application.

## Best Practices
Only one <i>Injection__Application</i> should typically be in your module classpath. And in production applications there should never be any test service types or a <i>Injection__TestApplication</i>, etc.
