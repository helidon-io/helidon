# Oracle Cloud Infrastructure Tutorial

This guide describes the basic process of deploying a Helidon MP application on Oracle Cloud Infrastructure (OCI).

## Overview

Oracle Cloud Infrastructure (OCI) is a set of complementary cloud services that enable you to build and run a range of applications and services in a highly available hosted environment. For an overview of OCI features, see [Welcome to Oracle Cloud Infrastructure](https://docs.oracle.com/en-us/iaas/Content/GSG/Concepts/baremetalintro.htm) in OCI documentation.

The Helidon MP OCI Project Starter intended to quickly create a Helidon project for deployment on OCI. It is currently only available with Helidon MP, but you can write Helidon SE applications directly with the [OCI SDK for Java APIs](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm) and use this guide as a guideline for setting up your OCI environment.

By deploying Helidon applications on OCI, you can take advantage of OCI services and features. Helidon integration with OCI includes support for the [OCI SDK for Java](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm) - allowing you to write custom code to extend features.

Helidon also provides a [Helidon OCI SDK CDI portable extension](https://jakarta.ee/specifications/cdi/4.0/jakarta-cdi-spec-4.0.html#spi) to support injecting [Oracle Cloud Infrastructure SDK Clients](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm) in your Helidon application. See [OCI Integration](../integrations/oci.md).

This guide assumes that you have basic knowledge and familiarity with OCI. If you already have an OCI environment set up and running, you can consult the [Deploying a Helidon OCI MP Application on a Basic OCI Setup](https://github.com/helidon-io/helidon-labs/blob/main/hols/oci-basic-setup/README.md) lab in the Helidon Labs GitHub repository for the requirements of configuring and running the server service.

## What You Need

For this tutorial, you will need the following:

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../../about/kubernetes.md)). |
| [Helidon CLI](../../about/cli.md) | If you want to use the Helidon CLI to create and build your application |

*Verify Prerequisites*

``` bash
java -version
mvn --version
docker --version
kubectl version
```

*Setting JAVA_HOME*

``` bash
# On Mac
export JAVA_HOME=`/usr/libexec/java_home -v 21`

# On Linux
# Use the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-21
```

## Generating the Project

You can generate Helidon MP project files for OCI using the Helidon Project Starter.

A Helidon Project Starter allows you to choose from a set of archetypes with pre-defined feature sets and lets you customize it by providing a host of options.

1.  Go to the [Helidon Project Starter](https://helidon.io/starter) page on the Helidon website.
2.  Under **Helidon Flavor**, select **Helidon MP** and click **Next**.
3.  Under **Application Type**, select **OCI** and click **Next**.
4.  If you are using this application for testing or development purposes, then under **Customize Project**, leave the values as default and go to the next step. If you want to use the generated project as the basis for a production application, then replace `groupId`, `artifactId` and `package` with values appropriate for your application.
5.  Click **Download** to save the project zip archive to your computer.
6.  Extract the project files.

You can also use the Helidon CLI to generate the Helidon MP OCI project instead.

The Helidon MP OCI project is pre-configured to integrate with several common OCI services but you must update its configuration with values specific to your application and OCI environment. To learn more about the components of Helidon MP OCI Starter Project, see `/README.md` and `/server/README.md`, included in the starter project.

The project contains both server and client services that leverage OpenAPI to demonstrate an API-first development approach. Both services generate API source codes using the [OpenAPI java-helidon-server Generator](https://openapi-generator.tech/docs/generators/java-helidon-server/) and [OpenAPI java-helidon-client Generator](https://openapi-generator.tech/docs/generators/java-helidon-client/), along with the [OpenAPI 3.0 Specification](https://spec.openapis.org/oas/v3.0.0).

It should be noted that the project is limited to implementing the business logic of the generated Server API code.

For more information on Helidon OpenAPI support, see the [MicroProfile OpenAPI specification](https://download.eclipse.org/microprofile/microprofile-open-api-3.1.1/microprofile-openapi-spec-3.1.1.html).

The server service also allows you to build, configure and test the application locally.

The Helidon MP OCI Starter Project also includes Helidon’s built in liveness and readiness health checks which allow you to expose the project’s health status to external tools. See [MicroProfile Health](../health.md) for general information on the Health Check feature and then read the **Health Checks** section in `/server/README.md` for how it’s implemented in this project.

*The structure of the Helidon MP OCI Project*

├── README.md
    ├── client
    │   ├── README.md
    │   ├── pom.xml
    │   └── src
    │       └── main
    │           └── resources
    │               └── META-INF
    │                   └── beans.xml
    ├── pom.xml
    ├── server
    │   ├── Dockerfile
    │   ├── README.md
    │   ├── app.yaml
    │   ├── distribution.xml
    │   ├── pom.xml
    │   └── src
    │       ├── main
    │       │   ├── java
    │       │   │   └── me
    │       │   │       └── username
    │       │   │           └── mp
    │       │   │               └── oci
    │       │   │                   └── server
    │       │   │                       ├── GreetLivenessCheck.java
    │       │   │                       ├── GreetReadinessCheck.java
    │       │   │                       ├── GreetResource.java
    │       │   │                       ├── GreetingProvider.java
    │       │   │                       └── package-info.java
    │       │   └── resources
    │       │       ├── META-INF
    │       │       │   ├── beans.xml
    │       │       │   ├── microprofile-config-prod.properties
    │       │       │   ├── microprofile-config-test.properties
    │       │       │   └── microprofile-config.properties
    │       │       ├── application.yaml
    │       │       └── logging.properties
    │       └── test
    │           └── java
    │               └── me
    │                   └── username
    │                       └── mp
    │                           └── oci
    │                               └── server
    │                                   ├── Common.java
    │                                   ├── GreetResourceConfigFileTest.java
    │                                   ├── GreetResourceInstancePrincipalTest.java
    │                                   └── GreetResourceMockedTest.java
    └── spec
        ├── README.md
        └── api.yaml

## Running the Application Locally

Use the project to build an application jar for the example. It saves all runtime dependencies in the `/target/libs` directory, allowing you to easily start the application by running the application jar file.

1.  Change to the directory containing the project files and run:

*Build the Application*

``` bash
mvn package
```

1.  Then, to run the application, run:

*Run the application*

``` bash
java -jar server/target/myproject-server.jar
```

The example is a very simple "Hello World" greeting service. It supports GET requests for generating a greeting message, and a PUT request for changing the greeting itself. The response is encoded using JSON.

For example:

*Try the Application*

``` bash
curl -X GET http://localhost:8080/greet
{"message":"Hello World!"}

curl -X GET http://localhost:8080/greet/Joe
{"message":"Hello Joe!"}

curl -X PUT -H "Content-Type: application/json" -d '{"greeting" : "Hola"}' http://localhost:8080/greet/greeting

curl -X GET http://localhost:8080/greet/Jose
{"message":"Hola Jose!"}
```

## Deploying a Helidon Application on OCI

You can deploy a Helidon application to an Oracle Cloud Infrastructure (OCI) environment, using either OCI Compute or OCI Kubernetes Engine (OKE). Depending on your requirements, one option might be better suited to your needs than the other.

- **OCI Compute** lets you provision and manage Compute hosts, known as instances, to run your applications. Instances can be either bare metal or virtual machines (VM). See [Compute](https://docs.oracle.com/en-us/iaas/Content/Compute/home.htm) in OCI documentation for more information.
- **OKE** is a fully-managed, scalable, and highly available service that you can use to deploy your containerized applications to the cloud. See [Kubernetes Engine](https://docs.oracle.com/en-us/iaas/Content/ContEng/home.htm) in OCI documentation for more information.

After you have selected your OCI environment, you can get started with deploying your Helidon applications.

- To learn how to deploy a Helidon MP application on **OCI Compute**, follow the instructions outlined at [Deploying a Helidon OCI MP Application on a Basic OCI Setup](https://github.com/helidon-io/helidon-labs/blob/main/hols/oci-basic-setup/README.md) in the Helidon Labs GitHub repository.
- To learn how to deploy a Helidon MP application on **OCI OKE**, follow the instructions outlined at [Kubernetes: Deploy a Java Helidon Application](https://docs.oracle.com/en-us/iaas/developer-tutorials/tutorials/helidon-k8s/01oci-helidon-k8s-summary.htm) in OCI documentation. Make sure you select **OCI** as the Application Type, and *not* Quickstart.

If you already have an OCI environment, you can deploy the application there instead of setting up a new environment. At minimum, you must configure the following OCI resources to deploy a Helidon application:

- **Compartment**: to organize the OCI resources required for the Helidon project.
- **Dynamic Group**: to group the Compute instances as principal actors required to grant access certain OCI resources using OCI policies.
- **Policies**: to provide access to some OCI resources for Compute instances defined in the OCI Dynamic Group. This project requires access to the Logging and Metrics resources. For example:

      ----
      Allow dynamic-group <My_Dynamic_Group> to use log-content in compartment <My_Compartment>
      Allow dynamic-group <My_Dynamic_Group> to use metrics in compartment <My_Compartment>
      ---

- **Compute Instance**: to host the deployed application. Open port `8080` in the firewall. The Helidon application is accessed from port 8080.
- **Virtual Cloud Network (VCN)**: With a `Security List` that contains an ingress security rule that opens port `8080`. The Helidon application is accessed from port 8080.
- **Log and Log Group Resources**: if you plan on using the Custom Logs service.

### Integrating with OCI Services

Before you can use OCI services, you must properly configure your application to run on OCI. The Helidon MP OCI Project Starter is already partially configured to integrate with several common OCI services but you must update its configuration with values specific to your application and OCI environment.

The Helidon OCI SDK extension also supports injecting clients for any [OCI services supported by the OCI SDK for Java](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm#Services_Supported). See [OCI Integration](../integrations/oci.md).

> [!NOTE]
> Whenever you integrate a new OCI service in your application, you must build and redeploy the application to enable the feature of that OCI service.

The [Deploying a Helidon OCI MP Application on a Basic OCI Setup](https://github.com/helidon-io/helidon-labs/blob/main/hols/oci-basic-setup/README.md) lab in the Helidon Labs GitHub repository includes a `update_config_values.sh` script , which updates the necessary values required to set up OCI Monitoring and OCI Logging. You can also read the applicable sections in `/server/README.md` for instructions.

#### Helidon MP Metrics to OCI Monitoring

The OCI Monitoring services allows you to actively and passively monitor cloud resources using the Metrics and Alarms features.

Use OCI Monitoring with your Helidon MP application to amass an abundance of valuable server and application insight and place it directly into OCI Monitoring where it can be analyzed as needed. For an overview of OCI Monitoring features, see [Monitoring](https://docs.oracle.com/en-us/iaas/Content/Monitoring/Concepts/monitoringoverview.htm) in OCI documentation.

To learn how to send Helidon MP metrics to OCI Monitoring, read the **Helidon MP Metrics to OCI Monitoring** section in `/server/README.md`.

#### Logging

The OCI Logging service is a highly scalable and fully managed single pane of glass for all of the logs in your tenancy. For an overview of the OCI Logging feature, see [Logging Overview](https://docs.oracle.com/en-us/iaas/Content/Logging/Concepts/loggingoverview.htm) in OCI documentation.

Helidon uses the [custom logs](https://docs.oracle.com/en-us/iaas/Content/Logging/Concepts/custom_logs.htm) feature of OCI Logging for integration. The ability to add custom logs from any OCI Compute instance makes it more flexible to use this feature with other components such as Oracle Kubernetes Engine (OKE).

To learn how to enable OCI Logging integration, read the **Custom Logs Monitoring** section in `/server/README.md`.

## Working with Other OCI Services

While the Helidon MP OCI Starter Project focuses on the OCI Monitoring and OCI Logging services, you can extend the project to use other OCI services, as demonstrated by the examples below.

### Streaming

The OCI Streaming service provides a fully managed, scalable, and durable solution for ingesting and consuming high-volume data streams in real time. See [Streaming](https://docs.oracle.com/en-us/iaas/Content/Streaming/Concepts/streamingoverview.htm) in OCI documentation.

The OCI Streaming service is compatible with Apache Kafka clients which means that when your Helidon applications are deployed in OCI environments, you can use either the OCI Streaming service or Apache Kafka. See [Using Streaming with Apache Kafka](https://docs.oracle.com/en-us/iaas/Content/Streaming/Tasks/kafkacompatibility.htm) in OCI documentation.

Streams are organized into logical groups called Stream Pools. When you connect to OCI Streaming with a Kafka client, streams are handled as Kafka topics and stream pools as virtual Kafka clusters.

To enable OCI Streaming integration, first you need to create a new stream in OCI. For instructions, see [Creating a Stream](https://docs.oracle.com/en-us/iaas/Content/Streaming/Tasks/creatingstreamsandstreampools_create-stream.htm) in OCI documentation.

After you have finished creating the stream, make a note of the following values:

- Stream name (on the Stream Details page)
- Messages endpoint (on the Stream Details page)
- OCID of the stream *pool* (on the Stream Pool Details page)

You can also click Kafka Connection Settings (on the Stream Pool Details page) to see an example of Kafka connection settings.

Next, add the following dependency to your project’s `pom.xml`:

*Adding the dependency for OCI Streaming*

``` xml
<dependency>
  <groupId>io.helidon.microprofile.messaging</groupId>
  <artifactId>helidon-microprofile-messaging</artifactId>
</dependency>
<dependency>
  <groupId>io.helidon.messaging.kafka</groupId>
  <artifactId>helidon-messaging-kafka</artifactId>
</dependency>
```

Next, update `/server/src/main/resources/application.yaml` with streaming connection properties:

*Example of an application.yaml configured for OCI Streaming*

``` yaml
oci:
 tenant: helidon-app-example 
 user: email@domain.com 
 token: oci-auth-token 
 test-stream: 
   name: TestStream
   endpoint: cell-1.streaming.us-phoenix-1.oci.oraclecloud.com
   port: 9092
   streampool-ocid: ocid1.streampool.oci1.phoenix.amaaaaaamevwycaap72ouurhfjrakuccakjpse5kenpkm5oikbgaadtq6byq
```

- The name of the OCI tenancy.
- The OCI account user name.
- The OCI authentication token. See [Getting an Auth Token](https://docs.oracle.com/en-us/iaas/Content/Registry/Tasks/registrygettingauthtoken.htm) in OCI documentation.
- The details of the stream which you saved earlier. The `port` should be the standard Kafka port number `9092`.

Then, still in `/server/src/main/resources/application.yaml`, configure messaging channels to use Helidon’s Kafka connector.

*Example of a configuration for the helidon-kafka connector*

``` yaml
mp.messaging:

  incoming.from-stream:
    connector: helidon-kafka
    topic: TestStream 
    auto.offset.reset: latest
    enable.auto.commit: true
    group.id: example-group-id 

  outgoing.to-stream:
    connector: helidon-kafka
    topic: TestStream 

  connector:
     helidon-kafka:
       bootstrap.servers: ocid1.streampool.oci1.phoenix.amaaaaaamevwycaap72ouurhfjrakuccakjpse5kenpkm5oikbgaadtq6byq:9092 
       sasl.mechanism: PLAIN
       security.protocol: SASL_SSL
       sasl.jaas.config:  >-
         org.apache.kafka.common.security.plain.PlainLoginModule
         required
         username="helidon-app-example/person@domain.com/ocid1.streampool.oci1.phoenix.amaaaaaamevwycaap72ouurhfjrakuccakjpse5kenpkm5oikbgaadtq6byq" 
         password="oci-auth-token"; 

       key.serializer: org.apache.kafka.common.serialization.StringSerializer
       value.serializer: org.apache.kafka.common.serialization.StringSerializer
       key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
       value.deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

- The OCI stream name (which should match the value that you defined earlier in application.yaml).
- The ID for the stream group
- Kafka client’s property [bootstrap.servers](https://kafka.apache.org/28/documentation.html#consumerconfigs_bootstrap.servers) configuration for all channels using the connector, using the following structure `<oci.test-stream.endpoint>:<oci.test-stream.port>`.
- A username in this structure: `<oci.tenant>/<oci.user>/<oci.test-stream.streampool-ocid>`.
- The OCI authentication token.

After you configure the `helidon-kafka` connector, you can use it on messaging channels to integrate with the OCI Streaming service.

### Tracing

The OCI Application Performance Monitoring (APM) service provides deep visibility into the performance of applications and provides the ability to diagnose issues quickly in order to deliver a consistent level of service. See [About APM](https://docs.oracle.com/en-us/iaas/application-performance-monitoring/doc/application-performance-monitoring.html) in OCI documentation.

Use OCI APM with Helidon to trace requests from start to finish, allowing you to analyze requests across your distributed services and thereby obtain a complete picture to identify bottlenecks and issues early on.

To enable OCI Tracing integration, start by creating an APM Domain, an OCI resource that contains the systems being monitored by APM. See [Create an APM Domain](https://docs.oracle.com/en-us/iaas/application-performance-monitoring/doc/create-apm-domain.html#GUID-ABC79A90-3940-4360-9E21-57D25B86F92B) in OCI documentation.

Make sure that you record the **Data Upload Endpoint** and **Data Keys** for the APM domain - you will need them to configure the APM Java Tracer on the application. If you need to find them again, follow the steps at [Obtain Data Upload Endpoint and Data Keys](https://docs.oracle.com/en-us/iaas/application-performance-monitoring/doc/obtain-data-upload-endpoint-and-data-keys.html) in OCI documentation.

Next, configure the APM Java Tracer to use with Helidon. The APM Java Tracer (or just APM Tracer) is an APM data source that gathers and uploads data such as metrics and spans for monitoring. See [Use APM Tracer in Helidon](https://docs.oracle.com/en-us/iaas/application-performance-monitoring/doc/use-apm-tracer-helidon.html) in OCI documentation.

After you configure OCI APM with Helidon, you can use OCI Trace Explorer to view traces and spans and identify performance issues in your monitored application. See [Monitor Traces in Trace Explorer](https://docs.oracle.com/en-us/iaas/application-performance-monitoring/doc/monitor-traces-trace-explorer.html#GUID-D23BCE18-F42F-44A1-B583-47DF9F9817D8) in OCI documentation.

> [!TIP]
> You can use OpenTelemetry for tracing and then ship the traces to OCI APM. Read the [Distributed Tracing in Helidon, Coherence and Oracle Autonomous Database with OpenTelemetry and OCI APM](https://medium.com/oracledevs/distributed-tracing-in-helidon-coherence-and-oracle-autonomous-database-with-opentelemetry-and-c60fe965f902) article on Medium.
