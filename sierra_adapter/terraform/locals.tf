locals {
#  ec2_terminating_topic_arn                       = "${data.terraform_remote_state.shared_infra.ec2_terminating_topic_arn}"
#  ec2_instance_terminating_for_too_long_alarm_arn = "${data.terraform_remote_state.shared_infra.ec2_instance_terminating_for_too_long_alarm_arn}"
#  ec2_terminating_topic_publish_policy            = "${data.terraform_remote_state.shared_infra.ec2_terminating_topic_publish_policy}"
#
#
#  bucket_infra_arn = "${data.terraform_remote_state.shared_infra.bucket_infra_arn}"
#  bucket_infra_id  = "${data.terraform_remote_state.shared_infra.bucket_infra_id}"
#
#  bucket_alb_logs_id = "${data.terraform_remote_state.shared_infra.bucket_alb_logs_id}"
#
#  run_ecs_task_topic_arn = "${data.terraform_remote_state.shared_infra.run_ecs_task_topic_arn}"
  services_alb_listener_http_arn = "${data.terraform_remote_state.catalogue_pipeline.services_alb_listener_http_arn}"
  services_alb_listener_https_arn = "${data.terraform_remote_state.catalogue_pipeline.services_alb_listener_https_arn}"

  services_alb_cloudwatch_id = "${data.terraform_remote_state.catalogue_pipeline.services_alb_cloudwatch_id}"

  services_alb_server_error_alarm_arn = "${data.terraform_remote_state.shared_infra.alb_server_error_alarm_arn}"
  services_alb_client_error_alarm_arn = "${data.terraform_remote_state.shared_infra.alb_client_error_alarm_arn}"

  services_vpc_id = "${data.terraform_remote_state.catalogue_pipeline.vpc_services_id}"

  lambda_error_alarm_arn = "${data.terraform_remote_state.shared_infra.lambda_error_alarm_arn}"
}
