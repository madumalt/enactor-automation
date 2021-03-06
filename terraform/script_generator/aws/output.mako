## Template file for generating terraform code snippet for invoking output-inventory module

module "output-inventory" {
    source = "./modules/output-inventory"

% for key,value in attributes.iteritems():
    ${key} = "${value}"
% endfor

}

