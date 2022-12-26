package org.example;


import jdk.nashorn.internal.parser.JSONParser;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wso2.balana.utils.Utils;
import org.wso2.balana.utils.exception.PolicyBuilderException;
import org.wso2.balana.utils.policy.PolicyBuilder;
import org.wso2.balana.utils.policy.dto.*;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class testDataBuilder {


    public static void main(String []args) throws Exception {

        String res = giveMeOnePolicy("resource" + String.valueOf(0), 2);
        System.out.println(res);
        Document doc = toDocument(res);

//        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
//        factory.setNamespaceAware(false); // never forget this!
//        DocumentBuilder builder = factory.newDocumentBuilder();
//        Document doc = builder.parse("resources/kmarket-blue-policy.xml");


//        System.out.println("document is");
//        System.out.println(doc);

//        XPathFactory xpathfactory = XPathFactory.newInstance();
//        XPath xpath = xpathfactory.newXPath();
//        xpath.setNamespaceContext(new NamespaceContext() {
//            @Override
//            public String getNamespaceURI(String s) {
//                if (s.equals(""))
//                    return "urn:oasis:names:tc:xacml:3.0:core:schema:wd-17";
//                return null;
//            }
//
//            @Override
//            public String getPrefix(String s) {
//                return "";
//            }
//
//            @Override
//            public Iterator getPrefixes(String s) {
//                return null;
//            }
//        });
//        try {
////            XPathExpression expr = xpath.compile("urn:oasis:names:tc:xacml:3.0:core:schema:wd-17:Policy/urn:oasis:names:tc:xacml:3.0:core:schema:wd-17:Rule");
//            XPathExpression expr = xpath.compile("Policy/Rule");
//
//            Object result = expr.evaluate(doc, XPathConstants.NODESET);
//            NodeList nodes = (NodeList) result;
//            System.out.println(nodes.getLength());
//
//            for (int i=0; i<nodes.getLength(); i++) {
//                StringWriter writer = new StringWriter();
//                Transformer transformer = TransformerFactory.newInstance().newTransformer();
//                transformer.transform(new DOMSource(nodes.item(i)), new StreamResult(writer));
//                String xml = writer.toString();
//                System.out.println(xml);
//            }
//
//
//
//        } catch (Exception e) {
//            System.out.println("ERROR!!!");
//        }




//        for (int i=0; i<5; i++) {
//            try {
//                String res = giveMeOnePolicy("resource" + String.valueOf(i), 3);
//                String name = "./resources/resource" + String.valueOf(i) + ".xml";
//                saveStringPolicy(res, name);
//            } catch (IOException e) {
//                System.out.printf("writing to document error");
//            }
//        }


//        String res = giveMeOneRequest("user1", "resource1", "action1");
//        System.out.println("\n" + res + "\n");
        System.out.println("program succeeds");

    }











    /*
     * generate policy for one resource
     * @ruleNum, the number of rules in this policy
     * */
    public static String giveMeOnePolicy(String resource, int ruleNum) {

        String action = "action1";

        PolicyElementDTO peDto = new PolicyElementDTO();

        TargetElementDTO teDto = getTargetBlockforResource(resource);
        peDto.setTargetElementDTO(teDto);

        List<RuleElementDTO> ruleList = new ArrayList<RuleElementDTO>();
        for (int i=0; i<ruleNum; i++) {
            String user = "user" + String.valueOf(i);
            ruleList.add(getRuleBlock(user, action, false));
        }
//        ruleList.add(getRuleBlock(user, action, true)); // qiwei, can't generate DENY rule
        peDto.setRuleElementDTOs(ruleList);



        peDto.setPolicyName("Web_Filter_Policy_for_" + resource);
        peDto.setRuleCombiningAlgorithms("urn:oasis:names:tc:xacml:1.0:rule-combining-algorithm:first-applicable");
        peDto.setVersion("1.0");


        System.setProperty("org.wso2.balana.TransformerFactory",
                "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
        PolicyBuilder pb = PolicyBuilder.getInstance();
        String it = "";
        try {
            it = pb.build(peDto);
//            System.out.println("the policy is built successfully");
        } catch (PolicyBuilderException e) {
            e.printStackTrace();
        }
        return it;
    }




    public static String giveMeOneRequest(String user, String resource, String action) {
        String res = null;

        RequestElementDTO reDto = new RequestElementDTO();
        reDto.setCombinedDecision(false);
        reDto.setReturnPolicyIdList(false);

        List<AttributesElementDTO> tmp1 = new ArrayList<AttributesElementDTO>();
        AttributesElementDTO aeDto1 = generateAttributesBlock("urn:oasis:names:tc:xacml:3.0:attribute-category:action",
                "urn:oasis:names:tc:xacml:1.0:action:action-id", "http://www.w3.org/2001/XMLSchema#string",action);
        AttributesElementDTO aeDto2 = generateAttributesBlock("urn:oasis:names:tc:xacml:1.0:subject-category:access-subject",
                "urn:oasis:names:tc:xacml:1.0:subject:subject-id", "http://www.w3.org/2001/XMLSchema#string",user);
        AttributesElementDTO aeDto3 = generateAttributesBlock("urn:oasis:names:tc:xacml:3.0:attribute-category:resource",
                "urn:oasis:names:tc:xacml:1.0:resource:resource-id", "http://www.w3.org/2001/XMLSchema#anyURI",resource);


        tmp1.add(aeDto1);
        tmp1.add(aeDto2);
        tmp1.add(aeDto3);
        reDto.setAttributesElementDTOs(tmp1);

        System.setProperty("org.wso2.balana.TransformerFactory",
                "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
        PolicyBuilder pb = PolicyBuilder.getInstance();
        String it = null;
        try {
            it = pb.buildRequest(reDto);

        } catch (PolicyBuilderException e) {
            e.printStackTrace();
        }

        return it;
    }

    public static AttributesElementDTO generateAttributesBlock(String cat, String attid, String dtype, String val) {
        AttributesElementDTO atsedto = new AttributesElementDTO();
        atsedto.setCategory(cat);


        AttributeElementDTO aeDto = new AttributeElementDTO();
        aeDto.setAttributeId(attid);
        aeDto.setIncludeInResult(false);
        aeDto.setDataType(dtype);

        List<String> tmp3 = new ArrayList<String>();
        tmp3.add(val);
        aeDto.setAttributeValues(tmp3);

//        AttributeValueElementDTO avDto = new AttributeValueElementDTO();
//        avDto.setAttributeDataType("http://www.w3.org/2001/XMLSchema#string");
//        avDto.setAttributeValue(val);
//
//        List<AttributeValueElementDTO> tmp1 = new ArrayList<AttributeValueElementDTO>();
//        tmp1.add(avDto);
//        aeDto.setAttributeValues(tmp1);


        List<AttributeElementDTO> tmp2 = new ArrayList<AttributeElementDTO>();
        tmp2.add(aeDto);
        atsedto.setAttributeElementDTOs(tmp2);

        return atsedto;
    }

    public static TargetElementDTO getTargetBlockforResource(String resource) {
        MatchElementDTO meDto = getMatchBlockforResource(resource);
        List<MatchElementDTO> meDtoList = new ArrayList<MatchElementDTO>();
        meDtoList.add(meDto);

        AllOfElementDTO alleDto = new AllOfElementDTO();
        alleDto.setMatchElementDTOs(meDtoList);
        List<AllOfElementDTO> alleDtoList = new ArrayList<AllOfElementDTO>();
        alleDtoList.add(alleDto);

        AnyOfElementDTO anyeDto = new AnyOfElementDTO();
        anyeDto.setAllOfElementDTOs(alleDtoList);

        TargetElementDTO teDto = new TargetElementDTO();
        teDto.addAnyOfElementDTO(anyeDto);

        return teDto;
    }

    public static TargetElementDTO getTargetBlockforUser(String user) {
        MatchElementDTO meDto = getMatchBlockforUser(user);
        List<MatchElementDTO> meDtoList = new ArrayList<MatchElementDTO>();
        meDtoList.add(meDto);

        AllOfElementDTO alleDto = new AllOfElementDTO();
        alleDto.setMatchElementDTOs(meDtoList);
        List<AllOfElementDTO> alleDtoList = new ArrayList<AllOfElementDTO>();
        alleDtoList.add(alleDto);

        AnyOfElementDTO anyeDto = new AnyOfElementDTO();
        anyeDto.setAllOfElementDTOs(alleDtoList);

        TargetElementDTO teDto = new TargetElementDTO();
        teDto.addAnyOfElementDTO(anyeDto);

        return teDto;
    }

    public static MatchElementDTO getMatchBlockforResource(String resource) {
        AttributeDesignatorDTO adDto2 = new AttributeDesignatorDTO();
        adDto2.setAttributeId("urn:oasis:names:tc:xacml:1.0:resource:resource-id");
        adDto2.setCategory("urn:oasis:names:tc:xacml:3.0:attribute-category:resource");
        adDto2.setDataType("http://www.w3.org/2001/XMLSchema#anyURI");
        adDto2.setMustBePresent("true");

        AttributeValueElementDTO avDto2 = new AttributeValueElementDTO();
        avDto2.setAttributeDataType("http://www.w3.org/2001/XMLSchema#anyURI");
        avDto2.setAttributeValue(resource);

        MatchElementDTO meDto = new MatchElementDTO();
        meDto.setMatchId("urn:oasis:names:tc:xacml:1.0:function:anyURI-equal");
        meDto.setAttributeDesignatorDTO(adDto2);
        meDto.setAttributeValueElementDTO(avDto2);

        return meDto;
    }

    public static MatchElementDTO getMatchBlockforUser(String user) {
        AttributeDesignatorDTO adDto2 = new AttributeDesignatorDTO();
        adDto2.setAttributeId("urn:oasis:names:tc:xacml:1.0:subject:subject-id");
        adDto2.setCategory("urn:oasis:names:tc:xacml:1.0:subject-category:access-subject");
        adDto2.setDataType("http://www.w3.org/2001/XMLSchema#string");
        adDto2.setMustBePresent("true");

        AttributeValueElementDTO avDto2 = new AttributeValueElementDTO();
        avDto2.setAttributeDataType("http://www.w3.org/2001/XMLSchema#string");
        avDto2.setAttributeValue(user);

        MatchElementDTO meDto = new MatchElementDTO();
        meDto.setMatchId("urn:oasis:names:tc:xacml:1.0:function:string-equal");
        meDto.setAttributeDesignatorDTO(adDto2);
        meDto.setAttributeValueElementDTO(avDto2);

        return meDto;
    }


    public static RuleElementDTO getRuleBlock(String user, String action, boolean isdefault) {
        if (!isdefault) {
            AttributeDesignatorDTO adDto = new AttributeDesignatorDTO();
            adDto.setAttributeId("urn:oasis:names:tc:xacml:1.0:action:action-id");
            adDto.setCategory("urn:oasis:names:tc:xacml:3.0:attribute-category:action");
            adDto.setDataType("http://www.w3.org/2001/XMLSchema#string");
            adDto.setMustBePresent("true");

            ApplyElementDTO innerAeDto = new ApplyElementDTO();
            innerAeDto.setFunctionId("urn:oasis:names:tc:xacml:1.0:function:string-one-and-only");
            innerAeDto.setAttributeDesignators(adDto);

            AttributeValueElementDTO avDto = new AttributeValueElementDTO();
            avDto.setAttributeDataType("http://www.w3.org/2001/XMLSchema#string");
            avDto.setAttributeValue(action);




            ApplyElementDTO outerAeDto = new ApplyElementDTO();
            outerAeDto.setFunctionId("urn:oasis:names:tc:xacml:1.0:function:string-equal");
            outerAeDto.setApplyElement(innerAeDto);
            outerAeDto.setAttributeValueElementDTO(avDto);

            ConditionElementDT0 ceDto = new ConditionElementDT0();
            ceDto.setApplyElement(outerAeDto);

            RuleElementDTO reDto = new RuleElementDTO();
            reDto.setConditionElementDT0(ceDto);
            reDto.setRuleEffect("Permit");
            reDto.setRuleId("Rule_" + user);
            reDto.setTargetElementDTO(getTargetBlockforUser(user));

            return reDto;
        } else {
            RuleElementDTO reDto = new RuleElementDTO();
            reDto.setRuleEffect("Deny");
            reDto.setRuleId("Rule_deny_all");
        }
        return null;
    }



    public static String toString(Document doc) {
        try {
            StringWriter sw = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error converting to String", ex);
        }
    }


    public static Document toDocument(String str) {
        Document document = null;


        try {
            DocumentBuilderFactory factory = Utils.getSecuredDocumentBuilderFactory();
            factory.setIgnoringComments(true);
            factory.setNamespaceAware(true);
            factory.setValidating(false);


            DocumentBuilder db = factory.newDocumentBuilder();
            InputSource is = new InputSource();
            is.setCharacterStream(new StringReader(str));
            document = db.parse(is);
        } catch (Exception e) {
            System.out.println("convert string policy to Document doc wrong");
        }

        return document;
    }

    public static void saveStringPolicy(String xmlSource, String fileName)
            throws IOException {
        java.io.FileWriter fw = new java.io.FileWriter(fileName);
        fw.write(xmlSource);
        fw.close();
    }


}
