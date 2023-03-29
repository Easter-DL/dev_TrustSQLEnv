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

package org.rdlms.toss.server;

import org.rdlms.messagestorage.model.TOSTransaction;
import org.rdlms.toss.connector.TOSSConnector.ServiceInfo;

public interface TOSSInterface {

    /**
     * Publish user transaction to TOSS and get back ordering-stamped transaction, a receipt. 	
	 *@param	fullServiceName(=SERVICE ID)
     *@param    PAYLOAD (transaction) 	
	 *@return   errorCode
     *@return   tosTrnsaction (Stamped PAYLAOD with Order)
	 */    
    public int publishTransaction(String fullServiceName, String payload, TOSTransaction tosTransaction) throws Exception;
    
    /**	
     * Pull all transactions that correspond to the fullServiceName and order number is after the lastID from TOSS.
	 *@param	fullServiceName
     /@param    lastID
	 *@return   tosTransaction[]
	 */
    public TOSTransaction[] getTOSTransactions(String fullServiceName,long lastID) throws Exception;

	/**
     * Pull serviceInfo seviced by TOSS. 	
	 *@param	 fullServiceName	
	 *@return ServiceInfo
	*/
	public ServiceInfo getServiceInfo(String serviceName) throws Exception;

    /**
     * Pull serviceStatus seviced by TOSS. 	
     * serviceName, ThreadID, Table Name
     * service Order
     * Table Order.
	 *@param	 fullServiceName	
	 *@return ServiceInfo
	*/
	//public ServiceStatus getServiceStatus(String serviceName);

    /**
     * add new service to TOSS	
     * add config for the new service to cofnfig file and start new thread for new service
	 *@param
	 *@return 
	*/
    public int addService(String serviceName, String strJson) throws Exception;

    /**
     * stop servce to TOSS	     * 
	 *@param
	 *@return 
	*/
    public int stopService(int threadID) throws Exception;

    /**
     * stop servce to TOSS	
	 *@param
	 *@return 
	*/
    public int startService(int threadID) throws Exception;
	
}
