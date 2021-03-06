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

package org.apache.shardingsphere.core.rewrite;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.core.constant.DatabaseType;
import org.apache.shardingsphere.core.optimize.result.OptimizeResult;
import org.apache.shardingsphere.core.parse.sql.statement.SQLStatement;
import org.apache.shardingsphere.core.parse.sql.statement.dml.SelectStatement;
import org.apache.shardingsphere.core.parse.sql.token.SQLToken;
import org.apache.shardingsphere.core.parse.sql.token.Substitutable;
import org.apache.shardingsphere.core.parse.sql.token.impl.AggregationDistinctToken;
import org.apache.shardingsphere.core.rewrite.builder.ParameterBuilder;
import org.apache.shardingsphere.core.rewrite.builder.SQLBuilder;
import org.apache.shardingsphere.core.rewrite.rewriter.EncryptSQLRewriter;
import org.apache.shardingsphere.core.rewrite.rewriter.ShardingSQLRewriter;
import org.apache.shardingsphere.core.route.SQLRouteResult;
import org.apache.shardingsphere.core.route.SQLUnit;
import org.apache.shardingsphere.core.route.type.RoutingUnit;
import org.apache.shardingsphere.core.route.type.TableUnit;
import org.apache.shardingsphere.core.rule.BaseRule;
import org.apache.shardingsphere.core.rule.BindingTableRule;
import org.apache.shardingsphere.core.rule.EncryptRule;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.core.strategy.encrypt.ShardingEncryptorEngine;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL rewrite engine.
 * 
 * <p>Rewrite logic SQL to actual SQL.</p>
 *
 * @author panjuan
 */
@RequiredArgsConstructor
public final class SQLRewriteEngine {
    
    private final BaseRule baseRule;
    
    private final String originalSQL;
    
    private final DatabaseType databaseType;
    
    private final SQLRouteResult sqlRouteResult;
    
    private final SQLStatement sqlStatement;
    
    private final SQLBuilder sqlBuilder;
    
    public SQLRewriteEngine(final ShardingRule shardingRule, final String originalSQL, final DatabaseType databaseType, final SQLRouteResult sqlRouteResult, final List<Object> parameters) {
        this(shardingRule, originalSQL, databaseType, sqlRouteResult, sqlRouteResult.getSqlStatement(), new SQLBuilder(new ParameterBuilder(parameters)));
        pattern(sqlRouteResult.getOptimizeResult());
    }
    
    public SQLRewriteEngine(final EncryptRule encryptRule, final String originalSQL, final SQLStatement sqlStatement, final OptimizeResult optimizeResult, final List<Object> parameters) {
        this(encryptRule, originalSQL, DatabaseType.MySQL, null, sqlStatement, new SQLBuilder(new ParameterBuilder(parameters)));
        pattern(optimizeResult);
    }
    
    public SQLRewriteEngine(final String originalSQL, final SQLStatement sqlStatement) {
        this(null, originalSQL, null, null, sqlStatement, new SQLBuilder(new ParameterBuilder(Collections.emptyList())));
        pattern(null);
    }
    
    private void pattern(final OptimizeResult optimizeResult) {
        if (sqlStatement.getSQLTokens().isEmpty()) {
            sqlBuilder.appendLiterals(originalSQL);
            return;
        }
        rewrite(optimizeResult);
    }
    
    private void rewrite(final OptimizeResult optimizeResult) {
        rewriteInitialLiteral();
        int count = 0;
        for (SQLToken each : sqlStatement.getSQLTokens()) {
            new EncryptSQLRewriter(getShardingEncryptorEngine(), sqlStatement, optimizeResult).rewrite(sqlBuilder, each);
            new ShardingSQLRewriter(getShardingRule(), originalSQL, databaseType, sqlStatement, sqlRouteResult).rewrite(sqlBuilder, each);
            rewriteRestLiteral(sqlBuilder, each, count);
            count++;
        }
    }
    
    private void rewriteInitialLiteral() {
        if (isRewriteDistinctLiteral()) {
            appendAggregationDistinctLiteral(sqlBuilder);
        } else {
            sqlBuilder.appendLiterals(originalSQL.substring(0, sqlStatement.getSQLTokens().get(0).getStartIndex()));
        }
    }
    
