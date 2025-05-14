package main.com.custom;

import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;

import sailpoint.tools.GeneralException;

import org.apache.log4j.Logger;

import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import java.util.*;
import java.sql.*;

public class JDBCOperation {

    private final static Logger logger = Logger.getLogger("com.example.mycustomlogger");

    // Helper method to get attribute request value
    public static Object getAttributeRequestValue(AccountRequest acctReq, String attribute) {
        if (acctReq != null) {
            AttributeRequest attrReq = acctReq.getAttributeRequest(attribute);
            if (attrReq != null) {
                return attrReq.getValue();
            }
        }
        return null;
    }

    // Return ProvisioningResult and logs with error message
    private static ProvisioningResult nullCheckHandler(String errorMessage, ProvisioningResult result,
            Connection connection) {
        try {
            logger.error(errorMessage);
            result.setStatus(ProvisioningResult.STATUS_FAILED);
            result.addError(errorMessage);
            connection.rollback();
            return result;
        } catch (SQLException e1) {

            logger.error(e1);
            result.setStatus(ProvisioningResult.STATUS_FAILED);
            result.addError(e1);
            return result;
        }
    }

    // Execute quires with Prepared Statement
    public static void executeQuery(Connection connection, ProvisioningPlan plan, String query, List<String> paramNames,
            AccountRequest acctRequest) throws SQLException {

        PreparedStatement statement = connection.prepareStatement(query);
        for (int i = 0; i < paramNames.size(); i++) {
            String paramName = paramNames.get(i);

            if (paramName.equals("nativeIdentity")) {
                statement.setObject(i + 1, plan.getNativeIdentity());
            } else {
                statement.setObject(i + 1, getAttributeRequestValue(acctRequest, paramName));
            }
        }
        statement.executeUpdate();
        logger.info("Executed query for user: { " + plan.getNativeIdentity() + " }");
    }

    // Execute quires with Prepared Statement for entitlements add / remove
    public static void executeQueryEntitlement(Connection connection, ProvisioningPlan plan, String query,
            List<String> paramNames, AccountRequest acctRequest,
            AttributeRequest attRequest) throws SQLException {

        PreparedStatement statement = connection.prepareStatement(query);
        for (int i = 0; i < paramNames.size(); i++) {
            String paramName = paramNames.get(i);
            if (paramName.equals("nativeIdentity")) {
                statement.setObject(i + 1, plan.getNativeIdentity());
            } else if (paramName.equals("value")) {
                statement.setObject(i + 1, attRequest.getValue());
            } else {
                statement.setObject(i + 1, getAttributeRequestValue(acctRequest, paramName));
            }
        }
        statement.executeUpdate();
        logger.info("Executed query for user: { " + plan.getNativeIdentity() + " }");
    }

    // Execute quires with Callable Statement
    public static void executePreparedStatement(Connection connection, ProvisioningPlan plan, String query,
            List<String> paramNames, AccountRequest acctRequest) throws SQLException {

        CallableStatement statement = connection.prepareCall(query);
        for (int i = 0; i < paramNames.size(); i++) {
            String paramName = paramNames.get(i);
            if (paramName.equals("nativeIdentity")) {
                statement.setObject(i + 1, plan.getNativeIdentity());
            } else {
                statement.setObject(i + 1, getAttributeRequestValue(acctRequest, paramName));
            }
        }
        statement.execute();
        logger.info("Executed stored procedure for user: { " + plan.getNativeIdentity() + " }");
    }

    // Execute quires with Callable Statement for entitlements add / remove
    public static void executePreparedStatementEntitlement(Connection connection, ProvisioningPlan plan, String query,
            List<String> paramNames, AccountRequest acctRequest,
            AttributeRequest attRequest) throws SQLException {

        CallableStatement statement = connection.prepareCall(query);
        for (int i = 0; i < paramNames.size(); i++) {
            String paramName = paramNames.get(i);
            if (paramName.equals("nativeIdentity")) {
                statement.setObject(i + 1, plan.getNativeIdentity());
            } else if (paramName.equals("value")) {
                statement.setObject(i + 1, attRequest.getValue());
            } else {
                statement.setObject(i + 1, getAttributeRequestValue(acctRequest, paramName));
            }
        }
        statement.execute();
        logger.info("Executed stored procedure for user: { " + plan.getNativeIdentity() + " }");
    }

