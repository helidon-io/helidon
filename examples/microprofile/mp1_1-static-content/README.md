
# Helidon MP with Static Content

This example has a simple Hello World rest enpoint, plus
static content that is loaded from the application's classpath.
The configuration for the static content is in the
`microprofile-config.properties` file.

## Build

```
mvn package
```

## Run

```
mvn exec:java
```

## Endpoints

|Endpoint    |Description      |
|:-----------|:----------------|
|`helloworld`|Rest enpoint providing a link to the static content|
|`resource.html`|The static content|

