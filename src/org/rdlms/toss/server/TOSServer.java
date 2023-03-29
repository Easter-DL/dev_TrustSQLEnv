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

package org.rdlms.toss.server;

import java.util.Iterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.rdlms.messagestorage.control.MessageManager;
import org.rdlms.messagestorage.model.ServiceOrderInfo;
import org.rdlms.wallet.WalletManager;

import java.io.FileReader;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.*;
import java.net.*;
import java.util.concurrent.*;
import org.apache.log4j.Logger;
 
public class TOSServer {	
	
	TOSSConfig tossConfig;

	// HMALLServiceOrderInfo has order value for all services and tables
	ConcurrentHashMap<String, ServiceOrderInfo> hmAllServiceOrderInfo = new ConcurrentHashMap<String, ServiceOrderInfo>();
  	
	// hmAllTopicConfig has configuration informs from ttob.config file
	ConcurrentHashMap<String, Object> hmAllTopicsConfig = new ConcurrentHashMap<String, Object>();
	
	// To avoid abusing FILE hander, manage handlers in HM
  	ConcurrentHashMap<String,RandomAccessFile> hmAllFilesRW;    // Opend for RW !!!! Threads use it..
  	ConcurrentHashMap<String,RandomAccessFile> hmAllFilesR;    // Opend for RW !!!!  Threads use it..
  
	WalletManager walletManager = new WalletManager();
	  
	protected Logger log;  // DEBUG<INFO<WARN<ERROR<FATAL

	public final static byte[] MAGIC_CODE = { 1,2,3,4 };

	public final static String commonPassword ="1234";

	public class TOSSConfig {
		public TOSSParameters tossParameters;
		public ServiceConfig[] serviceConfigs;
	}
	
	public class TOSSParameters {		
		public int publish_service_port;
		public int subscribe_service_port;	
		public String	dataFolderPath;			
		public short dataFileSizeMB;
		public int transactionDownloadLimit;			
	}	

	public class ServiceConfig {			
		public String keyFilePath;
		public String[] whiltelistAddrs;
	}	
	
	public TOSServer() {
		tossConfig = new TOSSConfig();
		tossConfig.tossParameters = new TOSSParameters();
		log = Logger.getRootLogger();  	
		this.hmAllFilesRW = new ConcurrentHashMap<String,RandomAccessFile>();      
		this.hmAllFilesR = new ConcurrentHashMap<String,RandomAccessFile>();      
	}

