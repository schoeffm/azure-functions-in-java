# Web App for Azure Function API

This is just a skeleton to get you started and to give you an idea how the app can interact with the Azure Functions API.

## Provision a static web app

To set things up go into `src/main/terraform` and provision a blob storage as static webpage:
```bash
terraform init
terraform plan -out storage.tfplan
terraform apply storage.tfplan
```
That'll create a (pretty standard) storage account and will upload the files in `src/main/javascript` to its `$web`-container (so they're ready to be served).

```bash
terraform output
webpage = "https://stfnwebuitest.z6.web.core.windows.net/"
```

The single output `webpage` is the URI to be used in the `proxies.json`-file of the azure function.

## Web App

The app only renders the current list of todos and supports the creation of new todos ... that's it for the moment since it wasn't the focus to craft a decent Web App. But I guess it's enough to give you an idea how a web-app can be integrated (using the proxy feature). 