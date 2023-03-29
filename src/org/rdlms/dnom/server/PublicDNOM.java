/* Copyright (c) 2020, TRUSTDB Inc.
   
   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; version 2 of the License.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.   

   You should have received a copy of the GNU General Public License
   along with this program; If not, see <http://www.gnu.org/licenses/>.
*/

package org.rdlms.dnom.server;

import org.rdlms.messagestorage.control.MessageManager;
import org.rdlms.messagestorage.model.ServiceOrderInfo;
import org.rdlms.messagestorage.model.TOSTransaction;
import org.rdlms.toss.connector.TOSSConnector;

import java.util.Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.rdlms.util.Assert;

import java.io.FileReader;
import java.io.File;
import java.util.*;
import java.nio.channels.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.net.*;
import java.util.concurrent.*;
import org.apache.log4j.Logger;

 
public class PublicDNOM {
	final static int RECEIVE_BUFFER_SIZE=4096;
	final static String DNOM_PROTOCOL_VERSION="DNOM_PROTOCOLV_0001";
	final static String TTOB_PROTOCOL_VERSION="TTOB_PROTOCOLV_0001";
	
	final static String TOPIC_STR="www.trust-db.com/trustdb_securities/JEJU_LOVE_VOUCHER_SALE_BUYER";
	final static String TRANSACTION_STR="INSERT INTO JEJU_LOVE_VOUCHER_SALE_BUYER (SYNC_ID,VOUCHER_NAME,VOUCHER_NO,BUYER_SYNC_ID,BUYER_ID,BUYER_PUB_KEY,BUYER_SIGN,SYNC_SIGN) values (@SYNC_ID,'JEJU_LOVE','3','5','Parkgildong','04CD160E72DE7556C0CCFFFD822FB102E2D1EA7FE13214ED6FFA20331B0246DFFC2AAA2BC4066F34350518F98287CF79DCB220E3CAE1C96B90531A1A4C275FE350','3046022100DC45184F2040FAEB4FBB7B27CB3B268F6252889F4362891A533FEE103FF4DAFB022100FCB8D36F8E46D00705F224173C0BE3D1F161EEEE0580F0D0D4469D28348C39A4',@SYNC_SIGN);";
	
	final static byte NETWORK_TYPE_NA=0;
	final static byte NETWORK_TYPE_MAIN=1;
	final static byte NETWORK_TYPE_TEST=2;
	 
	final static short NETWORK_ID_BIGBLOGS=1;
	
	final static short TRANSACTION_TYPE_ADMIN=1;
	final static short TRANSACTION_TYPE_DDL=2;
	final static short TRANSACTION_TYPE_ASYNC_DML=3;
	final static short TRANSACTION_TYPE_SYNC_DML=4;
	final static byte  TRANSACTION_TYPE_GET_STATUS=10;
		 	
	public byte NETWROK_TYPE=0;
	public byte NETWROK_ID=0;
	public byte TRANSACTION_TYPE=0;  
	
	protected Logger log;  // TRACE<DEBUG<INFO<WARN<ERROR<FATAL
	PublicDNOMParameters publicDnomParameters;		
	ConcurrentHashMap<String, ServiceConfig> hmAllServicesConfig = new ConcurrentHashMap<String, ServiceConfig>();  

	// HMALLServiceOrderInfo has order value for all services and tables  Key is GlobalServiceName
	ConcurrentHashMap<String, ServiceOrderInfo> hmAllServiceOrderInfo = new ConcurrentHashMap<String, ServiceOrderInfo>();

	// hmAllErrorCodes used for publisher to know messge execution result by subscriber.
	ConcurrentHashMap<String, Integer> hmAllErrorCodes;	
				
	public class PublicDNOMParameters {			
		String	dataFolderPath;			
		int		dataFileSizeMB;
		int		transactionDownloadLimit;			
		int		dnomServicePort;
		int		publisherSleepTime;
		int		subscriberSleepTime;
	}

	public class ServiceConfig {
		String serviceName;
		String tossAddrs;
		String rdlmsAddrs;
	}
	
	public PublicDNOM() {		
		publicDnomParameters = new PublicDNOMParameters();	  	  			
		hmAllErrorCodes = new ConcurrentHashMap<String, Integer>();	
		log = Logger.getRootLogger();  			
	}

