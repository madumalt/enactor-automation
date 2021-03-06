# Create single AZURE LINUX host machine based on given configurations

variable "resource_group" { type = "map"}
variable "host_os_version" { default = "MicrosoftWindowsServer,WindowsServer,2016-Datacenter" }
variable "host_size"   { default = "Standard_B1s" }
variable "disk_size"   { default = "127" }
variable "host_label"  { }
variable "docker_node_labels" { default = "all=true" }
variable "security_group_id" { }
variable "subnet_identifier" { }
variable "uptime" { default = "" }
variable "allow_service_deployment" { default = "true" }
variable "password" { }
variable "username" { default = "Enactor" }
variable "shutdown_schedule" {
  default = ""
}

variable "tags" {
  type = "map"
  default = {}
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

  os_profile_windows_config {
    enable_automatic_upgrades = false
    provision_vm_agent = true
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
    insecure = true
    https = true
    type     = "winrm"
    port     = "5986"
    timeout  = "10m"
    user     = "${var.username}"
    password = "${var.password}"
  }

  provisioner "remote-exec" {
    inline = [ 
      "PowerShell.exe (Get-WMIObject win32_operatingsystem).name"
    ]
  }
}

data "template_file" "init_script" {
  template = "${file("${path.module}/../../templates/windows_init.tpl")}"
  vars {
    username = "${var.username}"
    password = "${var.password}"
  }
}

resource "azurerm_virtual_machine_extension" "ansible_enabler" {
  name                 = "${var.tags["Name"]}-vme"
  location             = "${var.resource_group["location"]}"
  resource_group_name  = "${var.resource_group["name"]}"
  virtual_machine_name = "${azurerm_virtual_machine.main.name}"
  publisher            = "Microsoft.Compute"
  type                 = "CustomScriptExtension"
  type_handler_version = "1.9"

  settings = <<SETTINGS
    {
        "commandToExecute": "powershell -ExecutionPolicy Unrestricted Invoke-Expression ((New-Object System.Net.Webclient).DownloadString('https://raw.githubusercontent.com/ansible/ansible/devel/examples/scripts/ConfigureRemotingForAnsible.ps1'))"
    }
SETTINGS
}

data "azurerm_public_ip" "main" {
  name                = "${azurerm_public_ip.main.name}"
  resource_group_name = "${var.resource_group["name"]}"
  depends_on = ["azurerm_virtual_machine.main"]
}

# These vars pass to output json file
data "template_file" "host_json" {
  template = "${file("${path.module}/../../templates/windows_host.json.tmpl")}"
  vars {
    server_name    = "${var.tags["Name"]}"
    docker_node_labels = "${var.docker_node_labels}"
    ansible_host   = "${data.azurerm_public_ip.main.ip_address}"
    private_ip     = "${azurerm_network_interface.main.private_ip_address}"
    password       = "${var.password}"
    username       = "${var.username}"
    allow_service_deployment = "${var.allow_service_deployment}"
  }
  depends_on = ["azurerm_virtual_machine.main"]
}

output "host_json" {
  value = "${data.template_file.host_json.rendered}"
}