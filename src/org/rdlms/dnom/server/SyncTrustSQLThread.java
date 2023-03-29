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
 
import org.rdlms.dnom.server.PublicDNOM.ServiceConfig;
import org.rdlms.messagestorage.control.MessageManager;
import org.rdlms.messagestorage.model.ServiceOrder;
import org.rdlms.messagestorage.model.ServiceOrderInfo;
import org.rdlms.messagestorage.model.TOSTransaction;
import org.rdlms.messagestorage.model.TableOrder;
import org.rdlms.trustsql.TrustSQLManager;
import org.rdlms.util.Assert;

import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;

import org.apache.log4j.Logger;


/**
* class SyncToRDLMSThread
*/

class SyncTrustSQLThread extends Thread{
	
	private Logger log;// DEBUG<INFO<WARN<ERROR<FATAL
	private PublicDNOM publicDnom;
	private PublicDNOM.PublicDNOMParameters publicDnomParameters;
		
	String globalServiceName;
	ServiceConfig serviceConfig;
	ServiceOrderInfo serviceOrderInfo;
	
	public SyncTrustSQLThread(PublicDNOM publicDnom, String globalServiceName) {
		this.publicDnom = publicDnom;
		this.log = publicDnom.log;		
		this.publicDnomParameters = publicDnom.publicDnomParameters;
		this.globalServiceName = globalServiceName;
		this.serviceOrderInfo = publicDnom.hmAllServiceOrderInfo.get(globalServiceName);
		this.serviceConfig = publicDnom.hmAllServicesConfig.get(globalServiceName);
 		return;	
 	}


