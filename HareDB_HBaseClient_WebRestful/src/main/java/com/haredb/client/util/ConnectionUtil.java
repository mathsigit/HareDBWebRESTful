package com.haredb.client.util;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import com.haredb.client.facade.bean.ConnectionBean;
import com.haredb.client.facade.bean.MessageInfo;
import com.haredb.hbaseclient.core.Connection;
import com.haredb.hbaseclient.core.KerberosPrincipal;
import com.haredb.hive.metastore.connection.HiveMetaConnectionBean;
import com.haredb.hive.metastore.connection.HiveMetaConnectionBean.EnumHiveMetaStoreConnectType;
import com.haredb.hive.metastore.connection.HiveMetaConnectionBean.EnumSQLType;

public class ConnectionUtil {
	final String HDFSMETASTOREDIR="/tmp/hive/warehouse";
	public static String connectionSessionName = "connection";
	public static String hiveMetaDataSessionName = "hiveMetaConnection";
	public static Properties prop = new Properties();
	
	private Connection conn;
	private HiveMetaConnectionBean metaConn;
	private ConnectionBean connBean;
	private List<String> checkItem = null;
	
	/**
	 * @return the metaConn
	 */
	public HiveMetaConnectionBean getMetaConn() {
		return metaConn;
	}


	/**
	 * @return the conn
	 */
	public Connection getConn() {
		return conn;
	}


	public ConnectionUtil(){
		//do nothing
	}
	
	
	public ConnectionUtil(ConnectionBean connBean){
		this.connBean = connBean;
		setSetcheckItem();
	}
	
