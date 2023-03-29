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

package org.rdlms.messagestorage.model;
 
public class TOSTransaction {	  
   public String  gt_name;             // global table name  ex) www.trustedhotel.com/p2pcashsystem/mint_mypoint	
   public long    order_in_service;    // TOS_ID
   public long    order_in_table;      // TOS_ID
	public String  stamped_transaction; // SQL Statment filled with TOSA's order & sign. 	   
   public String  tosa_account;        // tosa_account
   public String  tosa_sign;	         // (gt_name,order_in_service,order_in_table,stamped_transactions) signed by (TOSA Account)\
   
   public String toString() {
      String tempStr;
      tempStr= "\n";
      tempStr+="GT_NAME=\t"+gt_name+"\n";
      tempStr+="ORDER_IN_SERVICE=\t"+order_in_service+"\n";
      tempStr+="ORDER_IN_TABLE=\t"+order_in_table+"\n";
      tempStr+="STAMPED_TRANSACTION=\t"+stamped_transaction+"\n";
      tempStr+="TOSA_ACCOUNT=\t"+tosa_account+"\n";
      tempStr+="TOSA_SIGN=\t"+tosa_sign;
      tempStr= "\n";
      return tempStr;
   }
}