    private boolean isRewriteDistinctLiteral() {
        return null != sqlRouteResult && !sqlRouteResult.getRoutingResult().isSingleRouting() && isContainsAggregationDistinctToken();
    }
    
    private boolean isContainsAggregationDistinctToken() {
        return Iterators.tryFind(sqlStatement.getSQLTokens().iterator(), new Predicate<SQLToken>() {
            
            @Override
            public boolean apply(final SQLToken input) {
                return input instanceof AggregationDistinctToken;
            }
        }).isPresent();
    }
    
    private void appendAggregationDistinctLiteral(final SQLBuilder sqlBuilder) {
        StringBuilder stringBuilder = new StringBuilder();
        int firstSelectItemStartIndex = ((SelectStatement) sqlStatement).getFirstSelectItemStartIndex();
        stringBuilder.append(originalSQL.substring(0, firstSelectItemStartIndex)).append("DISTINCT ")
                .append(originalSQL.substring(firstSelectItemStartIndex, sqlStatement.getSQLTokens().get(0).getStartIndex()));
        sqlBuilder.appendLiterals(stringBuilder.toString());
    }
    
    private ShardingEncryptorEngine getShardingEncryptorEngine() {
        if (null == baseRule) {
            return null;
        }
        return baseRule instanceof ShardingRule ? ((ShardingRule) baseRule).getShardingEncryptorEngine() : ((EncryptRule) baseRule).getEncryptorEngine();
    }
    
    private ShardingRule getShardingRule() {
        return baseRule instanceof ShardingRule ? (ShardingRule) baseRule : null;
    }
    
    private void rewriteRestLiteral(final SQLBuilder sqlBuilder, final SQLToken sqlToken, final int count) {
        int stopPosition = sqlStatement.getSQLTokens().size() - 1 == count ? originalSQL.length() : sqlStatement.getSQLTokens().get(count + 1).getStartIndex();
        sqlBuilder.appendLiterals(originalSQL.substring(getStartIndex(sqlToken) > originalSQL.length() ? originalSQL.length() : getStartIndex(sqlToken), stopPosition));
    }
    
    private int getStartIndex(final SQLToken sqlToken) {
        return sqlToken instanceof Substitutable ? ((Substitutable) sqlToken).getStopIndex() + 1 : sqlToken.getStartIndex();
    }
    
    /**
     * Generate SQL.
     * 
     * @return sql unit
     */
    public SQLUnit generateSQL() {
        return sqlBuilder.toSQL();
    }
    
    /**
     * Generate SQL.
     * 
     * @param routingUnit routing unit
     * @return sql unit
     */
    public SQLUnit generateSQL(final RoutingUnit routingUnit) {
        return sqlBuilder.toSQL(routingUnit, getTableTokens(routingUnit));
    }
   
    private Map<String, String> getTableTokens(final RoutingUnit routingUnit) {
        Map<String, String> result = new HashMap<>();
        for (TableUnit each : routingUnit.getTableUnits()) {
            String logicTableName = each.getLogicTableName().toLowerCase();
            result.put(logicTableName, each.getActualTableName());
            Optional<BindingTableRule> bindingTableRule = ((ShardingRule) baseRule).findBindingTableRule(logicTableName);
            if (bindingTableRule.isPresent()) {
                result.putAll(getBindingTableTokens(routingUnit.getMasterSlaveLogicDataSourceName(), each, bindingTableRule.get()));
            }
        }
        return result;
    }
    
    private Map<String, String> getBindingTableTokens(final String dataSourceName, final TableUnit tableUnit, final BindingTableRule bindingTableRule) {
        Map<String, String> result = new HashMap<>();
        for (String each : sqlStatement.getTables().getTableNames()) {
            String tableName = each.toLowerCase();
            if (!tableName.equals(tableUnit.getLogicTableName().toLowerCase()) && bindingTableRule.hasLogicTable(tableName)) {
                result.put(tableName, bindingTableRule.getBindingActualTable(dataSourceName, tableName, tableUnit.getActualTableName()));
            }
        }
        return result;
    }
}