	/**
	 * Resolve ConnectionBean
	 * @return
	 */
	public MessageInfo resolveBean() {
		MessageInfo info = checkProperty();
		if(info.getStatus().equals(MessageInfo.SUCCESS) == false){
			return info;
		}
		try {
			prop.load(ConnectionUtil.class.getResourceAsStream("/connection.property"));
		
			this.conn = new Connection(connBean.getZookeeperHost(), connBean.getZookeeperPort());
			this.conn.setNameNodeHostPort(connBean.getNameNodeHostPort());
			this.conn.setRmAddressHostPort(connBean.getRmAddressHostPort());
			this.conn.setRmSchedulerAddressHostPort(connBean.getRmSchedulerAddressHostPort());
			this.conn.setRmResourceTrackerAddressHostPort(connBean.getRmResourceTrackerAddressHostPort());
			this.conn.setRmAdminAddressHostPort(connBean.getRmAdminAddressHostPort());
			this.conn.setMrJobhistoryAddress(connBean.getMrJobhistoryAddress());
			this.conn.setYarnNodeManagerAuxServices(prop.getProperty("yarnNodeManagerAuxServices"));
			
			this.conn.setRegisterCoprocessorHdfsPath(connBean.getNameNodeHostPort());
			
			if(connBean.getSolrZKHosts() != null) {
				this.conn.setSolrZKHosts(connBean.getSolrZKHosts());
			}
			
			if(connBean.isEnableKerberos()) {
				setCheckKerberosItem();
				info = checkProperty();
				if(info.getStatus().equals(MessageInfo.SUCCESS) == false){
					return info;
				}
				this.conn.setKerberosPrincipal(new KerberosPrincipal(connBean.getHbaseMasterPrincipal(),connBean.getHbaseRegionServerPrincipal(),
						connBean.getDfsNameNodePrincipal(),connBean.getDfsDataNodePrincipal(),connBean.getHiveMetaStorePrincipal(),
						connBean.getYarnResourceMgrPrincipal(),connBean.getYarnNodeMgrPrincipal()) );
			}
			this.conn.create();
			
			
			if(prop.getProperty("platform").equals("cloudera")){
				this.conn.setYarnApplicationClasspath(prop.getProperty("yarnApplicationClasspath"));
			}
			
			this.metaConn = new HiveMetaConnectionBean();
			if(connBean.getHiveConnType().toString().equals(EnumHiveMetaStoreConnectType.LOCAL.toString())){
				this.metaConn.setMetaStoreConnectDriver(connBean.getMetaStoreConnectDriver());
				this.metaConn.setMetaStoreConnectURL(connBean.getMetaStoreConnectURL());
				this.metaConn.setMetaStoreConnectUserName(connBean.getMetaStoreConnectUserName());
				this.metaConn.setMetaStoreConnectPassword(connBean.getMetaStoreConnectPassword());
			}else if(connBean.getHiveConnType().toString().equals(EnumHiveMetaStoreConnectType.REMOTE.toString())){
				this.metaConn.setMetaUris(connBean.getMetaUris());
			}else if(connBean.getHiveConnType().toString().equals(EnumHiveMetaStoreConnectType.SERVER2.toString())){
				this.metaConn.setHiveServer2Url(connBean.getHiveServer2Url());
			}else{
				this.metaConn.setEmbedPath(connBean.getEmbedPath());
			}
			this.metaConn.setHiveConnType(connBean.getHiveConnType());
			this.metaConn.setDbName(connBean.getDbName());
			this.metaConn.setDbBrand(connBean.getDbBrand());
			this.metaConn.setHdfsMetaStoreDir(connBean.getNameNodeHostPort()+HDFSMETASTOREDIR);
			
			//set connection key
			info.setConnectionKey(genConnectionKey(10,Mode.ALPHANUMERIC));
			
		} catch (Exception e) {
			info.setStatus(MessageInfo.ERROR);
			info.setException(e.getMessage());
		}
		return info;
		
	}
	
	
	public Connection getConnection(HttpServletRequest request) {
		try {
			Connection connection = (Connection)request.getSession().getAttribute(connectionSessionName);
			if(connection == null) {
				throw new Exception("Session time out, or session not found. please reconnect.");
			}
			return connection;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		
	}
	
	public HiveMetaConnectionBean getHiveMetaDataConnection(HttpServletRequest request){		
		try {
			HiveMetaConnectionBean hiveMetaConnectionBean = (HiveMetaConnectionBean)request.getSession().getAttribute(hiveMetaDataSessionName);
			if(hiveMetaConnectionBean == null) {
				throw new Exception("Session time out, or session not found. please reconnect.");
			}
			return hiveMetaConnectionBean;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
	}
	
	public enum Mode {
	    ALPHA, ALPHANUMERIC, NUMERIC 
	}
	
	public String genConnectionKey(int length, Mode mode) throws Exception {

		StringBuffer buffer = new StringBuffer();
		String characters = "";

		switch(mode){
		
		case ALPHA:
			characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
			break;
		
		case ALPHANUMERIC:
			characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
			break;
	
		case NUMERIC:
			characters = "1234567890";
		    break;
		}
		
		int charactersLength = characters.length();

		for (int i = 0; i < length; i++) {
			double index = Math.random() * charactersLength;
			buffer.append(characters.charAt((int) index));
		}
		return buffer.toString();
	}
	
	private MessageInfo checkProperty(){
		MessageInfo info = new MessageInfo();
		try {
			String propertyName = null;
			Object value = null;
			BeanInfo beanInfo = Introspector.getBeanInfo(ConnectionBean.class);
			for (PropertyDescriptor propertyDesc : beanInfo.getPropertyDescriptors()) {
			    propertyName = propertyDesc.getName();
			    if(checkItem.contains(propertyName)) {
			    	value = propertyDesc.getReadMethod().invoke(connBean);
				    if(value == null) {
				    	throw new Exception(propertyName+" is null!");
				    }
			    }
			    	
			    
			}
			info.setStatus(MessageInfo.SUCCESS);
		} catch (Exception e) {
			info.setStatus(MessageInfo.ERROR);
			info.setException(e.getMessage());
			return info;
		}
		
		return info;
	}
	
	private void setSetcheckItem() {
		if(checkItem == null) {
			checkItem = new ArrayList<String>();
			checkItem.add("zookeeperHost");
			checkItem.add("zookeeperPort");
			checkItem.add("nameNodeHostPort");
			checkItem.add("rmAddressHostPort");
			checkItem.add("rmSchedulerAddressHostPort");
			checkItem.add("rmResourceTrackerAddressHostPort");
			checkItem.add("rmAdminAddressHostPort");
			checkItem.add("mrJobhistoryAddress");
			
			checkItem.add("hiveConnType");
			if(connBean.getHiveConnType().toString().equals(EnumHiveMetaStoreConnectType.SERVER2.toString())) {
				checkItem.add("hiveServer2Url");
			}
			checkItem.add("metaStoreConnectDriver");
			checkItem.add("metaStoreConnectURL");
			checkItem.add("metaStoreConnectUserName");
			checkItem.add("metaStoreConnectPassword");
			
			checkItem.add("enableKerberos");
			checkItem.add("dbBrand");
			checkItem.add("dbName");
			
		}
		
	}
	
	private void setCheckKerberosItem(){
		if(checkItem != null) {
			checkItem.clear();
		} else {
			checkItem = new ArrayList<String>();
		}
		checkItem.add("hbaseMasterPrincipal");
		checkItem.add("hbaseRegionServerPrincipal");
		checkItem.add("dfsNameNodePrincipal");
		checkItem.add("dfsDataNodePrincipal");
		checkItem.add("hiveMetaStorePrincipal");
		checkItem.add("yarnResourceMgrPrincipal");
		checkItem.add("yarnNodeMgrPrincipal");
	}
	
}
