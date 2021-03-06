# Create a new VPC

variable "vpc_identifier" {}
variable "resource_group" { 
  type = "map"
}
variable "address_range" {}

resource "azurerm_virtual_network" "main" {
  name                = "${var.resource_group["name"]}-${var.vpc_identifier}"
  address_space       = ["${var.address_range}"]
  location            = "${var.resource_group["location"]}"
  resource_group_name = "${var.resource_group["name"]}"

  tags = {
    name = "${var.resource_group["name"]}-${var.vpc_identifier}"
    group = "${var.resource_group["name"]}"
  }
}

output "name" { 
  value = "${azurerm_virtual_network.main.name}"
}
