data "template_file" "es_cluster_host_ingestor" {
  template = "$${name}.$${region}.aws.found.io"

  vars {
    name   = "${var.es_config_ingestor["name"]}"
    region = "${var.es_config_ingestor["region"]}"
  }
}

module "ingestor" {
  source = "git::https://github.com/wellcometrust/terraform-modules.git//sqs_autoscaling_service?ref=v2.0.0"
  name   = "ingestor"

  source_queue_name  = "${module.es_ingest_queue.name}"
  source_queue_arn   = "${module.es_ingest_queue.arn}"
  ecr_repository_url = "${module.ecr_repository_ingestor.repository_url}"
  release_id         = "${var.release_ids["ingestor"]}"

  config_vars = {
    es_host           = "${data.template_file.es_cluster_host_ingestor.rendered}"
    es_port           = "${var.es_config_ingestor["port"]}"
    es_name           = "${var.es_config_ingestor["name"]}"
    es_index          = "${var.es_config_ingestor["index"]}"
    es_doc_type       = "${var.es_config_ingestor["doc_type"]}"
    es_username       = "${var.es_config_ingestor["username"]}"
    es_password       = "${var.es_config_ingestor["password"]}"
    es_protocol       = "${var.es_config_ingestor["protocol"]}"
    ingest_queue_id   = "${module.es_ingest_queue.id}"
    metrics_namespace = "ingestor"
  }

  alb_priority = "102"

  cluster_name               = "${aws_ecs_cluster.services.name}"
  vpc_id                     = "${module.vpc_services.vpc_id}"
  alb_cloudwatch_id          = "${module.services_alb.cloudwatch_id}"
  alb_listener_https_arn     = "${module.services_alb.listener_https_arn}"
  alb_listener_http_arn      = "${module.services_alb.listener_http_arn}"
  alb_server_error_alarm_arn = "${local.alb_server_error_alarm_arn}"
  alb_client_error_alarm_arn = "${local.alb_client_error_alarm_arn}"
}
