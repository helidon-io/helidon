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

## Issuing GraphQL requests 

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

     