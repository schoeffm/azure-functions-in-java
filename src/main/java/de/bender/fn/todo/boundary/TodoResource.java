package de.bender.fn.todo.boundary;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.table.*;
import de.bender.fn.todo.model.TodoEntity;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.*;
import java.util.logging.Level;

/**
 * Azure Functions with HTTP Trigger.
 */
public class TodoResource {

    @FunctionName("loadAllTodos")
    public HttpResponseMessage loadAllTodos(
            @HttpTrigger(name = "req", route = "todos", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @TableInput(name = "todos", tableName = "Todos", partitionKey = "TODOS", connection = "AzureWebJobsStorage") TodoEntity[] todos,
            final ExecutionContext ctx) {
        ctx.getLogger().info("Java HTTP trigger processed a request.");

        return request
                .createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(Arrays.stream(todos)
                        .map(this::toOutputJson)
                        .collect(JSONArray::new, JSONArray::put, JSONArray::putAll).toString())
                .build();
    }

    @FunctionName("getTodoById")
    public HttpResponseMessage getTodoById(
            @HttpTrigger(name = "req", route = "todos/{id}", methods = { HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @TableInput(name = "todos", tableName = "Todos", rowKey = "{id}", partitionKey = "TODOS", connection = "AzureWebJobsStorage") TodoEntity todo,
            final ExecutionContext ctx) {
        if (Objects.isNull(todo)) {
            return request
                    .createResponseBuilder(HttpStatus.NOT_FOUND)
                    .build();
        } else {
            return request
                    .createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(toOutputJson(todo).toString())
                    .build();
        }
    }

    @FunctionName("createTodo")
    public HttpResponseMessage createTodo(
            @HttpTrigger(name = "req", route = "todos", methods = { HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @TableOutput(name = "todos", tableName = "Todos", partitionKey = "TODOS", connection = "AzureWebJobsStorage") OutputBinding<TodoEntity> table,
            final ExecutionContext ctx) {
        ctx.getLogger().info("Java HTTP trigger processed a request.");

        Optional<TodoEntity> todo = request.getBody()
                .map(this::toTodo);

        if (todo.isPresent()) {
            table.setValue(todo.get());
            return request
                    .createResponseBuilder(HttpStatus.CREATED)
                    .body(todo.get().getRowKey())
                    .build();
        } else {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    @FunctionName("updateTodo")
    public HttpResponseMessage updateTodo(
            @HttpTrigger(name = "req", route = "todos/{id}", methods = { HttpMethod.PUT }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext ctx) {
        // first, if there's no body we cannot update anything
        if (request.getBody().isEmpty()) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(new JSONObject().put("err", "Request body is missing!"))
                    .build();
        }

        try {
            // now, no binding for CloudTable available - so we have to set that up manually
            CloudStorageAccount storageAccount = CloudStorageAccount.parse(System.getenv("AzureWebJobsStorage"));
            CloudTableClient cloudTableClient = storageAccount.createCloudTableClient();
            CloudTable todos = cloudTableClient.getTableReference("Todos");
            TableResult todo = todos.execute(TableOperation.retrieve("TODOS", id, TodoEntity.class));

            // if we couldn't find the entry ...
            if (Objects.isNull(todo.getResult())) {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND).build();
            }

            // since we have to update an existing entry (eTag must be there etc.) we first retrieve the entry
            // and overwrite those props that can actually be updated (Notice: it's suuuper important that the props
            // of the entity are in .NET notation with a capital first letter - otherwise the internals of the mapping
            // go crazy where you cannot retrieve a filled 'TodoEntity' object (but would have to read the props from
            // a Map))
            JSONObject jsonObject = new JSONObject(request.getBody().get());
            TodoEntity toBeUpdated = todo.getResultAsType();
            toBeUpdated.setDescription(jsonObject.getString("description"));
            toBeUpdated.setCompleted(jsonObject.has("completed") && jsonObject.getBoolean("completed"));

            // once the entity is prepared we can issue the TableOperation.replace call
            todos.execute(TableOperation.replace(toBeUpdated));

            // let the caller know that it was successful (we could even check the outcome of the execute-call)
            return request
                    .createResponseBuilder(HttpStatus.OK)
                    .body(this.toOutputJson(toBeUpdated))
                    .build();
        } catch (URISyntaxException | InvalidKeyException | StorageException ex) {
            ctx.getLogger().log(Level.SEVERE, "Couldn't update Table entry " + id, ex);
        }
        return request
                .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .build();
    }

    @FunctionName("deleteTodo")
    public HttpResponseMessage deleteTodo(
            @HttpTrigger(name = "req", route = "todos/{id}", methods = { HttpMethod.DELETE }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext ctx) {
        try {
            // now, no binding for CloudTable available - so we have to set that up manually
            CloudStorageAccount storageAccount = CloudStorageAccount.parse(System.getenv("AzureWebJobsStorage"));
            CloudTableClient cloudTableClient = storageAccount.createCloudTableClient();
            CloudTable todos = cloudTableClient.getTableReference("Todos");
            TableResult todo = todos.execute(TableOperation.retrieve("TODOS", id, TodoEntity.class));

            if (Objects.isNull(todo.getResult())) {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND).build();
            }
            TodoEntity toBeDeleted = todo.getResultAsType();
            todos.execute(TableOperation.delete(toBeDeleted));

            // let the caller know that it was successful (we could even check the outcome of the execute-call)
            return request
                    .createResponseBuilder(HttpStatus.OK)
                    .body(toBeDeleted.getRowKey())
                    .build();
        } catch (URISyntaxException | InvalidKeyException | StorageException ex) {
            ctx.getLogger().log(Level.SEVERE, "Couldn't delete Table entry " + id, ex);
        }
        return request
                .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .build();
    }

    // some converter functions

    private TodoEntity toTodo(String jsonAsString) {
        JSONObject jsonObject = new JSONObject(jsonAsString);
        TodoEntity result = new TodoEntity("TODOS", UUID.randomUUID().toString());
        result.setDescription(jsonObject.getString("description"));
        result.setCompleted(jsonObject.has("completed") && jsonObject.getBoolean("completed"));
        return result;
    }

    private JSONObject toOutputJson(TodoEntity todo) {
        return new JSONObject()
                .put("completed", todo.getCompleted())
                .put("description", todo.getDescription())
                .put("createdAt", todo.getTimestamp())
                .put("id", todo.getRowKey()); // that's so weird - the prop is filled, the API-field is null?!
    }

}
