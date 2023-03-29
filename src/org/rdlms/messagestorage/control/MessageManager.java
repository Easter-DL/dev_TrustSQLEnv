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

import java.io.File;
import java.io.RandomAccessFile;
import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.rdlms.messagestorage.model.ServiceOrder;
import org.rdlms.messagestorage.model.ServiceOrderInfo;
import org.rdlms.messagestorage.model.TOSTransaction;
import org.rdlms.messagestorage.model.TableOrder;
import org.rdlms.trustsql.TrustSQLManager;
import org.rdlms.util.ArrayUtil;
import org.rdlms.util.Assert;

public class MessageManager {


    /**
	* readServiceOrder
	* read ServiceOrder info (order_in_service, table_name, order_in_table) with given ois.
	* @param	log  logger object
    * @param    hmAllFilesR HashMap Object to store File objects.
    * @param	folderPath 
    * @param	serviceName 
    * @param    orderInService 
	* @reutrn 	serviceOrder {order_in_service, table_name, order_in_table}
	*/
    public static ServiceOrder readServiceOrder(Logger log, ConcurrentHashMap<String,RandomAccessFile> hmAllFilesRW, String folderPath, String globalServiceName, long orderInService) {
        ServiceOrder serviceOrder= new ServiceOrder();
        RandomAccessFile oisFile=null;
		String[] tokenArr = globalServiceName.split("/");
		String serviceName = tokenArr[1];
        int fileNo;
		String fileName;
        long offset;		

		try {
            fileNo = (int)((orderInService / ServiceOrder.MAX_RECORDS_IN_ORDER_SERVICE)+1);
            fileName = folderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",fileNo)+".ois";
			oisFile = hmAllFilesRW.get(fileName);
            if(oisFile==null) {
                oisFile = new RandomAccessFile(fileName,"rw");	
				hmAllFilesRW.put(fileName, oisFile);							
            }


			//  MAX_RCORDS_IN_ORDER_SERVICE = 100, RECORD_SIZE=80
            //      orderInService = 99
            //          file_no = 1
            //          offset = ((orderInService-1) % MAX_RCORDS_IN_ORDER_SERVICE)  * RECORD_SIZE = 98 * 80 
            //      orderInService = 100
            //          file_no = 2
            //          offset = ((orderInService-1) % MAX_RCORDS_IN_ORDER_SERVICE)  * RECORD_SIZE = 99 * 80 
            //      orderInService = 101
            //          file_no = 2
            //          offset = ((orderInService-1) % MAX_RCORDS_IN_ORDER_SERVICE)  * RECORD_SIZE = 0 * 80 

            offset = ((orderInService-1) % ServiceOrder.MAX_RECORDS_IN_ORDER_SERVICE) * ServiceOrder.RECORD_SIZE;
			oisFile.seek(offset);				
			byte[] baOrderInService = new byte[8];
			oisFile.read(baOrderInService);	
			byte[] baTableNameLen = new byte[2];
			oisFile.read(baTableNameLen);	
			byte[] baTableName = new byte[ArrayUtil.BAToShort(baTableNameLen)];
			oisFile.read(baTableName);	
			byte[] baTemp = new byte[64-2-ArrayUtil.BAToShort(baTableNameLen)];			
			oisFile.read(baTemp);	
			byte[] baOrderInTable = new byte[8];						
			oisFile.read(baOrderInTable);
            
			serviceOrder.order_in_service = ArrayUtil.BAToLong(baOrderInService);
			serviceOrder.order_in_table = ArrayUtil.BAToLong(baOrderInTable);
			serviceOrder.table_name = new String(baTableName).trim();
			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} 		
        return serviceOrder;
    }

    /**
	* readTableOrder
	* read TableOrder info (order_in_table, file_no, offset) with serviceOrder
	* @param	log  logger object
    * @param    hmAllFilesR HashMap Object to store File objects.
    * @param	folderPath 
    * @param	serviceName 
    * @param    serviceOrder
	* @reutrn 	tableOrder {order_in_table, file_no, offset}
	*/
    public static TableOrder readTableOrder(Logger log, ConcurrentHashMap<String,RandomAccessFile> hmAllFilesRW, String folderPath, String globalServiceName, ServiceOrder serviceOrder) {
        TableOrder tableOrder= new TableOrder();
        RandomAccessFile oitFile=null;
        int fileNo;
		String fileName;
        long offset;		
		try {			

            fileNo = (int)((serviceOrder.order_in_table / TableOrder.MAX_RECORDS_IN_ORDER_TABLE)+1);
            fileName = folderPath+"/"+globalServiceName+"/"+serviceOrder.table_name+"_#"+String.format("%05d",fileNo)+".oit";			
			oitFile = hmAllFilesRW.get(fileName);
            if(oitFile==null) {
                oitFile = new RandomAccessFile(fileName,"rw");	
				hmAllFilesRW.put(fileName,oitFile);							
            }

			offset = (long)((serviceOrder.order_in_table-1) % TableOrder.MAX_RECORDS_IN_ORDER_TABLE) * TableOrder.RECORD_SIZE;
			oitFile.seek(offset);
				
			byte[] baOrderInTable = new byte[8];
			byte[] baFileNo = new byte[2];
			byte[] baOffset = new byte[4];			
							
			oitFile.read(baOrderInTable);	
			oitFile.read(baFileNo);
			oitFile.read(baOffset);
            
			tableOrder.order_in_table = ArrayUtil.BAToLong(baOrderInTable);
			tableOrder.file_no = (short)ArrayUtil.BAToShort(baFileNo);
			tableOrder.offset = ArrayUtil.BAToInt(baOffset);
		} catch (Exception e) {
			log.error(e.toString());
			return null;
		} 		
		
        return tableOrder;
    }

