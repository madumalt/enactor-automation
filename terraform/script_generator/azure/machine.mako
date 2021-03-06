## Template file for generating terraform code snippet for invoking Linux/Windows machine module


module "${attributes['host_category']}-${attributes['host_label']}" {
        source = "./modules/machine-${attributes['host_os']}"
        resource_group = ${"\"${module.resource_group.info}\""}
    % if "security_group_id" not in attributes:
        security_group_id =  "${"${module.sg-" + attributes['host_label'] + ".id}"}"
    % endif
    % if "tags" not in attributes:
        <% machineName = "\"${var.resource_identifier}-" + attributes['host_label'] + "\"" %>
        tags = {
            Name = ${machineName}
        }
    % endif
% for key,value in attributes.iteritems():
    % if key in ["host_category", "host_os"]:
        # ${key} = "${value}" *--- meta data ---*
    % elif key in ["subnet_identifier"]:
        <% module_id = "${module." + value + ".id}" %>
        ${key} = "${module_id}"
    % elif key == "security_group_id":
        security_group_id =  "${"${module.sg-" + value + ".id}"}"
    % elif key == "tags":
        tags = {
        % if "Name" not in value:
            <% machineName = "\"${var.resource_identifier}-" + attributes['host_label'] + "\"" %>
            Name = ${machineName}
        % endif
        % for k,v in value.iteritems():
            ${k} = "${v}"
        % endfor
        }
    % elif key == "inventory_variables":
        inventory_variables = {
        % for k,v in value.iteritems():
            ${k} = "${v}"
        % endfor
        }
    % else:
        ${key} = "${value}"
    % endif
% endfor
}

