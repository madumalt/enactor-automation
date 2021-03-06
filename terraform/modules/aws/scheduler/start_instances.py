# Lambda function for start and stop ec2 instances in the scheduled time

import boto3
import datetime
from pprint import pprint


def time_in_range(start, end, x):
    """Return true if x is in the range [start, end]"""
    if start <= end:
        return start <= x <= end
    else:
        return start <= x or x <= end


def lambda_handler(event, context):
    # Boto Connection, this uses lambda function region as the default region
  instance_ids_to_start = []
  instance_ids_to_stop = []
  ec2 = boto3.resource('ec2')

  current_utc_time = datetime.datetime.utcnow().time()

  all_instances = ec2.instances.filter(
      Filters=[{
          'Name': 'tag:ShutdownScheduler',
          'Values': ['true']
      }]
  )

  for instance in all_instances:
    schedule_text = ""
    for tag in instance.tags:
      if tag['Key'] == 'AutoShutdownSchedule':
          schedule_text = tag['Value']
          break

    # time ranges for week ex: ['19:30 -> 23:00','','','','','','16:30 -> 22:00']
    schedule_times = schedule_text.split(',')
    if schedule_times == ['']:
      if instance.state['Name'] == "stopped" :
        print("not running: ", instance.id, " | ", instance.state['Name'])
        instance_ids_to_start.append(instance.id)  # add to start list
    else:
      # check with current day of the week( monday = 0, sunday = 7)
      schedule_time = schedule_times[datetime.datetime.today().weekday()]

      if schedule_time != "":
        start_hour = int(schedule_time.split("->")[0].split(":")[0].strip())
        start_min = int(schedule_time.split("->")[0].split(":")[1].strip())
        end_hour = int(schedule_time.split("->")[1].split(":")[0].strip())
        end_min = int(schedule_time.split("->")[1].split(":")[1].strip())
        start = datetime.time(start_hour, start_min, 0)
        end = datetime.time(end_hour, end_min, 0)

        # check current time is in the range
        if time_in_range(start, end, current_utc_time):
          print("Current time(", current_utc_time, ") is in the range(", schedule_time, ") ", instance.id, "- will be turned off")
          instance_ids_to_stop.append(instance.id)  # add to stop list
        else:
          if instance.state['Name'] != "running":
            print("not running: ", instance.id, " | ", instance.state['Name'])
            instance_ids_to_start.append(instance.id)  # add to start list
      else:
        if instance.state['Name'] != "running":
          print("not running: ", instance.id, " | ", instance.state['Name'])
          instance_ids_to_start.append(instance.id)

  print("Will be start:", instance_ids_to_start)
  print("Will be stop:", instance_ids_to_stop)

  ec2.instances.filter(
      Filters=[{'Name': 'instance-id', 'Values': instance_ids_to_start}]).start()
  ec2.instances.filter(
      Filters=[{'Name': 'instance-id', 'Values': instance_ids_to_stop}]).stop()
