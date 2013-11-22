/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package eu.stratosphere.pact.common.io;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.pact.common.type.PactRecord;
import eu.stratosphere.pact.common.type.base.PactBoolean;
import eu.stratosphere.pact.common.type.base.PactDouble;
import eu.stratosphere.pact.common.type.base.PactFloat;
import eu.stratosphere.pact.common.type.base.PactInteger;
import eu.stratosphere.pact.common.type.base.PactLong;
import eu.stratosphere.pact.common.type.base.PactNull;
import eu.stratosphere.pact.common.type.base.PactShort;
import eu.stratosphere.pact.common.type.base.PactString;

/**
 * InputFormat to read data from a database and generate PactReords.
 *
 * The InputFormat has to be configured with the query, and either all
 * connection parameters or a complete database URL.{@link Configuration}
 *
 * The position of a value inside a PactRecord is determined by the table
 * returned.
 *
 * @see Configuration
 * @see PactRecord
 * @see DriverManager
 */
public class JDBCInputFormat extends GenericInputFormat {
    private enum DBTypes {
        MYSQL,
        POSTGRESQL,
        MARIADB,
        DERBY
    }

    public static class NotTransformableSQLFieldException extends Exception {

        public NotTransformableSQLFieldException(String message) {
            super(message);
        }
    }

    private static final Log LOG = LogFactory.getLog(JDBCInputFormat.class);

    //Connection objects
    private Connection dbConn;
    private Statement statement;
    private ResultSet resultSet;

    private String query;

    //Connection parameters
    private String dbTypeStr;
    private String host;
    private Integer port;
    private String dbName;
    private String username;
    private String password;
    private String derbyDBPath;

    private String dbURL = null;

    private boolean need_configure = false;

    /**
     * Creates a non-configured JDBCInputFormat. This format has to be
     * configured using configure(configuration).
     */
    public JDBCInputFormat() {
        this.need_configure = true;
    }

    /**
     * Creates a JDBCInputFormat and configures it.
     *
     * @param dbURL Formatted URL containing all connection parameters.
     * @param query Query to execute.
     */
    public JDBCInputFormat(String dbURL, String query) {
        this.query = query;
        this.dbURL = dbURL;
    }

    /**
     * Creates a JDBCInputFormat and configures it.
     *
     * @param parameters Configuration with all connection parameters.
     * @param query Query to execute.
     */
    public JDBCInputFormat(Configuration parameters, String query) {
        this.query = query;
        this.dbTypeStr = parameters.getString("type", "mysql");
        this.host = parameters.getString("host", "localhost");
        this.port = parameters.getInteger("port", 3306);
        this.dbName = parameters.getString("name", "");
        this.username = parameters.getString("username", "");
        this.password = parameters.getString("password", "");
        this.derbyDBPath = parameters.getString("derbydbpath", "");
    }

    /**
     * Converts given database type string to dbTypes.
     *
     * @param dbTypeStr Database type as string.
     * @return Database type as dbTypes.
     */
    private DBTypes getDBType(String dbTypeStr) {
        dbTypeStr = dbTypeStr.toUpperCase().trim();
        try {
            return DBTypes.valueOf(dbTypeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Database type is not supported yet:\t" + e.getMessage());
        }
    }

    protected boolean setClassForDBType(String dbTypeStr) {
        DBTypes dbType = getDBType(dbTypeStr);
        return setClassForDBType(dbType);
    }

    /**
     * Loads appropriate JDBC driver.
     *
     * @param dbType Type of the database.
     * @return boolean value, indication whether an appropriate driver could be
     * found.
     */
    private boolean setClassForDBType(DBTypes dbType) {
        if (dbType == null) {
            return false;
        }

        try {
            switch (dbType) {
                case MYSQL:
                    Class.forName("com.mysql.jdbc.Driver");
                    return true;

                case POSTGRESQL:
                    Class.forName("org.postgresql.Driver");
                    return true;

                case MARIADB:
                    Class.forName("com.mysql.jdbc.Driver");
                    return true;

                case DERBY:
                    Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
                    return true;
            }
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException("JDBC-CLass not found:\t" + cnfe.getLocalizedMessage());
        }
        return false;
    }

    /**
     * Establishes a connection to a database.
     *
     * @param dbURL Assembled URL containing all connection parameters.
     * @return boolean value, indicating whether a connection could be
     * established
     */
    private boolean prepareConnection(String dbURL) throws SQLException {
        dbConn = DriverManager.getConnection(dbURL);
        return true;
    }

    /**
     * Assembles the Database URL and establishes a connection.
     *
     * @param dbType Type of the database.
     * @param username Login username.
     * @param password Login password.
     * @return boolean value, indicating whether a connection could be
     * established
     */
    private boolean prepareConnection(DBTypes dbType, String username, String password) throws SQLException {
        switch (dbType) {
            case MYSQL:
                dbURL = String.format("jdbc:mysql://%s:%d/%s", host, port, dbName);
                break;
            case POSTGRESQL:
                dbURL = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);
                break;
            case MARIADB:
                dbURL = String.format("jdbc:mysql://%s:%d/%s", host, port, dbName);
                break;
            case DERBY:
                dbURL = String.format("jdbc:derby://%s", derbyDBPath);
                break;
        }
        dbConn = DriverManager.getConnection(dbURL, username, password);
        return true;
    }

