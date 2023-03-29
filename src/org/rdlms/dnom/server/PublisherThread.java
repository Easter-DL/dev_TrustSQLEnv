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

import org.rdlms.messagestorage.model.TOSTransaction;
import org.rdlms.toss.connector.TOSSConnector;
import org.rdlms.util.ArrayUtil;
 
import java.io.RandomAccessFile;
import java.nio.channels.*;
import java.nio.*;
import java.util.concurrent.*;
import org.apache.log4j.Logger;

 
/**
* class PublisherThread 
*
*/

class PublisherThread extends Thread{
	// Client
	final static int RECEIVE_BUFFER_SIZE=1024*8;			
	private ConcurrentHashMap<String,RandomAccessFile> hmAllFiles;// Opend for Only READ !!!
	private ConcurrentHashMap<String, Integer> hmAllErrorCodes;	
	private SocketChannel socketChannel;
	private PublicDNOM publicDnom;	
	protected Logger log;// DEBUG<INFO<WARN<ERROR<FATAL
	
	/**
	*/
	public PublisherThread(SocketChannel sock, PublicDNOM publicDnom) {
		this.log=publicDnom.log;
		this.socketChannel = sock;
		this.publicDnom = publicDnom; 		
		this.hmAllErrorCodes = publicDnom.hmAllErrorCodes;
		return;	
	}
			
	/**
	* @param	errorcode		
	*/		
	public void clientResponse(int errorcode) {
	//	//System.out.println("ERROR1 = "+ errorcode);
		writeToChannel(ArrayUtil.intToBA(errorcode));
		writeToChannel(ArrayUtil.longToBA(0));
		writeToChannel(ArrayUtil.shortToBA((short)0));		
	}
	
	/**
	*/		
	public void clientStatusResponse(int satusCode) {
	//	//System.out.println("STATUS RETURN = "+ satusCode);
		writeToChannel(ArrayUtil.intToBA(satusCode));		
	}
	
	/**
	*/		
	public void clientResponse(TOSTransaction tosTransaction) {
		writeToChannel(ArrayUtil.intToBA(0));	// Success
		writeToChannel(ArrayUtil.longToBA(tosTransaction.order_in_service));
		writeToChannel(ArrayUtil.shortToBA((short)tosTransaction.stamped_transaction.getBytes().length));
		writeToChannel(tosTransaction.stamped_transaction.getBytes());		
	}
	
	public void clientResponse(int errorCode, TOSTransaction tosTransaction) {
	//	//System.out.println("ERROR3");
		writeToChannel(ArrayUtil.intToBA(errorCode));	// Success
		writeToChannel(ArrayUtil.longToBA(tosTransaction.order_in_service));
		writeToChannel(ArrayUtil.shortToBA((short)tosTransaction.stamped_transaction.getBytes().length));
		writeToChannel(tosTransaction.stamped_transaction.getBytes());		
	}
	
	
	/**
	*/		
	public int readFromChannel(ByteBuffer rBuffer, int minLength) {		
		int totalReadedLen=0;
		int readLen=0;
		
		try {		
			while(totalReadedLen < minLength) {
			
				readLen = this.socketChannel.read(rBuffer);
				
				if (readLen == -1) {	
					return -1;
				} else if (readLen==0) {
						
				}			
				totalReadedLen+=readLen;	
			}		
		} catch(Exception e) { // disconnected
			e.printStackTrace();
			totalReadedLen=-1;
		}		
		return totalReadedLen;
	}


	/**
	*/
	public void writeToChannel(byte[] baWrite) {
	
	try {
		ByteBuffer bBuffer = ByteBuffer.allocate(baWrite.length);
		bBuffer.put(baWrite);
		bBuffer.flip();
		while(bBuffer.hasRemaining()) {
			this.socketChannel.write(bBuffer);
		}
	} catch(Exception e) { // disconnected
		e.printStackTrace();
	}
	}

