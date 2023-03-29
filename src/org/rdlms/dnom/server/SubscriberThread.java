/* Copyright (c) 2020, TRUSTDB Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; If not, see <http://www.gnu.org/licenses/>.
*/

package org.rdlms.dnom.server;

import org.rdlms.messagestorage.control.MessageManager;
import org.rdlms.messagestorage.model.ServiceOrderInfo;
import org.rdlms.messagestorage.model.TOSTransaction;
import org.rdlms.toss.connector.TOSSConnector;
import org.rdlms.trustsql.TrustSQLManager;
import org.rdlms.util.Assert;

import java.io.RandomAccessFile;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.*;
import org.apache.log4j.Logger;

 
/**
* class SubscriberThread 
*/

class SubscriberThread extends Thread{	
	final static int RECEIVE_BUFFER_SIZE=1024*8;
	
	private ConcurrentHashMap<String,RandomAccessFile> hmAllFiles;// Opend for RW !!!!
	private ConcurrentHashMap<String, Integer> hmAllErrorCodes;
	
	private PublicDNOM publicDnom;
	private PublicDNOM.ServiceConfig serviceConfig;	
	private ServiceOrderInfo serviceOrderInfo;	
	private PublicDNOM.PublicDNOMParameters publicDnomParameters;	
	private String globalServiceName;
	protected Logger log;// DEBUG<INFO<WARN<ERROR<FATAL	
		
	public SubscriberThread(PublicDNOM publicDnom, String globalServiceName) {
		this.publicDnom = publicDnom;
		this.log = publicDnom.log;		
		this.publicDnomParameters = publicDnom.publicDnomParameters;
		this.globalServiceName = globalServiceName;
		this.serviceOrderInfo = publicDnom.hmAllServiceOrderInfo.get(globalServiceName);
		this.serviceConfig = publicDnom.hmAllServicesConfig.get(globalServiceName);
		this.hmAllErrorCodes = publicDnom.hmAllErrorCodes;
		this.hmAllFiles = new ConcurrentHashMap<String,RandomAccessFile>(); 
 		return;	
 	}

	static int partition(TOSTransaction[] tosTransactions, int start, int end) {
		TOSTransaction pivot = tosTransactions[(start+end)/2];
		while(start<=end) {
			while(tosTransactions[start].order_in_table  <pivot.order_in_table) start++;
			while(tosTransactions[end].order_in_table>pivot.order_in_table) end--;
			if(start<=end) {
				TOSTransaction tmp = tosTransactions[start];
				tosTransactions[start]=tosTransactions[end];
				tosTransactions[end]=tmp;
				start++;
				end--;
			}
		}
		return start;
	}

	/*//I can't remember sorting of transacions is required or not.- 2021.04.03		
	static TOSTransaction[] quickSortTransactions(TOSTransaction[] tosTransactions, int start, int end) {
		int p = partition(tosTransactions, start, end);
		if(start		<p-1)
			quickSort(tosTransactions, start, p-1);
		if(p<end)
			quickSort(tosTransactions, p, end);
		return tosTransactions;
	}	
	*/
		
