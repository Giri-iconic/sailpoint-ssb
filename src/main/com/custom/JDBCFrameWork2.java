package main.com.custom;

import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLObjectFactory;
import org.apache.log4j.Logger;

import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import java.util.*;
import java.sql.*;

public class JDBCFrameWork2 {

    private final static Logger logger = Logger.getLogger("com.example.mycustomlogger");

    public static Object getAttributeRequestValue(AccountRequest acctReq, String attribute) {
        if (acctReq != null) {
            AttributeRequest attrReq = acctReq.getAttributeRequest(attribute);
            if (attrReq != null) {
                return attrReq.getValue();
            }
        }
        return null;
    }

    private static ProvisioningResult nullCheckHandler(String errorMessage, ProvisioningResult result) {
        logger.error(errorMessage);
        result.setStatus(ProvisioningResult.STATUS_FAILED);
        result.addError(errorMessage);
        return result;
    }

    public static void executeQuery(Connection connection, ProvisioningPlan plan, String query, List<String> paramNames,
            AccountRequest acctRequest) throws SQLException {

        PreparedStatement statement = connection.prepareStatement(query);
        for (int i = 0; i < paramNames.size(); i++) {
            String paramName = paramNames.get(i);
            // Object paramValue = getParameterValue(paramName, results);
            if (paramName.equals("nativeIdentity")) {
                statement.setObject(i + 1, plan.getNativeIdentity());
            } else {
                logger.info("============ Query param : " + paramName);
                statement.setObject(i + 1, getAttributeRequestValue(acctRequest, paramName));
            }
        }
        statement.executeUpdate();
        logger.info("Executed query for user: { " + plan.getNativeIdentity() + " }");
    }

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

