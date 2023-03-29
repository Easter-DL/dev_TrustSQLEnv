/* Copyright (c) 2020, TRUSTDB Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; If not, see <http://www.gnu.org/licenses/>.
*/

package org.rdlms.wallet;

import java.security.*;
import java.util.Scanner;
import java.security.spec.*;
import java.security.interfaces.*;
import java.math.*;

import org.rdlms.crypto.ECDSA;
import org.rdlms.util.ArrayUtil;

public class ReadECKey {
	
	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws Exception {

		String name;
		Scanner sc = new Scanner(System.in);
		byte rawbytes[];
		byte pubbytes[];
		byte prvbytes[];
		KeyPair issuerPair;
		PrivateKey priv;
		PublicKey pub;
		Signature ecdsa;
		String inputs;
		byte[] strByte;
		byte[] realSig;
		
		while(true) {
			System.out.println("\n"); 
			System.out.println("==========================================================");  
			System.out.println("||														||"); 
			System.out.println("||				Read Key File  (ECC,secp256k1) 			||");  
			System.out.println("||				------------------------------ 			||");
			System.out.println("|| 														||"); 
			System.out.println("||  					Copyright (c) 2020, TRUSTDB Inc.||");		
			System.out.println("==========================================================");  
			System.out.println("\n\n");
			System.out.println("Put the name of file to read !(exit 'quit')"); 
												
			name = sc.nextLine();
			if(name.equals("quit")) return;		
			if(name.equals("")) continue;
						
			System.out.println("\n");
			issuerPair = ECDSA.deSerializeKeyPair("./keyfiles/"+name+".sek");
				
			priv = issuerPair.getPrivate();
			pub = issuerPair.getPublic();
			
			System.out.println("PUBLIC KEY TO STRING");
			System.out.println(pub.toString());
			System.out.println("\n");
			
			System.out.println("PUBLIC KEY getAlgorithm");
			System.out.println(pub.getAlgorithm());
			System.out.println("\n");
			
			System.out.println("PUBLIC KEY getFormat()");
			System.out.println(pub.getFormat());
			System.out.println("\n");
			
			System.out.println("PUBLIC KEY getEncoded");
			System.out.println(ArrayUtil.toHex(pub.getEncoded()));
			System.out.println("\n");
			
			
			ECPublicKey ecPub = (ECPublicKey)pub;
			
			ECPoint ecp = ecPub.getW();
			BigInteger x = ecp.getAffineX();
			BigInteger y = ecp.getAffineY();
			
			
			System.out.println("ECPoint X");
			System.out.println(x);
			System.out.println("Hex String:");
			System.out.println(ArrayUtil.toHex(x.toByteArray()));
			System.out.println("\n");
			
			System.out.println("ECPoint Y");
			System.out.println(y);
			System.out.println("Hex String:");
			System.out.println(ArrayUtil.toHex(y.toByteArray()));
			System.out.println("\n");
			
			System.out.println("Compressed PUBKEY");
			String compressedPubStr;
										/*	
			compressedPubStr = ArrayUtil.toHex(x.toByteArray());
			if(compressedPubStr.substring(0,2).equals("00")) {
				compressedPubStr = compressedPubStr.substring(2,compressedPubStr.length());
			}
			if (mod.intValue()==0) {
				// ¦����.. 0x02��,
				compressedPubStr = "02"+compressedPubStr;
			} else {
				compressedPubStr = "03"+compressedPubStr;
			}
			System.out.println(compressedPubStr);
			*/
			
			compressedPubStr = ECDSA.getCompKey(pub);
			System.out.println(compressedPubStr); 
				
			/*
			System.out.println("De-Compressed PUBKEY from Compressed PUBKEY");
			byte[] decompPub = decompressPubkey(ArrayUtil.toByte(compressedPubStr)); 
			System.out.println(ArrayUtil.toHex(decompPub));
			*/		
				
			rawbytes = pub.getEncoded();	  
			pubbytes = new byte[rawbytes.length-23];	
			System.arraycopy(rawbytes,23,pubbytes,0,rawbytes.length-23);
			System.out.println("\n");
			System.out.println("PUBLIC KEY :\n");	
			for(int i=0; i<pubbytes.length; i++) 
				System.out.print(String.format("%02x",pubbytes[i]));
			System.out.println("\n");		 
			
			rawbytes = priv.getEncoded();	  
			prvbytes = new byte[rawbytes.length-32];	
			System.arraycopy(rawbytes,32,prvbytes,0,rawbytes.length-32);
			System.out.println("\n");	
			System.out.println("PRIVATE KEY :\n");
			for(int i=0; i<prvbytes.length; i++) 
				System.out.print(String.format("%02x",prvbytes[i]));
			System.out.println("\n");	
			
			System.out.println("If you want to sign, put text (Exit 'quit')"); 
			System.out.println("\n");	
												
			inputs = sc.nextLine();
			if(inputs.equals("quit")) return;		
			if(inputs.equals("")) continue;
							
			ecdsa = Signature.getInstance("SHA256withECDSA");
		
			ecdsa.initSign(priv);
			strByte = inputs.getBytes("UTF-8");
			ecdsa.update(strByte);
		
			/*
			 * Now that all the data to be signed has been read in, generate a
			 * signature for it
			 */
		
			realSig = ecdsa.sign();
			System.out.println("Signature("+realSig.length+")") ; 
			for(int i=0; i<realSig.length; i++) 
				System.out.print(String.format("%02x",realSig[i]));
			System.out.println("\n");	
		}
	}
}