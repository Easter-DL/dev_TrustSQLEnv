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
import java.security.spec.ECGenParameterSpec;
import java.util.Scanner;
import org.rdlms.crypto.ECDSA;
import org.rdlms.util.ArrayUtil;

public class CreateECKey {
	
	public static KeyPair createNew(String keyName) {
		KeyPair newPair=null;
		KeyPairGenerator keyGen;
		try {	
			keyGen = KeyPairGenerator.getInstance("EC");
			keyGen.initialize(new ECGenParameterSpec("secp256k1"), new SecureRandom());

			newPair = keyGen.generateKeyPair();
			ECDSA.serializeKeyPair(newPair,"./keyfiles/"+keyName+".sek");
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return newPair;				
	}

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws Exception {
		String name;
		Scanner sc = new Scanner(System.in);
		SecureRandom random = new SecureRandom();
		byte rand[] = new byte[2];
		String id;
		String public_key;
		byte rawbytes[];
		byte pubbytes[];
		KeyPair newPair,issuerPair;
		PrivateKey priv;
		PublicKey pub;
		KeyPairGenerator keyGen;
		String mission=null;				

		while(true) {
			System.out.println("\n"); 
			System.out.println("==========================================================");  
			System.out.println("||      ||"); 
			System.out.println("||    Keypair Generator(ECC,secp256k1)  ||");  
			System.out.println("||    --------------------------------  ||");
			System.out.println("||      ||"); 
			System.out.println("||    Copyright (c) 2020, TRUSTDB Inc.  ||");		
			System.out.println("==========================================================");  
			System.out.println("\n\n");
								
			System.out.println("\n\n");
			
			
			System.out.println("\n");
			System.out.println("Waht do you want to do ? ( 0: One new key pair generate 1: Bulk generate new key pairs for test");
			mission = sc.nextLine();
			
			if(mission.equals("0")) {	
				while(true) {							
					System.out.println("Give me name of new key pair ! (exit 'quit')"); 
										
					name = sc.nextLine();
					if(name.equals("quit")) return;		
					if(name.equals("")) continue;
								
					id = name;
					System.out.println("\n");
				  
				keyGen = KeyPairGenerator.getInstance("EC");
				keyGen.initialize(new ECGenParameterSpec("secp256k1"), new SecureRandom());

				newPair = keyGen.generateKeyPair();
				
				priv = newPair.getPrivate();
				pub = newPair.getPublic();
				
				rawbytes = pub.getEncoded();	
				System.out.println("Public Key : "+pub.toString()); 
				System.out.println("\n");
				System.out.println("Public Key("+rawbytes.length+")");
				for(int i=0; i<rawbytes.length; i++) 
					System.out.print(String.format("%02x",rawbytes[i]));
				System.out.println("\n");	
				
				pubbytes = new byte[rawbytes.length-23];	
				System.arraycopy(rawbytes,23,pubbytes,0,rawbytes.length-23);

				System.out.println("Compressed Public Key="+ECDSA.getCompKey(pub));
					      
				public_key= ArrayUtil.toHex(pubbytes);
						
				ECDSA.serializeKeyPair(newPair,"./keyfiles/"+id+".sek");
				
				System.out.println("Name(ID) of File : "+id+".sek");
				System.out.println("\n");			
				System.out.println("\n");			
				}   
			} else if(mission.equals("1")) {		
				System.out.println("How many key pairs do you want to make?"); 
				String countStr = sc.nextLine();
				
				for(int i=1; i<Integer.parseInt(countStr)+1; i++) {
					name = Integer.toString(i);
					
					id = name;
					System.out.println("\n");
					keyGen = KeyPairGenerator.getInstance("EC");
					keyGen.initialize(new ECGenParameterSpec("secp256k1"), new SecureRandom());
		
					newPair = keyGen.generateKeyPair();
					
					priv = newPair.getPrivate();
					pub = newPair.getPublic();
					
					rawbytes = pub.getEncoded();	
					System.out.println("Public Key : "+pub.toString()); 
					System.out.println("\n");
					System.out.println("Public Key("+rawbytes.length+")");
					for(int j=0; j<rawbytes.length; j++) 
						System.out.print(String.format("%02x",rawbytes[j]));
					System.out.println("\n");	
					
					pubbytes = new byte[rawbytes.length-23];	
					System.arraycopy(rawbytes,23,pubbytes,0,rawbytes.length-23);
						      
					public_key= ArrayUtil.toHex(pubbytes);
							
					ECDSA.serializeKeyPair(newPair,"./keyfiles/"+id+".sek");
					
					System.out.println("Name(ID) of File : "+id+".sek");
					System.out.println("\n");			
					System.out.println("\n");			      
				}
					       
			}
			break;
		}				
	}
}