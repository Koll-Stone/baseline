package org.example;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.CommandsInfo;
import bftsmart.tom.server.defaultservices.DefaultRecoverable;
import bftsmart.tom.util.Storage;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.wso2.balana.Balana;
import org.wso2.balana.PDP;
import org.wso2.balana.PDPConfig;
import org.wso2.balana.finder.PolicyFinderModule;
import org.wso2.balana.finder.impl.FileBasedPolicyFinderModule;
import org.wso2.balana.finder.impl.updatablePolicyFinderModule;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.example.baselineParameters.*;


public class backupServer extends DefaultRecoverable {

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(this.getClass());


    private static Balana[] balana;
    private PDP[] pdpList;
    private static int nWorkers = 1;
    private static ExecutorService parallelVerifier = Executors.newWorkStealingPool(nWorkers);



    private int interval;
    //    private byte[] reply;
    private float maxTp = -1;
    private boolean context;
    private int signed;

    private byte[] state;

    private int iterations = 0;
    private long throughputMeasurementStartTime = System.currentTimeMillis();

    private Storage totalLatency = null;
    private Storage consensusLatency = null;
    private Storage preConsLatency = null;
    private Storage posConsLatency = null;
    private Storage proposeLatency = null;
    private Storage writeLatency = null;
    private Storage acceptLatency = null;

    private Storage batchSize = null;

    private ServiceReplica replica;

    private RandomAccessFile randomAccessFile = null;
    private FileChannel channel = null;

    public backupServer(int id, int interval, boolean context, int write) {

        // initialize balana
        initProperty();
        updatablePolicyFinderModule[] upfmList = new updatablePolicyFinderModule[nWorkers];

        balana = new Balana[nWorkers];
        pdpList = new PDP[nWorkers];
        for (int i=0; i<nWorkers; i++) {
            balana[i] = Balana.getInstance();
            upfmList[i] = new updatablePolicyFinderModule();
            Set<PolicyFinderModule> set1 = new HashSet<>();
            set1.add(upfmList[i]);
            balana[i].getPdpConfig().getPolicyFinder().setModules(set1);
            pdpList[i] = new PDP(new PDPConfig(null, balana[i].getPdpConfig().getPolicyFinder(), null, true));
        }
        List<List<Document>> newpolicies = new ArrayList<List<Document>>();
        for (int i=0; i<nWorkers; i++) {
            newpolicies.add(new ArrayList<Document>());
        }
        List<Integer> ResourceIds = new ArrayList<>();

        for (int i=0; i<RESOURCENUM; i++) {
            ResourceIds.add(i);
        }


        int stateSize = 0;
        Random rnd = new Random(0);
        // add policy

        for (int i=0; i<USERNUM; i++) {
            for (int j=0; j<RESOURCENUM; j++) {
                int policyid = i*USERNUM + j;
                Collections.shuffle(ResourceIds, rnd);
                int a1 = ResourceIds.get(0) % RESOURCENUM;
                int a2 = ResourceIds.get(1) % RESOURCENUM;
                int a3 = ResourceIds.get(2) % RESOURCENUM;
                String kmarketPolicy = createKMarketPolicy(""+policyid,"user"+i, "resource"+a1,
                        "resource"+a2, "resource"+a3);
                stateSize += kmarketPolicy.length();
                for (int k=0; k<nWorkers; k++) {
                    newpolicies.get(k).add(testDataBuilder.toDocument(kmarketPolicy));
                }
            }
        }
        for (int i=0; i<nWorkers; i++) {
            upfmList[i].loadPolicyBatchFromMemory(newpolicies.get(i));
        }
        // initialize balana end


        this.interval = interval;
        this.context = context;
        this.signed = -1;

//        this.reply = new byte[replySize];
//
//        for (int i = 0; i < replySize ;i++)
//            reply[i] = (byte) i;

        this.state = new byte[stateSize];

        for (int i = 0; i < stateSize ;i++)
            state[i] = (byte) i;

        totalLatency = new Storage(interval);
        consensusLatency = new Storage(interval);
        preConsLatency = new Storage(interval);
        posConsLatency = new Storage(interval);
        proposeLatency = new Storage(interval);
        writeLatency = new Storage(interval);
        acceptLatency = new Storage(interval);

        batchSize = new Storage(interval);

        if (write > 0) {

            try {
                final File f = File.createTempFile("bft-"+id+"-", Long.toString(System.nanoTime()));
                randomAccessFile = new RandomAccessFile(f, (write > 1 ? "rwd" : "rw"));
                channel = randomAccessFile.getChannel();

                Runtime.getRuntime().addShutdownHook(new Thread() {

                    @Override
                    public void run() {

                        f.delete();
                    }
                });
            } catch (IOException ex) {
                ex.printStackTrace();
                System.exit(0);
            }
        }
        replica = new ServiceReplica(id, this, this);
    }

