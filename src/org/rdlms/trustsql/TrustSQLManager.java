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

package org.rdlms.trustsql;

import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.rdlms.messagestorage.model.TOSTransaction;
import org.rdlms.util.ArrayUtil;
import org.rdlms.util.Assert;

public class TrustSQLManager {
	
	/**
	* Batch Insert기능을 수행한다.
	* TOSTRansaction.gt_name이 같아야 Batch가 동작하는 것이다. 거의 같음. 
	* 만일 다르다면 .Batch Insert 단건이지 머.
	* @param	dbConnection, table name, gid, sql error...
	* @return	
	*/
	public static int executeBatchStatement(Logger log, ConcurrentHashMap<String, Integer> hmAllErrorCodes, Connection dbCon, TOSTransaction[] tosTransactions, boolean dup_ignore) {
        PreparedStatement pstmt = null; 
		ResultSet rs = null;
        int sqlErrorCode;
        String sqlState=null;
        String sqlMsg=null;
		System.out.println("TRSUTSQL [BATCH EXECUTION] --------------------");
		// INSERT INTO TRANSACTIONS_mytoken (TORDER_NUM,TORDER_NONCE,SENDER,AMOUNT,RECEIVER,SENDER_SIGN,TORDER_TIME,TORDER_SIGN) values (a,b,c,d);";
		// BATCH INSERT
		// INSERT INTO TRANSACTIONS_mytoken (TORDER_NUM,TORDER_NONCE,SENDER,AMOUNT,RECEIVER,SENDER_SIGN,TORDER_TIME,TORDER_SIGN) values (a,b,c,d),(a1,b1,c1,d1);";
		
		//TODO Create문은 처리 하지 않는다. 오케? //대소문자 values VaLueS 이거 처리 해야 함. 

		String 	gt_name="";
		String 	fullSql;		
		String  sqlParts[];
		String  sqlBody=null;		
		StringBuffer valueBuffer = new StringBuffer();		
		int		checkPoint;
		/*
		아.. 쉽지 않네.. 이거.. 잘 생각해야 하네..

		A table 100개 나오고
		B table 50개 나왔는데, B에서 에러 나왔어.
		그럼 B부터 해야 하잖아. 하나씩.. 이논리를 만들어야 함.  check Point 개념으로.. 
		*/
		checkPoint=0;
		try{
			for(int i=0; i<tosTransactions.length; i++) {
				if(gt_name.equals(tosTransactions[i].gt_name)) {
					fullSql = tosTransactions[i].stamped_transaction;
					System.out.println("GT_NAME="+tosTransactions[i].gt_name+" transaction="+fullSql);
					fullSql = fullSql.replaceAll(" values"," VALUES");
					sqlParts = fullSql.split(" VALUES");
					// TODO 일단 지금은 DDL은 안되는 걸로 만들어 놓자. 
					Assert.assertTrue(sqlParts.length==2, "MULTI MESSAGE - DDL? "+"New GT_NAME="+tosTransactions[i].gt_name+" transaction="+fullSql);
					sqlParts[1] = sqlParts[1].replaceAll(";","");
					valueBuffer.append(sqlParts[1]);
					System.out.println(" append = "+sqlParts[1]);
				} else {
					if(sqlBody!=null) {
						// Table이 변경되었다. 그리고 이전에 SQL문이 있었다면 SQL문을 INSERT보내자.					
						log.info("NEW.T (OIS="+tosTransactions[i].order_in_service+",OIT="+tosTransactions[i].order_in_table+")");
						pstmt = dbCon.prepareStatement(sqlBody+valueBuffer.toString());
						rs = pstmt.executeQuery();		
						log.info("success");
						sqlErrorCode=0;
						checkPoint = i; // CheckPoint이다. 
					}

					// New gt name.
					sqlBody=null;
					valueBuffer.delete(0, valueBuffer.length());
					fullSql = tosTransactions[i].stamped_transaction;
					System.out.println("New GT_NAME="+tosTransactions[i].gt_name+" transaction="+fullSql);
					fullSql = fullSql.replaceAll(" values"," VALUES");
					
					sqlParts = fullSql.split(" VALUES");
					// TODO 일단 지금은 DDL은 안되는 걸로 만들어 놓자. 
					Assert.assertTrue(sqlParts.length==2, "MULTI MESSAGE - DDL? "+"New GT_NAME="+tosTransactions[i].gt_name+" transaction="+fullSql);
					sqlParts[1] = sqlParts[1].replaceAll(";","");

					sqlBody = sqlParts[0] + " VALUES ";
					valueBuffer.append(sqlParts[1]);
				}
				// 헛.. 이것도 해줘야 하네..
				if(hmAllErrorCodes!=null) {
					synchronized(hmAllErrorCodes) {					
						hmAllErrorCodes.put(tosTransactions[i].gt_name+"/"+tosTransactions[i].order_in_table,0);						
					}	
				}
			}		
		} catch(SQLException sqlE) {
            //sqlE.printStackTrace();
            sqlErrorCode = sqlE.getErrorCode();
            sqlState = sqlE.getSQLState();
            sqlMsg = sqlE.getMessage();
			log.error("------------------------------------------------------------------------------------------");
			log.error("------------------------------------------------------------------------------------------");
			return checkPoint;
        } catch(Exception e1) {
            //e1.printStackTrace();
			log.error("------------------------------------------------------------------------------------------");
			log.error("------------------------------------------------------------------------------------------");						
            return checkPoint;
        }				
        return 0;
    }


