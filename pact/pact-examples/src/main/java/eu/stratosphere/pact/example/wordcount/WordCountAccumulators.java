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

package eu.stratosphere.pact.example.wordcount;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

import eu.stratosphere.nephele.client.JobExecutionResult;
import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.nephele.services.accumulators.Histogram;
import eu.stratosphere.nephele.services.accumulators.LongCounter;
import eu.stratosphere.pact.client.LocalExecutor;
import eu.stratosphere.pact.common.contract.FileDataSink;
import eu.stratosphere.pact.common.contract.FileDataSource;
import eu.stratosphere.pact.common.contract.MapContract;
import eu.stratosphere.pact.common.contract.ReduceContract;
import eu.stratosphere.pact.common.contract.ReduceContract.Combinable;
import eu.stratosphere.pact.common.io.RecordOutputFormat;
import eu.stratosphere.pact.common.io.TextInputFormat;
import eu.stratosphere.pact.common.plan.Plan;
import eu.stratosphere.pact.common.plan.PlanAssembler;
import eu.stratosphere.pact.common.plan.PlanAssemblerDescription;
import eu.stratosphere.pact.common.stubs.Collector;
import eu.stratosphere.pact.common.stubs.MapStub;
import eu.stratosphere.pact.common.stubs.ReduceStub;
import eu.stratosphere.pact.common.stubs.StubAnnotation.ConstantFields;
import eu.stratosphere.pact.common.type.PactRecord;
import eu.stratosphere.pact.common.type.base.PactInteger;
import eu.stratosphere.pact.common.type.base.PactString;
import eu.stratosphere.pact.example.util.AsciiUtils;
import eu.stratosphere.pact.example.wordcount.WordCountAccumulators.TokenizeLine;

/**
 * This is similar to the WordCount example and additionally demonstrates how to
 * use custom accumulators (built-in or custom).
 */
public class WordCountAccumulators implements PlanAssembler, PlanAssemblerDescription {
	
	/**
	 * Converts a PactRecord containing one string in to multiple string/integer pairs.
	 * The string is tokenized by whitespaces. For each token a new record is emitted,
	 * where the token is the first field and an Integer(1) is the second field.
	 */
	public static class TokenizeLine extends MapStub implements Serializable {
		private static final long serialVersionUID = 1L;
		
		// initialize reusable mutable objects
		private final PactRecord outputRecord = new PactRecord();
		private final PactString word = new PactString();
		private final PactInteger one = new PactInteger(1);
		
		private final AsciiUtils.WhitespaceTokenizer tokenizer =
				new AsciiUtils.WhitespaceTokenizer();
		
		public static final String ACCUM_NUM_LINES = "accumulator.num-lines";
		private LongCounter numLines = new LongCounter();
		
    // This histogram accumulator collects the distribution of number of words
    // per line
		public static final String ACCUM_WORDS_PER_LINE = "accumulator.words-per-line";
		private Histogram wordsPerLine = new Histogram();
		
		@Override
		public void open(Configuration parameters) throws Exception {
		  getRuntimeContext().addAccumulator(ACCUM_NUM_LINES, this.numLines);
		  getRuntimeContext().addAccumulator(ACCUM_WORDS_PER_LINE, this.wordsPerLine);
		  
		  // You could also write to accumulators in open() or close()
		}
		
		@Override
		public void map(PactRecord record, Collector<PactRecord> collector) {
		  
		  // Increment counter
		  numLines.add(1L);
		  
			// get the first field (as type PactString) from the record
			PactString line = record.getField(0, PactString.class);

			// normalize the line
			AsciiUtils.replaceNonWordChars(line, ' ');
			AsciiUtils.toLowerCase(line);
			
			// tokenize the line
			this.tokenizer.setStringToTokenize(line);
			int numWords = 0;
			while (tokenizer.next(this.word))
			{
			  ++ numWords;
				// we emit a (word, 1) pair 
				this.outputRecord.setField(0, this.word);
				this.outputRecord.setField(1, this.one);
				collector.collect(this.outputRecord);
			}
			// Add a value to the historam accumulator
			this.wordsPerLine.add(numWords);
		}
	}

	/**
	 * Sums up the counts for a certain given key. The counts are assumed to be at position <code>1</code>
	 * in the record. The other fields are not modified.
	 */
	@Combinable
	@ConstantFields(0)
	public static class CountWords extends ReduceStub implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		private final PactInteger cnt = new PactInteger();
		
		@Override
		public void reduce(Iterator<PactRecord> records, Collector<PactRecord> out) throws Exception {
			PactRecord element = null;
			int sum = 0;
			while (records.hasNext()) {
				element = records.next();
				PactInteger i = element.getField(1, PactInteger.class);
				sum += i.getValue();
			}

			this.cnt.setValue(sum);
			element.setField(1, this.cnt);
			out.collect(element);
		}
		
		@Override
		public void combine(Iterator<PactRecord> records, Collector<PactRecord> out) throws Exception {
			// the logic is the same as in the reduce function, so simply call the reduce method
			reduce(records, out);
		}
	}


	@Override
	public Plan getPlan(String... args) {
		// parse job parameters
		int numSubTasks   = (args.length > 0 ? Integer.parseInt(args[0]) : 1);
		String dataInput = (args.length > 1 ? args[1] : "");
		String output    = (args.length > 2 ? args[2] : "");

		FileDataSource source = new FileDataSource(new TextInputFormat(), dataInput, "Input Lines");
		source.setParameter(TextInputFormat.CHARSET_NAME, "ASCII");		// comment out this line for UTF-8 inputs
		MapContract mapper = MapContract.builder(new TokenizeLine())
			.input(source)
			.name("Tokenize Lines")
			.build();
		ReduceContract reducer = ReduceContract.builder(CountWords.class, PactString.class, 0)
			.input(mapper)
			.name("Count Words")
			.build();
		FileDataSink out = new FileDataSink(new RecordOutputFormat(), output, reducer, "Word Counts");
		RecordOutputFormat.configureRecordFormat(out)
			.recordDelimiter('\n')
			.fieldDelimiter(' ')
			.field(PactString.class, 0)
			.field(PactInteger.class, 1);
		
		Plan plan = new Plan(out, "WordCount Example");
		plan.setDefaultParallelism(numSubTasks);
		return plan;
	}


	@Override
	public String getDescription() {
		return "Parameters: [numSubStasks] [input] [output]";
	}

	
	public static void main(String[] args) throws Exception {
		WordCountAccumulators wc = new WordCountAccumulators();
		
		if (args.length < 3) {
			System.err.println(wc.getDescription());
			System.exit(1);
		}
		
		Plan plan = wc.getPlan(args);
		
		// This will execute the word-count embedded in a local context. replace this line by the commented
		// succeeding line to send the job to a local installation or to a cluster for execution
		JobExecutionResult result = LocalExecutor.execute(plan);
		System.out.println("Number of lines counter: " + result.getAccumulatorResult(TokenizeLine.ACCUM_NUM_LINES));
		System.out.println("Words per line histogram: " + result.getAccumulatorResult(TokenizeLine.ACCUM_WORDS_PER_LINE));
//		PlanExecutor ex = new RemoteExecutor("localhost", 6123, "target/pact-examples-0.4-SNAPSHOT-WordCount.jar");
//		ex.executePlan(plan);
	}
}
