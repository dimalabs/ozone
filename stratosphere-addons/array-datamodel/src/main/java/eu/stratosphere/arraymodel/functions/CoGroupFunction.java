/***********************************************************************************************************************
 * Copyright (C) 2010-2013 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 **********************************************************************************************************************/

package eu.stratosphere.arraymodel.functions;

import java.lang.reflect.Method;
import java.util.Iterator;

import eu.stratosphere.api.common.functions.GenericCoGrouper;
import eu.stratosphere.types.Value;
import eu.stratosphere.util.Collector;


public abstract class CoGroupFunction extends AbstractArrayModelFunction implements GenericCoGrouper<Value[], Value[], Value[]> {

	private static final long serialVersionUID = 1L;

	/**
	 * This method must be implemented to provide a user implementation of a
	 * matcher. It is called for each two key-value pairs that share the same
	 * key and come from different inputs.
	 * 
	 * @param records1 The records from the first input which were paired with the key.
	 * @param records2 The records from the second input which were paired with the key.
	 * @param out A collector that collects all output pairs.
	 * 
	 * @throws Exception Implementations may forward exceptions, which are caught by the runtime. When the
	 *                   runtime catches an exception, it aborts the task and lets the fail-over logic
	 *                   decide whether to retry the task execution.
	 */
	@Override
	public abstract void coGroup(Iterator<Value[]> records1, Iterator<Value[]> records2, Collector<Value[]> out);

	@Override
	public Method getUDFMethod() {
		try {
			return getClass().getMethod("coGroup", Iterator.class, Iterator.class, Collector.class);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
