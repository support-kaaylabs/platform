module "complete_reindex_lambda" {
  source = "git::https://github.com/wellcometrust/terraform.git//lambda?ref=v10.2.2"

  name      = "complete_reindex"
  s3_bucket = "${var.infra_bucket}"
  s3_key    = "lambdas/reindexer/complete_reindex.zip"

  description = "Mark reindex work as done in the ${aws_dynamodb_table.reindex_shard_tracker.name} table."

  timeout = 120

  environment_variables = {
    TABLE_NAME = "${aws_dynamodb_table.reindex_shard_tracker.name}"
  }

  alarm_topic_arn = "${local.lambda_error_alarm_arn}"

  log_retention_in_days = 30
}

module "trigger_complete_reindex_lambda" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//lambda/trigger_sns?ref=v1.0.0"

  lambda_function_name = "${module.complete_reindex_lambda.function_name}"
  lambda_function_arn  = "${module.complete_reindex_lambda.arn}"

  sns_trigger_arn = "${module.reindex_jobs_complete_topic.arn}"
}

resource "aws_iam_role_policy" "complete_reindex_lambda_reindexer_tracker_table" {
  role   = "${module.complete_reindex_lambda.role_name}"
  policy = "${data.aws_iam_policy_document.reindex_shard_tracker_table_full_access.json}"
}
