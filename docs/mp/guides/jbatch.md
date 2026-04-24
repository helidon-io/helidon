# Helidon with JBatch Guide

This guide describes how Helidon and Jakarta Batch (JBatch) can be used together to execute batch jobs in environments that do not fully support EE environments.

## What You Need

For this 20 minute tutorial, you will need the following:

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../../about/kubernetes.md)). |

Prerequisite product versions for Helidon 4.4.0-SNAPSHOT

*Verify Prerequisites*

```bash
java -version
mvn --version
docker --version
kubectl version
```

*Setting JAVA_HOME*

```bash
# On Mac
export JAVA_HOME=`/usr/libexec/java_home -v 21`

# On Linux
# Use the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-21
```

> [!NOTE]
> This guide assumes you are familiar with the [Jakarta Batch project specification](https://projects.eclipse.org/projects/ee4j.batch) from the Eclipse Foundation project site.

## Dependencies

For this example, add the IBM JBatch implementation and the `derby` embedded DB (since JPA and JPA are not available by default) dependencies to the testing module:

*Maven dependencies*

```xml
<dependencies>
    <dependency>
        <groupId>com.ibm.jbatch</groupId>
        <artifactId>com.ibm.jbatch.container</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.derby</groupId>
        <artifactId>derby</artifactId>
    </dependency>
</dependencies>
```

## Add Sample Jobs

In this demonstration you will first create sample input and output records and then the following jobs:

- `MyItemReader`
- `MyItemProcessor`
- `MyItemWriter`

Finally, you will create `MyBatchlet` to demonstrate all possible usages of JBatch.

### 1. Create a unit of input information

*MyInputRecord*

```java
public class MyInputRecord {
    private int id;

    public MyInputRecord(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "MyInputRecord: " + id;
    }
}
```

#### 2. Create a unit of output information

*MyOutputRecord*

```java
public class MyOutputRecord {

    private int id;

    public MyOutputRecord(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "MyOutputRecord: " + id;
    }
}
```

#### 3. Create `MyItemReader` to extend `AbstractItemReader`

`MyItemReader` should look like this:

*MyItemReader*

```java
public class MyItemReader extends AbstractItemReader {

    private final StringTokenizer tokens;

    public MyItemReader() {
        tokens = new StringTokenizer("1,2,3,4,5,6,7,8,9,10", ",");
    }

    /**
     * Perform read Item.
     *
     * @return Stage result.
     */
    @Override
    public MyInputRecord readItem() {
        if (tokens.hasMoreTokens()) {
            return new MyInputRecord(Integer.valueOf(tokens.nextToken()));
        }
        return null;
    }
}
```

#### 4. Create `MyItemProcessor` to implement `ItemProcessor`

The `MyItemProcessor` will perform some simple operations:

*MyItemProcessor*

```java
public class MyItemProcessor implements ItemProcessor {

    @Override
    public MyOutputRecord processItem(Object t) {
        System.out.println("processItem: " + t);

        return (((MyInputRecord) t).getId() % 2 == 0) ? null : new MyOutputRecord(((MyInputRecord) t).getId() * 2);
    }
}
```

#### 5. Create `MyItemWriter` to extend `AbstractItemWriter`

`MyItemWriter` prints the result:

*MyItemWriter*

```java
public class MyItemWriter extends AbstractItemWriter {

    @Override
    public void writeItems(List list) {
        System.out.println("writeItems: " + list);
    }
}
```

#### 6. Create `MyBatchlet` to extend `AbstractBatchlet`

`MyBatchlet` simply completes the process:

*MyBatchlet*

```java
public class MyBatchlet extends AbstractBatchlet {

    @Override
    public String process() {
        System.out.println("Running inside a batchlet");

        return "COMPLETED";
    }

}
```

## Update the Descriptor File

Add this code to your job descriptor.xml file:

*Updated descriptor file*

```xml
<job id="myJob" xmlns="https://jakarta.ee/xml/ns/jakartaee"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/jobXML_2_0.xsd"
                version="2.0">
    <step id="step1" next="step2">
        <chunk item-count="3"> 
            <reader ref="io.helidon.examples.jbatch.jobs.MyItemReader"/>
            <processor ref="io.helidon.examples.jbatch.jobs.MyItemProcessor"/>
            <writer ref="io.helidon.examples.jbatch.jobs.MyItemWriter"/>
        </chunk>
    </step>
    <step id="step2"> 
        <batchlet ref="io.helidon.examples.jbatch.jobs.MyBatchlet"/>
    </step>
</job>
```

- The first step of the job includes `MyItemReader`, `MyItemProcessor` and `MyItemWriter`.
- The second step of the job includes `MyBatchlet`.

> [!NOTE]
> You must specify the fully qualified names in the `ref` properties, like “jobs.io.helidon.examples.jbatch.MyItemReader”, otherwise it will not work.

## Create an Endpoint

Create a small endpoint to activate the job:

*new endpoint*

```java
@Path("/batch")
@ApplicationScoped
public class BatchResource {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    private JobOperator jobOperator;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject executeBatch() {

        BatchSPIManager batchSPIManager = BatchSPIManager.getInstance();
        batchSPIManager.registerPlatformMode(BatchSPIManager.PlatformMode.SE);
        batchSPIManager.registerExecutorServiceProvider(new HelidonExecutorServiceProvider());

        jobOperator = getJobOperator();
        Long executionId = jobOperator.start("myJob", new Properties());

        return JSON.createObjectBuilder()
                .add("Started a job with Execution ID: ", executionId)
                .build();
    }

    @GET
    @Path("/status/{execution-id}")
    public JsonObject status(@PathParam("execution-id") Long executionId) {
        JobExecution jobExecution = jobOperator.getJobExecution(executionId);

        List<StepExecution> stepExecutions = jobOperator.getStepExecutions(executionId);
        List<String> executedSteps = new ArrayList<>();
        for (StepExecution stepExecution : stepExecutions) {
            executedSteps.add(stepExecution.getStepName());
        }

        return JSON.createObjectBuilder()
                .add("Steps executed", Arrays.toString(executedSteps.toArray()))
                .add("Status", jobExecution.getBatchStatus().toString())
                .build();
    }
}
```

Helidon specifies to JBatch that it should run in Standalone (SE) mode. It will also register the `HelidonExecutorServiceProvider` which is actually relatively small. For our example we need something quite small, like a `FixedTheadPool` with 2 threads. This provider is used to tell our JBatch engine exactly which ExecutorService to use.

*HelidonExecutorServiceProvider*

```java
public class HelidonExecutorServiceProvider implements ExecutorServiceProvider {
    @Override
    public ExecutorService getExecutorService() {
        return ThreadPoolSupplier.builder().corePoolSize(2).build().get();
    }
}
```

## Run the Code

```bash
mvn package
java -jar target/helidon-jbatch-example.jar
```

## Call the Endpoint

```bash
curl -X GET http://localhost:8080/batch
```

You should receive the following log:

```bash
processItem: MyInputRecord: 1
processItem: MyInputRecord: 2
processItem: MyInputRecord: 3
writeItems: [MyOutputRecord: 2, MyOutputRecord: 6]
processItem: MyInputRecord: 4
processItem: MyInputRecord: 5
processItem: MyInputRecord: 6
writeItems: [MyOutputRecord: 10]
processItem: MyInputRecord: 7
processItem: MyInputRecord: 8
processItem: MyInputRecord: 9
writeItems: [MyOutputRecord: 14, MyOutputRecord: 18]
processItem: MyInputRecord: 10
Running inside a batchlet
```

and the following result:

```bash
{"Started a job with Execution ID: ":1}
```

This indicates that the batch job was called and executed successfully.

### Check the Status

```bash
curl -X GET http://localhost:8080/batch/status/1
```

> [!NOTE]
> In this example the job ID is 1, but make sure that you enter your specific job ID in the string.

The results should look something like this:

```bash
{"Steps executed":"[step1, step2]","Status":"COMPLETED"}
```

## Summary

This guide demonstrated how to use Helidon with JBatch even though Helidon is not a full EE container.
