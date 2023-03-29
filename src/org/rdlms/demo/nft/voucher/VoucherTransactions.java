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

package org.rdlms.demo.nft.voucher;


import java.security.*;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.rdlms.crypto.ECDSA;
import org.rdlms.dnom.connector.DNOMConnector;
import org.rdlms.util.ArrayUtil;
import org.rdlms.wallet.WalletManager;

import java.util.HashMap;
import java.util.Properties;

public class VoucherTransactions {
	static String strDNOMAddress=null;
	static String strDNOMPort=null;
	static String strIssuer=null;
	static String strService=null;

	static String DBURL=null;
	static String DBUSR=null;
	static String DBUSRPW=null;
	
	static String ISSSUER_NAME=null;
	static String SERVICE_MEMBER_MANAGEMENT=null;
	static String SERVICE_VOUCHER_MANAGEMENT=null;
	
	public static HashMap<String, Object> SearchVoucherByPOID(String voucher_name, String id) {
		HashMap<String, Object> map = new HashMap<String, Object>();
		
		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;
		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		try { 
			// SEND_TOTAL		
			strStmt = "SELECT A.* FROM VOUCHER_"+voucher_name+" A, PO_"+voucher_name+" B "; 
			strStmt+= "WHERE A.VOUCHER_GID=B.VOUCHER_GID AND B.PO_GID='"+id+"'";

			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();		
			while(rs.next()) {
				map.put("VOUCHER_GID",rs.getLong("VOUCHER_GID"));
				map.put("VOUCHER_NONCE",rs.getString("VOUCHER_NONCE"));
				map.put("VOUCHER_NAME",rs.getString("VOUCHER_NAME"));
				map.put("VOUCHER_NO",rs.getString("VOUCHER_NO"));
				map.put("VOUCHER_PVALUE",rs.getLong("VOUCHER_PVALUE"));
				map.put("ISSUER_SIGN",rs.getString("ISSUER_SIGN"));
				map.put("TOSA_TIME",rs.getString("TOSA_TIME"));
				map.put("TOSA_SIGN",rs.getString("TOSA_SIGN"));								
			}			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return map;
	}

	public static HashMap<String, Object> SearchVoucherByPaymentID(String voucher_name, String id) {
		HashMap<String, Object> map = new HashMap<String, Object>();
		//select A.* FROM VOUCHER_2022_summer_event A, PO_2022_summer_event B, PAYMENT_2022_summer_event C 
		//where A.VOUCHER_GID=B.VOUCHER_GID AND B.PO_GID=C.PO_GID AND C.PAYMENT_GID='2';

		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;
		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		try { 
			// SEND_TOTAL		
			strStmt = "SELECT A.* FROM VOUCHER_"+voucher_name+" A, PO_"+voucher_name+" B, PAYMENT_"+voucher_name+" C "; 
			strStmt+= "WHERE A.VOUCHER_GID = B.VOUCHER_GID AND B.PO_GID = C.PO_GID AND C.PAYMENT_GID='"+id+"'";

			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			while(rs.next()) {
				map.put("VOUCHER_GID",rs.getLong("VOUCHER_GID"));
				map.put("VOUCHER_NONCE",rs.getString("VOUCHER_NONCE"));
				map.put("VOUCHER_NAME",rs.getString("VOUCHER_NAME"));
				map.put("VOUCHER_NO",rs.getString("VOUCHER_NO"));
				map.put("VOUCHER_PVALUE",rs.getLong("VOUCHER_PVALUE"));
				map.put("ISSUER_SIGN",rs.getString("ISSUER_SIGN"));
				map.put("TOSA_TIME",rs.getString("TOSA_TIME"));
				map.put("TOSA_SIGN",rs.getString("TOSA_SIGN"));				
				count++;
			}			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return map;
	}

	public static HashMap<String, Object> SearchPOByPaymentID(String voucher_name, String id) {
		HashMap<String, Object> map = new HashMap<String, Object>();
		//select B.* FROM VOUCHER_2022_summer_event A, PO_2022_summer_event B, PAYMENT_2022_summer_event C 
		//where A.VOUCHER_GID=B.VOUCHER_GID AND B.PO_GID=C.PO_GID AND C.PAYMENT_GID='2';

		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;
		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		try { 
			// SEND_TOTAL		
			strStmt = "SELECT B.* FROM VOUCHER_"+voucher_name+" A, PO_"+voucher_name+" B, PAYMENT_"+voucher_name+" C "; 
			strStmt+= "WHERE A.VOUCHER_GID = B.VOUCHER_GID AND B.PO_GID = C.PO_GID AND C.PAYMENT_GID='"+id+"'";

			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			while(rs.next()) {
				map.put("PO_GID",rs.getLong("PO_GID"));
				map.put("PO_NONCE",rs.getString("PO_NONCE"));
				map.put("USER_ACCOUNT",rs.getString("USER_ACCOUNT"));
				map.put("USER_SIGN",rs.getString("USER_SIGN"));
				map.put("TOSA_TIME",rs.getString("TOSA_TIME"));
				map.put("TOSA_SIGN",rs.getString("TOSA_SIGN"));				
				count++;
			}			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return map;
	}

	public static void showAllVouchers(String pvoucher_name){
		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;
		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		try { 
			String voucher_gid, voucher_nonce,voucher_name,voucher_no,voucher_pvalue,issuer_sign,torder_time,torder_sign;
			
			/* Query - available Voucher란 PAYMENT에 없는 VOUCHER 여야 한다. 
			SELECT A.*
			FROM VOUCHER_2022_summer_event A
			LEFT JOIN PO_2022_summer_event B ON A.VOUCHER_GID=B.VOUCHER_GID 
			LEFT JOIN PAYMENT_2022_summer_event C ON B.PO_GID=C.PO_GID
			WHERE B.VOUCHER_GID IS NULL OR C.PO_GID IS NULL; 
			*/

			strStmt = "SELECT A.* ";
			strStmt+= "FROM VOUCHER_"+pvoucher_name+" A ";
			strStmt+= "LEFT JOIN PO_"+pvoucher_name+" B ON A.VOUCHER_GID=B.VOUCHER_GID ";
			strStmt+= "LEFT JOIN PAYMENT_"+pvoucher_name+" C ON B.PO_GID=C.PO_GID "; 
			//strStmt+= "WHERE B.VOUCHER_GID IS NULL OR C.PO_GID IS NULL "; 
			strStmt+= "WHERE B.VOUCHER_GID IS NULL AND C.PO_GID IS NULL "; 
			strStmt+= "ORDER BY A.VOUCHER_GID;";	
			System.out.println("Query="+strStmt);
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println(String.format("[%8s] %12s %12s %8s %s ","COUNT","VOUCHER_GID","VOUCHER_NAME","VOUCHER_NO","VOUCHER_PVALUE" ));
			while(rs.next()) {
				voucher_gid = Long.toString(rs.getLong("VOUCHER_GID"));
				voucher_nonce = rs.getString("VOUCHER_NONCE");
				voucher_name = rs.getString("VOUCHER_NAME");
				voucher_no = rs.getString("VOUCHER_NO");
				voucher_pvalue = Long.toString(rs.getLong("VOUCHER_PVALUE"));
				issuer_sign = rs.getString("ISSUER_SIGN");
				torder_time = rs.getString("TOSA_TIME");
				torder_sign = rs.getString("TOSA_SIGN");				
				System.out.println(String.format("[%8d] %12s %12s %8s %s ",count,voucher_gid,voucher_name,voucher_no,voucher_pvalue));
				count++;
			}			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public static void showAvailableVouchers(String pvoucher_name){
		showAllVouchers(pvoucher_name);
	}

	public static void raisePO(String pvoucher_name,String user_wallet_name,String voucherid) {
		WalletManager walletManager = new WalletManager();
		String strStmt;		
		String strSignInput;
		String serviceName;	
		String strPubKey;
		byte nonce[] = new byte[8];
		SecureRandom random = new SecureRandom();		

		strPubKey = walletManager.readWallet(user_wallet_name);
	
		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return;
		}

		serviceName =ISSSUER_NAME+"/"+SERVICE_VOUCHER_MANAGEMENT+"/PO_"+pvoucher_name;

		try {
			random.nextBytes(nonce);						
			strSignInput = ArrayUtil.toHex(nonce)+voucherid; // SIGN INPUTS(TORDER_NONCE,VOUCHER_ID)							
		 	strStmt= "INSERT INTO PO_"+pvoucher_name+" (PO_GID,PO_NONCE,VOUCHER_GID,USER_ACCOUNT,USER_SIGN,TOSA_TIME,TOSA_SIGN) VALUES ";
		 	strStmt += "(@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+voucherid+"','"+strPubKey+"','"+walletManager.secureSign(user_wallet_name,strSignInput)+"',@DATETIME,@SYNC_SIGN);";
		 	System.out.println(strStmt);
				
		 	byte[] baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			int errorCode = ArrayUtil.BAToInt(baReturn);
			if(errorCode==0) {
				System.out.println("Success!");
			} else {
				System.out.println("Fail.. errorCode = "+errorCode);
			}
		} catch(Exception e) {
			e.printStackTrace();
		} 
		dnomConnector.close();
	}

	public static void showMyPO(String pvoucher_name,String buyer_wallet_name) {
		WalletManager walletManager = new WalletManager();
		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;

		String strPubKey = walletManager.readWallet(buyer_wallet_name);

		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		try { 
			String torder_num, torder_nonce,voucher_id,user_account,user_sign,torder_time,torder_sign;
			
			// SEND_TOTAL		
			strStmt = "SELECT * FROM PO_"+pvoucher_name+" WHERE USER_ACCOUNT = '"+strPubKey+"' ORDER BY PO_GID ASC;";
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println(String.format("[%8s] %12s\t%12s","COUNT","PO_GID","VOUCHER_GID"));
			while(rs.next()) {
				torder_num = Long.toString(rs.getLong("PO_GID"));
				torder_nonce = rs.getString("PO_NONCE");
				voucher_id = rs.getString("VOUCHER_GID");
				user_account = rs.getString("USER_ACCOUNT");
				user_sign = rs.getString("USER_SIGN");
				torder_time = rs.getString("TOSA_TIME");
				torder_sign = rs.getString("TOSA_SIGN");				
				System.out.println(String.format("[%8d] %12s\t%12s",count,torder_num,voucher_id));
				count++;
			}			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}			
		}
	}

	public static void showMyVoucher(String pvoucher_name,String buyer_wallet_name) {
		WalletManager walletManager = new WalletManager();
		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;

		String strPubKey;
		strPubKey = walletManager.readWallet(buyer_wallet_name);

		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		try { 
			String voucher_name, voucher_no, voucher_pvalue, po_gid, payment_gid, transfer_gid;
			
			strStmt = "SELECT A.*, B.PO_GID, C.PAYMENT_GID, TRANSACTION_GID FROM VOUCHER_"+pvoucher_name+" A, ";
			strStmt +="PO_"+pvoucher_name+" B, PAYMENT_"+pvoucher_name+" C, TRANSACTIONS_"+pvoucher_name+" D ";
			strStmt +="WHERE A.VOUCHER_GID=B.VOUCHER_GID AND B.USER_ACCOUNT='"+strPubKey+"' AND B.PO_GID=C.PO_GID AND C.PAYMENT_GID=D.PAYMENT_GID"; 
			
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println(String.format("[%8s] %12s %12s %8s %6s %6s %6s","COUNT","NAME","VOUCHER_NO","VALUE","PO_ID","PAY_ID","TRAN_ID"));
			while(rs.next()) {
				voucher_name = rs.getString("A.VOUCHER_NAME");
				voucher_no = rs.getString("A.VOUCHER_NO");
				voucher_name = Long.toString(rs.getLong("A.VOUCHER_PVALUE"));
				po_gid = rs.getString("B.PO_GID");
				payment_gid = rs.getString("C.PAYMENT_GID");
				transfer_gid = rs.getString("D.TRANSACTION_GID");							
				System.out.println(String.format("[%8d] %12s %12s %8s %6s %6s %6s",count,voucher_name,voucher_no,voucher_name,po_gid,payment_gid,transfer_gid));
				count++;
			}			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public static String searchMyBalance(String pvoucher_name,String wallet_name) {
		WalletManager walletManager = new WalletManager();
		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt;
		String strSenderPubKey;
		
		strSenderPubKey = walletManager.readWallet(wallet_name);
 	 	 
	 	long sender_sent_tot_amount=0;
		long receiver_recvd_tot_amount=0, receiver_recvd_ms_tot_amount=0;		 
		long holder_balance=0; 
		
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;

		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
 
		try {					
			// SEND_TOTAL		
			strStmt = "SELECT SUM(AMOUNT) FROM TRANSACTIONS_"+pvoucher_name+" WHERE SENDER ='"+strSenderPubKey+"';";
		//	System.out.println(strStmt);
			pstmt = con.prepareStatement(strStmt);			
		//	System.out.println("1");
			rs = pstmt.executeQuery();
			if (!rs.next()) {
				sender_sent_tot_amount = 0;
			} else {				
				sender_sent_tot_amount = rs.getLong(1); 				
			}	
			System.out.println("SENDER SENT TOT AMOUNT = "+sender_sent_tot_amount);

			// RECEIVE_TOTAL MULTI_SIG='N'		
			strStmt = "SELECT SUM(AMOUNT) FROM TRANSACTIONS_"+pvoucher_name+" WHERE RECEIVER ='"+strSenderPubKey+"' AND MULTISIG_YN='N';";
		//	System.out.println(strStmt);
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			if (!rs.next()) {
				receiver_recvd_tot_amount =0;
			} else {
				receiver_recvd_tot_amount = rs.getLong(1); 				
			}	
			System.out.println("SENDER RECEIVED MULTISIG=N TOT AMOUNT = "+receiver_recvd_tot_amount);
 		
			// RECEIVE_TOTAL MULTI_SIG='Y' AND SIGNED!
			strStmt = "SELECT SUM(a.AMOUNT) FROM TRANSACTIONS_"+pvoucher_name+" AS a, MULTISIG_"+pvoucher_name+" AS b WHERE RECEIVER ='"+strSenderPubKey+"' AND a.TRANSACTION_GID=b.TRANSACTION_GID";
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			if (!rs.next()) {
				receiver_recvd_ms_tot_amount =0;
			} else {
				receiver_recvd_ms_tot_amount = rs.getLong(1); 				
			}	
			System.out.println("SENDER RECEIVED MULTISIG=Y AND SIGNED TOT AMOUNT = "+receiver_recvd_ms_tot_amount);
 		
			holder_balance = (receiver_recvd_tot_amount+receiver_recvd_ms_tot_amount) - sender_sent_tot_amount;
			System.out.println("---------------------------------------------");
			System.out.println("HOLDER BALANCE= "+holder_balance);
			System.out.println("---------------------------------------------\n");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) {
					rs.close(); 
				}
				if(pstmt != null) {
					pstmt.close(); 
				}			 
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}						
		return Long.toString(holder_balance);		
	}

	public static void showAvailablePO(String pvoucher_name) {
		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;

		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}

		try { 
			String torder_num, torder_nonce,voucher_id,user_account,user_sign,torder_time,torder_sign;
			
			// SEND_TOTAL		
//			strStmt = "SELECT * FROM PO_"+pvoucher_name+" ORDER BY PO_GID ASC;";
			strStmt = "SELECT A.* FROM PO_"+pvoucher_name+" A LEFT JOIN PAYMENT_"+pvoucher_name+" B ON A.PO_GID=B.PO_GID WHERE B.PO_GID IS NULL;";
			System.out.println("QUERY = "+strStmt);

			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println(String.format("[%8s] %12s %12s","COUNT","PO_GID","VOUCHER_GID"));
			while(rs.next()) {
				torder_num = Long.toString(rs.getLong("PO_GID"));
				torder_nonce = rs.getString("PO_NONCE");
				voucher_id = rs.getString("VOUCHER_GID");
				user_account = rs.getString("USER_ACCOUNT");
				user_sign = rs.getString("USER_SIGN");
				torder_time = rs.getString("TOSA_TIME");
				torder_sign = rs.getString("TOSA_SIGN");								
				System.out.println(String.format("[%8d] %12s %12s",count,torder_num,voucher_id));
				count++;
			}			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public static void signPayment(String pvoucher_name, String ppg_wallet_name, String ppoid) {
		WalletManager walletManager = new WalletManager();
		String strStmt;
		String strSignInput;
		String serviceName;	
		String strPubKey;
		byte nonce[] = new byte[8];
		SecureRandom random = new SecureRandom();		

		strPubKey = walletManager.readWallet(ppg_wallet_name);
	
		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return;
		}

		serviceName =ISSSUER_NAME+"/"+SERVICE_VOUCHER_MANAGEMENT+"/PAYMENT_"+pvoucher_name;
		HashMap<String, Object> voucherRecord = SearchVoucherByPOID(pvoucher_name, ppoid);
		String voucher_no = (String) voucherRecord.get("VOUCHER_NO");
		try {			
			random.nextBytes(nonce);						
			strSignInput = ArrayUtil.toHex(nonce)+ppoid; // SIGN INPUTS(TORDER_NONCE,PO_ID)							
		 	strStmt= "INSERT INTO PAYMENT_"+pvoucher_name+" (PAYMENT_GID,PAYMENT_NONCE,PO_GID,VOUCHER_NO,PG_ACCOUNT,PG_SIGN,TOSA_TIME,TOSA_SIGN) VALUES ";
		 	strStmt += "(@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+ppoid+"','"+voucher_no+"','"+strPubKey+"','"+walletManager.secureSign(ppg_wallet_name, strSignInput)+"',@DATETIME,@SYNC_SIGN);";
		 	System.out.println(strStmt);
					
		 	byte[] baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			int errorCode = ArrayUtil.BAToInt(baReturn);
			if(errorCode==0) {
				System.out.println("Success!");
			} else {
				System.out.println("Fail.. errorCode = "+errorCode);
			}
		} catch(Exception e) {
			e.printStackTrace();
		} 
		dnomConnector.close();
	}

	public static void showMyPayment(String pvoucher_name, String ppg_wallet_name) {
		WalletManager walletManager = new WalletManager();
		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;

		String strPubKey;
		strPubKey = walletManager.readWallet(ppg_wallet_name);

		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		try { 
			String payment_gid, po_gid, voucher_gid;
			
			/* Query
			SELECT A.PAYMENT_GID, A.PO_GID, C.VOUCHER_NO FROM PAYMENT_2022_summer_event A, PO_2022_summer_event B, VOUCHER_2022_summer_event C 
			WHERE B.VOUCHER_GID=C.VOUCHER_GID AND A.PO_GID=B.PO_GID AND A.PG_ACCOUNT = '02DD11F39247193EFB1F6C1ABBC51C10FEA5AEB034CF754BAC8870ED5F3E32C338' ORDER BY A.PAYMENT_GID ASC;
			*/
	
			strStmt = "SELECT A.PAYMENT_GID, A.PO_GID, C.VOUCHER_GID FROM PAYMENT_"+pvoucher_name+" A, PO_"+pvoucher_name+" B, VOUCHER_"+pvoucher_name+" C ";
			strStmt+= "WHERE B.VOUCHER_GID=C.VOUCHER_GID AND A.PO_GID=B.PO_GID AND A.PG_ACCOUNT = '"+strPubKey+"' ORDER BY A.PAYMENT_GID ASC;";
			
			//System.out.println("Query="+strStmt);

			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println(String.format("[%8s] %12s\t%12s\t%12s","COUNT","PAYMENT_GID","PO_GID","VOUCHER_GID"));
			while(rs.next()) {
				payment_gid = Long.toString(rs.getLong("A.PAYMENT_GID"));
				po_gid = rs.getString("A.PO_GID");
				voucher_gid = rs.getString("C.VOUCHER_GID");				
				System.out.println(String.format("[%8d] %12s\t%12s\t%12s",count,payment_gid,po_gid,voucher_gid));
				count++;
			}			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public static void showAvailablePAYMENT(String voucher_name) {
		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;

		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		try { 
			String torder_num, torder_nonce,po_id,pg_account,pg_sign,torder_time,torder_sign;
			
			// SEND_TOTAL		
			strStmt = "SELECT * FROM PAYMENT_"+voucher_name+" ORDER BY PAYMENT_GID ASC;";
			strStmt = "SELECT A.* FROM PAYMENT_"+voucher_name+" A LEFT JOIN TRANSACTIONS_"+voucher_name+" B ON A.PAYMENT_GID=B.PAYMENT_GID WHERE B.PAYMENT_GID IS NULL;";

			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println(String.format("[%8s] %12s %12s","COUNT","PAYMENT_GID","PO_GID"));
			while(rs.next()) {
				torder_num = Long.toString(rs.getLong("PAYMENT_GID"));
				torder_nonce = rs.getString("PAYMENT_NONCE");
				po_id = rs.getString("PO_GID");
				pg_account = rs.getString("PG_ACCOUNT");
				pg_sign = rs.getString("PG_SIGN");
				torder_time = rs.getString("TOSA_TIME");
				torder_sign = rs.getString("TOSA_SIGN");				
				System.out.println(String.format("[%8d] %12s %12s",count,torder_num,po_id));
				count++;
			}			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void signTransfer(String voucher_name, String issuer_wallet_name, String paymentid) {
		WalletManager walletManager = new WalletManager();
		String strStmt;
		String strSignInput;
		String serviceName;	
		String strPubKey;
		byte nonce[] = new byte[8];
		SecureRandom random = new SecureRandom();		

		HashMap<String, Object> voucherRecord = SearchVoucherByPaymentID(voucher_name, paymentid);
		HashMap<String, Object> poRecord = SearchPOByPaymentID(voucher_name, paymentid);
		String receiver = poRecord.get("USER_ACCOUNT").toString();
		String amount = voucherRecord.get("VOUCHER_PVALUE").toString();

		strPubKey = walletManager.readWallet(issuer_wallet_name);
	
		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return;
		} 

		serviceName =ISSSUER_NAME+"/"+SERVICE_VOUCHER_MANAGEMENT+"/TRANSACTIONS_"+voucher_name;

		try {
			random.nextBytes(nonce);						
			strSignInput = ArrayUtil.toHex(nonce)+paymentid+strPubKey+amount+receiver+"N";
			strStmt= "INSERT INTO TRANSACTIONS_"+voucher_name+" (TRANSACTION_GID,TRANSACTION_NONCE,PAYMENT_GID,SENDER,AMOUNT,RECEIVER,MULTISIG_YN,MULTISIGNER,SENDER_SIGN,TOSA_TIME,TOSA_SIGN) VALUES ";
			strStmt += "(@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"',"+paymentid+",'"+strPubKey+"',"+amount+",'"+receiver+"','N','','"+walletManager.secureSign(issuer_wallet_name, strSignInput)+"',@DATETIME,@SYNC_SIGN);";
			System.out.println(strStmt);
			
			byte[] baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			int errorCode = ArrayUtil.BAToInt(baReturn);
			if(errorCode==0) {
				System.out.println("Success!");
			} else {
				System.out.println("Fail.. errorCode = "+errorCode);
			}
		} catch(Exception e) {
			e.printStackTrace();
		} 
		dnomConnector.close();
	}

	public static void showAllTransfer(String voucher_name) {
		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;

		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		try { 
			String torder_num, torder_nonce,payment_id,sender,amount,receiver;
			
			// SEND_TOTAL		
			strStmt = "SELECT * FROM TRANSACTIONS_"+voucher_name+" ORDER BY TRANSACTION_GID ASC;";
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println(String.format("[%8s] %12s %12s %12s %12s %12s","COUNT","TRANSACTION_GID","PAYMENT_GID","SENDER","AMOUNT","RECEIVER"));
			while(rs.next()) {
				torder_num = Long.toString(rs.getLong("TRANSACTION_GID"));
				payment_id = rs.getString("PAYMENT_GID");
				sender = rs.getString("SENDER");
				amount = Long.toString(rs.getLong("AMOUNT"));
				receiver = rs.getString("RECEIVER");				
				System.out.println(String.format("[%8s] %12s %12s %12s %12s %12s",count,torder_num,payment_id,sender,amount,receiver));
				count++;
			}			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public static void showSignedMultiSig(String token_name, String wallet_name) {
		WalletManager walletManager = new WalletManager();
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt;
		String strPubKey;

		Connection con=null;
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;
		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}

		strPubKey = walletManager.readWallet(wallet_name);
	 	try { 
			String transaction_gid, mint_gid, sender_account, receiver_account, multisig_yn, multisig_account, multisig_sign;
			String inout;
			long amount=0;
			transaction_gid=mint_gid=sender_account=receiver_account=multisig_yn=multisig_account=multisig_sign=null;
			
			strStmt = "SELECT * FROM TRANSACTIONS_"+token_name+" A INNER JOIN MULTISIG_"+token_name+" B WHERE A.TRANSACTION_GID = B.TRANSACTION_GID AND A.MULTISIGNER='"+strPubKey+"'";

			System.out.println("QUERY = "+strStmt);

			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println("");
			System.out.println(String.format("[%5s] %4s %4s %4s %4s %s %4s","COUNT","TX_ID","SENDER","AMOUNT","RECEIVER","M","MULTISIGNER"));
			while(rs.next()) {
				transaction_gid = Long.toString(rs.getLong("TRANSACTION_GID"));				
				mint_gid = rs.getString("PAYMENT_GID");
				sender_account = rs.getString("SENDER");
				amount = rs.getLong("AMOUNT");
				receiver_account = rs.getString("RECEIVER");
				multisig_yn = rs.getString("MULTISIG_YN");
				multisig_account = rs.getString("MULTISIGNER");
				System.out.println(String.format("[%5d] %4s %4s %8d %4s %4s %4s.",count,transaction_gid,sender_account.substring(0,4),amount, receiver_account.substring(0,4),multisig_yn,multisig_account.substring(0,4)));
				count++;
			}			
			System.out.println("");
	 	} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) {
					rs.close(); 
				}
				if(pstmt != null) {
					pstmt.close(); 
				}			 
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}		
	}						
	

	public static void showRequestedMultisig(String token_name, String wallet_name) {
		WalletManager walletManager = new WalletManager();
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt;
		String strPubKey;
		
		Connection con=null;
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;
		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
	 	strPubKey = walletManager.readWallet(wallet_name);
	 	try { 
			String transaction_gid, mint_gid, sender_account, receiver_account, multisig_yn, multisig_account, multisig_sign;
			String inout;
			long amount=0;
			transaction_gid=mint_gid=sender_account=receiver_account=multisig_yn=multisig_account=multisig_sign=null;
			
			//strStmt = "SELECT A.* FROM TRANSACTIONS_"+token_name+" A, MULTISIG_"+token_name+" B WHERE A.TRANSACTION_GID!=B.TRANSACTION_GID AND A.MULTISIG_YN='Y' AND A.MULTISIG_ACCOUNT='"+strPubKey+"'";
			strStmt = "SELECT A.* FROM TRANSACTIONS_"+token_name+" A LEFT JOIN MULTISIG_"+token_name+" B ON A.TRANSACTION_GID=B.TRANSACTION_GID WHERE B.TRANSACTION_GID IS NULL AND A.MULTISIG_YN='Y' AND A.MULTISIGNER='"+strPubKey+"'";
			System.out.println("QUERY = "+strStmt);

			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println("");
			System.out.println(String.format("[%5s] %4s %4s %4s %4s %s %4s","COUNT","TX_ID","SENDER","AMOUNT","RECEIVER","M","M_ACCOUNT"));
			while(rs.next()) {
				transaction_gid = Long.toString(rs.getLong("TRANSACTION_GID"));				
				mint_gid = rs.getString("PAYMENT_GID");
				sender_account = rs.getString("SENDER");
				amount = rs.getLong("AMOUNT");
				receiver_account = rs.getString("RECEIVER");
				multisig_yn = rs.getString("MULTISIG_YN");
				multisig_account = rs.getString("MULTISIGNER");
				System.out.println(String.format("[%5d] %4s %4s %8d %4s %4s %4s.",count,transaction_gid,sender_account.substring(0,4),amount, receiver_account.substring(0,4),multisig_yn,multisig_account.substring(0,4)));
				count++;
			}			
			System.out.println("");
	 	} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) {
					rs.close(); 
				}
				if(pstmt != null) {
					pstmt.close(); 
				}			 
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public static void showAllTransactions(String token_name, String wallet_name){
		WalletManager walletManager = new WalletManager();
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt;
		String strPubKey;

		Connection con=null;
		String strDBURL= DBURL+"/"+SERVICE_VOUCHER_MANAGEMENT;
		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
	 	strPubKey = walletManager.readWallet(wallet_name);
	 	try { 
			String torder_num, torder_nonce,minter_tx_id,sender,receiver,sender_sign,torder_time,torder_sign;
			String inout;
			long amount=0;
			// SEND_TOTAL		
			strStmt = "SELECT * FROM TRANSACTIONS_"+token_name+" WHERE SENDER ='"+strPubKey+"' OR RECEIVER = '"+strPubKey+"' ORDER BY TRANSACTION_GID ASC;";
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println("");
			System.out.println(String.format("[%5s]\t%s\t%s\t%s\t%s\t%s\t%s","COUNT","TX_ID","SENT/RECV","SENDER","AMOUNT","RECEIVER","TIME"));
			while(rs.next()) {
				torder_num = Long.toString(rs.getLong("TRANSACTION_GID"));
				torder_nonce = rs.getString("TRANSACTION_NONCE");
				minter_tx_id = rs.getString("PAYMENT_GID");
				sender = rs.getString("SENDER");
				amount = rs.getLong("AMOUNT");
				receiver = rs.getString("RECEIVER");
				sender_sign = rs.getString("SENDER_SIGN");
				torder_time = rs.getString("TOSA_TIME");
				torder_sign = rs.getString("TOSA_SIGN");
				if(sender.equals(strPubKey)) inout="SENT";
				else inout="RECV";
				System.out.println(String.format("[%5s]\t%s\t%s\t%s\t%s\t%s\t%s",count,torder_num,inout,sender.substring(0,8),amount,receiver.substring(0,8),torder_time));
				count++;
			}			
	 	} catch (Exception e) {
	 		e.printStackTrace();
		} finally {
			try {
				if(rs != null) {
					rs.close(); 
				}
				if(pstmt != null) {
					pstmt.close(); 
				}			 
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}						
	}

	public static int insertMultisig(String issuer_name, String service_name,String token_name, String wallet_name, String tx_id){
		WalletManager walletManager = new WalletManager();
		int iret=1;

		String strMultisigPubKey;
		String str_Sender_sign; 
		String strStmt;

		SecureRandom random = new SecureRandom();

		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return 1;
		} 

		strMultisigPubKey = walletManager.readWallet(wallet_name);
		 
		String serviceName = null;
		byte[] baReturn= null;
		int errorCode=0;
		try {
			byte nonce[] = new byte[8];
			random.nextBytes(nonce);
		
			String str = ArrayUtil.toHex(nonce)+tx_id;
			str_Sender_sign = walletManager.secureSign(wallet_name, str);	 
			
			// OOPS, In case inserting MINTER_TX_ID VALUE NULL, mysqld go to die.
			//strStmt= "INSERT INTO TRANSACTIONS_"+token_name+" (TORDER_NUM,TORDER_NONCE,MINTER_TX_ID,SENDER,AMOUNT,RECEIVER,SENDER_SIGN,TORDER_TIME,TORDER_SIGN) ";
			//strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"',null,'"+strSenderPubKey+"',"+Long.toString(amount)+",'"+strReceiverPubKey+"','"+str_Sender_sign+"','2021-11-01 00:00:00',@SYNC_SIGN);";
			
			strStmt= "INSERT INTO MULTISIG_"+token_name+" (MULTISIG_GID, MULTISIG_NONCE, TRANSACTION_GID,MULTISIGNER, MULTISIGNER_SIGN, TOSA_TIME,TOSA_SIGN) ";
			strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"',"+tx_id+",'"+strMultisigPubKey+"','"+str_Sender_sign+"',@DATETIME,@SYNC_SIGN);";
			System.out.println(strStmt);
			
			serviceName =issuer_name+"/"+service_name+"/"+"MULTISIG_"+token_name;
			//baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			errorCode = ArrayUtil.BAToInt(baReturn);
			if(errorCode==0) {
			//	System.out.println("Success!");
			} else {
				System.out.println("Fail.. errorCode = "+errorCode);
				return errorCode;
			}				
		} catch (Exception e) {
			e.printStackTrace();
		}		
		return iret;
	}
	
	public static int insertTransfer(String issuer_name, String service_name,String token_name, String wallet_name, String receiver_wallet_name, long amount, String multisig_yn, String multisig_wallet_name) {										
		WalletManager walletManager = new WalletManager();
		int iret=1;
		
		String strSenderPubKey;
		String strReceiverPubKey;
		String strMultisigPubKey;
		String str_Sender_sign; 
		String strStmt;

		SecureRandom random = new SecureRandom();

		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return 1;
		} 

		strSenderPubKey = walletManager.readWallet(wallet_name);
		strReceiverPubKey = walletManager.readWallet(receiver_wallet_name);	 	
		if(multisig_yn.equals("Y")) {
			strMultisigPubKey = walletManager.readWallet(multisig_wallet_name);
		} else {
			strMultisigPubKey="";
		}
		 
		String serviceName = null;
		byte[] baReturn= null;
		int errorCode=0;
		try {
			byte nonce[] = new byte[8];
			random.nextBytes(nonce);
			byte txid[] = new byte[4];
			random.nextBytes(txid);
			
			// TRANSCTIONS Table Fields
			// TRANSACTION_GID, TRANSACTION_NONCE,PAYMENT_GID, SENDER, AMOUT, RECEIVER,MULTISIG_YN,MULTISIGNER,SENDER_SIGN,TOSA_TIME,TOSA_SIGN			// 
			// CONSTRAINT SIGNATURE(SENDER_SIGN) INPUTS(TRANSACTION_NONCE,PAYMENT_GID,SENDER,AMOUNT,RECEIVER,MULTISIG_YN,MULTISIGNER) VERIFY KEY(SENDER),
			
			String signInputs = ArrayUtil.toHex(nonce)+strSenderPubKey+Long.toString(amount)+strReceiverPubKey+multisig_yn+strMultisigPubKey;
			str_Sender_sign = walletManager.secureSign(wallet_name, signInputs);	 
			
			// OOPS, In case inserting MINTER_TX_ID VALUE NULL, mysqld go to die.
			
			strStmt= "INSERT INTO TRANSACTIONS_"+token_name+" (TRANSACTION_GID,TRANSACTION_NONCE,SENDER,AMOUNT,RECEIVER,MULTISIG_YN,MULTISIGNER,SENDER_SIGN,TOSA_TIME,TOSA_SIGN) ";
			strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+strSenderPubKey+"',"+Long.toString(amount)+",'"+strReceiverPubKey+"','"+multisig_yn+"','"+strMultisigPubKey+"','"+str_Sender_sign+"',@DATETIME,@SYNC_SIGN);";
			System.out.println(strStmt);
			
			serviceName =issuer_name+"/"+service_name+"/"+"TRANSACTIONS_"+token_name;
			//baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			errorCode = ArrayUtil.BAToInt(baReturn);
			if(errorCode==0) {
			//	System.out.println("Success!");
			} else {
				System.out.println("Fail.. errorCode = "+errorCode);
				return errorCode;
			}				
		} catch (Exception e) {
			e.printStackTrace();
		}		
		return iret;
	}

	public static int insertTransfer(WalletManager walletManager, String issuer_name, String service_name,String token_name, String wallet_name, String receiver_wallet_name, long amount, String multisig_yn, String multisig_wallet_name) {										
		int iret=0;
		
		String strSenderPubKey;
		String strReceiverPubKey;
		String strMultisigPubKey;
		String str_Sender_sign; 
		String strStmt;

		SecureRandom random = new SecureRandom();

		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return 1;
		} 

		strSenderPubKey = walletManager.getAccount(wallet_name);
		strReceiverPubKey = walletManager.readWallet(receiver_wallet_name);	 	
		if(multisig_yn.equals("Y")) {
			strMultisigPubKey = walletManager.readWallet(multisig_wallet_name);
		} else {
			strMultisigPubKey="";
		}
		 
		String serviceName = null;
		byte[] baReturn= null;
		int errorCode=0;
		try {
			byte nonce[] = new byte[8];
			random.nextBytes(nonce);
			byte txid[] = new byte[4];
			random.nextBytes(txid);
			
			// TRANSCTIONS Table Fields
			// TRANSACTION_GID, TRANSACTION_NONCE,PAYMENT_GID, SENDER, AMOUT, RECEIVER,MULTISIG_YN,MULTISIGNER,SENDER_SIGN,TOSA_TIME,TOSA_SIGN			// 
			// CONSTRAINT SIGNATURE(SENDER_SIGN) INPUTS(TRANSACTION_NONCE,PAYMENT_GID,SENDER,AMOUNT,RECEIVER,MULTISIG_YN,MULTISIGNER) VERIFY KEY(SENDER),
			
			String signInputs = ArrayUtil.toHex(nonce)+strSenderPubKey+Long.toString(amount)+strReceiverPubKey+multisig_yn+strMultisigPubKey;
			str_Sender_sign = walletManager.sign(wallet_name, signInputs);	 
			
			// OOPS, In case inserting MINTER_TX_ID VALUE NULL, mysqld go to die.
			
			strStmt= "INSERT INTO TRANSACTIONS_"+token_name+" (TRANSACTION_GID,TRANSACTION_NONCE,SENDER,AMOUNT,RECEIVER,MULTISIG_YN,MULTISIGNER,SENDER_SIGN,TOSA_TIME,TOSA_SIGN) ";
			strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+strSenderPubKey+"',"+Long.toString(amount)+",'"+strReceiverPubKey+"','"+multisig_yn+"','"+strMultisigPubKey+"','"+str_Sender_sign+"',@DATETIME,@SYNC_SIGN);";
		//	System.out.println(strStmt);
			
			serviceName =issuer_name+"/"+service_name+"/"+"TRANSACTIONS_"+token_name;
			//baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			errorCode = ArrayUtil.BAToInt(baReturn);
			if(errorCode==0) {
			//	System.out.println("Success!");
			} else {
				System.out.println("Fail.. errorCode = "+errorCode);
				return errorCode;
			}				
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			dnomConnector.close();
		}		
		return iret;
	}

	public static int insertTransfer(DNOMConnector dnomConnector, WalletManager walletManager, String issuer_name, String service_name,String token_name, String wallet_name, String receiver_wallet_name, long amount, String multisig_yn, String multisig_wallet_name) {										
		int iret=0;
		
		String strSenderPubKey;
		String strReceiverPubKey;
		String strMultisigPubKey;
		String str_Sender_sign; 
		String strStmt;

		SecureRandom random = new SecureRandom();
		
		strSenderPubKey = walletManager.getAccount(wallet_name);
		strReceiverPubKey = walletManager.readWallet(receiver_wallet_name);	 	
		if(multisig_yn.equals("Y")) {
			strMultisigPubKey = walletManager.readWallet(multisig_wallet_name);
		} else {
			strMultisigPubKey="";
		}
		 
		String serviceName = null;
		byte[] baReturn= null;
		int errorCode=0;
		try {
			byte nonce[] = new byte[8];
			random.nextBytes(nonce);
			byte txid[] = new byte[4];
			random.nextBytes(txid);
			
			// TRANSCTIONS Table Fields
			// TRANSACTION_GID, TRANSACTION_NONCE,PAYMENT_GID, SENDER, AMOUT, RECEIVER,MULTISIG_YN,MULTISIGNER,SENDER_SIGN,TOSA_TIME,TOSA_SIGN			// 
			// CONSTRAINT SIGNATURE(SENDER_SIGN) INPUTS(TRANSACTION_NONCE,PAYMENT_GID,SENDER,AMOUNT,RECEIVER,MULTISIG_YN,MULTISIGNER) VERIFY KEY(SENDER),
			
			String signInputs = ArrayUtil.toHex(nonce)+strSenderPubKey+Long.toString(amount)+strReceiverPubKey+multisig_yn+strMultisigPubKey;
			str_Sender_sign = walletManager.sign(wallet_name, signInputs);	 
			
			// OOPS, In case inserting MINTER_TX_ID VALUE NULL, mysqld go to die.
			
			strStmt= "INSERT INTO TRANSACTIONS_"+token_name+" (TRANSACTION_GID,TRANSACTION_NONCE,SENDER,AMOUNT,RECEIVER,MULTISIG_YN,MULTISIGNER,SENDER_SIGN,TOSA_TIME,TOSA_SIGN) ";
			strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+strSenderPubKey+"',"+Long.toString(amount)+",'"+strReceiverPubKey+"','"+multisig_yn+"','"+strMultisigPubKey+"','"+str_Sender_sign+"',@DATETIME,@SYNC_SIGN);";
		//	System.out.println(strStmt);
			
			serviceName =issuer_name+"/"+service_name+"/"+"TRANSACTIONS_"+token_name;
			//baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			errorCode = ArrayUtil.BAToInt(baReturn);
			if(errorCode==0) {
			//	System.out.println("Success!");
			} else {
				System.out.println("Fail.. errorCode = "+errorCode);
				return errorCode;
			}				
		} catch (Exception e) {
			e.printStackTrace();
		} 		
		return iret;
	}

	public static void createVoucherTrasactionsTables(String voucher_name, String issuer_wallet_name, String tosa_wallet_name) {
		WalletManager walletManager = new WalletManager();
		String strStmt;
		String strTrgStmt=null;
		String strTransformed;		
		String serviceName;	
		String strIssuerPubKey;
		String strChainPubKey;
		String strOrdererPubKey;
		
		strIssuerPubKey = walletManager.readWallet(issuer_wallet_name);
		strChainPubKey = strIssuerPubKey;
		strOrdererPubKey = walletManager.readWallet(tosa_wallet_name);

		String engine="InnoDB";

		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return;
		}

		serviceName =ISSSUER_NAME+"/"+SERVICE_VOUCHER_MANAGEMENT+"/PO_"+voucher_name;

		strStmt= "CREATE TRUSTED,ORDERED TABLE IF NOT EXISTS PO_"+voucher_name+" ( \n";	
		strStmt += "PO_GID BIGINT UNSIGNED NOT NULL, \n";
		strStmt += "PO_NONCE VARCHAR(16) NOT NULL, \n";
		strStmt += "VOUCHER_GID BIGINT UNSIGNED , \n";
		strStmt += "USER_ACCOUNT VARCHAR(66) NOT NULL, \n";		
		strStmt += "USER_SIGN VARCHAR(160) NOT NULL, \n";		
		strStmt += "TOSA_TIME DATETIME NOT NULL, \n";		
		strStmt += "TOSA_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(PO_GID),\n";		
		strStmt += "CONSTRAINT UNIQUE (PO_NONCE), \n";		
		strStmt += "CONSTRAINT FOREIGN KEY(VOUCHER_GID) REFERENCES VOUCHER_"+voucher_name+"(VOUCHER_GID), \n";		
		// USER_ACCOUNT IS FOREIGN KEY OF member_management.verified_members.You should check it with TRIGGER.
		strStmt += "CONSTRAINT SIGNATURE(USER_SIGN) INPUTS(PO_NONCE,VOUCHER_GID) VERIFY KEY(USER_ACCOUNT), \n";
		strStmt += "CONSTRAINT SIGNATURE(TOSA_SIGN) ORDERED(PO_GID) TIMED(TOSA_TIME) \n";
		strStmt += ") ENGINE=InnoDB \n"; 
		strStmt += "DSA_SCHEME='SECP256K1' \n";
		strStmt += "TABLE_ISSUER_PUB_KEY='"+strIssuerPubKey+"' \n";		
		strStmt += "TRUSTED_REFERENCE_PUB_KEY='"+strChainPubKey+"'\n";
		strStmt += "TOSA_PUB_KEY='"+strOrdererPubKey+"'\n";
		strStmt += "TOSA_MASTER_PUB_KEY='"+strOrdererPubKey+"'\n"; 			
		strTransformed = ECDSA.transform_for_sign(strStmt);
		strStmt += "TABLE_SCHEMA=\""+strTransformed+"\" ";
		strStmt += "TABLE_SCHEMA_SIGN='"+walletManager.secureSign(issuer_wallet_name, strTransformed)+"'";

		byte[] baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
		int errorCode = ArrayUtil.BAToInt(baReturn);
		if(errorCode==0) {
			System.out.println("Success!");
		} else {
			System.out.println("Fail.. errorCode = "+errorCode);
		}

		serviceName =ISSSUER_NAME+"/"+SERVICE_VOUCHER_MANAGEMENT+"/PAYMENT_"+voucher_name;

		// 같은 VOUCHER_NO에 PO가 중복 될 수있지만 는 줄 수 있지만, PAYMENT는 단일 VOUCHER_NO에만 할 수 있다.
		// VOUCHER_NO을 컬럼에 추가하고 UNIQUE처리하자.
		strStmt= "CREATE TRUSTED,ORDERED TABLE IF NOT EXISTS PAYMENT_"+voucher_name+" ( \n";	
		strStmt += "PAYMENT_GID BIGINT UNSIGNED , \n";
		strStmt += "PAYMENT_NONCE VARCHAR(16) NOT NULL, \n";
		strStmt += "PO_GID BIGINT UNSIGNED NOT NULL, \n";	
		strStmt += "VOUCHER_NO VARCHAR(12) NOT NULL, \n";  //  VOUCHER를 2번 Payment할 수 있으므로 이 값을 UNIQUE조건으로 막자. 													
		strStmt += "PG_ACCOUNT VARCHAR(66) NOT NULL, \n";		
		strStmt += "PG_SIGN VARCHAR(160) NOT NULL, \n";		
		strStmt += "TOSA_TIME DATETIME NOT NULL, \n";		
		strStmt += "TOSA_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(PAYMENT_GID),\n";		
		strStmt += "CONSTRAINT UNIQUE (PAYMENT_NONCE), \n";
		strStmt += "CONSTRAINT UNIQUE (PO_GID), \n";
		strStmt += "CONSTRAINT UNIQUE (VOUCHER_NO), \n";
		strStmt += "CONSTRAINT FOREIGN KEY(PO_GID) REFERENCES PO_"+voucher_name+"(PO_GID), \n";		
		// USER_ACCOUNT IS FOREIGN KEY OF member_management.verified_members.You should check it with TRIGGER.
		strStmt += "CONSTRAINT SIGNATURE(PG_SIGN) INPUTS(PAYMENT_NONCE,PO_GID) VERIFY KEY(PG_ACCOUNT), \n";
		strStmt += "CONSTRAINT SIGNATURE(TOSA_SIGN) ORDERED(PAYMENT_GID) TIMED(TOSA_TIME) \n";
		strStmt += ") ENGINE=InnoDB \n"; 
		strStmt += "DSA_SCHEME='SECP256K1' \n";
		strStmt += "TABLE_ISSUER_PUB_KEY='"+strIssuerPubKey+"' \n";
		strStmt += "TRUSTED_REFERENCE_PUB_KEY='"+strChainPubKey+"'\n";		
		strStmt += "TOSA_PUB_KEY='"+strOrdererPubKey+"'\n";
		strStmt += "TOSA_MASTER_PUB_KEY='"+strOrdererPubKey+"'\n"; 			
		strTransformed = ECDSA.transform_for_sign(strStmt);
		strStmt += "TABLE_SCHEMA=\""+strTransformed+"\" ";
		strStmt += "TABLE_SCHEMA_SIGN='"+walletManager.secureSign(issuer_wallet_name, strTransformed)+"'";

		baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
		errorCode = ArrayUtil.BAToInt(baReturn);
		if(errorCode==0) {
			System.out.println("Success!");
		} else {
			System.out.println("Fail.. errorCode = "+errorCode);
		}

		//strTrgStmt= "DELIMITER $$ \n";
		//strTrgStmt= "drop trigger if exists VERIFICATION_TRANSACTIONS_"+voucher_name+"; \n";
		strTrgStmt  = "CREATE DEFINER=`"+ISSSUER_NAME+"`@`%` TRIGGER VERIFICATION_TRANSACTIONS_"+voucher_name+" \n";
		strTrgStmt += "BEFORE INSERT on TRANSACTIONS_"+voucher_name+" FOR EACH ROW \n";		
		strTrgStmt += "BEGIN \n";
		strTrgStmt += "DECLARE _msg VARCHAR(256); \n";
		strTrgStmt += "DECLARE _minted_amount BIGINT DEFAULT 0; \n";
		strTrgStmt += "DECLARE _senders_receive_total BIGINT DEFAULT 0; \n";
		strTrgStmt += "DECLARE _senders_send_total BIGINT DEFAULT 0; \n";
		strTrgStmt += "DECLARE _multis_senders_receive_total BIGINT DEFAULT 0; \n";
		strTrgStmt += "DECLARE _multis_senders_send_total BIGINT DEFAULT 0; \n";
		strTrgStmt += "DECLARE _balance BIGINT DEFAULT 0; \n";
		strTrgStmt += " \n";
		strTrgStmt += "-- VERIFICATION Rule \n";
		strTrgStmt += "-- #1 SENDER, RECEIVER should be different. \n";
		strTrgStmt += "IF NEW.SENDER = NEW.RECEIVER THEN \n";
		strTrgStmt += "SET _msg = concat('TRANSACTIONS TABLE VERIFICATION ERROR: SENDER, RECEIVER SHOULD BE DIFFERENT!'); \n";
		strTrgStmt += "signal sqlstate '45000' set message_text = _msg; \n";
		strTrgStmt += "END IF; \n";
		strTrgStmt += " \n";
		strTrgStmt += "IF NEW.PAYMENT_GID IS NOT NULL THEN \n";
		strTrgStmt += "-- #2MINT Transaction \n";
		strTrgStmt += "-- NEW.AMOUNT shoud be same VOUCHER_xxxx.VOUCHER_PVALUE in SAME KEY(PAYMENT_GID->PO_GID->VOUCHER_GID) \n";
		strTrgStmt += "SELECT A.VOUCHER_PVALUE INTO _minted_amount FROM VOUCHER_"+voucher_name+" A, PO_"+voucher_name+" B, PAYMENT_"+voucher_name+" C ";
		strTrgStmt += "WHERE A.VOUCHER_GID=B.VOUCHER_GID AND B.PO_GID=C.PO_GID AND C.PAYMENT_GID=NEW.PAYMENT_GID;";		
		strTrgStmt += " \n";
		strTrgStmt += "IF NEW.AMOUNT != _minted_amount THEN \n";
		strTrgStmt += "SET _msg = concat('TRANSACTIONS TABLE VERIFICATION ERROR: In case of MINT transaction, AMOUNT SHOULD SAME WITH VOUCHER_"+voucher_name+".PAR_VALUE'); \n";
		strTrgStmt += "signal sqlstate '45002' set message_text = _msg; \n";
		strTrgStmt += "END IF; \n";
		strTrgStmt += "ELSE \n";
		strTrgStmt += "-- #3 NORMAL Transaction \n";
		strTrgStmt += "-- #AMOUNT SHOUD BE LESS THAN or EQUAL TO SENDER'S BALANCE. p2p \n";
		strTrgStmt += " \n";
		strTrgStmt += "-- #3-1 SENDER's RECEIVE_TOTAL \n";
		strTrgStmt += "SELECT IFNULL(SUM(AMOUNT),0) INTO _senders_receive_total \n";
		strTrgStmt += "FROM TRANSACTIONS_"+voucher_name+" \n";
		strTrgStmt += "WHERE RECEIVER=NEW.SENDER AND MULTISIG_YN='N'; \n";
		strTrgStmt += " \n";
		strTrgStmt += "-- #3-2 SENDER's SEND_TOTAL \n";
		strTrgStmt += "SELECT IFNULL(SUM(AMOUNT),0) INTO _senders_send_total \n";
		strTrgStmt += "FROM TRANSACTIONS_"+voucher_name+" \n";
		strTrgStmt += "WHERE SENDER=NEW.SENDER AND MULTISIG_YN='N'; \n";
		strTrgStmt += " \n";
		strTrgStmt += "-- #4-1 MULTISIG SENDER's RECEIVE_TOTAL \n";
		strTrgStmt += "SELECT IFNULL(SUM(a.AMOUNT),0) INTO _multis_senders_receive_total \n";
		strTrgStmt += "FROM TRANSACTIONS_"+voucher_name+" AS a, MULTISIG_"+voucher_name+" AS b \n";
		strTrgStmt += "WHERE a.RECEIVER=NEW.SENDER AND a.MULTISIG_YN='Y' AND b.TRANSACTION_GID=NEW.TRANSACTION_GID; \n";
		strTrgStmt += " \n";
		strTrgStmt += "-- #4-2 MULTISIG SENDER's SEND_TOTAL \n";
		strTrgStmt += "SELECT IFNULL(SUM(a.AMOUNT),0) INTO _multis_senders_send_total \n";
		strTrgStmt += "FROM TRANSACTIONS_"+voucher_name+" AS a, MULTISIG_"+voucher_name+" AS b \n";
		strTrgStmt += "WHERE a.SENDER=NEW.SENDER AND a.MULTISIG_YN='Y'; \n";
		strTrgStmt += " \n";
		strTrgStmt += "SET _balance = (_senders_receive_total+_multis_senders_receive_total) - (_senders_send_total+_multis_senders_send_total); \n";
		strTrgStmt += " \n";
		strTrgStmt += "IF (NEW.AMOUNT > _balance) THEN \n";
		strTrgStmt += "SET _msg = concat('TRANSACTIONS TABLE VERIFICATION ERROR: AMOUNT IS GREATER THAN BALANCE'); \n";
		strTrgStmt += "signal sqlstate '45003' set message_text = _msg; \n";
		strTrgStmt += "END IF; \n";
		strTrgStmt += "END IF; \n";
		//strTrgStmt += "END $$\n";
		strTrgStmt += "END \n";

		serviceName =ISSSUER_NAME+"/"+SERVICE_VOUCHER_MANAGEMENT+"/TRANSACTIONS_"+voucher_name;

		strStmt= "CREATE TRUSTED,ORDERED TABLE IF NOT EXISTS TRANSACTIONS_"+voucher_name+" ( \n";	
		strStmt += "TRANSACTION_GID BIGINT UNSIGNED, \n";
		strStmt += "TRANSACTION_NONCE VARCHAR(16) NOT NULL, \n";
		strStmt += "PAYMENT_GID BIGINT UNSIGNED , \n";							
		strStmt += "SENDER VARCHAR(66) NOT NULL, \n";
		strStmt += "AMOUNT BIGINT UNSIGNED NOT NULL, \n";
		strStmt += "RECEIVER VARCHAR(66) NOT NULL, \n";
		strStmt += "MULTISIG_YN ENUM('N','Y') DEFAULT 'N', \n";
		strStmt += "MULTISIGNER VARCHAR(66) NOT NULL, \n";
		strStmt += "SENDER_SIGN VARCHAR(160) NOT NULL, \n";		
		strStmt += "TOSA_TIME DATETIME NOT NULL, \n";		
		strStmt += "TOSA_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(TRANSACTION_GID),\n";		
		strStmt += "CONSTRAINT UNIQUE (TRANSACTION_NONCE), \n";
		strStmt += "CONSTRAINT UNIQUE (PAYMENT_GID), \n";
		strStmt += "CONSTRAINT FOREIGN KEY(PAYMENT_GID) REFERENCES PAYMENT_"+voucher_name+"(PAYMENT_GID), \n";		
		// SENDER IS FOREIGN KEY OF member_management.verified_members.You should check it with TRIGGER.
		// RECEIVER IS FOREIGN KEY OF member_management.verified_members.You should check it with TRIGGER.
		// MULTISIGNER IS FOREIGN KEY OF member_management.verified_members.You should check it with TRIGGER.

		strStmt += "CONSTRAINT SIGNATURE(SENDER_SIGN) INPUTS(TRANSACTION_NONCE,PAYMENT_GID,SENDER,AMOUNT,RECEIVER,MULTISIG_YN,MULTISIGNER) VERIFY KEY(SENDER), \n";
		strStmt += "CONSTRAINT SIGNATURE(TOSA_SIGN) ORDERED(TRANSACTION_GID) TIMED(TOSA_TIME) \n";
		strStmt += ") ENGINE=InnoDB \n"; 
		strStmt += "DSA_SCHEME='SECP256K1' \n";
		strStmt += "TABLE_ISSUER_PUB_KEY='"+strIssuerPubKey+"' \n";		
		strStmt += "TRUSTED_REFERENCE_PUB_KEY='"+strChainPubKey+"'\n";
		strStmt += "TOSA_PUB_KEY='"+strOrdererPubKey+"'\n";
		strStmt += "TOSA_MASTER_PUB_KEY='"+strOrdererPubKey+"'\n"; 	
		strStmt += "TRIGGER_BEFORE_INSERT_SIGN='"+walletManager.secureSign(issuer_wallet_name, ECDSA.transform_for_sign(strTrgStmt))+"'\n";		 			
		strTransformed = ECDSA.transform_for_sign(strStmt);
		strStmt += "TABLE_SCHEMA=\""+strTransformed+"\" ";
		strStmt += "TABLE_SCHEMA_SIGN='"+walletManager.secureSign(issuer_wallet_name, strTransformed)+"'";

		baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
		errorCode = ArrayUtil.BAToInt(baReturn);
		if(errorCode==0) {
			System.out.println("Success!");
		} else {
			System.out.println("Fail.. errorCode = "+errorCode);
		}

		System.out.println("-------------------");
		System.out.println("STRSTMT="+strTrgStmt);
		System.out.println("-------------------");
			
		serviceName =ISSSUER_NAME+"/"+SERVICE_VOUCHER_MANAGEMENT+"/"+"TRANSACTIONS_"+voucher_name;
		baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strTrgStmt);
		errorCode = ArrayUtil.BAToInt(baReturn);
		if(errorCode==0) {
			System.out.println("Success!");
		} else {
			System.out.println("Fail.. errorCode = "+errorCode);
		//	return errorCode;
		}
		
		strStmt= "CREATE TRUSTED,ORDERED TABLE IF NOT EXISTS MULTISIG_"+voucher_name+" ( \n";
		strStmt += "MULTISIG_GID BIGINT UNSIGNED NOT NULL, \n";										
		strStmt += "MULTISIG_NONCE VARCHAR(16) NOT NULL, \n";		
		strStmt += "TRANSACTION_GID BIGINT UNSIGNED, \n";		
		strStmt += "MULTISIGNER VARCHAR(66) NOT NULL, \n";
		strStmt += "MULTISIGNER_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "TOSA_TIME DATETIME NOT NULL, \n";
		strStmt += "TOSA_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(MULTISIG_GID), \n";
		strStmt += "CONSTRAINT UNIQUE(MULTISIG_NONCE), \n";
		strStmt += "CONSTRAINT FOREIGN KEY(TRANSACTION_GID) REFERENCES TRANSACTIONS_"+voucher_name+"(TRANSACTION_GID) , \n";		
		strStmt += "CONSTRAINT SIGNATURE(MULTISIGNER_SIGN) INPUTS(MULTISIG_NONCE,TRANSACTION_GID) VERIFY KEY(MULTISIGNER) , \n";
		strStmt += "CONSTRAINT SIGNATURE(TOSA_SIGN) ORDERED(MULTISIG_GID) TIMED(TOSA_TIME) \n";
		strStmt += ") ENGINE="+engine+" \n";
		strStmt += "DSA_SCHEME='SECP256K1' \n";
		strStmt += "TABLE_ISSUER_PUB_KEY='"+strIssuerPubKey+"' \n";		
		strStmt += "TRUSTED_REFERENCE_PUB_KEY='"+strIssuerPubKey+"'\n";
		strStmt += "TOSA_PUB_KEY='"+strOrdererPubKey+"'\n";
		strStmt += "TOSA_MASTER_PUB_KEY='"+strOrdererPubKey+"'\n"; 			
		strTransformed = ECDSA.transform_for_sign(strStmt);
		strStmt += "TABLE_SCHEMA=\""+strTransformed+"\" ";
		strStmt += "TABLE_SCHEMA_SIGN='"+walletManager.secureSign(issuer_wallet_name, strTransformed)+"'";

		System.out.println("-------------------");
		System.out.println("STRSTMT="+strStmt);
		System.out.println("-------------------");
		
		serviceName =ISSSUER_NAME+"/"+SERVICE_VOUCHER_MANAGEMENT+"/"+"MULTISIG_"+voucher_name;
		baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
		errorCode = ArrayUtil.BAToInt(baReturn);
		if(errorCode==0) {
			System.out.println("Success!");
		} else {
			System.out.println("Fail.. errorCode = "+errorCode);
			//return errorCode;
		}


		dnomConnector.close(); 
	}

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws Exception {
		Console cons = System.console();
		String voucher_name=null;
		String issuer_wallet_name=null;
		String tosa_wallet_name=null;
		String escrower_wallet_name=null;
		
		String whoareyou=null;// 0 : Buyer, 1: PG, 2: Issuer
		String buyer_wallet_name=null;
		String pg_wallet_name=null;
		String flag=null;
		String menu;

		try { 
			Properties properties = new Properties();
			InputStream inputStream = new FileInputStream("trustsql.properties");
			properties.load(inputStream);
			inputStream.close();
			strDNOMAddress = (String) properties.get("DNOMADDR");
			strDNOMPort = (String) properties.get("DNOMPORT");					
			DBURL = (String) properties.get("DBURL");
			DBUSR = (String) properties.get("DBUSR");
			DBUSRPW = (String) properties.get("DBUSRPW");					
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		
		while(true) {
			System.out.println("\n"); 
			System.out.println("*======================================================*");
			System.out.println("|                                                      |"); 
			System.out.println("|                     NFT VOUCHER WALLET               |");
			System.out.println("|                       TRANSACTIONS                   |");
			System.out.println("|              -----------------------------           |");
			System.out.println("|                                                      |"); 
			System.out.println("|                      Copyright (c) 2020, TRUSTDB Inc.|");		
			System.out.println("*======================================================*\n");
			System.out.println("\n");
			
			System.out.println("Waht do you want ? ( 0: Deploy a Voucher Transaction  1: Do transactions )");
			flag = cons.readLine();
			if(flag.toUpperCase().equals("0")) {							
				System.out.println("=> Enter the ISSUER name ");
				ISSSUER_NAME = cons.readLine();
				System.out.println("\n");
								
				System.out.println("=> Enter the SERIVCE name ");
				SERVICE_VOUCHER_MANAGEMENT = cons.readLine();
				System.out.println("\n");
								
				System.out.println("\n");
				System.out.println("=> Enter the Issuer's key file name.");
				issuer_wallet_name = cons.readLine();
				System.out.println("\n");

				System.out.println("=> Enter the Orderer's Key file name");
				tosa_wallet_name = cons.readLine();					
				System.out.println("\n");					
				
				System.out.println("=> Enter the Voucher name.");
				voucher_name = cons.readLine();						
				System.out.println("\n");
				createVoucherTrasactionsTables(voucher_name, issuer_wallet_name, tosa_wallet_name);
			} else { 				
				while(true) {	
					if(voucher_name==null) {		
						System.out.println("=> Enter the ISSUER name ");
						ISSSUER_NAME = cons.readLine();
						System.out.println("\n");
										
						System.out.println("=> Enter the SERIVCE name ");
						SERVICE_VOUCHER_MANAGEMENT = cons.readLine();
						System.out.println("\n");
								
						System.out.println("\n");
						System.out.println("=> Enter the Voucher name.");					
						voucher_name = cons.readLine();						
						System.out.println("\n");
					}
		
					System.out.println("\n");
					System.out.println("Who are you ? (0:USER   1:PG    2:ISSUER   3:ESCROW Agent)");
					whoareyou = cons.readLine();
					
					if(whoareyou.equals("0")) {	
						System.out.println("=> Enter the Your wallet name.");
						buyer_wallet_name=cons.readLine();
						System.out.println("\n");						

						while(true) {
							System.out.println("");
							System.out.println("========================================");
							System.out.println("=> ["+buyer_wallet_name+"] WHAT DO YOU WANT ?");
							System.out.println("----------------------------------------");
							System.out.println(" 0:RETRUN TO PREVIOUS");						
							System.out.println(" 1:SHOW AVAILABLE VOUCHERS");
							System.out.println(" 2:PURCHASE ORDER");
							System.out.println(" 3:SHOW MY ORDERS");
							System.out.println(" 4:SHOW MY VOUCHERS");
							System.out.println(" 5:SHOW MY POINT BALANCE");
							System.out.println(" 6:TRANSFER POINT");
							System.out.println(" 7:TRANSFER HISTORY");
							System.out.println("========================================");								

							System.out.println("\n");				 
							menu = cons.readLine();			
							System.out.println("\n");				 
							
							if(menu.equals("0")) { 
								break; 
							} else if(menu.equals("1")) {
								showAvailableVouchers(voucher_name);
							} else if(menu.equals("2")) {
								System.out.println("=> Enter the VOUCHER_GID");
								String voucherid=cons.readLine();						
								raisePO(voucher_name, buyer_wallet_name, voucherid);
							} else if(menu.equals("3")) {
								showMyPO(voucher_name, buyer_wallet_name);
							} else if(menu.equals("4")) {
								showMyVoucher(voucher_name, buyer_wallet_name);
							} else if(menu.equals("5")) {
								String balance = searchMyBalance(voucher_name, buyer_wallet_name);
								System.out.println(buyer_wallet_name+"'s balance = "+balance);
							} else if(menu.equals("6")) {
								String receiver_wallet_name, amount, multisig_yn, multisig_wallet_name;
								System.out.println("=> Enter the Receiver's wallet name "); 
								receiver_wallet_name = cons.readLine();				
								System.out.println("");										
								System.out.println("=> How much do you want to transfer? ");
								amount = cons.readLine();
								System.out.println("");	

								System.out.println("=> Do you want transfer with Multisig? ( N : No, Y : Yes )");
								multisig_yn = cons.readLine();
								multisig_yn = multisig_yn.toUpperCase();
								if(multisig_yn.equals("Y")) {
									System.out.println("");	
									System.out.println("=> Enter the Multisigner's (ESCROW) wallet name");
									System.out.println("");	
									multisig_wallet_name = cons.readLine();					
								} else {
									multisig_yn="N";
									multisig_wallet_name=null;
								}
								
								long counter=0;
								System.out.println("=> How many times you want to transfer? ");
								counter = Long.parseLong(cons.readLine());
								System.out.println("");	

								WalletManager walletManager = new WalletManager();
								String password;
								System.out.println("");
								System.out.println("[WalletManger]=============================================");
								System.out.println("!!  Enter password to access the wallet ["+buyer_wallet_name+"]");            
								password = new String(cons.readPassword());
								System.out.println("============================================================");            
								System.out.println("");
								if(walletManager.readWallet(buyer_wallet_name,password)==null) {
									System.out.println("No exist wallet +"+buyer_wallet_name);
									continue;
								}

								System.out.println("");
								
								
								DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
								if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
									System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
									continue;
								} 
								for(long i=0; i<counter; i++) {
									System.out.print(i);
									System.out.print("\r");
									if(insertTransfer(dnomConnector, walletManager,ISSSUER_NAME,SERVICE_VOUCHER_MANAGEMENT, voucher_name, buyer_wallet_name, receiver_wallet_name, Long.parseLong(amount),multisig_yn,multisig_wallet_name)==0) {	
										//System.out.println("Sucessed!");					
									} else	{
										System.out.println("Failed");	
										break;
									}
								}
								dnomConnector.close();
							} else if(menu.equals("7")) {
								System.out.println("");					
								showAllTransactions(voucher_name,buyer_wallet_name);
							} else {
								continue;
							}
						}	
					} else if(whoareyou.equals("1")) {					
						System.out.println("=> Enter the YOUR NAME.");
						pg_wallet_name=cons.readLine();
						System.out.println("\n");						

						while(true) {
							System.out.println("");
							System.out.println("========================================");
							System.out.println("=> [PG] WHAT DO YOU WANT ?");
							System.out.println("----------------------------------------");
							System.out.println(" 0:RETRUN TO PREVIOUS");						
							System.out.println(" 1:SHOW Available PO");
							System.out.println(" 2:SIGN PAYMENT");
							System.out.println(" 3:SHOW MY SIGNED PAYMENT");						
							System.out.println("========================================");								

							System.out.println("\n");				 
							menu = cons.readLine();
							
							if(menu.equals("0")) { 
								break; 
							} else if(menu.equals("1")) {
								showAvailablePO(voucher_name);
							} else if(menu.equals("2")) {
								System.out.println("=> Enter the PO_GID");
								String poid=cons.readLine();						
								signPayment(voucher_name, pg_wallet_name, poid);
							} else if(menu.equals("3")) {
								showMyPayment(voucher_name, pg_wallet_name);;
							} else
								continue;
						}
					} else if(whoareyou.equals("2")) {
						
						System.out.println("=> Enter the YOUR NAME.");
						issuer_wallet_name=cons.readLine();
						System.out.println("\n");						

						while(true) {
							System.out.println("");
							System.out.println("========================================");
							System.out.println("=> [ISSUER] WHAT DO YOU WANT ?");
							System.out.println("----------------------------------------");
							System.out.println(" 0:RETRUN TO PREVIOUS");						
							System.out.println(" 1:SHOW Available PAYMENT");
							System.out.println(" 2:SIGN TRANSFER");
							System.out.println(" 3:SHOW MY SIGNED TRANSFER");						
							System.out.println("========================================");								

							System.out.println("\n");				 
							menu = cons.readLine();			
							
							if(menu.equals("0")) { 
								break; 
							} else if(menu.equals("1")) {
								showAvailablePAYMENT(voucher_name);
							} else if(menu.equals("2")) {
								System.out.println("=> Enter the PAYMENT_GID");
								String paymentid=cons.readLine();						
								signTransfer(voucher_name, issuer_wallet_name, paymentid);
							} else if(menu.equals("3")) {
								showAllTransfer(voucher_name);
							} else
								continue;
						}		
					} else if(whoareyou.equals("3")) {
						System.out.println("=> Enter the YOUR NAME.");
						escrower_wallet_name=cons.readLine();
						System.out.println("\n");						

						while(true) {
							System.out.println("");
							System.out.println("========================================");
							System.out.println("=> [ESCROW] WHAT DO YOU WANT ?");
							System.out.println("----------------------------------------");
							System.out.println(" 0:RETRUN TO PREVIOUS");						
							System.out.println(" 1:SHOW REQUESTED TRANSACTIONS");
							System.out.println(" 2:SIGN TRANSACTION");
							System.out.println(" 3:SHOW SIGNED TEANSACTIONS");						
							System.out.println("========================================");	

							System.out.println("\n");				 
							menu = cons.readLine();			
							
							if(menu.equals("0")) { 
								break; 
							} else if(menu.equals("1")) {
								showRequestedMultisig(voucher_name, escrower_wallet_name);
							} else if(menu.equals("2")) {
								System.out.println("Enter the TX_ID(TRANSACTION_GID) to Sign"); 					
								String tx_id=null;
								tx_id = cons.readLine();
								if(tx_id.equals("")) continue;									
								insertMultisig(ISSSUER_NAME,SERVICE_VOUCHER_MANAGEMENT, voucher_name,escrower_wallet_name, tx_id);
 
							} else if(menu.equals("3")) {
								showSignedMultiSig(voucher_name, escrower_wallet_name);
							} else 
								continue;
						}
					} else {
						System.out.println("");
						break;
					}
				}	
			}	
		}
	}
}