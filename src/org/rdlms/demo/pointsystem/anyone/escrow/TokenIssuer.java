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

public class TokenIssuer {
	 
	static String DBURL=null;
	static String DBUSR=null;
	static String DBUSRPW=null;
	static String strDNOMAddress=null;
	static String	strDNOMPort=null;
	static final int ASYNC_MODE=0;
	static final int ASYNC_MODE_GET_STATUS=1;
	static final int SYNC_MODE=2;
	 
	public static int createIssuerTable(String issuer_name, String service_name, String token_name, String issuer_wallet_name, String tosa_wallet_name) {
		WalletManager walletManager = new WalletManager();
		String strStmt=null;
		String strTrgStmt=null;
		String strTransformed;
		
		String strIssuerPubKey;
		String strOrdererPubKey;
		
		strIssuerPubKey = walletManager.readWallet(issuer_wallet_name);
		//System.out.println("Issuer Pub Key = "+strIssuerPubKey);
		
		strOrdererPubKey = walletManager.readWallet(tosa_wallet_name);
		//System.out.println("Orderer Pub Key = "+strOrdererPubKey);
		
	 	String serviceName = null;
	 	byte[] baReturn= null;
		int errorCode=0;
				
		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return 1;
		}

		System.out.println("\n ============== "+issuer_name+"/"+service_name+"/"+token_name+"create..");
		System.out.println("ISSUER");
		//String engine="MEMORY";
		String engine="InnoDB";
		
		strStmt= "CREATE TRUSTED,ORDERED TABLE MINT_"+token_name+" ( \n";
		strStmt += "MINT_GID BIGINT UNSIGNED NOT NULL, \n";										
		strStmt += "MINT_NONCE VARCHAR(16) NULL, \n";		
		strStmt += "MINT_AMOUNT BIGINT UNSIGNED NOT NULL, \n";
		strStmt += "MINT_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "TOSA_TIME DATETIME NOT NULL, \n";
		strStmt += "TOSA_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(MINT_GID), \n";
		strStmt += "CONSTRAINT UNIQUE (MINT_NONCE), \n";
		strStmt += "CONSTRAINT SIGNATURE(MINT_SIGN) INPUTS(MINT_NONCE,MINT_AMOUNT) VERIFY KEY TABLE_ISSUER, \n";		
		strStmt += "CONSTRAINT SIGNATURE(TOSA_SIGN) ORDERED(MINT_GID) TIMED(TOSA_TIME) \n";
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
		
		serviceName =issuer_name+"/"+service_name+"/"+"MINT_"+token_name;
		baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
		errorCode = ArrayUtil.BAToInt(baReturn);
		if(errorCode==0) {
			System.out.println("Success!");
		} else {
			System.out.println("Fail.. errorCode = "+errorCode);
			return errorCode;
		}
		
