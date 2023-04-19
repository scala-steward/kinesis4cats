/*
 * Copyright 2023-2023 etspaceman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis4cats.kpl
package localstack

import scala.concurrent.duration._

import cats.effect.{Async, Resource}
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration

import kinesis4cats.localstack.LocalstackConfig
import kinesis4cats.localstack.aws.v1.{AwsClients, AwsCreds}

/** Helpers for constructing and leveraging the KPL with Localstack.
  */
object LocalstackKPLProducer {

  /** [[https://github.com/awslabs/amazon-kinesis-producer/blob/master/java/amazon-kinesis-producer/src/main/java/com/amazonaws/services/kinesis/producer/KinesisProducerConfiguration.java KinesisProducerConfiguration]]
    * configuration compliant with Localstack
    *
    * @param config
    *   [[kinesis4cats.localstack.LocalstackConfig LocalstackConfig]]
    * @return
    *   [[https://github.com/awslabs/amazon-kinesis-producer/blob/master/java/amazon-kinesis-producer/src/main/java/com/amazonaws/services/kinesis/producer/KinesisProducerConfiguration.java KinesisProducerConfiguration]]
    */
  def kplConfig(config: LocalstackConfig): KinesisProducerConfiguration =
    new KinesisProducerConfiguration()
      .setVerifyCertificate(false)
      .setKinesisEndpoint(config.kinesisHost)
      .setCloudwatchEndpoint(config.cloudwatchHost)
      .setStsEndpoint(config.stsHost)
      .setCredentialsProvider(AwsCreds.LocalCredsProvider)
      .setKinesisPort(config.kinesisPort.toLong)
      .setCloudwatchPort(config.cloudwatchPort.toLong)
      .setStsPort(config.stsPort.toLong)
      .setMetricsLevel("none")
      .setLogLevel("warning")
      .setRegion(config.region.name)

  /** Creates a [[kinesis4cats.kpl.KPLProducer KPLProducer]] that is compliant
    * with Localstack
    *
    * @param config
    *   [[kinesis4cats.localstack.LocalstackConfig LocalstackConfig]]
    * @param F
    *   [[cats.effect.Async Async]]
    * @param encoders
    *   [[kinesis4cats.kpl.KPLProducer.LogEncoders KPLProducer.LogEncoders]]
    * @return
    *   [[kinesis4cats.kpl.KPLProducer KPLProducer]] as a
    *   [[cats.effect.Resource Resource]]
    */
  def producer[F[_]](
      config: LocalstackConfig,
      encoders: KPLProducer.LogEncoders
  )(implicit
      F: Async[F]
  ): Resource[F, KPLProducer[F]] =
    KPLProducer[F](kplConfig(config), encoders = encoders)

  /** Creates a [[kinesis4cats.kpl.KPLProducer KPLProducer]] that is compliant
    * with Localstack
    *
    * @param prefix
    *   Optional prefix for parsing configuration. Default to None
    * @param F
    *   [[cats.effect.Async Async]]
    * @param encoders
    *   [[kinesis4cats.kpl.KPLProducer.LogEncoders KPLProducer.LogEncoders]].
    *   Default to show
    * @return
    *   [[kinesis4cats.kpl.KPLProducer KPLProducer]] as a
    *   [[cats.effect.Resource Resource]]
    */
  def producer[F[_]](
      prefix: Option[String] = None,
      encoders: KPLProducer.LogEncoders = KPLProducer.LogEncoders.show
  )(implicit
      F: Async[F]
  ): Resource[F, KPLProducer[F]] =
    LocalstackConfig.resource[F](prefix).flatMap(producer(_, encoders))

  /** A resources that does the following:
    *
    *   - Builds a [[kinesis4cats.kpl.KPLProducer KPLProducer]] that is
    *     compliant for Localstack usage.
    *   - Creates a stream with the desired name and shard count, and waits
    *     until the stream is active.
    *   - Destroys the stream when the [[cats.effect.Resource Resource]] is
    *     closed
    *
    * @param config
    *   [[kinesis4cats.localstack.LocalstackConfig LocalstackConfig]]
    * @param streamName
    *   Stream name
    * @param shardCount
    *   Shard count for stream
    * @param describeRetries
    *   How many times to retry DescribeStreamSummary when checking the stream
    *   status
    * @param describeRetryDuration
    *   How long to delay between retries of the DescribeStreamSummary call
    * @param F
    *   F with an [[cats.effect.Async Async]] instance
    * @return
    *   [[cats.effect.Resource Resource]] of
    *   [[kinesis4cats.kpl.KPLProducer KPLProducer]]
    */
  def producerWithStream[F[_]](
      config: LocalstackConfig,
      streamName: String,
      shardCount: Int,
      describeRetries: Int,
      describeRetryDuration: FiniteDuration,
      encoders: KPLProducer.LogEncoders
  )(implicit
      F: Async[F]
  ): Resource[F, KPLProducer[F]] = AwsClients
    .kinesisStreamResource[F](
      config,
      streamName,
      shardCount,
      describeRetries,
      describeRetryDuration
    )
    .flatMap(_ => producer(config, encoders))

  /** A resource that does the following:
    *
    *   - Builds a [[kinesis4cats.kpl.KPLProducer KPLProducer]] that is
    *     compliant for Localstack usage.
    *   - Creates a stream with the desired name and shard count, and waits
    *     until the stream is active.
    *   - Destroys the stream when the [[cats.effect.Resource Resource]] is
    *     closed
    *
    * @param streamName
    *   Stream name
    * @param shardCount
    *   Shard count for stream
    * @param prefix
    *   Optional prefix for parsing configuration. Default to None
    * @param describeRetries
    *   How many times to retry DescribeStreamSummary when checking the stream
    *   status. Default to 5
    * @param describeRetryDuration
    *   How long to delay between retries of the DescribeStreamSummary call.
    *   Default to 500 ms
    * @param F
    *   F with an [[cats.effect.Async Async]] instance
    * @return
    *   [[cats.effect.Resource Resource]] of
    *   [[kinesis4cats.kpl.KPLProducer KPLProducer]]
    */
  def producerWithStream[F[_]](
      streamName: String,
      shardCount: Int,
      prefix: Option[String] = None,
      describeRetries: Int = 5,
      describeRetryDuration: FiniteDuration = 500.millis,
      encoders: KPLProducer.LogEncoders = KPLProducer.LogEncoders.show
  )(implicit
      F: Async[F]
  ): Resource[F, KPLProducer[F]] = AwsClients
    .kinesisStreamResource[F](
      streamName,
      shardCount,
      prefix,
      describeRetries,
      describeRetryDuration
    )
    .flatMap(_ => producer(prefix, encoders))
}
