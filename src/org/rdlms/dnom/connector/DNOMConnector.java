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

package org.rdlms.dnom.connector;
 
import java.io.OutputStream;
import java.io.InputStream;
import java.net.Socket;
import org.rdlms.util.ArrayUtil;

public class DNOMConnector {
	final static int RECEIVE_BUFFER_SIZE=4096;
	final static String PROTOCOL_VERSION="DNOM_PROTOCOLV_0001";

	public final static byte NETWORK_TYPE_NA=0;
	public final static byte NETWORK_TYPE_MAIN=1;
	public final static byte NETWORK_TYPE_TEST=2;
		
	public final static short NETWORK_ID_BIGBLOCKS=1;

	public final static byte TRANSACTION_TYPE_ADMIN=1;
	public final static byte TRANSACTION_TYPE_DDL=2;
	public final static byte TRANSACTION_TYPE_ASYNC=3;
	public final static byte TRANSACTION_TYPE_SYNC=4;
	public final static byte TRANSACTION_TYPE_GET_STATUS=10;
			
	byte  NETWORK_TYPE=0;
	short NETWORK_ID=0;
	byte  TRANSACTION_TYPE=0;

	Socket SOCK = null;
	OutputStream OUTPUTS = null;
	InputStream  INPUTS = null;
	String HOSTADDRESS=null;
	int HOSTPORT=0;
    		
	public DNOMConnector(String dnomAddr,  int dnomPort) {
		this.HOSTADDRESS = dnomAddr;
		this.HOSTPORT = dnomPort;
	}
			
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
    
	public boolean open(byte networkType, short networkID) {
		this.NETWORK_TYPE=networkType;
	 	this.NETWORK_ID=networkID;	 		
	    return open();
    }
   		
	private int readFromOStream(byte[] baReturn, int minLength) {		
		int minusCounter=0, plusCounter=0;
		int readLen=0;
		byte[] baTemp = new byte[minLength];
		minusCounter=minLength;
    	try {
			while(minusCounter > 0) {    		
				readLen = this.INPUTS.read(baTemp);      			
				if (readLen == -1) {        					
					return -1;
				}         
				System.arraycopy(baTemp,0,baReturn,plusCounter,readLen);
				minusCounter-=readLen;	        
				plusCounter +=readLen;			
			}		        
		} catch(Exception e) { 
			e.printStackTrace();
			plusCounter=-1;
		}    	
		return plusCounter;
	}
       
	public byte[] transmit(byte transactionType, String serviceName, String payload) {    
		byte[] baMagicCode = new byte[4];
		byte[] baReturn;
		byte[] baTemp;
		int readLen,readLen2;

		try {
			if(SOCK==null) return null;
			if(OUTPUTS==null) return null;
			if(INPUTS==null) return null;
			
			baMagicCode[0]=this.NETWORK_TYPE;
			System.arraycopy(ArrayUtil.shortToBA(this.NETWORK_ID),0,baMagicCode,1,2);
			baMagicCode[3]=transactionType;
			
			OUTPUTS.write(baMagicCode);
			OUTPUTS.write(ArrayUtil.shortToBA((short)serviceName.getBytes().length));
			OUTPUTS.write(serviceName.getBytes());
			OUTPUTS.flush();
				
			OUTPUTS.write(ArrayUtil.shortToBA((short)payload.getBytes().length));
			OUTPUTS.write(payload.getBytes());
			OUTPUTS.flush();
				
			baTemp = new byte[1024];
			readLen=readFromOStream(baTemp,14);        
			if(readLen==-1) return null;        
			
			byte[] baPayloadLen = new byte[2];
			System.arraycopy(baTemp,12,baPayloadLen,0,2);
			short payloadLen =(short) ArrayUtil.BAToShort(baPayloadLen);
			baReturn = new byte[14+payloadLen];        
			System.arraycopy(baTemp,0,baReturn,0,readLen);
			if(payloadLen!=0) { 
				baTemp = new byte[8192];        	
				readLen2=readFromOStream(baTemp,payloadLen-(readLen-14));        
				if(readLen2==-1) return null;
				System.arraycopy(baTemp,0,baReturn,readLen,payloadLen-(readLen-14));
			}        
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}         
		return baReturn;
    }		
    
    
    public int getStatus(String serviceName, long transactionID) {    
    	byte[] baMagicCode = new byte[4];
    	int    iReturn;
    	byte[] baTemp;
    	int readLen;
    	
    	try {
			if(SOCK==null) return -1;
			if(OUTPUTS==null) return -1;
			if(INPUTS==null) return -1;
			
			baMagicCode[0]=this.NETWORK_TYPE;
			System.arraycopy(ArrayUtil.shortToBA(this.NETWORK_ID),0,baMagicCode,1,2);
			baMagicCode[3]=TRANSACTION_TYPE_GET_STATUS;

			OUTPUTS.write(baMagicCode);
			OUTPUTS.write(ArrayUtil.shortToBA((short)serviceName.getBytes().length));
			OUTPUTS.write(serviceName.getBytes());
			OUTPUTS.write(ArrayUtil.longToBA(transactionID));
			OUTPUTS.flush();

			baTemp = new byte[1024];
			readLen=readFromOStream(baTemp,4);        
			if(readLen==-1) return -1;    
			iReturn = ArrayUtil.BAToInt(baTemp);       
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}         
		return iReturn;
    }		
    
    public void close() {
		try {
			if(OUTPUTS!=null) OUTPUTS.close();
			if(SOCK!=null) SOCK.close();
		} catch (Exception e) { e.printStackTrace();	}    
	}
    
    public static void main(String args[]) {
    	System.out.println("This is main !");    	
    }
}
