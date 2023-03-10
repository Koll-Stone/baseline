package org.example;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.CommandsInfo;
import bftsmart.tom.server.defaultservices.DefaultRecoverable;
import bftsmart.tom.util.Storage;
import bftsmart.tom.util.TOMUtil;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.wso2.balana.Balana;
import org.wso2.balana.PDP;
import org.wso2.balana.PDPConfig;
import org.wso2.balana.finder.PolicyFinderModule;
import org.wso2.balana.finder.impl.FileBasedPolicyFinderModule;
import org.wso2.balana.finder.impl.updatablePolicyFinderModule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.Signature;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.example.baselineParameters.*;

public final class baselineServer extends DefaultRecoverable {

    private List<Integer> tpsdata = new ArrayList<>();
    private final org.slf4j.Logger logger = LoggerFactory.getLogger(this.getClass());


    private static Balana[] balana;
    private PDP[] pdpList;
    private static int nWorkers = 2;
    private static ExecutorService parallelVerifier = Executors.newWorkStealingPool(nWorkers);
    private List<String> allPolicies;

    private int interval;
    private byte[] reply;
    private float maxTp = -1.0F;
    private boolean context;
    private boolean signed;
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


    private long total40ktime;
    private boolean total100kupdateflag;
    private long total60ktime;
    private boolean total160kupdateflag;

