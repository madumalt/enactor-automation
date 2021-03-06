## Template file for generating terraform code snippet for invoking subnet module

module "${attributes['subnet_identifier']}" {
        source = "./modules/${metadata['module_source']}"
        resource_group = ${"\"${module.resource_group.info}\""}
        vpc_identifier =  ${"\"${module.vpc.name}\""}
% for key,value in attributes.iteritems():
    % if key in ["type"]:
        # ${key} = "${value}" *--- meta data ---*
    % else:
        ${key} = "${value}"
    % endif
% endfor
}

