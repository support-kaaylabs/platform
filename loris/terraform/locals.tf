locals {
  vpc_id          = "${data.terraform_remote_state.shared_infra.catalogue_vpc_id}"
  public_subnets  = "${data.terraform_remote_state.shared_infra.catalogue_public_subnets}"
  private_subnets = "${data.terraform_remote_state.shared_infra.catalogue_private_subnets}"

  ssh_controlled_ingress_sg = "${data.terraform_remote_state.shared_infra.catalogue_ssh_controlled_ingress_sg[0]}"

  bucket_alb_logs_id = "${data.terraform_remote_state.shared_infra.bucket_alb_logs_id}"

  ec2_terminating_topic_arn                       = "${data.terraform_remote_state.shared_infra.ec2_terminating_topic_arn}"
  ec2_instance_terminating_for_too_long_alarm_arn = "${data.terraform_remote_state.shared_infra.ec2_instance_terminating_for_too_long_alarm_arn}"
  ec2_terminating_topic_publish_policy            = "${data.terraform_remote_state.shared_infra.ec2_terminating_topic_publish_policy}"

  alb_server_error_alarm_arn = "${data.terraform_remote_state.shared_infra.alb_server_error_alarm_arn}"
  alb_client_error_alarm_arn = "${data.terraform_remote_state.shared_infra.alb_client_error_alarm_arn}"

  cloudfront_logs_bucket_domain_name = "${data.terraform_remote_state.shared_infra.cloudfront_logs_bucket_domain_name}"
}
