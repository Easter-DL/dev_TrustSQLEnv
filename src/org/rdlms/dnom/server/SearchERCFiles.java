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

import org.rdlms.util.ArrayUtil;  
 
import java.io.RandomAccessFile;
import java.util.*;
 
public class SearchERCFiles {
	
	public static int checkIndex(String issuer, String service, String ledger) {		
		RandomAccessFile idxFile=null,datFile=null, ercFile=null;
				
		try {
			idxFile = new RandomAccessFile("./data/"+issuer+"/"+service+"/"+ledger+".idx","r");						
			
			idxFile.seek(0);
			
			long order_no;
			int fine_no;
			long offset;
			
			long totalRecord = idxFile.length()/14;
			//System.out.println("./data/"+issuer+"/"+service+"/"+ledger+".idx, index File Size  ="+idxFile.length()+" total Records ="+totalRecord);
			if(totalRecord==0) {
				byte[] baFNOffset = new byte[6];
				Arrays.fill(baFNOffset,(byte)0);				
				//System.out.println("No data");				
				return 0;
			}				
			
			byte[] baIdxOrderNo= new byte[8];
			byte[] baIdxFileNo = new byte[2];
			byte[] baIdxOffset = new byte[4];						
			long idxOrderNo;
			int	 idxFileNo,idxLastFileNo;
			long idxOffset;
			
			byte[] baDatOrderNo = new byte[8];
			byte[] baPayloadLen = new byte[2];
			byte[] baPayload;
			long datOrderNo;
			int  payloadLen;
			
			idxLastFileNo=0;								
			idxFile.seek(0);
			for(long i=0; i<totalRecord; i++) {
				//System.out.println("i="+i+String.format(" OFFSET=%d  0x%08X",i*14,i*14));
				idxFile.seek(i*14);
				idxFile.read(baIdxOrderNo);	
				idxFile.read(baIdxFileNo);
				idxFile.read(baIdxOffset);
				
				idxOrderNo = ArrayUtil.BAToLong(baIdxOrderNo);
				idxFileNo  = ArrayUtil.BAToShort(baIdxFileNo);
				idxOffset  = ArrayUtil.BAToInt(baIdxOffset);
				
				if(idxFileNo!=idxLastFileNo) {					
					datFile = new RandomAccessFile("./data/"+issuer+"/"+service+"/"+ledger+"_#"+String.format("%05d",idxFileNo)+".dat","r");
				  idxLastFileNo=idxFileNo;
				}
								
				if((i+1)!=idxOrderNo) {
					System.out.println("wrong order of Msgs!");
					System.out.println("i+1="+(i+1));
					System.out.println("idxOrderNo="+idxOrderNo);
					idxFile.close();
					datFile.close();
					return 0;
				}
				
				datFile.seek(idxOffset);
				datFile.read(baDatOrderNo);
				datFile.read(baPayloadLen);					
				datOrderNo = ArrayUtil.BAToLong(baDatOrderNo);
				payloadLen = ArrayUtil.BAToShort(baPayloadLen);					
				baPayload = new byte[payloadLen];
				datFile.read(baPayload);
				//System.out.println("\t idxOrderNo="+idxOrderNo+"   datOrderNo="+datOrderNo);
				if(idxOrderNo!=datOrderNo) {
					//System.out.println("Mismatch between IDX and DAT!");
					return 0; 
				}
				byte[] baFNOffset = new byte[6];
				System.arraycopy(baFNOffset,0,baIdxFileNo,0,2);
				System.arraycopy(baFNOffset,2,baIdxOffset,0,4);				
			}
		
		} catch (Exception e) {
			e.printStackTrace();			
			return 0;
		} finally {
	try {
   	if(idxFile != null) { idxFile.close(); }
if(datFile != null) { datFile.close(); }
  }catch (Exception e) {
  	e.printStackTrace();
  }
		}
		return 1;
	}
	
