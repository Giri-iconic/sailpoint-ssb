
package main.com.custom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.*;

import sailpoint.object.*;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.tools.GeneralException;
import sailpoint.api.SailPointContext;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.Custom;
import org.apache.log4j.Logger;
import sailpoint.object.Application;
import sailpoint.object.IdentityEntitlement;

public class JDBCFrameWork {

    private static Logger logger = Logger.getLogger("com.example.mycustomlogger");

    public static ProvisioningResult createUserWithQuery(SailPointContext context, Connection connection,
            ProvisioningPlan plan,
            AccountRequest accountRequest,
            Map<Integer, String> attributesMap) throws GeneralException {
        logger.info(accountRequest.toXml());
        logger.info(plan.toXml());
        ProvisioningResult result = new ProvisioningResult();

        if (plan == null) {
            String error = "Provisioning Plan is null";
            return nullCheckHandler(error, result);
        }

        AccountRequest acctRequest = (AccountRequest) accountRequest;

        Custom jdbcCustomObj = context.getObject(Custom.class, "JDBC Custom Object");

        Map<String, String> entitlementQueriesMap = (Map) jdbcCustomObj.get("jdbc-frame-work-addEntitlementQuery");

        String insertQuery = (String) jdbcCustomObj.get("jdbc-frame-work-insertQuery");

        if (insertQuery == null || insertQuery.isEmpty()) {
            return nullCheckHandler("Insert Query is null", result);
        }

        if (entitlementQueriesMap == null || entitlementQueriesMap.isEmpty()) {
            return nullCheckHandler("Entitlement Add Queries Map is null", result);
        }

        // Check for null values in essential attributes
        StringBuilder attributeNullStringBuilder = new StringBuilder();
        StringBuilder mapNullStringBuilder = new StringBuilder();
        boolean hasNullAttribute = false;
        boolean hasKeyNull = false;

        attributeNullStringBuilder.append("Error: Provisioning Plan has following null value(s) :- ");
        mapNullStringBuilder.append("Error: Map has following key value(s) as null :- ");

        // Retrieve attribute values
        for (Map.Entry<Integer, String> attributeEntry : attributesMap.entrySet()) {
            int key = attributeEntry.getKey();
            String attribute = attributeEntry.getValue();

            if (attribute == null) {
                hasKeyNull = true;
                mapNullStringBuilder.append(" '" + key + "'");
            }

            if (getAttributeRequestValue(acctRequest, attribute) == null) {
                hasNullAttribute = true;
                attributeNullStringBuilder.append(" '" + attribute + "'");
            }
        }

        if (hasKeyNull) {
            String error = mapNullStringBuilder.toString();
            return nullCheckHandler(error, result);
        }

        if (hasNullAttribute) {
            String error = attributeNullStringBuilder.toString();
            return nullCheckHandler(error, result);
        }


        try {

            // Prepare and execute the insert query
            PreparedStatement statement = connection.prepareStatement(insertQuery);
            statement.setString(1, plan.getNativeIdentity());
            for(Map.Entry<Integer, String> attributeEntry : attributesMap.entrySet()){
                statement.setString(attributeEntry.getKey() + 1, getAttributeRequestValue(accountRequest, attributeEntry.getValue()));
            }

            statement.executeUpdate();

            // Log successful execution of the insert query
            logger.info("Executed insert query for user: { " + plan.getNativeIdentity() + " }");

            for (AttributeRequest attributeRequest : accountRequest.getAttributeRequests()) {
                if (ProvisioningPlan.Operation.Add.equals(attributeRequest.getOperation())) {

                    String attributeName = attributeRequest.getName();
                    String attributeValue = (String) attributeRequest.getValue();
                    String entitlementAddQuery = entitlementQueriesMap.get(attributeName);

                    if (entitlementAddQuery != null) {
                        statement = connection.prepareStatement(entitlementAddQuery);
                        statement.setString(1, plan.getNativeIdentity());
                        statement.setString(2, attributeValue);
                        statement.executeUpdate();

                        // Log successful execution of each entitlement attribute query
                        logger.info("Executed entitlement add query for attribute: { " + attributeName
                                + " }  with value: { " + attributeValue + " }");
                    } else {
                        logger.warn(attributeName + " Entitlement Add query is null");
                    }
                }
            }

            result.setStatus(ProvisioningResult.STATUS_COMMITTED);
        } catch (SQLException e) {
            logger.error(e);
            result.setStatus(ProvisioningResult.STATUS_FAILED);
            result.addError(e);
        }
        // Log the result and return
        logger.info("Result: { " + result.toXml() + " }");
        return result;
    }

      
  public static ProvisioningResult deleteUserWithQuery(SailPointContext context, Connection connection,
            ProvisioningPlan plan, AccountRequest accountRequest, Application application)throws GeneralException {
        ProvisioningResult result = new ProvisioningResult();


        // Retrieve Custom object for JDBC
        Custom jdbcCustomObj = context.getObject(Custom.class, "JDBC Custom Object");
        Map<String, String> entitlementDeleteQueriesMap = (Map<String, String>) jdbcCustomObj.get("jdbc-frame-work-removeAllEntitlementsQuery");

        // Get Account Delete Query
        String accountDeleteQuery = (String) jdbcCustomObj.get("jdbc-frame-work-deleteQuery");

        // Null check for query
        if (accountDeleteQuery == null) {
            return nullCheckHandler("Account Delete query is null", result);
        }

        if (entitlementDeleteQueriesMap == null || entitlementDeleteQueriesMap.isEmpty()) {
            return nullCheckHandler("Entitlement Delete queries Map is null", result);
        }

        try {

            PreparedStatement statement;

            Filter identityFilter = Filter.eq("identity.name", plan.getNativeIdentity());
            Filter applicationFilter = Filter.eq("application", application);
            Filter collectiveFilter = Filter.and(identityFilter, applicationFilter);

            QueryOptions qo = new QueryOptions();
            qo.addFilter(collectiveFilter);

            List<IdentityEntitlement> entitlementsList = context.getObjects(IdentityEntitlement.class, qo);
            Set processedAttr = new HashSet();

            // Delete all Entitlements
            for (IdentityEntitlement id : entitlementsList) {
                String attName = id.getName();
                if (!processedAttr.contains(attName)) {
                    processedAttr.add(attName);

                    String query = entitlementDeleteQueriesMap.get(attName);
                    if (query == null || query.isEmpty()) {
                        logger.warn("Entitlement Delete " + attName + " query is null");
                        continue;
                    }

                    statement = connection.prepareStatement(query);
                    statement.setString(1, (String) plan.getNativeIdentity());
                    statement.executeUpdate();
                    statement.close();

                    // Log successful execution of the delete query
                    logger.info("Executed Entitlement delete query for attribute: { " + attName + " }");
                }
            }

            // Delete User Account
            statement = connection.prepareStatement(accountDeleteQuery);
            statement.setString(1, (String) plan.getNativeIdentity());
            statement.executeUpdate();

            // Log successful execution of the delete query
            logger.info("Executed Account delete query for user: { " + plan.getNativeIdentity() + " }");
            result.setStatus(ProvisioningResult.STATUS_COMMITTED);
        } catch (SQLException e) {
            logger.error(e);
            result.setStatus(ProvisioningResult.STATUS_FAILED);
            result.addError(e);
        }
        logger.debug("Result {" + result.toXml() + " }");
        return result;
    }