    /**
	* insert SQL Error .
	* @param	dbConnection, table name, gid, sql error...
	* @return	
	*/
	public static int executeStatement(Logger log, ConcurrentHashMap<String, Integer> hmAllErrorCodes, Connection dbCon, TOSTransaction tosTransaction, boolean dup_ignore) {
        PreparedStatement pstmt = null; 
		ResultSet rs = null;
        int sqlErrorCode;
        String sqlState=null;
        String sqlMsg=null;
		//System.out.println("TRSUTSQL [EXECUTION] --------------------");
		//System.out.println(tosTransaction.toString());

        try{
			log.info("NEW.T (OIS="+tosTransaction.order_in_service+",OIT="+tosTransaction.order_in_table+")");
            pstmt = dbCon.prepareStatement(tosTransaction.stamped_transaction);
            rs = pstmt.executeQuery();		
			log.info("success");
            sqlErrorCode=0;
        } catch(SQLException sqlE) {
            //sqlE.printStackTrace();
            sqlErrorCode = sqlE.getErrorCode();
            sqlState = sqlE.getSQLState();
            sqlMsg = sqlE.getMessage();
			log.error("------------------------------------------------------------------------------------------");
			log.trace("["+tosTransaction.gt_name+"] SERVICE ORDER = "+tosTransaction.order_in_service);									
			log.trace("["+tosTransaction.gt_name+"] TABLE   ORDER = "+tosTransaction.order_in_table);									
			log.trace("["+tosTransaction.gt_name+"] SQL STATEMENT = "+tosTransaction.stamped_transaction);									
			log.error("["+tosTransaction.gt_name+"] SQL Error : "+sqlErrorCode);
            log.error("["+tosTransaction.gt_name+"] SQL State : "+sqlE.getSQLState());		
			log.error("["+tosTransaction.gt_name+"] SQL Message : "+sqlE.getMessage());
            log.error("["+tosTransaction.gt_name+"] RDLMS INSERT FAIL ! OIS="+tosTransaction.order_in_service+" OIT="+tosTransaction.order_in_table);            
			log.error("------------------------------------------------------------------------------------------");
        } catch(Exception e1) {
            //e1.printStackTrace();
			log.error("------------------------------------------------------------------------------------------");
			log.trace("["+tosTransaction.gt_name+"] SERVICE ORDER = "+tosTransaction.order_in_service);									
			log.trace("["+tosTransaction.gt_name+"] TABLE   ORDER = "+tosTransaction.order_in_table);									
            log.trace("["+tosTransaction.gt_name+"] SQL STATEMENT = "+tosTransaction.stamped_transaction);			
			log.error("["+tosTransaction.gt_name+"] RUNTIME EXCEPTION : "+e1.toString());
            log.error("["+tosTransaction.gt_name+"] RDLMS INSERT FAIL ! OIS="+tosTransaction.order_in_service+" OIT="+tosTransaction.order_in_table);            
			log.error("------------------------------------------------------------------------------------------");						
            return 777;		
        }	
        //log.debug("["+fullServiceName+"] TT-T7");				
        if(sqlErrorCode!=0) {
            int eret=0;
            String[] tokenArr = tosTransaction.gt_name.split("/");
			//System.out.println("XXXXXXXXXXXXXXXX "+tosTransaction.gt_name);
            eret = TrustSQLManager.insertSQLResult(log,dbCon,tokenArr[2],tosTransaction.order_in_table, sqlErrorCode, sqlState, sqlMsg);
            if(eret==1146) { // 1146 means result table is not exist. Let's make a table.
				int eret2=0;
				eret2 = TrustSQLManager.createSQLResultTable(log, dbCon,tokenArr[2]);
				if(eret2!=0) {
					log.error("Cant't create Database Table  error="+eret2);
					return 888;
				}
				eret = TrustSQLManager.insertSQLResult(log,dbCon,tokenArr[2],tosTransaction.order_in_table, sqlErrorCode, sqlState, sqlMsg);
				if(eret!=0) {
					log.error("Cant't insert error code to error_table error="+eret);
					return 999;
				}
			}	
        }
        if(hmAllErrorCodes!=null) {
            synchronized(hmAllErrorCodes) {					
                hmAllErrorCodes.put(tosTransaction.gt_name+"/"+tosTransaction.order_in_table,new Integer(sqlErrorCode));						
            }
        }		
		//System.out.println("END-------------------TRSUTSQL [EXECUTION]");
        return 0;
    }


