/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.spi.hook;

import org.apache.shardingsphere.core.metadata.table.ShardingTableMetaData;
import org.apache.shardingsphere.core.parsing.parser.sql.SQLStatement;

/**
 * Parsing hook.
 *
 * @author zhangliang
 */
public interface ParsingHook {
    
    /**
     * Handle when parse started.
     *
     * @param sql SQL to be parsed
     */
    void start(String sql);
    
    /**
     * Handle when parse finished success.
     *
     * @param sqlStatement sql statement
     * @param shardingTableMetaData sharding table meta data
     */
    void finishSuccess(SQLStatement sqlStatement, ShardingTableMetaData shardingTableMetaData);
    
    /**
     * Handle when parse finished failure.
     * 
     * @param cause failure cause
     */
    void finishFailure(Exception cause);
}