package uk.ac.wellcome.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{Message, ReceiveMessageRequest}
import com.google.inject.Inject
import com.twitter.inject.Logging
import uk.ac.wellcome.models.aws.SQSConfig

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import uk.ac.wellcome.utils.GlobalExecutionContext.context

class SQSReader @Inject()(sqsClient: AmazonSQS,
                          sqsConfig: SQSConfig)
    extends Logging {

  def retrieveMessages(): Future[List[Message]] = Future {
    info(s"Looking for new messages at ${sqsConfig.queueUrl}")
    sqsClient
      .receiveMessage(
        new ReceiveMessageRequest(sqsConfig.queueUrl)
          .withWaitTimeSeconds(sqsConfig.waitTime.toSeconds.toInt)
          .withMaxNumberOfMessages(sqsConfig.maxMessages))
      .getMessages
      .toList
  }

}
