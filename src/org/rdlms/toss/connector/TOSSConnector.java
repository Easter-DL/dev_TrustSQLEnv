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

package org.rdlms.toss.connector;

import org.apache.log4j.Logger;
import org.rdlms.messagestorage.model.TOSTransaction;
import org.rdlms.toss.server.TOSSInterface;
import org.rdlms.util.ArrayUtil;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;

 
public class TOSSConnector implements TOSSInterface{
	final static int RECEIVE_BUFFER_SIZE=4096;
	final static String PROTOCOL_VERSION="TOSSPV000001";
	final static byte[] MAGIC_CODE = { 1,2,3,4 };
			 	
	Socket SOCK = null;
	OutputStream OUTPUTS = null;
	InputStream  INPUTS = null;
	String HOSTADDRESS=null;
	int HOSTPORT=0;

	protected Logger log;  // TRACE<DEBUG<INFO<WARN<ERROR<FATAL

	public class ServiceList {
		public String[] serviceName;
	}

	public class ServiceInfo {
		public String serviceName;
		public short datFileSizeMB;
		public int   maxDownloadTransactions;
		public String[] tableName;
	}


	/**	
	*@param hostAddr 	TOSS IP
	*@param port			TOSS Port
	*/  		
	public TOSSConnector(String ttobAddr,  int ttobPort) {
		this.HOSTADDRESS = ttobAddr;
		this.HOSTPORT = ttobPort;
		//////System.out.println("TOSS Addr="+hostAddr+" Port="+port);
		this.log = Logger.getRootLogger();  	
	}
	
	
	/**
	 * TOSSInterface publicTransaction Implementation.
     * Publish user transaction to TOSS and get back ordering-stamped transaction, a receipt. 	
	 *@param	fullServiceName(=SERVICE ID)
     *@param    PAYLOAD (transaction) 	
	 *@return   errorCode
     *@return   tosTrnsaction (Stamped PAYLAOD with Order)
	 */
	public int publishTransaction(String fullServiceName, String payload, TOSTransaction tosTransaction) throws Exception {
		int errorCode=-1;	
		try {
			if(sendGetStamp(fullServiceName, payload)!=0) {
				//System.out.println("getTOS ");
				return 8890;
			}
			errorCode = recvStampedTransaction(tosTransaction);  	
		} catch (SocketException se) {
			throw se;			
		} catch (Exception e) {
			log.error(e.toString());
			return -1;
		}
		return errorCode;
	};
  
	/**	
	 * TossInterface publicTransaction Implementation.
     * Pull all transactions that correspond to the fullServiceName and order number is after the lastID from TOSS.
	 *@param	fullServiceName
     /@param    lastID
	 *@return   tosTransaction[]
	 */
	public TOSTransaction[] getTOSTransactions(String fullServiceName,long lastID) throws Exception {  	
		//System.out.println("getTOSTransactions called");
		TOSTransaction[] tosTransactions;
		try {
		
			if(sendGetTransactions(fullServiceName, lastID, 0)!=0) {
				//System.out.println("Write ");
				return null;
			}   			
			
			tosTransactions = recvTransactions(fullServiceName);  	  	
		} catch (SocketException se) {
			throw se;			
		} catch (Exception e) {
			log.error(e.toString());
			return null;
		}

		return tosTransactions;
	}

	/**
     * Pull serviceInfo seviced by TOSS. 	
	 *@param	 fullServiceName	
	 *@return ServiceInfo
	*/
	public ServiceInfo getServiceInfo(String serviceName) throws Exception {
		ServiceInfo serviceInfo;
		try {
		if(sendGetServiceInfo(serviceName)==false) {
			return null;
		}
		serviceInfo = recvServiceinfo();
		} catch (SocketException se) {
			throw se;			
		} catch (Exception e) {
			log.error(e.toString());
			return null;
		}
		return serviceInfo;
	}


