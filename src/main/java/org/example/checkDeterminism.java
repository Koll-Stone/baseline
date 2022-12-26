package org.example;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.w3c.dom.Document;
import org.wso2.balana.Balana;
import org.wso2.balana.PDP;
import org.wso2.balana.PDPConfig;
import org.wso2.balana.finder.PolicyFinderModule;
import org.wso2.balana.finder.impl.FileBasedPolicyFinderModule;
import org.wso2.balana.finder.impl.updatablePolicyFinderModule;

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

import static org.example.baselineParameters.createKMarketPolicy;
import static org.example.baselineParameters.createKMarketRequest;

public class checkDeterminism {


    private static Balana[] balana;
    private static int nWorkers = 1;
    private static ExecutorService parallelVerifier = Executors.newWorkStealingPool(nWorkers);



    public static void main(String[] args) {
        testBalana();
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

        System.out.println("Hello world!");
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        List<List<Document>> newpolicies = new ArrayList<List<Document>>();
        for (int i=0; i<nWorkers; i++) {
            newpolicies.add(new ArrayList<Document>());
        }

        // initiazlize policies
        List<Long> timecost = new ArrayList<Long>();
        Random ran = new Random();
        List<Integer> ResourceIds = new ArrayList<>();
        int USERNUM = 20;
        int RESOURCENUM= 20;
        for (int i=0; i<RESOURCENUM; i++) {
            ResourceIds.add(i);
        }
        for (int i=0; i<USERNUM*RESOURCENUM; i++) {
            int userid = i/RESOURCENUM;
            Collections.shuffle(ResourceIds);
            String kmarketPolicy = createKMarketPolicy(""+i,"user"+userid, "resource"+ResourceIds.get(0),
                    "resource"+ResourceIds.get(1), "resource"+ResourceIds.get(2));
            for (int k=0; k<nWorkers; k++) {
                newpolicies.get(k).add(testDataBuilder.toDocument(kmarketPolicy));
            }
        }

        for (int i=0; i<nWorkers; i++) {
            upfmList[i].loadPolicyBatchFromMemory(newpolicies.get(i));
        }
        for (int k=0; k<nWorkers; k++) {
            System.out.println("policy number : " + upfmList[k].showPolicies().size()+"\n");
        }



        // start testing
        long start;
        long elapsedTime;
        String response;
        start = System.nanoTime();


        System.out.println("----------evaluation starts-----------");
        int exampleNum = 100000;
        String[] historyReqs = new String[exampleNum];
        for (int i=0; i<exampleNum; i++) {
            int userid = ran.nextInt(USERNUM);
            int resourceid = ran.nextInt(RESOURCENUM);
            int amount = ran.nextInt(10);
            int totalamount = ran.nextInt(80);
            String kmarketrequest = createKMarketRequest("user"+userid, "resource"+resourceid,
                    amount, totalamount);
            historyReqs[i] = kmarketrequest;
        }

//        String[][] finalres = new String[nWorkers][];
//        for (int i=0; i<nWorkers; i++) {
//            finalres[i] = new String[exampleNum];
//        }
//        final CountDownLatch latch = new CountDownLatch(nWorkers);
//        start = System.nanoTime();
//        for (int j=0; j<nWorkers; j++) {
//            parallelVerifier.submit(() -> {
//                int ind = (int) Thread.currentThread().getId() % nWorkers;
//                for (int l=0; l<exampleNum; l++) {
//                    finalres[ind][l] = shortise(pdpList[ind].evaluate(historyReqs[l]));
//                }
//                System.out.println("worker "+ind + " finished");
//                latch.countDown();
//            });
//        }
//        try {
//            latch.await();
//        } catch (InterruptedException e) {
//            System.out.println(e + "balana parallel execution fail");
//        }
//
//        System.out.println("start checking----");
//        for (int i=0; i<exampleNum; i++) {
//            boolean compareres = true;
//            String first = finalres[0][i];
//            for (int j=1; j<nWorkers; j++) {
//                if (!finalres[j][i].equals(first)) {
//                    compareres = false;
//                    break;
//                }
//            }
//            if (compareres) {
//                if (i%1000==0)
//                    System.out.println(i + ": ok");
//            } else {
//                System.out.println(i + ": bad");
//                System.out.println(historyReqs[i]);
//            }
//        }



        // check repeated evaluation
        int repeatedTimes = 4;
        String[][] finalres = new String[repeatedTimes][];
        for (int i=0; i<repeatedTimes; i++) {
            finalres[i] = new String[exampleNum];
        }
        for (int j=0; j<repeatedTimes; j++) {
            for (int i=0; i<exampleNum; i++) {
                finalres[j][i] = shortise(pdpList[0].evaluate(historyReqs[i]));
            }
            try {
                Thread.sleep(200);
            } catch (Exception e) {
                System.out.println("sleep error");
            }

        }
        System.out.println("start checking----");
        for (int i=0; i<exampleNum; i++) {
            boolean compareres = true;
            String first = finalres[0][i];
            for (int j=1; j<repeatedTimes; j++) {
                if (!finalres[j][i].equals(first)) {
                    compareres = false;
                    break;
                }
            }
            if (compareres) {
                if (i%1000==0)
                    System.out.println(i + ": ok");
            } else {
                System.out.println(i + ": bad");
                System.out.println(historyReqs[i]);
            }
        }
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