		//strTrgStmt= "DELIMITER $$ \n";
		strTrgStmt= "CREATE DEFINER=`"+issuer_name+"`@`%` TRIGGER VERIFICATION_TRANSACTIONS_"+token_name+" \n";
		strTrgStmt += "BEFORE INSERT on TRANSACTIONS_"+token_name+" FOR EACH ROW \n";		
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
		strTrgStmt += "IF NEW.SENDER_ACCOUNT = NEW.RECEIVER_ACCOUNT THEN \n";
		strTrgStmt += "SET _msg = concat('TRANSACTIONS TABLE VERIFICATION ERROR: SENDER, RECEIVER SHOULD DIFFERENT!'); \n";
		strTrgStmt += "signal sqlstate '45000' set message_text = _msg; \n";
		strTrgStmt += "END IF; \n";
		strTrgStmt += " \n";
		strTrgStmt += "IF NEW.MINT_GID IS NOT NULL THEN \n";
		strTrgStmt += "-- #2MINT Transaction \n";
		strTrgStmt += "-- NEW.AMOUNT shoud be same MINT_xxxx.MINTED_AMOUNT in SAME KEY(MINTER_TX_ID) \n";
		strTrgStmt += "SELECT MINT_AMOUNT INTO _minted_amount FROM MINT_"+token_name+" WHERE MINT_GID=NEW.MINT_GID; \n";
		strTrgStmt += " \n";
		strTrgStmt += "IF NEW.AMOUNT != _minted_amount THEN \n";
		strTrgStmt += "SET _msg = concat('TRANSACTIONS TABLE VERIFICATION ERROR: In case of MINT transaction, AMOUNT SHOULD SAME WITH MINT_"+token_name+".MINT_AMOUNT'); \n";
		strTrgStmt += "signal sqlstate '45002' set message_text = _msg; \n";
		strTrgStmt += "END IF; \n";
		strTrgStmt += "ELSE \n";
		strTrgStmt += "-- #3 NORMAL Transaction \n";
		strTrgStmt += "-- #AMOUNT SHOUD BE LESS THAN or EQUAL TO SENDER'S BALANCE. p2p \n";
		strTrgStmt += " \n";
		strTrgStmt += "-- #3-1 SENDER's RECEIVE_TOTAL \n";
		strTrgStmt += "SELECT IFNULL(SUM(AMOUNT),0) INTO _senders_receive_total \n";
		strTrgStmt += "FROM TRANSACTIONS_"+token_name+" \n";
		strTrgStmt += "WHERE RECEIVER_ACCOUNT=NEW.SENDER_ACCOUNT AND MULTISIG_YN='N'; \n";
		strTrgStmt += " \n";
		strTrgStmt += "-- #3-2 SENDER's SEND_TOTAL \n";
		strTrgStmt += "SELECT IFNULL(SUM(AMOUNT),0) INTO _senders_send_total \n";
		strTrgStmt += "FROM TRANSACTIONS_"+token_name+" \n";
		strTrgStmt += "WHERE SENDER_ACCOUNT=NEW.SENDER_ACCOUNT AND MULTISIG_YN='N'; \n";
		strTrgStmt += " \n";
		strTrgStmt += "-- #4-1 MULTISIG SENDER's RECEIVE_TOTAL \n";
		strTrgStmt += "SELECT IFNULL(SUM(a.AMOUNT),0) INTO _multis_senders_receive_total \n";
		strTrgStmt += "FROM TRANSACTIONS_"+token_name+" AS a, MULTISIG_"+token_name+" AS b \n";
		strTrgStmt += "WHERE a.RECEIVER_ACCOUNT=NEW.SENDER_ACCOUNT AND a.MULTISIG_YN='Y' AND b.TRANSACTION_GID=NEW.TRANSACTION_GID; \n";
		strTrgStmt += " \n";
		strTrgStmt += "-- #4-2 MULTISIG SENDER's SEND_TOTAL \n";
		strTrgStmt += "SELECT IFNULL(SUM(a.AMOUNT),0) INTO _multis_senders_send_total \n";
		strTrgStmt += "FROM TRANSACTIONS_"+token_name+" AS a, MULTISIG_"+token_name+" AS b \n";
		//strTrgStmt += "WHERE a.SENDER_ACCOUNT=NEW.SENDER_ACCOUNT AND a.MULTISIG_YN='Y' AND b.TRANSACTION_GID=NEW.TRANSACTION_GID; \n";
		strTrgStmt += "WHERE a.SENDER_ACCOUNT=NEW.SENDER_ACCOUNT AND a.MULTISIG_YN='Y'; \n";
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