	public static int checkIndex(String path) {		
		RandomAccessFile idxFile=null,datFile=null, ercFile=null;;
				
		try {
			idxFile = new RandomAccessFile(path+".idx","r");						
			
			idxFile.seek(0);
			
			long order_no;
			int fine_no;
			long offset;
			int mode=1;  // mode =1 check idx, dat, erc  mode=0 check idx, dat
			
			long totalRecord = idxFile.length()/14;
			System.out.println(path+".idx, index File Size  ="+idxFile.length()+" total Records ="+totalRecord);
			if(totalRecord==0) {
				byte[] baFNOffset = new byte[6];
				Arrays.fill(baFNOffset,(byte)0);				
				System.out.println("No data");				
				return 0;
			}				
			
			byte[] baIdxOrderNo= new byte[8];
			byte[] baIdxFileNo = new byte[2];
			byte[] baIdxOffset = new byte[4];						
			long idxOrderNo;
			int	 idxFileNo,idxLastFileNo;
			long idxOffset;
			
			byte[] baDatOrderNo = new byte[8];
			byte[] baPayloadLen = new byte[2];
			byte[] baPayload;
			long datOrderNo;
			int  payloadLen;
			
			idxLastFileNo=0;								
			idxFile.seek(0);
			byte[] baErcOrderNo = new byte[8];
			byte[] baErcErrorCode = new byte[4];
			long ercOrderNo=0;
			int  ercErrorCode=0;
			
			try {			
				ercFile = new RandomAccessFile(path+".erc","r");						
			} catch (Exception e) {
			}
			
			if(ercFile==null) mode = 0;
			
			for(long i=0; i<totalRecord; i++) {
				System.out.println("i="+i+String.format(" OFFSET=%d  0x%08X",i*14,i*14));
				idxFile.seek(i*14);
				idxFile.read(baIdxOrderNo);	
				idxFile.read(baIdxFileNo);
				idxFile.read(baIdxOffset);
				
				idxOrderNo = ArrayUtil.BAToLong(baIdxOrderNo);
				idxFileNo  = ArrayUtil.BAToShort(baIdxFileNo);
				idxOffset  = ArrayUtil.BAToInt(baIdxOffset);
				
				if(mode==1) {
					ercFile.seek(i*12); // order (8) + ErrorCode(4);
					ercFile.read(baErcOrderNo);	
					ercFile.read(baErcErrorCode);
					
					ercOrderNo = ArrayUtil.BAToLong(baErcOrderNo);
					ercErrorCode = ArrayUtil.BAToInt(baErcErrorCode);					
				}
				
				if(idxFileNo!=idxLastFileNo) {					
					datFile = new RandomAccessFile(path+"_#"+String.format("%05d",idxFileNo)+".dat","r");
				  idxLastFileNo=idxFileNo;
				}
								
				if((i+1)!=idxOrderNo) {
					System.out.println("FAIL !!!! order of Msgs!");
					System.out.println("i+1="+(i+1));
					System.out.println("idxOrderNo="+idxOrderNo);					
					return 0;
				}
				if(mode==1) {
					if((i+1)!=ercOrderNo) {
						System.out.println("FAIL !!!! order of ERC Msgs!");
						System.out.println("i+1="+(i+1));
						System.out.println("ercOrderNo="+ercOrderNo);					
						return 0;
					}
				}
								
				
				datFile.seek(idxOffset);
				datFile.read(baDatOrderNo);
				datFile.read(baPayloadLen);					
				datOrderNo = ArrayUtil.BAToLong(baDatOrderNo);
				payloadLen = ArrayUtil.BAToShort(baPayloadLen);					
				baPayload = new byte[payloadLen];
				datFile.read(baPayload);
				System.out.println("\t idxOrderNo="+idxOrderNo+"   datOrderNo="+datOrderNo+ " ercOrderNo="+ercOrderNo+"  ercErrorCode="+ercErrorCode);
				if(idxOrderNo!=datOrderNo) {
					System.out.println("FAIL !!!!  Mismatch between IDX and DAT!");
					return 0; 
				}
				
				if(mode==1) {
					if(idxOrderNo!=ercOrderNo) {
						System.out.println("FAIL !!!!  Mismatch between IDX and ERC!");
						return 0; 
					}
				}
				
				
				byte[] baFNOffset = new byte[6];
				System.arraycopy(baFNOffset,0,baIdxFileNo,0,2);
				System.arraycopy(baFNOffset,2,baIdxOffset,0,4);
				
			}
		
		} catch (Exception e) {
			e.printStackTrace();			
			return 0;
		} finally {
			try {
				if(idxFile != null) { idxFile.close(); }
				if(datFile != null) { datFile.close(); }
				if(ercFile != null) { ercFile.close(); }
			}catch (Exception e) {
				e.printStackTrace();
			}
		}
		return 1;
	}
		
	
	public static void main(String[] args) {
		String fullServiceName=null;
		String issuer,service,ledger;
	
		if(args.length!=1) {
			System.out.println("Let me know name of .ERC file to check...");
			return;
		}  

	/*
	while(true) {
		System.out.println("Which menu don you want ?  (0: Search Error Cde by SYNC_ID 1:DUMP ALL ERROR ");
		System.out.prknt
	
	
	
	if(checkIndex(args[0])==0) {
		System.out.println("Fail !");
	} else {
		System.out.println("Success !");
	}
	/*
	
	if(checkIndex(issuer,service,ledger)==0) {
		System.out.println("Fail !");
	} else {
		System.out.println("Success !");
	}
	*/
	}		
}
