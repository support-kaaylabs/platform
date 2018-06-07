package uk.ac.wellcome.platform.matcher.lockable

import java.time.{Duration, Instant}

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.gu.scanamo.{Scanamo, Table}
import com.gu.scanamo.syntax._
import com.gu.scanamo.syntax.{attributeExists, not}
import grizzled.slf4j.Logging
import javax.inject.Inject

import uk.ac.wellcome.storage.dynamo._

class DynamoLockingService @Inject()(dynamoDBClient: AmazonDynamoDB, tableName: String)
  extends LockingService with Logging {

  private val defaultDuration = Duration.ofSeconds(10)
  private val table = Table[RowLock](tableName)

  private def createRowLock(id: Identifier) = {
    val created = Instant.now()
    val expires = created.plus(defaultDuration)

    RowLock(id.value, created, expires)
  }

  def lockRow(id: Identifier): Either[LockFailure, RowLock] = {
    val rowLock = createRowLock(id)

    debug(s"Trying to create RowLock: $rowLock")

    val scanamoOps = table
      .given(not(attributeExists('id)))
      .put(rowLock)

    val result = Scanamo.exec(dynamoDBClient)(scanamoOps)

    debug(s"Got $result when creating $rowLock")

    result
      .left.map(e => LockFailure(e.toString))
      .right.map(_ => rowLock)
  }

  def unlockRow(id: Identifier): Either[UnlockFailure, Unit] = {

    debug(s"Trying to unlock row: $id")

    val scanamoOps = table
      .given(attributeExists('id))
      .delete('id -> id.value)

    val result = Scanamo.exec(dynamoDBClient)(scanamoOps)

    debug(s"Got $result when unlocking $id")

    result
      .left.map(e => UnlockFailure(e.toString))
      .right.map(_ => ())
  }
}