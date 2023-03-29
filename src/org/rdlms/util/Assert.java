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

package org.rdlms.util;

import org.apache.log4j.Logger;
 
public class Assert {
	
	public static void assertTrue(boolean condition, String msg) {
		if(!condition) {
			System.out.println("\n");
			System.out.println("##########  Assertion Fail! ##########");
			System.out.println("\n");
			System.out.println(msg);
			System.out.println("\n");
			System.out.println("##########  Assertion Fail! ##########");
			System.out.println("\n");
			System.exit(1);
		}	
	}

	public static void assertTrue(Logger log, boolean condition, String msg) {
		if(!condition) {
			log.fatal("\n");
			log.fatal("##########  Assertion Fail! ##########");
			log.fatal("\n");
			log.fatal(msg);
			log.fatal("\n");
			log.fatal("##########  Assertion Fail! ##########");
			log.fatal("\n");
			System.exit(1);
		}
	}
	

}	