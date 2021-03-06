#--------------------------------------------------------------
# This module creates all resources necessary for a public
# subnet
#--------------------------------------------------------------

variable "subnet_identifier"   { default = "public" }
variable "resource_identifier" {}
variable "vpc_identifier" {}
variable "address_range"  {}
variable "create_nat" {
  default = "false"
}

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

resource "aws_internet_gateway" "public" {
  vpc_id = "${var.vpc_identifier}"

  tags = {
    Name = "${var.resource_identifier}-${var.subnet_identifier}-ig"
    ResourceGroup = "${var.resource_identifier}"
  }
}

resource "aws_subnet" "public" {
  vpc_id            = "${var.vpc_identifier}"
  cidr_block        = "${var.address_range}"
  availability_zone = "${var.availability_zone}"

  tags      { 
    Name = "${var.resource_identifier}-${var.subnet_identifier}"
    ResourceGroup = "${var.resource_identifier}"
  }

  map_public_ip_on_launch = true
}

resource "aws_network_acl" "subnet" {
  vpc_id = "${var.vpc_identifier}"
  subnet_ids = ["${aws_subnet.public.id}"]

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

resource "aws_eip" "nat" {
  count = "${var.create_nat == "true"? 1:0}"
  vpc      = true

  tags = {
    Name = "${var.resource_identifier}-${var.subnet_identifier}-nat"
    ResourceGroup = "${var.resource_identifier}"
  }
}

resource "aws_nat_gateway" "gw" {
  count = "${var.create_nat == "true"? 1:0}"
  allocation_id = "${aws_eip.nat.id}"
  subnet_id     = "${aws_subnet.public.id}"

  tags = {
    Name = "${var.resource_identifier}-${var.subnet_identifier}-nat"
    ResourceGroup = "${var.resource_identifier}"
  }
}

resource "aws_route_table" "public" {
  vpc_id = "${var.vpc_identifier}"

  route {
      cidr_block = "0.0.0.0/0"
      gateway_id = "${aws_internet_gateway.public.id}"
  }

  tags { 
    Name = "${var.resource_identifier}-${var.subnet_identifier}-rt"
    ResourceGroup = "${var.resource_identifier}"
  }
}

data "aws_route_table" "selected" {
  vpc_id = "${var.vpc_identifier}"
  filter {
      name   = "association.main"
      values = ["true"]
  }
}

resource "aws_route" "nat_route" {
  count = "${var.create_nat == "true"? 1:0}"
  route_table_id            = "${data.aws_route_table.selected.id}"
  destination_cidr_block    = "0.0.0.0/0"
  nat_gateway_id = "${aws_nat_gateway.gw.id}"
}

resource "aws_route_table_association" "public" {
  subnet_id      = "${aws_subnet.public.id}"
  route_table_id = "${aws_route_table.public.id}"
}

output "id" { 
  value = "${aws_subnet.public.id}"
}
