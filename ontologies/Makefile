ROOT = $(shell git rev-parse --show-toplevel)
ONTOLOGIES = $(ROOT)/ontologies

lint-turtle:
	$(ROOT)/docker_run.py -- \
		--volume $(ONTOLOGIES):/data wellcome/turtlelint

lint-json:
	$(ROOT)/docker_run.py -- \
		--volume $(ROOT)/ontologies:/data wellcome/jslint

lint-ontologies: lint-turtle lint-json
