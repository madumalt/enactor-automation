# Generates a terraform script based on user input json for the infrastructure
# outputs the generated.tf and generated-security-groups.tf

import json
import requests
import sys, getopt
from mako.template import Template
from pprint import pprint

module_templates_dir = ""

def render_module(metadata, attributes, template_name) :
    resource_module = ""
    module_template = Template(filename=module_templates_dir + template_name +'.mako')
    resource_module = module_template.render(metadata= metadata, attributes = attributes)
    return resource_module


def parse_json(json_path = "infrastructure.json") :
    rendered_result = ""
    with open(json_path) as data_file:    
        resource_categories = json.load(data_file)

    manager_nodes = []
    worker_nodes  = []
    tester_nodes  = []
    storage_nodes = []
    other_nodes   = []
    bastion_nodes = []

    for category_name,resource_data in resource_categories.iteritems():
        
        if isinstance(resource_data, basestring):
            rendered_result += render_module(
                metadata = {"variable_name": category_name, "variable_value": resource_data}, 
                attributes = {}, 
                template_name = "variable"
            )
        elif category_name == "vpc":
            if resource_data.get('existing', "false") == "true":
                module_source = "vpc-existing"
            else:
                module_source = "vpc-new"
            rendered_result += render_module(
                metadata = {"module_source": module_source, "index": ""}, 
                attributes = resource_data, 
                template_name = "vpc"
            )
        elif category_name == "subnets": # list of subnets
            for index,resource in enumerate(resource_data):
                if resource.get('type', "public") == "public":
                    module_source = "subnet-public"
                else:
                    module_source = "subnet-private"
                rendered_result += render_module(
                    metadata = {"module_source": module_source, "module_name": category_name, "index": index}, 
                    attributes = resource, 
                    template_name = "subnet"
                )
        elif category_name == "schedulers": # list of schedulers
            for index,resource in enumerate(resource_data):
                rendered_result += render_module(
                    metadata = {"module_source": "scheduler", "module_name": category_name, "index": index}, 
                    attributes = resource, 
                    template_name = "scheduler"
                )
        elif category_name == "machines": # list of machines
            for index,resource in enumerate(resource_data):
                rendered_result += render_module(
                    metadata = {"module_source": "machine-linux", "module_name": category_name, "index": index}, 
                    attributes = resource, 
                    template_name = "machine"
                )
                if resource['host_category'] == "manager":
                    manager_nodes.append("${module." + resource['host_category'] + "-" + resource['host_label'] + ".host_json}")
                elif resource['host_category'] == "worker":
                    worker_nodes.append("${module." + resource['host_category'] + "-" + resource['host_label'] + ".host_json}") 
                elif resource['host_category'] == "tester":
                    tester_nodes.append("${module." + resource['host_category'] + "-" + resource['host_label'] + ".host_json}")
                elif resource['host_category'] == "storage":
                    storage_nodes.append("${module." + resource['host_category'] + "-" + resource['host_label'] + ".host_json}")
                elif resource['host_category'] == "other":
                    other_nodes.append("${module." + resource['host_category'] + "-" + resource['host_label'] + ".host_json}")
                elif resource['host_category'] == "bastion":
                    bastion_nodes.append("${module." + resource['host_category'] + "-" + resource['host_label'] + ".host_json}")
        elif category_name == "security_groups": # list of subnets
            for index,resource in enumerate(resource_data):
                rendered_result += render_module(
                    metadata = {"module_source": "security-group", "module_name": category_name, "index": index}, 
                    attributes = resource, 
                    template_name = "security-group"
                ) 
        else:
            print("# DEBUG_INFO: " + str(type(resource_data)) + ": " + category_name)
    
    if(manager_nodes or worker_nodes or tester_nodes or other_nodes or storage_nodes or bastion_nodes):
        
        rendered_result += render_module(
            metadata = {}, 
            attributes = {
                'manager_nodes': ', '.join(manager_nodes), 
                'worker_nodes': ', '.join(worker_nodes), 
                'tester_nodes': ', '.join(tester_nodes), 
                'storage_nodes': ', '.join(storage_nodes),
                'other_nodes': ', '.join(other_nodes),
                'bastion_nodes': ', '.join(bastion_nodes)
                }, 
            template_name = "output"
        )
    print("========================Generated Terrform Script==============================\n")
    print(rendered_result)
    print("========================Generated Terrform Script==============================\n")
    return rendered_result


def main(argv):
    global module_templates_dir
    debug = False
    provider = 'aws'
    input_file_path = 'infrastructure.json'
    output_file_path = 'generated.tf'

    try:
        opts, args = getopt.getopt(argv,"vhi:o:p:",["input=","output=","provider="])
    except getopt.GetoptError:
        print('terraform-generator.py -i <inputfile> -o <outputfile> -p provider')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print('terraform-generator.py -i <inputfile> -o <outputfile> -p provider')
            sys.exit()
        elif opt in ("-i", "--input"):
            input_file_path = arg
        elif opt in ("-o", "--output"):
            output_file_path = arg
        elif opt in ("-p", "--provider"):
            provider = arg
        elif opt in ("-v"):
            debug = True
    module_templates_dir = provider + "/"
    terraform_script = parse_json(json_path = input_file_path)
    if not debug:
        file= open(output_file_path,"w+")
        file.write(terraform_script)
        file.close()

if __name__ == "__main__":
   main(sys.argv[1:])
