#!/usr/bin/env sh

RELEASE_DIR=".releases"

# Name of tfvars file
TF_VARS=terraform.tfvars
RELEASE_IDS_FILE="release_ids.tfvars"

# Ensure we don't have stale variables from a previous run
rm -f $TF_VARS
rm -rf "$RELEASE_DIR"

echo "Getting variables from S3"
aws s3 cp s3://platform-infra/terraform.tfvars .

# Download releases from S3
mkdir -p "$RELEASE_DIR"
aws s3 cp s3://platform-infra/releases "$RELEASE_DIR" --recursive

# Build a tfvars file containing the release ids
echo "release_ids = {" >> "$TF_VARS"
for f in "$RELEASE_DIR"/*;
do
  echo "Processing $f: $(cat $f)"
  echo "  $(basename $f) = \"$(cat $f)\"" >> "$TF_VARS"
done
echo "}" >> "$TF_VARS"