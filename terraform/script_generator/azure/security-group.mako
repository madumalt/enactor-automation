## Template file for generating terraform code snippet for invoking security-group module


module "sg-${attributes['group_identifier']}" {
        source = "./modules/${metadata['module_source']}"
        resource_group = ${"\"${module.resource_group.info}\""}
## print all attributes as module key value pair 
% for key,value in attributes.iteritems():
  
    % if key == "rules":
        rules = [
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

