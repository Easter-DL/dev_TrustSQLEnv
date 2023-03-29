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

package org.rdlms.demo.pointsystem.membership;

import java.security.*;
import java.io.*;

import org.rdlms.crypto.ECDSA;
import org.rdlms.dnom.connector.DNOMConnector;
import org.rdlms.util.ArrayUtil;
import org.rdlms.wallet.WalletManager;

import java.util.Properties;

public class TokenIssuer {
	 
	static String strDNOMAddress=null;
	static String	strDNOMPort=null;
	static String strIssuer=null;
	static String strService=null;
	 
	public static int createIssuerTable(String issuer_name, String service_name, String token_name, String issuer_wallet_name, String tosa_wallet_name) {
		WalletManager walletManager= new WalletManager();
		String strStmt=null;
		String strTrgStmt=null;
		String strTransformed;
		
		String strIssuerPubKey;
		String strOrdererPubKey;
		
		
		strIssuerPubKey = walletManager.readWallet(issuer_wallet_name);
		strOrdererPubKey = walletManager.readWallet(tosa_wallet_name);
				
	 	String serviceName = null;
	 	byte[] baReturn= null;
		int errorCode=0;
			
		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return 1;
		}
	
		System.out.println("\n ============== "+issuer_name+"/"+service_name+"/"+token_name+"XXX create..");
		System.out.println("ISSUER");
		//String engine="MEMORY";
		String engine="InnoDB";
		
		strStmt= "CREATE TRUSTED,ORDERED TABLE MINT_"+token_name+" ( \n";
		strStmt += "TORDER_NUM BIGINT UNSIGNED NOT NULL, \n";										
		strStmt += "TORDER_NONCE VARCHAR(16) NULL, \n";
		strStmt += "MINTER_TX_ID VARCHAR(8), \n";						
		strStmt += "MINTED_AMOUNT BIGINT UNSIGNED NOT NULL, \n";
		strStmt += "MINTER_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "TORDER_TIME DATETIME NOT NULL, \n";
		strStmt += "TORDER_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(MINTER_TX_ID), \n";
		strStmt += "CONSTRAINT SIGNATURE(MINTER_SIGN) INPUTS(TORDER_NONCE,MINTER_TX_ID,MINTED_AMOUNT) VERIFY KEY TABLE_ISSUER, \n";		
		strStmt += "CONSTRAINT SIGNATURE(TORDER_SIGN) ORDERED(TORDER_NUM) TIMED(TORDER_TIME) \n";
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
				
		strStmt= "CREATE TRUSTED,ORDERED TABLE MEMBERSHIP_"+token_name+" ( \n";
		strStmt += "MEMBER_GID BIGINT UNSIGNED NOT NULL, \n";							
		strStmt += "MEMBER_NONCE VARCHAR(16) NOT NULL, \n";
		strStmt += "MEMBER_ID VARCHAR(40), \n";
		strStmt += "MEMBER_ACCOUNT VARCHAR(66) NOT NULL, \n";
		strStmt += "IVA_ACCOUNT VARCHAR(66) NOT NULL, \n";
		strStmt += "IVA_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "TOSA_TIME DATETIME NOT NULL, \n";
		strStmt += "TOSA_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(MEMBER_GID), \n";
		strStmt += "CONSTRAINT UNIQUE(MEMBER_NONCE), \n";
		strStmt += "CONSTRAINT UNIQUE(MEMBER_ID), \n";
		strStmt += "CONSTRAINT UNIQUE(MEMBER_ACCOUNT), \n";
		// TODO - IT's BETTER THAT the TORDER_NONCE is ONE OF INPUT FOR RECORD SIGN, BUT IN THIS CASE, THERE IS NO REGERATION ATTACK!
		strStmt += "CONSTRAINT SIGNATURE(IVA_SIGN) INPUTS(MEMBER_ID,MEMBER_ACCOUNT) VERIFY KEY(IVA_ACCOUNT), \n";
		strStmt += "CONSTRAINT SIGNATURE(TOSA_SIGN) ORDERED(MEMBER_GID) TIMED(TOSA_TIME) \n";
		strStmt += ") ENGINE=InnoDB\n";
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
		
