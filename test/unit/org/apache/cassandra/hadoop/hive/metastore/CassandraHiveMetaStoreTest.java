package org.apache.cassandra.hadoop.hive.metastore;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.cassandra.CleanupHelper;
import org.apache.cassandra.EmbeddedServer;
import org.apache.cassandra.config.ConfigurationException;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.thrift.transport.TTransportException;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test plumbing of CassandraHiveMetaStore
 * 
 * @author zznate
 */
public class CassandraHiveMetaStoreTest extends CleanupHelper {


    @BeforeClass
    public static void setup() throws TTransportException, IOException, InterruptedException, ConfigurationException
    {
        EmbeddedServer.startBrisk();                
    }

    @Test
    public void testSetConf() 
    {
        // INIT procedure from HiveMetaStore:
        //empty c-tor
        // setConf
        CassandraHiveMetaStore metaStore = new CassandraHiveMetaStore();
        metaStore.setConf(buildConfiguration());        
    }
    
    @Test(expected=CassandraHiveMetaStoreException.class)
    public void testSetConfConnectionFail() 
    {
        // INIT procedure from HiveMetaStore:
        //empty c-tor
        // setConf
        CassandraHiveMetaStore metaStore = new CassandraHiveMetaStore();
        // Throw a runtime exception if we can ping cassandra?
        Configuration conf = buildConfiguration();
        conf.setInt("cassandra.connection.port",8181);
        metaStore.setConf(conf);        
    }
    
    @Test
    public void testCreateDeleteDatabaseAndTable() throws Exception 
    {
        CassandraHiveMetaStore metaStore = new CassandraHiveMetaStore();
        metaStore.setConf(buildConfiguration());
        Database database = new Database("db_name", "My Database", "file:///tmp/", new HashMap<String, String>());
        metaStore.createDatabase(database);
        Database foundDatabase = metaStore.getDatabase("db_name");
        assertEquals(database, foundDatabase);
        
        Table table = new Table();
        table.setDbName("db_name");
        table.setTableName("table_name");
        metaStore.createTable(table);
        
        Table foundTable = metaStore.getTable("db_name", "table_name");
        assertEquals(table, foundTable);
        
        assertEquals(1,metaStore.getAllDatabases().size());
        assertEquals(1,metaStore.getAllTables("db_name").size());
        
        metaStore.dropTable("db_name", "table_name");
        assertNull(metaStore.getTable("db_name", "table_name"));
        assertEquals(0,metaStore.getAllTables("db_name").size());
    }
    
    @Test
    public void testFindEmptyPatitionList() throws Exception 
    {
        CassandraHiveMetaStore metaStore = new CassandraHiveMetaStore();
        metaStore.setConf(buildConfiguration());
        Database database = new Database("db_name", "My Database", "file:///tmp/", new HashMap<String, String>());
        metaStore.createDatabase(database);
        Database foundDatabase = metaStore.getDatabase("db_name");
        assertEquals(database, foundDatabase);
        
        Table table = new Table();
        table.setDbName("db_name");
        table.setTableName("table_name");
        metaStore.createTable(table);
        
        List<String> partitionNames = metaStore.listPartitionNames("db_name", "table_name", (short) 100);
        assertEquals(0, partitionNames.size());
        partitionNames = metaStore.listPartitionNames("db_name", "table_name", (short) -1);
        assertEquals(0, partitionNames.size());
    }
    
    @Test
    public void testAlterTable() throws Exception 
    {
        CassandraHiveMetaStore metaStore = new CassandraHiveMetaStore();
        metaStore.setConf(buildConfiguration());
        
        Table table = new Table();
        table.setDbName("alter_table_db_name");
        table.setTableName("orig_table_name");
        metaStore.createTable(table);
        
        Table foundTable = metaStore.getTable("alter_table_db_name", "orig_table_name");
        assertEquals(table, foundTable);
        
        Table altered = new Table();
        altered.setDbName("alter_table_db_name");
        altered.setTableName("new_table_name");
               
        metaStore.alterTable("alter_table_db_name", "orig_table_name", altered);
        assertEquals(1,metaStore.getAllTables("alter_table_db_name").size());
        
    }
    
    @Test
    public void testAlterDatabaseTable() throws Exception 
    {
        CassandraHiveMetaStore metaStore = new CassandraHiveMetaStore();
        metaStore.setConf(buildConfiguration());
        Database database = new Database("alter_db_db_name", "My Database", "file:///tmp/", new HashMap<String, String>());
        metaStore.createDatabase(database);
        
        Table table = new Table();
        table.setDbName("alter_db_db_name");
        table.setTableName("table_name");
        metaStore.createTable(table);
        
        Table foundTable = metaStore.getTable("alter_db_db_name", "table_name");
        assertEquals(table, foundTable);
        Database altered = new Database("alter_db_db_name2", "My Database", "file:///tmp/", new HashMap<String, String>());
        metaStore.alterDatabase("alter_db_db_name", altered);
        
        assertEquals(1,metaStore.getAllTables("alter_db_db_name2").size());
        
    }
    
    @Test
    public void testAddParition() throws Exception 
    {
        CassandraHiveMetaStore metaStore = new CassandraHiveMetaStore();
        metaStore.setConf(buildConfiguration());
        Database database = new Database("alter_part_db", "My Database", "file:///tmp/", new HashMap<String, String>());
        metaStore.createDatabase(database);
        Database foundDatabase = metaStore.getDatabase("alter_part_db");
        assertEquals(database, foundDatabase);
        
        Table table = new Table();
        table.setDbName("alter_part_db");
        table.setTableName("table_name");
        metaStore.createTable(table);
        
        Partition part = new Partition();        
        part.setDbName("alter_part_db");
        part.setTableName("table_name");
        List<String> partValues = new ArrayList<String>();
        partValues.add("cassandra://localhost:9160/user/hive/warehouse/mydb.db/invites/ds=2008-08-08");
        partValues.add("cassandra://localhost:9160/user/hive/warehouse/mydb.db/invites/ds=2008-08-15");
        part.setValues(partValues);
        metaStore.addPartition(part);

        
        Partition foundPartition = metaStore.getPartition("alter_part_db", "table_name", partValues);
        assertEquals(part,foundPartition);

    }
    
    
    private Configuration buildConfiguration() 
    {
        Configuration conf = new Configuration();
        conf.set(CassandraClientHolder.CONF_PARAM_HOST, "localhost");
        conf.setInt(CassandraClientHolder.CONF_PARAM_PORT, DatabaseDescriptor.getRpcPort());
        conf.setBoolean(CassandraClientHolder.CONF_PARAM_FRAMED, true);
        conf.setBoolean(CassandraClientHolder.CONF_PARAM_RANDOMIZE_CONNECTIONS, true);
        return conf;
    }
}