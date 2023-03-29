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

package org.rdlms.crypto;

import java.security.*;
import java.io.*;
import java.security.spec.*;
import java.security.interfaces.*;
import java.math.*;
import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.util.Arrays;
import java.util.Base64;
import org.rdlms.util.ArrayUtil;


public class ECDSA {

	final static String iv = "0000000000000000";
	final static String alg = "AES/CBC/PKCS5Padding";
	//final static String alg = "AES/CBC/NoPadding";
	
	public static void serializeKeyPair(KeyPair pair, String fname) {
		try { 
			FileOutputStream fos = new FileOutputStream(fname); 
			BufferedOutputStream bos = new BufferedOutputStream(fos); 
			ObjectOutputStream out = new ObjectOutputStream(bos); 			
			out.writeObject(pair); 			
			out.close();
			} catch (Exception e) { e.printStackTrace(); } 
	}
		
	public static KeyPair deSerializeKeyPair(String fname) {
		KeyPair pair=null;
		try { 
			FileInputStream fis = new FileInputStream(fname); 
			BufferedInputStream bis = new BufferedInputStream(fis); 
			ObjectInputStream in = new ObjectInputStream(bis); 
			
			pair = (KeyPair) in.readObject();
			
			in.close();			
			
		} catch (Exception e) { /*e.printStackTrace(); */ }				
		return pair;		
	}
	
		
	public static String getCompKey(PublicKey pub) {
		ECPublicKey ecPub = (ECPublicKey)pub;
		
		ECPoint ecp = ecPub.getW();
		BigInteger x = ecp.getAffineX();
		BigInteger y = ecp.getAffineY();
		
		BigInteger mod = y.mod(new BigInteger("2",16));
		String compressedPubStr;
		
		compressedPubStr = ArrayUtil.toHex(x.toByteArray());
		//System.out.println("pub X1["+compressedPubStr.length()+"] ="+compressedPubStr);
		if(compressedPubStr.substring(0,2).equals("00")) {
			compressedPubStr = compressedPubStr.substring(2,compressedPubStr.length());
		//	System.out.println("pub X2["+compressedPubStr.length()+"] ="+compressedPubStr);
			if(compressedPubStr.length()==62) {
				compressedPubStr = "00"+compressedPubStr;
			}
		//	System.out.println("pub X3["+compressedPubStr.length()+"] ="+compressedPubStr);
		}
		if (mod.intValue()==0) {
			compressedPubStr = "02"+compressedPubStr;
		} else {
			compressedPubStr = "03"+compressedPubStr;
		}
		return compressedPubStr;
	}	

	

	static final BigInteger MODULUS =
    new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
	static final BigInteger CURVE_A = new BigInteger("0");
	static final BigInteger CURVE_B = new BigInteger("7");


// Given a 33-byte compressed public key, this returns a 65-byte uncompressed key.
	public static byte[] decompressPubkey(byte[] compKey) {
		// Check array length and type indicator byte
		if (compKey.length != 33 || compKey[0] != 2 && compKey[0] != 3)
			throw new IllegalArgumentException();

		final byte[] xCoordBytes = Arrays.copyOfRange(compKey, 1, compKey.length);
		final BigInteger xCoord = new BigInteger(1, xCoordBytes);  // Range [0, 2^256)
		
		BigInteger temp = xCoord.pow(2).add(CURVE_A);
		temp = temp.multiply(xCoord);
		temp = sqrtMod(temp.add(CURVE_B));		
		boolean tempIsOdd = temp.testBit(0);
		boolean yShouldBeOdd = compKey[0] == 3;
		if (tempIsOdd != yShouldBeOdd)
			temp = temp.negate().mod(MODULUS);
		final BigInteger yCoord = temp;

		// Copy the x coordinate into the new
		// uncompressed key, and change the type byte
		byte[] result = Arrays.copyOf(compKey, 65);
		result[0] = 4;

		// Carefully copy the y coordinate into uncompressed key
		final byte[] yCoordBytes = yCoord.toByteArray();
		for (int i = 0; i < 32 && i < yCoordBytes.length; i++)
			result[result.length - 1 - i] = yCoordBytes[yCoordBytes.length - 1 - i];

		return result;
	}

	// Given x, this returns a value y such that y^2 % MODULUS == x.
	public static BigInteger sqrtMod(BigInteger value) {
		assert (MODULUS.intValue() & 3) == 3;
		BigInteger pow = MODULUS.add(BigInteger.ONE).shiftRight(2);
		BigInteger result = value.modPow(pow, MODULUS);
		assert result.pow(2).mod(MODULUS).equals(value);
		return result;
	}
	
