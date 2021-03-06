module "resource_group" { # sg-group_identifier
    source = "./modules/resource_group"
    resource_identifier = "${var.resource_identifier}"
    location = "${var.region}"
}

