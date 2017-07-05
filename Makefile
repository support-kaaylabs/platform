INFRA_BUCKET = platform-infra


# Tasks for building Docker images #

## Build the image used for jslint
docker-build-jslint:
	docker build ./docker/jslint_ci --tag jslint_ci

## Build the image used for flake8 linting
docker-build-flake8:
	docker build ./docker/python3.6_ci --tag python3.6_ci



nginx-build-api:
	./scripts/build_nginx_image.py --variant=api

nginx-build-loris:
	./scripts/build_nginx_image.py --variant=loris

nginx-build-services:
	./scripts/build_nginx_image.py --variant=services

## Build images for all of our nginx proxies
nginx-build:	\
	nginx-build-api \
	nginx-build-loris \
	nginx-build-services



nginx-deploy-api: nginx-build-api
	./scripts/deploy_docker_to_aws.py --project=nginx_api --infra-bucket=$(INFRA_BUCKET)

nginx-deploy-loris: nginx-build-loris
	./scripts/deploy_docker_to_aws.py --project=nginx_api --infra-bucket=$(INFRA_BUCKET)

nginx-deploy-services: nginx-build-services
	./scripts/deploy_docker_to_aws.py --project=nginx_api --infra-bucket=$(INFRA_BUCKET)

## Push images for all of our nginx proxies
nginx-deploy:	\
	nginx-deploy-api \
	nginx-deploy-loris \
	nginx-deploy-services



# Tasks for running linting #

## Run JSON linting over the ontologies directory
lint-ontologies: docker-build-jslint
	docker run -v $$(pwd)/ontologies:/data jslint_ci:latest

## Run flake8 linting over our Lambda code
lint-lambdas: docker-build-flake8
	docker run -v $$(pwd)/lambdas:/data python3.6_ci:latest



.PHONY: help

## Display this help text
help: # Some kind of magic from https://gist.github.com/rcmachado/af3db315e31383502660
	$(info Available targets)
	@awk '/^[a-zA-Z\-\_0-9\/]+:/ {                                      \
		nb = sub( /^## /, "", helpMsg );                                \
		if(nb == 0) {                                                   \
			helpMsg = $$0;                                              \
			nb = sub( /^[^:]*:.* ## /, "", helpMsg );                   \
		}                                                               \
		if (nb)                                                         \
			printf "\033[1;31m%-" width "s\033[0m %s\n", $$1, helpMsg;  \
	}                                                                   \
	{ helpMsg = $$0 }'                                                  \
	width=30                                                            \
	$(MAKEFILE_LIST)
