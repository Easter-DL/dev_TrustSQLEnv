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

import java.io.File;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.rdlms.crypto.ECDSA;
import org.rdlms.util.ArrayUtil;

public class ECDSAWallet {
   public byte[] encoded_public_key=null;
   public byte[] encoded_private_key=null;
   private byte[] encrypted_encoded_private_key=null;
   private byte[] account=null; // account is compressed publicKey   
   private PublicKey public_key=null;
   private PrivateKey private_key=null;
   public String  wallet_name=null;
   final String wallet_path="./wallets";
   final String pub_file_ext=".pub";
   final String encprv_file_ext=".encprv";
   final String wallet_version="org.rdlms.wallet.ECDSAWallet v0.0.1";

   
   /**	
	* Generate a key pair 
	* @param	String name : wallet name
   * @param	String password : password to encrypt generated key pair.
	* @reutrn String account : compressed PublicKey. 
	*/   
   public String generate(String name, String password) {
      KeyPair newPair=null;
		KeyPairGenerator keyGen;
      PrivateKey priv;
		PublicKey pub;
      byte[] encodedPrivateKey;      
		try {	
			keyGen = KeyPairGenerator.getInstance("EC");
			keyGen.initialize(new ECGenParameterSpec("secp256k1"), new SecureRandom());

			newPair = keyGen.generateKeyPair();         		
			priv = newPair.getPrivate();
			pub = newPair.getPublic();
         this.public_key = pub;         

         this.wallet_name = name;
         this.account = ArrayUtil.toByte(ECDSA.getCompKey(pub));
			this.encoded_public_key = pub.getEncoded();	  			
			encodedPrivateKey = priv.getEncoded();			                  

         // normally private key and encodedPrivateKey is not stored on ECDSAWallet.         

         this.encrypted_encoded_private_key = ECDSA.aes256_encrypt(password, encodedPrivateKey);         
      } catch (Exception e) {
         e.printStackTrace();         
      }
      return ArrayUtil.toHex(this.account);
   }
   
   public void write() throws Exception  {
      String fileName;
      RandomAccessFile pubFile=null,encPrvFile=null;
      
      if(account==null || encoded_public_key==null || encrypted_encoded_private_key==null) {
         throw new Exception("No Key data!");
      }
      
      try {
         fileName = this.wallet_path;
         File newFile = new File(fileName);
         if(!newFile.exists()) {
            newFile.mkdirs();				
			}

         fileName = wallet_path+"/"+wallet_name+pub_file_ext;
         newFile = new File(fileName);
         if(newFile.exists()) {
            throw new Exception("Can't save, the wallet file "+fileName+" already exist!");
         } else {			
            newFile.createNewFile();
         }

         pubFile = new RandomAccessFile(fileName,"rw");
         pubFile.seek(0);
         pubFile.write(this.encoded_public_key);

         fileName = wallet_path+"/"+wallet_name+encprv_file_ext;
         newFile = new File(fileName);
         if(newFile.exists()) {
            throw new Exception("Can't save, the wallet file "+fileName+" already exist!");
         } else {			
            newFile.createNewFile();
         }
         
         encPrvFile = new RandomAccessFile(fileName,"rw");
         encPrvFile.seek(0);
         encPrvFile.write(this.encrypted_encoded_private_key);
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         try {
            if(pubFile!=null) pubFile.close();
            if(encPrvFile!=null) encPrvFile.close();
         } catch (Exception e) {};         
      }
   }
   
   public String read(String name) {
      String fileName=null;
      RandomAccessFile pubFile=null,encPrvFile=null;

      this.wallet_name=name;
      try {
         fileName = wallet_path+"/"+wallet_name+pub_file_ext;         
         pubFile = new RandomAccessFile(fileName,"r");
         this.encoded_public_key = new byte[(int)pubFile.length()];
         pubFile.seek(0);
         pubFile.read(this.encoded_public_key);
         
         fileName = wallet_path+"/"+wallet_name+encprv_file_ext;
         encPrvFile = new RandomAccessFile(fileName,"rw");
         encrypted_encoded_private_key = new byte[(int)encPrvFile.length()];         
         encPrvFile.seek(0);
         encPrvFile.read(this.encrypted_encoded_private_key);
         //System.out.println("@read encrypted_encoded_private_key="+ArrayUtil.toHex(this.encrypted_encoded_private_key));
         
         PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(this.encoded_public_key));
         this.public_key = publicKey;
         this.account = ArrayUtil.toByte(ECDSA.getCompKey(publicKey));
      } catch (Exception e) {
         return null;
      } finally {
         try {
            if(pubFile!=null) pubFile.close();
            if(encPrvFile!=null) encPrvFile.close();
         } catch (Exception e) {};         
      }
      
