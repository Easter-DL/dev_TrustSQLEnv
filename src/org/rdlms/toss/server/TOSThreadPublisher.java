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


import org.rdlms.util.ArrayUtil;
import org.rdlms.util.Assert;
import org.rdlms.wallet.WalletManager;
import org.rdlms.crypto.ECDSA;
import org.rdlms.messagestorage.control.MessageManager;
import org.rdlms.messagestorage.model.ServiceOrder;
import org.rdlms.messagestorage.model.ServiceOrderInfo;
import org.rdlms.messagestorage.model.TOSTransaction;
import org.rdlms.messagestorage.model.TableOrder;

import java.io.RandomAccessFile;
import java.security.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.nio.channels.*;
import java.nio.*;
import java.util.concurrent.*;
import org.apache.log4j.Logger;

/**
* class TOSThreadPublisher 
*

*	1. 	Client
*	2. 	TOSS Protocol
* 3. 	
*/

class TOSThreadPublisher extends Thread{
	//   
	final static int RECEIVE_BUFFER_SIZE=1024*8;
	final static String PROTOCOL_VERSION="TOSSPV000001";

	private SocketChannel socketChannel;
	private TOSServer tosServer;
	private String remoteAddress;
		
	TOSServer.TOSSConfig tossConfig;
	// HMALLServiceOrderInfo has order value for all services and tables
	ConcurrentHashMap<String, ServiceOrderInfo> hmAllServiceOrderInfo = new ConcurrentHashMap<String, ServiceOrderInfo>();
	
	ConcurrentHashMap<String, Object> hmAllTopicsConfig;
	private ConcurrentHashMap<String,RandomAccessFile> hmAllFilesRW;// Opend for RW !!!! It can't be shared other Thread
	private ConcurrentHashMap<String,RandomAccessFile> hmAllFilesR;// Opend for R !!!!   It can't be shared other Thread
	private WalletManager walletManager; 
	
	protected Logger log;  // DEBUG<INFO<WARN<ERROR<FATAL
		
	public TOSThreadPublisher(SocketChannel sock, TOSServer server) {
		this.socketChannel = sock;
		this.tosServer = server;  
		this.tossConfig = server.tossConfig;
		this.hmAllServiceOrderInfo  = server.hmAllServiceOrderInfo;
		this.hmAllTopicsConfig  = server.hmAllTopicsConfig;
		this.log = server.log;

		this.hmAllFilesRW = new ConcurrentHashMap<String,RandomAccessFile> ();
		this.hmAllFilesR = new ConcurrentHashMap<String,RandomAccessFile> ();
		
		this.walletManager = server.walletManager;
		try {
			this.remoteAddress= socketChannel.getRemoteAddress().toString();		
		} catch (Exception e) {};
	}
	
	/**
	* Read from SocketChannel
	* @param	rBUffer
	* @param	minLength
	* @reutrn int
	*/   								
	public int read(ByteBuffer rBuffer, int minLength) {		
		int totalReadedLen=0;
		int readLen=0;
		
		try {		
			while(totalReadedLen < minLength) {
				readLen = this.socketChannel.read(rBuffer);
				if (readLen == -1) {	
					return -1;
				}			
				totalReadedLen+=readLen;	
			}		
		} catch(Exception e) { // disconnected	  	
			log.error(e.toString());
		}				
		return totalReadedLen;
	}

	/**
	* Write data to SocketChannel
	* @param	wBUffer	
	*/   								
	public void write(ByteBuffer wBuffer) {
		wBuffer.flip();
		try {
			while(wBuffer.hasRemaining()) {		
				this.socketChannel.write(wBuffer);
			}
		} catch(Exception e) { // disconnected
			log.error(e.toString());
		}
	}

	/**
 	* Write data to SocketChannel
	* @param	baWrite write  		
	*/   								
	public void write(byte[] baWrite) {		
		int totalSentLen=0;		
		
		try {
			
			ByteBuffer bBuffer = ByteBuffer.allocate(baWrite.length);
			bBuffer.put(baWrite);
			bBuffer.flip();
			//while(bBuffer.hasRemaining()) {
			while(totalSentLen<baWrite.length) {	
				totalSentLen+=this.socketChannel.write(bBuffer);
			}
			//System.out.println("\t@ WRITE ================"+totalSentLen);
			//System.out.println("\t@ "+ArrayUtil.toHex(baWrite));
		} catch(Exception e) { // disconnected
			e.printStackTrace();	
		}
	} 
	
	/**		
	*/   
	public boolean checkMsgsInService(String globalTableName) {
		String issuer,service;
		String[] tokenArr = globalTableName.split("/");
		if(tokenArr.length!=3) {			
			return false;
		}	
		issuer = tokenArr[0];
		service = tokenArr[1];
				
		TOSServer.ServiceConfig sc = (TOSServer.ServiceConfig) hmAllTopicsConfig.get(issuer+"/"+service);
		if(sc==null) {
			log.error("Not registed Service ! : "+globalTableName);
			return false;
		}
		return true;
	}
		