	/**
	* SubscriberThread�� Main Process	
	*/
	public void run() {	
		int retCode;
		
		TOSSConnector ttobConnector=null;		
		Connection dbCon = null;
		
		// Connect to TOSS
		String 		ttobAddrs = serviceConfig.tossAddrs;
		String[] 	tokenArr = ttobAddrs.split(":");													
		ttobConnector = new TOSSConnector(tokenArr[0],Integer.parseInt(tokenArr[1]));	
		if(ttobConnector.open()!=true) {
			log.fatal("["+serviceConfig.serviceName+"] TOSS ("+ttobAddrs+") Connection Failed !!");
			Assert.assertTrue(log,false,"TOSS Connection Failed !!"); 
		} 
	
		// Connect to RDLMS.
		tokenArr = serviceConfig.serviceName.split("/");
		String issuer = tokenArr[0];
		String service = tokenArr[1];
		String DBURL = serviceConfig.rdlmsAddrs+"/"+service;
		String DBUSR = issuer;
		String DBUSRPW="1234";
		try {
			Class.forName("org.mariadb.jdbc.Driver");
			
			dbCon = DriverManager.getConnection(
			DBURL,
			DBUSR,
			DBUSRPW);
		} catch(Exception e) {
			System.out.println(e.getMessage());
			try{
				dbCon.close();
			} catch(Exception e1) {};
			log.fatal("["+serviceConfig.serviceName+"] DB ("+DBURL+") Connection Failed !!");
			Assert.assertTrue(log,false,"DB Connection Failed !!");	
		}
	
		// Main Loop of SubsriberThread
		String[] fullServiceNames;
		try {	
			while(true) { 							
				long local_order_in_service=0;
				TOSTransaction[] tosTransactions=null;
				// serviceOrderInfo -> null means there is no message in local
				if(serviceOrderInfo!=null) {					
					local_order_in_service = serviceOrderInfo.order_in_service;					
				} else {
					serviceOrderInfo = new ServiceOrderInfo();
					serviceOrderInfo.order_in_service = 0;
					serviceOrderInfo.hm_table_orders = new ConcurrentHashMap<String, Long>();
				}
				
				// 1. get new Messages from TTOB.
				//System.out.println("1.Get new Messages from TTOB "+globalServiceName+"   "+local_order_in_service);
				tosTransactions = ttobConnector.getTOSTransactions(globalServiceName,local_order_in_service);					
				if(tosTransactions==null) {		
					//System.out.println("------------------------------");
					//System.out.println("No new tranactions!");		
					//System.out.println("Current order_in_service="+serviceOrderInfo.order_in_service);
					Thread.sleep(10);
					continue;	// No new transactions. 
				}	

				// 2. update serviceOrderInfo object first. 
				//System.out.println("2. update serviceOrderInfo object first. ");
				ConcurrentHashMap<String, Long> hmTableOrders = serviceOrderInfo.hm_table_orders;
				for(int i=0; i<tosTransactions.length; i++) {
					serviceOrderInfo.order_in_service = tosTransactions[i].order_in_service;
					if(serviceOrderInfo.hm_table_orders.containsKey(tosTransactions[i].gt_name)) {
						// update new order_in_table
						serviceOrderInfo.hm_table_orders.replace(tosTransactions[i].gt_name, new Long(tosTransactions[i].order_in_table));
					} else {
						serviceOrderInfo.hm_table_orders.put(tosTransactions[i].gt_name, new Long(tosTransactions[i].order_in_table));
						MessageManager.makeFilesForNewTable(log, hmAllFiles, publicDnomParameters.dataFolderPath, tosTransactions[i].gt_name, dbCon);
					}
				}
	
				// TODO
				// Need to check sorting of transacions is required or not.- 2021.04.03
				//		tosTransactions = QuickSortTransactions(tosTransactions,0,tosTransactions.length-1);
									
				// TODO
				// !!!!!!!!!!!!!  Oooops.... maybe new table can be showed at getTOS before getServiceInfo!!!!!!!!!!!!!!!!!!!!!!
				// !!!!!!!!!!!!!  Oooops.... maybe new table can be showed at getTOS before getServiceInfo!!!!!!!!!!!!!!!!!!!!!!
				// !!!!!!!!!!!!!  Oooops.... maybe new table can be showed at getTOS before getServiceInfo!!!!!!!!!!!!!!!!!!!!!!
				// !!!!!!!!!!!!!  Oooops.... maybe new table can be showed at getTOS before getServiceInfo!!!!!!!!!!!!!!!!!!!!!!
				// !!!!!!!!!!!!!  Oooops.... maybe new table can be showed at getTOS before getServiceInfo!!!!!!!!!!!!!!!!!!!!!!

				// 3. Save new messages and sync to TrustSQL
				//System.out.println("3. Save new messages and sync to TrustSQL");
				//retCode= saveAndInsertTOSTransactions(dbCon, globalServiceName, tosTransactions);

				boolean bRet;

				// TODO.여기에 BatchInsert 기능을 넣어주자. 그럼 빨라지겠지. --------- 작업 중.시작
				if(tosTransactions.length>1) {					
					bRet=MessageManager.saveTranactions(log, publicDnomParameters.dataFolderPath, publicDnomParameters.dataFileSizeMB, hmAllFiles, tosTransactions); 			
					Assert.assertTrue(bRet=true,"Can't save raw messages to storage ! \n"); 
					if(bRet==true) {
						retCode=TrustSQLManager.executeBatchStatement(log, hmAllErrorCodes, dbCon, tosTransactions, false);
						Assert.assertTrue(retCode==0,"Message Statement Excecution Failed! \n"); 
					}
				}

				// ------------------------ 작업 중..끝

				for(int i=0; i<tosTransactions.length; i++) {
					bRet=MessageManager.saveTranaction(log, publicDnomParameters.dataFolderPath, publicDnomParameters.dataFileSizeMB, hmAllFiles, tosTransactions[i]); 			
					if(bRet==true) {
						retCode=TrustSQLManager.executeStatement(log, hmAllErrorCodes, dbCon, tosTransactions[i], false);
						Assert.assertTrue(retCode==0,"Message Statement Excecution Failed! \n"); 
					}
				}
				//System.out.println("Last serviceOrderInfo.order_in_service="+serviceOrderInfo.order_in_service);

				// 4. get new tables from TTOB.
				//System.out.println("4. // 4. get new tables from TTOB.");
				TOSSConnector.ServiceInfo serviceInfo = ttobConnector.getServiceInfo(serviceConfig.serviceName);
				
				if(serviceInfo==null) {
					log.info("Not supported servcie");
					continue;
				}

				// 5. If there is new tables , setup for them. -> 
				// serviceOrderInfo.hm_table_orders have local table list.
				// serviceInfo.tableNames[] has TTOB table list.
				/* This routines no need any more, because inital files are created at step 2.update serviceOrderInfo ojbect first.				
				Set<String> sLocalFile = serviceOrderInfo.hm_table_orders.keySet();									
				for(int i=0; i<serviceInfo.tableName.length; i++) {			
					boolean find=false;
					for(String localFileName : sLocalFile) {
						if(localFileName.equals(serviceInfo.tableName[i])) {
							find=true;
						}			
					}
					if(find==false) {				
						if(MessageManager.makeFilesForNewTable(log, hmAllFiles, publicDnomParameters.dataFolderPath, serviceInfo.tableName[i], dbCon)) {
							serviceOrderInfo.hm_table_orders.put(serviceInfo.tableName[i], new Long(0));
						}else {
							return;
						}
					}
				} */						
			} // while
		} catch (SocketException se) {
			//log.fatal(se.toString());
			Assert.assertTrue(log,false,"TOSS Connection Failed !! Stopped"); 
		} catch (Exception e) {		
			//log.fatal(e.toString());
			Assert.assertTrue(log,false,"DNOM meet exception !! Stopped"); 
		} finally {
			// TODO All files opend in hmAllFiles should be closed !!!!!!!!!!!!!!!!!!
			//
		}
 	} // function run 
 	
	
}
					