	/**
	* PublisherThread Main Process
	*/
	public void run() {
		int fullServiceNameLen=0;
		String fullServiceName;
		int payloadLen=0;
		String payloadStr;
		int readLen=0;
		ByteBuffer byteBuffer;		
		ConcurrentHashMap<String, TOSSConnector> hmTossConnectors = new ConcurrentHashMap<String, TOSSConnector>();
		TOSSConnector ttobConnector=null;
		byte[] baMagicCode;
		try {
			byteBuffer = ByteBuffer.allocate(PublicDNOM.DNOM_PROTOCOL_VERSION.length());
			readLen = readFromChannel(byteBuffer, PublicDNOM.DNOM_PROTOCOL_VERSION.length());	
			if(readLen==-1) {
				return;
			}
			
			byte[] baTemp = byteBuffer.array();
			String protocolVer = new String(baTemp);	
			
			/* Client protocolver*/
			if(!protocolVer.equals(PublicDNOM.DNOM_PROTOCOL_VERSION)) {
				return;
			}
		
			//String clientaddr=socketChannel.socket().getRemoteSocketAddress().toString();
		
			while(true) {			
				byteBuffer = ByteBuffer.allocate(4);
				readLen =readFromChannel(byteBuffer, 4);
				if(readLen==-1) {			
					return;
				}
				baMagicCode = byteBuffer.array();
					
				byteBuffer = ByteBuffer.allocate(2);
				readLen =readFromChannel(byteBuffer, 2);
				if(readLen==-1) {			
					return;
				}
				fullServiceNameLen = ArrayUtil.BAToShort(byteBuffer.array());
				
				byteBuffer = ByteBuffer.allocate(fullServiceNameLen);
				readLen = readFromChannel(byteBuffer,fullServiceNameLen);		
				baTemp = new byte[fullServiceNameLen];
				System.arraycopy(byteBuffer.array(),0,baTemp,0,fullServiceNameLen);
				fullServiceName = new String(baTemp);
					
				if(baMagicCode[3]==PublicDNOM.TRANSACTION_TYPE_GET_STATUS) { // 10 ..
					byteBuffer = ByteBuffer.allocate(8);
					readLen =readFromChannel(byteBuffer, 8);
					if(readLen==-1) {			
						return;
					}
					long tosID = ArrayUtil.BAToLong(byteBuffer.array());

					String[] tokenArr2 = fullServiceName.split("/");			
					
					// TODO TrustSQL tablename_error Table에서 조회하도록 수정해야 함. 
					int errorCode=0;
					clientStatusResponse(errorCode); // Not supported Service..
							
				} else {										
					byteBuffer = ByteBuffer.allocate(2);
					readLen =readFromChannel(byteBuffer, 2);						
					payloadLen = ArrayUtil.BAToShort(byteBuffer.array());
		
					byteBuffer = ByteBuffer.allocate(payloadLen);
					readLen = readFromChannel(byteBuffer, payloadLen);	
					baTemp = new byte[payloadLen];
					System.arraycopy(byteBuffer.array(),0,baTemp,0,payloadLen);
					payloadStr = new String(baTemp);
									
					PublicDNOM.ServiceConfig sc = publicDnom.checkMsgsInService(fullServiceName);					
					if(sc == null) {
						clientResponse(5001); // Not supported Service... DOM
						continue;
					}
					String tossAddrs = sc.tossAddrs;
					TOSTransaction tosTransaction = new TOSTransaction();
					
					String[] tokenArr;
					tokenArr = tossAddrs.split(":");
					ttobConnector = hmTossConnectors.get(fullServiceName);
					if(ttobConnector==null) {
						ttobConnector = new TOSSConnector(tokenArr[0],Integer.parseInt(tokenArr[1]));	
						ttobConnector.open();
						hmTossConnectors.put(fullServiceName,ttobConnector);
					}
					
					int errorCode;					
					errorCode = ttobConnector.publishTransaction(fullServiceName,payloadStr,tosTransaction);					
					if(errorCode!=0) {
						clientResponse(errorCode); // Not supported Service... TOSS						
						continue;
					}
										
					boolean syncMode=false;			
					if(baMagicCode[3]==PublicDNOM.TRANSACTION_TYPE_SYNC_DML) {
						syncMode=true;
					}
					if(syncMode==true) {												
						Thread.sleep(1);
						Integer sqlErrorCode;
						int timeOutCounter=100;
						while(timeOutCounter>0) {
							synchronized(hmAllErrorCodes) {								
								sqlErrorCode = (Integer)hmAllErrorCodes.get(fullServiceName+"/"+tosTransaction.order_in_table);								
							}
							if(sqlErrorCode==null) {								
								Thread.sleep(100);
								log.trace("\t@@@ Waiting TRNASCTION Result of "+fullServiceName+"/"+tosTransaction.order_in_table);								
								timeOutCounter--;
								continue;
							} else {
								//System.out.println("\t@@@ Success receive TRANSACTION Result : "+sqlErrorCode.intValue());								
								hmAllErrorCodes.remove(fullServiceName+"/"+tosTransaction.order_in_table);
								clientResponse(sqlErrorCode.intValue(),tosTransaction); 
								break;
							}
						}
						if(timeOutCounter<1) {
							log.error("[Publisher Thread] Transaction Timeout "+fullServiceName+"/"+tosTransaction.order_in_table);
							clientResponse(9999);
						}
					} else {						
						clientResponse(tosTransaction); 
					}
				}
			}	// While										
		} catch (Exception e) {		
			e.printStackTrace();	
		}finally { 
			try {
				if(socketChannel != null) {
					socketChannel.close();
				}
				// 	TODO close all RandomAccesFiles in hmAllFiles !!!
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		}
	} // function run 
} // class PublichserThread