	/**
     * Pull serviceStatus seviced by TOSS. 	
     * serviceName, ThreadID, Table Name
     * service Order
     * Table Order.
	 *@param	 fullServiceName	
	 *@return ServiceInfo
	*/
	//public ServiceStatus getServiceStatus(String serviceName);

    /**
     * add new service to TOSS	
     * add config for the new service to cofnfig file and start new thread for new service
	 *@param
	 *@return 
	*/
    public int addService(String serviceName, String strJson) {
		return 0;
	}

    /**
     * stop servce to TOSS	     * 
	 *@param
	 *@return 
	*/
    public int stopService(int threadID) {
		return 0;
	};

    /**
     * stop servce to TOSS	
	 *@param
	 *@return 
	*/
    public int startService(int threadID) {
		return 0;
	};

	/**	
	* sendGetStamp 
	* 
	* raw message format
	* --------------------------------------------
	* HEADER (36 ytes)
	* 		message_config	char[4]
	*		command			char[24]
	*		body_len		u4
	*		checksum		char[4]
	* BODY (body_len bytes)
	*		gt_name_len		U2
	*		gt_name			char[gt_name_oen]	global table name ex)www.trustedhotel.com/p2pcaahsystem/MYPOINT_TRANSACTIONS
	*		transaction_len	u2()
	*		transaction		char[transaction_len
	*
	* 
	*@param	 fullServiceName	is Global Table Name ex)www.trustedhtotel.com/p2pcashsystem/MYPOINT_TRANSACTIONS
	*@param	 transaction		is special SQL statement includes annotations like @order and @order's sign  	
	*@return error code  
	*/
	private int sendGetStamp(String fullServiceName, String transaction) throws Exception {  	
		log.debug("SEND [GetStamp]-------------------------------------");  	
		log.debug("@P1 fullServiceName ="+fullServiceName);  	
		log.trace("@P2 transaction ="+transaction);  	
		
		try {
			byte[] cmdHeader = new byte[36];
			byte[] baCmd = "GetStamp".getBytes();
			byte[] baPayloadLen = ArrayUtil.intToBA(2+fullServiceName.length()+2+transaction.getBytes().length);
			byte[] baCheckSum = { 0,0,0,0 };
			
			Arrays.fill(cmdHeader,(byte)0);	    	
			System.arraycopy(MAGIC_CODE,0,cmdHeader,0,4);	    		    		    	
			System.arraycopy(baCmd,0,cmdHeader,4,baCmd.length);
			System.arraycopy(baPayloadLen,0,cmdHeader,28,4);
			System.arraycopy(baCheckSum,0,cmdHeader,32,4);
			write(cmdHeader);	    	
						
			byte[] baServiceName = fullServiceName.getBytes();
			byte[] baTransaction = transaction.getBytes();
			byte[] baPayload = new byte[2+baServiceName.length+2+baTransaction.length];		
			System.arraycopy(ArrayUtil.shortToBA((short)baServiceName.length),0,baPayload,0,2);  	  	
			System.arraycopy(baServiceName,0,baPayload,2,baServiceName.length);
			System.arraycopy(ArrayUtil.shortToBA((short)baTransaction.length),0,baPayload,2+baServiceName.length,2);
			System.arraycopy(baTransaction,0,baPayload,2+baServiceName.length+2,baTransaction.length);		
			write(baPayload);	   
		} catch (SocketException se) {
			throw se;			
		} catch (Exception e) {
			log.error(e.toString());
			return -1;
		} 
		return 0; 		
	}
  
