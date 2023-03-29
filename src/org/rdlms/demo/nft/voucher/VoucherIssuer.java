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

public class VoucherIssuer {
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
	
	public static void createVoucherTable(String issuer_wallet_name, String tosa_wallet_name, String voucher_name) {
		WalletManager walletManager = new WalletManager();
		String strStmt;
		String strTransformed;
		String serviceName;	
		String strIssuerPubKey;
		String strChainPubKey;
		String strOrdererPubKey;
		
		strIssuerPubKey = walletManager.readWallet(issuer_wallet_name);
		strChainPubKey = strIssuerPubKey;
		strOrdererPubKey = walletManager.readWallet(tosa_wallet_name);

		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return;
		}

		serviceName =ISSSUER_NAME+"/"+SERVICE_VOUCHER_MANAGEMENT+"/VOUCHER_"+voucher_name;

		strStmt= "CREATE TRUSTED,ORDERED TABLE VOUCHER_"+voucher_name+" ( \n";	
		strStmt += "VOUCHER_GID BIGINT UNSIGNED, \n";
		strStmt += "VOUCHER_NONCE VARCHAR(16) NOT NULL, \n";	
		strStmt += "VOUCHER_NAME VARCHAR(40) , \n";							
		strStmt += "VOUCHER_NO VARCHAR(12) , \n";							
		strStmt += "VOUCHER_PVALUE BIGINT UNSIGNED NOT NULL, \n";		
		strStmt += "ISSUER_SIGN VARCHAR(160) NOT NULL, \n";		
		strStmt += "TOSA_TIME DATETIME NOT NULL, \n";		
		strStmt += "TOSA_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(VOUCHER_GID), \n";
		strStmt += "CONSTRAINT UNIQUE (VOUCHER_NONCE), \n";
		strStmt += "CONSTRAINT UNIQUE (VOUCHER_NAME,VOUCHER_NO), \n";						
		strStmt += "CONSTRAINT SIGNATURE(ISSUER_SIGN) INPUTS(VOUCHER_NONCE,VOUCHER_NAME,VOUCHER_NO,VOUCHER_PVALUE) VERIFY KEY TABLE_ISSUER, \n";
		strStmt += "CONSTRAINT SIGNATURE(TOSA_SIGN) ORDERED(VOUCHER_GID) TIMED(TOSA_TIME) \n";
		strStmt += ") ENGINE=InnoDB \n";	 
		strStmt += "DSA_SCHEME='SECP256K1' \n";	
		strStmt += "TABLE_ISSUER_PUB_KEY='"+strIssuerPubKey+"'\n";
		strStmt += "TRUSTED_REFERENCE_PUB_KEY='"+strChainPubKey+"'\n";
		strStmt += "TOSA_PUB_KEY='"+strOrdererPubKey+"'\n";
		strStmt += "TOSA_MASTER_PUB_KEY='"+strOrdererPubKey+"'\n"; 			
		strTransformed = ECDSA.transform_for_sign(strStmt);													
		strStmt += "TABLE_SCHEMA=\""+strTransformed+"\" ";
		strStmt += "TABLE_SCHEMA_SIGN='"+walletManager.secureSign(issuer_wallet_name, strTransformed)+"'";
						
		System.out.println("\n");
		System.out.println(strStmt);
 
