package com.haredb.client.facade.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.util.Bytes;

import com.haredb.client.facade.bean.DataCellBean;
import com.haredb.client.facade.bean.MessageInfo;
import com.haredb.client.facade.bean.ScanResultStatusBean;
import com.haredb.hbaseclient.core.Connection;
import com.haredb.hbaseclient.core.HareTable;
import com.haredb.hbaseclient.core.QueryCriterion;
import com.haredb.hbaseclient.core.QueryResult;

public class HareQueryScan {
	private Connection connection;
	
	public HareQueryScan(Connection connection){
		this.connection = connection;
		this.connection.create();
	}
	
	public ScanResultStatusBean scanHTable(String tableName, int pageSize, int limit){
		ScanResultStatusBean resultStatusBean = new ScanResultStatusBean();
		try{
			long startTime = System.currentTimeMillis();
		    HareTable hareTable = new HareTable(connection, tableName);
		    QueryCriterion queryCriterion = new QueryCriterion();
		    queryCriterion.setHareTable(hareTable);
			
		    QueryResult result = queryCriterion.query();
	        ResultScanner rs = result.getResultScanner();
				
			List<List<String>> datas = new ArrayList<List<String>>();
			List<DataCellBean> cellHeads = new ArrayList<DataCellBean>(){
				@Override
				public boolean equals(Object o){
					ListIterator<DataCellBean> e1 = listIterator();
					DataCellBean e2 = (DataCellBean)o;
					while(e1.hasNext()){
						DataCellBean o1 = e1.next();
						if(o1.getColumnFamily().equals(e2.getColumnFamily()) && o1.getQualifier().equals(e2.getQualifier())){
							return true;
						}
					}
					return false;
				}
			};
			int rowCount = 1;
			int limitCount = 1;
			
			for(Result r : rs){
				if(rowCount >= pageSize && limitCount <= limit){
					List<String> data = new ArrayList<String>();
					KeyValue keyvalues[] = r.raw();
					for(KeyValue keyvalue:keyvalues){
						DataCellBean cellBean = new DataCellBean();
						cellBean.setColumnFamily(Bytes.toString(keyvalue.getFamily()));
						cellBean.setQualifier(Bytes.toString(keyvalue.getQualifier()));
						if(!cellHeads.equals(cellBean)){
							cellHeads.add(cellBean);
						}
					}
					for(DataCellBean cellHead : cellHeads){
						String value = Bytes.toString(r.getValue(Bytes.toBytes(cellHead.getColumnFamily()), Bytes.toBytes(cellHead.getQualifier())));
						if(value == null){
							data.add("");
						}else{
							data.add(value);
						}
					}
					datas.add(data);
					limitCount++;
				}
				rowCount++;
			}
			String results[][] = new String[datas.size()][cellHeads.size()];
			int i = 0;
			for(List<String> data : datas){
				int j = 0;
				for(int k = 0 ; k < cellHeads.size() ; k++){
					results[i][k] = "";
				}
				for(String columnValue : data){
					results[i][j] = columnValue;
					j++;
				}
				i++;
			}
			long endTime = System.currentTimeMillis();
			resultStatusBean.setResponseTime(endTime - startTime);
			resultStatusBean.setHeads(cellHeads);
			resultStatusBean.setResults(results);
			resultStatusBean.setStatus(MessageInfo.SUCCESS);
		}catch(Exception e){
			resultStatusBean.setStatus(MessageInfo.ERROR);
			resultStatusBean.setException(e.getMessage());
			return resultStatusBean;
		}
		return resultStatusBean;
		
	}
}
