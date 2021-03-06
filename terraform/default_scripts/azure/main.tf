variable "subscription_id" {  }
variable "client_id" {  }
variable "client_secret" {  }
variable "tenant_id" {  }
variable "region" { }

provider "local" {
  version = "1.1.0"
}

provider "template" {
  version = "2.0.0"
}

provider "random" {
  version = "2.0.0"
}

terraform {
  required_version = "0.11.11"
}

provider "azurerm" {
  version         = "1.23.0" 
  subscription_id = "${var.subscription_id}"
  client_id       = "${var.client_id}"
  client_secret   = "${var.client_secret}"
  tenant_id       = "${var.tenant_id}"
}
