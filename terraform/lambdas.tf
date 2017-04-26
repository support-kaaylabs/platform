# Lambda for publishing ECS service schedules to an SNS topic

module "lambda_service_scheduler" {
  source      = "./lambda"
  name        = "service_scheduler"
  description = "Publish an ECS service schedule to SNS"
  filename    = "../lambdas/service_scheduler.py"
}

module "schedule_calm_adapter" {
  source                  = "./lambda/trigger_cloudwatch"
  lambda_function_name    = "${module.lambda_service_scheduler.function_name}"
  lambda_function_arn     = "${module.lambda_service_scheduler.arn}"
  cloudwatch_trigger_arn  = "${aws_cloudwatch_event_rule.weekdays_at_7am.arn}"
  cloudwatch_trigger_name = "${aws_cloudwatch_event_rule.weekdays_at_7am.name}"

  input = <<EOF
{
  "topic_arn": "${aws_sns_topic.service_scheduler_topic.arn}",
  "cluster": "${aws_ecs_cluster.services.name}",
  "service": "${module.calm_adapter.service_name}",
  "desired_count": 1
}
EOF
}

# Lambda for updating ECS service size

module "lambda_update_ecs_service_size" {
  source      = "./lambda"
  name        = "update_ecs_service_size"
  description = "Update the desired count of an ECS service"
  filename    = "../lambdas/update_ecs_service_size.py"
}

module "update_ecs_service_size_trigger" {
  source               = "./lambda/trigger_sns"
  lambda_function_name = "${module.lambda_update_ecs_service_size.function_name}"
  lambda_function_arn  = "${module.lambda_update_ecs_service_size.arn}"
  sns_trigger_arn      = "${aws_sns_topic.service_scheduler_topic.arn}"
}

# Lambda for restarting applications when their config changes

module "lambda_stop_running_tasks" {
  source      = "./lambda"
  name        = "stop_running_tasks"
  description = "Stop all the running instances of a task"
  filename    = "../lambdas/stop_running_tasks.py"
}
