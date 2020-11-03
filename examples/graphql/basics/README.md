# Basic Microprofile GraphQL Example

This example creates a simple Task APU using the Microprofile GraphQL API.


## Running the example

1. Build the example

```bash
mvn clean install
```              

2. Run the example

```bash
mvn exec:java
```                

A Message will be displayed indicating the GraphiQL UI URL.

## Issuing GraphQL Requests 

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
    
    # Find all the tasks
    query findAllTasks {
      tasks {
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
    
    # Create a task
    mutation createTask {
      createTask(description: "Task Description 2") {
        ...task
      }
    }
    
    mutation updateTask {
      updateTask(id: "1f6ae5" description:"New Description") {
        ...task
      }
    }
    
    mutation completeTask {
      updateTask(id: "1f6ae5" completed:true) {
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
   3. Execute `findAllTasks` to show the 2 tasks
   4. Change the id and execute `updateTask` to update the existing task
   5. Change the id and execute `completeTask`
   6. Execute `findAllTasks` to show the task completed
   7. Execute `findCompletedTasks` to show only completed tasks
   8. Execute `deleteCompleted` to delete completed task
   9. Execute `findCompletedTasks` to show no completed tasks

     