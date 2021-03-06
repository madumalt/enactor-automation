## Template file for generating terraform code snippet for invoking Linux/Windows machine module

module "${attributes['host_category']}-${attributes['host_label']}" {
        source = "./modules/machine-${attributes['host_os']}"
        resource_identifier = ${"\"${var.resource_identifier}\""}
    % if attributes['host_category'] == "manager" and "security_group_ids" not in attributes:
        security_group_ids = [${"\"${module.sg-default.id}\", \"${module.sg-swarm.id}\", \"${module.sg-"+ attributes['host_label'] +".id}\""}]
    % elif attributes['host_category'] != "manager" and "security_group_ids" not in attributes:
        security_group_ids = [${"\"${module.sg-default.id}\", \"${module.sg-"+ attributes['host_label'] +".id}\""}]
    % endif
    % if "tags" not in attributes:
        <% machineName = "\"${var.resource_identifier}-" + attributes['host_label'] + "\"" %>
        tags = {
            Name = ${machineName}
            ResourceGroup = ${"\"${var.resource_identifier}\""}
            Schedulers = "${attributes.get("schedulers", "")}"
        }
    % endif
% for key,value in attributes.iteritems():
    % if key in ["host_category", "host_os", "schedulers"]:
        # ${key} = "${value}" *--- meta data ---*
    % elif key in ["subnet_identifier"]:
        <% module_id = "${module." + value + ".id}" %>
        ${key} = "${module_id}"
    % elif key == "security_group_ids":
        <% sg_ids = ["\"${module.sg-"+ sg +".id}\"" for sg in value.split(",")] %>
        ${key} = [${', '.join(sg_ids)}]
    % elif key == "tags":
        tags = {
        % if "Name" not in value:
            <% machineName = "\"${var.resource_identifier}-" + attributes['host_label'] + "\"" %>
            Name = ${machineName}
        % endif
        % if "Schedulers" not in value:
            Schedulers = "${attributes.get("schedulers", "")}"
        % endif
        % if "ResourceGroup" not in value:
            ResourceGroup = ${"\"${var.resource_identifier}\""}
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