    public static ProvisioningResult enableUserWithQuery(SailPointContext context, Connection connection,
            ProvisioningPlan plan) throws GeneralException {

        ProvisioningResult result = new ProvisioningResult();

        // Check if the provisioning plan is null
        if (plan == null) {
            return nullCheckHandler("ProvisioningPlan is null", result);
        }

        // Retrieve Custom object for JDBC
        Custom jdbcCustomObj = context.getObject(Custom.class, "JDBC Custom Object");

        // Get enable query from Custom Object
        String enableQuery = (String) jdbcCustomObj.get("jdbc-frame-work-enableQuery");

        // Null check for query
        if (enableQuery == null) {
            String error = "Account Enable query is null";
            logger.error(error);
            result.setStatus(ProvisioningResult.STATUS_FAILED);
            result.addError(error);
            return result;
        }

        try {

            PreparedStatement statement = connection.prepareStatement(enableQuery);
            statement.setString(1, (String) plan.getNativeIdentity());
            statement.executeUpdate();
            statement.close();

            // Log successful execution of the enable query
            logger.info("Executed enable query for user: { " + plan.getNativeIdentity() + " }");
            result.setStatus(ProvisioningResult.STATUS_COMMITTED);
        } catch (SQLException e) {
            logger.error("SQL Exception occurred: { " + e.getMessage() + " }");
            result.setStatus(ProvisioningResult.STATUS_FAILED);
            result.addError(e);
        }
        logger.debug("Result { " + result.toXml(false) + " }");
        return result;
    }

