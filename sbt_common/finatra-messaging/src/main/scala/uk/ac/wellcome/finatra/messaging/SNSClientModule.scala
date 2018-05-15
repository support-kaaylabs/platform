package uk.ac.wellcome.finatra.messaging

import com.amazonaws.services.sns.AmazonSNS
import com.google.inject.Provides
import com.twitter.inject.TwitterModule
import javax.inject.Singleton
import uk.ac.wellcome.messaging.sns.SNSClientFactory
import uk.ac.wellcome.models.aws.AWSConfig

object SNSClientModule extends TwitterModule {
  val snsEndpoint = flag[String](
    "aws.sns.endpoint",
    "",
    "Endpoint of AWS SNS. The region will be used if the enpoint is not provided")

  private val accessKey =
    flag[String]("aws.sns.accessKey", "", "AccessKey to access SNS")
  private val secretKey =
    flag[String]("aws.sns.secretKey", "", "SecretKey to access SNS")

  @Singleton
  @Provides
  def providesSNSClient(awsConfig: AWSConfig): AmazonSNS =
    SNSClientFactory.create(
      region = awsConfig.region,
      endpoint = snsEndpoint(),
      accessKey = accessKey(),
      secretKey = secretKey()
    )

}