      if(this.account!=null) {
         return ArrayUtil.toHex(this.account);
      } else {
         return null;
      }
   }

   public String read(String name, String password) {
      String fileName=null;
      RandomAccessFile pubFile=null,encPrvFile=null;
      this.wallet_name=name;
      try {
         fileName = wallet_path+"/"+wallet_name+pub_file_ext;         
         pubFile = new RandomAccessFile(fileName,"r");
         this.encoded_public_key = new byte[(int)pubFile.length()];
         pubFile.seek(0);
         pubFile.read(this.encoded_public_key);
         //System.out.println("@read encoded_public_key="+ArrayUtil.toHex(this.encoded_public_key));
         
         byte[] pubBytes = new byte[this.encoded_public_key.length-23];
         System.arraycopy(this.encoded_public_key,23,pubBytes,0,this.encoded_public_key.length-23);
         //System.out.println("@public_key="+ArrayUtil.toHex(pubBytes));

         fileName = wallet_path+"/"+wallet_name+encprv_file_ext;
         encPrvFile = new RandomAccessFile(fileName,"rw");
         encrypted_encoded_private_key = new byte[(int)encPrvFile.length()];         
         encPrvFile.seek(0);
         encPrvFile.read(this.encrypted_encoded_private_key);
         
         this.encoded_private_key = ECDSA.aes256_decrypt(password, this.encrypted_encoded_private_key);
         
         this.private_key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(this.encoded_private_key));
         
         this.public_key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(this.encoded_public_key));
         this.account = ArrayUtil.toByte(ECDSA.getCompKey(this.public_key));

         //System.out.println("@account(compressed public key) ="+ArrayUtil.toHex(this.account));

      } catch (Exception e) {
         e.printStackTrace();
         return null;
      } finally {
         try {
            if(pubFile!=null) pubFile.close();
            if(encPrvFile!=null) encPrvFile.close();
         } catch (Exception e) {};         
      }
      
      if(this.account!=null) {
         return ArrayUtil.toHex(this.account);
      } else {
         return null;
      }
   }


   public String readPub(String name) {
      String fileName=null;
      RandomAccessFile pubFile=null;

      this.wallet_name=name;
      try {
         fileName = wallet_path+"/"+wallet_name+pub_file_ext;         
         pubFile = new RandomAccessFile(fileName,"r");
         this.encoded_public_key = new byte[(int)pubFile.length()];
         pubFile.seek(0);
         pubFile.read(this.encoded_public_key);         
         
         PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(this.encoded_public_key));
         this.account = ArrayUtil.toByte(ECDSA.getCompKey(publicKey));
      } catch (Exception e) {
         return null;
      } finally {
         try {
            if(pubFile!=null) pubFile.close();            
         } catch (Exception e) {};         
      }
      
      if(this.account!=null) {
         return ArrayUtil.toHex(this.account);
      } else {
         return null;
      }
   }

   public PublicKey readPublicKey(String name) {
      String fileName=null;
      RandomAccessFile pubFile=null;

      this.wallet_name=name;
      try {
         fileName = wallet_path+"/"+wallet_name+pub_file_ext;         
         pubFile = new RandomAccessFile(fileName,"r");
         this.encoded_public_key = new byte[(int)pubFile.length()];
         pubFile.seek(0);
         pubFile.read(this.encoded_public_key);         
         
         PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(this.encoded_public_key));         
         return publicKey;
      } catch (Exception e) {
         return null;
      } finally {
         try {
            if(pubFile!=null) pubFile.close();            
         } catch (Exception e) {};         
      }   
   }

   public String sign(String inputs, String password) throws Exception {	 
		Signature ecdsa;
		byte[] realSig={0}; 
		byte[] strByte;

      if(this.encrypted_encoded_private_key==null) {
         throw new Exception("no private key !");
      }
		try{	
		   ecdsa = Signature.getInstance("SHA256withECDSA");
         byte[] encoded_private_key;     
         encoded_private_key = ECDSA.aes256_decrypt(password, this.encrypted_encoded_private_key);
         PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(encoded_private_key));

         ecdsa.initSign(privateKey);	
		   strByte = inputs.getBytes("UTF-8");
		   ecdsa.update(strByte);
		   realSig = ecdsa.sign();
		} catch(Exception e) { 
         e.printStackTrace(); 
         return null;      
      }	
		return ArrayUtil.toHex(realSig);
	}

   public String sign(String inputs) throws Exception {	 
		Signature ecdsa;
		byte[] realSig={0}; 
		byte[] strByte;

      if(this.private_key==null) {
         throw new Exception("no private key !");
      }
		try{	
		   ecdsa = Signature.getInstance("SHA256withECDSA");         
         ecdsa.initSign(this.private_key);	
		   strByte = inputs.getBytes("UTF-8");
		   ecdsa.update(strByte);
		   realSig = ecdsa.sign();
		} catch(Exception e) { 
         e.printStackTrace(); 
         return null;      
      }	
		return ArrayUtil.toHex(realSig);
	}

	public boolean verify(String sign, String inputs) throws Exception {      
		Signature ecdsa;
		boolean ret=false;

      if(this.encoded_public_key==null) {
         throw new Exception("no public key !");
      }

		try{	
			ecdsa = Signature.getInstance("SHA256withECDSA");
         PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(this.encoded_public_key));
			ecdsa.initVerify(publicKey);	
			ecdsa.update(inputs.getBytes());
			ret = ecdsa.verify(ArrayUtil.toByte(sign));		
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

   public String getCompKey() {
      return ArrayUtil.toHex(this.account);
   }

   public String getAccount() {
      return ArrayUtil.toHex(this.account);
   }

   /*
   public String getPublicKey() {
      byte[] baPub = new byte[this.encoded_public_key.length-23];	
		System.arraycopy(this.encoded_public_key,23,baPub,0,this.encoded_public_key.length-23);
      return ArrayUtil.toHex(baPub);
   }
   */
   public PublicKey getPublicKey() {
      return this.public_key;
   }
   /*
   public String getPrivateKey(String password) {
      String ret=null;

      return ret;
   }
   */
   public PrivateKey getPrivateKey() {
      return this.private_key;
   }
   
   public static PublicKey buildPublicKey(byte[] ck) {
      byte[] dk = ECDSA.decompressPubkey(ck);
		byte[] bx = new byte[32];
		byte[] by = new byte[32];

		//System.out.println("CompKey = "+ArrayUtil.toHex(ck));
		//System.out.println("Decompressed Key = "+ArrayUtil.toHex(dk));
		System.arraycopy(dk,1,bx,0,32);
		//System.out.println("Point X = "+ArrayUtil.toHex(bx));
		System.arraycopy(dk,33,by,0,32);
		//System.out.println("Point Y = "+ArrayUtil.toHex(by));

		ECPoint ecPoint = new ECPoint(new BigInteger(bx), new BigInteger(by));
		try {
			AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
			parameters.init(new ECGenParameterSpec("secp256k1"));		
			ECParameterSpec ecParameters = parameters.getParameterSpec(ECParameterSpec.class);
			ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(ecPoint, ecParameters);         

			PublicKey key = KeyFactory.getInstance("EC").generatePublic(pubKeySpec);
         //System.out.println("Encoded Public Key 1 = "+ArrayUtil.toHex(key.getEncoded()));
			return key;
		} catch (Exception e) {
			e.printStackTrace();
		}
      return null;
   }

   public String ecdhEncryption(PublicKey receiverPublicKey, String plainText) {
      ECDHCrypto ecdh= new ECDHCrypto();            
      String encryptedText = ArrayUtil.toHex(ecdh.encryption(new KeyPair(this.public_key,this.private_key), receiverPublicKey, plainText));      
      return encryptedText;
   }

   public String ecdhDecryption(PublicKey senderPublicKey, String encryptedText) {
      ECDHCrypto ecdh= new ECDHCrypto();            
      
      String decryptedText = ecdh.decryption(new KeyPair(this.public_key,this.private_key), senderPublicKey, ArrayUtil.toByte(encryptedText));
      return decryptedText;
   }

   public static void main(String args[]) {
      String walletName = "myWallet";
      String passWord = "1234";
      
      ECDSAWallet myWallet = new ECDSAWallet();
      String account = myWallet.generate(walletName,passWord);
      System.out.println("Generated accout="+account);      
      
      try {
         myWallet.write();
         account = myWallet.read(walletName);
         System.out.println("Read accout="+account);      

         String sign = myWallet.sign("abcd12345678",passWord);
         System.out.println("veryfi="+myWallet.verify(sign,"abcd12345678"));
      } catch(Exception e) {
         e.printStackTrace();
      }
   }
}