    /**
	* readTOSTransaction
	* read TOSTrnasaction ServiceOrder info (order_in_service, table_name, order_in_table) by given ois.
	* @param	log  logger object
    * @param    hmAllFilesR HashMap Object to store File objects.
    * @param	folderPath 
    * @param	serviceName 
    * @param    tableOrder 
	* @reutrn 	serviceOrder {order_in_service, table_name, order_in_table}
	*/
    public static TOSTransaction readTOSTransaction(Logger log, ConcurrentHashMap<String,RandomAccessFile> hmAllFilesRW, String folderPath, String issuerName, String serviceName, String tableName, TableOrder tableOrder) {
        TOSTransaction tosTransaction= new TOSTransaction();

        RandomAccessFile tosFile=null;
        int fileNo;
		String fileName;
        long offset;		
		try {
            fileNo = tableOrder.file_no;
            fileName = folderPath+"/"+serviceName+"/"+tableName+"_#"+String.format("%05d",fileNo)+".tos";
			tosFile = hmAllFilesRW.get(fileName);
            if(tosFile==null) {
                tosFile = new RandomAccessFile(fileName,"rw");
				hmAllFilesRW.put(fileName,tosFile);								
            }
            
            tosFile.seek(tableOrder.offset);
		
            tosTransaction.gt_name = serviceName+"/"+tableName;
            
            byte[] baLong = new byte[8];
            byte[] baShort = new byte[2];
            byte[] baString;
            
            // order_in_service
            tosFile.read(baLong);
            tosTransaction.order_in_service = ArrayUtil.BAToLong(baLong);
            // order_in_table
            tosFile.read(baLong);
            tosTransaction.order_in_table = ArrayUtil.BAToLong(baLong);
            // stamepd_transaction
            tosFile.read(baShort);
            baString = new byte[ArrayUtil.BAToShort(baShort)];
            tosFile.read(baString);
            tosTransaction.stamped_transaction = new String(baString);
            // tosa_account
            tosFile.read(baShort);
            baString = new byte[ArrayUtil.BAToShort(baShort)];
            tosFile.read(baString);
            tosTransaction.tosa_account = new String(baString);
            // tosa_sign
            tosFile.read(baShort);
            baString = new byte[ArrayUtil.BAToShort(baShort)];
            tosFile.read(baString);
            tosTransaction.tosa_sign = new String(baString);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} 		

		return tosTransaction;        
    }

