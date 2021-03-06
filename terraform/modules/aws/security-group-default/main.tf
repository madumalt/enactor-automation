# Create a default security group which enables internal network traffic within vpc

variable "resource_identifier" {}
variable "group_identifier" { default = "sg-default"}
variable "vpc_identifier" { }

resource "aws_security_group" "default" {
  name        = "${var.resource_identifier}-${var.group_identifier}"
  vpc_id      = "${var.vpc_identifier}"

  tags { 
    Name = "${var.resource_identifier}-${var.group_identifier}"
    ResourceGroup = "${var.resource_identifier}"
  }
}

resource "aws_security_group_rule" "private_network" {
  type = "ingress"
  from_port = 0
  to_port = 0
  protocol = "-1"
  self = true
  security_group_id = "${aws_security_group.default.id}"
}

resource "aws_security_group_rule" "all_outbound" {
  type = "egress"
  from_port = 0
  to_port = 65535
  protocol = "-1"
  cidr_blocks = ["0.0.0.0/0"]
  security_group_id = "${aws_security_group.default.id}"
}

output "id" {
  value = "${aws_security_group.default.id}"
}