    // Handle all AccountRequest and AttributeRequest Operations
    public static ProvisioningResult provision(SailPointContext context, Connection connection, ProvisioningPlan plan,
            String custObj) throws GeneralException {

        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Initialize ProvisioningResult and Custom object
        Custom jdbcCustomObj = context.getObject(Custom.class, custObj);
        ProvisioningResult result = new ProvisioningResult();

        // Check if the provisioning plan is null
        if (plan == null) {
            return nullCheckHandler("ProvisioningPlan is null", result, connection);
        }

        logger.debug("plan [" + plan.toXml() + "]");
        // Retrieve account requests
        List<AccountRequest> accounts = plan.getAccountRequests();

        if ((accounts != null) && (accounts.size() > 0)) {

            for (AccountRequest account : accounts) {

                try {
                    if (AccountRequest.Operation.Create.equals(account.getOperation())) {

                        logger.info("====================== Create Operation =========================");
                        List<Map<String, Object>> queryList = (List<Map<String, Object>>) jdbcCustomObj
                                .get("insert");
                        Map<String, Object> entitlementAddQueriesMap = (Map<String, Object>) jdbcCustomObj
                                .get("addEntitlement");

                        if (queryList == null) {
                            String error = "Insert Query / Stored Procedure List is null";
                            return nullCheckHandler(error, result, connection);
                        }

                        // Traverse through Query List and access the Map object for essential data
                        for (Map<String, Object> queryData : queryList) {
                            String query = (String) queryData.get("query");
                            if (query == null) {
                                String error = "Insert Query / Stored Procedure is null";
                                return nullCheckHandler(error, result, connection);
                            }
                            boolean isPreparedStatement = Boolean
                                    .parseBoolean((String) queryData.get("isPreparedStatement"));
                            List<String> paramNames = (List<String>) queryData.get("parameters");

                            // Call appropriate method with respect to type of query
                            if (isPreparedStatement) {
                                executePreparedStatement(connection, plan, query, paramNames, account);
                            } else {
                                executeQuery(connection, plan, query, paramNames, account);
                            }
                        }

                        // Fetch AttributeRequest requests
                        List<AttributeRequest> attributeRequests = account.getAttributeRequests();

                        for (AttributeRequest attributeRequest : attributeRequests) {
                            String attributeName = attributeRequest.getName();
                            String attributeValue = (String) attributeRequest.getValue();

                            if (entitlementAddQueriesMap == null) {
                                String error = "Entitlement Add Query / Stored Procedure Map is null";
                                return nullCheckHandler(error, result, connection);
                            }

                            if (ProvisioningPlan.Operation.Add.equals(attributeRequest.getOperation())) {
                                List<Map<String, Object>> entitleMentQueryList = (List<Map<String, Object>>) entitlementAddQueriesMap
                                        .get(attributeName);

                            logger.info(
                                    "====================== Create Operation Add Entitlement =========================");
                                // Iterate on entilement add Queries
                                for (Map<String, Object> queryData : entitleMentQueryList) {
                                    String query = (String) queryData.get("query");
                                    boolean isPreparedStatement = Boolean
                                            .parseBoolean((String) queryData.get("isPreparedStatement"));
                                    List<String> paramNames = (List<String>) queryData.get("parameters");

                                    if (query == null) {
                                        String error = "Entitlement Add Query / Stored Procedure is null for "
                                                + attributeName;
                                        logger.warn(error);
                                        continue;
                                    }

                                    if (isPreparedStatement) {
                                        executePreparedStatementEntitlement(connection, plan, query, paramNames,
                                                account,
                                                attributeRequest);
                                    } else {
                                        executeQueryEntitlement(connection, plan, query, paramNames, account,
                                                attributeRequest);
                                    }
                                }
                            }
                        }

                        connection.commit();
                        result.setStatus(ProvisioningResult.STATUS_COMMITTED);
                    } else if (AccountRequest.Operation.Modify.equals(account.getOperation())) {

                        logger.info("====================== Modify Operation =========================");
                       
                        Map<String, Object> entitlementAddQueriesMap = (Map<String, Object>) jdbcCustomObj
                                .get("addEntitlement");
                        Map<String, Object> entitlementRemoveQueriesMap = (Map<String, Object>) jdbcCustomObj
                                .get("removeEntitlement");
                        Map<String, Object> attributeUpdateQuery = (Map<String, Object>) jdbcCustomObj.get("attributeUpdate");

                        if (entitlementAddQueriesMap == null) {
                            String error = "Entitlement Add Query / Stored Procedure Map is null";
                            return nullCheckHandler(error, result, connection);
                        }

                        List<AttributeRequest> attributeRequests = account.getAttributeRequests();

                        for (AttributeRequest attributeRequest : attributeRequests) {
                            String attributeName = attributeRequest.getName();
                            String attributeValue = (String) attributeRequest.getValue();

                            if (ProvisioningPlan.Operation.Add.equals(attributeRequest.getOperation())) {
                               
                                logger.info("====================== Add Entitlement =========================");
                               
                                List<Map<String, Object>> queryList = (List<Map<String, Object>>) entitlementAddQueriesMap
                                        .get(attributeName);

                                for (Map<String, Object> queryData : queryList) {
                                    String query = (String) queryData.get("query");
                                    boolean isPreparedStatement = Boolean
                                            .parseBoolean((String) queryData.get("isPreparedStatement"));
                                    List<String> paramNames = (List<String>) queryData.get("parameters");

                                    if (query == null) {
                                        String error = "Entitlement Add Query / Stored Procedure is null for "
                                                + attributeName;
                                        logger.warn(error);
                                        continue;
                                    }

                                    if (isPreparedStatement) {
                                        executePreparedStatementEntitlement(connection, plan, query, paramNames,
                                                account,
                                                attributeRequest);
                                    } else {
                                        executeQueryEntitlement(connection, plan, query, paramNames, account,
                                                attributeRequest);
                                    }
                                }
                            }

                            // Remove entitlement
                            if (ProvisioningPlan.Operation.Remove.equals(attributeRequest.getOperation())) {
                                
                                logger.info("====================== Remove Entitlement =========================");
                                List<Map<String, Object>> queryList = (List<Map<String, Object>>) entitlementRemoveQueriesMap
                                        .get(attributeName);

                                for (Map<String, Object> queryData : queryList) {
                                    String query = (String) queryData.get("query");
                                    boolean isPreparedStatement = Boolean
                                            .parseBoolean((String) queryData.get("isPreparedStatement"));
                                    List<String> paramNames = (List<String>) queryData.get("parameters");

                                    if (query == null) {
                                        String error = "Entitlement Remove Query / Stored Procedure is null for "
                                                + attributeName;
                                        logger.warn(error);
                                        continue;
                                    }

                                    if (isPreparedStatement) {
                                        executePreparedStatementEntitlement(connection, plan, query, paramNames,
                                                account,
                                                attributeRequest);
                                    } else {
                                        executeQueryEntitlement(connection, plan, query, paramNames, account,
                                                attributeRequest);
                                    }
                                }
                            }

                            // Update Account attribute
                            if (ProvisioningPlan.Operation.Set.equals(attributeRequest.getOperation())) {

                                logger.info("====================== Update Operation =========================");

                                List<Map<String, Object>> queryList = (List<Map<String, Object>>) attributeUpdateQuery.get(attributeName);

                                for (Map<String, Object> queryData : queryList) {
                                    String query = (String) queryData.get("query");
                                    boolean isPreparedStatement = Boolean
                                            .parseBoolean((String) queryData.get("isPreparedStatement"));
                                    List<String> paramNames = (List<String>) queryData.get("parameters");

                                    if (query == null) {
                                        String error = "Update Query / Stored Procedure is null for " + attributeName;
                                        return nullCheckHandler(error, result, connection);
                                    }

                                    if (isPreparedStatement) {
                                        executePreparedStatement(connection, plan, query, paramNames, account);
                                    } else {
                                        executeQuery(connection, plan, query, paramNames, account);
                                    }
                                }

                            }
                        }
                        connection.commit();
                        result.setStatus(ProvisioningResult.STATUS_COMMITTED);
                    } else if (AccountRequest.Operation.Disable.equals(account.getOperation())) {
                        
                        logger.info("====================== Disable Operation =========================");

                        List<Map<String, Object>> queryList = (List<Map<String, Object>>) jdbcCustomObj
                                .get("disable");

                        if (queryList == null) {
                            String error = "Account Disable Query / Stored Procedure List is null";
                            return nullCheckHandler(error, result, connection);
                        }

                        for (Map<String, Object> queryData : queryList) {
                            String query = (String) queryData.get("query");
                            boolean isPreparedStatement = Boolean
                                    .parseBoolean((String) queryData.get("isPreparedStatement"));
                            List<String> paramNames = (List<String>) queryData.get("parameters");

                            if (query == null) {
                                String error = "Account Disable Query / Stored Procedure is null";
                                return nullCheckHandler(error, result, connection);
                            }

                            if (isPreparedStatement) {
                                executePreparedStatement(connection, plan, query, paramNames, account);
                            } else {
                                executeQuery(connection, plan, query, paramNames, account);
                            }
                        }
                        connection.commit();
                        result.setStatus(ProvisioningResult.STATUS_COMMITTED);
                    } else if (AccountRequest.Operation.Enable.equals(account.getOperation())) {
                        
                        logger.info("====================== Enable Operation =========================");
                        
                        List<Map<String, Object>> queryList = (List<Map<String, Object>>) jdbcCustomObj
                                .get("enable");

                        if (queryList == null) {
                            String error = "Account Enable Query / Stored Procedure is null";
                            return nullCheckHandler(error, result, connection);
                        }

                        for (Map<String, Object> queryData : queryList) {
                            String query = (String) queryData.get("query");
                            boolean isPreparedStatement = Boolean
                                    .parseBoolean((String) queryData.get("isPreparedStatement"));
                            List<String> paramNames = (List<String>) queryData.get("parameters");

                            if (query == null) {
                                String error = "Account Enable Query / Stored Procedure is null";
                                return nullCheckHandler(error, result, connection);
                            }

                            if (isPreparedStatement) {
                                executePreparedStatement(connection, plan, query, paramNames, account);
                            } else {
                                executeQuery(connection, plan, query, paramNames, account);
                            }
                        }
                        connection.commit();
                        result.setStatus(ProvisioningResult.STATUS_COMMITTED);

                    } else if (AccountRequest.Operation.Delete.equals(account.getOperation())) {

                        logger.info("====================== Delete Operation =========================");

                        Map<String, Object> entitlementDeleteQueriesMap = (Map<String, Object>) jdbcCustomObj
                                .get("removeAllEntitlements");
                        List<Map<String, Object>> deleteQueryList = (List<Map<String, Object>>) jdbcCustomObj
                                .get("delete");

                        if (deleteQueryList == null) {
                            String error = "Account Delete Query / Stored Procedure Map is null";
                            return nullCheckHandler(error, result, connection);
                        }

                        if (entitlementDeleteQueriesMap == null) {
                            String error = "Entitlement Remove Query / Stored Procedure List is null";
                            return nullCheckHandler(error, result, connection);
                        }

                        Application application = context.getObjectByName(Application.class,
                                plan.getTargetIntegration());

                        Filter identityFilter = Filter.eq("identity.name", plan.getNativeIdentity());
                        Filter applicationFilter = Filter.eq("application", application);
                        Filter collectiveFilter = Filter.and(identityFilter, applicationFilter);

                        QueryOptions qo = new QueryOptions();
                        qo.addFilter(collectiveFilter);

                        List<IdentityEntitlement> entitlementsList = context.getObjects(IdentityEntitlement.class,
                                qo);
                        Set<String> processedAttr = new HashSet<>();

                        // Delete all Entitlements
                        for (IdentityEntitlement id : entitlementsList) {

                            logger.info("====================== Delete Operation : Remove Entitlement =========================");

                            String attName = id.getName();
                            if (!processedAttr.contains(attName)) {
                                processedAttr.add(attName);

                                List<Map<String, Object>> queryList = (List<Map<String, Object>>) entitlementDeleteQueriesMap
                                        .get(attName);

                                if (queryList == null) {
                                    String error = "Entitlement Remove Query / Stored Procedure List is null";
                                    return nullCheckHandler(error, result, connection);
                                }

                                for (Map<String, Object> queryData : queryList) {
                                    String query = (String) queryData.get("query");
                                    boolean isPreparedStatement = Boolean
                                            .parseBoolean((String) queryData.get("isPreparedStatement"));
                                    List<String> paramNames = (List<String>) queryData.get("parameters");

                                    if (query == null) {
                                        String error = "Entitlement Remove Query / Stored Procedure is null";
                                        return nullCheckHandler(error, result, connection);
                                    }

                                    if (isPreparedStatement) {
                                        executePreparedStatement(connection, plan, query, paramNames, account);
                                    } else {
                                        executePreparedStatement(connection, plan, query, paramNames, account);
                                    }
                                }
                            }
                        }

                        for (Map<String, Object> queryData : deleteQueryList) {
                            String query = (String) queryData.get("query");
                            boolean isPreparedStatement = Boolean
                                    .parseBoolean((String) queryData.get("isPreparedStatement"));
                            List<String> paramNames = (List<String>) queryData.get("parameters");

                            if (query == null) {
                                String error = "Account Delete Query / Stored Procedure is null";
                                return nullCheckHandler(error, result, connection);
                            }

                            if (isPreparedStatement) {
                                executePreparedStatement(connection, plan, query, paramNames, account);
                            } else {
                                executeQuery(connection, plan, query, paramNames, account);
                            }
                        }

                        application.setDisabled(true);
                    } else if (AccountRequest.Operation.Unlock.equals(account.getOperation())) {
                        
                        logger.info("====================== Unlock Operation =========================");

                        List<Map<String, Object>> queryList = (List<Map<String, Object>>) jdbcCustomObj
                                .get("unlock");
                        
                        if (queryList == null) {
                            String error = "Account Unlock Query / Stored Procedure List is null";
                            return nullCheckHandler(error, result, connection);
                        }

                        for (Map<String, Object> queryData : queryList) {
                            String query = (String) queryData.get("query");
                            boolean isPreparedStatement = Boolean
                                    .parseBoolean((String) queryData.get("isPreparedStatement"));
                            List<String> paramNames = (List<String>) queryData.get("parameters");

                            if (query == null) {
                                String error = "Account Unlock Query / Stored Procedure is null";
                                return nullCheckHandler(error, result, connection);
                            }
                            
                            if (isPreparedStatement) {
                                executePreparedStatement(connection, plan, query, paramNames, account);
                            } else {
                                executeQuery(connection, plan, query, paramNames, account);
                            }
                        }
                    }
                    connection.commit();
                    result.setStatus(ProvisioningResult.STATUS_COMMITTED);
                } catch (SQLException e) {

                    try {
                        connection.rollback();
                        logger.error(e);
                        result.setStatus(ProvisioningResult.STATUS_FAILED);
                        result.addError(e);
                    } catch (SQLException e1) {
                        logger.error(e1);
                        result.setStatus(ProvisioningResult.STATUS_FAILED);
                        result.addError(e1);
                    }
                } catch (Exception e) {

                    try {
                        logger.error(e);
                        result.setStatus(ProvisioningResult.STATUS_FAILED);
                        result.addError(e);
                        connection.rollback();
                    } catch (SQLException e1) {

                        logger.error(e1);
                        result.setStatus(ProvisioningResult.STATUS_FAILED);
                        result.addError(e1);
                    }
                } finally {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        logger.debug("result [" + result.toXml(false) + "]");
        return result;
    }

    public static String test() {
        return "Hello World!";
    }
}

