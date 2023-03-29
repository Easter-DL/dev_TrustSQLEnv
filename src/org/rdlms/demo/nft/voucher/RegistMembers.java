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

import org.rdlms.crypto.ECDSA;
import org.rdlms.dnom.connector.DNOMConnector;
import org.rdlms.util.ArrayUtil;
import org.rdlms.wallet.WalletManager;

import java.util.Properties;

public class RegistMembers {
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
		
	public static void registVerifiedMembers(String iva_wallet_name, String wallet_name) {
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

		serviceName =ISSSUER_NAME+"/"+SERVICE_MEMBER_MANAGEMENT+"/VERIFIED_MEMBERS";

		try {
			random.nextBytes(nonce);						
			strSignInput = wallet_name+strMemberAccount; // SIGN INPUTS(MEMBER_NONCE, MEMBER_ID, MEMBER_ACCOUNT)	
			strStmt= "INSERT INTO VERIFIED_MEMBERS (MEMBER_GID,MEMBER_NONCE,MEMBER_ID,MEMBER_ACCOUNT,IVA_ACCOUNT,IVA_SIGN,TOSA_TIME,TOSA_SIGN) VALUES ";
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

	public static void createMemberTable(String issuer_wallet_name, String tosa_wallet_name) {		
		WalletManager walletManager = new WalletManager();
		String strStmt;
		String strTransformed;
		String serviceName;
		
		String strIssuerPubKey;
		String strOrdererPubKey;
		String strTrustChainPubKey;
		
		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return ;
		}

		strIssuerPubKey = walletManager.readWallet(issuer_wallet_name);		
		strTrustChainPubKey = strIssuerPubKey;
		strOrdererPubKey = walletManager.readWallet(tosa_wallet_name);		
						
		serviceName =ISSSUER_NAME+"/"+SERVICE_MEMBER_MANAGEMENT+"/VERIFIED_MEMBERS";
		
		strStmt= "CREATE TRUSTED,ORDERED TABLE VERIFIED_MEMBERS ( \n";
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
		strStmt += "TRUSTED_REFERENCE_PUB_KEY='"+strTrustChainPubKey+"'\n";
		strStmt += "TOSA_PUB_KEY='"+strOrdererPubKey+"'\n";
		strStmt += "TOSA_MASTER_PUB_KEY='"+strOrdererPubKey+"'\n"; 			
		strTransformed = ECDSA.transform_for_sign(strStmt);
		strStmt += "TABLE_SCHEMA=\""+strTransformed+"\" ";
		strStmt += "TABLE_SCHEMA_SIGN='"+walletManager.secureSign(issuer_wallet_name, strTransformed)+"'";

		byte[] baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_ASYNC,serviceName,strStmt);
		int errorCode = ArrayUtil.BAToInt(baReturn);
		if(errorCode==0) {
			System.out.println("Success!");
		} else {
			System.out.println("Fail.. errorCode = "+errorCode);
		}
		dnomConnector.close(); 
	}
		
		
	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws Exception {
		Console cons = System.console();				
		String issuer_wallet_name;
		String tosa_wallet_name;
		String iva_wallet_name;
		String wallet_name;
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

		System.out.println("\n");
		System.out.println("*======================================================*");
		System.out.println("|                                                      |"); 
		System.out.println("|                     NFT VOUCHER MEMBER               |");
		System.out.println("|                       REGISTRATION                   |");
		System.out.println("|              -----------------------------           |");
		System.out.println("|                                                      |"); 
		System.out.println("|                      Copyright (c) 2020, TRUSTDB Inc.|");		
		System.out.println("*======================================================*\n");
		System.out.println("\n");
		
		while(true) {
			System.out.println("Waht do you want ? ( 0: Deploy a MEMBERSHIP 1: Regist Member)");
			menu = cons.readLine();            
			System.out.println("\n");
			if(menu.equals("0")) {						
				System.out.println("=> Enter the ISSUER name ");
				ISSSUER_NAME = cons.readLine();
				System.out.println("\n");
								
				System.out.println("=> Enter the SERIVCE name ");
				SERVICE_MEMBER_MANAGEMENT = cons.readLine();
				System.out.println("\n");

				System.out.println("=> Enter the ISSUER's wallet name");
				issuer_wallet_name = cons.readLine();
				System.out.println("\n");
				
				System.out.println("=> Enter the TOSA's wallet name");
				tosa_wallet_name = cons.readLine();
				System.out.println("\n");								
				createMemberTable(issuer_wallet_name, tosa_wallet_name);

			} else if(menu.equals("1"))	{				
				System.out.println("=> Enter the IVA(Identity Verification Authority)'s wallet name  (goto back -> quit)");
				iva_wallet_name = cons.readLine();					
				System.out.println("\n");		
				if(iva_wallet_name.equals("quit")) break;		
				while(true) {				
					System.out.println("=> Enter new MEMBER's wallet name (goto back -> quit)");
					wallet_name = cons.readLine();
					if(wallet_name.equals("quit")) break;		
					registVerifiedMembers(iva_wallet_name, wallet_name);
					System.out.println("\n");		
				}
			}
		}
	}
}