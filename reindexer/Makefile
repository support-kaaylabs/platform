ROOT = $(shell git rev-parse --show-toplevel)
include $(ROOT)/functions.Makefile

STACK_ROOT 	= reindexer
SBT_DOCKER_LIBRARIES =
SBT_NO_DOCKER_LIBRARIES =

SBT_APPS 	= reindex_request_creator reindex_request_processor
ECS_TASKS 	=
LAMBDAS 	=

TF_NAME 	= reindexer
TF_PATH 	= $(STACK_ROOT)/terraform

TF_IS_PUBLIC_FACING = false

$(val $(call stack_setup))
