ROOT = $(shell git rev-parse --show-toplevel)
CATALOGUE_API = $(ROOT)/catalogue_api
INFRA_BUCKET = platform-infra

ifneq ($(ROOT), $(shell pwd))
	include $(ROOT)/shared.Makefile
endif


## Run a plan on main stack
catalogue_api-terraform-plan: uptodate-git $(ROOT)/.docker/terraform_ci
	$(ROOT)/builds/docker_run.py --aws -- \
		-v $(CATALOGUE_API)/terraform:/data \
		-e OP=plan \
		terraform_ci:latest

## Run an apply on main stack
catalogue_api-terraform-apply: $(ROOT)/.docker/terraform_ci
	$(ROOT)/builds/docker_run.py --aws -- \
		-v $(CATALOGUE_API)/terraform:/data \
		-e OP=apply \
		terraform_ci:latest


.PHONY: catalogue_api-terraform-plan catalogue_api-terraform-apply