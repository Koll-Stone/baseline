package org.example;

public class baselineParameters {

    public static int USERNUM = 800;
    public static int RESOURCENUM= 20;

    public static int POLICYEACHUSER = 1;

    public static String createKMarketPolicy(String policyid, String subjectid, String resource1, String resource2, String resource3) {

        String kmarketPolicy = "<Policy xmlns=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\" PolicyId=\"KmarketPolicy"+ policyid + "\"  RuleCombiningAlgId=\"urn:oasis:names:tc:xacml:3.0:rule-combining-algorithm:deny-overrides\" Version=\"1.0\">\n" +
                "   <Target>\n" +
                "      <AnyOf>\n" +
                "         <AllOf>\n" +
                "            <Match MatchId=\"urn:oasis:names:tc:xacml:1.0:function:string-equal\">\n" +
                "               <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">" + subjectid + "</AttributeValue>\n" +
                "               <AttributeDesignator AttributeId=\"urn:oasis:names:tc:xacml:1.0:subject:subject-id\" Category=\"urn:oasis:names:tc:xacml:1.0:subject-category:access-subject\" DataType=\"http://www.w3.org/2001/XMLSchema#string\" MustBePresent=\"true\"/>\n" +
                "            </Match>\n" +
                "         </AllOf>\n" +
                "      </AnyOf>\n" +
                "   </Target>\n" +
                "   <Rule Effect=\"Deny\" RuleId=\"total-amount\">\n" +
                "      <Condition>\n" +
                "         <Apply FunctionId=\"urn:oasis:names:tc:xacml:1.0:function:integer-greater-than\">\n" +
                "            <Apply FunctionId=\"urn:oasis:names:tc:xacml:1.0:function:integer-one-and-only\">\n" +
                "               <AttributeDesignator AttributeId=\"http://kmarket.com/id/totalAmount\" Category=\"http://kmarket.com/category\" DataType=\"http://www.w3.org/2001/XMLSchema#integer\" MustBePresent=\"true\"/>\n" +
                "            </Apply>\n" +
                "            <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#integer\">100</AttributeValue>\n" +
                "         </Apply>\n" +
                "      </Condition>\n" +
                "   </Rule>\n" +
                "   <Rule Effect=\"Deny\" RuleId=\"deny-liquor-medicine\">\n" +
                "   <Target>\n" +
                "      <AnyOf>\n" +
                "         <AllOf>\n" +
                "            <Match MatchId=\"urn:oasis:names:tc:xacml:1.0:function:string-equal\">\n" +
                "               <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">" + resource1 + "</AttributeValue>\n" +
                "               <AttributeDesignator AttributeId=\"urn:oasis:names:tc:xacml:1.0:resource:resource-id\" Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:resource\" DataType=\"http://www.w3.org/2001/XMLSchema#string\" MustBePresent=\"true\"/>\n" +
                "            </Match>\n" +
                "         </AllOf>\n" +
                "         <AllOf>\n" +
                "            <Match MatchId=\"urn:oasis:names:tc:xacml:1.0:function:string-equal\">\n" +
                "               <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">" + resource2 + "</AttributeValue>\n" +
                "               <AttributeDesignator AttributeId=\"urn:oasis:names:tc:xacml:1.0:resource:resource-id\" Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:resource\" DataType=\"http://www.w3.org/2001/XMLSchema#string\" MustBePresent=\"true\"/>\n" +
                "            </Match>\n" +
                "         </AllOf>\n" +
                "      </AnyOf>\n" +
                "   </Target>\n" +
                "   </Rule>\n" +
                "   <Rule Effect=\"Deny\" RuleId=\"max-drink-amount\">\n" +
                "   <Target>\n" +
                "      <AnyOf>\n" +
                "         <AllOf>\n" +
                "            <Match MatchId=\"urn:oasis:names:tc:xacml:1.0:function:string-equal\">\n" +
                "               <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">" + resource3 + "</AttributeValue>\n" +
                "               <AttributeDesignator AttributeId=\"urn:oasis:names:tc:xacml:1.0:resource:resource-id\" Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:resource\" DataType=\"http://www.w3.org/2001/XMLSchema#string\" MustBePresent=\"true\"/>\n" +
                "            </Match>\n" +
                "         </AllOf>\n" +
                "      </AnyOf>\n" +
                "   </Target>\n" +
                "      <Condition>\n" +
                "         <Apply FunctionId=\"urn:oasis:names:tc:xacml:1.0:function:integer-greater-than\">\n" +
                "            <Apply FunctionId=\"urn:oasis:names:tc:xacml:1.0:function:integer-one-and-only\">\n" +
                "               <AttributeDesignator AttributeId=\"http://kmarket.com/id/amount\" Category=\"http://kmarket.com/category\" DataType=\"http://www.w3.org/2001/XMLSchema#integer\" MustBePresent=\"true\"/>\n" +
                "            </Apply>\n" +
                "            <AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#integer\">10</AttributeValue>\n" +
                "         </Apply>\n" +
                "      </Condition>\n" +
                "   </Rule>\n" +
                "    <Rule RuleId=\"permit-rule\" Effect=\"Permit\"/>    \n" +
                "</Policy>";
        return kmarketPolicy;
    }

    public static String createKMarketRequest(String user, String resource, int amount, int totalamount) {
        String kmarketrequest = "<Request\n" +
                "\txmlns=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\" CombinedDecision=\"false\" ReturnPolicyIdList=\"false\">\n" +
                "\t<Attributes Category=\"urn:oasis:names:tc:xacml:1.0:subject-category:access-subject\">\n" +
                "\t\t<Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:subject:subject-id\" IncludeInResult=\"false\">\n" +
                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">" + user + "</AttributeValue>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<Attributes Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:resource\">\n" +
                "\t\t<Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:resource:resource-id\" IncludeInResult=\"false\">\n" +
                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">" + resource+ "</AttributeValue>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<Attributes Category=\"http://kmarket.com/category\">\n" +
                "\t\t<Attribute AttributeId=\"http://kmarket.com/id/amount\" IncludeInResult=\"false\">\n" +
                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#integer\">" + amount + "</AttributeValue>\n" +
                "\t\t</Attribute>\n" +
                "\t\t<Attribute AttributeId=\"http://kmarket.com/id/totalAmount\" IncludeInResult=\"false\">\n" +
                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#integer\">" + totalamount + "</AttributeValue>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "</Request>";
        return kmarketrequest;
    }
}
