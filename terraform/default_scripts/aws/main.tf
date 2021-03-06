variable "region" {}
variable "aws_access_key" {}
variable "aws_secret_key" {}

provider "aws" {
  version    = "1.60.0"
  access_key = "${var.aws_access_key}"
  secret_key = "${var.aws_secret_key}"
  region     = "${var.region}"
}

provider "local" {
  version = "1.1.0"
}

provider "null" {
  version = "2.0.0"
}

provider "template" {
  version = "2.0.0"
}

provider "random" {
  version = "2.0.0"
}

terraform {
  required_version = "0.11.11"
}