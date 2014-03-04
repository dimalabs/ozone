package eu.stratosphere.hadoopcompatibility;

import com.google.common.base.Preconditions;
import eu.stratosphere.api.common.operators.GenericDataSink;
import eu.stratosphere.api.common.operators.Operator;
import eu.stratosphere.hadoopcompatibility.datatypes.DefaultStratosphereTypeConverter;
import eu.stratosphere.hadoopcompatibility.datatypes.StratosphereTypeConverter;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;

import java.util.List;

/**
 * The HadoopDataSink is a generic wrapper for all Hadoop OutputFormats.
 *
 * Example usage:
 * <pre>
 * 		HadoopDataSink out = new HadoopDataSink(new org.apache.hadoop.mapred.TextOutputFormat<Text, IntWritable>(), new JobConf(), "Hadoop TextOutputFormat",reducer, Text.class,IntWritable.class);
 *		org.apache.hadoop.mapred.TextOutputFormat.setOutputPath(out.getJobConf(), new Path(output));
 * </pre>
 *
 * Note that it is possible to provide custom data type converter.
 *
 * The HadoopDataSink provides a default converter: {@link eu.stratosphere.hadoopcompatibility.datatypes.DefaultStratosphereTypeConverter}
 **/
public class HadoopDataSink<K,V> extends GenericDataSink {

	private static String DEFAULT_NAME = "<Unnamed Hadoop Data Sink>";

	private JobConf jobConf;

	public HadoopDataSink(OutputFormat<K,V> hadoopFormat, JobConf jobConf, String name, Operator input, StratosphereTypeConverter<K,V> conv, Class<K> keyClass, Class<V> valueClass) {
		super(new HadoopOutputFormatWrapper<K,V>(hadoopFormat, jobConf, conv),input, name);
		Preconditions.checkNotNull(hadoopFormat);
		Preconditions.checkNotNull(jobConf);
		this.name = name;
		this.jobConf = jobConf;
		jobConf.setOutputKeyClass(keyClass);
		jobConf.setOutputValueClass(valueClass);
	}

	public HadoopDataSink(OutputFormat<K,V> hadoopFormat, JobConf jobConf, String name, Operator input, Class<K> keyClass, Class<V> valueClass) {
		this(hadoopFormat, jobConf, name, input, new DefaultStratosphereTypeConverter<K, V>(keyClass, valueClass), keyClass, valueClass);
	}

	public HadoopDataSink(OutputFormat<K,V> hadoopFormat, JobConf jobConf, Operator input, Class<K> keyClass, Class<V> valueClass) {
		this(hadoopFormat, jobConf, DEFAULT_NAME, input, new DefaultStratosphereTypeConverter<K, V>(keyClass, valueClass), keyClass, valueClass);
	}

	public HadoopDataSink(OutputFormat<K,V> hadoopFormat, Operator input, Class<K> keyClass, Class<V> valueClass) {
		this(hadoopFormat, new JobConf(), DEFAULT_NAME, input, new DefaultStratosphereTypeConverter<K, V>(keyClass, valueClass), keyClass, valueClass);
	}



	public HadoopDataSink(OutputFormat<K,V> hadoopFormat, JobConf jobConf, String name, List<Operator> input, StratosphereTypeConverter<K,V> conv, Class<K> keyClass, Class<V> valueClass) {
		super(new HadoopOutputFormatWrapper<K,V>(hadoopFormat, jobConf, conv),input, name);
		Preconditions.checkNotNull(hadoopFormat);
		Preconditions.checkNotNull(jobConf);
		this.name = name;
		this.jobConf = jobConf;
		jobConf.setOutputKeyClass(keyClass);
		jobConf.setOutputValueClass(valueClass);
	}

	public HadoopDataSink(OutputFormat<K,V> hadoopFormat, JobConf jobConf, String name, List<Operator> input, Class<K> keyClass, Class<V> valueClass) {
		this(hadoopFormat, jobConf, name, input, new DefaultStratosphereTypeConverter<K, V>(keyClass, valueClass), keyClass, valueClass);
	}

	public HadoopDataSink(OutputFormat<K,V> hadoopFormat, JobConf jobConf, List<Operator> input, Class<K> keyClass, Class<V> valueClass) {
		this(hadoopFormat, jobConf, DEFAULT_NAME, input, new DefaultStratosphereTypeConverter<K, V>(keyClass, valueClass), keyClass, valueClass);
	}

	public HadoopDataSink(OutputFormat<K,V> hadoopFormat, List<Operator> input, Class<K> keyClass, Class<V> valueClass) {
		this(hadoopFormat, new JobConf(), DEFAULT_NAME, input, new DefaultStratosphereTypeConverter<K, V>(keyClass, valueClass), keyClass, valueClass);
	}

	public JobConf getJobConf() {
		return this.jobConf;
	}

}