    /**
	* insert SQL Error .
	* @param	dbConnection, table name, gid, sql error...
	* @return	
	*/
	public static int insertSQLResult(Logger log, Connection dbCon, String ledger, long gid, int sqlErrorCode, String sqlState, String sqlMsg) {
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt=null;
		int errorCode=-1;				
		try{
			if(sqlErrorCode==0) {
				strStmt= "INSERT INTO "+ledger+"_error (GID,SQL_CODE) VALUES ";
				strStmt += "("+gid+","+sqlErrorCode+");";
			} else {
				if(sqlMsg.length()>512) {
					sqlMsg= sqlMsg.substring(0,512);				}
				strStmt= "INSERT INTO "+ledger+"_error (GID,SQL_CODE, SQL_STATE, SQL_MSG) VALUES ";
				strStmt += "("+gid+","+sqlErrorCode+",'"+sqlState+"',\""+sqlMsg+"\")";
			}							
			log.trace("SQLRESULT INSERT\n"+strStmt);
			pstmt = dbCon.prepareStatement(strStmt);
			rs = pstmt.executeQuery();		
			errorCode=0;
		} catch(SQLException sqlE) {
			//sqlE.printStackTrace();
			errorCode= sqlE.getErrorCode();
			log.error("------------------------------------------------------------------------------------------");
			log.error("SQL Error : "+errorCode);
            log.error("SQL State : "+sqlE.getSQLState());		
			log.error("SQL Message : "+sqlE.getMessage());            
            log.trace("SQL STATEMENT = "+strStmt);
			log.error("------------------------------------------------------------------------------------------");

		} catch(Exception e1) {			
			log.error("ERROR Table Creation Failed!");			
			return 1;		
		}	
		return errorCode;
	}

    	
	/**
	* CREATE SQL TABLE (XXX_error) to record SQL_ERROR
	* @param	dbConnection, table name
	* @return	
	*/
	public static int createSQLResultTable(Logger log, Connection dbCon, String tableName) {
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt=null;
		int sqlErrorCode=-1;				
		try{
			strStmt= "CREATE TABLE IF NOT EXISTS "+tableName+"_error ( \n";	
			strStmt += "GID BIGINT UNSIGNED, \n";
			strStmt += "SQL_CODE INT NOT NULL, \n";	
			strStmt += "SQL_STATE CHAR(5), \n";							
			strStmt += "SQL_MSG VARCHAR(512), \n";		
			strStmt += "REG_TIME TIMESTAMP NOT NULL default CURRENT_TIMESTAMP, \n";							
			strStmt += "CONSTRAINT PRIMARY KEY(GID) \n";
			strStmt += ") ENGINE=InnoDB \n";	 
		//	log.trace(strStmt);
			pstmt = dbCon.prepareStatement(strStmt);
			rs = pstmt.executeQuery();		
			sqlErrorCode=0;
		} catch(SQLException sqlE) {
			//sqlE.printStackTrace();
			sqlErrorCode = sqlE.getErrorCode();			
			log.error("SQLERRORCODE="+sqlErrorCode);
			log.error("SQLSTATE="+sqlE.getSQLState());
			log.error("SQLMSG="+sqlE.getMessage());
		} catch(Exception e1) {
			//e1.printStackTrace();
			log.error("ERROR Table Creation Failed!");			
			return 777;		
		}	
		return sqlErrorCode;
	}
	
	