    /**
     * Enters data value from the current resultSet into a PactRecord.
     *
     * @param pos PactRecord position to be set.
     * @param type SQL type of the resultSet value.
     * @param record Target PactRecord.
     *
     */
    private void retrieveTypeAndFillRecord(int pos, int type, PactRecord record) throws SQLException, NotTransformableSQLFieldException {
        switch (type) {
            case java.sql.Types.NULL:
                record.setField(pos, new PactNull());
                break;
            case java.sql.Types.BOOLEAN:
                record.setField(pos, new PactBoolean(resultSet.getBoolean(pos + 1)));
                break;
            case java.sql.Types.BIT:
                record.setField(pos, new PactBoolean(resultSet.getBoolean(pos + 1)));
                break;
            case java.sql.Types.CHAR:
                record.setField(pos, new PactString(resultSet.getString(pos + 1)));
                break;
            case java.sql.Types.NCHAR:
                record.setField(pos, new PactString(resultSet.getString(pos + 1)));
                break;
            case java.sql.Types.VARCHAR:
                record.setField(pos, new PactString(resultSet.getString(pos + 1)));
                break;
            case java.sql.Types.LONGVARCHAR:
                record.setField(pos, new PactString(resultSet.getString(pos + 1)));
                break;
            case java.sql.Types.LONGNVARCHAR:
                record.setField(pos, new PactString(resultSet.getString(pos + 1)));
                break;
            case java.sql.Types.TINYINT:
                record.setField(pos, new PactShort(resultSet.getShort(pos + 1)));
                break;
            case java.sql.Types.SMALLINT:
                record.setField(pos, new PactShort(resultSet.getShort(pos + 1)));
                break;
            case java.sql.Types.BIGINT:
                record.setField(pos, new PactLong(resultSet.getLong(pos + 1)));
                break;
            case java.sql.Types.INTEGER:
                record.setField(pos, new PactInteger(resultSet.getInt(pos + 1)));
                break;
            case java.sql.Types.FLOAT:
                record.setField(pos, new PactDouble(resultSet.getDouble(pos + 1)));
                break;
            case java.sql.Types.REAL:
                record.setField(pos, new PactFloat(resultSet.getFloat(pos + 1)));
                break;
            case java.sql.Types.DOUBLE:
                record.setField(pos, new PactDouble(resultSet.getDouble(pos + 1)));
                break;
            case java.sql.Types.DECIMAL:
                record.setField(pos, new PactDouble(resultSet.getBigDecimal(pos + 1).doubleValue()));
                break;
            case java.sql.Types.NUMERIC:
                record.setField(pos, new PactDouble(resultSet.getBigDecimal(pos + 1).doubleValue()));
                break;
            case java.sql.Types.DATE:
                record.setField(pos, new PactString(resultSet.getDate(pos + 1).toString()));
                break;
            case java.sql.Types.TIME:
                record.setField(pos, new PactLong(resultSet.getTime(pos + 1).getTime()));
                break;
            case java.sql.Types.TIMESTAMP:
                record.setField(pos, new PactString(resultSet.getTimestamp(pos + 1).toString()));
                break;
            case java.sql.Types.SQLXML:
                record.setField(pos, new PactString(resultSet.getSQLXML(pos + 1).toString()));
                break;
            default:
                throw new NotTransformableSQLFieldException("Unknown sql-type [" + type + "]on column [" + pos + "]");

            //			case java.sql.Types.BINARY:
            //			case java.sql.Types.VARBINARY:
            //			case java.sql.Types.LONGVARBINARY:
            //			case java.sql.Types.ARRAY:
            //			case java.sql.Types.JAVA_OBJECT:
            //			case java.sql.Types.BLOB:
            //			case java.sql.Types.CLOB:
            //			case java.sql.Types.NCLOB:
            //			case java.sql.Types.DATALINK:
            //			case java.sql.Types.DISTINCT:
            //			case java.sql.Types.OTHER:
            //			case java.sql.Types.REF:
            //			case java.sql.Types.ROWID:
            //			case java.sql.Types.STRUCT:
        }
    }

