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

package org.rdlms.demo.digitalID;

import java.security.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.*;

import org.rdlms.crypto.ECDSA;
import org.rdlms.dnom.connector.DNOMConnector;
import org.rdlms.util.ArrayUtil;
import org.rdlms.wallet.ECDSAWallet;
import org.rdlms.wallet.WalletManager;

import java.util.Properties;

public class DriversLicense {
	static String strDNOMAddress=null;
	static String strDNOMPort=null;
	static String strIssuer=null;
	static String strService=null;

	static String DBURL=null;
	static String DBUSR=null;
	static String DBUSRPW=null;

	static String ISSSUER_NAME=null;
	static String SERVICE_MEMBER_MANAGEMENT=null;
	
	public static void showApplicationForm() {
		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		String strDBURL= DBURL+"/"+SERVICE_MEMBER_MANAGEMENT;

		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		try { 
			String application_gid, account, name_enc, address_enc, applicant_sign;
			
			// SEND_TOTAL		
			strStmt = "SELECT * FROM APPLICATION_FORM;";
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			System.out.println(String.format("[%8s] %12s %12s %24s %12s","APPLICATION_GID","ACCOUNT","NAME_ENC","ADDRESS_ENC","APPLICANT_SIGN"));
			while(rs.next()) {
				application_gid = Long.toString(rs.getLong("APPLICATION_GID"));
				account = rs.getString("ACCOUNT");
				name_enc = rs.getString("NAME_ENC");
				address_enc = rs.getString("ADDRESS_ENC");
				applicant_sign = rs.getString("APPLICANT_SIGN");				
				System.out.println(String.format("[%8s] %12s %12s %24s %12s",application_gid,account,name_enc,address_enc,applicant_sign));
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

	public static void showApplicationFormDecrypt(String wallet_name, String password) {
		WalletManager walletManager = new WalletManager();
		Connection con=null;
		PreparedStatement pstmt = null; 
		ResultSet rs = null;
		String strStmt; 
		String strDBURL= DBURL+"/"+SERVICE_MEMBER_MANAGEMENT;

		walletManager.readWallet(wallet_name,password);
		try {
			con = DriverManager.getConnection(
				strDBURL,
				DBUSR,
				DBUSRPW);	
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		try { 
			String application_gid, account, name_enc, address_enc, applicant_sign;
			String name, address;
			
			// SEND_TOTAL		
			strStmt = "SELECT * FROM APPLICATION_FORM;";
			pstmt = con.prepareStatement(strStmt);
			rs = pstmt.executeQuery();			
			System.out.println(String.format("[%8s] %12s %12s %24s %12s","APPLICATION_GID","ACCOUNT","NAME_ENC","ADDRESS_ENC","APPLICANT_SIGN"));
			while(rs.next()) {
				application_gid = Long.toString(rs.getLong("APPLICATION_GID"));
				account = rs.getString("ACCOUNT");
				name_enc = rs.getString("NAME_ENC");
				address_enc = rs.getString("ADDRESS_ENC");
				applicant_sign = rs.getString("APPLICANT_SIGN");				

				name = walletManager.ecdhDecryption(wallet_name, ECDSAWallet.buildPublicKey(ArrayUtil.toByte(account)),name_enc);
				address = walletManager.ecdhDecryption(wallet_name, ECDSAWallet.buildPublicKey(ArrayUtil.toByte(account)),address_enc);

				System.out.println(String.format("[%8s] %12s %12s %24s %12s",application_gid,account,name,address,applicant_sign));
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

	public static void issueLicense(String iva_wallet_name, String password, String applicant_gid) {


	}
		
	public static void makeApplicationForm(String iva_wallet_name, String wallet_name, String password, String name, String address){
		WalletManager walletManager = new WalletManager();
		String strStmt;
		String strSignInput;
		String serviceName;	
		String strPubKey;
		String strMemberAccount;
		String strEncryptedName;
		String strEncryptedAddress;
		byte nonce[] = new byte[8];
		SecureRandom random = new SecureRandom();		

		walletManager.readWallet(iva_wallet_name);		
		strPubKey = walletManager.readWallet(wallet_name, password);
			
		strEncryptedName = walletManager.ecdhEncryption(wallet_name,iva_wallet_name,name);
		System.out.println("");
		System.out.println("Your name is "+name+" and ecrypted name is "+strEncryptedName);
		strEncryptedAddress = walletManager.ecdhEncryption(wallet_name,iva_wallet_name,address);
		System.out.println("Your address is "+address+" and ecrypte address is "+strEncryptedAddress);

		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return;
		}

		serviceName =ISSSUER_NAME+"/"+SERVICE_MEMBER_MANAGEMENT+"/APPLICATION_FORM";
		try {
			strSignInput = strPubKey+strEncryptedName+strEncryptedAddress; // SIGN INPUTS(ACCOUNT, NAME_ENC, ADDRESS_ENC)
			strStmt= "INSERT INTO APPLICATION_FORM (APPLICATION_GID, ACCOUNT, NAME_ENC, ADDRESS_ENC, APPLICANT_SIGN, TOSA_TIME, TOSA_SIGN) VALUES ";
			strStmt += "(@SYNC_ID,'"+strPubKey+"','"+strEncryptedName+"','"+strEncryptedAddress+"','"+walletManager.secureSign(wallet_name, strSignInput)+"',@DATETIME,@SYNC_SIGN);";
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

	public static void issueLicense(String iva_wallet_name, String wallet_name, String password, String name, String address){
		WalletManager walletManager = new WalletManager();
		String strStmt;
		String strSignInput;
		String serviceName;	
		String strPubKey;
		String strMemberAccount;
		String strEncryptedName;
		String strEncryptedAddress;
		byte nonce[] = new byte[8];
		SecureRandom random = new SecureRandom();		

		walletManager.readWallet(iva_wallet_name);		
		strPubKey = walletManager.readWallet(wallet_name, password);
			
		strEncryptedName = walletManager.ecdhEncryption(wallet_name,iva_wallet_name,name);
		System.out.println("");
		System.out.println("Your name is "+name+" and ecrypted name is "+strEncryptedName);
		strEncryptedAddress = walletManager.ecdhEncryption(wallet_name,iva_wallet_name,address);
		System.out.println("Your address is "+address+" and ecrypte address is "+strEncryptedAddress);

		DNOMConnector dnomConnector = new DNOMConnector(strDNOMAddress,Integer.parseInt(strDNOMPort));
		if(!dnomConnector.open(DNOMConnector.NETWORK_TYPE_TEST, DNOMConnector.NETWORK_ID_BIGBLOCKS)) {
			System.out.println("DNOM Connection Failed!Addr="+strDNOMAddress+"Port="+strDNOMPort);
			return;
		}

		serviceName =ISSSUER_NAME+"/"+SERVICE_MEMBER_MANAGEMENT+"/APPLICATION_FORM";
		try {
			strSignInput = strPubKey+strEncryptedName+strEncryptedAddress; // SIGN INPUTS(ACCOUNT, NAME_ENC, ADDRESS_ENC)
			strStmt= "INSERT INTO APPLICATION_FORM (APPLICATION_GID, ACCOUNT, NAME_ENC, ADDRESS_ENC, APPLICANT_SIGN, TOSA_TIME, TOSA_SIGN) VALUES ";
			strStmt += "(@SYNC_ID,'"+strPubKey+"','"+strEncryptedName+"','"+strEncryptedAddress+"','"+walletManager.secureSign(wallet_name, strSignInput)+"',@DATETIME,@SYNC_SIGN);";
			System.out.println(strStmt);
			
			/*
			byte[] baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_SYNC,serviceName,strStmt);
			int errorCode = ArrayUtil.BAToInt(baReturn);
			if(errorCode==0) {
				System.out.println("Success!");
			} else {
				System.out.println("Fail.. errorCode = "+errorCode);
			}
			*/
		} catch(Exception e) {
			e.printStackTrace();
		} 		
		dnomConnector.close();
	}

	public static void createLicenseTables(String issuer_wallet_name, String tosa_wallet_name) {		
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
						
		serviceName =ISSSUER_NAME+"/"+SERVICE_MEMBER_MANAGEMENT+"/APPLICATION_FORM";
		
		strStmt= "CREATE TRUSTED,ORDERED TABLE APPLICATION_FORM ( \n";
		strStmt += "APPLICATION_GID BIGINT UNSIGNED NOT NULL, \n";							
		strStmt += "ACCOUNT VARCHAR(66) NOT NULL, \n";
        strStmt += "NAME_ENC VARCHAR(64) NOT NULL, \n";
        strStmt += "ADDRESS_ENC VARCHAR(128) NOT NULL, \n";
        strStmt += "APPLICANT_SIGN VARCHAR(160) NOT NULL, \n";
        strStmt += "TOSA_TIME DATETIME NOT NULL, \n";
		strStmt += "TOSA_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(APPLICATION_GID), \n";
		strStmt += "CONSTRAINT UNIQUE(ACCOUNT), \n";
		strStmt += "CONSTRAINT SIGNATURE(APPLICANT_SIGN) INPUTS(ACCOUNT,NAME_ENC,ADDRESS_ENC) VERIFY KEY(ACCOUNT), \n";
		strStmt += "CONSTRAINT SIGNATURE(TOSA_SIGN) ORDERED(APPLICATION_GID) TIMED(TOSA_TIME) \n";
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

		serviceName =ISSSUER_NAME+"/"+SERVICE_MEMBER_MANAGEMENT+"/DRIVERS_LICENSE";
		
		strStmt= "CREATE TRUSTED,ORDERED TABLE DRIVERS_LICENSE ( \n";
		strStmt += "DRIVERS_LICENSE_GID BIGINT UNSIGNED NOT NULL, \n";							
		strStmt += "LICENSE_ID VARCHAR(40) NOT NULL, \n";
		strStmt += "ACCOUNT VARCHAR(66) NOT NULL, \n";
        strStmt += "NAME_ENC VARCHAR(64) NOT NULL, \n";
        strStmt += "ADDRESS_ENC VARCHAR(128) NOT NULL, \n";
        strStmt += "APPLICANT_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "ISSUER_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "TOSA_TIME DATETIME NOT NULL, \n";
		strStmt += "TOSA_SIGN VARCHAR(160) NOT NULL, \n";
		strStmt += "CONSTRAINT PRIMARY KEY(DRIVERS_LICENSE_GID), \n";
		strStmt += "CONSTRAINT UNIQUE(LICENSE_ID), \n";
        strStmt += "CONSTRAINT SIGNATURE(APPLICANT_SIGN) INPUTS(ACCOUNT,NAME_ENC,ADDRESS_ENC) VERIFY KEY(ACCOUNT), \n";
		strStmt += "CONSTRAINT SIGNATURE(ISSUER_SIGN) INPUTS(LICENSE_ID,ACCOUNT,NAME_ENC,ADDRESS_ENC,APPLICANT_SIGN) VERIFY KEY TABLE_ISSUER, \n";
		strStmt += "CONSTRAINT SIGNATURE(TOSA_SIGN) ORDERED(DRIVERS_LICENSE_GID) TIMED(TOSA_TIME) \n";
		strStmt += ") ENGINE=InnoDB\n";
		strStmt += "DSA_SCHEME='SECP256K1' \n";
		strStmt += "TABLE_ISSUER_PUB_KEY='"+strIssuerPubKey+"' \n";		
		strStmt += "TRUSTED_REFERENCE_PUB_KEY='"+strTrustChainPubKey+"'\n";
		strStmt += "TOSA_PUB_KEY='"+strOrdererPubKey+"'\n";
		strStmt += "TOSA_MASTER_PUB_KEY='"+strOrdererPubKey+"'\n"; 			
		strTransformed = ECDSA.transform_for_sign(strStmt);
		strStmt += "TABLE_SCHEMA=\""+strTransformed+"\" ";
		strStmt += "TABLE_SCHEMA_SIGN='"+walletManager.secureSign(issuer_wallet_name, strTransformed)+"'";

		baReturn = dnomConnector.transmit(DNOMConnector.TRANSACTION_TYPE_ASYNC,serviceName,strStmt);
		errorCode = ArrayUtil.BAToInt(baReturn);
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
		String password;
		String name;
		String address;
		String menu;
		
		try { 
			Properties properties = new Properties();
			InputStream inputStream = new FileInputStream("trustsql.properties");
			properties.load(inputStream);
			inputStream.close();
			strDNOMAddress = (String) properties.get("DNOMADDR");
			strDNOMPort = (String) properties.get("DNOMPORT");					
			DBURL = (String) properties.get("DBURL");
			DBUSR = "www.trustedpolice.gov";
			DBUSRPW = "1234";
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		System.out.println("\n");
		System.out.println("*======================================================*");
		System.out.println("|                                                      |"); 
		System.out.println("|               Digital ID - Driver's License          |");
		System.out.println("|                                                      |");
		System.out.println("|              -----------------------------           |");
		System.out.println("|                                                      |"); 
		System.out.println("|                      Copyright (c) 2020, TRUSTDB Inc.|");		
		System.out.println("*======================================================*\n");
		System.out.println("\n");
		
		while(true) {
			System.out.println("What do you want ? ( 0: Deploy Driver's License Ledger 1: write Application Form   2: Issue License )");
			menu = cons.readLine();            
			System.out.println("\n");
			if(menu.equals("0")) {						
				if(ISSSUER_NAME==null) {
					System.out.println("=> Enter the ISSUER name ");
					ISSSUER_NAME = cons.readLine();
					System.out.println("\n");
				}								
				if(SERVICE_MEMBER_MANAGEMENT==null) {
					System.out.println("=> Enter the SERIVCE name ");
					SERVICE_MEMBER_MANAGEMENT = cons.readLine();
					System.out.println("\n");
				}

				System.out.println("=> Enter the ISSUER's wallet name");
				issuer_wallet_name = cons.readLine();
				System.out.println("\n");
				
				System.out.println("=> Enter the TOSA's wallet name");
				tosa_wallet_name = cons.readLine();
				System.out.println("\n");								
				createLicenseTables(issuer_wallet_name, tosa_wallet_name);

			} else if(menu.equals("1"))	{
				if(ISSSUER_NAME==null) {
					System.out.println("=> Enter the ISSUER name ");
					ISSSUER_NAME = cons.readLine();
					System.out.println("\n");
				}								
				if(SERVICE_MEMBER_MANAGEMENT==null) {
					System.out.println("=> Enter the SERIVCE name ");
					SERVICE_MEMBER_MANAGEMENT = cons.readLine();
					System.out.println("\n");
				}

				System.out.println("=> Enter the IVA(Identity Verification Authority)'s wallet name  (goto back -> quit)");
				iva_wallet_name = cons.readLine();					
				System.out.println("\n");		
				if(iva_wallet_name.equals("quit")) break;		

				System.out.println("=> Enter your wallet name  (goto back -> quit)");
				wallet_name = cons.readLine();					
				System.out.println("\n");		
				if(wallet_name.equals("quit")) break;		

				System.out.println("");
				System.out.println("Enter password for "+wallet_name);                                         
				password = new String(cons.readPassword());

				System.out.println("=> Enter your name  (goto back -> quit)");
				name = cons.readLine();					
				System.out.println("\n");		
				if(name.equals("quit")) break;		

				System.out.println("=> Enter your address (goto back -> quit)");
				address = cons.readLine();					
				System.out.println("\n");		
				if(address.equals("quit")) break;		
				
				//registVerifiedMembers(iva_wallet_name, wallet_name);
				makeApplicationForm(iva_wallet_name, wallet_name, password, name, address);
				System.out.println("\n");		
			} else if(menu.equals("2"))	{
				if(ISSSUER_NAME==null) {
					System.out.println("=> Enter the ISSUER name ");
					ISSSUER_NAME = cons.readLine();
					System.out.println("\n");
				}								
				if(SERVICE_MEMBER_MANAGEMENT==null) {
					System.out.println("=> Enter the SERIVCE name ");
					SERVICE_MEMBER_MANAGEMENT = cons.readLine();
					System.out.println("\n");
				}

				// 1. 지갑 및 비번 받고
				System.out.println("=> Enter the IVA(Identity Verification Authority)'s wallet name  (goto back -> quit)");
				iva_wallet_name = cons.readLine();					
				System.out.println("\n");		
				if(iva_wallet_name.equals("quit")) break;		

				System.out.println("");
				System.out.println("Enter password for "+iva_wallet_name);                                         
				password = new String(cons.readPassword());

				// 2. 신청자 리스트를 보여준다.
				showApplicationForm();

				System.out.println("");
				System.out.println("=> Do you want decrypt ? (Y:Yes, Others:No)");
				String yesNo=null;
				yesNo = cons.readLine();
				if(yesNo.equalsIgnoreCase("Y")) {
					showApplicationFormDecrypt(iva_wallet_name, password);
				}
				
				// 3. 라이센스를 줄 신청자 GID를 입력받는다.
				String applicant_gid;
				System.out.println("=> Enter the APPLICATION_FORM GID to issue a License");
				applicant_gid =cons.readLine();		
				
				// 4. 라이센스를 발급한다.
				issueLicense(iva_wallet_name, password, applicant_gid);
			}
		}
	}
}