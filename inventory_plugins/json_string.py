# A modified version of https://github.com/ansible/ansible/blob/stable-2.7/lib/ansible/plugins/inventory/yaml.py
# that extend the capability to pass JSON-string as -i option to ansible playbooks.

from __future__ import (absolute_import, division, print_function)
__metaclass__ = type

DOCUMENTATION = '''
    inventory: json_string
    version_added: "N/A"
    short_description: Custom plugin to get inventory as a json string.
    description:
        - "JSON based inventory, starts with the 'all' group and has hosts/vars/children entries."
        - Host entries can have sub-entries defined, which will be treated as variables.
        - Vars entries are normal group vars.
        - "Children are 'child groups', which can also have their own vars/hosts/children and so on."
    notes:
        - JSON string should be a valid inventory as inventories passed by .json or .yml file.
'''
EXAMPLES = '''
{
   "all": {
      "hosts": {
         "test1": null,
         "test2": {
            "var1": "value1"
         }
      },
      "vars": {
         "group_var1": "value2"
      },
      "children": {
         "other_group": {
            "children": {
               "group_x": {
                  "hosts": "test5"
               }
            },
            "vars": {
               "g2_var2": "value3"
            },
            "hosts": {
               "test4": {
                  "ansible_host": "127.0.0.1"
               }
            }
         },
         "last_group": {
            "hosts": "test1",
            "vars": {
               "last_var": "MYVALUE"
            }
         }
      }
   }
}
'''

import json

from collections import MutableMapping

from ansible.errors import AnsibleParserError
from ansible.module_utils.six import string_types
from ansible.module_utils._text import to_native
from ansible.parsing.utils.addresses import parse_address
from ansible.plugins.inventory import BaseFileInventoryPlugin, detect_range, expand_hostname_range


class InventoryModule(BaseFileInventoryPlugin):

    NAME = 'json_string'

    def __init__(self):

        super(InventoryModule, self).__init__()

    def verify_file(self, json_str):

        try:
           json.loads(json_str)
           valid = True
        except Exception:
           valid = False
        return valid

    def parse(self, inventory, loader, json_str, cache=True):
        ''' parses the inventory file '''

        super(InventoryModule, self).parse(inventory, loader, json_str)
        self.set_options()

        try:
            data = json.loads(json_str)
        except Exception as e:
            raise AnsibleParserError(e)

        if not data:
            raise AnsibleParserError('An empty or no JSON string is passed as the inventory!')
        elif not isinstance(data, MutableMapping):
            raise AnsibleParserError('JSON inventory has invalid structure, it should be a dictionary, '
                                     'but got: %s .' % type(data))
        elif data.get('plugin'):
            raise AnsibleParserError('Plugin configuration JSON/YAML file, not JSON string inventory!')

        # We expect top level keys to correspond to groups, iterate over them
        # to get host, vars and subgroups (which we iterate over recursivelly)
        if isinstance(data, MutableMapping):
            for group_name in data:
                self._parse_group(group_name, data[group_name])
        else:
            raise AnsibleParserError("Invalid data from JSON string, expected dictionary and got:\n\n%s" % to_native(data))

    def _parse_group(self, group, group_data):

        if isinstance(group_data, (MutableMapping, type(None))):

            self.inventory.add_group(group)

            if group_data is not None:
                # make sure they are dicts
                for section in ['vars', 'children', 'hosts']:
                    if section in group_data:
                        # convert strings to dicts as these are allowed
                        if isinstance(group_data[section], string_types):
                            group_data[section] = {group_data[section]: None}

                        if not isinstance(group_data[section], (MutableMapping, type(None))):
                            raise AnsibleParserError('Invalid "%s" entry for "%s" group, requires a dictionary, found "%s" instead.' %
                                                     (section, group, type(group_data[section])))

                for key in group_data:
                    if key == 'vars':
                        for var in group_data['vars']:
                            self.inventory.set_variable(group, var, group_data['vars'][var])

                    elif key == 'children':
                        for subgroup in group_data['children']:
                            self._parse_group(subgroup, group_data['children'][subgroup])
                            self.inventory.add_child(group, subgroup)

                    elif key == 'hosts':
                        for host_pattern in group_data['hosts']:
                            hosts, port = self._parse_host(host_pattern)
                            self._populate_host_vars(hosts, group_data['hosts'][host_pattern] or {}, group, port)
                    else:
                        self.display.warning('Skipping unexpected key (%s) in group (%s), only "vars", "children" and "hosts" are valid' % (key, group))

        else:
            self.display.warning("Skipping '%s' as this is not a valid group definition" % group)

    def _parse_host(self, host_pattern):
        '''
        Each host key can be a pattern, try to process it and add variables as needed
        '''
        (hostnames, port) = self._expand_hostpattern(host_pattern)

        return hostnames, port

    def _expand_hostpattern(self, hostpattern):
        '''
        Takes a single host pattern and returns a list of hostnames and an
        optional port number that applies to all of them.
        '''
        # Can the given hostpattern be parsed as a host with an optional port
        # specification?

        try:
            (pattern, port) = parse_address(hostpattern, allow_ranges=True)
        except Exception:
            # not a recognizable host pattern
            pattern = hostpattern
            port = None

        # Once we have separated the pattern, we expand it into list of one or
        # more hostnames, depending on whether it contains any [x:y] ranges.

        if detect_range(pattern):
            hostnames = expand_hostname_range(pattern)
        else:
            hostnames = [pattern]

        return (hostnames, port)