	/**	
	* receiveStampedTransaction 
	* 	It receives response to "GetStamp" command.
	*
	* raw message format
	* --------------------------------------------
	* HEADER (36 ytes)
	* 		message_config	char[4]
	*		command			char[24]
	*		body_len		u4
	*		checksum		char[4]
	* BODY (body_len bytes)
	*		gt_name_len		u2
	*		gt_name			global table name
	*		order_in_service u8	
	*		order_in_table	u8	it is the TRUSTED ORDER included SQLSTATEMENT
	*		stamped_transaction_len	u2
	*		stamped_transaction char[stamped_transaction_len]
	*		tosa_account_len u2
	*		tosa_account	char[tosa_account_len]
	*		tosa_sign_len	u2
	*		tosa_sign		char[tosa_sign]
	*@param	 
	*@return error code  
	*/	
	private int recvStampedTransaction(TOSTransaction tosTransaction) throws Exception {		
		//System.out.println("\nRECV [recvStampedTransaction]-------------------------------------");  	
		log.debug("RECV [StampedTransaction]-------------------------------------");  	
		int errorCode;		
		//System.out.println("[X1]");
		try {


			byte[] cmdHeader = read(36);
			int magic_code = ArrayUtil.BAToInt(cmdHeader,0);
			byte[] cmd = new byte[24];
			System.arraycopy(cmdHeader,4,cmd,0,24);
			String commandStr = new String(cmd);
			//System.out.println("command = "+commandStr);
			int payloadLen = ArrayUtil.BAToInt(cmdHeader,28);
			errorCode = ArrayUtil.BAToInt(cmdHeader,32);	  

			if(errorCode!=0) return errorCode;

			//System.out.println("5");
			// Receive Command Body
			int offset=0;
			byte[] cmdBody = read(payloadLen);
			//System.out.println("payloadlen="+payloadLen);

			// gt_name_len
			short gt_name_len = (short)ArrayUtil.BAToShort(cmdBody,0);
			offset+=2;
			// gt_name
			byte[] gt_name = new byte[gt_name_len];
			System.arraycopy(cmdBody,offset,gt_name,0,gt_name_len);
			tosTransaction.gt_name= new String(gt_name);
			offset+=gt_name_len;
			// order_in_service u8
			byte[] order_in_service = new byte[8];
			System.arraycopy(cmdBody,offset,order_in_service,0,8);
			tosTransaction.order_in_service = ArrayUtil.BAToLong(order_in_service);
			offset+=8;
			// order_in_table u8
			byte[] order_in_table = new byte[8];
			System.arraycopy(cmdBody,offset,order_in_table,0,8);
			tosTransaction.order_in_table = ArrayUtil.BAToLong(order_in_table);
			offset+=8;
			// stamped_transaction_len
			short stamped_transaction_len = (short) ArrayUtil.BAToShort(cmdBody,offset);
			offset+=2;
			// stamped_transaction
			byte[] stamped_transaction = new byte[stamped_transaction_len];
			System.arraycopy(cmdBody,offset,stamped_transaction,0,stamped_transaction_len);
			tosTransaction.stamped_transaction = new String(stamped_transaction);
			offset+=stamped_transaction_len;
			// tosa_account_len
			short tosa_account_len = (short) ArrayUtil.BAToShort(cmdBody,offset);
			offset+=2;
			// tosa_account
			byte[] tosa_account = new byte[tosa_account_len];
			System.arraycopy(cmdBody,offset,tosa_account,0,tosa_account_len);
			tosTransaction.tosa_account = new String(tosa_account);
			offset+=tosa_account_len;
			// tosa_sign
			short tosa_sign_len = (short) ArrayUtil.BAToShort(cmdBody,offset);
			offset+=2;
			// tosa_sign
			byte[] tosa_sign = new byte[tosa_sign_len];
			System.arraycopy(cmdBody,offset,tosa_sign,0,tosa_sign_len);
			tosTransaction.tosa_sign = new String(tosa_sign);
		} catch (SocketException se) {
			throw se;			
		} catch (Exception e) {
			log.error(e.toString());
			return -1;
		} 	  	
		//System.out.println("TOSTransaction");
		//System.out.println(tosTransaction.toString());

		//System.out.println("[X5]");
		return errorCode;
	}
  
