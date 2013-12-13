/***********************************************************************************************************************
 *
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
 *
 **********************************************************************************************************************/

package eu.stratosphere.pact.runtime.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import eu.stratosphere.pact.common.stubs.Collector;
import eu.stratosphere.pact.common.util.MutableObjectIterator;
import eu.stratosphere.pact.generic.stub.GenericCoGrouper;
import eu.stratosphere.pact.generic.types.TypeComparator;
import eu.stratosphere.pact.generic.types.TypePairComparatorFactory;
import eu.stratosphere.pact.generic.types.TypeSerializer;
import eu.stratosphere.pact.runtime.sort.SortMergeCoGroupIterator;
import eu.stratosphere.pact.runtime.task.util.CoGroupTaskIterator;
import eu.stratosphere.pact.runtime.task.util.TaskConfig;

/**
 * CoGroup task which is executed by a Nephele task manager. The task has two
 * inputs and one or multiple outputs. It is provided with a CoGroupStub
 * implementation.
 * <p>
 * The CoGroupTask group all pairs that share the same key from both inputs. Each for each key, the sets of values that
 * were pair with that key of both inputs are handed to the <code>coGroup()</code> method of the CoGroupStub.
 * 
 * @see eu.stratosphere.pact.common.stubs.CoGroupStub
 * @author Stephan Ewen
 */
public class CoGroupDriver<IT1, IT2, OT> implements PactDriver<GenericCoGrouper<IT1, IT2, OT>, OT>
{
	private static final Log LOG = LogFactory.getLog(CoGroupDriver.class);
	
	
	private PactTaskContext<GenericCoGrouper<IT1, IT2, OT>, OT> taskContext;
	
	private CoGroupTaskIterator<IT1, IT2> coGroupIterator;				// the iterator that does the actual cogroup
	
	private volatile boolean running;

	// ------------------------------------------------------------------------

	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.PactDriver#setup(eu.stratosphere.pact.runtime.task.PactTaskContext)
	 */
	@Override
	public void setup(PactTaskContext<GenericCoGrouper<IT1, IT2, OT>, OT> context) {
		this.taskContext = context;
		this.running = true;
	}
	
	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.AbstractPactTask#getNumberOfInputs()
	 */
	@Override
	public int getNumberOfInputs() {
		return 2;
	}
	
	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.AbstractPactTask#getStubType()
	 */
	@Override
	public Class<GenericCoGrouper<IT1, IT2, OT>> getStubType() {
		@SuppressWarnings("unchecked")
		final Class<GenericCoGrouper<IT1, IT2, OT>> clazz = (Class<GenericCoGrouper<IT1, IT2, OT>>) (Class<?>) GenericCoGrouper.class;
		return clazz;
	}
	
	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.AbstractPactTask#requiresComparatorOnInput()
	 */
	@Override
	public boolean requiresComparatorOnInput() {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.AbstractPactTask#prepare()
	 */
	@Override
	public void prepare() throws Exception
	{
		final TaskConfig config = this.taskContext.getTaskConfig();
		if (config.getDriverStrategy() != DriverStrategy.CO_GROUP) {
			throw new Exception("Unrecognized driver strategy for CoGoup driver: " + config.getDriverStrategy().name());
		}
		
		final MutableObjectIterator<IT1> in1 = this.taskContext.getInput(0);
		final MutableObjectIterator<IT2> in2 = this.taskContext.getInput(1);
		
		// get the key positions and types
		final TypeSerializer<IT1> serializer1 = this.taskContext.getInputSerializer(0);
		final TypeSerializer<IT2> serializer2 = this.taskContext.getInputSerializer(1);
		final TypeComparator<IT1> groupComparator1 = this.taskContext.getInputComparator(0);
		final TypeComparator<IT2> groupComparator2 = this.taskContext.getInputComparator(1);
		
		final TypePairComparatorFactory<IT1, IT2> pairComparatorFactory = config.getPairComparatorFactory(
					this.taskContext.getUserCodeClassLoader());
		if (pairComparatorFactory == null) {
			throw new Exception("Missing pair comparator factory for CoGroup driver");
		}

		// create CoGropuTaskIterator according to provided local strategy.
		this.coGroupIterator = new SortMergeCoGroupIterator<IT1, IT2>(in1, in2,
				serializer1, groupComparator1,  serializer2, groupComparator2,
				pairComparatorFactory.createComparator12(groupComparator1, groupComparator2));
		
		// open CoGroupTaskIterator - this triggers the sorting and blocks until the iterator is ready
		this.coGroupIterator.open();
		
		if (LOG.isDebugEnabled())
			LOG.debug(this.taskContext.formatLogString("CoGroup task iterator ready."));
	}
	
	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.AbstractPactTask#run()
	 */
	@Override
	public void run() throws Exception
	{
		final GenericCoGrouper<IT1, IT2, OT> coGroupStub = this.taskContext.getStub();
		final Collector<OT> collector = this.taskContext.getOutputCollector();
		final CoGroupTaskIterator<IT1, IT2> coGroupIterator = this.coGroupIterator;
		
		while (this.running && coGroupIterator.next()) {
			coGroupStub.coGroup(coGroupIterator.getValues1(), coGroupIterator.getValues2(), collector);
		}
	}

	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.AbstractPactTask#cleanup()
	 */
	@Override
	public void cleanup() throws Exception {
		if (this.coGroupIterator != null) {
			this.coGroupIterator.close();
			this.coGroupIterator = null;
		}
	}

	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.PactDriver#cancel()
	 */
	@Override
	public void cancel() throws Exception {
		this.running = false;
		cleanup();
	}
}
