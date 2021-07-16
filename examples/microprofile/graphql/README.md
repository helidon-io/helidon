# Microprofile GraphQL Example

This example creates a simple Task API using Helidon's implementation of the Microprofile GraphQL API Specification.

See [here](https://github.com/eclipse/microprofile-graphql) for more information on the 
Microprofile GraphQL Specification as well as the [Helidon documentation](https://helidon.io/docs/v2/#/mp/introduction/01_introduction)
for an introduction to using GraphQL in Helidon MP.

## Running the example

1. Build

```bash
mvn clean install
```              

2. Run the example

```bash
java -jar target/helidon-examples-microprofile-graphql.jar
```

## Issuing GraphQL requests via REST

Access the `/graphql` endpoint via `http://127.0.0.1:7001/graphql`:

1. Display the generated GraphQL Schema

    ```bash
    curl http://127.0.0.1:7001/graphql/schema.graphql
    ```       
   
   This will produce the following:
   
    ```graphql
    type Mutation {
      "Create a task with the given description"
      createTask(description: String!): Task
      "Remove all completed tasks and return the tasks left"
      deleteCompletedTasks: [Task]
      "Delete a task and return the deleted task details"
      deleteTask(id: String!): Task
      "Update a task"
      updateTask(completed: Boolean, description: String, id: String!): Task
    }
    
    type Query {
      "Return a given task"
      findTask(id: String!): Task
      "Query tasks and optionally specified only completed"
      tasks(completed: Boolean): [Task]
    }
    
    type Task {
      completed: Boolean!
      createdAt: BigInteger!
      description: String
      id: String
    }
    
    "Custom: Built-in java.math.BigInteger"
    scalar BigInteger
    ``` 
   
1. Create a Task

    ```bash
    curl -X POST http://127.0.0.1:7001/graphql -d '{"query":"mutation createTask { createTask(description: \"Task Description 1\") { id description createdAt completed }}"}'    
    ```   
   
    Response is a newly created task:
    
    ```json 
    {"data":{"createTask":{"id":"0d4a8d","description":"Task Description 1","createdAt":1605501774877,"completed":false}}
    ```  

## Accessing Metrics

In [TaskApi.java](src/main/java/io/helidon/examples/graphql/basics/TaskApi.java), the [Microprofile Metrics](https://github.com/eclipse/microprofile-metrics)
annotation`@Timed` has been added to queries and mutations. If you access the metrics endpoint at http://127.0.0.1:7001/metrics you 
will see the metrics available.

```bash
$ curl http://127.0.0.1:7001/metrics

# TYPE application_io_helidon_examples_graphql_basics_TaskApi_updateTask_rate_per_second gauge
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_rate_per_second 0.0
# TYPE application_io_helidon_examples_graphql_basics_TaskApi_updateTask_one_min_rate_per_second gauge
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_one_min_rate_per_second 0.0
# TYPE application_io_helidon_examples_graphql_basics_TaskApi_updateTask_five_min_rate_per_second gauge
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_five_min_rate_per_second 0.0
# TYPE application_io_helidon_examples_graphql_basics_TaskApi_updateTask_fifteen_min_rate_per_second gauge
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_fifteen_min_rate_per_second 0.0
# TYPE application_io_helidon_examples_graphql_basics_TaskApi_updateTask_mean_seconds gauge
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_mean_seconds 0.0
# TYPE application_io_helidon_examples_graphql_basics_TaskApi_updateTask_max_seconds gauge
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_max_seconds 0.0
# TYPE application_io_helidon_examples_graphql_basics_TaskApi_updateTask_min_seconds gauge
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_min_seconds 0.0
# TYPE application_io_helidon_examples_graphql_basics_TaskApi_updateTask_stddev_seconds gauge
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_stddev_seconds 0.0
# TYPE application_io_helidon_examples_graphql_basics_TaskApi_updateTask_seconds summary
# HELP application_io_helidon_examples_graphql_basics_TaskApi_updateTask_seconds
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_seconds_count 0
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_seconds{quantile="0.5"} 0.0
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_seconds{quantile="0.75"} 0.0
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_seconds{quantile="0.95"} 0.0
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_seconds{quantile="0.98"} 0.0
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_seconds{quantile="0.99"} 0.0
application_io_helidon_examples_graphql_basics_TaskApi_updateTask_seconds{quantile="0.999"} 0.0
```

> Note: Output above has been truncated for brevity

## Incorporating the GraphiQL UI

The [GraphiQL UI](https://github.com/graphql/graphiql), which provides a UI to execute GraphQL commands, is not included by default in Helidon's Microprofile GraphQL 
implementation. You can follow the guide below to incorporate the UI into this example:

1. Add the following contents to the sample `examples/microprofile/graphql/src/main/resources/web/index.html` file, which has been included below from [here](https://github.com/graphql/graphiql/blob/main/packages/graphiql/README.md)
for convenience. 

   ```html
   <html>
     <head>
       <title>Simple GraphiQL Example</title>
       <link href="https://unpkg.com/graphiql/graphiql.min.css" rel="stylesheet" />
     </head>
     <body style="margin: 0;">
       <div id="graphiql" style="height: 100vh;"></div>

       <script
         crossorigin
         src="https://unpkg.com/react/umd/react.production.min.js"
       ></script>
       <script
         crossorigin
         src="https://unpkg.com/react-dom/umd/react-dom.production.min.js"
       ></script>
       <script
         crossorigin
         src="https://unpkg.com/graphiql/graphiql.min.js"
       ></script>

       <script>
         const graphQLFetcher = graphQLParams =>
           fetch('http://127.0.0.1:7001/graphql', {
             method: 'post',
             headers: { 'Content-Type': 'application/json' },
             body: JSON.stringify(graphQLParams),
           })
             .then(response => response.json())
             .catch(() => response.text());
         ReactDOM.render(
           React.createElement(GraphiQL, { fetcher: graphQLFetcher }),
           document.getElementById('graphiql'),
         );
       </script>
     </body>
   </html>
   ```

   > Note: If you copy the original file, change the URL in the line `fetch('https://my/graphql', {` to `http://127.0.0.1:7001/graphql`

1. Build and run the example using the instructions above.

1. Access the GraphiQL UI via the following URL: http://127.0.0.1:7001/ui.

2. Copy the following commands into the editor on the left.  

    ```graphql 
    # Fragment to allow shorcut to display all fields for a task
    fragment task on Task {
      id
      description
      createdAt
      completed
    }
    
    # Create a task
    mutation createTask {
      createTask(description: "Task Description 1") {
        ...task
      }
    }
    
    # Find all the tasks
    query findAllTasks {
      tasks {
        ...task
      }
    }
    
    # Find a task
    query findTask {
      findTask(id: "251474") {
        ...task
      }
    }
    
    # Find completed Tasks
    query findCompletedTasks {
      tasks(completed: true) {
        ...task
      }
    }
    
    # Find outstanding Tasks
    query findOutstandingTasks {
      tasks(completed: false) {
        ...task
      }
    }
    
    mutation updateTask {
      updateTask(id: "251474" description:"New Description") {
        ...task
      }
    }
    
    mutation completeTask {
      updateTask(id: "251474" completed:true) {
        ...task
      } 
    }
    
    # Delete a task
    mutation deleteTask {
      deleteTask(id: "1f6ae5") {
        ...task
      }
    }
    
    # Delete completed
    mutation deleteCompleted {
      deleteCompletedTasks {
        ...task
      }
    }
    ```
   
3. Run individual commands by clicking on the `Play` button and choosing the query or mutation to run.

4. Sample requests

   1. Execute `createTask` 
   2. Change the description and execute `createTask`
   3. Execute `findTask` to show the exception when a task does not exist
   3. Change the id and execute `findTask` to show your newly created task
   5. Execute `findAllTasks` to show the 2 tasks
   6. Change the id and execute `updateTask` to update the existing task
   7. Change the id and execute `completeTask`
   8. Execute `findAllTasks` to show the task completed
   9. Execute `findCompletedTasks` to show only completed tasks
   10. Execute `deleteCompleted` to delete completed task
   11. Execute `findCompletedTasks` to show no completed tasks

     