		serviceName =issuer_name+"/"+service_name+"/"+"MEMBERSHIP_"+token_name;
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
		strTrgStmt += "\n";
		strTrgStmt += "-- VERIFICATION Rule \n";
		strTrgStmt += "-- #1 SENDER, RECEIVER should be different.\n"; 
		strTrgStmt += "IF NEW.SENDER = NEW.RECEIVER THEN \n";
		strTrgStmt += "	SET _msg = concat('TRANSACTIONS TABLE VERIFICATION ERROR: SENDER, RECEIVER SHOULD DIFFERENT!');\n";
		strTrgStmt += "signal sqlstate '45000' set message_text = _msg; \n";
		strTrgStmt += "END IF; \n";
		strTrgStmt += "\n";
		strTrgStmt += "IF NEW.MINTER_TX_ID IS NOT NULL THEN \n";
		strTrgStmt += "	-- #2MINT Transaction \n";
		strTrgStmt += "-- NEW.AMOUNT shoud be same MINT_xxxx.MINTED_AMOUNT in SAME KEY(MINTER_TX_ID)\n";
		strTrgStmt += "SELECT MINTED_AMOUNT INTO _minted_amount\n";
		strTrgStmt += "	FROM MINT_"+token_name+" \n";
		strTrgStmt += "	WHERE MINTER_TX_ID=NEW.MINTER_TX_ID;	\n";
		strTrgStmt += "	IF NEW.AMOUNT != _minted_amount THEN\n";
		strTrgStmt += "		SET _msg = concat('TRANSACTIONS TABLE VERIFICATION ERROR: In case of MINT transaction, AMOUNT SHOULD SAME WITH MINT_"+token_name+".MINTED_AMOUNT');\n";
		strTrgStmt += "signal sqlstate '45002' set message_text = _msg; \n";
		strTrgStmt += "	END IF;\n";
		strTrgStmt += "ELSE \n";
		strTrgStmt += "	-- #3 NORMAL Transaction \n";
		strTrgStmt += "	-- #AMOUNT SHOUD BE LESS THAN or EQUAL TO SENDER'S BALANCE. \n";
		strTrgStmt += "	\n";
		strTrgStmt += "	-- #3-1 SENDER's RECEIVE_TOTAL\n";
		strTrgStmt += "	SELECT IFNULL(SUM(AMOUNT),0) INTO _senders_receive_total\n";
		strTrgStmt += "	FROM TRANSACTIONS_"+token_name+" \n";
		strTrgStmt += "	WHERE RECEIVER=NEW.SENDER;	\n";
		strTrgStmt += "\n";
		strTrgStmt += "	-- #3-1 SENDER's SEND_TOTAL\n";
		strTrgStmt += "	SELECT IFNULL(SUM(AMOUNT),0) INTO _senders_send_total\n";
		strTrgStmt += "	FROM TRANSACTIONS_"+token_name+" \n";
		strTrgStmt += "	WHERE SENDER=NEW.SENDER;	\n";
		strTrgStmt += "\n";
		strTrgStmt += "	IF (NEW.AMOUNT > (_senders_receive_total-_senders_send_total)) THEN\n";
		strTrgStmt += "		SET _msg = concat('TRANSACTIONS TABLE VERIFICATION ERROR: AMOUNT IS GREATER THAN BALANCE'); \n";
		strTrgStmt += "	 	signal sqlstate '45003' set message_text = _msg; \n";
		strTrgStmt += "	END IF;\n";
		strTrgStmt += "END IF;\n";
		//strTrgStmt += "END $$\n";
		strTrgStmt += "END \n";
		
		strStmt= "CREATE TRUSTED,ORDERED TABLE TRANSACTIONS_"+token_name+" ( \n";
		strStmt += "TORDER_NUM BIGINT UNSIGNED NOT NULL, \n";										
		strStmt += "TORDER_NONCE VARCHAR(16) NOT NULL, \n";
		strStmt += "MINTER_TX_ID VARCHAR(8) NULL DEFAULT NULL, \n";
		strStmt += "SENDER VARCHAR(66) NOT NULL, \n";
		strStmt += "AMOUNT BIGINT UNSIGNED NOT NULL, \n";
		strStmt += "RECEIVER VARCHAR(66) NOT NULL, \n";
		strStmt += "SENDER_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "TORDER_TIME DATETIME NOT NULL, \n";
		strStmt += "TORDER_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(TORDER_NUM), \n";
		strStmt += "CONSTRAINT UNIQUE(TORDER_NONCE), \n";
		strStmt += "CONSTRAINT FOREIGN KEY(MINTER_TX_ID) REFERENCES MINT_"+token_name+"(MINTER_TX_ID) , \n";		
		strStmt += "CONSTRAINT FOREIGN KEY(SENDER) REFERENCES MEMBERSHIP_"+token_name+"(MEMBER_ACCOUNT) , \n";		
		strStmt += "CONSTRAINT FOREIGN KEY(RECEIVER) REFERENCES MEMBERSHIP_"+token_name+"(MEMBER_ACCOUNT) , \n";		
		strStmt += "CONSTRAINT SIGNATURE(SENDER_SIGN) INPUTS(TORDER_NONCE,MINTER_TX_ID,SENDER,AMOUNT,RECEIVER) VERIFY KEY(SENDER) , \n";
		strStmt += "CONSTRAINT SIGNATURE(TORDER_SIGN) ORDERED(TORDER_NUM) TIMED(TORDER_TIME) \n";		
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