    public static ProvisioningResult disableUserWithQuery(SailPointContext context, Connection connection,
            ProvisioningPlan plan)throws GeneralException {

        // Initialize result object
        ProvisioningResult result = new ProvisioningResult();

        // Check if the provisioning plan is null
        if (plan == null) {
            return nullCheckHandler("ProvisioningPlan is null", result);
        }

        // Retrieve Custom object for JDBC
        Custom jdbcCustomObj = context.getObject(Custom.class, "JDBC Custom Object");

        // Get enable query from Custom Object
        String disableQuery = (String) jdbcCustomObj.get("jdbc-frame-work-disableQuery");

        // Null check for query
        if (disableQuery == null) {
            String error = "Account Disable query is null";
            return nullCheckHandler(error, result);
        }

        try {

            PreparedStatement statement = connection.prepareStatement(disableQuery);
            statement.setString(1, (String) plan.getNativeIdentity());
            statement.executeUpdate();
            statement.close();

            // Log successful execution of the Disable query
            logger.info("Executed disable query for user: { " + plan.getNativeIdentity() + " }");
            result.setStatus(ProvisioningResult.STATUS_COMMITTED);
        } catch (SQLException e) {
            logger.error(e);
            result.setStatus(ProvisioningResult.STATUS_FAILED);
            result.addError(e);
        }
        logger.debug("Result { " + result.toXml() + " }");
        return result;
    }

    public static ProvisioningResult unlockUserWithQuery(SailPointContext context, Connection connection,
            ProvisioningPlan plan) throws GeneralException {

        // Initialize result object
        ProvisioningResult result = new ProvisioningResult();

        // Check if the provisioning plan is null
        if (plan == null) {
            String error = "Provisioning Plan is null";
            return nullCheckHandler(error, result);
        }

        // Retrieve Custom object for JDBC
        Custom jdbcCustomObj = context.getObject(Custom.class, "JDBC Custom Object");

        // Get enable query from Custom Object
        String unLockQuery = (String) jdbcCustomObj.get("jdbc-frame-work-unLockQuery");

        // Null check for query
        if (unLockQuery == null) {
            String error = "Account Unlock query is null";
            return nullCheckHandler(error, result);
        }

        try {
            PreparedStatement statement = connection.prepareStatement(unLockQuery);
            statement.setString(1, (String) plan.getNativeIdentity());
            statement.executeUpdate();
            statement.close();

            // Log successful execution of the Unlock query
            logger.info("Executed Unlock query for user: { " + plan.getNativeIdentity() + " }");
            result.setStatus(ProvisioningResult.STATUS_COMMITTED);
        } catch (SQLException e) {
            logger.error(e);
            result.setStatus(ProvisioningResult.STATUS_FAILED);
            result.addError(e);
        }
        logger.debug("Result { " + result.toXml(false) + " }");
        return result;
    }

    // Helper method to get attribute request value
    public static String getAttributeRequestValue(AccountRequest acctReq, String attribute) {
        if (acctReq != null) {
            AttributeRequest attrReq = acctReq.getAttributeRequest(attribute);
            if (attrReq != null) {
                return (String) attrReq.getValue();
            }
        }
        return null;
    }

    // Return ProvisioningResult and logs with error message
    private static ProvisioningResult nullCheckHandler(String errorMessage, ProvisioningResult result) {
        logger.error(errorMessage);
        result.setStatus(ProvisioningResult.STATUS_FAILED);
        result.addError(errorMessage);
        return result;
    }
    public static String test(){
      return "Hello World!";
    }
}
