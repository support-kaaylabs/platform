ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/functions.Makefile


# Build and tag a Docker image (specifically for nginx).
#
# Args:
#   $1 - Name of the variant.
#
define nginx_build_image
	$(ROOT)/docker_run.py \
		--dind -- \
		wellcome/image_builder:latest \
            --project=nginx \
            --variant=$(1) \
            --file=nginx/$(2)
endef

nginx-build-api-delta:
	$(call nginx_build_image,api-delta,Dockerfile)

nginx-build-loris:
	$(call nginx_build_image,loris,Dockerfile)

nginx-build-services:
	$(call nginx_build_image,services,services.Dockerfile.template)

nginx-build-loris-delta:
	$(call nginx_build_image,loris-delta,Dockerfile)

nginx-build-grafana:
	$(call nginx_build_image,grafana,Dockerfile)

nginx-build: nginx-build-api-delta nginx-build-loris nginx-build-services nginx-build-loris-delta nginx-build-grafana

nginx-publish-api-delta: nginx-build-api-delta
	$(call publish_service,nginx_api-delta)

nginx-publish-loris: nginx-build-loris
	$(call publish_service,nginx_loris)

nginx-publish-services: nginx-build-services
	$(call publish_service,nginx_services)

nginx-publish-loris-delta: nginx-build-loris-delta
	$(call publish_service,nginx_loris-delta)

nginx-publish-grafana: nginx-build-grafana
	$(call publish_service,nginx_grafana)

nginx-publish: nginx-publish-api-delta nginx-publish-loris nginx-publish-services nginx-publish-loris-delta nginx-publish-grafana
