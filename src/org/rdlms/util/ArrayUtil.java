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

package org.rdlms.util;
 
public class ArrayUtil {
  
 	public static byte[] shortToBA(short value) {
		byte[] byteArray = new byte[2];
		byteArray[0] = (byte)(value >> 8);
		byteArray[1] = (byte)(value);
		return byteArray;
	}
	
	public static byte[] intToBA(int value) {
		byte[] byteArray = new byte[4];
			byteArray[0] = (byte)(value >> 24);
			byteArray[1] = (byte)(value >> 16);
			byteArray[2] = (byte)(value >> 8);
			byteArray[3] = (byte)(value);
		return byteArray;
	}
	
	public static byte[] longToBA(long value) {
		byte[] byteArray = new byte[8];
		  byteArray[0] = (byte)(value >> 56);
			byteArray[1] = (byte)(value >> 48);
			byteArray[2] = (byte)(value >> 40);
			byteArray[3] = (byte)(value >> 32);
			byteArray[4] = (byte)(value >> 24);
			byteArray[5] = (byte)(value >> 16);
			byteArray[6] = (byte)(value >> 8);
			byteArray[7] = (byte)(value);
		return byteArray;
	}
  
    public static int BAToShort(byte ba[]) {
		return ((((int)ba[0] & 0xff) << 8) |
		(((int)ba[1] & 0xff)));
	} 
	
	public static int BAToShort(byte ba[],int offset) {
		return ((((int)ba[offset+0] & 0xff) << 8) |
		(((int)ba[offset+1] & 0xff)));
	}
	
	public static int BAToInt(byte ba[]) {
		return ((((int)ba[0] & 0xff) << 24) |
		(((int)ba[1] & 0xff) << 16) |
		(((int)ba[2] & 0xff) << 8) |
		(((int)ba[3] & 0xff)));
	}
	
	public static int BAToInt(byte ba[],int offset) {
		return ((((int)ba[offset+0] & 0xff) << 24) |
		(((int)ba[offset+1] & 0xff) << 16) |
		(((int)ba[offset+2] & 0xff) << 8) |
		(((int)ba[offset+3] & 0xff)));
	}
			
	public static long BAToLong(byte ba[]) {
		return ((((long)ba[0] & 0xff) << 56) |
		(((long)ba[1] & 0xff) << 48) |
		(((long)ba[2] & 0xff) << 40) |
		(((long)ba[3] & 0xff) << 32) | 
		(((long)ba[4] & 0xff) << 24) |
		(((long)ba[5] & 0xff) << 16) |
		(((long)ba[6] & 0xff) << 8) |
		(((long)ba[7] & 0xff)));
	}
			
	public static long BAToLong(byte ba[], int offset ){
		return ((((long)ba[offset+0] & 0xff) << 56) |
		(((long)ba[offset+1] & 0xff) << 48) |
		(((long)ba[offset+2] & 0xff) << 40) |
		(((long)ba[offset+3] & 0xff) << 32) | 
		(((long)ba[offset+4] & 0xff) << 24) |
		(((long)ba[offset+5] & 0xff) << 16) |
		(((long)ba[offset+6] & 0xff) << 8) |
		(((long)ba[offset+7] & 0xff)));
	}
	
	public static byte[] toByte(String hexString)
	{
		int len = hexString.length()/2;
		byte[] result = new byte[len];
		for (int i = 0; i < len; i++)
			result[i] = Integer.valueOf(hexString.substring(2*i, 2*i+2), 16).byteValue();
		return result;
	}

	public static String toHex(byte[] buf)
	{
		if (buf == null)
			return "";
		StringBuffer result = new StringBuffer(2*buf.length);
		for (int i = 0; i < buf.length; i++) {
			appendHex(result, buf[i]);
		}
		return result.toString();
	}
	
	private final static String HEX = "0123456789ABCDEF";
	private static void appendHex(StringBuffer sb, byte b)
	{
		sb.append(HEX.charAt((b>>4)&0x0f)).append(HEX.charAt(b&0x0f));
	}		
}
