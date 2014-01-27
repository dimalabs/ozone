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

package eu.stratosphere.nephele.instance;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import eu.stratosphere.core.io.IOReadableWritable;
import eu.stratosphere.core.io.StringRecord;
import eu.stratosphere.util.StringUtils;

/**
 * This class encapsulates all connection information necessary to connect to the instance's task manager.
 * 
 */
public class InstanceConnectionInfo implements IOReadableWritable, Comparable<InstanceConnectionInfo> {

	/**
	 * The network address the instance's task manager binds its sockets to.
	 */
	private InetAddress inetAddress;

	/**
	 * The port the instance's task manager runs its IPC service on.
	 */
	private int ipcPort;

	/**
	 * The port the instance's task manager expects to receive transfer envelopes on.
	 */
	private int dataPort;

	/**
	 * The host name of the instance.
	 */
	private String hostName;

	/**
	 * The domain name of the instance.
	 */
	private String domainName;

	/**
	 * Constructs a new instance connection info object. The constructor will attempt to retrieve the instance's
	 * hostname and domain name through the operating system's lookup mechanisms.
	 * 
	 * @param inetAddress
	 *        the network address the instance's task manager binds its sockets to
	 * @param ipcPort
	 *        the port instance's task manager runs its IPC service on
	 * @param dataPort
	 *        the port instance's task manager expects to receive transfer envelopes on
	 */
	public InstanceConnectionInfo(InetAddress inetAddress, int ipcPort, int dataPort) {
		if (inetAddress == null) {
			throw new IllegalArgumentException("Argument inetAddress must not be null");
		}

		if (ipcPort <= 0) {
			throw new IllegalArgumentException("Argument ipcPort must be greater than zero");
		}

		if (dataPort <= 0) {
			throw new IllegalArgumentException("Argument dataPort must be greater than zero");
		}

		this.inetAddress = inetAddress;

		final String hostAddStr = inetAddress.getHostAddress();
		final String fqdn = inetAddress.getCanonicalHostName();

		if (hostAddStr.equals(fqdn)) {
			this.hostName = fqdn;
			this.domainName = null;
		} else {

			// Look for the first dot in the FQDN
			final int firstDot = fqdn.indexOf('.');
			if (firstDot == -1) {
				this.hostName = fqdn;
				this.domainName = null;
			} else {
				this.hostName = fqdn.substring(0, firstDot);
				this.domainName = fqdn.substring(firstDot + 1);
			}
		}

		this.ipcPort = ipcPort;
		this.dataPort = dataPort;
	}

	/**
	 * Constructs an empty {@link InstanceConnectionInfo} object.
	 */
	public InstanceConnectionInfo() {}

	/**
	 * Returns the port instance's task manager runs its IPC service on.
	 * 
	 * @return the port instance's task manager runs its IPC service on
	 */
	public int getIPCPort() {
		return this.ipcPort;
	}

	/**
	 * Returns the port instance's task manager expects to receive transfer envelopes on.
	 * 
	 * @return the port instance's task manager expects to receive transfer envelopes on
	 */
	public int getDataPort() {
		return this.dataPort;
	}

	/**
	 * Returns the network address the instance's task manager binds its sockets to.
	 * 
	 * @return the network address the instance's task manager binds its sockets to
	 */
	public InetAddress getAddress() {
		return this.inetAddress;
	}

	/**
	 * Returns the host name of the instance. If the host name could not be determined, the return value will be a
	 * textual representation of the instance's IP address.
	 * 
	 * @return the host name of the instance
	 */
	public String getHostName() {
		return this.hostName;
	}


	@Override
	public void read(DataInput in) throws IOException {
		final int addr_length = in.readInt();
		byte[] address = new byte[addr_length];
		in.readFully(address);
		this.hostName = StringRecord.readString(in);
		this.domainName = StringRecord.readString(in);
		this.ipcPort = in.readInt();
		this.dataPort = in.readInt();
		
		try {
			this.inetAddress = InetAddress.getByAddress(address);
		} catch (UnknownHostException uhe) {
			throw new IOException(StringUtils.stringifyException(uhe));
		}
	}


	@Override
	public void write(DataOutput out) throws IOException {
		out.writeInt(this.inetAddress.getAddress().length);
		out.write(this.inetAddress.getAddress());
		StringRecord.writeString(out, this.hostName);
		StringRecord.writeString(out, this.domainName);
		out.writeInt(this.ipcPort);
		out.writeInt(this.dataPort);
	}


	@Override
	public String toString() {
		return this.hostName != null ? this.hostName : inetAddress.toString().replace("/", "");
	}


	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof InstanceConnectionInfo) {
			InstanceConnectionInfo ici = (InstanceConnectionInfo) obj;
			return this.inetAddress.equals(ici.getAddress()) &&
					this.ipcPort == ici.getIPCPort() &&
					this.dataPort == ici.getDataPort();
		} else {
			return false;
		}
	}


	@Override
	public int hashCode() {
		return this.inetAddress.hashCode();
	}


	@Override
	public int compareTo(final InstanceConnectionInfo o) {
		return this.getAddress().getHostName()
			.compareTo(((InstanceConnectionInfo) o).getAddress().getHostName());
	}

}
