#--------------------------------------------------------------
# This module creates all resources necessary for a public
# subnet
#--------------------------------------------------------------

variable "subnet_identifier"   { default = "private" }
variable "resource_identifier" {}
variable "vpc_identifier" {}
variable "address_range"  {}
variable "availability_zone" {
  default = ""
}

variable "acl_rules" {
  type = "list"
  default = [
    {
      protocol = "-1"
    }
  ]
}

resource "aws_subnet" "private" {
  vpc_id            = "${var.vpc_identifier}"
  cidr_block        = "${var.address_range}"
  availability_zone = "${var.availability_zone}"

  tags {
    Name = "${var.resource_identifier}-${var.subnet_identifier}"
    ResourceGroup = "${var.resource_identifier}"
  }

  map_public_ip_on_launch = false
}

resource "aws_network_acl" "subnet" {
  vpc_id = "${var.vpc_identifier}"
  subnet_ids = ["${aws_subnet.private.id}"]

  tags = {
    Name = "${var.resource_identifier}-${var.subnet_identifier}"
    ResourceGroup = "${var.resource_identifier}"
  }
}

resource "aws_network_acl_rule" "inbound" {
  count = "${length(var.acl_rules)}"

  network_acl_id = "${aws_network_acl.subnet.id}"
  rule_number    = "${lookup(var.acl_rules[count.index], "priority","${(count.index * 10) + 100}")}"
  egress         = false # inbound
  protocol       = "${lookup(var.acl_rules[count.index], "protocol", "tcp")}"
  rule_action    = "${lookup(var.acl_rules[count.index], "rule_action", "allow")}"
  cidr_block     = "${lookup(var.acl_rules[count.index], "cidr_block", "0.0.0.0/0")}"
  from_port      = "${lookup(var.acl_rules[count.index], "from_port", 0)}"
  to_port        = "${lookup(var.acl_rules[count.index], "to_port", 65535)}"
}

resource "aws_network_acl_rule" "outbound" {
  network_acl_id = "${aws_network_acl.subnet.id}"
  rule_number    = 100
  egress         = true # traffic leaving subnet - outbound
  protocol       = "-1" # all protocol all ports will be opened 
  rule_action    = "allow"
  cidr_block     =  "0.0.0.0/0"
}

output "id" { 
  value = "${aws_subnet.private.id}"
}