	/**
	* loadConfigFile
	* load dnom_config.json and make it object and put it hmAllServiceConfig
	* @param	path	toss.config  path
	* @reutrn true - , false - 	
	*/   					
	public boolean loadConfigFile(String path) {
		boolean boolReturn=true;
		JSONParser parser = new JSONParser();
				
		try {
			Object obj = parser.parse(new FileReader(path));
			
			JSONObject configObj = (JSONObject) obj;				
			JSONObject publicDnomParam = (JSONObject) configObj.get("PublicDNOMParameters");
			
			publicDnomParameters.dataFolderPath = (String) publicDnomParam.get("dataFolderPath");
			String tStr = (String) publicDnomParam.get("dataFileSizeMB");
			publicDnomParameters.dataFileSizeMB = Integer.parseInt(tStr);
			tStr = (String) publicDnomParam.get("transactionDownloadLimit");
			publicDnomParameters.transactionDownloadLimit = Integer.parseInt(tStr);
			tStr = (String) publicDnomParam.get("dnomServicePort");
			publicDnomParameters.dnomServicePort = Integer.parseInt(tStr);
			tStr = (String) publicDnomParam.get("publisherSleepTime");
			publicDnomParameters.publisherSleepTime = Integer.parseInt(tStr);
			tStr = (String) publicDnomParam.get("subscriberSleepTime");
			publicDnomParameters.subscriberSleepTime = Integer.parseInt(tStr);
			
			JSONArray topics_groups = (JSONArray) configObj.get("ServiceConfig");
			Iterator<Object> iterator = topics_groups.iterator();					
			while(iterator.hasNext()) {
				ServiceConfig sc = new ServiceConfig();
				JSONObject topicObj= (JSONObject)iterator.next();
				sc.serviceName = (String) topicObj.get("serviceName");
				sc.tossAddrs = (String) topicObj.get("tossAddrs");
				sc.rdlmsAddrs = (String) topicObj.get("rdlmsAddrs");
				
				hmAllServicesConfig.put(sc.serviceName.toLowerCase(), sc);
		  }			  				  		  
		} catch(Exception e) {
			e.printStackTrace();			
			boolReturn=false;
		}
		return boolReturn;
	}	
		
	/**
	* FullServiceName
	* @param	fullServiceName
	* @return true - ,  false - 
	*/   					
	public boolean makeDirectories(String fullServiceName) {
		String issuer,service;
		String[] tokenArr = fullServiceName.split("/");
		if(tokenArr.length!=2) {
			//System.out.println("  Pattern !");
			return false;
		}	
		issuer = tokenArr[0];
		service = tokenArr[1];
		
		try {
			File file = new File("./data/"+issuer+"/"+service);
			if(!file.exists()) {
				file.mkdirs();				
			}			
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}		
		return true;
	}

