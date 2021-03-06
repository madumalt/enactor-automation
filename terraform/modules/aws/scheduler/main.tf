# Create Lambda function and cloud watch trigger

variable "cron" { }
variable "resource_identifier" {  }
variable "scheduler_name" {  }


resource "aws_cloudwatch_event_rule" "scheduled_actions_event_rule" {
  name = "scheduled_actions-${var.resource_identifier}-${var.scheduler_name}"
  description = "Run scheduled actions on aws resources"
  schedule_expression = "cron(${var.cron})"
  depends_on = ["aws_lambda_function.scheduled_actions_lambda"]
}

# Event target: Associates a rule with a function to run
resource "aws_cloudwatch_event_target" "scheduled_actions_event_target" {
  target_id = "scheduled_actions-${var.resource_identifier}-${var.scheduler_name}"
  rule = "${aws_cloudwatch_event_rule.scheduled_actions_event_rule.name}"
  arn = "${aws_lambda_function.scheduled_actions_lambda.arn}"
}

# AWS Lambda Permissions: Allow CloudWatch to execute the Lambda Functions
resource "aws_lambda_permission" "allow_cloudwatch_to_call_scheduled_actions" {
  statement_id = "AllowExecutionFromCloudWatch"
  action = "lambda:InvokeFunction"
  function_name = "${aws_lambda_function.scheduled_actions_lambda.function_name}"
  principal = "events.amazonaws.com"
  source_arn = "${aws_cloudwatch_event_rule.scheduled_actions_event_rule.arn}"
}

### AWS Lambda function ###
# AWS Lambda API requires a ZIP file with the execution code
data "archive_file" "scheduled_actions" {
  type        = "zip"
  source_file = "${path.module}/scheduled_actions.py"
  output_path = "${path.module}/scheduled_actions.zip"
}

# Lambda defined that runs the Python code with the specified IAM role
resource "aws_lambda_function" "scheduled_actions_lambda" {
  filename = "${data.archive_file.scheduled_actions.output_path}"
  function_name = "scheduled_actions-${var.resource_identifier}-${var.scheduler_name}"
  role = "${aws_iam_role.scheduled_actions.arn}"
  handler = "scheduled_actions.lambda_handler"
  runtime = "python2.7"
  timeout = 300
  source_code_hash = "${data.archive_file.scheduled_actions.output_base64sha256}"
  environment {
    variables = {
      ResourceGroup = "${var.resource_identifier}"
      SchedulerName = "${var.scheduler_name}"
    }
  }
}