variable "vpc_identifier" {}
variable "existing" {default="true"}
variable "resource_identifier" {default=""}

output "id"   { 
  value = "${var.vpc_identifier}" 
}