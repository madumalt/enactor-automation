# Create single AZURE LINUX host machine based on given configurations

variable "resource_group" { type = "map"}
variable "host_os_version" { default = "Canonical,UbuntuServer,18.04-LTS" }
variable "host_size"   { default = "Standard_B1s" }
variable "execute_command" {
  default = "uname -a"
}

variable "disk_size"   { default = "30" }
variable "host_label"  { default = "leader" }

variable "security_group_id" { }
variable "subnet_identifier" { }
variable "uptime" { default = "" }
variable "password" { }
variable "username" { default = "ubuntu" }

variable "tags" {
  type = "map"
  default = {}
}

variable "shutdown_schedule" {
  default = ""
}

variable "inventory_variables" { 
  default = {
    allow_service_deployment = "true"
    ansible_python_interpreter = "/usr/bin/python3"
  }
}



locals {
 # os detials splited
  os_publisher = "${element(split(",", var.host_os_version), 0)}"
  os_offer     = "${element(split(",", var.host_os_version), 1)}"
  os_sku       =  "${element(split(",", var.host_os_version), 2)}"
}

# create public IPs
resource "azurerm_public_ip" "main" {
    name = "${var.tags["Name"]}-ip"
    location = "${var.resource_group["location"]}"
    resource_group_name = "${var.resource_group["name"]}"
    allocation_method = "Static"
}

resource "azurerm_network_interface" "main" {
  name                = "${var.tags["Name"]}-ni"
  location            = "${var.resource_group["location"]}"
  resource_group_name = "${var.resource_group["name"]}"
  network_security_group_id = "${var.security_group_id}"

  ip_configuration {
    name                          = "${var.tags["Name"]}"
    subnet_id                     = "${var.subnet_identifier}"
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id = "${azurerm_public_ip.main.id}"
  }
}

# create a server instance
resource "azurerm_virtual_machine" "main" {
  name                  = "${var.tags["Name"]}-vm"
  location              = "${var.resource_group["location"]}"
  resource_group_name   = "${var.resource_group["name"]}"
  network_interface_ids = ["${azurerm_network_interface.main.id}"]
  vm_size               = "${var.host_size}"

  delete_os_disk_on_termination = true
  delete_data_disks_on_termination = true

  storage_image_reference {
    publisher = "${local.os_publisher}"
    offer     = "${local.os_offer}"
    sku       = "${local.os_sku}"
    version   = "latest"
  }

  storage_os_disk {
    name              = "${var.tags["Name"]}-disk"
    caching           = "ReadWrite"
    disk_size_gb      = "${var.disk_size}"
    create_option     = "FromImage"
    managed_disk_type = "Standard_LRS"
  }

  os_profile_linux_config {
    disable_password_authentication = false
  }

  os_profile {
    computer_name  = "${var.username}"
    admin_username = "${var.username}"
    admin_password = "${var.password}"
  }

  tags = "${var.tags}"

}

resource "null_resource" "provision" {

  triggers {
        build_number = "${timestamp()}"
  }

  connection {
    host     = "${data.azurerm_public_ip.main.ip_address}"
    type     = "ssh"
    timeout  = "10m"
    user     = "${var.username}"
    password = "${var.password}"
  }
  provisioner "remote-exec" {
    inline = [ 
      "${var.execute_command}"
    ]
  }
}

data "azurerm_public_ip" "main" {
  name                = "${azurerm_public_ip.main.name}"
  resource_group_name = "${var.resource_group["name"]}"
  depends_on = ["azurerm_virtual_machine.main"]
}

# These vars pass to output json file
data "template_file" "host_json" {
  template = "${file("${path.module}/../../templates/linux_host.json.tmpl")}"
  vars {
    server_name    = "${var.tags["Name"]}"
    ansible_host   = "${data.azurerm_public_ip.main.ip_address}"
    private_ip     = "${azurerm_network_interface.main.private_ip_address}"
    password       = "${var.password}"
    username       = "${var.username}"
    inventory_variables      = "${substr(jsonencode(var.inventory_variables),1,length(jsonencode(var.inventory_variables))-2)}" # remove json start and end curly braces from encoded json of var.inventory_variables map 
  }
  depends_on = ["azurerm_virtual_machine.main"]
}

output "host_json" {
  value = "${data.template_file.host_json.rendered}"
}