	/**	
	*@param	 Key 
	*@param	 fromOrder TOSID
	*@param	 toOrder 
	*@return TosTransaction	TOSS
	*/  					  	  
	private int sendGetTransactions(String key, long fromOrder, long toOrder) throws Exception{
		
		//command MAGIC_CODE(4), COMMAND(24) PAYLOAD_LEN(4), CHECKSUM(4), PAYLOAD �Դϴ�.
		// MAGIC_CODE 
		//System.out.println("\nSEND [GetTransactions]-------------------------------------");  	
		//System.out.println("@P1 KEY ="+key);  	
		//System.out.println("@P2 fromOrder ="+fromOrder);  	
		//System.out.println("@P1 toOrder ="+toOrder);
		//log.trace("SEND [GetTransactions] - "+key+" from = "+fromOrder+" to = "+toOrder);
		try {
			int keyLength = key.getBytes().length;
			byte[] cmdHeader = new byte[36];
			byte[] baCmd = "GetTransactions".getBytes();
			byte[] baPayloadLen = ArrayUtil.intToBA(2+keyLength+8+8);  // Full Topic Name+Order(8) +Order(8)
			byte[] baCheckSum = { 0,0,0,0 };
			byte[] baPayload;
			
			Arrays.fill(cmdHeader,(byte)0);	    	
			System.arraycopy(MAGIC_CODE,0,cmdHeader,0,4);	    		    		    	
			System.arraycopy(baCmd,0,cmdHeader,4,baCmd.length);
			System.arraycopy(baPayloadLen,0,cmdHeader,28,4);
			System.arraycopy(baCheckSum,0,cmdHeader,32,4);
			write(cmdHeader);	
			
			byte[] body = new byte[2+keyLength+8+8];
			System.arraycopy(ArrayUtil.shortToBA((short) keyLength),0,body,0,2);	
			System.arraycopy(key.getBytes(),0,body,2,keyLength);	
			System.arraycopy(ArrayUtil.longToBA(fromOrder),0,body,2+keyLength,8);
			System.arraycopy(ArrayUtil.longToBA(toOrder),0,body,2+keyLength+8,8);
			write(body);
		} catch (SocketException se) {
			throw se;			
		} catch (Exception e) {
			log.error(e.toString());
			return -1;
		}
		return 0;	    	
	}	

	/**	
	*@param	 fullTopicStr
	*@return TosTransaction[]	TOSS
	*/  					  	  
	private TOSTransaction[] recvTransactions(String fullTopicStr) throws Exception {		
		//System.out.println("\nRECV [Transactions]-------------------------------------"); 
		TOSTransaction[] tosTransactions;
		
		try {
			byte[] cmdHeader = read(36);  	  	
			int magic_code = ArrayUtil.BAToInt(cmdHeader,0);
			byte[] cmd = new byte[24];
			System.arraycopy(cmdHeader,4,cmd,0,24);
			String commandStr = new String(cmd);
			int transactionNo = ArrayUtil.BAToInt(cmdHeader,28);
			int checksum = ArrayUtil.BAToInt(cmdHeader,32);   		 			
			if(transactionNo==0) {
				//System.out.println(" No new Transaction !");
				return null; // No data.
			}	
			//System.out.println("HEADER="+ArrayUtil.toHex(cmdHeader));
			
			log.debug("RECV [Transactions] - "+fullTopicStr+" n("+transactionNo+")");

			tosTransactions = new TOSTransaction[transactionNo];
			for(int i=0; i<transactionNo; i++) tosTransactions[i]= new TOSTransaction();

			//System.out.println("transactionNo="+transactionNo);		
			byte[] baShortLen;		
			byte[] baGtName;
			byte[] baStampedTransaction;
			byte[] baTosaAccount;
			byte[] baTosaSign;
			for(int i=0; i<transactionNo; i++) {
				baShortLen = read(2);			
				baGtName = read(ArrayUtil.BAToShort(baShortLen));
				//System.out.println("gt_name len="+ArrayUtil.BAToShort(baShortLen));
				tosTransactions[i].gt_name = new String(baGtName);
				//System.out.println("receivd TOSTransaction["+i+"].gt_name="+tosTransactions[i].gt_name);
				
				tosTransactions[i].order_in_service = ArrayUtil.BAToLong(read(8));
				//System.out.println("receivd TOSTransaction["+i+"].order_in_service="+tosTransactions[i].order_in_service);
				
				tosTransactions[i].order_in_table = ArrayUtil.BAToLong(read(8));
				//System.out.println("receivd TOSTransaction["+i+"].order_in_table="+tosTransactions[i].order_in_table);
				
				baShortLen = read(2);
				//System.out.println("2");
				//System.out.println("StampedTransaction.length="+ArrayUtil.BAToShort(baShortLen));
				baStampedTransaction = read(ArrayUtil.BAToShort(baShortLen));
				tosTransactions[i].stamped_transaction = new String(baStampedTransaction);
				
				baShortLen = read(2);
				//System.out.println("3");
				baTosaAccount = read(ArrayUtil.BAToShort(baShortLen));
				tosTransactions[i].tosa_account = new String(baTosaAccount);
				
				baShortLen = read(2);
				//System.out.println("4");
				baTosaSign = read(ArrayUtil.BAToShort(baShortLen));
				tosTransactions[i].tosa_sign = new String(baTosaSign);

				//System.out.println("receivd TOSTransaction["+i+"]");
				//System.out.println(tosTransactions[i].toString());
			}    	
		} catch (SocketException se) {
			throw se;			
		} catch (Exception e) {
			log.error(e.toString());
			return null;
		}
		//System.out.println("tosTransactions.length = "+tosTransactions.length);
		//System.out.println("[Y5]");
		return tosTransactions;
	}
	
