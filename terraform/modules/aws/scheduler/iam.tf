
# Grants stop and start EC2 instances permission to the Lambda function

resource "aws_iam_role" "scheduled_actions" {
  name = "scheduled_actions-${var.resource_identifier}-${var.scheduler_name}"
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

data "aws_iam_policy_document" "scheduled_actions" {
  statement = [
    {
      actions = [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ]
      resources = [
        "arn:aws:logs:*:*:*",
      ]
    },
    {
      actions = [
        "ec2:Describe*",
        "ec2:Stop*",
        "ec2:Start*"
      ]
      resources = [
          "*",
      ]
    }
  ]
}

resource "aws_iam_policy" "scheduled_actions" {
  name = "scheduled_actions-${var.resource_identifier}-${var.scheduler_name}"
  path = "/"
  policy = "${data.aws_iam_policy_document.scheduled_actions.json}"
}

resource "aws_iam_role_policy_attachment" "scheduled_actions" {
  role       = "${aws_iam_role.scheduled_actions.name}"
  policy_arn = "${aws_iam_policy.scheduled_actions.arn}"
}