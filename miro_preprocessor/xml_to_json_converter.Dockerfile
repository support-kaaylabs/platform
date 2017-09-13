FROM python:3-alpine3.6
LABEL maintainer "Alex Chan <a.chan@wellcome.ac.uk>"
LABEL description "Copy records from Miro XML dumps into DynamoDB"

RUN apk update

# Required for lxml
RUN apk add build-base libxml2-dev libxslt-dev python3-dev

COPY xml_to_json_converter/requirements.txt /requirements.txt
RUN pip3 install -r requirements.txt

COPY xml_to_json_converter /app

ENTRYPOINT ["/app/run.py"]