	/**
	* TOSS GetServiceList TOSS
	*@param serviceName
	*/
	private boolean sendGetServiceInfo(String serviceName) throws Exception {
		
		//command MAGIC_CODE(4), COMMAND(24) PAYLOAD_LEN(4), CHECKSUM(4), PAYLOAD �Դϴ�.
		// MAGIC_CODE ( Magic )
		//System.out.println("\nSEND [GetServiceInfo]-------------------------------------");  	
		//System.out.println("serviceName = "+serviceName);
		log.debug("SEND [GetServiceInfo] - "+serviceName);
		try {  	
			byte[] cmdHeader = new byte[36];
			byte[] baCmd = "GetServiceInfo".getBytes();
			byte[] baPayloadLen = ArrayUtil.intToBA(2+serviceName.getBytes().length);
			byte[] baCheckSum = { 0,0,0,0 };
			
			Arrays.fill(cmdHeader,(byte)0);	    	
			System.arraycopy(MAGIC_CODE,0,cmdHeader,0,4);	    		    		    	
			System.arraycopy(baCmd,0,cmdHeader,4,baCmd.length);
			System.arraycopy(baPayloadLen,0,cmdHeader,28,4);
			System.arraycopy(baCheckSum,0,cmdHeader,32,4);
			write(cmdHeader);	    	
				
			byte[] baServiceName = serviceName.getBytes();
			byte[] baPayload = new byte[2+baServiceName.length];
			System.arraycopy(ArrayUtil.shortToBA((short)baServiceName.length),0,baPayload,0,2);  	  	
			System.arraycopy(baServiceName,0,baPayload,2,baServiceName.length);
			write(baPayload);	    	
		} catch (SocketException se) {
			throw se;			
		} catch (Exception e) {
			log.error(e.toString());
			return false;
		}	
		  	  	
		return true;
	}