    @Override
    public byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs, boolean fromConsensus) {

        batchSize.store(commands.length);
        byte[][] replies = new byte[commands.length][];
        ReentrantLock replyLock = new ReentrantLock();

        // validate query in parallel, start
        String[] queries = new String[commands.length];
        for (int i=0; i<commands.length; i++) {
            byte[] tx = commands[i];

            ByteBuffer buffer = ByteBuffer.wrap(tx);
            int l = buffer.getInt();
            byte[] requestbyte = new byte[l];
            buffer.get(requestbyte);
            queries[i] = new String(requestbyte);
        }



//        for (int i=0; i<commands.length; i++) {
//            logger.info("request is " + queries[i].hashCode());
//            String result = pdpList[0].evaluate(queries[i]);
//            replies[i] = result.getBytes();
//            logger.info("result is " + shortise(result));
//        }

        final CountDownLatch latch = new CountDownLatch(nWorkers);
        logger.debug(commands.length + " requests to validate in parallel");
        for (int i=0; i<commands.length; i++) {
            final int cind = i;
            parallelVerifier.submit(() -> {
                try {
                    int tind = (int) Thread.currentThread().getId() % nWorkers;
                    String result = pdpList[tind].evaluate(queries[cind]);
                    replyLock.lock();
                    replies[cind] = result.getBytes();
                    replyLock.unlock();
//                    logger.info("request is " + queries[cind].hashCode() + ", result is " + shortise(result));
//                    logger.info("\n" + Thread.currentThread().getName()+" finished validating 1 request, the query is\n" +
////                            queries[cind] + "\nthe result is " + shortise(result) + "\n");
                }
                catch (Exception e) {
                    logger.info("error in validating query");
                }
                latch.countDown();
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.info(e + "Ocurred during PDPB Executor operation");
        }

        // validate query in parallel, end
        for (int i = 0; i < commands.length; i++) {
            recordInfo(commands[i],msgCtxs[i]);
        }


        if (randomAccessFile != null) {

            ObjectOutputStream oos = null;
            try {
                CommandsInfo cmd = new CommandsInfo(commands,msgCtxs);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                oos = new ObjectOutputStream(bos);
                oos.writeObject(cmd);
                oos.flush();
                byte[] bytes = bos.toByteArray();
                oos.close();
                bos.close();

                ByteBuffer bb = ByteBuffer.allocate(bytes.length);
                bb.put(bytes);
                bb.flip();

                channel.write(bb);
                channel.force(false);
            } catch (IOException ex) {
                Logger.getLogger(backupServer.class.getName()).log(Level.SEVERE, null, ex);

            } finally {
                try {
                    oos.close();
                } catch (IOException ex) {
                    Logger.getLogger(backupServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }


        // for (int i=0; i<commands.length; i++) {
        //     replies[i] = "reply".getBytes();
        // }

        return replies;
    }

    @Override
    public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
        recordInfo(command,msgCtx);
        byte[] res = new byte[100];
        return res;
    }

    public void recordInfo(byte[] command, MessageContext msgCtx) {

//        ByteBuffer buffer = ByteBuffer.wrap(command);
//        int l = buffer.getInt();
//        byte[] request = new byte[l];
//        buffer.get(request);
//        l = buffer.getInt();
//        byte[] signature = new byte[l];
//
//        buffer.get(signature);
//        Signature eng;

//        try {
//
//            if (signed > 0) {
//
//                if (signed == 1) {
//
//                    eng = TOMUtil.getSigEngine();
//                    eng.initVerify(replica.getReplicaContext().getStaticConfiguration().getPublicKey());
//                } else {
//
//                    eng = Signature.getInstance("SHA256withECDSA", "SunEC");
//                    Base64.Decoder b64 = Base64.getDecoder();
//                    CertificateFactory kf = CertificateFactory.getInstance("X.509");
//
//                    byte[] cert = b64.decode(ThroughputLatencyClient.pubKey);
//                    InputStream certstream = new ByteArrayInputStream (cert);
//
//                    eng.initVerify(kf.generateCertificate(certstream));
//
//                }
//                eng.update(request);
//                if (!eng.verify(signature)) {
//
//                    logger.info("Client sent invalid signature!");
//                    System.exit(0);
//                }
//            }
//
//        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | CertificateException ex) {
//            ex.printStackTrace();
//            System.exit(0);
//        } catch (NoSuchProviderException ex) {
//            ex.printStackTrace();
//            System.exit(0);
//        }

        boolean readOnly = false;

        iterations++;

        if (msgCtx != null && msgCtx.getFirstInBatch() != null) {


            readOnly = msgCtx.readOnly;

            msgCtx.getFirstInBatch().executedTime = System.nanoTime();

            totalLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().receptionTime);

            if (readOnly == false) {

                consensusLatency.store(msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().consensusStartTime);
                long temp = msgCtx.getFirstInBatch().consensusStartTime - msgCtx.getFirstInBatch().receptionTime;
                preConsLatency.store(temp > 0 ? temp : 0);
                posConsLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().decisionTime);
                proposeLatency.store(msgCtx.getFirstInBatch().writeSentTime - msgCtx.getFirstInBatch().consensusStartTime);
                writeLatency.store(msgCtx.getFirstInBatch().acceptSentTime - msgCtx.getFirstInBatch().writeSentTime);
                acceptLatency.store(msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().acceptSentTime);


            } else {


                consensusLatency.store(0);
                preConsLatency.store(0);
                posConsLatency.store(0);
                proposeLatency.store(0);
                writeLatency.store(0);
                acceptLatency.store(0);


            }

        } else {


            consensusLatency.store(0);
            preConsLatency.store(0);
            posConsLatency.store(0);
            proposeLatency.store(0);
            writeLatency.store(0);
            acceptLatency.store(0);


        }

        float tp = -1;
        if(iterations % interval == 0) {
            if (context) logger.info("--- (Context)  iterations: "+ iterations + " // regency: " + msgCtx.getRegency() + " // consensus: " + msgCtx.getConsensusId() + " ---");

            logger.info("--- Measurements after "+ iterations+" ops ("+interval+" samples) ---");

            tp = (float)(interval*1000/(float)(System.currentTimeMillis()-throughputMeasurementStartTime));

            if (tp > maxTp) maxTp = tp;

            logger.info("Throughput = " + tp +" operations/sec (Maximum observed: " + maxTp + " ops/sec)");

            logger.info("Total latency = " + totalLatency.getAverage(false) / 1000 + " (+/- "+ (long)totalLatency.getDP(false) / 1000 +") us ");
            totalLatency.reset();
            logger.info("Consensus latency = " + consensusLatency.getAverage(false) / 1000 + " (+/- "+ (long)consensusLatency.getDP(false) / 1000 +") us ");
            consensusLatency.reset();
            logger.info("Pre-consensus latency = " + preConsLatency.getAverage(false) / 1000 + " (+/- "+ (long)preConsLatency.getDP(false) / 1000 +") us ");
            preConsLatency.reset();
            logger.info("Pos-consensus latency = " + posConsLatency.getAverage(false) / 1000 + " (+/- "+ (long)posConsLatency.getDP(false) / 1000 +") us ");
            posConsLatency.reset();
            logger.info("Propose latency = " + proposeLatency.getAverage(false) / 1000 + " (+/- "+ (long)proposeLatency.getDP(false) / 1000 +") us ");
            proposeLatency.reset();
            logger.info("Write latency = " + writeLatency.getAverage(false) / 1000 + " (+/- "+ (long)writeLatency.getDP(false) / 1000 +") us ");
            writeLatency.reset();
            logger.info("Accept latency = " + acceptLatency.getAverage(false) / 1000 + " (+/- "+ (long)acceptLatency.getDP(false) / 1000 +") us ");
            acceptLatency.reset();

            logger.info("Batch average size = " + batchSize.getAverage(false) + " (+/- "+ (long)batchSize.getDP(false) +") requests");
            batchSize.reset();

            throughputMeasurementStartTime = System.currentTimeMillis();
        }

    }

    public static void main(String[] args){
//        if(args.length < 6) {
//            logger.info("Usage: ... backserver <processId> <measurement interval> <reply size> <state size> <context?> <nosig | default | ecdsa> [rwd | rw]");
//            System.exit(-1);
//        }
        if(args.length < 4) {
            System.out.println("Usage: ... backserver <processId> <measurement interval> <context?> [rwd | rw]");
            System.exit(-1);
        }

        int processId = Integer.parseInt(args[0]);
        int interval = Integer.parseInt(args[1]);
//        int replySize = Integer.parseInt(args[2]);
//        int stateSize = Integer.parseInt(args[3]);
        boolean context = Boolean.parseBoolean(args[2]);
//        String signed = args[3];
        String write = args.length > 6 ? args[3] : "";

//        int s = 0;
//
//        if (!signed.equalsIgnoreCase("nosig")) s++;
//        if (signed.equalsIgnoreCase("ecdsa")) s++;

//        if (s == 2 && Security.getProvider("SunEC") == null) {
//
//            logger.info("Option 'ecdsa' requires SunEC provider to be available.");
//            System.exit(0);
//        }

        int w = 0;

        if (!write.equalsIgnoreCase("")) w++;
        if (write.equalsIgnoreCase("rwd")) w++;

        new backupServer(processId,interval, context, w);
    }

    @Override
    public void installSnapshot(byte[] state) {
        //nothing
    }

    @Override
    public byte[] getSnapshot() {
        return this.state;
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



}
