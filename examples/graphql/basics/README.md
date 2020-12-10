# Helidon GraphQL Basic Example

This example shows the basics of using Helidon SE GraphQL. The example
manually creates a GraphQL Schema using the [GraphQL Java](https://github.com/graphql-java/graphql-java) API.

## Build and run

Start the application:

```bash
mvn package
java -jar target/helidon-examples-graphql-basics.jar
```

Note the port number reported by the application.

Probe the GraphQL endpoints:

1. Hello word endpoint:

    ```bash
    curl -X POST http://127.0.0.1:PORT/graphql -d '{"query":"query { hello }"}'       
   
    "data":{"hello":"world"}}
    ```
     
1. Hello in different languages

    ```bash
    curl -X POST http://127.0.0.1:PORT/graphql -d '{"query":"query { helloInDifferentLanguages }"}'       
   
    {"data":{"helloInDifferentLanguages":["Bonjour","Hola","Zdravstvuyte","Nǐn hǎo","Salve","Gudday","Konnichiwa","Guten Tag"]}}
    ```  
