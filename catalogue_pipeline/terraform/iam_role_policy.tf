# Role policies for the transformer

resource "aws_iam_role_policy" "ecs_transformer_task_sns" {
  role   = "${module.transformer.task_role_name}"
  policy = "${module.id_minter_topic.publish_policy}"
}

resource "aws_iam_role_policy" "ecs_transformer_task_vhs" {
  role   = "${module.transformer.task_role_name}"
  policy = "${module.versioned-hybrid-store.read_policy}"
}

resource "aws_iam_role_policy" "transformer_task_cloudwatch_metric" {
  role   = "${module.transformer.task_role_name}"
  policy = "${data.aws_iam_policy_document.allow_cloudwatch_push_metrics.json}"
}

# Role policies for the miro_reindexer service

resource "aws_iam_role_policy" "reindexer_tracker_table" {
  role   = "${module.miro_reindexer.task_role_name}"
  policy = "${data.aws_iam_policy_document.reindex_tracker_table.json}"
}

resource "aws_iam_role_policy" "reindexer_target_miro" {
  role   = "${module.miro_reindexer.task_role_name}"
  policy = "${data.aws_iam_policy_document.reindex_target_miro.json}"
}

resource "aws_iam_role_policy" "reindexer_cloudwatch" {
  role   = "${module.miro_reindexer.task_role_name}"
  policy = "${data.aws_iam_policy_document.allow_cloudwatch_push_metrics.json}"
}

# Role policies for the Elasticsearch ingestor

resource "aws_iam_role_policy" "ecs_ingestor_task_cloudwatch_metric" {
  role   = "${module.ingestor.task_role_name}"
  policy = "${data.aws_iam_policy_document.allow_cloudwatch_push_metrics.json}"
}

# Role policies for the ID minter

resource "aws_iam_role_policy" "ecs_id_minter_task_sns" {
  role   = "${module.id_minter.task_role_name}"
  policy = "${module.es_ingest_topic.publish_policy}"
}

resource "aws_iam_role_policy" "id_minter_cloudwatch" {
  role   = "${module.id_minter.task_role_name}"
  policy = "${data.aws_iam_policy_document.allow_cloudwatch_push_metrics.json}"
}

resource "aws_iam_role_policy" "allow_reindex_job_creator_publish_to_sns" {
  role   = "${module.reindex_job_creator_lambda.role_name}"
  policy = "${module.reindex_jobs_topic.publish_policy}"
}
