## Template file for generating terraform code snippet for invoking schuduler module

module "scheduler-${attributes['scheduler_name']}" {
        source = "./modules/${metadata['module_source']}"
        resource_identifier = ${"\"${var.resource_identifier}\""}
% for key,value in attributes.iteritems():
        ${key} = "${value}"
% endfor
}