    /**
	* public ConcurrentHashMap<String, Long> createTableIfNotExist(Connection pcon)
	*/	
	public static ConcurrentHashMap<String,Long> createTableIfNotExist(Logger log, Connection pcon, String folderPath, String[] fullServiceNames) {
		ConcurrentHashMap<String,Long> hmStartTransactionOrders = new ConcurrentHashMap<String,Long>();
		ConcurrentHashMap<String,Long> hmCreatedTables = new ConcurrentHashMap<String,Long>();				
		Connection con = pcon;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;

		String queryStr;
		 
		RandomAccessFile datFile=null;
		RandomAccessFile idxFile=null;

		if(fullServiceNames.length==0) {
			return null;		
		}	
		
		int notExistTableCounter=fullServiceNames.length; 	
		int existTableCounter=0;
		while(notExistTableCounter!=existTableCounter) {		
			for(int i=0; i<fullServiceNames.length; i++) { 										
				try {
					String[] tokens = fullServiceNames[i].split("/");
					String tableName = tokens[2];
			
					if(hmStartTransactionOrders.containsKey(tableName)!=true) {				
						idxFile = new RandomAccessFile(folderPath+"/"+fullServiceNames[i]+".idx","r");						
						byte[] idxRecord = new byte[14];
						byte[] baOrderNo = new byte[8];
						byte[] baFileNo = new byte[2];
						byte[] baOffset = new byte[4];
						long orderNo;
						short fileNo;
						int offset;
						int idxRecordCount;				
						
						byte[] baDatOrderNo = new byte[8];
						byte[] baPayloadLen = new byte[2];
						long datOrderNo=0;
						short payloadLen=0;					
						
						idxRecordCount = (int)(idxFile.length()/14);
					
						datFile = null;
						for(int j=0; j<idxRecordCount; j++) { 		
							idxFile.seek((j)*14);
							idxFile.read(idxRecord);
							
							System.arraycopy(idxRecord,0,baOrderNo,0,8);
							System.arraycopy(idxRecord,8,baFileNo,0,2);
							System.arraycopy(idxRecord,10,baOffset,0,4);
							
							orderNo = ArrayUtil.BAToLong(baOrderNo);
							fileNo= (short)ArrayUtil.BAToShort(baFileNo);
							offset= ArrayUtil.BAToInt(baOffset);
							if(datFile==null) {
								datFile = new RandomAccessFile(folderPath+"/"+fullServiceNames[i]+"_#"+String.format("%05d",fileNo)+".tos","rw");
							}
							datFile.seek(offset);
							datFile.read(baDatOrderNo);
							datFile.read(baPayloadLen);					
							datOrderNo = ArrayUtil.BAToLong(baDatOrderNo);
													
							if(datOrderNo!=orderNo) {
									return null;
							}
																
							payloadLen = (short)ArrayUtil.BAToShort(baPayloadLen);													
							byte[] baPayload = new byte[payloadLen];
							datFile.read(baPayload);
							String payloadStr = new String(baPayload);
							String first6 = payloadStr.substring(0,6);
							first6 = first6.toUpperCase();						
							if(first6.equals("CREATE")) {
								hmStartTransactionOrders.put(fullServiceNames[i], new Long(datOrderNo));
							} else {
							//	System.out.println("NO CREATE !!!");
								break;
							}											
						}
						datFile.close();
						datFile = null;
					}
					
					if(hmCreatedTables.containsKey(fullServiceNames[i])==true) {
						continue;
					}
	
					queryStr = "SELECT 1 FROM Information_schema.tables WHERE table_schema = '"+tokens[1]+"' and table_name = '"+tableName+"'";			
					int errorCode=0;
					int tableYesNo=0;
					try {
						pstmt = con.prepareStatement(queryStr);
						rs = pstmt.executeQuery();				
						while(rs.next()) {
							tableYesNo = rs.getInt("1");
						}		
					} catch(SQLException sqlE) {		
						errorCode = sqlE.getErrorCode();		
					}	
						
					if(tableYesNo==0) {		
						idxFile = new RandomAccessFile(folderPath+"/"+fullServiceNames[i]+".idx","r");	
											
						byte[] idxRecord = new byte[14];
						byte[] baOrderNo = new byte[8];
						byte[] baFileNo = new byte[2];
						byte[] baOffset = new byte[4];
						long orderNo;
						short fileNo;
						int offset;
						int idxRecordCount;				
							
						int nowFileNo=0;								
						byte[] baDatOrderNo = new byte[8];
						byte[] baPayloadLen = new byte[2];
						long datOrderNo=0;
						short payloadLen=0;					
						int sqlErrorCode=0;		
						String sqlState=null;
						String sqlMsg=null;		
						
						idxRecordCount = (int)(idxFile.length()/14);
						
						datFile = new RandomAccessFile(folderPath+"/"+fullServiceNames[i]+"_#"+String.format("%05d",1)+".tos","r");
						for(int j=0; j<idxRecordCount; j++) { 
							sqlErrorCode=0;
							try {
								idxFile.seek((j)*14);
								idxFile.read(idxRecord);
								
								System.arraycopy(idxRecord,0,baOrderNo,0,8);
								System.arraycopy(idxRecord,8,baFileNo,0,2);
								System.arraycopy(idxRecord,10,baOffset,0,4);
								
								orderNo = ArrayUtil.BAToLong(baOrderNo);
								fileNo= (short)ArrayUtil.BAToShort(baFileNo);
								offset= ArrayUtil.BAToInt(baOffset);
																						
								datFile.seek(offset);
								datFile.read(baDatOrderNo);
								datFile.read(baPayloadLen);					
								datOrderNo = ArrayUtil.BAToLong(baDatOrderNo);
														
								if(datOrderNo!=orderNo) {						
										return null;
								}
																	
								payloadLen = (short)ArrayUtil.BAToShort(baPayloadLen);													
								byte[] baPayload = new byte[payloadLen];
								datFile.read(baPayload);
								String payloadStr = new String(baPayload);
								//System.out.println("\n PAYLOAD ["+j+"] = "+payloadStr);
								
								String first6 = payloadStr.substring(0,6);
								first6 = first6.toUpperCase();						
								if(first6.equals("CREATE")) {
									sqlErrorCode=0;
									try {								
										queryStr = new String(baPayload);
									//System.out.println("#### "+queryStr);
										pstmt = con.prepareStatement(queryStr);	
										rs = pstmt.executeQuery();
									} catch(SQLException sqlE) {
										sqlErrorCode = sqlE.getErrorCode();																		
										sqlState = sqlE.getSQLState();
										sqlMsg = sqlE.getMessage();
										log.error("["+fullServiceNames[i]+"] RUNTIME EXCEPTION : ",sqlE);
										log.error("["+fullServiceNames[i]+"] SQL State : "+sqlE.getSQLState());		
										log.error("["+fullServiceNames[i]+"] RDLMS INSERT FAIL !TOSID="+datOrderNo);
										log.error("["+fullServiceNames[i]+"] SQL STATEMENT = "+queryStr);									
									}
									if(sqlErrorCode!=0) {
										int eret=0;
										eret = insertSQLResult(log, con,tableName,datOrderNo, sqlErrorCode, sqlState, sqlMsg);
									}
									if(sqlErrorCode==0) hmCreatedTables.put(fullServiceNames[i],new Long(0)); //
									if(sqlErrorCode==1005) {										
										break;
									}							
									//System.out.println("!!!!!!!!!!! SQL ERROR CODE ="+sqlErrorCode);
								} else {	
									//System.out.println("!!!!!!!!!!! It's not Create Query="+queryStr);								
									break;
								}							
							} catch(Exception e) {
								log.error(e.toString());	
								//e.printStackTrace();
							}							
						}
						if(datFile!=null) datFile.close();
						if(sqlErrorCode==0) {							
							existTableCounter++;
						}
					} else if((errorCode==0)||(errorCode==1054)){						
						existTableCounter++;
					}
				} catch(Exception e) {
					log.error(e.toString());	
					//e.printStackTrace();
				}	
			} // for		
		} // While
		return hmStartTransactionOrders;
	}

