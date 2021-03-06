variable "vpc_identifier" {}

output "name" { 
  value = "${var.vpc_identifier}"
}