	/**
	 * getTTOBTime - KEK TIME ZONE
	 * 
	 * @param timeFormat
	 * @return current time in timeFormat
	 */
	public static String getTTOBTime(String timeFormat) {
		String currentTimeStr=null;
		LocalDateTime now = LocalDateTime.now();

		if(timeFormat.equals("DATE")) {
			////2021-11-01
			currentTimeStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		} else if(timeFormat.equals("DATETIME")) {
			//2021-11-01 00:00:00
			currentTimeStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		} 
		//currentTimeStr="2022-01-29 17:41:14";
		return currentTimeStr;
	}


	/**
	 * Make Ordering-Stamp and return TOSTransaction
	 * @param globalTableName
	 * @param order_no
	 * @param payload
	 * @return TOSTransaction
	*/
	public TOSTransaction addOredringStamp(String gt_name, long order_in_service, long order_in_table, String transaction, String keyFile ) {
		KeyPair ordererPair=null;
		PrivateKey priv=null;		
		Signature ecdsa=null;
		String signInput=null;
		String trustedTime=null;
		
		TOSTransaction tosTransaction = new TOSTransaction();
		tosTransaction.gt_name=gt_name;
		tosTransaction.order_in_service=order_in_service;
		tosTransaction.order_in_table=order_in_table;				
		
		//System.out.println("[addOrderingStmp]-------------------------------------");
		//System.out.println("[addOrderingStmp]-------------------------------------");
		//System.out.println("[addOrderingStmp]-------------------------------------");

		String stamped_transaction=null;
		// Check @SYNC_ID token		
		if((transaction.indexOf("@SYNC_ID")==-1) || (transaction.indexOf("@SYNC_SIGN")==-1))  {	
			stamped_transaction=transaction;
		} else {			
			// INSERT INTO A (FA, FB,FC, FD) VALUES (@SYNC_ID,'B','C',@SYNC_SIGN);
			String[] tokens = transaction.split("@SYNC_ID");
			// tokens[0] = INSERT INTO A (FA, FB,FC, FD) VALUES (
			// tokens[1] = ,'B','C',@DATETIME,@SYNC_SIGN);

			// TODO
			// !!!!!!!!!! there some values the next of @SYNC_SIGN
			// !!!!!!!!!! there some values the next of @SYNC_SIGN
			// !!!!!!!!!! there some values the next of @SYNC_SIGN
			// !!!!!!!!!! there some values the next of @SYNC_SIGN
			// !!!!!!!!!! there some values the next of @SYNC_SIGN
			
			
			String[] tokens2 = tokens[1].split("@SYNC_SIGN");			
			// tokens2[0] = ,'B','C',@DATETIME
			// tokens2[1] = )
			signInput = tokens2[0];
			// signInput = ,'B','C',@DATETIME			
			
			// TODO
			// !!!!!!!!!! You should mapping @SYMBOL !!
			// !!!!!!!!!! You should mapping @SYMBOL !!
			// !!!!!!!!!! You should mapping @SYMBOL !!
			// !!!!!!!!!! You should mapping @SYMBOL !!
			// !!!!!!!!!! You should mapping @SYMBOL !!
			
			
			if(transaction.contains("@DATETIME")) {
				trustedTime = getTTOBTime("DATETIME");
				signInput = signInput.replaceFirst("@DATETIME","'"+trustedTime+"'");
			}
			// signInput = ,'B','C','2021-11-01 00:00:00'	

			signInput = new Long(order_in_table).toString()+signInput; // add first, order_no
			// signInput = 124,'B','C','2021-11-01 00:00:00'
			signInput = signInput.replaceAll("'","");
			signInput = signInput.replaceAll(",","");
			signInput = signInput.trim();			
			// signInput = 1245BC2021-11-01 00:00:00
		
			/* TODO 아래의 것은 입력값을 조작한다. 추후 확인 필요 !!
			signInput = signInput.replaceAll(" ","");
			signInput = signInput.replaceAll("\t","");
			signInput = signInput.replaceAll("\n","");
			*/			
			//System.out.println("\t@@@@ SIGNINPUT = "+signInput);
			
			try {
				String syncSign = this.walletManager.sign(keyFile, signInput);
	
				stamped_transaction = transaction;
				stamped_transaction = stamped_transaction.replaceFirst("@SYNC_ID","'"+new Long(order_in_table).toString()+"'");
				stamped_transaction = stamped_transaction.replaceFirst("@SYNC_SIGN","'"+syncSign+"'");
				stamped_transaction = stamped_transaction.replaceFirst("@DATETIME","'"+trustedTime+"'");

			} catch(Exception e) {
				e.printStackTrace();
				return null;
			}			  					
		}
		
		tosTransaction.stamped_transaction = stamped_transaction;
		//System.out.println("\t@@@@ STAMPED_TRANSACTION="+stamped_transaction.indexOf(0,2));

		String pubStr;
		pubStr = this.walletManager.getAccount(keyFile);
		tosTransaction.tosa_account = pubStr;
		//System.out.println("TOSA ACCOUNT="+pubStr);

		signInput = tosTransaction.gt_name+new Long(tosTransaction.order_in_service).toString()+new Long(tosTransaction.order_in_table).toString()+
					tosTransaction.stamped_transaction+tosTransaction.tosa_account+tosTransaction;
		try {
			String syncSign = this.walletManager.sign(keyFile, signInput);
			tosTransaction.tosa_sign = syncSign;
		} catch(Exception e) {
			e.printStackTrace();
			return null;
		}				

		log.trace("[addOrderingStemp] "+tosTransaction.toString());
		return tosTransaction;
	}

		
	/**
	* addOrdering Stamp Linux Version
	* It uses libsecp256k1.
	* import org.bitcoin.*;
	*/
	/*
	public String addOredringStamp(long orderNo, String payload, String keyFile) {
		String stampedStr=null;
		String signInput;
		KeyPair ordererPair;
		PrivateKey priv;
		MessageDigest digest;
		
		stampedStr = payload;
		// Check @SYNC_ID token
		if(payload.indexOf("@SYNC_ID")==-1) {
		return stampedStr;
		}
		if(payload.indexOf("@SYNC_SIGN")==-1) {
		return stampedStr;
		}
		
		String[] tokens = payload.split("@SYNC_ID");
		String[] tokens2 = tokens[1].split("@SYNC_SIGN");
		signInput = tokens2[0];
		
		signInput = new Long(orderNo).toString()+signInput; // add first, order_no
		signInput = signInput.replaceAll("'","");
		signInput = signInput.replaceAll(",","");
		signInput = signInput.replaceAll(" ","");
		signInput = signInput.replaceAll("\t","");
		signInput = signInput.replaceAll("\n","");
		  
		ordererPair = Crypto.deSerializeKeyPair(keyFile);
		try {
			digest = MessageDigest.getInstance("SHA-256");
			priv = ordererPair.getPrivate();
			byte[] rawbytes = priv.getEncoded();
			byte[] prvbytes = new byte[rawbytes.length-32];
			System.arraycopy(rawbytes,32,prvbytes,0,rawbytes.length-32);
			byte[] hashedData = digest.digest(signInput.getBytes("UTF-8"));
			byte[] realSig = secp256k1.sign(hashedData,prvbytes);
			String syncSign = ArrayUtil.toHex(realSig);
			
			stampedStr = payload;
			stampedStr = stampedStr.replaceFirst("@SYNC_ID","'"+new Long(orderNo).toString()+"'");
			stampedStr = stampedStr.replaceFirst("@SYNC_SIGN","'"+syncSign+"'");
		
		} catch(Exception e) {
		e.printStackTrace();
		return null;
		}
		return stampedStr;
	}
	 */
		
