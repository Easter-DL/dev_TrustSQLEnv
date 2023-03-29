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

package org.rdlms.demo.pointsystem.anyone.escrow;


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

import java.util.Properties;

public class TokenWallet {
	
	static String DBURL=null;
	static String DBUSR=null;
	static String DBUSRPW=null;

	static String strDNOMAddress=null;
	static String	strDNOMPort=null;

	static final int ASYNC_MODE=0;
	static final int ASYNC_MODE_GET_STATUS=1;
	static final int SYNC_MODE=2;

	public static String searchBalance(Connection con, String token_name, String wallet_name) {
		WalletManager walletManager = new WalletManager();
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt;
		String strSenderPubKey;
		
		strSenderPubKey = walletManager.readWallet(wallet_name);
 	 	 
	 	long sender_sent_tot_amount=0;
		long receiver_recvd_tot_amount=0, receiver_recvd_ms_tot_amount=0;		 
		long holder_balance=0; 
 
		try {					
			// SEND_TOTAL		
			strStmt = "SELECT SUM(AMOUNT) FROM TRANSACTIONS_"+token_name+" WHERE SENDER_ACCOUNT ='"+strSenderPubKey+"';";
			System.out.println(strStmt);
			pstmt = con.prepareStatement(strStmt);			
			System.out.println("1");
			rs = pstmt.executeQuery();
			if (!rs.next()) {
				sender_sent_tot_amount = 0;
			} else {				
				sender_sent_tot_amount = rs.getLong(1); 				
			}	
			System.out.println("SENDER SENT TOT AMOUNT = "+sender_sent_tot_amount);

			// RECEIVE_TOTAL MULTI_SIG='N'		
			strStmt = "SELECT SUM(AMOUNT) FROM TRANSACTIONS_"+token_name+" WHERE RECEIVER_ACCOUNT ='"+strSenderPubKey+"' AND MULTISIG_YN='N';";
			System.out.println(strStmt);
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			if (!rs.next()) {
				receiver_recvd_tot_amount =0;
			} else {
				receiver_recvd_tot_amount = rs.getLong(1); 				
			}	
			System.out.println("SENDER RECEIVED MULTISIG=N TOT AMOUNT = "+receiver_recvd_tot_amount);
 		
			// RECEIVE_TOTAL MULTI_SIG='Y' AND SIGNED!
			strStmt = "SELECT SUM(a.AMOUNT) FROM TRANSACTIONS_"+token_name+" AS a, MULTISIG_"+token_name+" AS b WHERE RECEIVER_ACCOUNT ='"+strSenderPubKey+"' AND a.TRANSACTION_GID=b.TRANSACTION_GID";
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			if (!rs.next()) {
				receiver_recvd_ms_tot_amount =0;
			} else {
				receiver_recvd_ms_tot_amount = rs.getLong(1); 				
			}	
			System.out.println("SENDER RECEIVED MULTISIG=Y AND SIGNED TOT AMOUNT = "+receiver_recvd_ms_tot_amount);
 		
			holder_balance = (receiver_recvd_tot_amount+receiver_recvd_ms_tot_amount) - sender_sent_tot_amount;
			System.out.println("HOLDER BALANCE= "+holder_balance);
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

	public static void printAllTransactions(Connection con,String token_name, String wallet_name){
		WalletManager walletManager = new WalletManager();
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt;
		String strPubKey;

		strPubKey = walletManager.readWallet(wallet_name);
		try { 
			String transaction_gid, mint_gid, sender_account, receiver_account, multisig_yn, multisig_account, multisig_sign;
			String inout;
			long amount=0;
			transaction_gid=mint_gid=sender_account=receiver_account=multisig_yn=multisig_account=multisig_sign=null;
			// SEND_TOTAL		
			strStmt = "SELECT a.TRANSACTION_GID, a.MINT_GID, a.SENDER_ACCOUNT, a.AMOUNT, a.RECEIVER_ACCOUNT, a.MULTISIG_YN, a.MULTISIG_ACCOUNT, b.MULTISIG_SIGN ";
			strStmt+= "from TRANSACTIONS_"+token_name+" AS a LEFT JOIN MULTISIG_"+token_name+" AS b ON a.TRANSACTION_GID = b.TRANSACTION_GID ";
			strStmt+= "WHERE a.SENDER_ACCOUNT='"+strPubKey+"' OR a.RECEIVER_ACCOUNT='"+strPubKey+"' ORDER BY TRANSACTION_GID ASC;";

			//System.out.println("QUERY = "+strStmt);

			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println("");
			System.out.println(String.format("[%5s] %4s %4s %s %8s %1s %4s %4s","COUNT","TX_ID","MINT","INOUT","AMOUNT","M","M_ACCOUNT","M_SIGN"));
			while(rs.next()) {
				transaction_gid = Long.toString(rs.getLong("a.TRANSACTION_GID"));				
				mint_gid = rs.getString("a.MINT_GID");
				sender_account = rs.getString("a.SENDER_ACCOUNT");
				amount = rs.getLong("a.AMOUNT");
				receiver_account = rs.getString("a.RECEIVER_ACCOUNT");
				multisig_yn = rs.getString("a.MULTISIG_YN");
				multisig_account = rs.getString("a.MULTISIG_ACCOUNT");
				multisig_sign = rs.getString("b.MULTISIG_SIGN");
				if(sender_account.equals(strPubKey)) inout="SENT";
				else inout="RECV";
				System.out.println(String.format("[%5d] %4s. %4s. %s %8d %1s %4s. %4s.",count,transaction_gid,mint_gid,inout,amount, multisig_yn,multisig_account,multisig_sign));
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

	public static void printSignedMultiSig(Connection con, String token_name, String wallet_name) {
		WalletManager walletManager = new WalletManager();
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt;
		String strPubKey;

		strPubKey = walletManager.readWallet(wallet_name);
	 	try { 
			String transaction_gid, mint_gid, sender_account, receiver_account, multisig_yn, multisig_account, multisig_sign;
			String inout;
			long amount=0;
			transaction_gid=mint_gid=sender_account=receiver_account=multisig_yn=multisig_account=multisig_sign=null;
			
			strStmt = "SELECT * FROM TRANSACTIONS_"+token_name+" A INNER JOIN MULTISIG_"+token_name+" B WHERE A.TRANSACTION_GID = B.TRANSACTION_GID AND A.MULTISIG_ACCOUNT='"+strPubKey+"'";

			System.out.println("QUERY = "+strStmt);

			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println("");
			System.out.println(String.format("[%5s] %4s %4s %4s %4s %s %4s","COUNT","TX_ID","SENDER","AMOUNT","RECEIVER","M","M_ACCOUNT"));
			while(rs.next()) {
				transaction_gid = Long.toString(rs.getLong("TRANSACTION_GID"));				
				mint_gid = rs.getString("MINT_GID");
				sender_account = rs.getString("SENDER_ACCOUNT");
				amount = rs.getLong("AMOUNT");
				receiver_account = rs.getString("RECEIVER_ACCOUNT");
				multisig_yn = rs.getString("MULTISIG_YN");
				multisig_account = rs.getString("MULTISIG_ACCOUNT");
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
	

	public static void printRequestedMultisig(Connection con, String token_name, String wallet_name) {
		WalletManager walletManager = new WalletManager();
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt;
		String strPubKey;
		
	 	strPubKey = walletManager.readWallet(wallet_name);
	 	try { 
			String transaction_gid, mint_gid, sender_account, receiver_account, multisig_yn, multisig_account, multisig_sign;
			String inout;
			long amount=0;
			transaction_gid=mint_gid=sender_account=receiver_account=multisig_yn=multisig_account=multisig_sign=null;
			
			//strStmt = "SELECT A.* FROM TRANSACTIONS_"+token_name+" A, MULTISIG_"+token_name+" B WHERE A.TRANSACTION_GID!=B.TRANSACTION_GID AND A.MULTISIG_YN='Y' AND A.MULTISIG_ACCOUNT='"+strPubKey+"'";
			strStmt = "SELECT A.* FROM TRANSACTIONS_"+token_name+" A LEFT JOIN MULTISIG_"+token_name+" B ON A.TRANSACTION_GID=B.TRANSACTION_GID WHERE B.TRANSACTION_GID IS NULL AND A.MULTISIG_YN='Y' AND A.MULTISIG_ACCOUNT='"+strPubKey+"'";
			System.out.println("QUERY = "+strStmt);

			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			System.out.println("");
			System.out.println(String.format("[%5s] %4s %4s %4s %4s %s %4s","COUNT","TX_ID","SENDER","AMOUNT","RECEIVER","M","M_ACCOUNT"));
			while(rs.next()) {
				transaction_gid = Long.toString(rs.getLong("TRANSACTION_GID"));				
				mint_gid = rs.getString("MINT_GID");
				sender_account = rs.getString("SENDER_ACCOUNT");
				amount = rs.getLong("AMOUNT");
				receiver_account = rs.getString("RECEIVER_ACCOUNT");
				multisig_yn = rs.getString("MULTISIG_YN");
				multisig_account = rs.getString("MULTISIG_ACCOUNT");
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
 	
	public static int insertTransfer(DNOMConnector dnomConnector, String issuer_name, String service_name,String token_name, String wallet_name, String receiver_wallet_name, long amount, String multisig_yn, String multisig_wallet_name,int mode) {										
		WalletManager walletManager = new WalletManager();
		int iret=1;
		
		String strSenderPubKey;
		String strReceiverPubKey;
		String strMultisigPubKey;
		String str_Sender_sign; 
		String strStmt;

		SecureRandom random = new SecureRandom();

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
			// TRANSACTION_GID, TRANSACTION_NONCE,MINT_GID, SENDER_ACCOUNT, AMOUT, RECEIVER_ACCOUNT,MULTISIG_YN,SENDER_SIGN,TOSA_TIME,TOSA_SIGN
			String str = ArrayUtil.toHex(nonce)+strSenderPubKey+Long.toString(amount)+strReceiverPubKey+multisig_yn+strMultisigPubKey;
			str_Sender_sign = walletManager.secureSign(wallet_name, str);	 
			
			// OOPS, In case inserting MINTER_TX_ID VALUE NULL, mysqld go to die.
			//strStmt= "INSERT INTO TRANSACTIONS_"+token_name+" (TORDER_NUM,TORDER_NONCE,MINTER_TX_ID,SENDER,AMOUNT,RECEIVER,SENDER_SIGN,TORDER_TIME,TORDER_SIGN) ";
			//strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"',null,'"+strSenderPubKey+"',"+Long.toString(amount)+",'"+strReceiverPubKey+"','"+str_Sender_sign+"','2021-11-01 00:00:00',@SYNC_SIGN);";
			
			strStmt= "INSERT INTO TRANSACTIONS_"+token_name+" (TRANSACTION_GID,TRANSACTION_NONCE,SENDER_ACCOUNT,AMOUNT,RECEIVER_ACCOUNT,MULTISIG_YN,MULTISIG_ACCOUNT,SENDER_SIGN,TOSA_TIME,TOSA_SIGN) ";
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

	public static int insertMultisig(DNOMConnector dnomConnector, String issuer_name, String service_name,String token_name, String wallet_name, String tx_id){
		WalletManager walletManager = new WalletManager();
		int iret=1;

		String strMultisigPubKey;
		String str_Sender_sign; 
		String strStmt;

		SecureRandom random = new SecureRandom();

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
			
			strStmt= "INSERT INTO MULTISIG_"+token_name+" (MULTISIG_GID, MULTISIG_NONCE, TRANSACTION_GID,MULTISIG_ACCOUNT, MULTISIG_SIGN, TOSA_TIME,TOSA_SIGN) ";
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
		
	
	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws Exception {
		Console cons = System.console();				
		String issuer_name=null;
		String service_name=null;
		
		String token_name=null;
		String wallet_name=null;
		String receiver_wallet_name=null;
		String multisig_wallet_name=null;
		String amount;
		String balance;
		String multisig_yn;
		
		String menu=null;
		
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

		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return;
		}

		Connection con=null;						
		System.out.println("*======================================================*");
		System.out.println("|                                                      |"); 
		System.out.println("|             P2PCASHSYSTEM COIN(TOKEN) WALLET         |");
		System.out.println("|                   ANYONE & ESCROW                    |");
		System.out.println("|              -----------------------------           |");
		System.out.println("|                                                      |"); 
		System.out.println("|                      Copyright (c) 2020, TRUSTDB Inc.|");		
		System.out.println("*======================================================*\n");
		System.out.println("\n");

		System.out.println("=> Enter the ISSUER name ");
		issuer_name = cons.readLine();
		System.out.println("\n");
				
		System.out.println("=> Enter the SERIVCE name ");
		service_name = cons.readLine();
		System.out.println("\n");
							
		System.out.println("=> Enter the COIN(TOKEN,POINT) name ");
		token_name = cons.readLine();
		System.out.println("\n");
		System.out.println("=> Enter the Wallet name ");
		wallet_name = cons.readLine();
		
		String strDBURL= DBURL+"/"+service_name;
		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		WalletManager walletManager = new WalletManager();

		while(true) {			
			balance = searchBalance(con,token_name,wallet_name);
			System.out.println("");
			System.out.println("*=======================================================================*");
			System.out.println("* WALLET_NAME = "+ wallet_name); 
			System.out.println("* ADDRESS= "+ walletManager.readWallet(wallet_name)); 
			System.out.println("* BALANCE= "+ balance); 
			System.out.println("*=======================================================================*");
			System.out.println("");
			System.out.println("=> What do you want ?");
			System.out.println("---------------------");
			System.out.println(" 0:TRANSFER");
			System.out.println(" 1:PRINT ALL TRANSACTIONS");
			System.out.println(" 2:MULTISIG (ESCROW)");
			System.out.println(" 3:CHANGE WALLET");				
			System.out.println("\n");			
			menu = cons.readLine();
			System.out.println("\n");

			if(menu.equals("0")) {
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
				System.out.println("");					
				if(insertTransfer(dnomConnector,issuer_name,service_name, token_name, wallet_name, receiver_wallet_name, Long.parseLong(amount),multisig_yn,multisig_wallet_name,SYNC_MODE)==1) {													
					System.out.println("Sucessed!");					
				} else	{
					System.out.println("Failed");	
				}									
			} else if(menu.equals("1")) {
				System.out.println("Print All Transactions !"); 
				printAllTransactions(con, token_name, wallet_name);
			} else if(menu.equals("2")) {
				System.out.println("Here transactions you've completed multisig transactions!"); 
				printSignedMultiSig(con, token_name, wallet_name);
				System.out.println("");
				System.out.println("Here transactions you've requested completing multisig transactions!"); 
				printRequestedMultisig(con, token_name, wallet_name);
				System.out.println("");
				System.out.println("Enter the TX_ID(TRANSACTION_GID) to Sign"); 					
				String tx_id=null;
				tx_id = cons.readLine();
				if(tx_id.equals("")) continue;									
				insertMultisig(dnomConnector, issuer_name, service_name, token_name, wallet_name, tx_id);

			} else if(menu.equals("3")) {
				System.out.println("=> Enter the Wallet name");
				// If the wallet is not exist, create.
				wallet_name = cons.readLine();
				System.out.println("");				
			}				
			System.out.println("\n");	
		}							
	}
}