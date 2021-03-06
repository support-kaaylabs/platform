#!/usr/bin/env python
# -*- encoding: utf-8
"""
Report the progress of the Sierra adapter.

Usage:
    report_adapter_progress.py [--purge_dlqs]
    report_adapter_progress.py -h | --help

Options:
    --purge_dlqs    Purge the associated DLQs *if* the report shows that
                    the windows don't have any gaps.
    -h --help       Show this message.

"""

import datetime as dt
import os
import sys

import attr
import boto3
import docopt


BUCKET = 'wellcomecollection-platform-adapters-sierra'


def get_matching_s3_keys(bucket, prefix=''):
    """
    Generate the keys in an S3 bucket.

    :param bucket: Name of the S3 bucket.
    :param prefix: Only fetch keys that start with this prefix (optional).

    """
    # https://alexwlchan.net/2017/07/listing-s3-keys/
    s3 = boto3.client('s3')
    kwargs = {'Bucket': bucket}

    # If the prefix is a single string (not a tuple of strings), we can
    # do the filtering directly in the S3 API.
    if prefix:
        kwargs['Prefix'] = prefix

    while True:

        # The S3 API response is a large blob of metadata.
        # 'Contents' contains information about the listed objects.
        resp = s3.list_objects_v2(**kwargs)

        try:
            contents = resp['Contents']
        except KeyError:
            contents = []

        for obj in contents:
            yield obj['Key']

        # The S3 API is paginated, returning up to 1000 keys at a time.
        # Pass the continuation token into the next response, until we
        # reach the final page (when this field is missing).
        try:
            kwargs['ContinuationToken'] = resp['NextContinuationToken']
        except KeyError:
            break


@attr.s(repr=False)
class Interval:
    start = attr.ib()
    end = attr.ib()
    key = attr.ib()

    def __repr__(self):
        return f'%s(start=%r, end=%r, key=%r)' % (
            type(self).__name__,
            self.start.isoformat(),
            self.end.isoformat(),
            self.key
        )

    def __str__(self):
        return repr(self)


def get_intervals(keys):
    """
    Generate the intervals completed for a particular resource type.

    :param keys: A generator of S3 key names.

    """
    for k in keys:
        name = os.path.basename(k)
        start, end = name.split('__')
        start = start.strip('Z')
        end = end.strip('Z')
        try:
            yield Interval(
                start=dt.datetime.strptime(start, '%Y-%m-%dT%H-%M-%S.%f+00-00'),
                end=dt.datetime.strptime(end, '%Y-%m-%dT%H-%M-%S.%f+00-00'),
                key=k
            )
        except ValueError:
            yield Interval(
                start=dt.datetime.strptime(start, '%Y-%m-%dT%H-%M-%S+00-00'),
                end=dt.datetime.strptime(end, '%Y-%m-%dT%H-%M-%S+00-00'),
                key=k
            )


def combine_overlapping_intervals(sorted_intervals):
    """
    Given a generator of sorted open intervals, generate the covering set.
    It produces a series of 2-tuples: (interval, running), where ``running``
    is the set of sub-intervals used to build the overall interval.

    :param sorted_intervals: A generator of ``Interval`` instances.

    """
    lower = None
    running = []

    for higher in sorted_intervals:
        if not lower:
            lower = higher
            running.append(higher)
        else:
            # We treat these as open intervals.  This first case is for the
            # two intervals being wholly overlapping, for example:
            #
            #       ( -- lower -- )
            #               ( -- higher -- )
            #
            if higher.start < lower.end:
                upper_bound = max(lower.end, higher.end)
                lower = Interval(start=lower.start, end=upper_bound, key=None)
                running.append(higher)

            # Otherwise the two intervals are disjoint.  Note that this
            # includes the case where lower.end == higher.start, because
            # we can't be sure that point has been included.
            #
            #       ( -- lower -- )
            #                      ( -- higher -- )
            #
            # or
            #
            #       ( -- lower -- )
            #                           ( -- higher -- )
            #
            else:
                yield (lower, running)
                lower = higher
                running = [higher]

    # And spit out the final interval
    if lower is not None:
        yield (lower, running)


def build_report(bucket, resource_type):
    """
    Generate a complete set of covering windows for a resource type.
    """
    keys = get_matching_s3_keys(
        bucket=BUCKET,
        prefix=f'windows_{resource_type}_complete'
    )
    intervals = get_intervals(keys=keys)
    yield from combine_overlapping_intervals(intervals)


def chunks(iterable, chunk_size):
    return (
        iterable[i:i + chunk_size]
        for i in range(0, len(iterable), chunk_size)
    )


if __name__ == '__main__':
    args = docopt.docopt(__doc__)

    final_reports = {}

    for resource_type in ('bibs', 'items'):
        print('')
        print('=' * 79)
        print(f'{resource_type} windows')
        print('=' * 79)

        report = build_report(bucket=BUCKET, resource_type=resource_type)

        for iv, running in report:
            if len(running) > 1:
                client = boto3.client('s3')

                # Create a consolidated marker that represents the entire
                # interval.  The back-history of Sierra includes >100k windows,
                # so combining them makes reporting faster on subsequent runs.
                start_str = iv.start.strftime("%Y-%m-%dT%H-%M-%S.%f+00-00")
                end_str = iv.end.strftime("%Y-%m-%dT%H-%M-%S.%f+00-00")

                consolidated_key = f'windows_{resource_type}_complete/{start_str}__{end_str}'

                client.put_object(
                    Bucket=BUCKET,
                    Key=consolidated_key,
                    Body=b''
                )

                # Then clean up the individual intervals that made up the set.
                # We sacrifice granularity for performance.
                for sub_ivs in chunks(running, chunk_size=1000):
                    keys = [
                        s.key for s in sub_ivs if s.key != consolidated_key
                    ]
                    client.delete_objects(
                        Bucket=BUCKET,
                        Delete={
                            'Objects': [{'Key': k} for k in keys]
                        }
                    )

            print(f'{iv.start.isoformat()} -- {iv.end.isoformat()}')

        print('')

        # Now we've consolidated the markers in S3, produce a second copy
        # of the report that "summarises" the output.
        report = build_report(bucket=BUCKET, resource_type=resource_type)
        final_reports[resource_type] = [iv for (iv, _) in report]

    # We only need to give instructions for building missing windows if
    # there are gaps in the run.
    #
    if len(final_reports['bibs']) > 1 or len(final_reports['items']) > 1:
        print('')
        print('-' * 79)
        print('')
        print('You can build new windows for the gaps:')
        print('')
        print(f'$ python {sys.argv[0].replace("report_adapter_progress.py", "build_missing_windows.py")}')\

    # If the run completed successfully, we can purge the associated DLQs.
    #
    # Because each timestamp is fetched (at least) twice, we can have
    # windows land on the DLQ and still get a complete history from Sierra --
    # every record in that window was picked up as part of a different window.
    #
    if args['--purge_dlqs']:
        print('Purging DLQs...')
        for resource_type in ('bibs', 'items'):
            if len(final_reports[resource_type]) == 1:
                sqs = boto3.client('sqs')
                queue_url = sqs.get_queue_url(
                    QueueName=f'sierra_{resource_type}_windows_dlq'
                )['QueueUrl']
                sqs.purge_queue(QueueUrl=queue_url)