		strStmt= "CREATE TRUSTED,ORDERED TABLE TRANSACTIONS_"+token_name+" ( \n";
		strStmt += "TRANSACTION_GID BIGINT UNSIGNED NOT NULL, \n";										
		strStmt += "TRANSACTION_NONCE VARCHAR(16) NOT NULL, \n";		
		strStmt += "MINT_GID BIGINT UNSIGNED, \n";		
		strStmt += "SENDER_ACCOUNT VARCHAR(66) NOT NULL, \n";
		strStmt += "AMOUNT BIGINT UNSIGNED NOT NULL, \n";
		strStmt += "RECEIVER_ACCOUNT VARCHAR(66) NOT NULL, \n";
		strStmt += "MULTISIG_YN ENUM('N','Y') DEFAULT 'N', \n";
		strStmt += "MULTISIG_ACCOUNT VARCHAR(66) NOT NULL, \n";
		strStmt += "SENDER_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "TOSA_TIME DATETIME NOT NULL, \n";
		strStmt += "TOSA_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(TRANSACTION_GID), \n";
		strStmt += "CONSTRAINT UNIQUE(TRANSACTION_NONCE), \n";
		strStmt += "CONSTRAINT FOREIGN KEY(MINT_GID) REFERENCES MINT_"+token_name+"(MINT_GID), \n";		
		strStmt += "CONSTRAINT SIGNATURE(SENDER_SIGN) INPUTS(TRANSACTION_NONCE,MINT_GID,SENDER_ACCOUNT,AMOUNT,RECEIVER_ACCOUNT,MULTISIG_YN,MULTISIG_ACCOUNT) VERIFY KEY(SENDER_ACCOUNT), \n";
		strStmt += "CONSTRAINT SIGNATURE(TOSA_SIGN) ORDERED(TRANSACTION_GID) TIMED(TOSA_TIME) \n";
		strStmt += ") ENGINE="+engine+" \n";
		strStmt += "DSA_SCHEME='SECP256K1' \n";
		strStmt += "TABLE_ISSUER_PUB_KEY='"+strIssuerPubKey+"' \n";		
		strStmt += "TRUSTED_REFERENCE_PUB_KEY='"+strIssuerPubKey+"'\n";
		strStmt += "TOSA_PUB_KEY='"+strOrdererPubKey+"'\n";
		strStmt += "TOSA_MASTER_PUB_KEY='"+strOrdererPubKey+"'\n";
		strStmt += "TRIGGER_BEFORE_INSERT_SIGN='"+walletManager.secureSign(issuer_wallet_name, ECDSA.transform_for_sign(strTrgStmt))+"'\n";
		strTransformed = ECDSA.transform_for_sign(strStmt);
		strStmt += "TABLE_SCHEMA=\""+strTransformed+"\" ";
		strStmt += "TABLE_SCHEMA_SIGN='"+walletManager.secureSign(issuer_wallet_name, strTransformed)+"'";

		System.out.println("-------------------");
		System.out.println("STRSTMT="+strStmt);
		System.out.println("-------------------");
		
		serviceName =issuer_name+"/"+service_name+"/"+"TRANSACTIONS_"+token_name;
		baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
		errorCode = ArrayUtil.BAToInt(baReturn);
		if(errorCode==0) {
			System.out.println("Success!");
		} else {
			System.out.println("Fail.. errorCode = "+errorCode);
			return errorCode;
		}

		

		System.out.println("-------------------");
		System.out.println("STRSTMT="+strTrgStmt);
		System.out.println("-------------------");
			
