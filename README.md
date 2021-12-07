# Azure Function with Java - REST API Example

This is an example of a Java based Azure Function (to get started with it and get some experience). When using Java documentation is a bit thin (depending on what you're doing).

### ToDo
- [ ] add a proxy to server also a WebApp


### How to create a REST-API
- We use the `HttpTrigger`-binding to expose a HTTP based server
- for each trigger add explicitly a `route` that determines the uri-suffix
  - so you can define more than one function in a jar but make sure they don't interfere (by fn-name, route, method etc.)
- you can define path-parameters as usual by declaring 'em in the `route`
```java
    @FunctionName("loadAllTodos")   // must be unique in this function-app
    public HttpResponseMessage loadAllTodos(
            @HttpTrigger(name = "req", route = "todos", methods = { GET }, authLevel = ANONYMOUS) HttpRequestMessage<Optional<String>> request) { ... }

    @FunctionName("getTodoById")
    public HttpResponseMessage getTodoById(
        @HttpTrigger(name = "req", route = "todos/{id}", methods = { GET }, authLevel = ANONYMOUS) HttpRequestMessage<Optional<String>> request) { ... }
        
    ...
```
Followin' that pattern and starting the function app the output looks finally like this:
```bash
Functions:
        createTodo: [POST] http://localhost:7071/api/todos
        deleteTodo: [DELETE] http://localhost:7071/api/todos/{id}
        getTodoById: [GET] http://localhost:7071/api/todos/{id}
        loadAllTodos: [GET] http://localhost:7071/api/todos
        updateTodo: [PUT] http://localhost:7071/api/todos/{id}
```
which is pretty much what we try to achieve. 

### Integration with a Storage Account (Table Storage)
That integration had a pretty steep learning curve - starting simple and ending up with manual plumbing.

So, following [tutorials][blog] or the [reference pages from MSFT][msft-docs-java] it's pretty easy (once you got used to it) to do some simple use-cases like
- inserting a new value (see `TableOutput`-binding)
- reading in all values (see `TableInput`-binding)
- reading in a value by id (again `TableInput`-binding with some more props)

since all these use-cases can be done by only leveraging annotations that are part of the azure functions SDK. Just annotate your function properly - everything else is done by the mvn-plugin which generates the required `function.json` accordingly. So, for example in order to read from a Table you can define a `TableInput` like this:
```java
public HttpResponseMessage loadAllTodos(
    @HttpTrigger(name = "req", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
    @TableInput(name = "todos", tableName = "Todos", partitionKey = "TODOS", connection = "AzureWebJobsStorage") Todo[] todos,
    final ExecutionContext context) { ... }
```
- first, make sure the respective Table exists (otherwise you'll get an error)
- next, add the [proper connection string also to your [local.settings.json'][local-setting] so you can test that also locally (_you can simulate a StorageAccount (by using `"AzureWebJobsStorage": "UseDevelopmentStorage=true"`) only on Windows - not on Mac or Linux_)
```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "<ConnectionStringForTableAccount>",
    "FUNCTIONS_WORKER_RUNTIME": "java"
  }
}
```

More work is required when you'd like to update or delete entries ... since that is not doable via annotations. For that you'd have to:
- add `azure-storage` as further dependency which contains client-code for manual interaction with storage accounts
- get hands on the connection string which is passed into the function - don't use `@BindingName` for this but good old `System.getEnv()`-lookups 
- to make actual use of the `TableOperation`-class I had to introduce the usage of `TableEntity`-interface
  - you can use the abstract class `TableServiceEntity` instead to safe some boilerplate code
  - **Notice:** I had to stick to the .NETish naming-convention of starting class-member with a capital letter
    - did a bunch of tests (with getter and setter etc.) but then the retrieval of the `TodoEntity` failed since my members weren't filled with values (although the `TableResult`-object contained the respective props - but apparently wasn't able to map 'em to the members properly). I could only fix this by switching to the .NETish convention. 
- for an example see `TodoResource.updateTodo` or `TodoResource.deleteTodo` 

### Build and deploy
In order to use the [maven-plugin behind a proxy][mvn-plugin] (especially when that proxy requires a username/password) use system-properties like in this example: 
```bash
# build and run/test locally (Notice: depending on the value for 'AzureWebJobsStorage' 
# you might still write into the remote storage)
mvn clean package azure-functions:run

# build and deploy to azure
mvn clean package azure-functions:deploy \
  -Dhttps.proxyHost=http://proxy.muc \
  -Dhttps.proxyPort=8080 \
  -Dhttps.proxyUser=<user> \
  -Dhttps.proxyPassword=<pass>   
```
### Testing
```bash
# get all
curl -X GET localhost:7071/api/todos -s | jq

# create new entry
curl -X POST -d'{"description": "Buy more milk"}' http://localhost:7071/api/todos -s | jq

# get specific entry
curl -X GET localhost:7071/api/todos/e3f8ad97-b24b-48ad-a377-991c4f822b -s | jq

# update an entry (only description and completed is possible)
curl -X PUT -d '{"description":"Updated again!!!","completed":true}' localhost:7071/api/todos/e3f8ad97-b24b-48ad-a377-991c4f822b -v

# delete entry
curl -X DELETE localhost:7071/api/todos/e3f8ad97-b24b-48ad-a377-991c4f822b -s
```

[local-setting]:https://docs.microsoft.com/en-us/azure/azure-functions/functions-develop-local#local-settings-file
[msft-docs-java]:https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java?tabs=bash%2Cconsumption
[fn-api]:https://docs.microsoft.com/en-us/java/api/overview/azure/readme?view=azure-java-stable
[blog]:https://blog.nebrass.fr/playing-with-java-in-azure-functions-new-release/
[mvn-plugin]:https://github.com/microsoft/azure-maven-plugins/wiki
[fn-ref]:https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java