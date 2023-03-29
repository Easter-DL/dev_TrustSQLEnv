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

package org.rdlms.demo.pointsystem.anyone.fast;

import java.security.*;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

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

	public static String searchBalance(Connection con, String token_name, String strSenderPubKey) {
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt;
		 	 
	 	long sender_sent_tot_amount=0;
	 	long receiver_recvd_tot_amount=0;
	 	long holder_balance=0; 
	 
	 	try {					
			// SEND_TOTAL		
			strStmt = "SELECT SUM(AMOUNT) FROM TRANSACTIONS_"+token_name+" WHERE SENDER ='"+strSenderPubKey+"';";
			//System.out.println(strStmt);
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			if (!rs.next()) {
				sender_sent_tot_amount = 0;
			} else {				
				sender_sent_tot_amount = rs.getLong(1); 				
			}	
			//System.out.println("SENDER SENT TOT AMOUNT = "+sender_sent_tot_amount);
	
			// RECEIVE_TOTAL		
			strStmt = "SELECT SUM(AMOUNT) FROM TRANSACTIONS_"+token_name+" WHERE RECEIVER ='"+strSenderPubKey+"';";
			//System.out.println(strStmt);
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			if (!rs.next()) {
				receiver_recvd_tot_amount =0;
			} else {
				receiver_recvd_tot_amount = rs.getLong(1); 				
			}	
			System.out.println("SENDER RECEIVED TOT AMOUNT = "+receiver_recvd_tot_amount); 		
	 		
	 		holder_balance = receiver_recvd_tot_amount - sender_sent_tot_amount;
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

	public static void printAllTransactions(Connection con,String token_name, WalletManager walletManager, String wallet_name){
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt;
		String strPubKey;
		
	 	strPubKey = walletManager.readWallet(wallet_name);
	 	try { 
			String torder_num, torder_nonce,minter_tx_id,sender,receiver,sender_sign,torder_time,torder_sign;
			String inout;
			long amount=0;
			// SEND_TOTAL		
			strStmt = "SELECT * FROM TRANSACTIONS_"+token_name+" WHERE SENDER ='"+strPubKey+"' OR RECEIVER = '"+strPubKey+"' ORDER BY TORDER_NUM ASC;";
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			while(rs.next()) {
				torder_num = Long.toString(rs.getLong("TORDER_NUM"));
				torder_nonce = rs.getString("TORDER_NONCE");
				minter_tx_id = rs.getString("MINTER_TX_ID");
				sender = rs.getString("SENDER");
				amount = rs.getLong("AMOUNT");
				sender_sign = rs.getString("SENDER_SIGN");
				torder_time = rs.getString("TORDER_TIME");
				torder_sign = rs.getString("TORDER_SIGN");
				if(sender.equals(strPubKey)) inout="SENT";
				else inout="RECV";
				System.out.println(String.format("[%8d] %8s %s %8d %s ",count,torder_num,inout,amount,torder_time));
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
 	
	public static int insertTransfer(DNOMConnector dnomConnector, String issuer_name, String service_name,String token_name, WalletManager walletManager, String sender_name, String receiver_name, long amount, int mode) {									
		int iret=1;
		String strSenderPubKey;
		String strReceiverPubKey;
		String str_Sender_sign; 
		String strStmt;
		SecureRandom random = new SecureRandom();

	 	strSenderPubKey = walletManager.getAccount(sender_name);	 	
	 	strReceiverPubKey = walletManager.readWallet(receiver_name);	 	
 	 
		String serviceName = null;
		byte[] baReturn= null;
		int errorCode=0;
		try {
			byte nonce[] = new byte[8];
			random.nextBytes(nonce);
			byte txid[] = new byte[4];
			random.nextBytes(txid);

			// TRANSCTIONS Table Fields
			// TORDER_NUM, TORDER_NONCE, MINTER_TX_ID, SENDER, AMOUNT, RECEIVER, SENDER_SIGN, TORDER_TIME, TORDER_SIGN
		
			String str = ArrayUtil.toHex(nonce)+strSenderPubKey+Long.toString(amount)+strReceiverPubKey;			
			//str_Sender_sign = walletManager.secureSign(sender_name, str);
			str_Sender_sign = walletManager.sign(sender_name, str);
			
			// OOPS, In case inserting MINTER_TX_ID VALUE NULL, mysqld go to die.
			//strStmt= "INSERT INTO TRANSACTIONS_"+token_name+" (TORDER_NUM,TORDER_NONCE,MINTER_TX_ID,SENDER,AMOUNT,RECEIVER,SENDER_SIGN,TORDER_TIME,TORDER_SIGN) ";
			//strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"',null,'"+strSenderPubKey+"',"+Long.toString(amount)+",'"+strReceiverPubKey+"','"+str_Sender_sign+"','2021-11-01 00:00:00',@SYNC_SIGN);";
			
			strStmt= "INSERT INTO TRANSACTIONS_"+token_name+" (TORDER_NUM,TORDER_NONCE,SENDER,AMOUNT,RECEIVER,SENDER_SIGN,TORDER_TIME,TORDER_SIGN) ";
			strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+strSenderPubKey+"',"+Long.toString(amount)+",'"+strReceiverPubKey+"','"+str_Sender_sign+"',@DATETIME,@SYNC_SIGN);";
			//System.out.println(strStmt);
			
			serviceName =issuer_name+"/"+service_name+"/"+"TRANSACTIONS_"+token_name;
			//baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_ASYNC,serviceName,strStmt);
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
		WalletManager walletManager = new WalletManager();
				
		Scanner sc = new Scanner(System.in);
		Console cons = System.console();
		String issuer_name=null;
		String service_name=null;
		
		String token_name=null;
		String receiver_name=null;
		String wallet_name=null;
		String strPubKey=null;
		long amount;
		String balance;
		
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

		int mode=0;

		Connection con=null;
						
		while(true) {
			
			System.out.println("*======================================================*");
			System.out.println("|                                                      |"); 
			System.out.println("|             P2PCASHSYSTEM COIN(TOKEN) WALLET         |");
			System.out.println("|                           ANYONE                     |");
			System.out.println("|              -----------------------------           |");
			System.out.println("|                                                      |"); 
			System.out.println("|                      Copyright (c) 2020, TRUSTDB Inc.|");		
			System.out.println("*======================================================*\n");
			System.out.println("\n");
						
			System.out.println("=> Enter the Issuer name. (ex. www.rdlms.com) ");
			issuer_name = sc.nextLine();
			System.out.println("\n");
			
			System.out.println("=> Enter the Service name (=database name)");
			service_name = sc.nextLine();
			System.out.println("\n");
								
			System.out.println("=> Enter the Coin(TOKEN,POINT) name (ex. myBITCOIN, myETHREUM...) ");
			token_name = sc.nextLine();
			System.out.println("\n");

			System.out.println("=> Enter the User's wallet name");

			// If the wallet is not exist, create.	
			wallet_name = sc.nextLine();
			
			System.out.println("");
            System.out.println("Enter password of the wallet");                                         
            String password = new String(cons.readPassword());
                   
			strPubKey= walletManager.readWallet(wallet_name,password);

			String strDBURL= DBURL+"/"+service_name;
			try {
				con = DriverManager.getConnection(
					strDBURL,
					DBUSR,
					DBUSRPW);	
			} catch(Exception e) {
				e.printStackTrace();
			}

			while(true) {							
				balance = searchBalance(con,token_name,strPubKey);
				System.out.println("");
				System.out.println("*======================================================*");
				System.out.println("* WALLET_NAME = "+ wallet_name); 
				System.out.println("* ADDRESS= "+ strPubKey); 
				System.out.println("* BALANCE= "+ balance); 
				System.out.println("*======================================================*");
				System.out.println("");
				System.out.println("=> WHAT DO YOU WANT ?");
				System.out.println(" 0:TRANSFER");
				System.out.println(" 1:PRINT ALL TRANSACTIONS");
				System.out.println(" 2:Make Many TRASFER FOR TEST ");
				System.out.println(" 3:CHANGE WALLET");				
				System.out.println("\n");
				 
				mode = sc.nextInt();
				sc.nextLine(); 					
				System.out.println("\n");				 
				if(mode>2) {
					 continue;
				}
																							 
			 	if(mode==0) {										 
					System.out.println("=> Enter the Receiver's wallet name (exit -> quit)"); 
					receiver_name = sc.nextLine();						
					if(receiver_name.equals("quit")) return;
					if(receiver_name.equals("")) continue;									
					
					System.out.println("=> How much do you want to transfer ? ");
					amount = sc.nextLong();					
					sc.nextLine(); 					
					System.out.println("\n");	

					if(insertTransfer(dnomConnector,issuer_name,service_name, token_name, walletManager, wallet_name, receiver_name, amount,SYNC_MODE)==1) {
						System.out.println("Sucessed!");					
					} else	{
						System.out.println("Failed");	
					}					
					
				} else if(mode==1) {
					System.out.println("PIRNT ALL TRANSACTIONS !"); 
					printAllTransactions(con, token_name, walletManager, wallet_name);
				} else if(mode==2) {

					//System.out.println("=> Enter the wallet's password");

					// If the wallet is not exist, create.
					//String password  = sc.nextLine();
					
					strPubKey= walletManager.readWallet(wallet_name,password);
					System.out.println("will Send 1 token to user_1,user_2,user_3,user_4 and user_5 !"); 
					String wallets[] ={ "user_1","user_2", "user_3", "user_4", "user_5" };
					for(int i=0; i<100000; i++) {
						System.out.print(i);
						System.out.print("\r");
						if(insertTransfer(dnomConnector,issuer_name,service_name, token_name, walletManager, wallet_name, wallets[i%5], 1,SYNC_MODE)==1) {
							//System.out.println("Sucessed!");					
						} else	{
							System.out.println("Failed");	
							break;
						}					
					}	
					System.out.println("\n");	
				} else if(mode==3) {
					System.out.println("=> Enter the WALLET NAME");

					// If the wallet is not exist, create.
					wallet_name = sc.nextLine();			
					strPubKey = walletManager.readWallet(wallet_name);		
				}				
				System.out.println("\n");	
			}					
		}
	}
}