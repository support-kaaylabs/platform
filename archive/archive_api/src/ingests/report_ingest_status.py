# -*- encoding: utf-8

from werkzeug.exceptions import NotFound as NotFoundError


def report_ingest_status(dynamodb_resource, table_name, guid):
    """
    Look up a single GUID in DynamoDB, and return the complete contents
    of the row.
    """
    table = dynamodb_resource.Table(table_name)
    item = table.get_item(Key={'id': guid})

    try:
        return item['Item']
    except KeyError:
        raise NotFoundError(f'No ingest found for id={guid!r}')
