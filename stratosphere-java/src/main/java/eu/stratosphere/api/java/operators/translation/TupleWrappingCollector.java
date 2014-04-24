/***********************************************************************************************************************
 *
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
 *
 **********************************************************************************************************************/
package eu.stratosphere.api.java.operators.translation;

import eu.stratosphere.api.java.tuple.Tuple2;
import eu.stratosphere.util.Collector;

/**
 * Needed to wrap tuples to Tuple2<key, value> pairs for combine method of group reduce with key selector function
 */
public class TupleWrappingCollector<K, IN> implements Collector<IN>, java.io.Serializable {
	
	private static final long serialVersionUID = 1L;

	private K key;
	
	private Collector<Tuple2<K, IN>> outerCollector;
	
	public TupleWrappingCollector() {
	}
	
	public void set(K key, Collector<Tuple2<K, IN>> outerCollector) {
		this.key = key;
		this.outerCollector = outerCollector;
	}
	
	@Override
	public void close() {
		this.outerCollector.close();
	}

	@Override
	public void collect(IN record) {
		this.outerCollector.collect(new Tuple2<K, IN>(this.key, record));
	}

}