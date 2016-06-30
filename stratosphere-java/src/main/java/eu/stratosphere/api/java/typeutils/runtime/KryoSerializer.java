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
package eu.stratosphere.api.java.typeutils.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import eu.stratosphere.api.common.typeutils.TypeSerializer;
import eu.stratosphere.core.memory.DataInputView;
import eu.stratosphere.core.memory.DataOutputView;
import eu.stratosphere.util.InstantiationUtil;

public class KryoSerializer<T> extends TypeSerializer<T> {

	private static final long serialVersionUID = 1L;

	private transient Output writer;

	private transient Input reader;

	private final Class<T> type;

	private Kryo kryoSerializer;

	// --------------------------------------------------------------------------------------------

	public KryoSerializer(Class<T> type) {
		this.type = type;
		this.writer = new Output();
		this.reader = new Input();
		this.kryoSerializer = new Kryo();
		this.kryoSerializer.register(type);
	}

	// --------------------------------------------------------------------------------------------

	@Override
	public T createInstance() {
		return InstantiationUtil.instantiate(type, Object.class);
	}

	@Override
	public T copy(T from, T reuse) {
		reuse = kryoSerializer.copy(from);
		return reuse;
	}

	@Override
	public int getLength() {
		return -1;
	}

	@Override
	public void serialize(T value, DataOutputView target) throws IOException {
		this.writer = new Output((OutputStream)target);
		this.kryoSerializer.writeClassAndObject(this.writer, value);
		this.writer.close();
	}

	@SuppressWarnings("unchecked")
	@Override
	public T deserialize(T reuse, DataInputView source) throws IOException {
		this.reader = new Input((InputStream) source);
		Object deserialized = kryoSerializer.readClassAndObject(this.reader);
		return (T) deserialized;
	}

	@Override
	public void copy(DataInputView source, DataOutputView target)
			throws IOException {
		throw new UnsupportedOperationException();
	}

	private void writeObject(java.io.ObjectOutputStream s)
			throws java.io.IOException {
		// write the core object, ignore the remainder
		s.defaultWriteObject();
	}

	// --------------------------------------------------------------------------------------------
	// serialization
	// --------------------------------------------------------------------------------------------

	private void readObject(java.io.ObjectInputStream s)
			throws java.io.IOException, ClassNotFoundException {
		// read basic object and the type
		s.defaultReadObject();
	}
}
