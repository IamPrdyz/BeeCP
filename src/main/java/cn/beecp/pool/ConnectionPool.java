/*
 * Copyright Chris2018998
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.beecp.pool;

import cn.beecp.BeeDataSourceConfig;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Connection pool
 * 
 * @author Chris.Liao
 * @version 1.0
 */
public interface ConnectionPool {

	/**
	 * initialize pool with configuration
	 * 
	 * @param config data source configuration
	 * @throws SQLException check configuration fail or to create initiated connection 
	 */
	void init(BeeDataSourceConfig config)throws SQLException;
	
	/**
	 * borrow a connection from pool
	 * @return If exists idle connection in pool,then return one;if not, waiting until other borrower release
	 * @throws SQLException if pool is closed or waiting timeout,then throw exception
	 */
	Connection getConnection() throws SQLException;

	/**
	 * borrow one connection from pool
	 * 
	 * @param wait must be greater than zero
	 *             
	 * @return If exists idle connection in pool,then return one;if not, waiting
	 *         until other borrower release
	 * @throws SQLException
	 *             if pool is closed or waiting timeout,then throw exception
	 */
	Connection getConnection(long wait) throws SQLException;
	
	/**
	 * return connection to pool
	 * @param pConn target connection need release
	 */
	 void recycle(PooledConnection pConn);
		
	/**
	 * close pool
	 */
	void shutdown();
	
}
	
