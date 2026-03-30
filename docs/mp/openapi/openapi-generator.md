# OpenAPI-based Code Generation

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Configuration](#configuration)
- [Usage](#usage)
- [References](#references)

## Overview

The [OpenAPI specification](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md) provides a standard way to express RESTful APIs.

Separately, the [OpenAPI generator](https://openapi-generator.tech) project has created a powerful code generator tool which accepts an OpenAPI document and generates client and server code for many languages and frameworks. The Helidon team contributes to this tool to ensure that it provides strong support for Helidon MP clients and servers. As a result, you can use the generator to create code that fits smoothly into your Helidon applications.

Use the OpenAPI generator release 7.6.0 or later which this document describes.

In the vocabulary of the tool, there are two *generators* for Helidon:

- `java-helidon-client` (hereafter the Helidon client generator)
- `java-helidon-server` (hereafter the Helidon server generator).

Each of these generators supports two *libraries*:

- `mp` - for Helidon MP code generation
- `se` - for Helidon SE code generation

Use the Helidon *client* generator and its `mp` library to create a [Helidon MicroProfile REST client](../../mp/restclient/restclient.md). The resulting client library works with any server that implements the API declared in the OpenAPI document you specified when you ran the generator. The client library provides an abstraction similar to remote procedure calls (RPC). To access a remote service that implements the endpoints declared in the OpenAPI document, your code uses the generated client library first to establish a connection to the remote service and then to call remote service endpoints by invoking local methods passing POJO business objects or Java types as arguments.

Use the tool’s Helidon *server* generator and its `mp` library to create server endpoint stubs for a Helidon MP service. You build on these stubs by extending a generated class or implementing a generated interface, adding your specific business logic to finish the implementation of the endpoints. The combination of the generated server code plus Helidon MP underneath it allows you to focus on the business details instead of resource boilerplate.

You can run the OpenAPI generators in three ways:

- using the OpenAPI generator CLI
- using the OpenAPI generator Maven plug-in
- using the online OpenAPI generator website

The rest of this document walks you through [how to use](#usage) each technique and how to [configure](#configuration) the generators to produce the code you want.

## Maven Coordinates

Your project does not need any dependencies on the OpenAPI generator.

To use the OpenAPI generator plug-in to generate or regenerate files during your project build, add the following to your project’s `pom.xml` file to declare the plug-in. Choose whichever version of the generator plug-in meets your needs as long as it is at least 7.6.0.

*Declaring the OpenAPI Generator Plug-in*

``` xml
<properties>
    <openapi-generator-version>7.6.0</openapi-generator-version>
</properties>
...
<build>
    ...
    <plugin-management>
        ...
        <plugin>
             <groupId>org.openapitools</groupId>
             <artifactId>openapi-generator-maven-plugin</artifactId>
             <version>${openapi-generator-version}</version>
        </plugin>
        ...
    </plugin-management>
    ...
</build>
```

A [later section](#invoking-the-openapi-generator-maven-plug-in) describes how to invoke the plug-in during your build.

## Configuration

The OpenAPI generators support a substantial, powerful, and sometimes bewildering group of configuration settings. <a id="links-to-settings"></a>
For complete lists see these pages:

- [generic options](https://github.com/OpenAPITools/openapi-generator/blob/v7.6.0/docs/usage.md#generate)
- [Helidon client generator options](https://github.com/OpenAPITools/openapi-generator/blob/v7.6.0/docs/generators/java-helidon-client.md) and
- [Helidon server generator options](https://github.com/OpenAPITools/openapi-generator/blob/v7.6.0/docs/generators/java-helidon-server.md)

The OpenAPI generator loosely divides its settings into three types:

- *global properties*

  These settings generally govern the overall behavior of the tool, regardless of which specific generator you use.

  For the CLI, use the common option style:

  `-i petstore.yaml`

  `--input-spec petstore.yaml`

  For the Maven plug-in, use elements within the `<configuration>` section of the plug-in:

  ``` xml
  <configuration>
      <inputSpec>petstore.yaml</inputSpec>
  </configuration>
  ```

- *options*

  These settings typically affect how particular generators operate.

  For the CLI, specify config options as additional properties:

  `--additional-properties=groupId=com.mycompany.test,artifactId=my-example`

  or

  ``` bash
  -p groupId=com.mycompany.test
  -p artifactId=my-example
  ```

  For the Maven plug-in, use the `<configOptions>` section within `<configuration>`:

  ``` xml
  <configuration>
      ...
      <configOptions>
          <groupId>com.mycompany.test</groupId>
          <artifactId>my-example</artifactId>
      </configOptions>
      ...
  </configuration>
  ```

- *additional properties*

  Settings in this category typically are passed to the templates used in generating the files, although generators can use additional properties in deciding how to generate the files.

  For the CLI:

  `--additional-properties "useAbstractClasses=false,returnResponse=true"`

  or

  ``` bash
  -p useAbstractClasses=false
  -p returnResponse=true
  ```

  For the Maven plug-in, use an `<additionalProperties>` section within the `<configuration>` section for the plug-in:

  ``` xml
  <configuration>
      ....
      <additionalProperties>
          <additionalProperty>useAbstractClasses=false</additionalProperty>
          <additionalProperty>returnResponse=true</additionalProperty>
      </additionalProperties>
  </configuration>
  ```

Keep this distinction among global options, config options, and additional properties in mind so you know how to express the configuration you want. The [earlier links](#links-to-settings) to the lists of configuration options for the Helidon generators groups options and additional properties in separate tables.

The next few sections describe, in turn, required settings, settings we recommend, and other common settings most developers will want to use.

### Required Settings

You must specify the following options:

<table>
<caption>Required OpenAPI Generator Options</caption>
<colgroup>
<col style="width: 20%" />
<col style="width: 5%" />
<col style="width: 20%" />
<col style="width: 30%" />
<col style="width: 25%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Option</th>
<th style="text-align: left;">Short Option</th>
<th style="text-align: left;">Plug-in Setting</th>
<th style="text-align: left;">Description</th>
<th style="text-align: left;">Values</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>--inputSpec</code></p></td>
<td style="text-align: left;"><p><code>-i</code></p></td>
<td style="text-align: left;"><p><code>&lt;inputSpec&gt;</code></p></td>
<td style="text-align: left;"><p>Path to the OpenAPI document defining the REST API</p></td>
<td style="text-align: left;"></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>--generatorName</code></p></td>
<td style="text-align: left;"><p><code>-g</code></p></td>
<td style="text-align: left;"><p><code>&lt;generatorName&gt;</code></p></td>
<td style="text-align: left;"><p>Generator you want to use (<code>java-helidon-server</code> or <code>java-helidon-client</code>)</p></td>
<td style="text-align: left;"><p><code>java-helidon-server</code><br />
<code>java-helidon-client</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>--library</code></p></td>
<td style="text-align: left;"><p> </p></td>
<td style="text-align: left;"><p><code>&lt;library&gt;</code></p></td>
<td style="text-align: left;"><p>Library you want to use</p></td>
<td style="text-align: left;"><p><code>mp</code><br />
<code>se</code></p></td>
</tr>
</tbody>
</table>

### Recommended Settings for the OpenAPI Generator

Your project might have different needs, but in general we advise developers to use the following settings when using the OpenAPI generator, both from the command line and using the Maven plug-in.

<table>
<caption>Recommended OpenAPI Generator Additional Properties</caption>
<colgroup>
<col style="width: 21%" />
<col style="width: 42%" />
<col style="width: 35%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Property</th>
<th style="text-align: left;">Description</th>
<th style="text-align: left;">Default</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>apiPackage</code></p></td>
<td style="text-align: left;"><p>Name of the package for generated API interfaces/classes</p></td>
<td style="text-align: left;"><p><code>org.openapitools.server.api</code> or<br />
<code>org.openapitools.client.api</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>modelPackage</code></p></td>
<td style="text-align: left;"><p>Name of the package for generated model (POJO) classes</p></td>
<td style="text-align: left;"><p><code>org.openapitools.server.model</code> or<br />
<code>org.openapitools.client.model</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>invokerPackage</code></p></td>
<td style="text-align: left;"><p>Name of the package for generated driver classes</p></td>
<td style="text-align: left;"><p><code>org.openapitools.server</code> or<br />
<code>org.openapitools.client</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>groupId</code></p></td>
<td style="text-align: left;"><p>Group ID in the generated <code>pom.xml</code></p></td>
<td style="text-align: left;"><p><code>org.openapitools</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>artifactId</code></p></td>
<td style="text-align: left;"><p>Artifact ID in the generated <code>pom.xml</code></p></td>
<td style="text-align: left;"><p><code>openapi-java-server</code> or<br />
<code>openapi-java-client</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>artifactVersion</code></p></td>
<td style="text-align: left;"><p>Artifact version in the generated <code>pom.xml</code></p></td>
<td style="text-align: left;"><p><code>1.0.0</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>useAbstractClass</code></p></td>
<td style="text-align: left;"><p>Generate server abstract classes instead of interfaces. Setting to <code>true</code> generates significantly more helpful code.</p></td>
<td style="text-align: left;"><p><code>false</code></p></td>
</tr>
</tbody>
</table>

> [!NOTE]
> The next table contains recommendations only for using the OpenAPI generator plug-in (not for using the CLI).

<table>
<caption>Recommended OpenAPI Generator Plug-in Options</caption>
<colgroup>
<col style="width: 23%" />
<col style="width: 47%" />
<col style="width: 29%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Plug-in Option</th>
<th style="text-align: left;">Description</th>
<th style="text-align: left;">Default</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>&lt;output&gt;</code></p></td>
<td style="text-align: left;"><p>Directory where the generator should place files.<br />
+ We strongly recommend <code>&lt;output&gt;target/generated-sources&lt;/output&gt;</code> or a subdirectory below there.</p></td>
<td style="text-align: left;"><p><code>.</code><br />
(current directory)</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>&lt;addCompileSourceRoot&gt;</code></p></td>
<td style="text-align: left;"><p>Whether Maven should include the output directory as a source root (that is, include it automatically in the build).<br />
+ We advise <code>&lt;addCompileSourceRoot&gt;true&lt;/addCompileSourceRoot&gt;</code>.</p></td>
<td style="text-align: left;"><p><code>false</code></p></td>
</tr>
</tbody>
</table>

### Common Settings

Among the many configuration settings available to you, some you should particularly consider are summarized in the table below. Refer to the [earlier links](#links-to-settings) for complete lists.

<table>
<caption>Common OpenAPI Generator Additional Properties</caption>
<colgroup>
<col style="width: 18%" />
<col style="width: 22%" />
<col style="width: 13%" />
<col style="width: 13%" />
<col style="width: 31%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Property</th>
<th style="text-align: left;">Description</th>
<th style="text-align: left;">Values</th>
<th style="text-align: left;">Default</th>
<th style="text-align: left;">Notes</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>helidonVersion</code></p></td>
<td style="text-align: left;"><p>Version of Helidon for which to generate the files</p></td>
<td style="text-align: left;"><p> </p></td>
<td style="text-align: left;"><p>Latest published Helidon release *</p></td>
<td style="text-align: left;"><p>Affects:</p>
<ul>
<li><p>Helidon version for the <code>&lt;parent&gt;</code></p></li>
<li><p>Dependencies (<code>javax</code> vs. <code>jakarta</code>)</p></li>
<li><p><code>java import</code> statements in generated code (<code>javax</code> vs. <code>jakarta</code>)</p></li>
<li><p>Which Helidon APIs are used (3.x vs. 4.x, for example)</p></li>
</ul></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>fullProject</code></p></td>
<td style="text-align: left;"><p>Whether to generate all the normal files or only API files</p></td>
<td style="text-align: left;"><p><code>true</code>/<code>false</code></p></td>
<td style="text-align: left;"><p><code>false</code></p></td>
<td style="text-align: left;"><p>The "API files" include files developers do not normally modify after they are generated: the interfaces or classes for the declared API and the model classes.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>serializationLibrary</code></p></td>
<td style="text-align: left;"><p>which Java library to use for serializing JSON</p></td>
<td style="text-align: left;"><p><code>jsonb</code>, <code>jackson</code></p></td>
<td style="text-align: left;"><p><code>jackson</code></p></td>
<td style="text-align: left;"></td>
</tr>
</tbody>
</table>

- The generator attempts to retrieve the list of released Helidon versions from the Helidon website, falling back to locally-stored Java preferences loaded from the previous generator run, and as a last resort using hard-coded values for each major Helidon release.

## Usage

This section covers two major topics:

- [Planning your use of the OpenAPI generators](#planning-your-use-of-the-openapi-generators)
- [Running the generators](#running-the-openapi-generators)

### Planning Your Use of the OpenAPI Generators

Beyond the settings listed above, there are several important choices you need to make when planning your project and when running the OpenAPI generators. This section addresses those choices.

#### Generating a New Project and Generating *Into* an Existing Project

You can use the OpenAPI generator to create a new project or to generate files into an existing project. Some developers do both, using the generator to create the project at first and then to update the project as they evolve the OpenAPI document or change the generation options they select. Others create the project in some other way—​for example, using the [Helidon CLI](../../about/cli.md). The OpenAPI generator CLI and plug-in both support each type of usage.

If the OpenAPI generator finds a pre-existing API or model file, it overwrites it with the latest content. It does *not* overwrite a `pom.xml` file or test files. This is important because certain generation settings can influence the generated dependencies in the `pom.xml` file. For example, the `serializationLibrary` setting creates dependencies on either JSON-B or Jackson artifacts. As a result, changing the generation options can change the dependencies your project should have. If you rerun the generator, the old `pom.xml` remains and does not reflect the revised depencencies.

As a practical matter, many developers use the OpenAPI generators in one of the following ways:

- Use the generator CLI once to create a new project.

  By default, the generator CLI creates files in the normal Maven project structure: `src/main/java`, etc. Then you add your own files to that same project structure. Because the generated files are in the standard places, the project build includes them by default.

  > [!NOTE]
  > You *can* run the generator CLI again to update the generated files. Because this happens outside the project’s build lifecycle, you need to remember to rerun the CLI yourself when you change the OpenAPI document.
  >
  > You also need to identify and manually remove any previously-generated files that become obsolete. Similarly, you must understand how changes in the OpenAPI document or the generation options affect the project dependencies and update the project `pom.xml` accordingly.

- Use the generator plug-in to (re)generate files during each build.

  Specify in the plug-in configuration that the generated files should reside in `target/generated-sources` directory (the conventional location for generated sources) or a subdirectory below there. Each project build runs the OpenAPI generator which reads the then-current OpenAPI document file. With the generated files under `target`, you can use `mvn clean` to remove any obsolete generated files left over from previous builds.

  > [!NOTE]
  > In particular, with `mvn clean` each build regenerates the candidate `pom.xml` under `target/generated-sources`. You can inspect the generated `pom.xml` file for changes in dependencies and make any necessary changes in the actual project `pom.xml` file.

#### Generating Interfaces or Classes

As you generate a Helidon MP *server*, you can choose whether you want Java interfaces or classes to represent the RESTful API endpoints.

By default, the Helidon OpenAPI server generator creates classes. You write your own concrete subclasses which extend those generated classes, supplying the business logic for each REST endpoint. *Do not* modify the generated classes.

If you set `useAbstractClasses=false` then the generator creates Java interfaces instead of classes. You then write classes which implement those generated interfaces.

Either way, you can safely regenerate the code later so long as you have not edited the generated code. The generator replaces the generated classes or interfaces but does not touch other classes you wrote.

The Helidon *client* generator always creates concrete classes. Typically, you do not need to customize the behavior in the generated client API classes. If you choose to do so, write your own subclass of the generated client API class; *do not* modify the generated files.

#### Grouping Operations into APIs

Each operation in an OpenAPI document can have a `tags` attribute. By default, the generators group operations with the same `tags` value into the same API or service. Alternatively, if you specify the option `x-helidon-groupBy` as `first-path-segment`, the generators use the first segment of the path to group operations together.

When you generate a Helidon MP server, the generator creates a separate interface or class for each API your service *exposes*. You implement each interface or extend each class to add your business logic for that API.

When you generate a Helidon MP client, the generated code contains a separate API class for each distinct API your code might *invoke*.

### Running the OpenAPI Generators

Earlier we listed the ways you can run the OpenAPI generator:

- using the OpenAPI generator CLI
- using the OpenAPI generator Maven plug-in
- using the online OpenAPI generator website

The next sections describe each of these techniques in detail.

#### Using the OpenAPI Generator CLI

> [!NOTE]
> You need to download the CLI `.jar` file before you can run the CLI. Follow these [instructions](https://github.com/OpenAPITools/openapi-generator#13---download-jar) and remember where you save the `.jar` file. The examples below use the placeholder `path-to-generator` to represent the directory where you store that downloaded file.

The following example uses the Helidon server generator to create a project or regenerate files into an existing project.

*Creating or updating a server project using the OpenAPI generator CLI*

``` bash
java -jar ${path-to-generator}/openapi-generator-cli.jar \
  generate \
  -i src/main/resources/petstore.yaml \
  -g java-helidon-server \
  --library mp \
  -p groupId=io.helidon.examples \
  -p artifactId=helidon-openapigen-mp-server \
  -p artifactVersion=1.0.0-SNAPSHOT \
  -p apiPackage=io.helidon.examples.openapigen.mp.server.api \
  -p modelPackage=io.helidon.examples.openapigen.mp.server.model \
  -p invokerPackage=io.helidon.examples.openapigen.mp.server
```

The next example runs the Helidon client generator using the same input file.

*Creating or updating a client project using the OpenAPI generator CLI*

``` bash
java -jar ${path-to-generator}/openapi-generator-cli.jar \
  generate \
  -i src/main/resources/petstore.yaml \
  -g java-helidon-client \
  --library mp \
  -p groupId=io.helidon.examples \
  -p artifactId=helidon-openapigen-mp-client \
  -p artifactVersion=1.0.0-SNAPSHOT \
  -p apiPackage=io.helidon.examples.openapigen.mp.client.api \
  -p modelPackage=io.helidon.examples.openapigen.mp.client.model \
  -p invokerPackage=io.helidon.examples.openapigen.mp.client
```

The key differences between the commands are:

- the generator selected by the `-g` option (`client` vs. `server`),
- the artifact ID and package names (`client` vs. `server`).

You could use these two commands together to generate a server submodule and a client submodule in a pre-existing multi-module Maven project. Remember that the resulting client project can access any server which implements the API described in the `petstore.yaml` OpenAPI document, whether it was generated using the OpenAPI generator tool or not.

In both examples, the generator creates the entire project if it does not exist and recreates the generated API and model files if the project already exists. The generator does not overwrite an existing `pom.xml` file, previously-generated test files, or files you create yourself.

#### Invoking the OpenAPI Generator Maven Plug-in

You can run the OpenAPI generator plug-in as part of your project build to generate or regenerate files.

First, declare the plug-in as explained in the [earlier section on Maven coordinates](#maven-coordinates).

Then, in the `<build>` section of your `pom.xml` file, add an execution of the plug-in with the configuration you want. By default, the plug-in runs during the `generate-sources` phase of the Maven build.

The plug-in execution in the following example is equivalent to the CLI example above for generating server files:

*Creating or updating a client project using the OpenAPI Maven plug-in*

``` xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <inputSpec>${project.basedir}/src/main/resources/petstore.yaml</inputSpec>
                <generatorName>java-helidon-client</generatorName>
                <library>mp</library>
                <output>${project.build.directory}/generated-sources/client</output> 
                <addCompileSourceRoot>true</addCompileSourceRoot>
                <configOptions>
                    <groupId>io.helidon.examples</groupId>
                    <artifactId>helidon-openapigen-mp-client</artifactId>
                    <artifactVersion>1.0.0-SNAPSHOT</artifactVersion>
                    <apiPackage>io.helidon.examples.openapigen.mp.client.api</apiPackage>
                    <modelPackage>io.helidon.examples.openapigen.mp.client.model</modelPackage>
                    <invokerPackage>io.helidon.examples.openapigen.mp.client</invokerPackage>
                </configOptions>
                <additionalProperties>
                    <additionalProperty>returnResponse=true</additionalProperty>
                </additionalProperties>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- Specifies that the generated files should reside in the `target/generated-sources/client` directory.

#### Using the Online Generator

The OpenAPI tools project hosts and maintains the online OpenAPI generator at <http://api.openapi-generator.tech>. You can use the site’s API browser to explore the available generators and the settings each supports, expressed as JSON.

To generate your project, you supply the options and additional properties as JSON. The online generator provides you with a file ID, and you refer to the file ID in a subsequent HTTP request to retrieve your project.

> [!NOTE]
> The online generator stores your project on the server which you then retrieve using a separate HTTP request. Before you use the online generator, consider whether any of the input you provide—​the OpenAPI document, package or Maven coordinates—​and therefore the generated project will reveal any sensitive information.

This document does not explore further the use of the online generator.

## Using the Generated Code

The Helidon generators go a long way in helping you write your client or server. Even so, there are important parts of your project only you can provide. This section describes your next steps *after* you have run the generator.

### Completing the Server

Recall from earlier how the OpenAPI generator gathers operations into one or more APIs or services and generates either an abstract class or an interface—​your choice—​for each API. You need to extend each generated API class or implement each generated API interface by writing your own classes.

Any input parameters to the operations are expressed as POJO model objects or Java types as declared in the OpenAPI document. You write server code to use each of the input parameters to accomplish whatever business purpose that operation is responsible for, possibly returning a result as a POJO or Java type as indicated for that operation in the OpenAPI document.

In some cases, you might need more control over the response sent to the client. In that case, specify the additional property `returnResponse=true` when you run the Helidon server generator. The return type for the generated methods is the Jakarta RESTful web services `Response` and your code has complete control—​and therefore responsibility—​over setting the status, writing the response entity (if any), and assigning any returned headers.

Your code plus the server code from the Helidon generator—​all running on Helidon MP—​combine to fully implement the server API declared in the original OpenAPI document. Build your project to get a tailored Helidon MP server `.jar` file or Docker image and your server is ready to run.

### Using the Client Library

The generated client code represents a true library. Typically, you do not need to customize the generated client code itself. You *do* need to write code to invoke the code in that library.

The Helidon MP client generator creates a MicroProfile REST client interface for each API. Each generated API interface is annotated so your code can `@Inject` the API into one of your own beans and then use the interface directly to invoke the remote service. Alternatively, you can also explicitly use the [`RestClientBuilder`](https://download.eclipse.org/microprofile/microprofile-rest-client-3.0/apidocs/org/eclipse/microprofile/rest/client/RestClientBuilder.html) to create an instance programmatically and then invoke its methods to contact the remote service. The [Helidon MP REST Client](../../mp/restclient/restclient.md) documentation describes both approaches in more detail.

In the following example, `ExampleResource` (itself running in a server) invokes a remote Pet service and shows one way to use the generated `PetApi` REST client interface.

*Using the generated `PetApi` returned from a separate service*

``` java
@Path("/exampleServiceCallingService") 
public class ExampleOpenApiGenClientResource {
    @Inject 
    @RestClient 
    private PetApi petApi; 

    @GET
    @Path("/getPet/{petId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pet getPetUsingId(@PathParam("petId") Long petId) throws ApiException {
        Pet pet = petApi.getPetById(petId); 
        return pet;
    }
}
```

- Uses a bean-defining annotation so CDI can inject into this class.
- Requests that CDI inject the following field.
- Identifies to Helidon MP that the following field is a REST client.
- Declares the field using the generated `PetApi` type.
- Invokes the remote service using the injected field and the parameter from the incoming request.

## References

- [OpenAPI Generator Official Website](https://openapi-generator.tech)
- [OpenAPI Generator GitHub Repository](https://github.com/OpenAPITools/openapi-generator)
- [OpenAPI specification](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md)
- [MicroProfile REST Client specification](https://github.com/eclipse/microprofile-rest-client)
