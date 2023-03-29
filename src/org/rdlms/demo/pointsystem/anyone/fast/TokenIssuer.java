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
	
	public static int createIssuerTable(String issuer_name, String service_name, String token_name, String isser_wallet_name, String orderer_wallet_name) {
 		String strStmt=null;
		String strTrgStmt=null;
		String strTransformed;
		
		String strIssuerPubKey;
		String strOrdererPubKey;
		
		WalletManager walletManager = new WalletManager();
		strIssuerPubKey = walletManager.readWallet(isser_wallet_name);
		strOrdererPubKey = walletManager.readWallet(orderer_wallet_name);
		
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

		String engine="InnoDB";

		// 1. Create MINT_xxxx Table		
		strStmt  = "CREATE TRUSTED,ORDERED TABLE MINT_"+token_name+" ( \n";
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
		strStmt += "TABLE_SCHEMA_SIGN='"+walletManager.secureSign(isser_wallet_name,strTransformed)+"'";

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
	
		// 2. generate Trgigger's sign	
		//strTrgStmt= "DELIMITER $$ \n";		
		String newline = System.getProperty("line.separator");
		strTrgStmt= "CREATE DEFINER=`"+issuer_name+"`@`%` TRIGGER VERIFICATION_TRANSACTIONS_"+token_name+newline;
		strTrgStmt += "BEFORE INSERT on TRANSACTIONS_"+token_name+" FOR EACH ROW "+newline;
		strTrgStmt += "BEGIN "+newline;
		strTrgStmt += "DECLARE _msg VARCHAR(256); "+newline;
		strTrgStmt += "DECLARE _minted_amount BIGINT DEFAULT 0; "+newline;
		strTrgStmt += "DECLARE _senders_balance BIGINT DEFAULT 0; "+newline;
		strTrgStmt += "DECLARE _receivers_balance BIGINT DEFAULT 0; "+newline;
		strTrgStmt += newline;
		strTrgStmt += "-- VERIFICATION Rule "+newline;
		strTrgStmt += "-- #1 SENDER, RECEIVER should be different."+newline;
		strTrgStmt += "IF NEW.SENDER = NEW.RECEIVER THEN "+newline;
		strTrgStmt += "	SET _msg = concat('TRANSACTIONS TABLE VERIFICATION ERROR: SENDER, RECEIVER SHOULD DIFFERENT!'); "+newline;
		strTrgStmt += "	signal sqlstate '45000' set message_text = _msg; \n";
		strTrgStmt += "END IF; "+newline;
		strTrgStmt += newline;
		strTrgStmt += "IF NEW.MINTER_TX_ID IS NOT NULL THEN "+newline;
		strTrgStmt += "	-- #2MINT Transaction "+newline;
		strTrgStmt += "	-- NEW.AMOUNT shoud be same MINT_xxxx.MINTED_AMOUNT in SAME KEY(MINTER_TX_ID)"+newline;
		strTrgStmt += "	SELECT MINTED_AMOUNT INTO _minted_amount "+newline;
		strTrgStmt += "	FROM MINT_"+token_name+newline;
		strTrgStmt += "	WHERE MINTER_TX_ID=NEW.MINTER_TX_ID;"+newline;
		strTrgStmt += "	IF NEW.AMOUNT != _minted_amount THEN"+newline;
		strTrgStmt += "		SET _msg = concat('TRANSACTIONS TABLE VERIFICATION ERROR: In case of MINT transaction, AMOUNT SHOULD SAME WITH MINT_"+token_name+".MINTED_AMOUNT');"+newline;
		strTrgStmt += "		signal sqlstate '45002' set message_text = _msg; "+newline;
		strTrgStmt += "	END IF;"+newline;
		strTrgStmt += "	SELECT IFNULL(BALANCE,0) INTO _receivers_balance FROM BALANCE_"+token_name+" WHERE ACCOUNT=NEW.RECEIVER;"+newline;
		strTrgStmt += "	SET _receivers_balance = _receivers_balance + NEW.AMOUNT; "+newline;
		strTrgStmt += "	INSERT INTO BALANCE_"+token_name+" VALUES (NEW.RECEIVER, _receivers_balance) ON DUPLICATE KEY UPDATE BALANCE=_receivers_balance; "+newline;		
		strTrgStmt += "ELSE "+newline;
		strTrgStmt += "	-- #3 NORMAL Transaction "+newline;
		strTrgStmt += "	-- #3-1 SENDER's Balance "+newline;
		strTrgStmt += "	SELECT IFNULL(BALANCE,0) INTO _senders_balance FROM BALANCE_"+token_name+" WHERE ACCOUNT=NEW.SENDER;"+newline;
		strTrgStmt += "	-- #3-1 SENDER's Balance "+newline;
		strTrgStmt += "	SELECT IFNULL(BALANCE,0) INTO _receivers_balance FROM BALANCE_"+token_name+" WHERE ACCOUNT=NEW.RECEIVER;"+newline;
		strTrgStmt += newline;
		strTrgStmt += "	-- #AMOUNT SHOUD BE LESS THAN or EQUAL TO SENDER'S BALANCE. "+newline;
		strTrgStmt += "	IF (NEW.AMOUNT > _senders_balance) THEN "+newline;
		strTrgStmt += "		SET _msg = concat('TRANSACTIONS TABLE VERIFICATION ERROR: AMOUNT IS GREATER THAN BALANCE'); "+newline;
		strTrgStmt += "	 	signal sqlstate '45003' set message_text = _msg; "+newline;
		strTrgStmt += "	ELSE "+newline;
		strTrgStmt += "		SET _senders_balance = _senders_balance - NEW.AMOUNT; "+newline;
		strTrgStmt += "		SET _receivers_balance = _receivers_balance + NEW.AMOUNT; "+newline;
		strTrgStmt += "		INSERT INTO BALANCE_"+token_name+" VALUES (NEW.SENDER, _senders_balance) ON DUPLICATE KEY UPDATE BALANCE=_senders_balance; "+newline;
		strTrgStmt += "		INSERT INTO BALANCE_"+token_name+" VALUES (NEW.RECEIVER, _receivers_balance) ON DUPLICATE KEY UPDATE BALANCE=_receivers_balance; "+newline;
		strTrgStmt += "	END IF; "+newline;
		strTrgStmt += "END IF;"+newline;
		//strStmt += "END $$\n";
		strTrgStmt += "END "+newline;

		// 3. Create TRANSACTIONX_xxxx Table	
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
		strStmt += "CONSTRAINT SIGNATURE(SENDER_SIGN) INPUTS(TORDER_NONCE,MINTER_TX_ID,SENDER,AMOUNT,RECEIVER) VERIFY KEY(SENDER) , \n";
		strStmt += "CONSTRAINT SIGNATURE(TORDER_SIGN) ORDERED(TORDER_NUM) TIMED(TORDER_TIME) \n";				
		strStmt += ") ENGINE="+engine+" \n";
		strStmt += "DSA_SCHEME='SECP256K1' \n";
		strStmt += "TABLE_ISSUER_PUB_KEY='"+strIssuerPubKey+"' \n";		
		strStmt += "TRUSTED_REFERENCE_PUB_KEY='"+strIssuerPubKey+"'\n";
		strStmt += "TOSA_PUB_KEY='"+strOrdererPubKey+"'\n";
		strStmt += "TOSA_MASTER_PUB_KEY='"+strOrdererPubKey+"'\n"; 		
		//strStmt += "TRIGGER_BEFORE_INSERT_SIGN='"+ECDSA.getSign(ECDSA.transform_for_sign(strTrgStmt),priv)+"'\n"; 			
		strStmt += "TRIGGER_BEFORE_INSERT_SIGN='"+walletManager.secureSign(isser_wallet_name, ECDSA.transform_for_sign(strTrgStmt))+"'\n";

		strTransformed = ECDSA.transform_for_sign(strStmt);
		strStmt += "TABLE_SCHEMA=\""+strTransformed+"\" ";
		//strStmt += "TABLE_SCHEMA_SIGN='"+ECDSA.getSign(strTransformed, priv)+"'";
		strStmt += "TABLE_SCHEMA_SIGN='"+walletManager.secureSign(isser_wallet_name,strTransformed)+"'";

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

		// 4. create BALANCE_xxxx Table	
		strStmt= "CREATE TABLE BALANCE_"+token_name+" ( \n";
		strStmt += "ACCOUNT varchar(66), \n";										
		strStmt += "BALANCE BIGINT UNSIGNED NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(ACCOUNT) \n";
		strStmt += ") ENGINE="+engine+" \n";

		System.out.println("-------------------");
		System.out.println("STRSTMT="+strStmt);
		System.out.println("-------------------");

		serviceName =issuer_name+"/"+service_name+"/"+"BALANCE_"+token_name;
		baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
		errorCode = ArrayUtil.BAToInt(baReturn);
		if(errorCode==0) {
			System.out.println("Success!");
		} else {
			System.out.println("Fail.. errorCode = "+errorCode);
			return errorCode;
		}

		// 5. create Trigger
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

	/*
		INSERT to MINT & TRANSACTIONS
	*/	
	public static int mintCoin(String issuer_name, String service_name, String token_name, String issuer_wallet_name, int amount, String minter_wallet_name) { 	 
		String strStmt;		
		SecureRandom random = new SecureRandom();
		String strReceiverPubKey, strIssuerPubKey;			
		byte baReturn[];
		int errorCode;

		String str_issuer_sign;
		String serviceName = null;

		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return 1;
		}

		WalletManager walletManager = new WalletManager();
		strIssuerPubKey = walletManager.readWallet(issuer_wallet_name);
		strReceiverPubKey = walletManager.readWallet(minter_wallet_name);

		try {
			byte nonce[] = new byte[8];
			random.nextBytes(nonce);
			byte txid[] = new byte[4];
			random.nextBytes(txid);

			String str = ArrayUtil.toHex(nonce)+ArrayUtil.toHex(txid)+Integer.toString(amount);
			str_issuer_sign = walletManager.secureSign(issuer_wallet_name,str);
													
			strStmt= "INSERT INTO MINT_"+token_name+" (TORDER_NUM,TORDER_NONCE,MINTER_TX_ID,MINTED_AMOUNT,MINTER_SIGN,TORDER_TIME,TORDER_SIGN) ";
			strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+ArrayUtil.toHex(txid)+"',"+Integer.toString(amount)+",'"+str_issuer_sign+"',@DATETIME,@SYNC_SIGN);";
						
			serviceName =issuer_name+"/"+service_name+"/MINT_"+token_name;
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
			str = ArrayUtil.toHex(nonce)+ArrayUtil.toHex(txid)+strIssuerPubKey+Integer.toString(amount)+strReceiverPubKey;			
			str_issuer_sign = walletManager.secureSign(issuer_wallet_name,str);
													
			strStmt= "INSERT INTO TRANSACTIONS_"+token_name+" (TORDER_NUM,TORDER_NONCE,MINTER_TX_ID,SENDER,AMOUNT,RECEIVER,SENDER_SIGN,TORDER_TIME,TORDER_SIGN) ";
			strStmt += "values (@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+ArrayUtil.toHex(txid)+"','"+strIssuerPubKey+"',"+Integer.toString(amount)+",'"+strReceiverPubKey+"','"+str_issuer_sign+"',@DATETIME,@SYNC_SIGN);";
			System.out.println(strStmt);
			
			serviceName =issuer_name+"/"+service_name+"/TRANSACTIONS_"+token_name;
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
		String minter_wallet_name=null;
		String flag=null;		
		String menu;

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
		
							
		while(true) {
			System.out.println("\n");
			System.out.println("*======================================================*");
			System.out.println("|                                                      |"); 
			System.out.println("|             P2PCASHSYSTEM COIN(TOKEN) ISSUER         |");
			System.out.println("|                           ANYONE                     |");
			System.out.println("|              -----------------------------           |");
			System.out.println("|                                                      |"); 
			System.out.println("|                      Copyright (c) 2020, TRUSTDB Inc.|");		
			System.out.println("*======================================================*\n");
			System.out.println("\n");
					
			System.out.println("Waht do you want to do ? ( 0: Deploy a Point  1: Mint )");
			menu = cons.readLine();            
			System.out.println("\n");
			if(menu.equals("0")) {						
				System.out.println("=> Enter the ISSUER name (ex. www.rdlms.com) ");
				issuer_name = cons.readLine();
				System.out.println("\n");
				
				System.out.println("=> Enter the SERIVCE name ");
				service_name = cons.readLine();
				System.out.println("\n");
									
				System.out.println("=> Enter the COIN(TOKEN,POINT) name (ex. myBITCOIN, myETHREUM...)");
				token_name = cons.readLine();
				System.out.println("\n");
														
				System.out.println("=> Enter the ISSUER's wallet name");
				issuer_wallet_name = cons.readLine();
				System.out.println("\n");
				
				System.out.println("=> Enter the TRUSTED ORDER-STAMPING AUTHORITY's wallet(pub) name");
				tosa_wallet_name = cons.readLine();
				System.out.println("\n");
			
				createIssuerTable(issuer_name,service_name, token_name, issuer_wallet_name, tosa_wallet_name);			
				System.out.println("\n=> "+token_name+" is created !!!"); 					
				continue;
			} else if(menu.equals("1")){
				if(issuer_name==null) {
					System.out.println("=> Enter the ISSUER name (ex. www.rdlms.com) ");
					issuer_name = cons.readLine();
					System.out.println("\n");					
					
					System.out.println("=> Enter the SERIVCE name (it should be same with database name)");
					service_name = cons.readLine();
					System.out.println("\n");
										
					System.out.println("=> Enter the COIN(TOKEN,POINT) name (ex. myBITCOIN, myETHREUM...)");
					token_name = cons.readLine();
					System.out.println("\n");

					System.out.println("=> Enter the ISSUER's wallet name");
					issuer_wallet_name = cons.readLine();
					System.out.println("\n");
				}	
				while(true) {
					System.out.println("=> How much token minted ?  (0 -> goto back)"); 
					String amount = cons.readLine();
					if(amount.equals("0")) break;
					System.out.println("\n");

					System.out.println("=> Enter the Minter's wallet name");
					minter_wallet_name = cons.readLine();
					System.out.println("\n");
					mintCoin(issuer_name,service_name, token_name, issuer_wallet_name, Integer.parseInt(amount), minter_wallet_name);
					System.out.println("\n");
				}
			} 							
		}
	}
}