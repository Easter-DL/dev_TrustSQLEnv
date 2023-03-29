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
 
public class ServiceOrder {	  
   public final static long MAX_RECORDS_IN_ORDER_SERVICE=50000000;
   public final static long RECORD_SIZE=80;

   public long    order_in_service;    // order in a service
   public String  table_name;          // table name
	public long    order_in_table;      // order in a table

   public String toString() {
      String tempStr;
      tempStr= "order_in_service=\t"+order_in_service+"\n";
      tempStr+="table_name=\t"+table_name+"\n";
      tempStr+="order_in_table=\t"+order_in_table;
      return tempStr;
   }
}