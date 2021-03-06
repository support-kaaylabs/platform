#!/usr/bin/env python
# -*- encoding: utf-8 -*-

import collections
import datetime as dt
import json

import boto3

from build_windows import generate_windows
from report_adapter_progress import BUCKET, build_report


def sliding_window(iterable):
    """Returns a sliding window (of width 2) over data from the iterable."""
    result = collections.deque([], maxlen=2)

    for elem in iterable:
        result.append(elem)
        if len(result) == 2:
            yield tuple(list(result))


def get_missing_windows(report):
    """Given a report of saved Sierra windows, emit the gaps."""
    # Suppose we get two windows:
    #
    #   (-- window_1 --)
    #                       (-- window_2 --)
    #
    # We're missing any records created between the *end* of window_1
    # and the *start* of window_2, so we use these as the basis for
    # our new window.
    for (window_1, _), (window_2, _) in sliding_window(report):
        missing_start = window_1.end - dt.timedelta(seconds=1)
        missing_end = window_2.start + dt.timedelta(seconds=1)

        yield from generate_windows(
            start=missing_start,
            end=missing_end,
            minutes=5
        )


if __name__ == '__main__':
    client = boto3.client('sns')

    for resource_type in ('bibs', 'items'):
        report = build_report(bucket=BUCKET, resource_type=resource_type)
        for missing_window in get_missing_windows(report):
            print(missing_window)
            client.publish(
                TopicArn=f'arn:aws:sns:eu-west-1:760097843905:sierra_{resource_type}_windows',
                Message=json.dumps(missing_window),
                Subject=f'Window sent by {__file__}'
            )
