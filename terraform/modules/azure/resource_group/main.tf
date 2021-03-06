# All Azure resources created through terraform will be grouped under this. 
# Resource group name is derived as '${customer_name}-${environment_name}'

variable "location" {}
variable "resource_identifier" {}

resource "azurerm_resource_group" "main" {
  name     = "${var.resource_identifier}" 
  location = "${var.location}"
}

output "info" {
  value = "${
    map(
      "location", "${azurerm_resource_group.main.location}",
      "name", "${azurerm_resource_group.main.name}"
    )
  }"
}