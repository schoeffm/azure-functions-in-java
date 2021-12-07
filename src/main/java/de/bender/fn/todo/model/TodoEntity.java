package de.bender.fn.todo.model;

import com.microsoft.azure.storage.table.TableServiceEntity;

public class TodoEntity extends TableServiceEntity {
    private String RowKey;
    private String PartitionKey;
    private Boolean Completed;
    private String Description;

    public TodoEntity() {
        super();
    }

    public TodoEntity(String partitionKey, String rowKey) {
        this.RowKey = rowKey;
        this.PartitionKey = partitionKey;
    }


    @Override
    public String getRowKey() {
        return RowKey;
    }

    @Override
    public void setRowKey(String rowKey) {
        RowKey = rowKey;
    }

    @Override
    public String getPartitionKey() {
        return PartitionKey;
    }

    @Override
    public void setPartitionKey(String partitionKey) {
        PartitionKey = partitionKey;
    }

    public Boolean getCompleted() {
        return Completed;
    }

    public void setCompleted(Boolean completed) {
        this.Completed = completed;
    }

    public String getDescription() {
        return Description;
    }

    public void setDescription(String description) {
        this.Description = description;
    }
}
