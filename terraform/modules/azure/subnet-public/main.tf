#--------------------------------------------------------------
# This module creates all resources necessary for a public
# subnet
#--------------------------------------------------------------

variable "subnet_identifier"   { default = "public" }
variable "resource_group" { type="map"}
variable "vpc_identifier" {}
variable "address_range"  {}

resource "azurerm_subnet" "public" {
  name                 = "${var.resource_group["name"]}-${var.subnet_identifier}"
  resource_group_name  = "${var.resource_group["name"]}"
  virtual_network_name = "${var.vpc_identifier}"
  address_prefix       = "${var.address_range}"
}

output "id" { 
  value = "${azurerm_subnet.public.id}"
}