    public baselineServer(int id, int interval, boolean context, boolean signed, int write) {


        total40ktime = System.currentTimeMillis();
        total100kupdateflag = false;
        total60ktime = System.currentTimeMillis();
        total160kupdateflag = false;


        logger.info("w is {}", write);
        // initialize balana
        initProperty();
        updatablePolicyFinderModule[] upfmList = new updatablePolicyFinderModule[nWorkers];
        allPolicies = new ArrayList<String>();

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
        List<Document> initialPolicies = new ArrayList<Document>();
        List<Integer> ResourceIds = new ArrayList<>();

        for (int i=0; i<RESOURCENUM; i++) {
            ResourceIds.add(i);
        }

        // add policy
        int stateSize = 0;
        int policyIndex = 0;
        Random rnd = new Random(0);
        for (int i=0; i<USERNUM; i++) {
            for (int j=0; j<POLICYEACHUSER; j++) {
                policyIndex++;
                Collections.shuffle(ResourceIds, rnd);
                int a1 = ResourceIds.get(0) % RESOURCENUM;
                int a2 = ResourceIds.get(1) % RESOURCENUM;
                int a3 = ResourceIds.get(2) % RESOURCENUM;
                String kmarketPolicy = createKMarketPolicy("policy"+policyIndex,"user"+i, "resource"+a1,
                        "resource"+a2, "resource"+a3);
                stateSize += kmarketPolicy.length();
                for (int k=0; k<nWorkers; k++) {
                    initialPolicies.add(testDataBuilder.toDocument(kmarketPolicy));
                }
                allPolicies.add(kmarketPolicy);
            }
        }
        for (int i=0; i<nWorkers; i++) {
            upfmList[i].loadPolicyBatchFromMemory(initialPolicies);
        }
        // initialize balana end





        this.interval = interval;
        this.context = context;
        this.signed = signed;

        logger.info("state size is " + stateSize);
        this.state = new byte[stateSize];
        
        int i;
        for(i = 0; i < stateSize; ++i) {
            this.state[i] = (byte)i;
        }

        this.totalLatency = new Storage(interval);
        this.consensusLatency = new Storage(interval);
        this.preConsLatency = new Storage(interval);
        this.posConsLatency = new Storage(interval);
        this.proposeLatency = new Storage(interval);
        this.writeLatency = new Storage(interval);
        this.acceptLatency = new Storage(interval);
        this.batchSize = new Storage(interval);
        if (write > 0) {
            try {
                final File f = File.createTempFile("bft-" + id + "-", Long.toString(System.nanoTime()));
                this.randomAccessFile = new RandomAccessFile(f, write > 1 ? "rwd" : "rw");
                this.channel = this.randomAccessFile.getChannel();
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    public void run() {

                        f.delete();
                    }
                });
            } catch (IOException var9) {
                var9.printStackTrace();
                System.exit(0);
            }
        }

        this.replica = new ServiceReplica(id, this, this);

        // try {
        //     Thread.sleep(300000);
        //     this.replica.kill();
        //     logger.info("kill service replica after running for 30 seconds");
        // } catch (InterruptedException e) {
        //     System.out.println("baseline server sleep failed!\n" + e);
        // }
    }

    public byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs, boolean fromConsensus) {
        logger.debug("execute batch with size = {} ", commands.length);
        this.batchSize.store((long)commands.length);
        byte[][] replies = new byte[commands.length][];
        byte[][] signatures = new byte[commands.length][];


        byte[][] queries = new byte[commands.length][];
        for(int i = 0; i < commands.length; ++i) {
            ByteBuffer buffer = ByteBuffer.wrap(commands[i]);
            int l = buffer.getInt();
            byte[] request = new byte[l];
            buffer.get(request);
            queries[i] = request;
            l = buffer.getInt();
            signatures[i] = new byte[l];
            buffer.get(signatures[i]);
        }

        


        // multi-thread evaluation
        CountDownLatch latch = new CountDownLatch(commands.length);
        for (int i=0; i<commands.length; i++) {
            final int cind = i;
            parallelVerifier.submit(() -> {
                try {
                    int tind = (int) Thread.currentThread().getId() % nWorkers;
                    // String result = pdpList[tind].evaluate(new String(queries[cind]));
                    String result = "this is result";
                    replies[cind] = result.getBytes();
//                    logger.info("thread {} finishes its work", tind);

                    if (this.signed) {
                        try {
                            Signature ecdsaVerify = TOMUtil.getSigEngine();
                            ecdsaVerify.initVerify(replica.getReplicaContext().getStaticConfiguration().getPublicKey());
                            ecdsaVerify.update(queries[cind]);
                            if (!ecdsaVerify.verify(signatures[cind])) {
                                System.out.println("Client sent invalid signature!");
                                System.exit(0);
                            } else {
                                // System.out.println("thread " + tind + " finished validating 1 request sig which is valid");
                            }
                        } catch (Exception e) {
                            System.out.println("error in validating query " + e);
                        }
                    }



                    latch.countDown();
                } catch (Exception e) {
                    logger.info("error in multi-thread evaluation\n" + e);
                }
            });
        }
        try {
            latch.await();
            for (byte[] reply: replies) {
//                logger.info(new String(reply));
            }
        } catch (InterruptedException e) {
            logger.info("error in waiting multi-thread evaluation\n"+e);
        }

        if (msgCtxs[0].getConsensusId()==8000) {
            System.out.println("consensus 8000, batch size = "+commands.length);
        }

        for(int i = 0; i < commands.length; ++i) {
            this.recordInfo(queries[i], msgCtxs[i]);
        }


        if (this.randomAccessFile != null) {
            ObjectOutputStream oos = null;

            try {
                CommandsInfo cmd = new CommandsInfo(commands, msgCtxs);
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
                this.channel.write(bb);
                this.channel.force(false);
            } catch (IOException var18) {
                Logger.getLogger(baselineServer.class.getName()).log(Level.SEVERE, (String)null, var18);
            } finally {
                try {
                    oos.close();
                } catch (IOException var17) {
                    Logger.getLogger(baselineServer.class.getName()).log(Level.SEVERE, (String)null, var17);
                }

            }
        }

        return replies;
    }

    public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
        ByteBuffer buffer = ByteBuffer.wrap(command);
        int l = buffer.getInt();
        byte[] request = new byte[l];
        buffer.get(request);

        return this.recordInfo(request, msgCtx);
    }

    public byte[] recordInfo(byte[] request, MessageContext msgCtx) {


        boolean readOnly = false;
        ++this.iterations;

        if (iterations>100000 && !total100kupdateflag)
        {
            total40ktime = System.currentTimeMillis();
            total100kupdateflag = true;
        }
        if (iterations>160000 && !total160kupdateflag) {
            total60ktime = System.currentTimeMillis();
            logger.info("************************\ntps at stable phase is {} \n***********************",
                    (60000.0*1000.0/(total60ktime-total40ktime)));
            total160kupdateflag = true;
        }

        if (msgCtx != null && msgCtx.getFirstInBatch() != null) {
            readOnly = msgCtx.readOnly;
            msgCtx.getFirstInBatch().executedTime = System.nanoTime();
            this.totalLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().receptionTime);

            if (msgCtx.getConsensusId()==8000) {
                System.out.println("*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*- consensus instance "+ msgCtx.getConsensusId());
                System.out.println("consensus latency: " + ( msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().consensusStartTime)/1000 + " us");
                long temp = msgCtx.getFirstInBatch().consensusStartTime - msgCtx.getFirstInBatch().receptionTime;
                System.out.println("preconsensus latency: " +( temp > 0L ? temp : 0L)/1000 + " us");
                System.out.println("pos consensus latency: " + (msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().decisionTime)/1000 + " us");
                System.out.println("propose latency: " + (msgCtx.getFirstInBatch().writeSentTime - msgCtx.getFirstInBatch().consensusStartTime)/1000 + " us");
                System.out.println("write latency: " + (msgCtx.getFirstInBatch().acceptSentTime - msgCtx.getFirstInBatch().writeSentTime)/1000 + " us");
                System.out.println("accept latency: " + (msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().acceptSentTime)/1000 + " us");
                System.out.println("*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-");
            }

            if (!readOnly) {
                this.consensusLatency.store(msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().consensusStartTime);
                long temp = msgCtx.getFirstInBatch().consensusStartTime - msgCtx.getFirstInBatch().receptionTime;
                this.preConsLatency.store(temp > 0L ? temp : 0L);
                this.posConsLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().decisionTime);
                this.proposeLatency.store(msgCtx.getFirstInBatch().writeSentTime - msgCtx.getFirstInBatch().consensusStartTime);
                this.writeLatency.store(msgCtx.getFirstInBatch().acceptSentTime - msgCtx.getFirstInBatch().writeSentTime);
                this.acceptLatency.store(msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().acceptSentTime);
            } else {
                this.consensusLatency.store(0L);
                this.preConsLatency.store(0L);
                this.posConsLatency.store(0L);
                this.proposeLatency.store(0L);
                this.writeLatency.store(0L);
                this.acceptLatency.store(0L);
            }
        } else {
            this.consensusLatency.store(0L);
            this.preConsLatency.store(0L);
            this.posConsLatency.store(0L);
            this.proposeLatency.store(0L);
            this.writeLatency.store(0L);
            this.acceptLatency.store(0L);
        }

        float tp = -1.0F;
        if (this.iterations % this.interval == 0) {
            if (this.context) {
                System.out.println("--- (Context)  iterations: " + this.iterations + " // regency: " + msgCtx.getRegency() + " // consensus: " + msgCtx.getConsensusId() + " ---");
            }

            System.out.println("--- Measurements after " + this.iterations + " ops (" + this.interval + " samples) ---");
            tp = (float)(this.interval * 1000) / (float)(System.currentTimeMillis() - this.throughputMeasurementStartTime);
            if (tp > this.maxTp) {
                this.maxTp = tp;
            }
            tpsdata.add((int) tp);
            if (tpsdata.size()>4)
                System.out.println(Arrays.toString(tpsdata.toArray()));

            System.out.println("Throughput = " + tp + " operations/sec (Maximum observed: " + this.maxTp + " ops/sec)");
            System.out.println("Total latency = " + this.totalLatency.getAverage(false) / 1000.0 + " (+/- " + (long)this.totalLatency.getDP(false) / 1000L + ") us ");
            this.totalLatency.reset();
            System.out.println("Consensus latency = " + this.consensusLatency.getAverage(false) / 1000.0 + " (+/- " + (long)this.consensusLatency.getDP(false) / 1000L + ") us ");
            this.consensusLatency.reset();
            System.out.println("Pre-consensus latency = " + this.preConsLatency.getAverage(false) / 1000.0 + " (+/- " + (long)this.preConsLatency.getDP(false) / 1000L + ") us ");
            this.preConsLatency.reset();
            System.out.println("Pos-consensus latency = " + this.posConsLatency.getAverage(false) / 1000.0 + " (+/- " + (long)this.posConsLatency.getDP(false) / 1000L + ") us ");
            this.posConsLatency.reset();
            System.out.println("Propose latency = " + this.proposeLatency.getAverage(false) / 1000.0 + " (+/- " + (long)this.proposeLatency.getDP(false) / 1000L + ") us ");
            this.proposeLatency.reset();
            System.out.println("Write latency = " + this.writeLatency.getAverage(false) / 1000.0 + " (+/- " + (long)this.writeLatency.getDP(false) / 1000L + ") us ");
            this.writeLatency.reset();
            System.out.println("Accept latency = " + this.acceptLatency.getAverage(false) / 1000.0 + " (+/- " + (long)this.acceptLatency.getDP(false) / 1000L + ") us ");
            this.acceptLatency.reset();
            System.out.println("Batch average size = " + this.batchSize.getAverage(false) + " (+/- " + (long)this.batchSize.getDP(false) + ") requests");
            this.batchSize.reset();
            this.throughputMeasurementStartTime = System.currentTimeMillis();


        }

        return "reply".getBytes();
    }

    public static void main(String[] args) {
        // <processid> <measurement interval> <showcontext?> <signed?>
        int processId = Integer.parseInt(args[0]);
        int interval = Integer.parseInt(args[1]);
        boolean context = Boolean.parseBoolean(args[2]);
        boolean signed = Boolean.parseBoolean(args[3]);
        String write = "";


        int w = 0;
        if (!write.equalsIgnoreCase("")) {
            ++w;
        }

        if (write.equalsIgnoreCase("rwd")) {
            ++w;
        }

        new baselineServer(processId, interval, context, signed, w);
        // System.out.println("baseline server main thread stops");
        // System.exit(0);
    }

    public void installSnapshot(byte[] state) {
    }

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
