# Microprofile Cloud Proposal

## Proposal

The API integrates Helidon with the next different Cloud Function providers.
1. Google Cloud Functions
2. Microsoft Azure Functions
3. AWS Lambda Functions
4. OCI FN

The user needs to identify the function that he wants to deploy in the cloud. Normally this function is specified as a command line argument when the application is deployed.

Function classes are implementations of cloud provider interfaces (note these are not implementations of java.util.Function). These functions are instanced and executed by the cloud, so we need to start Helidon to make sure the user can make use of it.

## Helidon functions

There are the functions that the cloud will instantiate and invoke.

They have to start Helidon and prepare the user function. There is one Helidon function for each type of cloud interface.

## Common

This section is the core of every cloud provider.

### CommonCloudFunction<T>

This is an abstract class that all the other Helidon functions will extend. The purpose of this class is:

1. Start Helidon (Main.main(new String[0])) the first time the function is invoked.
2. Instance the user function from the annotated @CloudFunction taking care of dependencies. This refers to <T>.

## Google Cloud Functions

It is possible to upload a Maven project and it is required to specify an entry point. The entry point must be one of these:
 - io.helidon.microprofile.cloud.googlecloudfunctions.http.GoogleCloudHttpFunction
 - io.helidon.microprofile.cloud.googlecloudfunctions.background.GoogleCloudBackgroundFunction

### GoogleCloudHttpFunction extends CommonCloudFunction<HttpFunction> implements HttpFunction

User function requirements:
 - Implements com.google.cloud.functions.HttpFunction.
 - Can be instanced by CDI container.
 - It is annotated with @CloudFunction

Entry point: io.helidon.microprofile.cloud.googlecloudfunctions.http.GoogleCloudHttpFunction

### GoogleCloudBackgroundFunction<T> extends CommonCloudFunction<BackgroundFunction<T>> implements RawBackgroundFunction

User function requirements:
 - Implements com.google.cloud.functions.BackgroundFunction.
 - Can be instanced by CDI container.
 - It is annotated with @CloudFunction

Entry point: io.helidon.microprofile.cloud.googlecloudfunctions.background.GoogleCloudBackgroundFunction

## Microsoft Azure Functions

Azure works different than Google, AWS and OCI. There is no entry point to specify because Azure will discover Azure annotations.

This is one problem, because we need to initialize Helidon CDI before the request reaches the user code.

### AzureCloudFunction<I, O> extends CommonCloudFunction<AzureEmptyFunction>

The user will need to extend AzureCloudFunction class instead of annotating the function with @CloudFunction.

Then he can declare the Azure function on this extension, but Helidon is not initialized at this point. So he will need to:

1. Invoke the super method handleRequest in the Azure function method.
2. Implement the abstract method 'abstract O execute(I input, ExecutionContext context)'. Here is where Helidon is initialized.

## AWS Lambda Functions

In AWS an unique jar file needs to be uploaded, containing the function. This is a problem because some resources could be overwritten when the fat jar is created.

The entry point must be one of these:
 - io.helidon.microprofile.cloud.awslambda.request.AWSLambdaRequestFunction
 - io.helidon.microprofile.cloud.awslambda.stream.AWSLambdaStreamFunction

### AWSLambdaRequestFunction<I, O> extends CommonCloudFunction<RequestHandler<I, O>> implements RequestHandler<I, O>

User function requirements:
 - Implements com.amazonaws.services.lambda.runtime.RequestHandler.
 - Can be instanced by CDI container.
 - It is annotated with @CloudFunction

Entry point: io.helidon.microprofile.cloud.awslambda.request.AWSLambdaRequestFunction

### AWSLambdaStreamFunction extends CommonCloudFunction<RequestStreamHandler> implements RequestStreamHandler

User function requirements:
 - Implements com.amazonaws.services.lambda.runtime.RequestStreamHandler.
 - Can be instanced by CDI container.
 - It is annotated with @CloudFunction

Entry point: io.helidon.microprofile.cloud.awslambda.stream.AWSLambdaStreamFunction

## OCI FN

OCI is based on FN. The function needs to be specified in a func.yaml.

OCI reads the input and output parameters of the function in runtime, so it is not able to know the type of the generic types.

### OCIFunction<I, O> extends CommonCloudFunction<Function<I, O>> implements Function<I, O>

User function requirements:
 - Implements java.util.Function.
 - Can be instanced by CDI container.
 - It is annotated with @CloudFunction

OCIFunction cannot be specified as entry point because in runtime it is not possible to obtain the types of the input and output.

The user needs to create the entry point by his own, extending OCIFunction and declaring the types.

For example, if the user's function input and output are String, he will need to create the class:

```
package user.custom.function;

public class StringTypedOCIFunction extends OCIFunction<String, String> {}
```
And then specify in the func.yaml:

```
cmd: user.custom.function.StringTypedOCIFunction::apply
```
