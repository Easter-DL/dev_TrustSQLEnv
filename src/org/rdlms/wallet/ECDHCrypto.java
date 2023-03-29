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
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.nio.ByteBuffer;

import org.rdlms.util.ArrayUtil;

public class ECDHCrypto {
    final static String iv = "0000000000000000";
    final static String alg = "AES/CBC/PKCS5Padding";

    public byte[] aes256_encrypt(byte[] bKey, String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(alg);
            SecretKeySpec keySpec = new SecretKeySpec(bKey, "AES");
            IvParameterSpec ivParamSpec = new IvParameterSpec(iv.getBytes());
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivParamSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));			
            return encrypted;
        } catch (Exception e) { e.printStackTrace(); }
        return null;	
    }

    public String aes256_decrypt(byte[] bKey, byte[] encryptedText) {
        try {
            Cipher cipher = Cipher.getInstance(alg);
            SecretKeySpec keySpec = new SecretKeySpec(bKey, "AES");
            IvParameterSpec ivParamSpec = new IvParameterSpec(iv.getBytes());
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivParamSpec);
            byte[] decrypted = cipher.doFinal(encryptedText);
            return new String(decrypted,"UTF-8");
        } catch (Exception e) { 
        e.printStackTrace(); 
        }
        return null;
    }

    public byte[] encryption(KeyPair sourceKeyPair, PublicKey targetPublicKey, String plainText) {
        try {
            byte[] baSourcePubK = sourceKeyPair.getPublic().getEncoded();
                    
            byte[] baTargetPubK = targetPublicKey.getEncoded();
            //System.out.println("Public Key: "+ArrayUtil.toHex(baTargetPubK));

            KeyFactory kf = KeyFactory.getInstance("EC");
            X509EncodedKeySpec pkSpec = new X509EncodedKeySpec(baTargetPubK);
            PublicKey kfTargetPublicKey = kf.generatePublic(pkSpec);

            // Perform key agreement
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(sourceKeyPair.getPrivate());
            ka.doPhase(kfTargetPublicKey, true);

            // Read shared secret
            byte[] sharedSecret = ka.generateSecret();
            //System.out.println("Shared secret: "+ArrayUtil.toHex(sharedSecret));

            // Derive a key from the shared secret and both public keys
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(sharedSecret);
            // Simple deterministic ordering
            List<ByteBuffer> keys = Arrays.asList(ByteBuffer.wrap(baSourcePubK), ByteBuffer.wrap(baTargetPubK));
            Collections.sort(keys);
            hash.update(keys.get(0));
            hash.update(keys.get(1));

            byte[] derivedKey = hash.digest();
            //System.out.println("Final key: "+ArrayUtil.toHex(derivedKey));
            //System.out.println("Final key length = "+derivedKey.length);
                
            byte[] encrypted = aes256_encrypt(derivedKey,plainText);
            //System.out.println("encrypted Text = "+ArrayUtil.toHex(encrypted));
                    
            return encrypted;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String decryption(KeyPair sourceKeyPair, PublicKey targetPublicKey, byte[] encryptedText) {
        try {
            byte[] baSourcePubK = sourceKeyPair.getPublic().getEncoded();
                    
            byte[] baTargetPubK = targetPublicKey.getEncoded();
            //System.out.println("Public Key: "+ArrayUtil.toHex(baTargetPubK));

            KeyFactory kf = KeyFactory.getInstance("EC");
            X509EncodedKeySpec pkSpec = new X509EncodedKeySpec(baTargetPubK);
            PublicKey kfTargetPublicKey = kf.generatePublic(pkSpec);

            // Perform key agreement
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(sourceKeyPair.getPrivate());
            ka.doPhase(kfTargetPublicKey, true);

            // Read shared secret
            byte[] sharedSecret = ka.generateSecret();
            //System.out.println("Shared secret: "+ArrayUtil.toHex(sharedSecret));

            // Derive a key from the shared secret and both public keys
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(sharedSecret);
            // Simple deterministic ordering
            List<ByteBuffer> keys = Arrays.asList(ByteBuffer.wrap(baSourcePubK), ByteBuffer.wrap(baTargetPubK));
            Collections.sort(keys);
            hash.update(keys.get(0));
            hash.update(keys.get(1));

            byte[] derivedKey = hash.digest();
            //System.out.println("Final key: "+ArrayUtil.toHex(derivedKey));
            //System.out.println("Final key length = "+derivedKey.length);
                
            String decryptedText = aes256_decrypt(derivedKey,encryptedText);
                    
            return decryptedText;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public static void main(String[] args) throws Exception {
        ECDHCrypto ecdh = new ECDHCrypto();
        
        String plainText="Hey I'm plain";  
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        //kpg.initialize(256);
        kpg.initialize(new ECGenParameterSpec("secp256k1"), new SecureRandom());
        KeyPair akp = kpg.generateKeyPair();
        KeyPair bkp = kpg.generateKeyPair();

        byte[] encryptedText = ecdh.encryption(akp, bkp.getPublic(),plainText);
        System.out.println("Encrypted Text = "+ArrayUtil.toHex(encryptedText));
        String decryptedText = ecdh.decryption(bkp, akp.getPublic(),encryptedText);
        System.out.println("decrypted Text = "+decryptedText);

    }

}
