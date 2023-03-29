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

package org.rdlms.messagestorage.control;
  
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Scanner;

import org.rdlms.util.ArrayUtil;
 
public class CheckDataFiles {
		
	public static int checkIndex(String path) {		
		RandomAccessFile idxFile=null,datFile=null, ercFile=null;;
				
		try {
			idxFile = new RandomAccessFile(path+".idx","r");						
			
			idxFile.seek(0);
			
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
				
				System.out.print(String.format("%d/%d\r",i,totalRecord));				
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
					System.out.println("\n");
					System.out.println("FAIL !!!! Index File, out of order!");
					System.out.println("i+1="+(i+1));
					System.out.println("idxOrderNo="+idxOrderNo);					
				}
				if(mode==1) {
					if((i+1)!=ercOrderNo) {
						System.out.println("\n");
						System.out.println("FAIL !!!! ERC File, out of order!");
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
				if(idxOrderNo!=datOrderNo) {					
					System.out.println("\n");
					System.out.println("FAIL !!!!  Mismatch between IDX and DAT!");
					System.out.println("\t idxOrderNo="+idxOrderNo+"   datOrderNo="+datOrderNo+ " ercOrderNo="+ercOrderNo+"  ercErrorCode="+ercErrorCode);				
				}
				
				if(mode==1) {
					if(idxOrderNo!=ercOrderNo) {
						System.out.println("FAIL !!!!  Mismatch between IDX and ERC!");
						System.out.println("\t idxOrderNo="+idxOrderNo+"   datOrderNo="+datOrderNo+ " ercOrderNo="+ercOrderNo+"  ercErrorCode="+ercErrorCode);									
					}
				}
				
				if(ercErrorCode!=0) {
					System.out.println("FAIL !!!!  Transaction Fail !");
					System.out.println("\t idxOrderNo="+idxOrderNo+"   datOrderNo="+datOrderNo+ " ercOrderNo="+ercOrderNo+"  ercErrorCode="+ercErrorCode);									
					System.out.println("\nPAYLOAD=\n"+new String(baPayload)+"\n");
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
	
	
	public static int searchTransaction(long sync_id, String path) {
		
		RandomAccessFile idxFile=null,datFile=null, ercFile=null;;
				
		try {
			idxFile = new RandomAccessFile(path+".idx","r");						
			
			idxFile.seek(0);
			
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
							
			idxFile.seek(sync_id*14);
			idxFile.read(baIdxOrderNo);	
			idxFile.read(baIdxFileNo);
			idxFile.read(baIdxOffset);
				
			idxOrderNo = ArrayUtil.BAToLong(baIdxOrderNo);
			idxFileNo  = ArrayUtil.BAToShort(baIdxFileNo);
			idxOffset  = ArrayUtil.BAToInt(baIdxOffset);
			
			if(idxFileNo!=idxLastFileNo) {					
					datFile = new RandomAccessFile(path+"_#"+String.format("%05d",idxFileNo)+".dat","r");
				  idxLastFileNo=idxFileNo;
			}				
			
			ercFile.seek(sync_id*12); // order (8) + ErrorCode(4);
			ercFile.read(baErcOrderNo);	
			ercFile.read(baErcErrorCode);
					
			ercOrderNo = ArrayUtil.BAToLong(baErcOrderNo);
			ercErrorCode = ArrayUtil.BAToInt(baErcErrorCode);					
				
				
			datFile.seek(idxOffset);
			datFile.read(baDatOrderNo);
			datFile.read(baPayloadLen);					
			datOrderNo = ArrayUtil.BAToLong(baDatOrderNo);
			payloadLen = ArrayUtil.BAToShort(baPayloadLen);					
			baPayload = new byte[payloadLen];
			datFile.read(baPayload);
			
			System.out.println("idxOrderNo="+idxOrderNo+"   datOrderNo="+datOrderNo+ " ercOrderNo="+ercOrderNo+"  ercErrorCode="+ercErrorCode);				
			System.out.println("PAYLOAD="+new String(baPayload));
			
			byte[] baFNOffset = new byte[6];
			System.arraycopy(baFNOffset,0,baIdxFileNo,0,2);
			System.arraycopy(baFNOffset,2,baIdxOffset,0,4);				
				
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
  	
  	Scanner sc = new Scanner(System.in);
	String dataHome=null;
	String fullServiceName=null;
						
    if(args.length!=2) {
		System.out.println("\n\n");
		System.out.println("[USAGE] dataHome FULL_SERVICE_NAME");
		System.out.println("\n\n");
		System.out.println("example ) NodeA_data www.trust-db.com/SG_EU_H/TOKEN_HEALTHCARE_TRANSFER");
		System.out.println("\n\n");
		return;
		}
		
		dataHome=args[0];
		fullServiceName=args[1];
		
		System.out.println("\n"); 
		System.out.println("*======================================================*");  
		System.out.println("|                                                      |"); 
		System.out.println("|                     CheckDataFiles                   |");  
		System.out.println("|                ------------------------              |");
		System.out.println("|                                                      |"); 
		System.out.println("|            Copyright (c) 2020, TRUSTDB Inc.          |");	        	        
		System.out.println("*======================================================*");  
		System.out.println("\n\n");
		
		String workMode=null;
		String sync_id=null;
		while(true) {
			System.out.println("=> What do you want ? ( 0: Search All Errors  1: Transaction Exploror)");
			workMode = sc.nextLine();
			if(workMode.equals("0")) {							
				System.out.println("\n");			
				checkIndex(args[0]+"/"+args[1]);
				System.out.println("\nFinished!\n");			
			} else {
				System.out.println("=> What is SYNC_ID you want to search ?");
				sync_id = sc.nextLine();
				searchTransaction(Long.parseLong(sync_id), args[0]+"/"+args[1]);			
				System.out.println("\nFinished!\n");
			}		
		}
	} 	
	
}