	/**
	* 
	* @param	path	toss.config  path
	* @reutrn true - , false - 	
	*/   					
	public boolean loadConfigFile(String path) {		
		JSONParser parser = new JSONParser();
		
		try {
			Object obj = parser.parse(new FileReader(path));
						
			JSONObject configObj = (JSONObject) obj;				
			JSONObject tossParamObj = (JSONObject) configObj.get("TOSSParameters");
			String tStr = (String) tossParamObj.get("publish_service_port");
			tossConfig.tossParameters.publish_service_port = Integer.parseInt(tStr);
			tStr = (String)tossParamObj.get("subscribe_service_port");
			tossConfig.tossParameters.subscribe_service_port = Integer.parseInt(tStr);
			tossConfig.tossParameters.dataFolderPath = (String) tossParamObj.get("dataFolderPath");
			tStr = (String)tossParamObj.get("dataFileSizeMB");
			tossConfig.tossParameters.dataFileSizeMB = (short) Integer.parseInt(tStr);
			tStr = (String)tossParamObj.get("transactionDownloadLimit");
			tossConfig.tossParameters.transactionDownloadLimit = Integer.parseInt(tStr);
			
			JSONArray topics_groups = (JSONArray) configObj.get("ServiceConfig");
			Iterator<Object> iterator = topics_groups.iterator();			  
			while(iterator.hasNext()) {
				ServiceConfig sc = new ServiceConfig();
				
				JSONObject topicObj= (JSONObject)iterator.next();			  	
				String pattern = (String) topicObj.get("serviceName");
				
				String key_file_name = (String) topicObj.get("keyFileNamePath");
				sc.keyFilePath=key_file_name;
							  	
				JSONArray outbound_addrsObj = (JSONArray)topicObj.get("whitelist_node");
				Iterator<Object> iterator2 = outbound_addrsObj.iterator();
				int counter=0;			  
				while(iterator2.hasNext()) { 
					iterator2.next(); 
					counter++; 
				}
				String[] addrs = new String[counter];			  
				iterator2 = outbound_addrsObj.iterator();			  	
				counter=0;
				while(iterator2.hasNext()) {
					JSONObject addrObject = (JSONObject) iterator2.next();
					String addr = (String) addrObject.get("addr");
					addrs[counter]=addr;
				}
				sc.whiltelistAddrs = addrs;				
				hmAllTopicsConfig.put(pattern.toLowerCase(),sc);
			}			  	
		} catch(Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	/**
	* 
	* @param	topic	 Key
	* @reutrn KeyFilePath
	*/   					
	
	public String getKeyFilePathFromConfig(String topic) {
		String patternKey;
		String[] tokenArr = topic.split("/");
		patternKey = tokenArr[0]+"/"+tokenArr[1];
		
		ServiceConfig serviceConfig = (ServiceConfig) hmAllTopicsConfig.get(patternKey);		
		return serviceConfig.keyFilePath;			
	}
			
		
	/**	
	* DNOM
	* @param	fullServiceName	
	* @reutrn true - ,  false -  
	*/   
	public boolean makeInitialFiles(String fullServiceName) {
		String issuer,service,ledger;
		String[] tokenArr = fullServiceName.split("/");
		if(tokenArr.length!=3) {
			return false;
		}	
		issuer = tokenArr[0];
		service = tokenArr[1];
		ledger = tokenArr[2];
		try {
			File orderFile = new File(tossConfig.tossParameters.dataFolderPath+"/"+issuer+"/"+service+"_#00001.ois","rw");
			orderFile.createNewFile();
			
			File idxFile = new File(tossConfig.tossParameters.dataFolderPath+"/"+issuer+"/"+service+"/"+ledger+".oit");
			idxFile.createNewFile();
			RandomAccessFile ridxFile = new RandomAccessFile(tossConfig.tossParameters.dataFolderPath+"/"+issuer+"/"+service+"/"+ledger+".oit","rw");  
			
			File datFile = new File(tossConfig.tossParameters.dataFolderPath+"/"+issuer+"/"+service+"/"+ledger+"_#00001.tos");
			datFile.createNewFile();
			RandomAccessFile rdatFile = new RandomAccessFile(tossConfig.tossParameters.dataFolderPath+"/"+issuer+"/"+service+"/"+ledger+"_#00001.tos","rw");  
			
			// !! Don't close random access file !!!!			
			hmAllFilesRW.put(tossConfig.tossParameters.dataFolderPath+"/"+issuer+"/"+service+"/"+ledger+".oit",ridxFile);
			hmAllFilesRW.put(tossConfig.tossParameters.dataFolderPath+"/"+issuer+"/"+service+"/"+ledger+"_#00001.tos",rdatFile);			
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}		
		return true;
	}
		
	
	public int getMaxFileSizeMB() {
					
		return tossConfig.tossParameters.dataFileSizeMB;
	}
		
				
	/**
	* TOSServer  Main()
	* TOSSErver Service  Entry 

	*/   					
	public static void main(String[] args) {	  		
		String configFileName;	
		TOSServer   tosServer = new TOSServer();
	
		if(args.length==0) {
		    System.out.println("[USAGE] java org.rdlms.toss.server.TOSServer [CONFIGFILE_NAME]");
			return;
		}
  	
		configFileName = args[0];

		System.out.println("\n"); 
		System.out.println("*======================================================*");  
		System.out.println("|                                                      |"); 
		System.out.println("|                     TOSServer V 0.2                  |");  
		System.out.println("|                    ----------------                  |");
		System.out.println("|                                                      |"); 
		System.out.println("|            Copyright (c) 2020, TRUSTDB Inc.          |");	        	        
		System.out.println("*======================================================*");  
		System.out.println("\n\n");
	  	  	
		// [STEP-1]
		System.out.println("[Read Configurations] .... "+configFileName);
		tosServer.loadConfigFile(configFileName);
		System.out.println("");

		tosServer.log.info("[Initailze Local Directories and Files] .... ");
		// [STEP-2]
		tosServer.hmAllTopicsConfig.forEach((key, value) -> {
			MessageManager.makeFolders(tosServer.tossConfig.tossParameters.dataFolderPath, (String)key);
		});
		tosServer.log.info("");

				
		// [STEP-3]
		tosServer.log.info("[Intialize All Services and Last Orders] .... ");
		tosServer.hmAllTopicsConfig.forEach((key, value) -> {
			ServiceOrderInfo serviceOrderInfo;
			serviceOrderInfo = MessageManager.readLastOrderFromStorage(tosServer.log,tosServer.tossConfig.tossParameters.dataFolderPath,(String)key);
			tosServer.hmAllServiceOrderInfo.put(key,serviceOrderInfo);
			tosServer.log.info("Service "+key+" -> Last Order = "+serviceOrderInfo.order_in_service);
			serviceOrderInfo.hm_table_orders.forEach((key2, value2)-> {
				tosServer.log.info("\tTABLE "+key2+" -> Last Order = "+value2);
			});

		});
		tosServer.log.info("");

		// [STEP-4]
		tosServer.log.info("[Load TOSA key for Services] .... ");
		tosServer.hmAllTopicsConfig.forEach((key, value) -> {
			ServiceConfig serviceConfig;
			serviceConfig = (ServiceConfig) tosServer.hmAllTopicsConfig.get((String)key);			
			/* TODO 
				You have to use password file or KMS to keep password securely
			*/
			// When ECDSAWallet read with password, ECDSAWallet object keep private key with decrypted status.
			// So you need not to use password for signing on the ECDSAWallet object.
			String account;
			account = tosServer.walletManager.readWallet(serviceConfig.keyFilePath,commonPassword);  
			if(account!=null) {
				tosServer.log.info("Service "+key+" -> "+serviceConfig.keyFilePath);
			} else {
				tosServer.log.error("Service "+key+" -> "+serviceConfig.keyFilePath+"  .... failed to read");				
			}
		});
		tosServer.log.info("\n");

		tosServer.log.info("[TOSServer start] .... \n");		
		try {
			  
			ServerSocketChannel serverSocketChannelPublisher = ServerSocketChannel.open();		      
			serverSocketChannelPublisher.socket().bind(new InetSocketAddress(  tosServer.tossConfig.tossParameters.publish_service_port));
			serverSocketChannelPublisher.configureBlocking(false);

			ServerSocketChannel serverSocketChannelSubscriber = ServerSocketChannel.open();		      
			serverSocketChannelSubscriber.socket().bind(new InetSocketAddress(  tosServer.tossConfig.tossParameters.subscribe_service_port));
			serverSocketChannelSubscriber.configureBlocking(false);
			                
			while(true) {	          
				/* ServerSocket ο Ӹ Socket ȯϴµ */	          
				SocketChannel socketChannelPublisher = serverSocketChannelPublisher.accept();				
				if(socketChannelPublisher!=null) {              	
					/* Thread Socket ü ޽  ConcurrentHashMap ü ־  */
					tosServer.log.info("[Connected ] "+socketChannelPublisher.getRemoteAddress().toString());
					TOSThreadPublisher tossThreadPublisher = new TOSThreadPublisher(socketChannelPublisher,   tosServer);
					/* Thread   GOGO */
					tossThreadPublisher.start();
				}			    
				
				SocketChannel socketChannelSubscriber = serverSocketChannelSubscriber.accept();
				if(socketChannelSubscriber!=null) {              	
					/* Thread Socket ü ޽  ConcurrentHashMap ü ־  */
					tosServer.log.info("[Connected ] "+socketChannelSubscriber.getRemoteAddress().toString());
					TOSThreadSubscriber tossThreadSubscriber = new TOSThreadSubscriber(socketChannelPublisher,   tosServer);
					/* Thread   GOGO */
					tossThreadSubscriber.start();
				}				

				Thread.sleep(100);

			}
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
} 