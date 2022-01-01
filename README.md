# Azure Function with Java - REST API Example

This is an example of a Java based Azure Function (to get started with it and get some experience). When using Java documentation is a bit thin (depending on what you're doing).

What's contained:
- example of a RESTful API using standard Azure Functions Java SDK
- integration with a persistent storage (here Azure Storage Tables)
- usage of azure function proxy feature to mesh several offerings into one host (and circumvent CORS issues)

## How to create a REST-API
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

## Integration with a Storage Account (Table Storage)
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

### Insights after finishing the integration

Integration with Table-Storage is _only_ supported in a deprecated, outdated version of the SDK (see [Available packages](https://docs.microsoft.com/en-us/java/api/overview/azure/storage?view=azure-java-stable#available-packages) in the MSFT docs - only version 8 supports Table storage which is not even listed anymore in [SDK release page](https://azure.github.io/azure-sdk/releases/latest/java.html)).
So for a more real-life version of that ToDo API you'd rather integrate with a CosmosDB or one of the relational DB offerings. 

## Build and deploy
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

## Adding a Proxy to server a web-app

A quite convenient feature of function apps is the possibility to define a proxy - by that means you could i.e. serve a static web-app (hosted/deployed in a simple blob store and which relies on your REST API) without fiddling around with CORS-definitions etc. 
```bash
curl -X GET localhost:7071/api/todos      --directly--> { fn-json-output }    
curl -X GET localhost:7071/app/{resource} -proxied-to-> https://blobstore/container/resource 
```
So from the perspective of a caller both are served by the very same host.

### Configuration

For a general overview have a look at [this MSFT docs page][fn-proxies] - if you'd like to include that proxy definition in your source code (and not define it via the web-portal) you'd have to add a file called `proxies.json` in the root-directory of your staging-dir.

To craft the content of the `proxies.json`-file you can import the [respective json-schema][fn-proxies-schema] into your IDE and use code-completion to basically define:
- potentially several proxies
- that match by HTTP-Method and path
- that can augment/change requests as well as responses transparently

```json
{
  "$schema": "http://json.schemastore.org/proxies",
  "proxies": {
    "indexServingProxy": {
      "matchCondition": {
        "methods": [ "GET" ],
        "route": "/index.html"
      },
      "backendUri": "https://stfnwebapptest.z6.web.core.windows.net/index.html"
    },
    "webAppServingProxy": {
      "matchCondition": {
        "methods": [ "GET" ],
        "route": "/app/app.js"
      },
      "backendUri": "https://stfnwebapptest.z6.web.core.windows.net/app.js",
      "responseOverrides": {
        "response.headers.Content-Type": "application/javascript"
      }
    }
  }
}
```

### Build setup

The `proxies.json` file has to be included in the staging-directory that is used by the azure functions maven plugin. There is [no obvious option to include arbitrary resources when packaging](https://github.com/microsoft/azure-maven-plugins/wiki/Azure-Functions:-Configuration-Details) the function app nor is there an option to directly support the definition of a `proxies.json`-file.

To bypass those restrictions I used the [standard maven resources plugin](https://maven.apache.org/plugins/maven-resources-plugin/examples/copy-resources.html) to copy the `proxies.json` file over to the staging directory (by default that's `${project.basedir}/target/azure-functions/${function-app-name}/` - see [here](https://github.com/microsoft/azure-maven-plugins/wiki/Azure-Functions:-Package)) during `package`-phase

### Providing also a Web-UI
Since the purpose of this repo was to get familiar with azure functions I wasn't so keen on getting into the creation of a beautiful, full-function web-app. Hence, I just sketched things out a bit.

If you'd like to test the proxy and how a single-page works with that have a look into `fn-todo-gui` 

[local-setting]:https://docs.microsoft.com/en-us/azure/azure-functions/functions-develop-local#local-settings-file
[msft-docs-java]:https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java
[fn-api]:https://docs.microsoft.com/en-us/java/api/overview/azure/readme?view=azure-java-stable
[blog]:https://blog.nebrass.fr/playing-with-java-in-azure-functions-new-release/
[mvn-plugin]:https://github.com/microsoft/azure-maven-plugins/wiki
[fn-ref]:https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java
[fn-proxies]:https://docs.microsoft.com/en-us/azure/azure-functions/functions-proxies
[fn-proxies-schema]:http://json.schemastore.org/proxies