		byte[] baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
		dnomConnector.close();
		int errorCode = ArrayUtil.BAToInt(baReturn);
		if(errorCode==0) {
			System.out.println("Success!");
		} else {
			System.out.println("Fail.. errorCode = "+errorCode);
		}
	}
	
	public static void insertVouchers(WalletManager walletManager, String issuer_wallet_name, String voucher_name, int startNo, int amount, int parvalue) {
		String strStmt;
		String strSignInput;
		String serviceName;	
		byte nonce[] = new byte[8];
		SecureRandom random = new SecureRandom();		

		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return;
		}

		serviceName =ISSSUER_NAME+"/"+SERVICE_VOUCHER_MANAGEMENT+"/VOUCHER_"+voucher_name;
		//walletManager.readWallet(issuer_wallet_name);
		try {
			for(int i=0; i<amount; i++) {				
				random.nextBytes(nonce);						
				strSignInput = ArrayUtil.toHex(nonce)+voucher_name+String.valueOf(startNo+i)+String.valueOf(parvalue); // SIGN IN (TORDER_NONCE,VOUCHER_NAME,VOUCHER_NO,PVALUE)
							
				strStmt= "INSERT INTO VOUCHER_"+voucher_name+" (VOUCHER_GID,VOUCHER_NONCE,VOUCHER_NAME,VOUCHER_NO,VOUCHER_PVALUE,ISSUER_SIGN,TOSA_TIME,TOSA_SIGN) VALUES ";
				strStmt += "(@SYNC_ID,'"+ArrayUtil.toHex(nonce)+"','"+voucher_name+"','"+String.valueOf(startNo+i)+"','"+String.valueOf(parvalue)+"','"+walletManager.sign(issuer_wallet_name, strSignInput)+"',@DATETIME,@SYNC_SIGN);";
				
				//byte[] baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
				byte[] baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_ASYNC,serviceName,strStmt);
				int errorCode = ArrayUtil.BAToInt(baReturn);
				if(errorCode==0) {
					System.out.println("["+i+"] voucher is minted successfully!");
				} else {
					System.out.println("["+i+"] Fail.. errorCode = "+errorCode);
					break;
				}
			} 
		} catch(Exception e) {
			e.printStackTrace();
		} 
		dnomConnector.close();
	}
	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws Exception {
		Console cons = System.console();
		String issuer_wallet_name=null;
		String tosa_wallet_name=null;
		String voucher_name=null;
		String menu=null;
		String startNo, amount, parvalue;

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
		System.out.println("|                     NFT VOUCHER ISSUER               |");
		System.out.println("|                       TRANSACTIONS                   |");
		System.out.println("|              -----------------------------           |");
		System.out.println("|                                                      |"); 
		System.out.println("|                      Copyright (c) 2020, TRUSTDB Inc.|");		
		System.out.println("*======================================================*\n");
		
		while(true) {
			System.out.println("Waht do you want ? ( 0: Deploy a Voucher  1: Mint Voucher )");
			menu = cons.readLine();            
			System.out.println("\n");
			if(menu.equals("0")) {		
				System.out.println("=> Enter the ISSUER name ");
				ISSSUER_NAME = cons.readLine();
				System.out.println("\n");
								
				System.out.println("=> Enter the SERIVCE name ");
				SERVICE_VOUCHER_MANAGEMENT = cons.readLine();
				System.out.println("\n");
								
				System.out.println("=> Enter the ISSUER's wallet name");
				issuer_wallet_name = cons.readLine();
				System.out.println("\n");
				
				System.out.println("=> Enter the TOSA's wallet name");
				tosa_wallet_name = cons.readLine();
				System.out.println("\n");								

				System.out.println("=> Enter Voucher name");
				voucher_name = cons.readLine();
				System.out.println("\n");												
				createVoucherTable(issuer_wallet_name, tosa_wallet_name, voucher_name);

			} else if(menu.equals("1"))	{				
				if(issuer_wallet_name==null) {
					System.out.println("=> Enter the ISSUER name ");
					ISSSUER_NAME = cons.readLine();
					System.out.println("\n");
									
					System.out.println("=> Enter the SERIVCE name ");
					SERVICE_VOUCHER_MANAGEMENT = cons.readLine();
					System.out.println("\n");
									
					System.out.println("=> Enter the ISSUER's wallet name");
					issuer_wallet_name = cons.readLine();
					System.out.println("\n");
					
					System.out.println("=> Enter Voucher name");
					voucher_name = cons.readLine();
					System.out.println("\n");		
				}				
				System.out.println("=> Enter the START NUMBER of the VOUCHER ");
				startNo = cons.readLine();
				System.out.println("\n");					
				System.out.println("=> Enter How many VOUCHERs do you want to issue ?");
				amount = cons.readLine();
				System.out.println("\n");					
				System.out.println("=> Waht is PAR VALUE of the VOUCHER ?");
				parvalue = cons.readLine();			
				System.out.println("\n");			
				
				WalletManager walletManager = new WalletManager();
				String password;
				System.out.println("");
				System.out.println("[WalletManger]=============================================");
				System.out.println("!!  Enter password to access the wallet ["+issuer_wallet_name+"]");            
				password = new String(cons.readPassword());
				System.out.println("============================================================");            
				System.out.println("");
				if(walletManager.readWallet(issuer_wallet_name,password)==null) {
					System.out.println("No exist wallet +"+issuer_wallet_name);
					continue;
				}
				System.out.println("");

				insertVouchers(walletManager, issuer_wallet_name, voucher_name, Integer.parseInt(startNo), Integer.parseInt(amount), Integer.parseInt(parvalue));
			}
	 
		}		
	}
}