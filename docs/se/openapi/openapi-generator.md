# OpenAPI-based Code Generation

## Overview

The [OpenAPI specification](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md) provides a standard way to express RESTful APIs.

Separately, the [OpenAPI generator](https://openapi-generator.tech) project has created a powerful code generator tool which accepts an OpenAPI document and generates client and server code for many languages and frameworks. The Helidon team contributes to this tool to ensure that it provides strong support for Helidon SE clients and servers. As a result, you can use the generator to create code that fits smoothly into your Helidon applications.

Use the OpenAPI generator release 7.6.0 or later which this document describes.

In the vocabulary of the tool, there are two *generators* for Helidon:

- `java-helidon-client` (hereafter the Helidon client generator)
- `java-helidon-server` (hereafter the Helidon server generator).

Each of these generators supports two *libraries*:

- `mp` - for Helidon MP code generation
- `se` - for Helidon SE code generation

Use the Helidon *client* generator and its `se` library to create a Helidon SE client based on [Helidon WebClients](../../se/webclient.md). The resulting client library works with any server that implements the API declared in the OpenAPI document you specified when you ran the generator. The client library provides an abstraction similar to remote procedure calls (RPC). To access a remote service that implements the endpoints declared in the OpenAPI document, your code uses the generated client library first to establish a connection to the remote service and then to call remote service endpoints by invoking local methods passing POJO business objects or Java types as arguments.

Use the tool’s Helidon *server* generator and its `se` library to create server endpoint stubs for a Helidon SE service. You build on these stubs by extending a generated class or implementing a generated interface, adding your specific business logic to finish the implementation of the endpoints. The combination of the generated server code plus Helidon SE underneath it allows you to focus on the business details instead of resource boilerplate.

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

As you generate a Helidon SE *server*, you can choose whether you want Java interfaces or classes to represent the RESTful API endpoints.

By default, the Helidon OpenAPI server generator creates classes. You write your own concrete subclasses which extend those generated classes, supplying the business logic for each REST endpoint. *Do not* modify the generated classes.

If you set `useAbstractClasses=false` then the generator creates Java interfaces instead of classes. You then write classes which implement those generated interfaces.

Either way, you can safely regenerate the code later so long as you have not edited the generated code. The generator replaces the generated classes or interfaces but does not touch other classes you wrote.

The Helidon *client* generator always creates concrete classes. Typically, you do not need to customize the behavior in the generated client API classes. If you choose to do so, write your own subclass of the generated client API class; *do not* modify the generated files.

#### Grouping Operations into APIs

Each operation in an OpenAPI document can have a `tags` attribute. By default, the generators group operations with the same `tags` value into the same API or service. Alternatively, if you specify the option `x-helidon-groupBy` as `first-path-segment`, the generators use the first segment of the path to group operations together.

When you generate a Helidon SE server, the generator creates a separate interface or class for each API your service *exposes*. You implement each interface or extend each class to add your business logic for that API.

The generator creates Helidon routing logic based on the longest common path prefix shared among the operations that are grouped into each API.

> [!NOTE]
> If the operations in an API have no common prefix then the generated routing will be inefficient at runtime. The generator logs a warning and includes a `TODO` comment in the generated routing.
>
> Review the paths and the `tags` settings in your OpenAPI document and consider revising one or the other so all operations in each API share a common path prefix. If you do not have control over the OpenAPI document or do not want to change it, consider specifying the generator option `x-helidon-groupBy first-path-segment` which groups operations into APIs not by `tags` value but by the first segment of each operation’s path.

When you generate a Helidon SE client, the generated code contains a separate API class for each distinct API your code might *invoke*.

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
  --library se \
  -p groupId=io.helidon.examples \
  -p artifactId=helidon-openapigen-se-server \
  -p artifactVersion=1.0.0-SNAPSHOT \
  -p apiPackage=io.helidon.examples.openapigen.se.server.api \
  -p modelPackage=io.helidon.examples.openapigen.se.server.model \
  -p invokerPackage=io.helidon.examples.openapigen.se.server
```

The next example runs the Helidon client generator using the same input file.

*Creating or updating a client project using the OpenAPI generator CLI*

``` bash
java -jar ${path-to-generator}/openapi-generator-cli.jar \
  generate \
  -i src/main/resources/petstore.yaml \
  -g java-helidon-client \
  --library se \
  -p groupId=io.helidon.examples \
  -p artifactId=helidon-openapigen-se-client \
  -p artifactVersion=1.0.0-SNAPSHOT \
  -p apiPackage=io.helidon.examples.openapigen.se.client.api \
  -p modelPackage=io.helidon.examples.openapigen.se.client.model \
  -p invokerPackage=io.helidon.examples.openapigen.se.client
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
                <library>se</library>
                <output>${project.build.directory}/generated-sources/client</output> 
                <addCompileSourceRoot>true</addCompileSourceRoot>
                <configOptions>
                    <groupId>io.helidon.examples</groupId>
                    <artifactId>helidon-openapigen-se-client</artifactId>
                    <artifactVersion>1.0.0-SNAPSHOT</artifactVersion>
                    <apiPackage>io.helidon.examples.openapigen.se.client.api</apiPackage>
                    <modelPackage>io.helidon.examples.openapigen.se.client.model</modelPackage>
                    <invokerPackage>io.helidon.examples.openapigen.se.client</invokerPackage>
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

If you choose to generate interfaces for the APIs, the generator creates routing rules for the API services it generates but you write virtually all of the logic to process incoming requests by implementing the very short methods generated in the implementation class.

The rest of this sections focuses on your next steps if, on the other hand, you decide to generate abstract classes.

#### What you *must* do: implement your business logic and send the response

The generator creates an implementation class as well as the abstract class for each API. The implementation class contains a `handle` method for each API operation with a very simple method body that returns a not-yet-implemented HTTP status in the response. The following example shows the generated method for the `addPet` OpenAPI operation.

*The generated `handleAddPet` method in the `PetApiImpl` class*

``` java
public class PetServiceImpl extends PetService {
    @Override
    protected void handleAddPet(ServerRequest request, ServerResponse response,
                                Pet pet) {
        response.status(Status.NOT_IMPLEMENTED_501).send();
    }
}
```

Customize the class to manage the pets and revise the method to save the new pet and send the correct response, as shown next.

*The customized `handleAddPet` method in the `PetApiImpl` class*

``` java
public class PetServiceImpl extends PetService {

    private final Map<Long, Pet> pets = new HashMap<>(); 

    @Override
    protected void handleAddPet(ServerRequest request, ServerResponse response,
                                Pet pet) {
        if (pets.containsKey(pet.getId())) { 
            AddPetOp.Response405.builder().send(response);
        }
        pets.put(pet.getId(), pet); 
        AddPetOp.Response200.builder().send(response); 
    }
}
```

- Business logic: create a very simple data store - a real app would use a database.
- Business logic: make sure the pet being added does not already exist. Send the invalid request status code if it does.
- Business logic: add the pet to the data store.
- Prepare and send the `200` response.

If a response has any *required* response parameters you would pass them as parameters to the `builder` method. Add *optional* response parameters using other generated builder methods. The following example illustrates this for the `findPetsByTags` operation and its `response` output parameter.

*The customized `findPetsByTags` method in the `PetApiImpl` class*

``` java
public class PetServiceImpl extends PetService {

    private final Map<Long, Pet> pets = new HashMap<>(); 

    @Override
    protected void handleFindPetsByTags(ServerRequest request, ServerResponse response,
                                        List<String> tags) { 

        List<Pet> result = pets.values().stream()
                .filter(pet -> pet.getTags()
                        .stream()
                        .anyMatch(petTag -> tags.contains(petTag.getName())))
                .toList(); 

        FindPetsByTagsOp.Response200.builder() 
                .response(result) 
                .send(response); 

    }
}
```

- Uses the same data store as in the earlier example.
- The `tags` parameter conveys the tag values to be matched in selecting pets to report. Other generated code extracts the runtime argument’s value from the request and then automatically passes it to the method.
- Collects all pets with any tag that matches any of the selection tags passed in.
- Uses the generated `Response200` to prepare the response.
- Assigns the optional `response` output parameter—​the list of matching `Pet` objects.
- Send the response using the prepared response information.

Write each of the `handleXxx` methods appropriately so they implement the business logic you need and send the response.

The generator creates a `ResponseNNN` Java `record` for each operation response status code `NNN` that is declared in the OpenAPI document. You can return other status values with other output parameters even if they are not declared in the OpenAPI document, but your code must prepare the `ServerResponse` entirely by itself; the generator cannot generate helper records for responses that are absent from the document.

#### What you *can* do: override the generated behavior

Generated code takes care of the following work:

- Route each request to the method which should respond.
- Extract each incoming parameter from the request and convert it to the correct type, applying any validation declared in the OpenAPI document.
- Pass the extracted parameters to the developer-written `handleXxx` method.
- Assemble required and optional response parameters and send the response.

You can override any of the generated behavior by adding code to the generated API implementation class you are already editing to customize the `handleXxx` methods and by writing new classes which extend some of the generated classes.

##### Override routing

To change the way routing occurs, simply override the `routing` method in your `PetServiceImpl` class. Make sure your custom routing handles all the paths for which the API is responsible.

##### Override how to extract one or more parameters from a request

For each operation in an API the generator creates an inner class and, for each incoming parameter for that operation, a method which extracts and validates the parameter. Override how a parameter is extracted by following these steps, using the `AddPetOp` as an example.

1.  Write a class which extends the inner class for the operation.
2.  In that subclass override the relevant method.

    *Customized `AddPetOp` class*

``` java
    public class AddPetOpCustom extends PetService.AddPetOp {
        @Override
        protected Pet pet(ServerRequest request, ValidatorUtils.Validator validator) {
            Pet result = request.content().hasEntity() 
                    ? request.content().as(Pet.class)
                    : null;

            // Insist that pet names never start with a lower-case letter.
            if (result != null) {
                validator.validatePattern("pet", result.getName(), "[^a-z].*"); 
            }
            return result; 
        }
    }
    ```

    - Extracts the parameter from the request. This happens to use the same logic as in the generated method but you can customize that as well if you need to.
    - Apply any relevant validations. This silly but illustrative example rejects any pet name that starts with a lower-case letter.
    - Return the extracted value, properly typed.

3.  In the implementation class for the API (`PetServiceImpl`) override the `createAddPetOp` method so it returns an instance of your new subclass `AddPetOpCustom` of the operation inner class `AddPetOp`.

    *Providing your custom implementation of `AddPet`*

``` java
    public class PetServiceImpl extends PetService {
        @Override
        protected AddPetOp createAddPetOp() {
            return new AddPetOpCustom();
        }
    }
    ```

##### Override how an operation is prepared from a request

The generated abstract class contains a method named for each operation declared in the OpenAPI document (`addPet`) which accepts the Helidon request and response as parameters. The generated code in these methods invokes the code to extract each incoming parameter from the request, perform any declared validation on them, and pass them to the developer-written method (`handleAddPet(request, response, pet)`).

To completely change this behavior, override the `addPet` method in the `PetServiceImpl` class to do what you need.

### Using the Client Library

The generated client code represents a true library. Typically, you do not need to customize the generated client code itself. You *do* need to write code to invoke the code in that library.

The generated Helidon SE client includes the class `ApiClient`. This class corresponds to the Helidon [`WebClient`](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/WebClient.html) and represents the connection between your code and the remote server. The generator also creates one or more `Api` interfaces and corresponding implementation classes. The examples below use the `PetApi` interface and the `PetApiImpl` class.

To invoke the remote service your code must:

1.  Create an instance of `ApiClient` using an `ApiClient.Builder`.
2.  Use that `ApiClient` instance to instantiate a `PetApi` object.
3.  Invoke the methods on the `PetApi` object to access the remote services and then retrieve the returned result value.

The following sections explain these steps.

#### Creating an `ApiClient` Instance

The Helidon SE client generator gives you as much flexibility as you need in connecting to the remote service.

Internally, the `ApiClient` uses a Helidon `WebClient` object to contact the remote system. The `ApiClient.Builder` automatically prepares a Helidon [`WebClientConfig.Builder`](/apidocs/io.helidon.webclient.api/io/helidon/webclient/api/WebClientConfig.Builder.html) object using information from the OpenAPI document.

The next sections describe, from simplest to most complicated, the ways your code can create an `ApiClient` instance, each involving increased involvement with the `WebClientConfig.Builder` object.

##### Accepting the Automatic `WebClientConfig.Builder`

In the simplest case, your code can get an `ApiClient` instance directly.

*Creating an `ApiClient` instance - simple case*

``` java
public class ExampleClient {

    private ApiClient apiClient; 

    void init() {
        ApiClient apiClient = ApiClient.builder().build(); 
    }
}
```

- The same `ApiClient` instance can be reused to invoke multiple APIs handled by the same server.
- Creates an `ApiClient` instance using default settings from the OpenAPI document.

Your code relies fully on the automatic `WebClient`. In many cases, this approach works very well, especially if the OpenAPI document correctly declares the servers and their URIs.

##### Influencing the Automatic `WebClientConfig.Builder`

Your code can use the `ApiClient.Builder` to fine-tune the settings for the internal `WebClientConfig.Builder`. For instance, your code can set an object mapper to be used for Jackson processing or the `JsonbConfig` object to be used for JSON-B processing, depending on which serialization library you chose when you ran the generator.

Your code does not need to know how the object mapper setting is conveyed to the internal `WebClientConfig.Builder`. The `ApiClient.Builder` knows how to do that.

*Creating an `ApiClient` instance - influencing the `ApiClient.Builder`*

``` java
public class ExampleClient {

    private ApiClient apiClient; 

    void init() {
        ObjectMapper myObjectMapper = new ObjectMapper(); 
        apiClient = ApiClient.builder()
                .objectMapper(myObjectMapper) 
                .build();
    }
}
```

- Stores a reusable `ApiClient`.
- A real app would fully set up the `ObjectMapper`.
- Sets the object mapper for use in the `ApiClient.Builder` 's internal `WebClientConfig.Builder`.

##### Adjusting the Automatic `WebClientConfig.Builder`

In more complicated situations, your code can adjust the settings of the `WebClientConfig.Builder` which the `ApiClient.Builder` creates.

*Creating an `ApiClient` instance - adjusting the `WebClientConfig.Builder`*

``` java
public class ExampleClient {

    private ApiClient apiClient; 

    void init() {
        ApiClient.Builder apiClientAdjustedBuilder = ApiClient.builder(); 

        apiClientAdjustedBuilder
                .webClientBuilder() 
                .connectTimeout(Duration.ofSeconds(4)); 

        apiClient = apiClientAdjustedBuilder.build(); 
    }
}
```

- Stores a reusable `AppClient`.
- Creates a new `AppClient` builder.
- Access the `` ApiClient.Builder’s automatic `WebClientConfig.Builder `` instance.
- Adjusts a setting of the `WebClientConfig.Builder` directly.
- Builds the `ApiClient` which implicitly builds the `WebClient` from the now-adjusted internal `WebClientConfig.Builder`.

The automatic `WebClientConfig.Builder` retains information derived from the OpenAPI document unless your code overrides those specific settings.

##### Providing a Custom `WebClientConfig.Builder`

Lastly, you can construct the `WebClientConfig.Builder` entirely yourself and have the `ApiClient.Builder` use it instead of its own internal builder.

*Creating an `ApiClient` instance - using a custom `WebClientConfig.Builder`*

``` java
public class ExampleClient {

    private ApiClient apiClient; 

    void init() {
        WebClientConfig.Builder customWebClientBuilder = WebClient.builder() 
                .connectTimeout(Duration.ofSeconds(3)) 
                .baseUri("https://myservice.mycompany.com"); 

        apiClient = ApiClient.builder() 
                .webClientBuilder(customWebClientBuilder) 
                .build(); 
    }
}
```

- Stores a reusable `AppClient`.
- Creates a new `WebClientConfig.Builder`.
- Sets the connection timeout directly on the `WebClientConfig.Builder`.
- Sets the base URI on the `WebClienConfig.Builder`.
- Creates a new \`ApiClient.Builder'.
- Sets the `WebClientConfig.Builder` which the `ApiClient.Builder` should use (instead of the one it prepares internally).
- Builds the `ApiClient` which uses the newly-assigned `WebClientConfig.Builder` in the process.

Note that this approach entirely replaces the internal, automatically-prepared `WebClientConfig.Builder` with yours; it *does not* merge the new builder with the internal one. In particular, any information from the OpenAPI document the generator used to prepare the internal `WebClientConfig.Builder` is lost.

#### Creating a `PetApi` Instance

The `ApiClient` represents the connection to the remote server but not the individual RESTful operations. Each generated `xxxApi` interface exposes a method for each operation declared in the OpenAPI document associated with that API via its `tags` value. By example, the `PetApi` interface exposes a method for each operation in the OpenAPI document that pertains to pets.

To invoke an operation defined on the `PetApi` interface, your code instantiates a `PetApi` using an `ApiClient` object:

*Preparing the PetStore Client API*

``` java
public class ExampleClient {

    private ApiClient apiClient; 

    private PetApi petApi; 

    void preparePetApi() {
        petApi = PetApiImpl.create(apiClient); 
    }
}
```

- Stores a reusable `AppClient`.
- Stores a reusable `PetApi` for invoking pet-related operations.
- Initializes and saves the `PetApi` instance using the previously-prepared `apiClient`.

#### Invoking Remote Endpoints

With the `petApi` object, your code can invoke any of the methods on the `PetApi` interface to contact the remote service.

The Helidon SE client generator creates an `ApiResponse` interface. Each generated `PetApi` method returns an `ApiResponse<returnType>` where the `returnType` is the return type (if any) declared in the OpenAPI document for the corresponding operation.

The `ApiResponse` interface exposes two methods your code can use to work with the response from the remote service invocation:

- `T result()`

  Provides access to the value returned by the remote service in the response. This method lets your code fetch the return value directly.

- `HTTPClientResponse webClientResponse()`

  Provides access to the Helidon `HTTPClientResponse` object. Your code can find out the HTTP return status, read headers in the response, and process the content (if any) in the response however it needs to.

In the Helidon WebClient model, the first part of the response message can arrive (the status and headers are available) before the entity in the body of the response is readable. So there are two events associated with an incoming HTTP response:

1.  when the response *excluding* the entity content has arrived, and
2.  when your code can begin consuming the entity content.

You can adopt different styles of retrieving the results, depending on the specific needs of the code you are writing.

##### Access only the result

*Access with only result access*

``` java
void findAvailablePets() {
    ApiResponse<List<Pet>> apiResponse =
            petApi.findPetsByStatus(List.of(Pet.StatusEnum.AVAILABLE.value())); 

    List<Pet> availablePets = apiResponse.result(); 
}
```

- Use the previously-prepared `petApi` to find pets that have the `available` status.
- Retrieve the typed result from the `ApiResponse`.

##### Access with status checking

The Helidon WebClient programming model includes a `HTTPClientResponse` interface which exposes all aspects of the HTTP response returned from the remote service.

The next example shows how your code can use the `HTTPClientResponse`.

*Access with status checking*

``` java
void findAvailablePets() {
    ApiResponse<List<Pet>> apiResponse =
            petApi.findPetsByStatus(List.of(Pet.StatusEnum.AVAILABLE.value())); 

    try (HttpClientResponse webClientResponse = apiResponse.webClientResponse()) { 
        if (webClientResponse.status().code() != 200) { 
            // Handle a non-successful status.
        }
    }

    List<Pet> avlPets = apiResponse.result(); 
}
```

- Start the remote service invocation.
- Wait for the HTTP response status and headers to arrive.
- Check the status in the HTTP response.
- Wait for the content to arrive, extracting the result and converting it to the proper type.

This code also blocks the current thread, first to wait for the initial response and then to wait for the result content.

## References

- [OpenAPI Generator Official Website](https://openapi-generator.tech)
- [OpenAPI Generator GitHub Repository](https://github.com/OpenAPITools/openapi-generator)
- [OpenAPI specification](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md)
- [Helidon WebClient documentation](../../se/webclient.md)
