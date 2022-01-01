variable "location" {
  type = string
  default = "westeurope"
}
variable "resource-group-name" {
  type = string
  default = "rg-fn-static-web-page-test"
}

variable "storage-account-name" {
  type = string
  default = "stfnwebuitest"
}
variable "index-document" {
  type = string
  default = "index.html"
}