	/**
	* Check FSN(Full Service Name) of MSG is registerd in hmAllServicesConfig, If yes, return ServiceConfig
	* @param	Full Service Name of MSG
	* @reutrn ServiceConfig Object.	
	*/   
	public ServiceConfig checkMsgsInService(String fullServiceName) {
		String issuer,service,ledger;		
		String[] tokenArr = fullServiceName.split("/");
		if(tokenArr.length!=3) {			
			log.error("Wrong Service Name : "+fullServiceName);
			//System.out.println(" Wrong Pattern ! = "+fullServiceName);
			return null;
		}	
		issuer = tokenArr[0];
		service = tokenArr[1];
		ledger = tokenArr[2];
		
		ServiceConfig SC = (ServiceConfig) hmAllServicesConfig.get(issuer+"/"+service);
		if(SC==null) {			
			log.error("Not registed Service name : "+issuer+"/"+service);
			return null;
		}				
		return SC;
	}
	
	
	/**
	* Syncronize with TTOB
	* @reutrn Error Code 0 - Success	
	*/   
	private int receiveAllMissedTransactionsFromTTOB() {		
		ServiceConfig serviceConfig;
		TOSSConnector ttobConnector=null;
		String[] tokenArr;
		String tossAddrs;		
		
		try {		
			Set<String> configSet = hmAllServicesConfig.keySet();		
		
			for(String globalServiceName : configSet) {
				serviceConfig = hmAllServicesConfig.get(globalServiceName);
				tossAddrs = serviceConfig.tossAddrs;
				tokenArr = tossAddrs.split(":");
				ttobConnector = new TOSSConnector(tokenArr[0],Integer.parseInt(tokenArr[1]));						
				ttobConnector.open();

				log.info("Service "+globalServiceName+" TOSServer : "+tossAddrs);

				// 1. get ServiceInfo from TOSS
				TOSSConnector.ServiceInfo serviceInfo = ttobConnector.getServiceInfo(globalServiceName);
				if(serviceInfo ==null) {
					log.info(globalServiceName+" not supported !");
					continue;
				}
											
				// 2. ServiceInfo has TTOB's tableName belongs a service. ServiceOrderInfo(in hmAllServiceOrderInfo) has local table list(ServiceOrderInfo).
				//    If table exists TTOB but not exist local, make intial files.				
				ServiceOrderInfo serviceOrderInfo = hmAllServiceOrderInfo.get(globalServiceName);
				for(int i=0; i<serviceInfo.tableName.length; i++) {
					if(!serviceOrderInfo.hm_table_orders.containsKey(serviceInfo.tableName[i])) {
						if(makeInitialFiles(serviceConfig, globalServiceName+"/"+serviceInfo.tableName[i])) {
							// make new table_orders 
							serviceOrderInfo.hm_table_orders.put(serviceInfo.tableName[i],new Long(1));
						} else {
							// TODO error definition
							return 9993;
						}
					}
				}
				
				//3. Sync TTOB transactions to Local(PublicDON) transactions and save them.
				long localLastOrder=0; 
				TOSTransaction[] tosTransactions=null;
				localLastOrder = serviceOrderInfo.order_in_service;

				while(true) {
					tosTransactions = ttobConnector.getTOSTransactions(globalServiceName, localLastOrder);					
					if(tosTransactions!=null) {
						if(MessageManager.saveTransactions(log, publicDnomParameters.dataFolderPath, publicDnomParameters.dataFileSizeMB, tosTransactions)) {			
							log.fatal("Can't save Transactions");
							return 9994;
						}
						localLastOrder = tosTransactions[tosTransactions.length-1].order_in_service;
						log.debug("Last Order = "+localLastOrder+" Missed Transactions = "+tosTransactions.length);
					} else {
						// There is no more new tranctions.
						log.debug("Last Order = "+localLastOrder+" Missed Transactions = 0");
						break;
					}
				}
			}		
		}catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(ttobConnector!=null) ttobConnector.close();
		}
		return 0;  	
	}	

	  
	/**
	* Generate initial files
	* @param	fullServiceName
	* @return	true Success, false fail	
	*/   		
	public boolean makeInitialFiles(ServiceConfig serviceConfig, String fullServiceName) {
		String issuer,service;
		String[] tokenArr = fullServiceName.split("/");
		boolean retValue=true;		
		Connection dbCon = null;
		if(tokenArr.length!=3) {			
			return false;
		}	
		issuer = tokenArr[0];  
		service = tokenArr[1];		
		
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
			e.printStackTrace();
			try{
				dbCon.close();
			} catch(Exception e1) {};
			log.fatal("["+serviceConfig.serviceName+"] DB ("+DBURL+") Connection Failed !!");
			Assert.assertTrue(true,"DB Connection Failed !!");	
		}
		retValue= MessageManager.makeInitialFiles(log, null, publicDnomParameters.dataFolderPath, fullServiceName, dbCon);
		
		try {
			dbCon.close();
		} catch(Exception e) {
			e.printStackTrace();
		}

		return retValue;
	}
		
	/**
	* PublicDNOM Main()	
	*/   		
	public static void main(String[] args) {
		String configFileName;
	
		if(args.length==0) {
		//System.out.println("[USAGE] java org.rdlms.dnom.server.PublicDNOM [CONFIGFILE]");
			return;
		}
  	
		configFileName = args[0];
	
		PublicDNOM publicDnom = new PublicDNOM();				
		
		System.out.println("\n"); 
		System.out.println("*======================================================*");  
		System.out.println("|                                                      |"); 
		System.out.println("|                    PublicDNOM V 0.2                  |");  
		System.out.println("|                    ----------------                  |");
		System.out.println("|                                                      |"); 
		System.out.println("|            Copyright (c) 2020, TRUSTDB Inc.          |");	        	        
		System.out.println("*======================================================*");  
		System.out.println("\n\n");
		
		int dataFileCheckMode = 0;  // 0- No Check, 1 - All Check, 2 - All-Check & Re-index. 
		
		// [STEP-1] Load Config
		System.out.println("[Read Configruations].... "+configFileName);
		if(publicDnom.loadConfigFile(configFileName)==false) {
			publicDnom.log.error("Can't find configuration file !");
			return;
		}
		System.out.println("");

		publicDnom.log.info("[Initailze Local Directories and Files] .... ");
		publicDnom.hmAllServicesConfig.forEach((key, value) -> {
			MessageManager.makeFolders(publicDnom.publicDnomParameters.dataFolderPath, (String)key);
		});
		publicDnom.log.info("");

		publicDnom.log.info("[Load all Last Orders from raw block data] .... ");
		publicDnom.hmAllServicesConfig.forEach((key, value) -> {
			ServiceOrderInfo serviceOrderInfo;
			serviceOrderInfo = MessageManager.readLastOrderFromStorage(publicDnom.log,publicDnom.publicDnomParameters.dataFolderPath,(String)key);	
			if(serviceOrderInfo!=null) {
				publicDnom.hmAllServiceOrderInfo.put(key,serviceOrderInfo);
				publicDnom.log.info("Service "+key+" -> Last Order = "+serviceOrderInfo.order_in_service);
				serviceOrderInfo.hm_table_orders.forEach((key2, value2)-> {
					publicDnom.log.info("\tTABLE "+key2+" -> Last Order = "+value2);
				});
			} else {
				publicDnom.log.info("Service "+key+" -> Last Order = 0");
			}
		}); 
		publicDnom.log.info("");

		publicDnom.log.info("[verify all data files]....");
		if(dataFileCheckMode == 0) {
			publicDnom.log.info("\tignore");
		} else if(dataFileCheckMode==1) {
			
			// TO DO			
		} else if(dataFileCheckMode==2) {
			// TO DO			
		}
		publicDnom.log.info("");
		
		publicDnom.log.info("[Syncronize with TOSS] .... ");			
		if(publicDnom.receiveAllMissedTransactionsFromTTOB()!=0) {
			publicDnom.log.error("Can't receive Transactions from TOSS !");
			return;
		}		
		publicDnom.log.info("");
		publicDnom.hmAllServicesConfig.forEach((key, value) -> {
			ServiceOrderInfo serviceOrderInfo;
			serviceOrderInfo = MessageManager.readLastOrderFromStorage(publicDnom.log,publicDnom.publicDnomParameters.dataFolderPath,(String)key);	
			if(serviceOrderInfo!=null) {
				publicDnom.hmAllServiceOrderInfo.put(key,serviceOrderInfo);
				publicDnom.log.info("Service "+key+" -> Last Order = "+serviceOrderInfo.order_in_service);
				serviceOrderInfo.hm_table_orders.forEach((key2, value2)-> {
					publicDnom.log.info("\tTABLE "+key2+" -> Last Order = "+value2);
				});
			} else {
				publicDnom.log.info("Service "+key+" -> Last Order = 0");
			}
		}); 
		publicDnom.log.info("");
		
		publicDnom.log.info("[Syncronize with TrustSQL] ....");						
		try {
			
			Set<String>  kService= publicDnom.hmAllServicesConfig.keySet();			
			ServiceConfig serviceConfig;
			SyncTrustSQLThread[] syncTrustSQLThread = new SyncTrustSQLThread[publicDnom.hmAllServicesConfig.size()];
			int counter=0;
			for(String serviceKey : kService) {				
				serviceConfig = (ServiceConfig) publicDnom.hmAllServicesConfig.get(serviceKey);
				publicDnom.log.debug("[SyncToTrustSQLThread start].... "+serviceKey);			
				syncTrustSQLThread[counter] = new SyncTrustSQLThread(publicDnom,serviceKey);
				syncTrustSQLThread[counter].start();

				// For Test start
				while(true) {		  	
					if(!syncTrustSQLThread[counter].isAlive()) {
						break;					
					} else {
						Thread.sleep(500);
					}
				}			
				// For Test end		
				counter++;
			}
		
			Thread.sleep(500);
			int doneCounter = 0;
			while(true) {		  	
				for(int i=0; i<syncTrustSQLThread.length; i++) {					
					if(!syncTrustSQLThread[i].isAlive()) {
						doneCounter+=1;
					}
				}	
				if(doneCounter==syncTrustSQLThread.length) {
					break;
				} else {
					Thread.sleep(500);
					doneCounter=0;
				}
			}		  	
		} catch (Exception e) {
			e.printStackTrace();			
		}
		publicDnom.log.info("");

		publicDnom.log.info("[Start Subscribing From TOSS]....");	
		try {        		
			ServerSocketChannel publisherServerSocketChannel = ServerSocketChannel.open();
			
			int receptionPort = publicDnom.publicDnomParameters.dnomServicePort;
			publisherServerSocketChannel.socket().bind(new InetSocketAddress(receptionPort));          
			publisherServerSocketChannel.configureBlocking(false);
			
			Set<String>  kService= publicDnom.hmAllServicesConfig.keySet();			
			ServiceConfig serviceConfig;			
			for(String serviceKey : kService) {
				serviceConfig = (ServiceConfig) publicDnom.hmAllServicesConfig.get(serviceKey);
			 	publicDnom.log.info(" SubscriberThread -> "+serviceKey);						
					
				SubscriberThread subscriberThread = new SubscriberThread(publicDnom, serviceKey);						
				subscriberThread.start();
			}
			publicDnom.log.info("");


			publicDnom.log.info("[Publisher to TOSS]....");				

			while(true) {				
				SocketChannel publisherChannel = publisherServerSocketChannel.accept(); // Accepts a connection made to this channel's socket.              
				
				if(publisherChannel!=null) {              	
					publicDnom.log.info("[Connected] "+publisherChannel.getRemoteAddress().toString());
					PublisherThread publisherThread = new PublisherThread(publisherChannel,publicDnom);
					publisherThread.start();
				}
					
				/* TODO 
					Broadcast Thread to other nodes
				*/ 
				Thread.sleep(100);
			}
		}catch (Exception e) {
			 e.printStackTrace();
		}
	}
}