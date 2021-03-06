import boto3
import os

def lambda_handler(event, context):

  ec2 = boto3.resource('ec2')

  resource_group = os.environ['ResourceGroup']
  scheduler_name = os.environ['SchedulerName']

  filters = [{
          'Name': 'tag:ResourceGroup',
          'Values': [resource_group]
      }]

  all_instances = ec2.instances.filter(Filters=filters)
  
  validate_tags(all_instances) # will be exit if any of the tag is invalid(to avoid partial resource availability)

  for instance in all_instances:
    for tag in instance.tags:
      if tag['Key'] == 'Schedulers':
        schedulers_list = [x.strip() for x in tag['Value'].split(',')]
        for scheduler in schedulers_list: # ex:  ["scheduler1:ec2_stop", "scheduler2:ec2_start"]
          scheduler_tag_name = scheduler.split(":")[0] # ex: scheduler1 
          scheduler_function = scheduler.split(":")[1]  # ex: ec2_stop
          # scheduler in ec2 tags should be match to scheduler name pass to the lambda through env variable
          if scheduler_tag_name == scheduler_name: 
            if scheduler_function == 'ec2_start':
                ec2_start(instance)
            elif scheduler_function == 'ec2_stop':
                ec2_stop(instance)
            elif scheduler_function == 'ec2_info':
                ec2_info(instance)
            else:
              print("invalid tag: "+ scheduler)
        break # no need to check other tags


def ec2_start(instance):
  ec2_info(instance, "instance is being started: ")
  instance.start()

def ec2_stop(instance):
  ec2_info(instance, "instance is being stopped: ")
  instance.stop()

def ec2_info(instance, msg=""):
  ec2info = {
        'Type': instance.instance_type,
        'State': instance.state['Name'],
        'Private IP': instance.private_ip_address,
        'Public IP': instance.public_ip_address,
        'Launch Time': instance.launch_time
        }
  print(msg, ec2info)

def validate_tags(instances):
  for instance in instances:
    for tag in instance.tags:
      if tag['Key'] == 'Schedulers':
        schedulers_list = [x.strip() for x in tag['Value'].split(',')]
        for scheduler in schedulers_list: # ex:  ["scheduler1:ec2_stop", "scheduler2:ec2_start"]
          try:
            scheduler_tag_name = scheduler.split(":")[0] # ex: scheduler1 
            scheduler_function = scheduler.split(":")[1]  # ex: ec2_stop
          except IndexError:
            print("Schedulers tag is not in the correct format: " + scheduler)
            exit(1)
          except Exception, e:
            print("Something else went wrong: " + str(e))
            exit(1)
        break # no need to check other tags