		serviceName =issuer_name+"/"+service_name+"/"+"TRANSACTIONS_"+token_name;
		baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strTrgStmt);
		errorCode = ArrayUtil.BAToInt(baReturn);
		if(errorCode==0) {
			System.out.println("Success!");
		} else {
			System.out.println("Fail.. errorCode = "+errorCode);
			return errorCode;
		}
		
		strStmt= "CREATE TRUSTED,ORDERED TABLE MULTISIG_"+token_name+" ( \n";
		strStmt += "MULTISIG_GID BIGINT UNSIGNED NOT NULL, \n";										
		strStmt += "MULTISIG_NONCE VARCHAR(16) NOT NULL, \n";		
		strStmt += "TRANSACTION_GID BIGINT UNSIGNED, \n";		
		strStmt += "MULTISIG_ACCOUNT VARCHAR(66) NOT NULL, \n";
		strStmt += "MULTISIG_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "TOSA_TIME DATETIME NOT NULL, \n";
		strStmt += "TOSA_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(MULTISIG_GID), \n";
		strStmt += "CONSTRAINT UNIQUE(MULTISIG_NONCE), \n";
		strStmt += "CONSTRAINT FOREIGN KEY(TRANSACTION_GID) REFERENCES TRANSACTIONS_"+token_name+"(TRANSACTION_GID) , \n";		
		strStmt += "CONSTRAINT SIGNATURE(MULTISIG_SIGN) INPUTS(MULTISIG_NONCE,TRANSACTION_GID) VERIFY KEY(MULTISIG_ACCOUNT) , \n";
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
		
		serviceName =issuer_name+"/"+service_name+"/"+"MULTISIG_"+token_name;
		baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
		errorCode = ArrayUtil.BAToInt(baReturn);
		if(errorCode==0) {
			System.out.println("Success!");
		} else {
			System.out.println("Fail.. errorCode = "+errorCode);
			return errorCode;
		}
		
		
		dnomConnector.close();
		return 0;
	}

	/*
		INSERT to MINT & TRANSACTIONS
	*/
	public static int mintCoin(String issuer_name, String service_name, String token_name, String issuer_wallet_name, String minter_wallet_name, int amount) { 	 
		WalletManager walletManager = new WalletManager();
		Connection con = null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt;
		
		SecureRandom random = new SecureRandom();
		String strIssuerPubKey=null;
		String strReceiverPubKey=null;
		byte baReturn[];
		int errorCode;

		String str_issuer_sign;
		String serviceName = null;

		
		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return 1;
		}

		String strDBURL= DBURL+"/"+service_name;
		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}

		strIssuerPubKey = walletManager.readWallet(issuer_wallet_name);
		strReceiverPubKey =  walletManager.readWallet(minter_wallet_name);	
		
		try {
			byte nonce[] = new byte[8];
			random.nextBytes(nonce);
			
			String str = ArrayUtil.toHex(nonce)+Integer.toString(amount);
			str_issuer_sign = walletManager.secureSign(issuer_wallet_name, str);
													
			strStmt= "INSERT INTO MINT_"+token_name+" (MINT_GID,MINT_NONCE,MINT_AMOUNT,MINT_SIGN,TOSA_TIME,TOSA_SIGN) ";
			strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"',"+Integer.toString(amount)+",'"+str_issuer_sign+"',@DATETIME,@SYNC_SIGN);";
			System.out.println(strStmt);
			
			serviceName =issuer_name+"/"+service_name+"/"+"MINT_"+token_name;
			baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			errorCode = ArrayUtil.BAToInt(baReturn);
			if(errorCode==0) {
			//	System.out.println("Success!");
			} else {
				System.out.println("Fail.. errorCode = "+errorCode);
				return errorCode;
			}
			
			String mint_gid=null;
			// SEND_TOTAL		
			strStmt = "SELECT * FROM MINT_"+token_name+" WHERE MINT_NONCE ='"+ArrayUtil.toHex(nonce)+"';";
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			int count=1;
			while(rs.next()) {
				mint_gid = Long.toString(rs.getLong("MINT_GID"));
			}			

			// TRANSCTIONS Table Fields
			// TRANSACTION_GID, TRANSACTION_NONCE,MINT_GID, SENDER_ACCOUNT, AMOUT, RECEIVER_ACCOUNT,MULTISIG_YN,SENDER_SIGN,TOSA_TIME,TOSA_SIGN
			
			// TORDER_NONCE
			random.nextBytes(nonce);
			//! MINT_IGD is FOREIGN KEY of MINT Table's MINT_GID

			// SENDER is TABLE ISSUER(=MINTER)			
			// AMOUNT
			// RECEIVER is SENDER(=MINTER=ISSUER)
			// SENDER_SIGN (TRANSACTION_NONCE,MINT_GID,SENDER_ACCOUNT,AMOUNT,RECEIVER_ACCOUNT)
			str = ArrayUtil.toHex(nonce)+mint_gid+strIssuerPubKey+Integer.toString(amount)+strReceiverPubKey+"N";
			str_issuer_sign = walletManager.secureSign(issuer_wallet_name, str);
													
			strStmt= "INSERT INTO TRANSACTIONS_"+token_name+" (TRANSACTION_GID,TRANSACTION_NONCE,MINT_GID,SENDER_ACCOUNT,AMOUNT,RECEIVER_ACCOUNT,MULTISIG_YN,MULTISIG_ACCOUNT,SENDER_SIGN,TOSA_TIME,TOSA_SIGN) ";
			strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+mint_gid+"','"+strIssuerPubKey+"',"+Integer.toString(amount)+",'"+strReceiverPubKey+"','N','','"+str_issuer_sign+"',@DATETIME,@SYNC_SIGN);";
			System.out.println(strStmt);
			
			serviceName =issuer_name+"/"+service_name+"/"+"TRANSACTIONS_"+token_name;
			baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			errorCode = ArrayUtil.BAToInt(baReturn);
			if(errorCode==0) {
			//	System.out.println("Success!");
			} else {
				System.out.println("Fail.. errorCode = "+errorCode);
				return errorCode;
			}	
			dnomConnector.close();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close(); 
				if(pstmt != null) pstmt.close(); 
				con.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}			
		return 0; 
	}


	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws Exception {
		Console cons = System.console();		
		String issuer_name=null;
		String service_name=null;
		String token_name=null;
		String issuer_wallet_name=null;
		String tosa_wallet_name=null;
		String wallet_name=null;
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
		
		System.out.println("*======================================================*");
		System.out.println("|                                                      |"); 
		System.out.println("|                P2PCASHSYSTEM COIN ISSUER             |");
		System.out.println("|                   ANYONE & ESCROW                    |");
		System.out.println("|              -----------------------------           |");
		System.out.println("|                                                      |"); 
		System.out.println("|                      Copyright (c) 2020, TRUSTDB Inc.|");		
		System.out.println("*======================================================*\n");
						
		while(true) {
			System.out.println("Waht do you want ? ( 0: Deploy a Point  1: Mint )");
			menu = cons.readLine();            
			System.out.println("\n");
			if(menu.equals("0")) {						
				System.out.println("=> Enter the ISSUER name ");
				issuer_name = cons.readLine();
				System.out.println("\n");
				
				System.out.println("=> Enter the SERIVCE name ");
				service_name = cons.readLine();
				System.out.println("\n");
									
				System.out.println("=> Enter the COIN(TOKEN,POINT) name ");
				token_name = cons.readLine();
				System.out.println("\n");
														
				System.out.println("=> Enter the ISSUER's wallet name");
				issuer_wallet_name = cons.readLine();
				System.out.println("\n");
				
				System.out.println("=> Enter the TOSA's wallet name");
				tosa_wallet_name = cons.readLine();
				System.out.println("\n");								
				createIssuerTable(issuer_name,service_name, token_name, issuer_wallet_name, tosa_wallet_name);			
				System.out.println("");
			} else if(menu.equals("1")) {
				if(issuer_name==null) {
					System.out.println("=> Enter the ISSUER name ");
					issuer_name = cons.readLine();
					System.out.println("\n");
					
					System.out.println("=> Enter the SERIVCE name ");
					service_name = cons.readLine();
					System.out.println("\n");
										
					System.out.println("=> Enter the COIN(TOKEN,POINT) name ");
					token_name = cons.readLine();
					System.out.println("\n");
															
					System.out.println("=> Enter the ISSUER's wallet name");
					issuer_wallet_name = cons.readLine();
					System.out.println("\n");
				}
				while(true) {					
					System.out.println("\n"); 
					System.out.println("=> Do you want to mint coin? How much ?  (0 ->goto back)"); 
					String amount = cons.readLine();
					if(amount.equals("0")) break;
					System.out.println("\n");								

					System.out.println("=> Enter MINTER's wallet name "); 
					wallet_name = cons.readLine();					
					mintCoin(issuer_name,service_name, token_name, issuer_wallet_name, wallet_name,Integer.parseInt(amount));			
					System.out.println("\n");								
				}
			} else {				
			}				
		}
	}
}