	/**	
	* TTOB COMMAND : getStamp
	*
	* make TOS (trusted ordered stamped) message from user message and save TOS message and return it.
	*		
	* @param	baPayload global table name and user transaction
	* @reutrn	true success,   false fail - terminate this thread. 
	**/
	private boolean commandGetStamp(byte[] baPayload) {
		
		log.debug("COMMAND [GetStamp] "+remoteAddress);
		
		// Payload : ServiceName (U2+CHAR) + transaction(U2+CHAR[])
		short globalTableNameLen = (short)ArrayUtil.BAToShort(baPayload,0);	  	
		byte[] baGlobalTableName = new byte[globalTableNameLen];
		System.arraycopy(baPayload,2,baGlobalTableName,0,globalTableNameLen);
				
		String globalTableName = new String(baGlobalTableName);	  		
						
		short transactionLen = (short)ArrayUtil.BAToShort(baPayload,2+globalTableNameLen);
		byte[] baTransaction = new byte[transactionLen];
		System.arraycopy(baPayload,2+globalTableNameLen+2,baTransaction,0,transactionLen);
		//System.out.println("\t@P2 transaction="+globalTableName);
		String transactionStr = new String(baTransaction);
		
		String issuer,service, globalServiceName, table;
		String[] tokenArr = globalTableName.split("/");		
		if(tokenArr.length!=3) {
			// TODO RESPONSE to CLIENT !!
			log.error("Wrong globalTableName ! "+globalTableName);
			responseFailCode("StampedTransaction",20001);
			return false;
		}	
		issuer = tokenArr[0];
		service = tokenArr[1];
		table = tokenArr[2];
		// TODO parsing exactly !!!!
		if(!transactionStr.contains(table)) {
			log.error("Stament has not table name in GlobalTableName!");
			responseFailCode("StampedTransaction",20002);
			return false;
		}
		
		globalServiceName=issuer+"/"+service;														
		try {							
			long newOrderInTable=0;
			long newOrderInService=0;
			ServiceOrderInfo serviceOrderInfo=null;

			TOSServer.ServiceConfig serviceConfig = (TOSServer.ServiceConfig) hmAllTopicsConfig.get(globalServiceName.toLowerCase());
			if(serviceConfig==null) {
				log.error("Not registed Service to ServiceConfig! : "+globalServiceName);
				// TODO RESPONSE to CLIENT !!!
				return true;
			}
			
			TOSTransaction tosTransaction;	
			serviceOrderInfo = (ServiceOrderInfo) hmAllServiceOrderInfo.get(globalServiceName.toLowerCase());
			if(serviceOrderInfo==null) {
				log.error("Not registed Service to HashMap! : "+globalServiceName);
				// TODO RESPONSE to CLIENT !!! 
				return true;
			}	

			synchronized(serviceOrderInfo) {			
			//	log.trace("sync_in");
			//	System.out.println("sync_in");							
				// newOrserInService;	
				newOrderInService = serviceOrderInfo.order_in_service+1;
				// newOrderInTable
				Long LastOrderInTable = serviceOrderInfo.hm_table_orders.get(table);
				if(LastOrderInTable==null) {
					newOrderInTable=1;
				} else {
					newOrderInTable = LastOrderInTable.longValue()+1;					
				}

				// order_in_table increased in addOrderingStamp when the statment is includes @SYNC_ID
				tosTransaction = addOredringStamp(globalTableName, newOrderInService, newOrderInTable, transactionStr, serviceConfig.keyFilePath);
				if (!MessageManager.saveTranaction(log, tossConfig.tossParameters.dataFolderPath, tossConfig.tossParameters.dataFileSizeMB, hmAllFilesRW, tosTransaction)) {						
					Assert.assertTrue(true,"Transaction can't be saved !!");
				}
				log.trace("Stamped Transaction = "+tosTransaction.stamped_transaction);
			//	log.debug("aft_msgTODisk");
	
				// !!! After sign and save, increase last order in memory
				// increase order_in_service	
				serviceOrderInfo.order_in_service++;								
				// order_in_table
				if(newOrderInTable==1) {
					serviceOrderInfo.hm_table_orders.put(table, new Long(newOrderInTable));
				} else {
					serviceOrderInfo.hm_table_orders.replace(table, new Long(newOrderInTable));
				}
				
			//	log.trace("sync_out");
			//	System.out.println("sync_out");
			}		
											
			// Response FullServiceName, TOSID, StampedTransation
			//System.out.println("SEND [StampedTransaction]-------------------------------------");
			//System.out.println("MagicCode 4byte");
			write(TOSServer.MAGIC_CODE);
			byte[] baRespCommand = new byte[24];
			Arrays.fill(baRespCommand,(byte) 0);
			System.arraycopy("StampedTransaction".getBytes(),0,baRespCommand,0,"StampedTransaction".getBytes().length);
			//System.out.println("Command 24byte");
			write(baRespCommand);
			
			// payloadlen
			int respPayloadLen= 2+tosTransaction.gt_name.getBytes().length+8+8+2+tosTransaction.stamped_transaction.getBytes().length+2+tosTransaction.tosa_account.getBytes().length+2+tosTransaction.tosa_sign.getBytes().length;
			write(ArrayUtil.intToBA(respPayloadLen));
			//System.out.println("respPayloadLen="+respPayloadLen);
			
			// Checksum
			byte[] baCheckSum = new byte[4];
			Arrays.fill(baCheckSum,(byte) 0);
			write(baCheckSum);
			
			write(ArrayUtil.shortToBA((short)tosTransaction.gt_name.getBytes().length));
			write(tosTransaction.gt_name.getBytes());
			write(ArrayUtil.longToBA(tosTransaction.order_in_service));
			write(ArrayUtil.longToBA(tosTransaction.order_in_table));
			write(ArrayUtil.shortToBA((short)tosTransaction.stamped_transaction.getBytes().length));
			write(tosTransaction.stamped_transaction.getBytes());
			write(ArrayUtil.shortToBA((short)tosTransaction.tosa_account.getBytes().length));
			write(tosTransaction.tosa_account.getBytes());
			write(ArrayUtil.shortToBA((short)tosTransaction.tosa_sign.getBytes().length));
			write(tosTransaction.tosa_sign.getBytes());
			
			//log.debug("GetStamp out globalTableName="+globalTableName);
			//System.out.println("GetStamp out globalTableName="+globalTableName);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	/**	
	* TTOB COMMAND : getServiceInfo
	*
	* return service Config and table_names whithin a service.
	*		
	* @param	baPayload global service name (or global table name)
	* @reutrn	true success,   false fail - terminate this thread. 
	**/
	public boolean commandGetServiceInfo(byte[] baPayload) {
		//System.out.println("RECV [GetServiceInfo]-------------------------------------");
		// Payload : ServiceName (U2+CHAR) 
		log.debug("COMMAND [GetServiceInfo] "+remoteAddress);

		short serviceNameLen = (short)ArrayUtil.BAToShort(baPayload,0);
		byte[] baServiceName = new byte[serviceNameLen];
		System.arraycopy(baPayload,2,baServiceName,0,serviceNameLen);
				
		String serviceName = new String(baServiceName);
		//log.debug("GetServiceInfo ServiceName="+serviceName);	  		
		//System.out.println("GetServiceInfo ServiceName="+serviceName);
					
		TOSServer.ServiceConfig serviceConfig = (TOSServer.ServiceConfig) tosServer.hmAllTopicsConfig.get(serviceName.toLowerCase());
		
		if(serviceConfig==null) {
			// TODO check  Whether ASSERT or Not..
			log.error("No Service Info!");
			write(TOSServer.MAGIC_CODE);
			byte[] baRespCommand = new byte[24];
			Arrays.fill(baRespCommand,(byte) 0);
			System.arraycopy("ServiceInfo".getBytes(),0,baRespCommand,0,"ServiceInfo".length());			
			write(baRespCommand);
			write(ArrayUtil.intToBA(0));
			write(ArrayUtil.intToBA(0));
			return true;
		}
		
		// send ServiceInfo Command to respond GetServiceInfo	
		write(TOSServer.MAGIC_CODE);
		byte[] baRespCommand = new byte[24];
		Arrays.fill(baRespCommand,(byte) 0);
		System.arraycopy("ServiceInfo".getBytes(),0,baRespCommand,0,"ServiceInfo".length());
		//System.out.println("Command  24byte");
		write(baRespCommand);
		
		// payloadlen  Ѵ. ߿ . 							
		int respPayloadLen=0;
		
		// Checksum  Ѵ. ߿ 
		byte[] baCheckSum = new byte[4];
		Arrays.fill(baCheckSum,(byte) 0);
																
		int offset=0;							
		ByteBuffer byteBufferPayload = ByteBuffer.allocate(1024*8);
		// Service Name
		byteBufferPayload.put(ArrayUtil.shortToBA(serviceNameLen));
		offset+=2;
		byteBufferPayload.put(baServiceName);
		offset+=baServiceName.length;
		// datFileSizeMB
		byteBufferPayload.put(ArrayUtil.shortToBA((short)tossConfig.tossParameters.dataFileSizeMB));		
		offset+=2;							
		// maxDownloadTransactions
		byteBufferPayload.put(ArrayUtil.intToBA(tossConfig.tossParameters.transactionDownloadLimit));
		offset+=4;
		// Table No.
		//System.out.println("offset (serviceName+dataFIleSze+maxDown)="+offset);
		int counter=0;	
		// HMALLServiceOrderInfo has order value for all services and tables
		ConcurrentHashMap<String, ServiceOrderInfo> hmAllServiceOrderInfo = tosServer.hmAllServiceOrderInfo;
		ServiceOrderInfo serviceOrderInfo = hmAllServiceOrderInfo.get(serviceName);
		
		if(serviceOrderInfo==null) {
			counter=0;			
		} else {
			counter = serviceOrderInfo.hm_table_orders.size();
		}		
		//System.out.println("hm_table_orders.size="+counter);
		byteBufferPayload.put(ArrayUtil.shortToBA((short)counter));
		offset+=2;


		Set<String> set = serviceOrderInfo.hm_table_orders.keySet();
		for(String key : set) {
			String tableName = (String)key;
		//	System.out.println("key="+tableName);
			byteBufferPayload.put(ArrayUtil.shortToBA((short)tableName.getBytes().length));
			offset+=2;			
			byteBufferPayload.put(tableName.getBytes());			
			offset+=(short)tableName.getBytes().length;
		//	System.out.println("offset (serviceName+dataFIleSze+maxDown)+tableName="+offset);
		}
		
		respPayloadLen = offset;		
		byte[] baPayloadLen = ArrayUtil.intToBA(respPayloadLen);		
		
		write(baPayloadLen);									
		write(baCheckSum);
		
		baPayload = new byte[offset];		
		System.arraycopy(byteBufferPayload.array(),0,baPayload,0,offset); 					
		write(baPayload);	
		return true;
	}

	private void responseTransactionHeader(int recordNo) {
		write(TOSServer.MAGIC_CODE);
		byte[] baRespCommand = new byte[24];
		Arrays.fill(baRespCommand,(byte) 0);
		System.arraycopy("Transactions".getBytes(),0,baRespCommand,0,"Transactions".length());								
		write(baRespCommand);									
		// TRANSACTION_NO
		write(ArrayUtil.intToBA(recordNo));									
		// Checksum 
		byte[] baCheckSum = new byte[4];
		Arrays.fill(baCheckSum,(byte) 0);
		write(baCheckSum);				
		return;
	}

	private void responseFailCode(String command, int errorCode) {
		write(TOSServer.MAGIC_CODE);
		byte[] baRespCommand = new byte[24];
		Arrays.fill(baRespCommand,(byte) 0);
		System.arraycopy(command.getBytes(),0,baRespCommand,0,command.getBytes().length);								
		write(baRespCommand);									
		// BODY_LEN
		write(ArrayUtil.intToBA(0));									
		// errorCode		
		write(ArrayUtil.intToBA(errorCode));				
		return;
	}

	/**	
	* TTOB COMMAND : getTransactions
	*
	* return transactions requested 
	*		
	* Todo
	* 1. first, supports order_in_service search
	* 2. Last,	supports order_in_table search.
	*
	* @param	baPayload global service name (or global table name) and from & to
	*			to=0 means last.
	* @reutrn	true success,   false fail - terminate this thread. 
	**/

	public boolean commandGetTransactions(byte[] baPayload) {	
		
		//log.debug("RECV [GetTransactions] "+remoteAddress);

		// Payload : ServiceName (U2+CHAR) 
		short fullTopicLen = (short)ArrayUtil.BAToShort(baPayload,0);		
		byte[] baFullTopic = new byte[fullTopicLen];
		System.arraycopy(baPayload,2,baFullTopic,0,fullTopicLen);				

		String fullTopicStr = new String(baFullTopic);
		String tokenArr[] = fullTopicStr.split("/");
		int searchMode; 

		if(tokenArr.length==1) {
			searchMode = 1; // order_in_service search
		} else if(tokenArr.length==2) {
			searchMode = 2; // order_in_table Search
		} else {
			// Todo Fail Message
		}

		String globalServiceName = tokenArr[0]+"/"+tokenArr[1];
		String serviceName = tokenArr[1];		
		//System.out.println("\t@P1 FullTopicStr="+fullTopicStr);			
		long fromOrder = ArrayUtil.BAToLong(baPayload,2+fullTopicLen);
		//System.out.println("\t@P2 From Order="+fromOrder);
		long toOrder = ArrayUtil.BAToLong(baPayload,2+fullTopicLen+8);
		//System.out.println("\t@P3 To Order="+toOrder);
		//System.out.println("-----------------------------------------------------------");

		// 1. Check the Topic has transactions
		ServiceOrderInfo serviceOrderInfo = tosServer.hmAllServiceOrderInfo.get(globalServiceName);		
		if(serviceOrderInfo==null) {
			//System.out.println(" ConcurrentHashMap .. - "+fullTopicStr);							
			//System.out.println("SEND [Transactions]----------------------------------------");
			//System.out.println("\t No Data!");
			responseTransactionHeader(0);			
			return true;						
		} 

		// 2. Check the topic has transactios 2
		ConcurrentHashMap<String,Long> hmTableOrders = serviceOrderInfo.hm_table_orders;
		if(hmTableOrders.size()==0) {
			//System.out.println(" ConcurrentHashMap .. - "+fullTopicStr);							
			//System.out.println("SEND [Transactions]----------------------------------------");
			//System.out.println("\t No Data!");
			responseTransactionHeader(0);						
			return true;						
		}

		// 3. return transctions from requested order.		
		// Header
		/*
		write(TTOBServer.MAGIC_CODE);
		byte[] baRespCommand = new byte[24];
		Arrays.fill(baRespCommand,(byte) 0);
		System.arraycopy("Transactions".getBytes(),0,baRespCommand,0,"Transactions".length());								
		write(baRespCommand);									
		// Record No
		write(ArrayUtil.intToBA((int)(serviceOrderInfo.order_in_service-fromOrder)));									
		// Checksum 
		byte[] baCheckSum = new byte[4];
		Arrays.fill(baCheckSum,(byte) 0);
		write(baCheckSum);		
		*/
		// BODY
		RandomAccessFile oisFile=null;
		RandomAccessFile oitFile=null;
		RandomAccessFile datFile=null;		
		long lastOrderInService=0;
		String tableName=null;

		try {			
			lastOrderInService=serviceOrderInfo.order_in_service;						
			if(lastOrderInService<=fromOrder) {
				responseTransactionHeader(0);						
				return true;						
			} else {																
				//log.debug("TO sent Message Numbers = "+(lastOrderInService-fromOrder));
				log.debug("RESP [GetTransactions] n("+(lastOrderInService-fromOrder)+") f("+fromOrder+") t("+lastOrderInService+")");
				responseTransactionHeader((int)(lastOrderInService-fromOrder));
				short fileNo;
				String fileName=null;
				long  recordOffset;
				// 3.1 read tanactions by ois and return it.
				oisFile=null;
				oitFile=null;
				datFile=null;
				for(long i =(int)fromOrder+1; i <=lastOrderInService; i++) {
					// 3.1.1 find ois record to get oit record						
					fileNo = (short)((i / ServiceOrder.MAX_RECORDS_IN_ORDER_SERVICE)+1);
					// ./data/www.trustedhotel.com/p2pcashsystem/p2pcashsystem_#0001.ois
					fileName = tossConfig.tossParameters.dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",fileNo)+".ois";					
					//System.out.println("\t read order_in_service from file="+fileName);				
					oisFile = (RandomAccessFile) hmAllFilesR.get(fileName);
					if(oisFile==null) {		
						try {
							oisFile = new RandomAccessFile(fileName,"r");
						} catch(Exception re) { 
							System.out.println("!!! No OIS file : "+fileName);
							responseTransactionHeader(0);						
							return false;
						}
						hmAllFilesR.put(fileName,oisFile);
					}	
					
					recordOffset = (long)((i-1) % ServiceOrder.MAX_RECORDS_IN_ORDER_SERVICE)*ServiceOrder.RECORD_SIZE;
					//log.trace("ois index="+i+" record offset = "+recordOffset);					
					byte[] baOrderRecord = new byte[80];	// primitive arrays default 0	
					oisFile.seek(recordOffset);
					oisFile.read(baOrderRecord);					
					long   orderInTable;
					byte[] baOrderInService= new byte[8];
					long	orderInService;
					System.arraycopy(baOrderRecord,0,baOrderInService,0,8);
					orderInService = ArrayUtil.BAToLong(baOrderInService);
					//log.trace("OIS file order="+i+"  recordOffset="+recordOffset+" orderInService="+orderInService);

					while(recordOffset!=0 & orderInService==0) {
						log.trace("ois is 0 wait write finished!");
						Thread.sleep(100);
						oisFile.seek(recordOffset);
						oisFile.read(baOrderRecord);										
						System.arraycopy(baOrderRecord,0,baOrderInService,0,8);
						orderInService = ArrayUtil.BAToLong(baOrderInService);
					}
					
					while(i!=orderInService) {
						log.trace(" XXX ->ORDER_IN_SERVICE MISMATCH!!  "+i+"!="+orderInService);						
						Thread.sleep(100);
						oisFile.seek(recordOffset);
						oisFile.read(baOrderRecord);										
						System.arraycopy(baOrderRecord,0,baOrderInService,0,8);
						orderInService = ArrayUtil.BAToLong(baOrderInService);
						log.trace("Read Agian -> OIS file order="+i+"  recordOffset="+recordOffset+" orderInService="+orderInService);
					}
				
					
					Assert.assertTrue(i==orderInService," ORDER_IN_SERVICE MISMATCH!!  "+i+"!="+orderInService);
					// order_in_service -> don't read
					// table_name read
					byte[] baTableNameLen= new byte[2];
					System.arraycopy(baOrderRecord,8,baTableNameLen,0,2);
					byte[] baTableName = new byte[ArrayUtil.BAToShort(baTableNameLen)];
					System.arraycopy(baOrderRecord,10,baTableName,0,ArrayUtil.BAToShort(baTableNameLen));
					tableName = new String(baTableName);					
					// order_in_table read
					orderInTable = ArrayUtil.BAToLong(baOrderRecord,64+8);
					
					tableName = new String(baTableName).trim();
					//log.trace("get Transaction OIS="+i+" OIT="+orderInTable+" tableName="+tableName);
					//System.out.println("get Transaction OIS="+i+" OIT="+orderInTable+" tableName="+tableName);
					
					// 3.1.2 find oit recrod to get transactio
					fileNo = (short)((orderInTable / TableOrder.MAX_RECORDS_IN_ORDER_TABLE)+1);
					fileName = tossConfig.tossParameters.dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",fileNo)+".oit";
					oitFile = (RandomAccessFile) hmAllFilesR.get(fileName);
					if(oitFile==null) {						
						try {
							oitFile = new RandomAccessFile(fileName,"r");
						} catch(Exception re) { 
							System.out.println("!!! No OIT file : "+fileName);
							responseTransactionHeader(0);						
							return false;
						}
						hmAllFilesR.put(fileName,oitFile);
					}
										
					
					recordOffset = (long)((orderInTable-1) % TableOrder.MAX_RECORDS_IN_ORDER_TABLE)*TableOrder.RECORD_SIZE;
					oitFile.seek(recordOffset);		
					byte[] baOitRecord = new byte[14];
					oitFile.read(baOitRecord);					
					
					long orderInTable2;
					orderInTable2 = ArrayUtil.BAToLong(baOitRecord,0);

					while(recordOffset!=0 & orderInTable2==0) {
						log.trace("oit is 0 wait write finished!");
						Thread.sleep(100);
						oitFile.seek(recordOffset);
						oitFile.read(baOitRecord);										
						orderInTable2 = ArrayUtil.BAToLong(baOitRecord,0);
					}

					while(orderInTable!=orderInTable2) {
						log.trace(" XXX ->ORDER_IN_TABLE MISMATCH!!  "+orderInTable+"!="+orderInTable2);						
						Thread.sleep(100);
						oitFile.seek(recordOffset);
						oitFile.read(baOitRecord);										
						orderInTable2 = ArrayUtil.BAToLong(baOitRecord,0);
						log.trace("Read Agian -> OIT file order="+i+"  recordOffset="+recordOffset+" orderInService="+orderInTable2);
					}

					if(orderInTable!=orderInTable2) {
						// TODO Maybe fail.. because it sholoud be written..
						log.error("!!! OIT Mismatch "+tableName+" OIT in Index="+orderInTable+" OIT in Data="+orderInTable2);
						responseTransactionHeader(0);						
						return false;
					}					
					fileNo = (short)ArrayUtil.BAToShort(baOitRecord,8);
					recordOffset = ArrayUtil.BAToInt(baOitRecord,10);
				
					// 3.1.3 read tanactions by ois	// 
					fileName = tossConfig.tossParameters.dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",fileNo)+".tos";
					datFile = (RandomAccessFile) hmAllFilesR.get(fileName);
					if(datFile==null) {		
						try {
							datFile = new RandomAccessFile(fileName,"r");
						} catch(Exception re) { 
							System.out.println("!!! No DAT file : "+fileName);
							responseTransactionHeader(0);						
							return false;
						}
						hmAllFilesR.put(fileName,datFile);						
					}

					datFile.seek(recordOffset);

					// 3.1.4 response 
					// 1. gt_name
					String gt_name=globalServiceName+"/"+tableName;
					//System.out.println("gt_name.length="+(short)gt_name.getBytes().length);
					write(ArrayUtil.shortToBA((short)gt_name.getBytes().length));
					write(gt_name.getBytes());
					//System.out.println("gt_name="+gt_name);
					// 2. order_in_service
					baOrderInService = new byte[8];
					datFile.read(baOrderInService);
					long orderInService2;
					orderInService2 = ArrayUtil.BAToLong(baOrderInService);
					while(orderInService!=orderInService2) {
						log.trace("XXX DATA Mismatch OIS IN OIS="+orderInService+" OIS IN DAT="+orderInService2);
						Thread.sleep(100);
						datFile.seek(recordOffset);
						datFile.read(baOrderInService);
						orderInService2 = ArrayUtil.BAToLong(baOrderInService);
						log.trace("XXX read again OIS IN DAT="+orderInService2);
					}

					write(baOrderInService);
					// 3. order_in_table
					byte[] baOrderInTable = new byte[8];
					recordOffset= datFile.getFilePointer();
					datFile.read(baOrderInTable);
					long orderInTable3;
					orderInTable3 = ArrayUtil.BAToLong(baOrderInTable);
					while(orderInTable2!=orderInTable3) {
						log.trace("XXX DATA Mismatch OIT IN OIT="+orderInTable2+" OIT IN DAT="+orderInTable3);
						Thread.sleep(100);
						datFile.seek(recordOffset);
						datFile.read(baOrderInTable);
						orderInTable3 = ArrayUtil.BAToLong(baOrderInTable);
						log.trace("XXX read again OIT IN DAT="+orderInTable3);
					}

					write(baOrderInTable);
					// 4. stamepd_transaction
					byte[] baStampedTransactionLen = new byte[2];
					datFile.read(baStampedTransactionLen);
					write(baStampedTransactionLen);
					byte[] baStampedTransaction = new byte[ArrayUtil.BAToShort(baStampedTransactionLen)];
					datFile.read(baStampedTransaction);
					//System.out.println("StampedTransaction.length="+baStampedTransaction.length);					
					write(baStampedTransaction);
					//log.trace("StampedTrasaction="+new String(baStampedTransaction));

					// 5. tosa_account
					byte[] baTosaAccountLen = new byte[2];
					datFile.read(baTosaAccountLen);
					write(baTosaAccountLen);
					byte[] baTosaAccount = new byte[ArrayUtil.BAToShort(baTosaAccountLen)];
					datFile.read(baTosaAccount);
					write(baTosaAccount);
					//log.trace("tosa_account="+new String(baTosaAccount));
					// 6. tosa_sign
					byte[] baTosaSignLen = new byte[2];
					datFile.read(baTosaSignLen);
					write(baTosaSignLen);
					byte[] baTosaSign = new byte[ArrayUtil.BAToShort(baTosaSignLen)];
					datFile.read(baTosaSign);
					write(baTosaSign);
					//log.trace("tosa_sign="+new String(baTosaSign));
				}
				//log.debug("TO sent finished!  = "+fullTopicStr);
				//System.out.println("TO sent finished!  = "+fullTopicStr);
			}
			RandomAccessFile closeFile=null;
			Iterator<String> keys = hmAllFilesR.keySet().iterator();
			while(keys.hasNext() ){
				String key = keys.next();
				closeFile = hmAllFilesR.get(key);
				if(closeFile!=null) closeFile.close();
			}
			hmAllFilesR.clear();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	* TTOBThread Main Process
	*/  
	public void run() {
		  
		int topicLen=0;	
		String payloadStr;
		int readLen=0;
		ByteBuffer byteBuffer;
		
		try {
			
			byteBuffer = ByteBuffer.allocate(PROTOCOL_VERSION.getBytes().length);  
			readLen = read(byteBuffer, PROTOCOL_VERSION.getBytes().length);  	  
			if(readLen==-1) {
				return;
			}
			
			byte[] baTemp = byteBuffer.array();
			String protocolVer = new String(baTemp);
			
			//System.out.println("protocolVer="+protocolVer);
	
			if(!protocolVer.equals(PROTOCOL_VERSION)) {
				return;
			}
		
			long start=0,end=0;
			while(true) {
				// Command Header 36Bytes
				//System.out.println("\nWaiting .............................................");		  	
				//System.out.println("Elapsed Time nanoseconds = ["+(end-start)+"] miliseconds = ["+((end-start)/1000000)+"]");
				start = System.nanoTime();
				
				byteBuffer = ByteBuffer.allocate(36);
				readLen =  read(byteBuffer, 36);
				if(readLen<1) break;  
					
				//System.out.println("\nSUBSCRIBER MESSAGE =================================["+socketChannel.socket().getRemoteSocketAddress().toString()+"]");		
				byte[] baBuffer = byteBuffer.array();
				
				int magic_code = ArrayUtil.BAToInt(baBuffer,0);
				byte[] cmd = new byte[24];
				System.arraycopy(baBuffer,4,cmd,0,24);
				String commandStr = new String(cmd);
				int zeroFirstOffset = commandStr.indexOf(0);
				commandStr = commandStr.substring(0,zeroFirstOffset);
				//System.out.println("command = "+commandStr+"  length = "+commandStr.length());
				int payloadLen = ArrayUtil.BAToInt(baBuffer,28);
				int checksum = ArrayUtil.BAToInt(baBuffer,32);
					
				//System.out.println("PayloadLen="+payloadLen);
				
				byteBuffer = ByteBuffer.allocate(payloadLen);
				read(byteBuffer,payloadLen);
							
		
				byte[] baPayload = byteBuffer.array();
				
				if(commandStr.equals("GetStamp")) {	  		
					commandGetStamp(baPayload);				
				} else if(commandStr.equals("GetServiceInfo")) {
					commandGetServiceInfo(baPayload);
				} else if(commandStr.equals("GetTransactions")) {
					commandGetTransactions(baPayload);								 
				} else {	
		
				}	
				end = System.nanoTime();
			}
		} catch (Exception e) {		
			log.error(e.toString());
		// return;
		}finally {   		
			try {
				if(socketChannel != null) {
					log.info("[Connection Closed] "+this.socketChannel.getRemoteAddress().toString());
					socketChannel.close();
					return;					
				}		
			} catch (Exception e2) {
				log.error(e2.toString());
			}
		}	
	}

	public static void main(String args[]) {
		System.out.println("For test");

	}
}
