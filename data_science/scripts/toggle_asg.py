#!/usr/bin/env python
# -*- encoding: utf-8
"""
Start/stop all the instances in an autoscaling group.

Usage: toggle_asg.py (--start | --stop)

Actions:
  --start           Start the autoscaling group (set the desired count to 1).
  --stop            Stop the autoscaling group (set the desired count to 0).

"""

import sys

import boto3
import docopt

from asg_utils import discover_asg_name, set_asg_size


if __name__ == '__main__':
    args = docopt.docopt(__doc__)

    if args.get('--start'):
        desired_size = 1
    elif args.get('--stop'):
        desired_size = 0
    else:
        print('Neither --start nor --stop flags supplied?  args=%r' % args)
        sys.exit(1)

    asg_client = boto3.client('autoscaling')

    asg_name = discover_asg_name(asg_client=asg_client)

    set_asg_size(
        asg_client=asg_client,
        asg_name=asg_name,
        desired_size=desired_size
    )