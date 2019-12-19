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

import static cn.beecp.pool.PoolObjectsState.CONNECTION_IDLE;
import static cn.beecp.util.BeecpUtil.oclose;
import static cn.beecp.util.BeecpUtil.isNullText;
import static java.lang.System.currentTimeMillis;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.beecp.BeeDataSourceConfig;

/**
 * Pooled Connection
 *
 * @author Chris.Liao
 * @version 1.0
 */
final class PooledConnection{
	volatile int state;
	boolean stmCacheIsValid;
	StatementCache stmCache=null;
	BeeDataSourceConfig pConfig;
	Connection rawConn;
	ProxyConnectionBase proxyConn;
	long lastAccessTime;
	boolean commitDirtyInd;
	boolean curAutoCommit;
	private ConnectionPool pool;
	private Logger log = LoggerFactory.getLogger(this.getClass());
	
	//changed indicator
	private boolean[] changedInds=new boolean[4]; //0:autoCommit,1:transactionIsolation,2:readOnly,3:catalog
	private short changedBitVal=Byte.parseByte("0000",2); //pos:last ---> head;0:unchanged,1:changed
	static short Pos_AutoCommitInd=0;
	static short Pos_TransactionIsolationInd=1;
	static short Pos_ReadOnlyInd=2;
	static short Pos_CatalogInd=3;
	
	public PooledConnection(Connection rawConn,ConnectionPool connPool,BeeDataSourceConfig config)throws SQLException{
		 this(rawConn,connPool,config,CONNECTION_IDLE);
	}
	public PooledConnection(Connection rawConn,ConnectionPool connPool,BeeDataSourceConfig config,int connState)throws SQLException{
		pool=connPool;
		this.rawConn=rawConn;

		state=connState;
		pConfig=config;
		curAutoCommit=pConfig.isDefaultAutoCommit();
		if (stmCacheIsValid = pConfig.getPreparedStatementCacheSize() > 0) {
			stmCache = new StatementCache(pConfig.getPreparedStatementCacheSize());
		}
		setDefaultOnRawConn();
	}
	private void setDefaultOnRawConn()throws SQLException{
		rawConn.setAutoCommit(pConfig.isDefaultAutoCommit());
		rawConn.setTransactionIsolation(pConfig.getDefaultTransactionIsolation());
		rawConn.setReadOnly(pConfig.isDefaultReadOnly());
		if(!isNullText(pConfig.getDefaultCatalog()))
			rawConn.setCatalog(pConfig.getDefaultCatalog());
		updateAccessTime();
	}
	public boolean equals(Object obj) {
		return this==obj;
	}
	//called for pool
	void closeRawConn() {
		try{
			resetRawConnOnReturn();
			if(stmCacheIsValid)
				stmCache.clear();

			pConfig=null;
			oclose(rawConn);
		}finally{
			if(proxyConn!=null){
				proxyConn.setConnectionDataToNull();
				proxyConn=null;
			}
		}
	}


	//***************called fow raw conn proxy ********//
	final void returnToPoolBySelf(){
		try{
			resetRawConnOnReturn();
			pool.release(this,true);
		}finally{
			proxyConn = null;
		}
	}
	void setCurAutoCommit(boolean curAutoCommit) {
		this.curAutoCommit = curAutoCommit;
	}
	void updateAccessTime() {
		lastAccessTime = currentTimeMillis();
	}
	void updateAccessTimeWithCommitDirty() {
		lastAccessTime=currentTimeMillis();
		commitDirtyInd=!curAutoCommit;
	}
	int getChangedInd(int pos){
		return (changedBitVal >>pos)&1;
	}
    void setChangedInd(int pos,boolean changed){
		changedInds[pos]=changed;
    	changedBitVal^=(changedBitVal&(1<<pos))^((changed?1:0)<<pos);
    }
	//reset connection on return to pool
	private void resetRawConnOnReturn() {
		if (!curAutoCommit&&commitDirtyInd){//Roll back when commit dirty
			try {
				rawConn.rollback();
			} catch (SQLException e) {
				log.error("Failed to rollback on return to pool", e);
			}finally{
				commitDirtyInd=false;
			}
		}

		//reset begin 
		if (changedBitVal > 0) {//reset autoCommit
			if (changedInds[0]) {
				try {
					rawConn.setAutoCommit(pConfig.isDefaultAutoCommit());
					curAutoCommit=pConfig.isDefaultAutoCommit();
					updateAccessTime();
				} catch (SQLException e) {
					log.error("Failed to reset autoCommit to:{}",pConfig.isDefaultAutoCommit(),e);
				}finally{
					changedInds[0]=false;
				}
			}

			if (changedInds[1]) {
				try {
					rawConn.setTransactionIsolation(pConfig.getDefaultTransactionIsolation());
					updateAccessTime();
				} catch (SQLException e) {
					log.error("Failed to reset transactionIsolation to:{}",pConfig.getDefaultTransactionIsolation(),e);
				}finally {
					changedInds[1] = false;
				}
			}

			if (changedInds[2]) {//reset readonly
				try {
					rawConn.setReadOnly(pConfig.isDefaultReadOnly());
					updateAccessTime();
				} catch (SQLException e) {
					log.error("Failed to reset readOnly to:{}",pConfig.isDefaultReadOnly(),e);
				}finally{
					changedInds[2] = false;
				}
			}

			if (changedInds[3]) {//reset catalog
				try {
					rawConn.setCatalog(pConfig.getDefaultCatalog());
					updateAccessTime();
				} catch (SQLException e) {
					log.error("Failed to reset catalog to:{}",pConfig.getDefaultCatalog(),e);
				}finally{
					changedInds[3] = false;
				}
			}
			
			changedBitVal=0;
		}
		//reset end
		
		try {//clear warnings
			rawConn.clearWarnings();
		} catch (SQLException e) {
			log.error("Failed to clear warnings",e);
		}
	}
}
