## Template file for generating terraform code snippet for invoking subnet module

module "${attributes['subnet_identifier']}" {
        source = "./modules/${metadata['module_source']}"
        resource_identifier = ${"\"${var.resource_identifier}\""}
        vpc_identifier =  ${"\"${module.vpc.id}\""}
% for key,value in attributes.iteritems():
    % if key in ["type"]:
        # ${key} = "${value}" *--- meta data ---*
    % elif key == "acl_rules":
        acl_rules = [
        % for rule_key,rule in enumerate(value):
            {
            % for att_key,attribute in rule.iteritems():
                ${att_key} = "${attribute}"
            % endfor
            }\
            % if rule_key != len(value)-1: 
, 
            % endif
        % endfor 
        
        ]
    % else:
        ${key} = "${value}"
    % endif
% endfor
}

