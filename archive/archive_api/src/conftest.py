# -*- encoding: utf-8

import os
import random
import uuid

import pytest


@pytest.fixture
def client(dynamodb_resource, table_name, sns_client, topic_arn):
    from archive_api import app
    app.config['DYNAMODB_TABLE_NAME'] = table_name
    app.config['DYNAMODB_RESOURCE'] = dynamodb_resource
    app.config['SNS_CLIENT'] = sns_client
    app.config['SNS_TOPIC_ARN'] = topic_arn

    yield app.test_client()


@pytest.fixture
def guid():
    return str(uuid.uuid4())


@pytest.fixture()
def table_name(dynamodb_client):
    dynamodb_table_name = 'report_ingest_status--table-%d' % random.randint(0, 10000)
    os.environ.update({'TABLE_NAME': dynamodb_table_name})
    create_table(dynamodb_client, dynamodb_table_name)
    yield dynamodb_table_name
    dynamodb_client.delete_table(TableName=dynamodb_table_name)
    try:
        del os.environ['TABLE_NAME']
    except KeyError:
        pass


def create_table(dynamodb_client, table_name):
    try:
        dynamodb_client.create_table(
            TableName=table_name,
            KeySchema=[
                {
                    'AttributeName': 'id',
                    'KeyType': 'HASH'
                }
            ],
            AttributeDefinitions=[
                {
                    'AttributeName': 'id',
                    'AttributeType': 'S'
                }
            ],
            ProvisionedThroughput={
                'ReadCapacityUnits': 1,
                'WriteCapacityUnits': 1
            }
        )
        dynamodb_client.get_waiter('table_exists').wait(TableName=table_name)
    except dynamodb_client.exceptions.ResourceInUseException:
        pass