	/**	
	*@return ServiceInfo Service
	*/
	private ServiceInfo recvServiceinfo() throws Exception {		
		//System.out.println("\nRECV [recvServiceInfo]---------------------------------START");  	
		log.debug("RECV [ServiceInfo]");
		ServiceInfo serviceInfo = new ServiceInfo();
		byte[] cmdHeader = read(36);
		
		
		//System.out.println(ArrayUtil.toHex(cmdHeader));
		try {
			int magic_code = ArrayUtil.BAToInt(cmdHeader,0);
			byte[] cmd = new byte[24];
			System.arraycopy(cmdHeader,4,cmd,0,24);
			String commandStr = new String(cmd);
			int payloadLen = ArrayUtil.BAToInt(cmdHeader,28);
			int checksum = ArrayUtil.BAToInt(cmdHeader,32);
			
			if(payloadLen==0) {
				return null;
			}

			// Receive Command Body
			byte[] cmdBody = read(payloadLen);
			short serviceNameLen = (short)ArrayUtil.BAToShort(cmdBody,0);
			byte[] baServiceName = new byte[serviceNameLen];
			System.arraycopy(cmdBody,2,baServiceName,0,serviceNameLen);
			serviceInfo.serviceName = new String(baServiceName);
			
			serviceInfo.datFileSizeMB = (short) ArrayUtil.BAToShort(cmdBody,0+2+serviceNameLen);
			serviceInfo.maxDownloadTransactions = ArrayUtil.BAToInt(cmdBody,0+2+serviceNameLen+2);
			short tableNo = (short) ArrayUtil.BAToShort(cmdBody,0+2+serviceNameLen+2+4);
			
			serviceInfo.tableName = new String[tableNo];
			int offset = 0+2+serviceNameLen+2+4+2;
			for(int i=0; i<tableNo; i++) {
				short tnLen = (short)ArrayUtil.BAToShort(cmdBody,offset);
				byte[] baTemp = new byte[tnLen];
				offset+=2;
				System.arraycopy(cmdBody,offset,baTemp,0,tnLen);
				serviceInfo.tableName[i] = new String(baTemp).trim();
				offset+=tnLen;    				
			}
		} catch (SocketException se) {
			throw se;			
		} catch (Exception e) {
			log.error(e.toString());
			return null;
		}
	    //System.out.println("\nRECV [recvServiceInfo]-----------------------------------FINISH");  		  	
		return serviceInfo;
	}

	/**	
	*@return 		true , fail.
	*/  					
	public boolean open() {
		boolean bReturn=true;
		try {
			SOCK = new Socket(HOSTADDRESS,HOSTPORT);
			OUTPUTS = SOCK.getOutputStream();
			INPUTS  = SOCK.getInputStream();
						
			OUTPUTS.write(PROTOCOL_VERSION.getBytes());
			OUTPUTS.flush();
		}catch (Exception e) {
			e.printStackTrace();
			bReturn=false;
			try {
				if(OUTPUTS != null) {
					OUTPUTS.close();
				}          
				if(SOCK != null) {
					SOCK.close();
				}
			}catch (Exception f) {
				f.printStackTrace();
			}
		}
		return bReturn;
	}
    
 	/**
	*@param	ba
	*@return 
	*/  					
	private boolean write(byte[] ba) throws Exception {    
		try {
			if(SOCK==null) return false;
			if(OUTPUTS==null) return false;
			
			OUTPUTS.write(ba);
			OUTPUTS.flush();
						        
		} catch (SocketException se) {
			SOCK.close();
			OUTPUTS.close();
			throw se;			
		} catch (Exception e) {
			log.error(e.toString());
			return false;
		}    
		return  true;
	}

	/**	
	*@param	rlen
	*@return	
	*/  					
	private byte[] read(int rlen) throws Exception {
		byte[] baReturn = new byte[rlen];
		int rbyte=0;
		try {			
			while(rbyte<rlen) {
				rbyte+= INPUTS.read(baReturn,rbyte,rlen-rbyte);    			
			}
			//System.out.println("rbyte="+rbyte+"  rlen="+rlen);
			
		} catch (SocketException se) {
			SOCK.close();
			INPUTS.close();
			throw se;			
		} catch (Exception e) {
			log.error(e.toString());
			return null;
		} 		
		//System.out.println("\t@ READ ================");
		//System.out.println("\t@ "+ArrayUtil.toHex(baReturn));
		return baReturn;
	}

	/**
	*@param	rlen
	*@return	
	*/  					  
	public void close() {
		try {
			if(OUTPUTS!=null) OUTPUTS.close();
			if(SOCK!=null) SOCK.close();
		} catch (Exception e) { e.printStackTrace();	}    
	}

}
