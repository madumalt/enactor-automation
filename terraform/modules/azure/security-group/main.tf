# Create a security group based on given configurations
# Azure can attach only single security group for a machine

variable "resource_group" { type = "map"}
variable "group_identifier" {}
variable "rules" {
  type = "list"
}
resource "azurerm_network_security_group" "default" {
  name                = "${var.resource_group["name"]}-${var.group_identifier}"
  location            = "${var.resource_group["location"]}"
  resource_group_name = "${var.resource_group["name"]}"

  tags {
      name = "${var.resource_group["name"]}-${var.group_identifier}"
  }
}

resource "azurerm_network_security_rule" "enactor_services_ports" {
  count                       = "${length(var.rules)}"

  name = "${var.group_identifier}-${count.index}"
  priority = "${lookup(var.rules[count.index], "priority","${(count.index * 10) + 100}")}"
  direction                   = "${lookup(var.rules[count.index], "direction","Inbound")}"
  access                      = "${lookup(var.rules[count.index], "access","Allow")}"
  protocol                    = "${lookup(var.rules[count.index], "protocol","Tcp")}"
  source_port_range           = "${lookup(var.rules[count.index], "source_port_range", "*")}" # requried
  destination_port_range      = "${lookup(var.rules[count.index], "port", "*")}" # requried
  source_address_prefix       = "${lookup(var.rules[count.index], "source_address_prefix", "*")}"
  destination_address_prefix  = "${lookup(var.rules[count.index], "destination_address_prefix", "*")}"
  resource_group_name         = "${var.resource_group["name"]}"
  network_security_group_name = "${azurerm_network_security_group.default.name}"
}

output "id" {
  value = "${azurerm_network_security_group.default.id}"
}

