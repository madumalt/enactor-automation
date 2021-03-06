## Template file for generating terraform code snippet for vpc module

module "vpc" {
        source = "./modules/${metadata['module_source']}"
        resource_group = ${"\"${module.resource_group.info}\""}
% for key,value in attributes.iteritems():
    % if key in []:
        # ${key} = "${value}" *--- meta data ---*
    % elif key in []:
        <% module_id = "${module." + value + ".id}" %>
        ${key} = "${module_id}"
    % else:
        ${key} = "${value}"
    % endif
% endfor
}