    /**
	* searchLastOrderFromStorage
	* search last order of service and return result.
	* @param	log  logger object
    * @param	folderPath 
    * @param	globalServiceName  search last order_in_service named globalServiceName
	* @reutrn 	ServiceOrderInfo { last order_in_service and HashMap<table, order_in_table> }
	*/   								
	public static ServiceOrderInfo readLastOrderFromStorage(Logger log, String folderPath, String globalServiceName) {						
		ServiceOrderInfo serviceOrderInfo = new ServiceOrderInfo();
		ConcurrentHashMap<String, Long> hmTableOrders = new ConcurrentHashMap<String, Long>();		
		RandomAccessFile orderFile=null;
		String fileName=null;
		String[] tokenArr = globalServiceName.split("/");
		String serviceName = tokenArr[1];
		try {
			File dataDir = new File(folderPath+"/"+globalServiceName);
			
			if(!dataDir.exists()) {				
				return null;
			}
						
			int file_no=1;	// file number start from 1.			
			long totalRecordInFile;  //
			long expectedLastOrderInService=1; // order no start from 1
			long   lastOrderInService=0;
			long   lastOrderInTable=0;
			while(true) {
				// new RandomAccessFile throws FileNotFoundException , when the file is not exist.
				try {
					fileName = folderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",file_no)+".ois";
					orderFile = new RandomAccessFile(fileName,"rw");	
				} catch (Exception e) {
					orderFile=null;
				};
				
				if(orderFile==null) {
					// If orderFile (OIS) file is not exist, make it. 
					file_no=1;
					fileName = folderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",file_no)+".ois";										
					File newFile = new File(fileName);
					newFile.createNewFile();
					orderFile = new RandomAccessFile(fileName,"rw");  
					break;					
				}						
				totalRecordInFile = orderFile.length()/80; // order_in_service(8)+table_name(64)+order_in_table			
				if(totalRecordInFile==0) {									
					break;
				}				
				
				byte[] baOrderInService = new byte[8];
				byte[] baOrderInTable = new byte[8];							
				byte[] baTableNameLen = new byte[2];
				byte[] baTableName=null;			
				byte[] baTemp=null;	
				String tableName;
							
				for(long i=0; i<totalRecordInFile; i++) {
					orderFile.read(baOrderInService);	
					//System.out.println(ArrayUtil.toHex(baOrderInService));
					//System.out.println("offset="+orderFile.getFilePointer());
					orderFile.read(baTableNameLen);
					//System.out.println(ArrayUtil.toHex(baTableNameLen));
					//System.out.println("offset="+orderFile.getFilePointer());
					baTableName = new byte[ArrayUtil.BAToShort(baTableNameLen)];
					orderFile.read(baTableName);
					//System.out.println(ArrayUtil.toHex(baTableName));
					//System.out.println("offset="+orderFile.getFilePointer());

					baTemp = new byte[64-2-ArrayUtil.BAToShort(baTableNameLen)];
					orderFile.read(baTemp);
					//System.out.println(ArrayUtil.toHex(baTemp));
					//System.out.println("offset="+orderFile.getFilePointer());
					orderFile.read(baOrderInTable);
					//System.out.println(ArrayUtil.toHex(baOrderInTable));
					//System.out.println("offset="+orderFile.getFilePointer());
					
					lastOrderInService = ArrayUtil.BAToLong(baOrderInService);
					lastOrderInTable = ArrayUtil.BAToLong(baOrderInTable);
					tableName = new String(baTableName).trim();

					if(lastOrderInService!=expectedLastOrderInService) {
						log.error("ORDER file verification failed ! expected order="+expectedLastOrderInService+", but FILE="+file_no+" lastOrderInService="+lastOrderInService); 
						Assert.assertTrue(true,"ORDER file verification failed ! expected order="+expectedLastOrderInService+", but FILE="+file_no+" lastOrderInService="+lastOrderInService);
						System.exit(1);
						return null;
					}

					if(hmTableOrders.containsKey(tableName)) {
						hmTableOrders.replace(tableName, new Long(lastOrderInTable));
					} else {
						hmTableOrders.put(tableName,new Long(lastOrderInTable));
					}
					expectedLastOrderInService++;
				}
				orderFile.close();
				break;
			}
			serviceOrderInfo.order_in_service = lastOrderInService;
			serviceOrderInfo.hm_table_orders = hmTableOrders;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				if(orderFile != null) { orderFile.close(); }
			}catch (Exception e) {
				e.printStackTrace();
			}
		}
		return serviceOrderInfo;        
	}    

	/**	
	* Save multiple messages permenantly.
	*		
	* @param	TOSTransaction[]
	* @param	payload (stamped_transaction)
	* @reutrn	true : success, false : fail
	*/
	public static boolean saveTranactions(Logger log, String dataFolderPath, int maxFileSizeMB, ConcurrentHashMap<String,RandomAccessFile> hmAllFilesRW, TOSTransaction[] tosTransactions) { 			
		boolean bRet=true;
		for(int i=0; i<tosTransactions.length; i++) {
			bRet=MessageManager.saveTranaction(log, dataFolderPath, maxFileSizeMB, hmAllFilesRW, tosTransactions[i]); 	
		}	

		return true;
	}
    
	/**	
	* Save message permenantly.
	*
	* A.Service Order File Structre
	*	File name : service_name_#05d.order  Max Records in a File = 50,000,000
	*	Record : (80bytes)
	*		order_in_service 	u8
	*		table_name			char[64]
	* 		order_in_table		u8
	*
	* B. Table Data Index File				Max Records in a File = 300,000,00
	* 	File name : table_name_#05d.idx
	*	Record : (14bytes)
	*		order_in_table		u8
	*		data_file_no		u2
	*		offset				u4		
	* C. Table Data File
	*	File name : table_name_#05d.dat
	*	Record : Variable
	*		order_in_service	u8
	*		order_in_table		u8
	*		stamped_transaction_len u2
	*		stamped_transaction u1[stamped_transaction_len]
	*		tosa_account_len	u2
	*		tosa_account		u1[tosa_account_len]
	*		tosa_sign_len		u2
	*		tosa_sign			u1[tosa_sign_len]
	*		
	* @param	TOSTransaction
	* @param	payload (stamped_transaction)
	* @reutrn	true : success, false : fail
	*/
	public static boolean saveTranaction(Logger log, String dataFolderPath, int maxFileSizeMB, ConcurrentHashMap<String,RandomAccessFile> hmAllFilesRW, TOSTransaction tosTransaction) { 			
		String issuerName, serviceName, tableName;	
		String[] tokenArr = tosTransaction.gt_name.split("/");			
		issuerName = tokenArr[0];
		serviceName = tokenArr[1];
		tableName = tokenArr[2].trim();	
		String globalServiceName = issuerName+"/"+serviceName;
		RandomAccessFile oisFile=null,oitFile=null,lastDatFile=null,datFile=null;
		String 	fileName;
		short  	fileNo;		
		long 	recordOffset = 0;;
		long 	maxDataFileSize = (long)(maxFileSizeMB*1024*1024);
		
		Assert.assertTrue(tableName!=null,"Table name is null ="+tableName);

		try {
			// 1. Save order_in_service
			// 1.1 find ois file where the new record added
			fileNo = (short)((tosTransaction.order_in_service / ServiceOrder.MAX_RECORDS_IN_ORDER_SERVICE)+1);
			// ./data/www.trustedhotel.com/p2pcashsystem/p2pcashsystem_#0001.ois
			fileName = dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",fileNo)+".ois";
			oisFile = (RandomAccessFile) hmAllFilesRW.get(fileName);
			if(oisFile==null) {				
				File newFile = new File(fileName);
				newFile.createNewFile();
				oisFile = new RandomAccessFile(fileName,"rw");  
				hmAllFilesRW.put(fileName,oisFile);			
				// close prevoius openedd file to prevent too many file opens fail... 
				if(fileNo!=1) {
					RandomAccessFile temp;
					fileName = dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",fileNo-1)+".ois";
					temp = (RandomAccessFile) hmAllFilesRW.get(fileName);
					if(temp!=null) {
						temp.close();
						hmAllFilesRW.remove(fileName);
					}
				}	
			}			
			/*
				ex	MAX_RECORDS_IN_ORDER_FILE = 100
				order_in_service = 99
					fileNo = 1
					recordOffset = 99*80
				order_in_service = 100
					fileNo = 2
					recordOfset = 0*80
				order_in_service = 101
					fileNo = 2
					recordOffset = 1*80	
			*/
			// 1.2 find offset it ois and write ois record
			int offset=0;			
			
			//recordOffset = (tosTransaction.order_in_service % ServiceOrder.MAX_RECORDS_IN_ORDER_SERVICE)*80;
			// offset Start from 0
			recordOffset = ((tosTransaction.order_in_service-1) % ServiceOrder.MAX_RECORDS_IN_ORDER_SERVICE)*ServiceOrder.RECORD_SIZE;
			
			
			byte[] baOrderRecord = new byte[80];	// primitive arrays default 0		
			System.arraycopy(ArrayUtil.longToBA(tosTransaction.order_in_service),0,baOrderRecord,offset,8);				
			offset+=8;

			System.arraycopy(ArrayUtil.shortToBA((short)tableName.length()),0,baOrderRecord,offset,2);
			offset+=2;
			System.arraycopy(tableName.getBytes(),0,baOrderRecord,offset,tableName.getBytes().length);
			
			System.arraycopy(ArrayUtil.longToBA(tosTransaction.order_in_table),0,baOrderRecord,72,8); // !! tablename size is fix 64bytes. so oit should be written at 72.
			oisFile.seek(recordOffset);
			oisFile.write(baOrderRecord);
			//log.trace("ois written OIS="+tosTransaction.order_in_service+" OFFSET="+recordOffset);	

			// 2. Save orider_in_table 	
			// 2.1 find the last record saved (previous) to find out to use which data file. 
			short dataFileNo=0;
			long  dataFileOffset=0;
			long  prevTableOrder=0;			
			if(tosTransaction.order_in_table==0) {				
				// in case of first message
				// Le't make oit & dat file
				dataFileNo=1; // file no started from 1
				if(makeFilesForNewTable(log, hmAllFilesRW, dataFolderPath, globalServiceName+"/"+tableName)==false) {
					Assert.assertTrue(true, "File Creation failed!");
					return false;
				}				
				fileName = dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",dataFileNo)+".tos";				
				lastDatFile = hmAllFilesRW.get(fileName);
				if(lastDatFile==null) {
					lastDatFile = new RandomAccessFile(fileName,"rw");
					hmAllFilesRW.put(fileName,lastDatFile);
				}
			} else {
				prevTableOrder = tosTransaction.order_in_table-1;	
				if(prevTableOrder==0) {
					dataFileNo=1;
					fileName = dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",dataFileNo)+".tos";
					lastDatFile = hmAllFilesRW.get(fileName);
					if(lastDatFile==null) {
						lastDatFile = new RandomAccessFile(fileName,"rw");
						hmAllFilesRW.put(fileName,lastDatFile);
					}
				} else {					
					fileNo = (short)((prevTableOrder / TableOrder.MAX_RECORDS_IN_ORDER_TABLE)+1);
					fileName = dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",fileNo)+".oit";
					oitFile = (RandomAccessFile) hmAllFilesRW.get(fileName);
					if(oitFile==null) {				
						File newFile = new File(fileName);
						newFile.createNewFile();
						oitFile = new RandomAccessFile(fileName,"rw");  
						hmAllFilesRW.put(fileName,oitFile);
						// close prevoius openedd file to prevent too many file opens fail... 
						if(fileNo!=1) {
							RandomAccessFile temp;
							fileName = dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",fileNo-1)+".oit";
							temp = (RandomAccessFile) hmAllFilesRW.get(fileName);
							if(temp!=null) {
								temp.close();
								hmAllFilesRW.remove(fileName);
							}
						}				
					}		
					recordOffset = ((prevTableOrder-1) % TableOrder.MAX_RECORDS_IN_ORDER_TABLE)*TableOrder.RECORD_SIZE;
					oitFile.seek(recordOffset);
					byte[] baOitRecord = new byte[14];
					oitFile.read(baOitRecord);			
					// 2.2 I got the last order and fileNo for data.				
					dataFileNo = (short)ArrayUtil.BAToShort(baOitRecord,8);
					fileName = dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",dataFileNo)+".tos";
					lastDatFile = hmAllFilesRW.get(fileName);
					if(lastDatFile==null) {
						lastDatFile = new RandomAccessFile(fileName,"rw");
						hmAllFilesRW.put(fileName,lastDatFile);
					}		
				}		
			}			
			
			int dataRecordSize = 8+8+2+tosTransaction.stamped_transaction.getBytes().length+2+tosTransaction.tosa_account.getBytes().length+2+tosTransaction.tosa_sign.getBytes().length;
			// 2.3 check whether new record over maxDataFileSize or not.
			dataFileOffset = lastDatFile.length();
			if((dataFileOffset+dataRecordSize)>maxDataFileSize) {
				// if over, you can use next file recordOffset 0.
				dataFileNo+=1;
				if(dataFileNo>99999) {
					log.error("Overed max File Numbers 99,999");
					return false;
				}
				dataFileOffset=0;				
				fileName = dataFolderPath+"/"+tosTransaction.gt_name+"_#"+String.format("%05d",dataFileNo)+".tos";				
				File newDatFile = new File(fileName);
				newDatFile.createNewFile();
				RandomAccessFile rnewDatFile = new RandomAccessFile(fileName,"rw");  
				hmAllFilesRW.put(fileName,rnewDatFile);
				// close prevoius openedd file to prevent too many file opens fail... 
				if(dataFileNo!=1) {
					RandomAccessFile temp;
					fileName = dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",dataFileNo-1)+".tos";
					temp = (RandomAccessFile) hmAllFilesRW.get(fileName);
					if(temp!=null) {
						temp.close();
						hmAllFilesRW.remove(fileName);					
					}
				}								
			}

			// 2.4 I got the data file where the new record added.
			// dataFileNo & dataFileOffset

			// 2.5 find oit file where the new reocrd added.
			fileNo = (short)((tosTransaction.order_in_table / TableOrder.MAX_RECORDS_IN_ORDER_TABLE)+1);
			fileName = dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",fileNo)+".oit";
			oitFile = (RandomAccessFile) hmAllFilesRW.get(fileName);
			if(oitFile==null) {						
				File newFile = new File(fileName);
				newFile.createNewFile();
				oitFile = new RandomAccessFile(fileName,"rw");
				hmAllFilesRW.put(fileName,oitFile);
				// close prevoius opened file to prevent too many file opens fail... 
				if(fileNo!=1) {
					RandomAccessFile temp;
					fileName = dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",fileNo-1)+".oit";
					temp = (RandomAccessFile) hmAllFilesRW.get(fileName);
					if(temp!=null) {
						temp.close();
						hmAllFilesRW.remove(fileName);
					}
				}					
			}
			
			recordOffset = (long)((tosTransaction.order_in_table-1) % TableOrder.MAX_RECORDS_IN_ORDER_TABLE)*TableOrder.RECORD_SIZE;
			//log.trace("tosTransaction.order_in_table="+tosTransaction.order_in_table+" recordOffset="+recordOffset);
			oitFile.seek(recordOffset);
			
			byte[] baTOrderRecord = new byte[14];	// primitive arrays default 0
			offset=0;		
			System.arraycopy(ArrayUtil.longToBA(tosTransaction.order_in_table),0,baTOrderRecord,offset,8);				
			offset+=8;
			System.arraycopy(ArrayUtil.shortToBA(dataFileNo),0,baTOrderRecord,offset,2);
			offset+=2;
			System.arraycopy(ArrayUtil.intToBA((int)dataFileOffset),0,baTOrderRecord,offset,4);
			oitFile.write(baTOrderRecord);
			//log.trace("oit written OIT="+tosTransaction.order_in_table+" OFFSET="+recordOffset);	

			// 3. Save Data (new Reocrd)
			fileName = dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",dataFileNo)+".tos";
			datFile = (RandomAccessFile) hmAllFilesRW.get(fileName);
			if(datFile==null) {
				File newFile = new File(fileName);
				newFile.createNewFile();
				datFile = new RandomAccessFile(fileName,"rw");
				hmAllFilesRW.put(fileName,datFile);	
				// close prevoius openedd file to prevent too many file opens fail... 
				if(dataFileNo!=1) {
					RandomAccessFile temp;
					fileName = dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",dataFileNo-1)+".tos";
					temp = (RandomAccessFile) hmAllFilesRW.get(fileName);
					if(temp!=null) {
						temp.close();
						hmAllFilesRW.remove(fileName);					
					}
				}		
			}
			datFile.seek(dataFileOffset);

			byte[] baDOrderRecord = new byte[dataRecordSize];
			
			offset=0;
			System.arraycopy(ArrayUtil.longToBA(tosTransaction.order_in_service),0,baDOrderRecord,offset,8);				
			offset+=8;
			System.arraycopy(ArrayUtil.longToBA(tosTransaction.order_in_table),0,baDOrderRecord,offset,8);				
			offset+=8;
			System.arraycopy(ArrayUtil.shortToBA((short)tosTransaction.stamped_transaction.getBytes().length),0,baDOrderRecord,offset,2);				
			offset+=2;
			System.arraycopy(tosTransaction.stamped_transaction.getBytes(),0,baDOrderRecord,offset,tosTransaction.stamped_transaction.getBytes().length);				
			offset+=tosTransaction.stamped_transaction.getBytes().length;
			System.arraycopy(ArrayUtil.shortToBA((short)tosTransaction.tosa_account.getBytes().length),0,baDOrderRecord,offset,2);				
			offset+=2;
			System.arraycopy(tosTransaction.tosa_account.getBytes(),0,baDOrderRecord,offset,tosTransaction.tosa_account.getBytes().length);				
			offset+=tosTransaction.tosa_account.getBytes().length;
			System.arraycopy(ArrayUtil.shortToBA((short)tosTransaction.tosa_sign.getBytes().length),0,baDOrderRecord,offset,2);				
			offset+=2;
			System.arraycopy(tosTransaction.tosa_sign.getBytes(),0,baDOrderRecord,offset,tosTransaction.tosa_sign.getBytes().length);				
			
			datFile.write(baDOrderRecord);		
			//log.trace("data written OFFSET="+dataFileOffset);	
			//oitFile.write(baTOrderRecord);
			//oisFile.write(baOrderRecord);	
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} 	
		return true;
	}
	

	public static boolean saveTranaction_old(Logger log, String dataFolderPath, int maxFileSizeMB, ConcurrentHashMap<String,RandomAccessFile> hmAllFilesRW, TOSTransaction tosTransaction) { 			
		String issuerName, serviceName, tableName;	
		String[] tokenArr = tosTransaction.gt_name.split("/");			
		issuerName = tokenArr[0];
		serviceName = tokenArr[1];
		tableName = tokenArr[2].trim();	
		String globalServiceName = issuerName+"/"+serviceName;
		RandomAccessFile oisFile=null,oitFile=null,lastDatFile=null,datFile=null;
		String 	fileName;
		short  	fileNo;		
		long 	recordOffset = 0;;
		long 	maxDataFileSize = (long)(maxFileSizeMB*1024*1024);
		
		Assert.assertTrue(tableName!=null,"Table name is null ="+tableName);

		try {
			// 1. Save order_in_service
			// 1.1 find ois file where the new record added
			fileNo = (short)((tosTransaction.order_in_service / ServiceOrder.MAX_RECORDS_IN_ORDER_SERVICE)+1);
			// ./data/www.trustedhotel.com/p2pcashsystem/p2pcashsystem_#0001.ois
			fileName = dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",fileNo)+".ois";
			oisFile = (RandomAccessFile) hmAllFilesRW.get(fileName);
			if(oisFile==null) {				
				File newFile = new File(fileName);
				newFile.createNewFile();
				oisFile = new RandomAccessFile(fileName,"rw");  
				hmAllFilesRW.put(fileName,oisFile);			
				// close prevoius openedd file to prevent too many file opens fail... 
				if(fileNo!=1) {
					RandomAccessFile temp;
					fileName = dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",fileNo-1)+".ois";
					temp = (RandomAccessFile) hmAllFilesRW.get(fileName);
					if(temp!=null) {
						temp.close();
						hmAllFilesRW.remove(fileName);
					}
				}	
			}			
			/*
				ex	MAX_RECORDS_IN_ORDER_FILE = 100
				order_in_service = 99
					fileNo = 1
					recordOffset = 99*80
				order_in_service = 100
					fileNo = 2
					recordOfset = 0*80
				order_in_service = 101
					fileNo = 2
					recordOffset = 1*80	
			*/
			// 1.2 find offset it ois and write ois record
			int offset=0;			
			
			//recordOffset = (tosTransaction.order_in_service % ServiceOrder.MAX_RECORDS_IN_ORDER_SERVICE)*80;
			// offset Start from 0
			recordOffset = ((tosTransaction.order_in_service-1) % ServiceOrder.MAX_RECORDS_IN_ORDER_SERVICE)*ServiceOrder.RECORD_SIZE;
			
			
			byte[] baOrderRecord = new byte[80];	// primitive arrays default 0		
			System.arraycopy(ArrayUtil.longToBA(tosTransaction.order_in_service),0,baOrderRecord,offset,8);				
			offset+=8;

			System.arraycopy(ArrayUtil.shortToBA((short)tableName.length()),0,baOrderRecord,offset,2);
			offset+=2;
			System.arraycopy(tableName.getBytes(),0,baOrderRecord,offset,tableName.getBytes().length);
			
			System.arraycopy(ArrayUtil.longToBA(tosTransaction.order_in_table),0,baOrderRecord,72,8); // !! tablename size is fix 64bytes. so oit should be written at 72.
			oisFile.seek(recordOffset);
			oisFile.write(baOrderRecord);

			// 2. Save orider_in_table 	
			// 2.1 find the last record saved (previous) to find out to use which data file. 
			short dataFileNo=0;
			long  dataFileOffset=0;
			long  prevTableOrder=0;			
			if(tosTransaction.order_in_table==0) {				
				// in case of first message
				// Le't make oit & dat file
				dataFileNo=1; // file no started from 1
				if(makeFilesForNewTable(log, hmAllFilesRW, dataFolderPath, globalServiceName+"/"+tableName)==false) {
					Assert.assertTrue(true, "File Creation failed!");
					return false;
				}				
				fileName = dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",dataFileNo)+".tos";				
				lastDatFile = hmAllFilesRW.get(fileName);
				if(lastDatFile==null) {
					lastDatFile = new RandomAccessFile(fileName,"rw");
					hmAllFilesRW.put(fileName,lastDatFile);
				}
			} else {
				prevTableOrder = tosTransaction.order_in_table-1;	
				if(prevTableOrder==0) {
					dataFileNo=1;
					fileName = dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",dataFileNo)+".tos";
					lastDatFile = hmAllFilesRW.get(fileName);
					if(lastDatFile==null) {
						lastDatFile = new RandomAccessFile(fileName,"rw");
						hmAllFilesRW.put(fileName,lastDatFile);
					}
				} else {					
					fileNo = (short)((prevTableOrder / TableOrder.MAX_RECORDS_IN_ORDER_TABLE)+1);
					fileName = dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",fileNo)+".oit";
					oitFile = (RandomAccessFile) hmAllFilesRW.get(fileName);
					if(oitFile==null) {				
						File newFile = new File(fileName);
						newFile.createNewFile();
						oitFile = new RandomAccessFile(fileName,"rw");  
						hmAllFilesRW.put(fileName,oitFile);
						// close prevoius openedd file to prevent too many file opens fail... 
						if(fileNo!=1) {
							RandomAccessFile temp;
							fileName = dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",fileNo-1)+".oit";
							temp = (RandomAccessFile) hmAllFilesRW.get(fileName);
							if(temp!=null) {
								temp.close();
								hmAllFilesRW.remove(fileName);
							}
						}				
					}		
					recordOffset = ((prevTableOrder-1) % TableOrder.MAX_RECORDS_IN_ORDER_TABLE)*TableOrder.RECORD_SIZE;
					oitFile.seek(recordOffset);
					byte[] baOitRecord = new byte[14];
					oitFile.read(baOitRecord);			
					// 2.2 I got the last order and fileNo for data.				
					dataFileNo = (short)ArrayUtil.BAToShort(baOitRecord,8);
					fileName = dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",dataFileNo)+".tos";
					lastDatFile = hmAllFilesRW.get(fileName);
					if(lastDatFile==null) {
						lastDatFile = new RandomAccessFile(fileName,"rw");
						hmAllFilesRW.put(fileName,lastDatFile);
					}		
				}		
			}			
			
			int dataRecordSize = 8+8+2+tosTransaction.stamped_transaction.getBytes().length+2+tosTransaction.tosa_account.getBytes().length+2+tosTransaction.tosa_sign.getBytes().length;
			// 2.3 check whether new record over maxDataFileSize or not.
			dataFileOffset = lastDatFile.length();
			if((dataFileOffset+dataRecordSize)>maxDataFileSize) {
				// if over, you can use next file recordOffset 0.
				dataFileNo+=1;
				if(dataFileNo>99999) {
					log.error("Overed max File Numbers 99,999");
					return false;
				}
				dataFileOffset=0;				
				fileName = dataFolderPath+"/"+tosTransaction.gt_name+"_#"+String.format("%05d",dataFileNo)+".tos";				
				File newDatFile = new File(fileName);
				newDatFile.createNewFile();
				RandomAccessFile rnewDatFile = new RandomAccessFile(fileName,"rw");  
				hmAllFilesRW.put(fileName,rnewDatFile);
				// close prevoius openedd file to prevent too many file opens fail... 
				if(dataFileNo!=1) {
					RandomAccessFile temp;
					fileName = dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",dataFileNo-1)+".tos";
					temp = (RandomAccessFile) hmAllFilesRW.get(fileName);
					if(temp!=null) {
						temp.close();
						hmAllFilesRW.remove(fileName);					
					}
				}								
			}

			// 2.4 I got the data file where the new record added.
			// dataFileNo & dataFileOffset

			// 2.5 find oit file where the new reocrd added.
			fileNo = (short)((tosTransaction.order_in_table / TableOrder.MAX_RECORDS_IN_ORDER_TABLE)+1);
			fileName = dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",fileNo)+".oit";
			oitFile = (RandomAccessFile) hmAllFilesRW.get(fileName);
			if(oitFile==null) {						
				File newFile = new File(fileName);
				newFile.createNewFile();
				oitFile = new RandomAccessFile(fileName,"rw");
				hmAllFilesRW.put(fileName,oitFile);
				// close prevoius opened file to prevent too many file opens fail... 
				if(fileNo!=1) {
					RandomAccessFile temp;
					fileName = dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",fileNo-1)+".oit";
					temp = (RandomAccessFile) hmAllFilesRW.get(fileName);
					if(temp!=null) {
						temp.close();
						hmAllFilesRW.remove(fileName);
					}
				}					
			}
			
			recordOffset = ((tosTransaction.order_in_table-1) % TableOrder.MAX_RECORDS_IN_ORDER_TABLE)*TableOrder.RECORD_SIZE;
			oitFile.seek(recordOffset);
			
			baOrderRecord = new byte[14];	// primitive arrays default 0
			offset=0;		
			System.arraycopy(ArrayUtil.longToBA(tosTransaction.order_in_table),0,baOrderRecord,offset,8);				
			offset+=8;
			System.arraycopy(ArrayUtil.shortToBA(dataFileNo),0,baOrderRecord,offset,2);
			offset+=2;
			System.arraycopy(ArrayUtil.intToBA((int)dataFileOffset),0,baOrderRecord,offset,4);
			oitFile.write(baOrderRecord);

			// 3. Save Data (new Reocrd)
			fileName = dataFolderPath+"/"+globalServiceName+"/"+tableName+"_#"+String.format("%05d",dataFileNo)+".tos";
			datFile = (RandomAccessFile) hmAllFilesRW.get(fileName);
			if(datFile==null) {
				File newFile = new File(fileName);
				newFile.createNewFile();
				datFile = new RandomAccessFile(fileName,"rw");
				hmAllFilesRW.put(fileName,datFile);	
				// close prevoius openedd file to prevent too many file opens fail... 
				if(dataFileNo!=1) {
					RandomAccessFile temp;
					fileName = dataFolderPath+"/"+globalServiceName+"/"+serviceName+"_#"+String.format("%05d",dataFileNo-1)+".tos";
					temp = (RandomAccessFile) hmAllFilesRW.get(fileName);
					if(temp!=null) {
						temp.close();
						hmAllFilesRW.remove(fileName);					
					}
				}		
			}
			datFile.seek(dataFileOffset);

			baOrderRecord = new byte[dataRecordSize];
			
			offset=0;
			System.arraycopy(ArrayUtil.longToBA(tosTransaction.order_in_service),0,baOrderRecord,offset,8);				
			offset+=8;
			System.arraycopy(ArrayUtil.longToBA(tosTransaction.order_in_table),0,baOrderRecord,offset,8);				
			offset+=8;
			System.arraycopy(ArrayUtil.shortToBA((short)tosTransaction.stamped_transaction.getBytes().length),0,baOrderRecord,offset,2);				
			offset+=2;
			System.arraycopy(tosTransaction.stamped_transaction.getBytes(),0,baOrderRecord,offset,tosTransaction.stamped_transaction.getBytes().length);				
			offset+=tosTransaction.stamped_transaction.getBytes().length;
			System.arraycopy(ArrayUtil.shortToBA((short)tosTransaction.tosa_account.getBytes().length),0,baOrderRecord,offset,2);				
			offset+=2;
			System.arraycopy(tosTransaction.tosa_account.getBytes(),0,baOrderRecord,offset,tosTransaction.tosa_account.getBytes().length);				
			offset+=tosTransaction.tosa_account.getBytes().length;
			System.arraycopy(ArrayUtil.shortToBA((short)tosTransaction.tosa_sign.getBytes().length),0,baOrderRecord,offset,2);				
			offset+=2;
			System.arraycopy(tosTransaction.tosa_sign.getBytes(),0,baOrderRecord,offset,tosTransaction.tosa_sign.getBytes().length);				
			datFile.write(baOrderRecord);		
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} 	
		return true;
	}

    /**
	* Save TOSTransactions 
	* @param	fullServiceName	
	* @param	tosTransactions TOSTransactions[] 		
	* @return	Error Code
	*/			
	public static boolean saveTransactions(Logger log, String dataFolderPath, int maxFileSizeMB, TOSTransaction[] tosTransactions) { 			
        ConcurrentHashMap<String,RandomAccessFile> hmAllFilesRW;    // Opend for RW !!!! Threads use it..
        boolean bReturn=false;
        hmAllFilesRW = new ConcurrentHashMap<String,RandomAccessFile>();      
        
		if(tosTransactions.length==0) return true;		   
		for(int i=0; i<tosTransactions.length; i++) {
			if(!saveTranaction(log, dataFolderPath, maxFileSizeMB, hmAllFilesRW, tosTransactions[i])) {
                bReturn=false;
                break;
            }         
		}			
        
        Set<String> key = hmAllFilesRW.keySet();        
        RandomAccessFile rFile;
        for(String fileName : key) {
            rFile = hmAllFilesRW.get(fileName);
            if(rFile!=null) {
                try {
                    rFile.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    bReturn=false;
                }
            }
        }
		return bReturn;
	}
	

	/**
	* FullServiceName
	* @param	fullServiceName
	* @return true - ,  false - 
	*/   					
	public static boolean makeFolders(String folderPath, String globalServiceName) {		
		try {
			File file = new File(folderPath+"/"+globalServiceName);
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
	* @param	fullServiceName
	* @return	true , false	
	*/ 		
	public static boolean makeInitialFiles(Logger log, ConcurrentHashMap<String,RandomAccessFile> hmAllFiles, String folderPath, String fullServiceName, Connection dbCon) {
		String issuer,service,ledger;
		String[] tokenArr = fullServiceName.split("/");
		boolean retValue=true;
		File oisFile=null, oitFile=null, datFile=null;
		RandomAccessFile roisFile, roitFile, rdatFile;				
		if(tokenArr.length!=3) {
			
			return false;
		}	
		issuer = tokenArr[0];
		service = tokenArr[1];
		ledger = tokenArr[2];
		String fileName;
		try {
			fileName = folderPath+"/"+issuer+"/"+service+"/"+service+"_#00001.ois";
			oisFile = new File(fileName);
			oisFile.createNewFile();
			roisFile = new RandomAccessFile(fileName,"rw");
						
			fileName = folderPath+"/"+issuer+"/"+service+"/"+ledger+"_#00001.oit";
			oitFile = new File(fileName);
			oitFile.createNewFile();
			roitFile = new RandomAccessFile(fileName,"rw");
			
			fileName = folderPath+"/"+issuer+"/"+service+"/"+ledger+"_#00001.tos";
			datFile = new File(fileName);
			datFile.createNewFile();
			rdatFile = new RandomAccessFile(fileName,"rw");
						
			// !! Don't close random access file !!!!
            if(hmAllFiles!=null) {
                hmAllFiles.put(folderPath+"/"+issuer+"/"+service+"/"+service+"_#00001.ois",roisFile);
                hmAllFiles.put(folderPath+"/"+issuer+"/"+service+"/"+ledger+"_#00001.oit",roitFile);
				hmAllFiles.put(folderPath+"/"+issuer+"/"+service+"/"+ledger+"_#00001.tos",rdatFile);
            }
			
			int ret = TrustSQLManager.createSQLResultTable(log, dbCon,ledger);				
			if(ret!=0) return false;
			
		} catch (Exception e) {
			e.printStackTrace();
			retValue=false;
		} 
		
		return retValue;
	}

	/**
	* makeFilesForNewTable it called by TTOBServer 
	* @param	fullServiceName
	* @return	true , false	
	*/ 		
	public static boolean makeFilesForNewTable(Logger log, ConcurrentHashMap<String,RandomAccessFile> hmAllFiles, String folderPath, String gt_name) {
		boolean retValue=true;
		File oitFile, datFile;
		RandomAccessFile roitFile, rdatFile;		
		String[] tokenArr = gt_name.split("/");
		String table = tokenArr[2];
		try {
			oitFile = new File(folderPath+"/"+gt_name+"_#00001.oit");
			oitFile.createNewFile();
			roitFile = new RandomAccessFile(folderPath+"/"+gt_name+"_#00001.oit","rw");
			datFile = new File(folderPath+"/"+gt_name+"_#00001.tos");
			datFile.createNewFile();
			rdatFile = new RandomAccessFile(folderPath+"/"+gt_name+"_#00001.tos","rw");
						
			// !! Don't close random access file !!!!
            if(hmAllFiles!=null) {
                hmAllFiles.put(folderPath+"/"+gt_name+"_#00001.oit",roitFile);
                hmAllFiles.put(folderPath+"/"+gt_name+"_#00001.tos",rdatFile);
            }
		} catch (Exception e) {
			e.printStackTrace();
			retValue=false;
		} 
		
		return retValue;
	}

	/**
	* makeFilesForNewTable it called by DNOMServer 
	* @param	fullServiceName
	* @return	true , false	
	*/ 		
	public static boolean makeFilesForNewTable(Logger log, ConcurrentHashMap<String,RandomAccessFile> hmAllFiles, String folderPath, String gt_name, Connection dbCon) {
		boolean retValue=true;
		File oitFile, datFile;
		RandomAccessFile roitFile, rdatFile;		
		String[] tokenArr = gt_name.split("/");
		String table = tokenArr[2];
		Assert.assertTrue(table!=null,"TableName is Null");
		try {
			oitFile = new File(folderPath+"/"+gt_name+"_#00001.oit");			
			if(oitFile.exists()==false) oitFile.createNewFile();
			roitFile = new RandomAccessFile(folderPath+"/"+gt_name+"_#00001.oit","rw");

			datFile = new File(folderPath+"/"+gt_name+"_#00001.tos");
			if(datFile.exists()==false) datFile.createNewFile();
			rdatFile = new RandomAccessFile(folderPath+"/"+gt_name+"_#00001.tos","rw");
						
			// !! Don't close random access file !!!!
            if(hmAllFiles!=null) {
                hmAllFiles.put(folderPath+"/"+gt_name+"_#00001.oit",roitFile);
                hmAllFiles.put(folderPath+"/"+gt_name+"_#00001.tos",rdatFile);
            }
			
			int ret = TrustSQLManager.createSQLResultTable(log, dbCon,table);				
			if(ret!=0) return false;
			
		} catch (Exception e) {
			e.printStackTrace();
			retValue=false;
		} 
		
		return retValue;
	}


}
