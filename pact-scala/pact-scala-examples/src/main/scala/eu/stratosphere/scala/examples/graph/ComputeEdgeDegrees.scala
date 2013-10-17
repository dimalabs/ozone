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

package eu.stratosphere.scala.examples.graph

import eu.stratosphere.pact.client.LocalExecutor
import eu.stratosphere.scala.Args
import eu.stratosphere.scala.DataSource
import eu.stratosphere.scala.ScalaPlan
import eu.stratosphere.scala.analysis.GlobalSchemaPrinter
import eu.stratosphere.scala.operators.RecordDataSourceFormat
import eu.stratosphere.scala.operators.optionToIterator
import eu.stratosphere.scala.operators.DelimitedDataSinkFormat
import java.io.FileWriter
import java.io.File
import java.io.BufferedWriter

object RunComputeEdgeDegrees {
  def main(pArgs: Array[String]) {
    if (pArgs.size < 2) {
      println("usage: -input <file> -output <file>")
      return
    }
    val args = Args.parse(pArgs)
    val plan = new ComputeEdgeDegrees().getPlan(args("input"), args("output"))
    LocalExecutor.execute(plan)
    System.exit(0)
  }
}

/**
 * Annotates edges with associated vertex degrees.
 */
class ComputeEdgeDegrees extends Serializable {
   
  /*
   * Output formatting function for edges with annotated degrees
   */
  def formatEdgeWithDegrees = (v1: Int, v2: Int, c1: Int, c2: Int) => "%d,%d|%d,%d".format(v1, v2, c1, c2)
    
  /*
   * Emits one edge for each unique input edge with the vertex degree of the first(and grouping key) vertex.
   * The degree of the second (non-grouping key) vertexes are set to zero.
   * Edges are projected such that smaller vertex is the first vertex.
   */ 
  def annotateFirstVertexDegree(eI: Iterator[(Int, Int)]): List[(Int, Int, Int, Int)] = {
    val eL = eI.toList
    val eLUniq = eL.distinct
    val cnt = eLUniq.size
    for (e <- eLUniq)
      yield if (e._1 < e._2) 
    	  		(e._1, e._2, cnt, 0)
        	else 
        		(e._2, e._1, 0, cnt)
  }
  
  /*
   * Combines the degrees of both vertexes of an edge.
   */
  def combineVertexDegrees(eI: Iterator[(Int, Int, Int, Int)]) : (Int, Int, Int, Int) = {
    
    val eL = eI.toList
    if (eL.size != 2)
    	throw new RuntimeException("Problem when combinig vertex counts");
    
    if (eL(0)._3 == 0 && eL(1)._4 == 0)
      (eL(0)._1, eL(1)._3, eL(0)._2, eL(0)._4)
    else
      (eL(0)._1, eL(0)._3, eL(0)._2, eL(1)._4)
    
  }
    
  def getPlan(edgeInput: String, annotatedEdgeOutput: String) = {
    
    /*
     * Input format for edges. 
     * Edges are separated by new line '\n'. 
     * An edge is represented as two Integer vertex IDs which are separated by a blank ','.
     */
    val edges = DataSource(edgeInput, RecordDataSourceFormat[(Int, Int)]("\n", ","))

    /*
     * Emit each edge twice with both vertex orders.
     */
    val projEdges = edges flatMap { (e) => Iterator((e._1, e._2) , (e._2, e._1)) }
    
    /*
     * Annotates each edges with degree for the first vertex.
     */
    val vertexCnts = projEdges groupBy { _._1 } hadoopReduce { annotateFirstVertexDegree } flatMap {x => x.iterator }
    
    /*
     * Combines the degrees of both vertexes of an edge.
     */
    val combinedVertexCnts = vertexCnts groupBy { (x) => (x._1, x._2) } hadoopReduce { combineVertexDegrees }
    
    /*
     * Emit annotated edges.
     */
    val output = combinedVertexCnts.write(annotatedEdgeOutput, DelimitedDataSinkFormat(formatEdgeWithDegrees.tupled))
  
    new ScalaPlan(Seq(output), "Compute Edge Degrees")
  }
}