	/**
	* SyncToRDLMSTrhead Main
	*/
	public void run() {		
		Connection dbCon = null;			
		
		log.info("SyncTrustSQLThread for "+globalServiceName+", ThreadID("+this.getId()+") start!");
		//System.out.println("SyncTrustSQLThread for "+globalServiceName+", ThreadID("+this.getId()+") start!");
		if(serviceOrderInfo==null) {
			log.debug("--"+globalServiceName+" has no messages in local MessageStorage  -> thread exit");
			return;
		}		
		try {
			// 1. make TrustSQL connection
			String[] tokenArr = globalServiceName.split("/");
			Class.forName("org.mariadb.jdbc.Driver");
			String DBURL = serviceConfig.rdlmsAddrs+"/"+tokenArr[1];
			String DBUSR = tokenArr[0];
			String DBUSRPW="1234";		
			String issuerName = tokenArr[0];
			String serviceName = tokenArr[1];
			
			dbCon = DriverManager.getConnection(DBURL,DBUSR,DBUSRPW);

			// 1. calculate how many messages in the service are synced with TrustSQL
			// 		tableNames[] = SELECT TABLE_NAME FROM INFORMATION_SCEMA.TABLES WHERE TABLE_SCHEMA='SERVICENAME';
			// 		triggerNames[] = SELECT TRIGGER_NAME FROM INFORMATION_SCEMA.TABLES WHERE TRIGGER_SCHEMA='SERVICENAME';
			// 		for( tableNames[]) totalTableRowCount = SELECT COUNT(*) FROM 'SERVICENAME.tableNames[i]'
			long totalMessageCountInTrustSQL=0;
			HashMap<String, String> map;			
			map = TrustSQLManager.selectAllTableNames(log, dbCon, serviceName);

			/*
			흠.. 여기서 error 테이블 만들어야지.. 왜 안만드냐..map
			그리고.. 테스트를 위해서라도.. 하나 더 띠워야 겠어.. 데이터 파일 다르게 해서.. rdbms하나더 띠우자.
			아니면.. 음.. 조그만하게.. 하나. 만들어서 복제 시키던가.. 그것도 괜찮을것 같다. 
			한 32GB 짜리로.. 만들면.. 될것 같다. 8개 만들어도.. 256GB 니까. 할만할듯.. 
			*/
			if(map!=null) {			
				Iterator<String> keys = map.keySet().iterator();
				while(keys.hasNext() ){
					String key = keys.next();
					
					int sqlE;
					sqlE = TrustSQLManager.createSQLResultTable(log, dbCon, key);
					if(sqlE!=0) {
						log.fatal("Can't create TruSTSQL Result Table for "+key);
						Assert.assertTrue(sqlE==0,"Can't create TruSTSQL Result Table for "+key);
					} 
				}

				totalMessageCountInTrustSQL += map.size();
				log.trace(map.size()+" Tables are founded in "+serviceName);
				
				String triggerNames[] = TrustSQLManager.selectAllTriggerNames(log, dbCon, serviceName);
				if(triggerNames!=null) totalMessageCountInTrustSQL += triggerNames.length;
				log.trace(triggerNames.length+" Triggers are founded in "+serviceName);
					
				int tableRows[] = TrustSQLManager.selectTableRows(log,dbCon, serviceName, map);	 

				long totalRow=0;
				if(tableRows!=null) {
					for(int i=0; i<tableRows.length; i++) {
						log.trace("##table ["+i+"] rows = "+tableRows[i]);				
						totalRow+=tableRows[i];						
					}
				}
				log.trace("##rows quantity = "+totalRow);				
				totalMessageCountInTrustSQL+=totalRow;
				//System.out.println("##table rows="+totalRow);
			}

			log.debug("\t"+globalServiceName+" -> total Records amount In Tables = "+totalMessageCountInTrustSQL);
			log.debug("\t"+globalServiceName+" -> Total Messages amount In Raw Data Blocks = "+serviceOrderInfo.order_in_service);
					
			if(totalMessageCountInTrustSQL==serviceOrderInfo.order_in_service) {
				log.debug("\tNo more messages to sync -> thread exit");
				return;
			}

			Assert.assertTrue(totalMessageCountInTrustSQL<serviceOrderInfo.order_in_service,globalServiceName+" has fatal error, total records ("+totalMessageCountInTrustSQL+") in tables are more than raw messages ("+serviceOrderInfo.order_in_service+") in the raw data blocks stacks it from TOSA!");
			
			// 2. find oit and transctions and execute it.
			long startOIS = totalMessageCountInTrustSQL+1;
			long endOIS =  serviceOrderInfo.order_in_service;

			ServiceOrder serviceOrder;
			TableOrder tableOrder;
			TOSTransaction tosTransaction;
			ConcurrentHashMap<String,RandomAccessFile> hmAllFilesR = new ConcurrentHashMap<String, RandomAccessFile>();
			int executionResultCode;
			for(long i=startOIS; i<=endOIS; i++) {
				serviceOrder = MessageManager.readServiceOrder(log, hmAllFilesR, publicDnomParameters.dataFolderPath, globalServiceName, i);
				tableOrder = MessageManager.readTableOrder(log, hmAllFilesR, publicDnomParameters.dataFolderPath, globalServiceName,  serviceOrder);	
				tosTransaction = MessageManager.readTOSTransaction(log, hmAllFilesR, publicDnomParameters.dataFolderPath, issuerName, globalServiceName, serviceOrder.table_name, tableOrder);
				executionResultCode = TrustSQLManager.executeStatement(log, null, dbCon, tosTransaction, false);
				if(executionResultCode!=0) {
					Assert.assertTrue(true, " Execution Fail ! ERROR = "+executionResultCode);
				}
			}

			// 3. delete Duplicated record in XXXX_ERROR Table.
			TrustSQLManager.clearDupResultTable(log,dbCon, serviceName, map);	 



			// 4. close opend files.
			RandomAccessFile raFile=null;
			Set<String> set = hmAllFilesR.keySet();
			for(String key : set) {
				raFile = hmAllFilesR.get(key);
				raFile.close();
			}


		} catch(Exception e) {
		//	System.out.println(e.getMessage());
		//	e.printStackTrace();
		} finally {
			if(dbCon!=null) {
				try {
					dbCon.close();
				} catch(Exception dbE) { 
					dbE.printStackTrace();
				}
			}
		}		
		log.info("--"+globalServiceName+" Sync. OK.. -> thread exit");				 			
 	} // function run 
}