	public static HashMap<String, String> selectAllTableNames(Logger log, Connection dbCon, String serviceName) {
		HashMap<String, String> map = new HashMap<String, String>();
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		int rowCount;
		try { 			
			strStmt = "SELECT TABLE_NAME, ORDER_COLUMN FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='"+serviceName+"' AND TABLE_NAME NOT LIKE '%error' AND TRUSTED_TYPE=2;";
			pstmt = dbCon.prepareStatement(strStmt);
			log.trace(strStmt);
			rs = pstmt.executeQuery();						
			rs.last();
			rowCount= rs.getRow();
			//System.out.println("rowCount  "+rowCount);
			if(rowCount==0) {
				return null;
			}			
			rs.beforeFirst();			
			while(rs.next()) {
				map.put(rs.getString("TABLE_NAME"),rs.getString("ORDER_COLUMN"));
				log.trace("serviceName="+serviceName+"  TABLE="+rs.getString("TABLE_NAME")+"   ORDER_COLUMN="+rs.getString("ORDER_COLUMN"));
			}						
		} catch (Exception e) {
			log.error(e.toString());
			//e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close();
			} catch (SQLException e) {
				log.error(e.toString());
				//e.printStackTrace();
			}
		}
		return map;
	}

	public static String[] selectAllTriggerNames(Logger log, Connection dbCon, String serviceName) {
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		int rowCount;
		String triggerNames[]=null;
		
		try { 			
			strStmt = "SELECT TRIGGER_NAME FROM INFORMATION_SCHEMA.TRIGGERS WHERE TRIGGER_SCHEMA='"+serviceName+"';";
			pstmt = dbCon.prepareStatement(strStmt);
			log.trace(strStmt);
			rs = pstmt.executeQuery();						
			rs.last();
			rowCount= rs.getRow();
			if(rowCount==0) {
				return null;
			}
			triggerNames = new String[rowCount];
			rs.beforeFirst();
			int counter=0;
			while(rs.next()) {
				triggerNames[counter]= rs.getString("TRIGGER_NAME");
				counter++;
			}			
		} catch (Exception e) {
			log.error(e.toString());
			//e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close();
			} catch (SQLException e) {
				log.error(e.toString());
				//e.printStackTrace();
			}
		}
		return triggerNames;
	}

	public static int[] selectTableRows(Logger log, Connection dbCon, String serviceName, HashMap<String, String> map) {
		int tableRows[] = new int[map.size()];		
		int counter=0;
		Iterator<String> keys = map.keySet().iterator();
		while(keys.hasNext() ){
			String key = keys.next();
			String value = map.get(key);
			
			String strStmt;
			PreparedStatement pstmt = null; 
			ResultSet rs = null;

			try { 
				int left=0, right=0;
				strStmt = "SELECT COUNT(A."+value+") FROM "+serviceName+"."+key+" A ";
				strStmt+= "LEFT JOIN "+serviceName+"."+key+"_error B ON A."+value+" = B.GID WHERE B.GID is NULL; ";
				pstmt = dbCon.prepareStatement(strStmt);
				log.trace(strStmt);
				rs = pstmt.executeQuery();
				//log.trace("strStmt="+strStmt);
				while(rs.next()) {
					left = rs.getInt(1);
				}				
				//log.trace("left="+left);
				pstmt.close();
				rs.close();
				
				strStmt = "SELECT COUNT(B.GID) FROM "+serviceName+"."+key+" A ";
				strStmt+= "RIGHT JOIN "+serviceName+"."+key+"_error B ON A."+value+" = B.GID WHERE A."+value+" is NULL;";				
				pstmt = dbCon.prepareStatement(strStmt);	
				log.trace(strStmt);			
				rs = pstmt.executeQuery();
				//log.trace("strStmt="+strStmt);
				while(rs.next()) {
					right = rs.getInt(1);
				}				
				//log.trace("right="+right);
				tableRows[counter] = left+right;
				counter++;				
			} catch (Exception e) {
				log.error(e.toString());
			} finally {
				try {
					if(rs != null) rs.close(); 
					if(pstmt != null) pstmt.close();
				} catch (SQLException e) {
					log.error(e.toString());
				}
			}
		};		
		return tableRows;
	}
	
	/**
	* Table과 Table_error에 PK DUP레코드가 있다면 지워준다. 이중 에러 처리된 것이니까..
	* @param	log, dbConnection, serviceName, map(table_name,orderColumn)
	* @return	
	*/
	public static int clearDupResultTable(Logger log, Connection dbCon, String serviceName, HashMap<String, String> map) {
		int retCode=0;
		Iterator<String> keys = map.keySet().iterator();
		while(keys.hasNext() ){
			String key = keys.next();
			String value = map.get(key);
			
			String strStmt;
			PreparedStatement pstmt = null; 
			ResultSet rs = null;

			try {
				strStmt = "DELETE A.* FROM "+serviceName+"."+key+"_error A ";
				strStmt+= "INNER JOIN "+serviceName+"."+key+" B ";
				strStmt+= "ON A.GID = B."+value+" ";
				log.trace(strStmt);
				pstmt = dbCon.prepareStatement(strStmt);
				rs = pstmt.executeQuery();
				pstmt.close();
				rs.close();				
			} catch (Exception e) {
				log.error(e.toString());
				retCode=-1;
			} finally {
				try {
					if(rs != null) rs.close(); 
					if(pstmt != null) pstmt.close();
				} catch (SQLException e) {
					log.error(e.toString());
				}
			}
		};		
		return retCode;
	}

}
