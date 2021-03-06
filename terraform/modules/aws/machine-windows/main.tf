# Create single AWS Windows host machine based on given configurations
# An elastic ip will be assigned to each machine ( This is necessary for Ansible to work)
# Only 5 EIP is available for a region -> only 5 machines per region ( Can increase this limit by contact AWS)

variable "password" { }
variable "tags" { default = {} }
variable "subnet_identifier" { }
variable "resource_identifier" { }
variable "private_ip" { default = "" }
variable "disk_size"   { default = "30" }
variable "username" { default = "Administrator" }
variable "host_label"  { default = "leader" }
variable "shutdown_schedule" { default = "" }
variable "host_size"   { default = "t2.small" }
variable "security_group_ids" { type = "list" }
variable "execute_command" { default = "PowerShell.exe (Get-WMIObject win32_operatingsystem).name" }
variable "publicly_accessible" { default = "true" }
variable "connect_via_private_ip" { default = "false" }
variable "host_os_version" { 
  default = "amazon,Windows_Server-2016-English-Full-Base-*"
}
variable "inventory_variables" { 
  default = {
    allow_service_deployment = "true"
  }
}

locals {
  image_owner = "${element(split(",", var.host_os_version),0)}"
  image_name  = "${element(split(",", var.host_os_version),1)}"
}

data "aws_ami" "os_image" {

  most_recent = true
  owners      = ["${local.image_owner}"]

  filter {
    name   = "name"
    values = ["${local.image_name}"]
  }
}

resource "aws_eip" "ec2" {
  instance = "${aws_instance.host_machine.id}"
  vpc      = true

  tags = {
    Name = "${var.resource_identifier}-${var.host_label}"
    ResourceGroup = "${var.resource_identifier}"
  }
}

# create a server instance
resource "aws_instance" "host_machine" {
    tags          = "${var.tags}"
    private_ip    = "${var.private_ip}"
    instance_type = "${var.host_size}"
    subnet_id     = "${var.subnet_identifier}"
    ami           = "${data.aws_ami.os_image.id}"
    vpc_security_group_ids = ["${var.security_group_ids}"]
    user_data     = "${data.template_file.init_script.rendered}"

    root_block_device {
      volume_size = "${var.disk_size}"
      volume_type = "gp2"
    }

    lifecycle {
      ignore_changes = ["ami"]
    }
}

resource "null_resource" "provision" {
  count = "${var.publicly_accessible == "true" ? 1:0}"

  triggers {
        build_number = "${timestamp()}"
  }

  connection {
    host     = "${aws_eip.ec2.0.public_ip}"
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
      "${var.execute_command}"
    ]
  }
}

# will generate init command for ssh or winrm with a random password within i
data "template_file" "init_script" {
  template = "${file("${path.module}/../../templates/windows_init.tpl")}"
  vars {
    username = "${var.username}"
    password = "${var.password}"
  }
}

# These vars pass to output json file
data "template_file" "host_json" {
  template = "${file("${path.module}/../../templates/windows_host.json.tmpl")}"
  vars {
    password                 = "${var.password}"
    username                 = "${var.username}"
    host_label               = "${var.host_label}"
    server_name              = "${var.tags["Name"]}"
    private_ip               = "${aws_instance.host_machine.private_ip}"
    inventory_variables      = "${substr(jsonencode(var.inventory_variables),1,length(jsonencode(var.inventory_variables))-2)}" # remove json start and end curly braces from encoded json of var.inventory_variables map
    ansible_host             = "${(var.publicly_accessible == "true" && var.connect_via_private_ip == "false")? element(concat(aws_eip.ec2.*.public_ip, list("")), 0):aws_instance.host_machine.private_ip}"
  }
  depends_on = ["aws_instance.host_machine"]
}

output "host_json" {
  value = "${data.template_file.host_json.rendered}"
}

output "public_ip" {
  value = "${var.publicly_accessible == "true"? element(concat(aws_eip.ec2.*.public_ip, list("")), 0):aws_instance.host_machine.private_ip}"
}

output "private_ip" {
  value = "${aws_instance.host_machine.private_ip}"
}

output "host_password" {
  value = "${var.password}"
}

output "host_name" {
  value = "${var.tags["Name"]}"
}
