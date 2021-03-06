# Create a security group based on given configurations

variable "resource_identifier" {}
variable "group_identifier" {}
variable "vpc_identifier" { }
variable "rules" {
  type = "list"
}

resource "aws_security_group" "default" {
  name        = "${var.resource_identifier}-${var.group_identifier}"
  vpc_id      = "${var.vpc_identifier}"

  tags { 
    Name = "${var.resource_identifier}-${var.group_identifier}"
    ResourceGroup = "${var.resource_identifier}"
  }
}

resource "aws_security_group_rule" "generated" {
  count = "${length(var.rules)}"

  type = "${lookup(var.rules[count.index], "direction", "ingress")}"
  from_port = "${lookup(var.rules[count.index], "from_port", lookup(var.rules[count.index], "port", "0"))}"
  to_port = "${lookup(var.rules[count.index], "to_port", lookup(var.rules[count.index], "port", "0"))}"
  cidr_blocks = ["${lookup(var.rules[count.index], "source_address_prefix", "0.0.0.0/0")}"]
  protocol = "${lookup(var.rules[count.index], "protocol", "tcp")}"
  security_group_id = "${aws_security_group.default.id}"
}

output "id" {
  value = "${aws_security_group.default.id}"
}