    public static ProvisioningResult provision(SailPointContext context, Connection connection, ProvisioningPlan plan,
            String custObj) throws GeneralException {

        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        Custom jdbcCustomObj = context.getObject(Custom.class, custObj);
        ProvisioningResult result = new ProvisioningResult();

        if (plan != null) {

            logger.debug("plan [" + plan.toXml() + "]");
            List<AccountRequest> accounts = plan.getAccountRequests();

            if ((accounts != null) && (accounts.size() > 0)) {
                for (AccountRequest account : accounts) {

                    try {
                        if (AccountRequest.Operation.Create.equals(account.getOperation())) {

                            List<Map<String, Object>> queryList = (List<Map<String, Object>>) jdbcCustomObj
                                    .get("jdbc-frame-work-insertQuery");
                            Map<String, Object> entitlementAddQueriesMap = (Map<String, Object>) jdbcCustomObj
                                    .get("jdbc-frame-work-addEntitlementQuery");

                            for (Map<String, Object> queryData : queryList) {
                                String query = (String) queryData.get("query");
                                boolean isPreparedStatement = Boolean
                                        .parseBoolean((String) queryData.get("isPreparedStatement"));
                                List<String> paramNames = (List<String>) queryData.get("parameters");

                                if (isPreparedStatement) {
                                    executePreparedStatement(connection, plan, query, paramNames, account);
                                } else {
                                    executeQuery(connection, plan, query, paramNames, account);
                                }
                            }

                            List<AttributeRequest> attributeRequests = account.getAttributeRequests();

                            for (AttributeRequest attributeRequest : attributeRequests) {
                                String attributeName = attributeRequest.getName();
                                String attributeValue = (String) attributeRequest.getValue();

                                if (ProvisioningPlan.Operation.Add.equals(attributeRequest.getOperation())) {
                                    List<Map<String, Object>> entitleMentQueryList = (List<Map<String, Object>>) entitlementAddQueriesMap
                                            .get(attributeName);

                                    for (Map<String, Object> queryData : entitleMentQueryList) {
                                        String query = (String) queryData.get("query");
                                        boolean isPreparedStatement = Boolean
                                                .parseBoolean((String) queryData.get("isPreparedStatement"));
                                        List<String> paramNames = (List<String>) queryData.get("parameters");

                                        if (isPreparedStatement) {
                                            executePreparedStatementEntitlement(connection, plan, query, paramNames,
                                                    account,
                                                    attributeRequest);
                                            logger.info(paramNames);
                                        } else {
                                            executeQueryEntitlement(connection, plan, query, paramNames, account,
                                                    attributeRequest);
                                            logger.info(paramNames);
                                        }
                                    }
                                }
                            }

                            connection.commit();
                            result.setStatus(ProvisioningResult.STATUS_COMMITTED);
                        } else if (AccountRequest.Operation.Modify.equals(account.getOperation())) {

                            // Map entitlementAddQueriesMap = (Map<Map<, List<Map<String, Object>>>)
                            // jdbcCustomObj.get("jdbc-frame-work-addEntitlementQuery");
                            Map<String, Object> entitlementAddQueriesMap = (Map<String, Object>) jdbcCustomObj
                                    .get("jdbc-frame-work-addEntitlementQuery");
                            Map<String, Object> entitlementRemoveQueriesMap = (Map<String, Object>) jdbcCustomObj
                                    .get("jdbc-frame-work-removeEntitlementQuery");

                            List<AttributeRequest> attributeRequests = account.getAttributeRequests();

                            for (AttributeRequest attributeRequest : attributeRequests) {
                                String attributeName = attributeRequest.getName();
                                String attributeValue = (String) attributeRequest.getValue();

                                if (ProvisioningPlan.Operation.Add.equals(attributeRequest.getOperation())) {
                                    List<Map<String, Object>> queryList = (List<Map<String, Object>>) entitlementAddQueriesMap
                                            .get(attributeName);

                                    for (Map<String, Object> queryData : queryList) {
                                        String query = (String) queryData.get("query");
                                        boolean isPreparedStatement = Boolean
                                                .parseBoolean((String) queryData.get("isPreparedStatement"));
                                        List<String> paramNames = (List<String>) queryData.get("parameters");

                                        if (isPreparedStatement) {
                                            executePreparedStatementEntitlement(connection, plan, query, paramNames,
                                                    account,
                                                    attributeRequest);
                                            logger.info(paramNames);
                                        } else {
                                            executeQueryEntitlement(connection, plan, query, paramNames, account,
                                                    attributeRequest);
                                            logger.info(paramNames);
                                        }
                                    }
                                }

                                // Remove entitlement
                                if (ProvisioningPlan.Operation.Remove.equals(attributeRequest.getOperation())) {
                                    List<Map<String, Object>> queryList = (List<Map<String, Object>>) entitlementRemoveQueriesMap
                                            .get(attributeName);

                                    for (Map<String, Object> queryData : queryList) {
                                        String query = (String) queryData.get("query");
                                        boolean isPreparedStatement = Boolean
                                                .parseBoolean((String) queryData.get("isPreparedStatement"));
                                        List<String> paramNames = (List<String>) queryData.get("parameters");

                                        if (isPreparedStatement) {
                                            executePreparedStatementEntitlement(connection, plan, query, paramNames,
                                                    account,
                                                    attributeRequest);
                                            logger.info(paramNames);
                                        } else {
                                            executeQueryEntitlement(connection, plan, query, paramNames, account,
                                                    attributeRequest);
                                            logger.info(paramNames);
                                        }
                                    }
                                }
                            }
                            connection.commit();
                            result.setStatus(ProvisioningResult.STATUS_COMMITTED);
                        } else if (AccountRequest.Operation.Disable.equals(account.getOperation())) {
                            logger.info("=========================== : " + account.getOperation());
                            List<Map<String, Object>> queryList = (List<Map<String, Object>>) jdbcCustomObj
                                    .get("jdbc-frame-work-disableQuery");

                            for (Map<String, Object> queryData : queryList) {
                                String query = (String) queryData.get("query");
                                boolean isPreparedStatement = Boolean
                                        .parseBoolean((String) queryData.get("isPreparedStatement"));
                                List<String> paramNames = (List<String>) queryData.get("parameters");

                                if (isPreparedStatement) {
                                    executePreparedStatement(connection, plan, query, paramNames, account);
                                } else {
                                    executeQuery(connection, plan, query, paramNames, account);
                                }
                            }
                            connection.commit();
                            result.setStatus(ProvisioningResult.STATUS_COMMITTED);
                        } else if (AccountRequest.Operation.Enable.equals(account.getOperation())) {
                            logger.info("=========================== : " + account.getOperation());
                            List<Map<String, Object>> queryList = (List<Map<String, Object>>) jdbcCustomObj
                                    .get("jdbc-frame-work-enableQuery");

                            for (Map<String, Object> queryData : queryList) {
                                String query = (String) queryData.get("query");
                                boolean isPreparedStatement = Boolean
                                        .parseBoolean((String) queryData.get("isPreparedStatement"));
                                List<String> paramNames = (List<String>) queryData.get("parameters");

                                if (isPreparedStatement) {
                                    executePreparedStatement(connection, plan, query, paramNames, account);
                                } else {
                                    executeQuery(connection, plan, query, paramNames, account);
                                }
                            }
                            connection.commit();
                            result.setStatus(ProvisioningResult.STATUS_COMMITTED);

                        } else if (AccountRequest.Operation.Delete.equals(account.getOperation())) {

                            Map<String, Object> entitlementDeleteQueriesMap = (Map<String, Object>) jdbcCustomObj
                                    .get("jdbc-frame-work-removeAllEntitlementsQuery");
                            List<Map<String, Object>> deleteQueryList = (List<Map<String, Object>>) jdbcCustomObj
                                    .get("jdbc-frame-work-deleteQuery");

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
                                String attName = id.getName();
                                if (!processedAttr.contains(attName)) {
                                    processedAttr.add(attName);

                                    List<Map<String, Object>> queryList = (List<Map<String, Object>>) entitlementDeleteQueriesMap
                                            .get(attName);

                                    for (Map<String, Object> queryData : queryList) {
                                        String query = (String) queryData.get("query");
                                        boolean isPreparedStatement = Boolean
                                                .parseBoolean((String) queryData.get("isPreparedStatement"));
                                        List<String> paramNames = (List<String>) queryData.get("parameters");

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

                                if (isPreparedStatement) {
                                    executePreparedStatement(connection, plan, query, paramNames, account);
                                } else {
                                    executeQuery(connection, plan, query, paramNames, account);
                                }
                            }

                            application.setDisabled(true);
                        } else if (AccountRequest.Operation.Unlock.equals(account.getOperation())) {
                            logger.info("=========================== : " + account.getOperation());
                            List<Map<String, Object>> queryList = (List<Map<String, Object>>) jdbcCustomObj
                                    .get("jdbc-frame-work-unLockQuery");

                            for (Map<String, Object> queryData : queryList) {
                                String query = (String) queryData.get("query");
                                boolean isPreparedStatement = Boolean
                                        .parseBoolean((String) queryData.get("isPreparedStatement"));
                                List<String> paramNames = (List<String>) queryData.get("parameters");

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
        }
        logger.debug("result [" + result.toXml(false) + "]");
        return result;
    }

  public static String test() {
    return "Hello World!";
  }
}

