
package main.com.custom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.tools.GeneralException;
import sailpoint.api.SailPointContext;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.Custom;
import org.apache.log4j.Logger;

public class JDBCFrameWork {

    private static Logger logger = Logger.getLogger("com.example.mycustomlogger");

    public static ProvisioningResult createUserWithQuery(SailPointContext context, Connection connection,
            ProvisioningPlan plan,
            AccountRequest accountRequest,
            Map<Integer, String> attributesMap) throws GeneralException {

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
                statement.setString(attributeEntry.getKey(), attributeEntry.getValue());
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
        logger.error("From null check handler");
        result.setStatus(ProvisioningResult.STATUS_FAILED);
        result.addError(errorMessage);
        return result;
    }
}
