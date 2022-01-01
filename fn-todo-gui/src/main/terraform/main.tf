resource "azurerm_resource_group" "storage_group" {
  location = var.location
  name     = var.resource-group-name
}

resource "azurerm_storage_account" "storage_account" {
  name                     = var.storage-account-name
  resource_group_name      = azurerm_resource_group.storage_group.name
  location                 = azurerm_resource_group.storage_group.location
  account_tier             = "Standard"
  account_replication_type = "ZRS"
  static_website {
    index_document         = var.index-document
  }

  tags = {
    environment = "test"
  }
}

resource "null_resource" "upload_content" {
  provisioner "local-exec" {
    command = <<-EOT
      az storage blob upload-batch --account-name ${azurerm_storage_account.storage_account.name} -d '$web' -s ../javascript
      EOT

    interpreter = ["bash", "-c"]
  }

}

output "webpage" {
  value = azurerm_storage_account.storage_account.primary_web_endpoint
}