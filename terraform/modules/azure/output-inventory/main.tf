# Creates a json output (infrastructure-details.json)  based on a template (ansible_hosts.json.tmpl)
variable "manager_nodes" {
  default = ""
}
variable "worker_nodes" {
  default = ""
}
variable "tester_nodes" {
  default = ""
}
variable "other_nodes" {
  default = ""
}
variable "storage_nodes" {
  default = ""
}
variable "bastion_nodes" {
  default = ""
}


data "template_file" "ansible_hosts" {
  template = "${file("${path.module}/../../templates/infrastructure-details.tmpl")}"
  vars {
    manager_nodes = "${var.manager_nodes}"
    worker_nodes  = "${var.worker_nodes}"
    tester_nodes  = "${var.tester_nodes}"
    other_nodes   = "${var.other_nodes}"
    storage_nodes = "${var.storage_nodes}"
    bastion_nodes = "${var.bastion_nodes}"
  }
}

resource "local_file" "ansible_hosts" {
  content     = "${data.template_file.ansible_hosts.rendered}"
  filename = "infrastructure-tmp.json"
  provisioner "local-exec" {
    command = "python -m json.tool infrastructure-tmp.json > infrastructure-details.json && rm infrastructure-tmp.json"
  }
}

output "ansible_hosts" {
  value = "${data.template_file.ansible_hosts.rendered}"
}