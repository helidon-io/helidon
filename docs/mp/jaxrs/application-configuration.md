# Configuring the Application

Your application can use the MicroProfile Config or Helidon Config (or both). MicroProfile Config offers portability to other MicroProfile servers. Helidon Config supports a full tree structure, including repeating elements.

## Configuring the Application

You can inject values that the application can access from both MicroProfile Config and from Helidon Config.

*Jakarta REST - inject a single config property*

```java
@Inject
public MyResource(@ConfigProperty(name = "app.name") String appName) {
    this.applicationName = appName;
}
```

You can also inject the whole configuration instance, either `io.helidon.config.Config` or `org.eclipse.microprofile.config.Config`.

*Jakarta REST - inject config*

```java
@Inject
public MyResource(Config config) {
    this.config = config;
}
```