	public static String transform_for_sign(String inputs) {
		String retstr;
		
		retstr=inputs.trim();
		retstr=retstr.replaceAll("\r"," ");
		retstr=retstr.replaceAll("\n"," ");
		retstr=retstr.replaceAll("\t"," ");
		retstr=retstr.replaceAll("\\s+"," ");
		
		return retstr;
	}
	
	public static String getSign(String inputs, PrivateKey priv_key) {	 
		Signature ecdsa;
		byte[] realSig={0}; 
		byte[] strByte;
		try{	
		ecdsa = Signature.getInstance("SHA256withECDSA");
		ecdsa.initSign(priv_key);	
		strByte = inputs.getBytes("UTF-8");
		ecdsa.update(strByte);
		realSig = ecdsa.sign();
		} catch(Exception e) { e.printStackTrace(); }	
		return ArrayUtil.toHex(realSig);
	}

	public static boolean verify(String sign, String input, PublicKey pub) {
		Signature ecdsa;
		boolean ret=false;
		try{	
			ecdsa = Signature.getInstance("SHA256withECDSA");
			ecdsa.initVerify(pub);	
			ecdsa.update(input.getBytes());
			ret = ecdsa.verify(ArrayUtil.toByte(sign));		
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

/*
public static String getCompKey(PublicKey pub) {
ECPublicKey ecPub = (ECPublicKey)pub;

ECPoint ecp = ecPub.getW();
BigInteger x = ecp.getAffineX();
BigInteger y = ecp.getAffineY();

String compressedPubStr;

	String pubKeyYprefix = x.testBit(0) ? "03" : "02";
String pubKeyHex = ArrayUtil.toHex(x.toByteArray());
String pubKeyX = pubKeyHex.substring(0,64);

	return pubKeyYprefix + pubKeyX; 

} */

	/*	
	public static String aes256_encrypt(String strKey, String plainText) {
		try {
			Cipher cipher = Cipher.getInstance(alg);
			SecretKeySpec keySpec = new SecretKeySpec(iv.getBytes(), "AES");
			IvParameterSpec ivParamSpec = new IvParameterSpec(iv.getBytes());
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivParamSpec);

			byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
			//System.out.println("encrypted="+ArrayUtil.toHex(encrypted));
			return Base64.getEncoder().encodeToString(encrypted);
		} catch (Exception e) { e.printStackTrace(); }
		return null;	
	}

	public static String aes256_decrypt(String strKey, String strEncryptedText) {
		try {
			Cipher cipher = Cipher.getInstance(alg);
			SecretKeySpec keySpec = new SecretKeySpec(iv.getBytes(), "AES");
			IvParameterSpec ivParamSpec = new IvParameterSpec(iv.getBytes());
			cipher.init(Cipher.DECRYPT_MODE, keySpec, ivParamSpec);

			byte[] decodedBytes = Base64.getDecoder().decode(strEncryptedText);
			byte[] decrypted = cipher.doFinal(decodedBytes);
			return new String(decrypted, "UTF-8");
		} catch (Exception e) { e.printStackTrace(); }
		return null;	
	}
	*/

	/* aes256_encrypt
		encrypt key is message digest (SHA256) value of strKey
	*/
	public static byte[] aes256_encrypt(String strKey, byte[] plainText) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(strKey.getBytes(StandardCharsets.UTF_8));
			
			Cipher cipher = Cipher.getInstance(alg);
			SecretKeySpec keySpec = new SecretKeySpec(md.digest(), "AES");
			IvParameterSpec ivParamSpec = new IvParameterSpec(iv.getBytes());
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivParamSpec);

			byte[] encrypted = cipher.doFinal(plainText);			
			return encrypted;
		} catch (Exception e) { e.printStackTrace(); }
		return null;	
	}

	public static byte[] aes256_decrypt(String strKey, byte[] encryptedText) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(strKey.getBytes(StandardCharsets.UTF_8));
			
			Cipher cipher = Cipher.getInstance(alg);
			SecretKeySpec keySpec = new SecretKeySpec(md.digest(), "AES");
			IvParameterSpec ivParamSpec = new IvParameterSpec(iv.getBytes());
			cipher.init(Cipher.DECRYPT_MODE, keySpec, ivParamSpec);
			
			byte[] decrypted = cipher.doFinal(encryptedText);
			return decrypted;
		} catch (Exception e) { 
			System.out.println(e.getMessage());
		}
		return null;	
	}
	

	public static void main(String args[]) {
		byte[] baEnc = ECDSA.aes256_encrypt("1223","data".getBytes());
		byte[] dec = ECDSA.aes256_decrypt("1223",baEnc);
	}
}
