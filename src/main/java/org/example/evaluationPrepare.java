package org.example;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.w3c.dom.Document;
import org.wso2.balana.Balana;
import org.wso2.balana.PDP;
import org.wso2.balana.PDPConfig;
import org.wso2.balana.finder.PolicyFinder;
import org.wso2.balana.finder.PolicyFinderModule;
import org.wso2.balana.finder.impl.FileBasedPolicyFinderModule;
import org.wso2.balana.finder.impl.updatablePolicyFinderModule;

import javax.print.Doc;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class evaluationPrepare {

    private static Balana[] balana;
    private static int nWorkers = 1;
    private static ExecutorService parallelVerifier = Executors.newWorkStealingPool(nWorkers);



    public static void main(String[] args) {

        testBalana();
//        try {
//            testSig();
//        } catch (Exception e) {
//            System.out.println("error!");
//        }

    }

    public static void testBalana() {
        initProperty();
        updatablePolicyFinderModule[] upfmList = new updatablePolicyFinderModule[nWorkers];

        balana = new Balana[nWorkers];
        PDP[] pdpList = new PDP[nWorkers];
        for (int i=0; i<nWorkers; i++) {

            balana[i] = Balana.getInstance();
            upfmList[i] = new updatablePolicyFinderModule();
            Set<PolicyFinderModule> set1 = new HashSet<>();
            set1.add(upfmList[i]);
            balana[i].getPdpConfig().getPolicyFinder().setModules(set1);

            pdpList[i] = new PDP(new PDPConfig(null, balana[i].getPdpConfig().getPolicyFinder(), null, true));
        }


//        policyFinder.addModules(((PolicyFinderModule) upfm));



        System.out.println("Hello world!");
        System.out.println("Working Directory = " + System.getProperty("user.dir"));


        List<List<Document>> newpolicies = new ArrayList<List<Document>>();
        for (int i=0; i<nWorkers; i++) {
            newpolicies.add(new ArrayList<Document>());
        }

//        String request = "<Request\n" +
//                "\txmlns=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\" CombinedDecision=\"false\" ReturnPolicyIdList=\"false\">\n" +
//                "\t<Attributes Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:action\">\n" +
//                "\t\t<Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:action:action-id\" IncludeInResult=\"false\">\n" +
//                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">action1</AttributeValue>\n" +
//                "\t\t</Attribute>\n" +
//                "\t</Attributes>\n" +
//                "\t<Attributes Category=\"urn:oasis:names:tc:xacml:1.0:subject-category:access-subject\">\n" +
//                "\t\t<Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:subject:subject-id\" IncludeInResult=\"false\">\n" +
//                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">user1</AttributeValue>\n" +
//                "\t\t</Attribute>\n" +
//                "\t</Attributes>\n" +
//                "\t<Attributes Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:resource\">\n" +
//                "\t\t<Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:resource:resource-id\" IncludeInResult=\"false\">\n" +
//                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#anyURI\">resource1</AttributeValue>\n" +
//                "\t\t</Attribute>\n" +
//                "\t</Attributes>\n" +
//                "</Request>";





        List<Long> timecost = new ArrayList<Long>();
        Random ran = new Random();
        List<Integer> ResourceIds = new ArrayList<>();
        int USERNUM = 20;
        int RESOURCENUM= 20;
        for (int i=0; i<RESOURCENUM; i++) {
            ResourceIds.add(i);
        }
        Map<Integer, Integer> baselinemap = new HashMap<Integer, Integer>();
        baselinemap.put(0, 100);
        baselinemap.put(1, 100);

//            String onepolicy = testDataBuiler.giveMeOnePolicy("resource" + String.valueOf(i), 2);

//

        for (int i=0; i<USERNUM*RESOURCENUM; i++) {
            int userid = i/RESOURCENUM;
            Collections.shuffle(ResourceIds);
            String kmarketPolicy = createKMarketPolicy(""+i,"user"+userid, "resource"+ResourceIds.get(0),
                    "resource"+ResourceIds.get(1), "resource"+ResourceIds.get(2));
//            System.out.println("random resource ids: "+ResourceIds.get(0) + " " +ResourceIds.get(1)+" "+ResourceIds.get(2));
//            System.out.println("\n======================== XACML Policy ====================");
//            System.out.println(kmarketPolicy);
//            System.out.println("===========================================================");
            for (int k=0; k<nWorkers; k++) {
                newpolicies.get(k).add(testDataBuilder.toDocument(kmarketPolicy));
            }
//            if (i==10) break;
        }



        for (int i=0; i<nWorkers; i++) {
            upfmList[i].loadPolicyBatchFromMemory(newpolicies.get(i));
        }

        for (int k=0; k<nWorkers; k++) {
            System.out.println("policy number : " + upfmList[k].showPolicies().size()+"\n");
        }

        long start;
        long elapsedTime;
        String response;
        start = System.nanoTime();


        System.out.println("----------evaluation starts-----------");
        String[] historyReqs = new String[10];
        for (int i=0; i<1; i++) {
            int looptime = 10;
            final CountDownLatch latch = new CountDownLatch(looptime);

            start = System.nanoTime();
            for (int j=0; j<looptime; j++) {
                int userid = ran.nextInt(USERNUM);
                int resourceid = ran.nextInt(RESOURCENUM);
                int amount = ran.nextInt(10);
                int totalamount = ran.nextInt(80);
                String kmarketrequest = createKMarketRequest("user"+userid, "resource"+resourceid,
                        amount, totalamount);
                historyReqs[j+i*looptime] = kmarketrequest;
//                System.out.println("request size is: "+kmarketrequest.length());
                parallelVerifier.submit(() -> {
                    int ind = (int) Thread.currentThread().getId() % nWorkers;

//                    System.out.println("\n--------------");
                    for (int l=0; l<1; l++) {

                        String res = pdpList[ind].evaluate(kmarketrequest);
                        System.out.println(shortise(res));
//                    System.out.println("response size is: " + res.length());
//                    System.out.println("response is\n"+res);



                    }
//                    System.out.println("--------------\n");
                    latch.countDown();
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                System.out.println(e + "balana parallel execution fail");
            }

            elapsedTime = (System.nanoTime() - start)/1000000;
            timecost.add(elapsedTime);

            System.out.println("----------evaluation end-----------");
            System.out.println("PDP evaluation 1000 requests costs " + (elapsedTime) + " ms");

            for (int k=0; k<5; k++) {
                System.out.println("again-------------");
                for (int l=0; l<historyReqs.length; l++) {
                    String res = pdpList[0].evaluate(historyReqs[l]);
                    System.out.println(shortise(res));
                }
            }

//            System.out.println("\n======================== XACML Request ===================");
//            System.out.println(kmarketrequest);
//            System.out.println("===========================================================");
//            int tmp = (i%2==0)? 5: -5;
//            int x = baselinemap.get(0) + tmp;
//            int y = baselinemap.get(1) - tmp;
//            baselinemap.put(0, x);
//            baselinemap.put(1, y);
//
        }
        System.out.println("PDP evaluation costs: " + timecost);

//        start = System.nanoTime();
//        for (int i=0; i<1000; i++) {
//            response = pdp.evaluate(kmarketrequest3);
//            System.out.println("\n======================== XACML Response ===================");
//            System.out.println(response);
//            System.out.println("===========================================================");
//        }
//        elapsedTime = (System.nanoTime() - start)/1000000;
//        System.out.println("PDP evaluation costs " + (elapsedTime) + " ms");

    }



    private static void initProperty() {

        try{
            // using file based policy repository. so set the policy location as system property
            String policyLocation = (new File(".")).getCanonicalPath() + File.separator + "resources";
            System.setProperty(FileBasedPolicyFinderModule.POLICY_DIR_PROPERTY, policyLocation);
            System.out.println("policy location:");
            System.out.println(policyLocation);
        } catch (IOException e) {
            System.err.println("Can not locate policy repository");
        }
        // create default instance of Balana
        // get a Balana instance
    }

    private static String createKMarketPolicy(String policyid, String subjectid, String resource1, String resource2, String resource3) {

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

    private static String createKMarketRequest(String user, String resource, int amount, int totalamount) {
        String kmarketrequest = "<Request\n" +
                "\txmlns=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\" CombinedDecision=\"false\" ReturnPolicyIdList=\"false\">\n" +
                "\t<Attributes Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:action\">\n" +
                "\t\t<Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:action:action-id\" IncludeInResult=\"false\">\n" +
                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">action</AttributeValue>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
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

//    public static void testSignature() throws NoSuchAlgorithmException {
//        Signature signature = Signature.getInstance("SHA256WithECDSA");
//        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDSA");
//        kpg.initialize(1024);
//
//    }

    public static void GetTimestamp(String info) {
        System.out.println(info + new Timestamp((new Date()).getTime()));
    }/*ww  w.ja va  2s  .  com*/

    public static byte[] GenerateSignature(String plaintext, KeyPair keys)
            throws SignatureException, UnsupportedEncodingException,
            InvalidKeyException, NoSuchAlgorithmException,
            NoSuchProviderException {
        Signature ecdsaSign = Signature
                .getInstance("SHA256withECDSA", "BC");
        ecdsaSign.initSign(keys.getPrivate());
        ecdsaSign.update(plaintext.getBytes("UTF-8"));
        byte[] signature = ecdsaSign.sign();
        System.out.println(signature.toString());
        return signature;
    }

    public static boolean ValidateSignature(String plaintext, KeyPair pair,
                                            byte[] signature) throws SignatureException,
            InvalidKeyException, UnsupportedEncodingException,
            NoSuchAlgorithmException, NoSuchProviderException {
        Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA",
                "BC");
        ecdsaVerify.initVerify(pair.getPublic());
        ecdsaVerify.update(plaintext.getBytes("UTF-8"));
        return ecdsaVerify.verify(signature);
    }


    public static KeyPair GenerateKeys() throws NoSuchAlgorithmException,
            NoSuchProviderException, InvalidAlgorithmParameterException {
        //  Other named curves can be found in http://www.bouncycastle.org/wiki/display/JA1/Supported+Curves+%28ECDSA+and+ECGOST%29
        ECParameterSpec ecSpec = ECNamedCurveTable
                .getParameterSpec("B-571");

        KeyPairGenerator g = KeyPairGenerator.getInstance("ECDSA", "BC");

        g.initialize(ecSpec, new SecureRandom());

        return g.generateKeyPair();
    }

    public static void testSig() throws Exception {

        long start;
        long elapsedTime;

        Security.addProvider(new BouncyCastleProvider());

        String plaintext = "Simple plain text";
        GetTimestamp("Key Generation started: ");
        KeyPair keys = GenerateKeys();
        //    System.out.println(keys.getPublic().toString());
        //    System.out.println(keys.getPrivate().toString());
        GetTimestamp("Key Generation ended: ");

        GetTimestamp("Signature Generation started: ");
        byte[] signature = GenerateSignature(plaintext, keys);
        GetTimestamp("Signature Generation ended: ");

        GetTimestamp("Validation started: ");

        for (int l=0; l<20; l++) {
            start  = System.nanoTime();

            int loopnum = 1000;
            final CountDownLatch latch = new CountDownLatch(loopnum);
            for (int i=0; i<loopnum; i++) {
                parallelVerifier.submit(() -> {
                    try {
                        boolean isValidated = ValidateSignature(plaintext, keys, signature);
//                        System.out.println("Result: " + isValidated);
                    } catch (Exception e) {

                    }
                    latch.countDown();
                });

            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                System.out.println(e + "signature verification parallel execution fail");
            }
            elapsedTime = (System.nanoTime() - start)/1000000;
            System.out.println("signature evaluation costs " + (elapsedTime) + " ms");
        }


        GetTimestamp("Validation ended: ");

    }

    public static String getThatRequest() {
        return "<Request\n" +
                "\txmlns=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\" CombinedDecision=\"false\" ReturnPolicyIdList=\"false\">\n" +
                "\t<Attributes Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:action\">\n" +
                "\t\t<Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:action:action-id\" IncludeInResult=\"false\">\n" +
                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">action</AttributeValue>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<Attributes Category=\"urn:oasis:names:tc:xacml:1.0:subject-category:access-subject\">\n" +
                "\t\t<Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:subject:subject-id\" IncludeInResult=\"false\">\n" +
                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">user3</AttributeValue>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<Attributes Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:resource\">\n" +
                "\t\t<Attribute AttributeId=\"urn:oasis:names:tc:xacml:1.0:resource:resource-id\" IncludeInResult=\"false\">\n" +
                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\">resource3</AttributeValue>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<Attributes Category=\"http://kmarket.com/category\">\n" +
                "\t\t<Attribute AttributeId=\"http://kmarket.com/id/amount\" IncludeInResult=\"false\">\n" +
                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#integer\">7</AttributeValue>\n" +
                "\t\t</Attribute>\n" +
                "\t\t<Attribute AttributeId=\"http://kmarket.com/id/totalAmount\" IncludeInResult=\"false\">\n" +
                "\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#integer\">10</AttributeValue>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "</Request>";
    }

    public static String shortise(String str) {
        String res = null;
        switch (str) {
            case "<Response xmlns=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\"><Result><Decision>NotApplicable</Decision><Status><StatusCode Value=\"urn:oasis:names:tc:xacml:1.0:status:ok\"/></Status></Result></Response>": {
                res = "NotApplicable";
                break;
            }
            case "<Response xmlns=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\"><Result><Decision>Permit</Decision><Status><StatusCode Value=\"urn:oasis:names:tc:xacml:1.0:status:ok\"/></Status></Result></Response>": {
                res = "Permit";
                break;
            }

            case "<Response xmlns=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\"><Result><Decision>Deny</Decision><Status><StatusCode Value=\"urn:oasis:names:tc:xacml:1.0:status:ok\"/></Status></Result></Response>": {
                res = "Deny";
                break;
            }
            default:
                System.out.println("wired thing, res =\n"+str);
        }
        return res;
    }
}