		dnomConnector.close();		
		return 0;
	}

	public static void registVerifiedMembers(String issuer_name, String service_name, String token_name, String iva_wallet_name, String wallet_name) {

		WalletManager walletManager = new WalletManager();
		String strStmt;
		String strSignInput;
		String serviceName;	
		String strPubKey;
		String strMemberAccount;
		byte nonce[] = new byte[8];
		SecureRandom random = new SecureRandom();		

		strPubKey = walletManager.readWallet(iva_wallet_name);		
		strMemberAccount = walletManager.readWallet(wallet_name);		
	
		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return;
		}

		serviceName =issuer_name+"/"+service_name+"/"+"MEMBERSHIP_"+token_name;
		try {
			random.nextBytes(nonce);						
			strSignInput = wallet_name+strMemberAccount; // SIGN INPUTS(MEMBER_NONCE, MEMBER_ID, MEMBER_ACCOUNT)	
			strStmt= "INSERT INTO MEMBERSHIP_"+token_name+" (MEMBER_GID,MEMBER_NONCE,MEMBER_ID,MEMBER_ACCOUNT,IVA_ACCOUNT,IVA_SIGN,TOSA_TIME,TOSA_SIGN) VALUES ";
			strStmt += "(@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+wallet_name+"','"+strMemberAccount+"','"+strPubKey+"','"+walletManager.secureSign(iva_wallet_name, strSignInput)+"',@DATETIME,@SYNC_SIGN);";
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

	public static int insertUser(String issuer_name,String service_name, String token_name, String issuer_wallet_name, String user_wallet_name) {
		WalletManager walletManager = new WalletManager();
		String strStmt;
				
		SecureRandom random = new SecureRandom();
		byte baReturn[];
		int errorCode;

		String str_issuer_sign;
		String serviceName = null;

		walletManager.readWallet(issuer_wallet_name);
		String strPubKey = walletManager.readWallet(user_wallet_name);

		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return 1;
		}

		try {
			byte nonce[] = new byte[8];
			random.nextBytes(nonce);
			byte txid[] = new byte[4];
			random.nextBytes(txid);

			String str = ArrayUtil.toHex(nonce)+user_wallet_name+strPubKey;
			str_issuer_sign = walletManager.secureSign(issuer_wallet_name, str);
													
			strStmt= "INSERT INTO MEMBERSHIP_"+token_name+" (TORDER_NUM,TORDER_NONCE,MEMBER_ID,MEMBER_ACCOUNT,ISSUER_SIGN,TORDER_TIME,TORDER_SIGN) ";
			strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+user_wallet_name+"','"+strPubKey+"','"+str_issuer_sign+"',@DATETIME,@SYNC_SIGN);";
			System.out.println(strStmt);
			
			serviceName =issuer_name+"/"+service_name+"/"+"MEMBERSHIP_"+token_name;
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
		
		return 0; 
	}

	/*
		INSERT to MINT & TRANSACTIONS
	*/
	public static int mintCoin(String issuer_name, String service_name, String token_name, String issuer_wallet_name, String minter_wallet_name, int amount) { 	 
		WalletManager walletManager = new WalletManager();
		String strStmt;
				
		SecureRandom random = new SecureRandom();
		byte baReturn[];
		int errorCode;
		String strIssuerPubKey=null;
		String strReceiverPubKey=null;
		
		String str_issuer_sign;
		String serviceName = null;

		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return 1;
		}

		try {
			byte nonce[] = new byte[8];
			random.nextBytes(nonce);
			byte txid[] = new byte[4];
			random.nextBytes(txid);

			strIssuerPubKey = walletManager.readWallet(issuer_wallet_name);
			String str = ArrayUtil.toHex(nonce)+ArrayUtil.toHex(txid)+Integer.toString(amount);			
			str_issuer_sign = walletManager.secureSign(issuer_wallet_name, str);
													
			strStmt= "INSERT INTO MINT_"+token_name+" (TORDER_NUM,TORDER_NONCE,MINTER_TX_ID,MINTED_AMOUNT,MINTER_SIGN,TORDER_TIME,TORDER_SIGN) ";
			strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+ArrayUtil.toHex(txid)+"',"+Integer.toString(amount)+",'"+str_issuer_sign+"',@DATETIME,@SYNC_SIGN);";
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
			
			// TRANSCTIONS Table Fields
			// TORDER_NUM, TORDER_NONCE, MINTER_TX_ID, SENDER, AMOUNT, RECEIVER, SENDER_SIGN, TORDER_TIME, TORDER_SIGN
			
			// TORDER_NONCE
			random.nextBytes(nonce);
			//! MINTER_TX_ID is FOREIGN KEY of MINT Table's MINTER_TX_ID

			// SENDER is TABLE ISSUER(=MINTER)			
			// AMOUNT
			// RECEIVER is SENDER(=MINTER=ISSUER)
			// SENDER_SIGN (TORDER_NONCE,MINTER_TX_ID,SENDER,AMOUNT,RECEIVER)
			strReceiverPubKey = walletManager.readWallet(minter_wallet_name);

			str = ArrayUtil.toHex(nonce)+ArrayUtil.toHex(txid)+strIssuerPubKey+Integer.toString(amount)+strReceiverPubKey;
			str_issuer_sign = walletManager.secureSign(issuer_wallet_name, str);
													
			strStmt= "INSERT INTO TRANSACTIONS_"+token_name+" (TORDER_NUM,TORDER_NONCE,MINTER_TX_ID,SENDER,AMOUNT,RECEIVER,SENDER_SIGN,TORDER_TIME,TORDER_SIGN) ";
			strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+ArrayUtil.toHex(txid)+"','"+strIssuerPubKey+"',"+Integer.toString(amount)+",'"+strReceiverPubKey+"','"+str_issuer_sign+"',@DATETIME,@SYNC_SIGN);";
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
		String iva_wallet_name=null;
		String wallet_name=null;
		String menu=null;
		
		try { 
			Properties properties = new Properties();
			InputStream inputStream = new FileInputStream("trustsql.properties");
			properties.load(inputStream);
			inputStream.close();					
			strDNOMAddress = (String) properties.get("DNOMADDR");
			System.out.println("strDNOMAddress="+strDNOMAddress);
			strDNOMPort = (String) properties.get("DNOMPORT");					
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		System.out.println("*======================================================*");
		System.out.println("|                                                      |"); 
		System.out.println("|             P2PCASHSYSTEM COIN(TOKEN) ISSUER         |");
		System.out.println("|                         MEMBERSHIP                   |");
		System.out.println("|              -----------------------------           |");
		System.out.println("|                                                      |"); 
		System.out.println("|                      Copyright (c) 2020, TRUSTDB Inc.|");		
		System.out.println("*======================================================*\n");
		System.out.println("\n");
							
		while(true) {			
			System.out.println("Waht do you want ? ( 0: Deploy a Point  1: Regist User  2: Mint )");
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
				continue;
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

				System.out.println("=> Enter the IVA(Identity Verification Authority)'s wallet name  (goto back -> quit)");
				iva_wallet_name = cons.readLine();					
				System.out.println("\n");		
				if(iva_wallet_name.equals("quit")) break;		
				while(true) {				
					System.out.println("=> Enter new MEMBER's wallet name (goto back -> quit)");
					wallet_name = cons.readLine();
					if(wallet_name.equals("quit")) break;		
					registVerifiedMembers(issuer_name,service_name, token_name,iva_wallet_name, wallet_name);
					System.out.println("\n");		
				}
			} else if(menu.equals("2")) {
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
					System.out.println("=> How much token mint ?  (0 -> goto back)"); 
					String amount = cons.readLine();
					if(amount.equals("0")) break;
					System.out.println("\n");
					System.out.println("=> Enter the Minter's wallet name");
					wallet_name = cons.readLine();
					System.out.println("\n");
					mintCoin(issuer_name,service_name, token_name, issuer_wallet_name, wallet_name, Integer.parseInt(amount));
				}
			} else {

			}
		}		
	}
}