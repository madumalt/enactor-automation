# Create a new VPC
# Only 5 vpc for a region. If need to increase contact AWS

variable "vpc_identifier" {}
variable "resource_identifier" {}
variable "address_range" {}

resource "aws_vpc" "primary" {
  cidr_block           = "${var.address_range}"

  tags      { 
    Name = "${var.resource_identifier}-${var.vpc_identifier}"
    ResourceGroup = "${var.resource_identifier}"
  }
}

output "id"   { value = "${aws_vpc.primary.id}" }