    /**
     * Configures this JDBCInputFormat. This includes setting the connection
     * parameters (if necessary), establishing the connection and executing the
     * query.
     *
     * @param parameters Configuration containing all or no parameters.
     */
    @Override
    public void configure(Configuration parameters) {
        if (need_configure) {
            this.dbTypeStr = parameters.getString("type", "mysql");
            this.host = parameters.getString("host", "localhost");
            this.port = parameters.getInteger("port", 3306);
            this.dbName = parameters.getString("name", "");
            this.username = parameters.getString("username", "");
            this.password = parameters.getString("password", "");
            this.query = parameters.getString("query", "");
            this.derbyDBPath = parameters.getString("derbydbpath", System.getProperty("user.dir" + "/test/resources/derby/db;create=true;"));
        }

        if (dbURL == null) {
            DBTypes dbType = getDBType(dbTypeStr);
            if (setClassForDBType(dbType)) {
                try {
                    if (prepareConnection(dbType, username, password)) {
                        statement = dbConn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                        resultSet = statement.executeQuery(this.query);
                    }
                } catch (SQLException e) {
                    throw new IllegalArgumentException("Couldn't execute query:\t!" + e.getMessage());
                }
            }
        } else {

            try {
                if (prepareConnection(dbURL)) {
                    statement = dbConn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                    resultSet = statement.executeQuery(this.query);
                }

            } catch (SQLException e) {
                throw new IllegalArgumentException("Couldn't execute query:\t!" + e.getMessage());
            }
        }
    }

    /**
     * Checks whether all data has been read.
     *
     * @return boolean value indication whether all data has been read.
     */
    @Override
    public boolean reachedEnd() {
        try {
            if (resultSet.isLast()) {
                resultSet.close();
                statement.close();
                dbConn.close();
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException("Couldn't evaluate reachedEnd():\t" + e.getMessage());
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Couldn't access resultSet:\t" + e.getMessage());
        }
    }

    /**
     * Stores the next resultSet row in a PactRecord
     *
     * @param record target PactRecord
     * @return boolean value indicating that the operation was successful
     */
    @Override
    public boolean nextRecord(PactRecord record) {
        try {
            resultSet.next();
            ResultSetMetaData rsmd = resultSet.getMetaData();
            int column_count = rsmd.getColumnCount();
            record.setNumFields(column_count);

            for (int pos = 0; pos < column_count; pos++) {
                int type = rsmd.getColumnType(pos + 1);
                retrieveTypeAndFillRecord(pos, type, record);
            }
            return true;
        } catch (SQLException e) {
            throw new IllegalArgumentException("Couldn't read data:\t" + e.getMessage());
        } catch (NotTransformableSQLFieldException e) {
            throw new IllegalArgumentException("Couldn't read data because of unknown column sql-type:\t" + e.getMessage());
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Couldn't access resultSet:\t" + e.getMessage());
        }
    }

}
