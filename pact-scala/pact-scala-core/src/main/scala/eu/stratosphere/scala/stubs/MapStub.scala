/**
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package eu.stratosphere.scala.stubs

import eu.stratosphere.scala.analysis.{UDTSerializer, UDT, UDF1}
import eu.stratosphere.pact.common.stubs.{Collector, MapStub => JMapStub}
import eu.stratosphere.nephele.configuration.Configuration
import eu.stratosphere.pact.common.`type`.PactRecord

abstract class MapStubBase[In: UDT, Out: UDT] extends JMapStub with Serializable with Function1[In, Out] {
  val inputUDT: UDT[In] = implicitly[UDT[In]]
  val outputUDT: UDT[Out] = implicitly[UDT[Out]]
  lazy val udf: UDF1[In, Out] = new UDF1(inputUDT, outputUDT)

  protected lazy val deserializer: UDTSerializer[In] = udf.getInputDeserializer
  protected lazy val serializer: UDTSerializer[Out] = udf.getOutputSerializer
  protected lazy val discard: Array[Int] = udf.getDiscardIndexArray
  protected lazy val outputLength: Int = udf.getOutputLength

  // just so we satisfy Function1 requirements
  def apply(in: In): Out = throw new RuntimeException("Should never be called.")
}

abstract class MapStub[In: UDT, Out: UDT] extends MapStubBase[In, Out] {
  override def map(record: PactRecord, out: Collector[PactRecord]) = {
    val input = deserializer.deserializeRecyclingOn(record)
    val output = map(input)

    record.setNumFields(outputLength)

    for (field <- discard)
      record.setNull(field)

    serializer.serialize(output, record)
    out.collect(record)
  }

  def map(in: In): Out
}

abstract class FlatMapStub[In: UDT, Out: UDT] extends MapStubBase[In, Out] {
  override def map(record: PactRecord, out: Collector[PactRecord]) = {
    val input = deserializer.deserializeRecyclingOn(record)
    val output = flatMap(input)

    if (output.nonEmpty) {

      record.setNumFields(outputLength)

      for (field <- discard)
        record.setNull(field)

      for (item <- output) {

        serializer.serialize(item, record)
        out.collect(record)
      }
    }
  }

  def flatMap(in: In): Iterator[Out]
}

abstract class FilterStub[In: UDT, Out: UDT] extends MapStubBase[In, Out] {
  override def map(record: PactRecord, out: Collector[PactRecord]) = {
    val input = deserializer.deserializeRecyclingOn(record)
    if (filter(input)) {
      out.collect(record)
    }
  }

  def filter(in: In): Boolean
}
