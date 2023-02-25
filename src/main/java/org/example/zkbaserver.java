package org.example;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultRecoverable;
import bftsmart.tom.util.Storage;
import bftsmart.tom.util.TOMUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Signature;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class zkbaserver extends DefaultRecoverable {

    private static int nWorkers = 2;
    private static ExecutorService parallelVerifier = Executors.newWorkStealingPool(nWorkers);


    private int id;

    private static List<byte[]> Ltm;
    private static Map<Integer, byte[]> LtmMerkleRoot;

    private static Set<byte[]> snSets;
    private static List<byte[]> Lcm;
    private static List<Integer> delegatezkpcosts;
    private static List<Integer> usezkpcosts;


    private int interval;
    private int iterations = 0;
    private long throughputMeasurementStartTime = System.currentTimeMillis();

    private float maxTp = 0;
    private Storage totalLatency = null;
    private Storage consensusLatency = null;
    private Storage preConsLatency = null;
    private Storage posConsLatency = null;
    private Storage proposeLatency = null;
    private Storage writeLatency = null;
    private Storage acceptLatency = null;
    private Storage batchSize = null;
    private ServiceReplica replica;

    long total40ktime;
    boolean total100kupdateflag;
    long total60ktime;
    boolean total160kupdateflag;

    public zkbaserver(int id) {


        total40ktime = System.currentTimeMillis();
        total100kupdateflag = false;
        total60ktime = System.currentTimeMillis();
        total160kupdateflag = false;
        interval = 10000;
        this.totalLatency = new Storage(interval);
        this.consensusLatency = new Storage(interval);
        this.preConsLatency = new Storage(interval);
        this.posConsLatency = new Storage(interval);
        this.proposeLatency = new Storage(interval);
        this.writeLatency = new Storage(interval);
        this.acceptLatency = new Storage(interval);
        this.batchSize = new Storage(interval);

        this.id = id;

        Ltm = new ArrayList<byte[]>();
        LtmMerkleRoot = new HashMap<Integer, byte[]>();
        Lcm = new ArrayList<byte[]>();
        snSets = new HashSet<byte[]>();
        delegatezkpcosts = new ArrayList<Integer>();
        usezkpcosts = new ArrayList<Integer>();

        //initialize delegatezkpcosts and usezkpcosts
        String delefile = "zkpexperiment/zkpcostdata/delegate_result.txt";
        String usefile = "zkpexperiment/zkpcostdata/use_result.txt";

        try {
            String deleres = readFile(delefile, StandardCharsets.UTF_8);
            for (String x: deleres.split(",")) {
                delegatezkpcosts.add(Integer.parseInt(x));
            }

            int sum = 0;
            for (int x: delegatezkpcosts) sum+=x;
            System.out.println("delegate average cost " + sum/delegatezkpcosts.size()+ " us");

            String useres = readFile(usefile, StandardCharsets.UTF_8);
            for (String x:useres.split(",")) {
                usezkpcosts.add(Integer.parseInt(x));
            }
            sum=0;
            for (int x:usezkpcosts) sum+=x;
            System.out.println("use average cost " + sum/usezkpcosts.size()+" us");


            System.out.println("delegate cost length: " + delegatezkpcosts.size() +", use cost length: " + usezkpcosts.size());
        } catch (IOException e) {

        }


        this.replica = new ServiceReplica(id, this, this);

    }


    public static void main(String[] args) {
        int id = Integer.parseInt(args[0]);
        new zkbaserver(id);
    }

    static String readFile(String path, Charset encoding)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    public byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs, boolean fromConsensus) {
        byte[][] replies = new byte[commands.length][];
        for (int i=0; i<commands.length; i++) {
            replies[i] = "this is reply".getBytes();
        }

        CountDownLatch latch = new CountDownLatch(commands.length);
        for (int i=0; i<commands.length; i++) {
            final int cind = i;
            parallelVerifier.submit(() -> {
                try {
                    int tind = (int) Thread.currentThread().getId() % nWorkers;

                    long start = System.nanoTime();
                    replies[cind] = processtx(commands[cind]);
                    long duration = System.nanoTime()-start;
//                    System.out.println("worker "+tind + " finished "+cind+" time costs " + duration/1000 +" us");
                    latch.countDown();
                } catch (Exception e) {
                    System.out.print("error in multi-thread evaluation\n" + e);
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            System.out.println("error in executing batch");
        }


        for(int i = 0; i < commands.length; ++i) {
            this.recordInfo(commands[i], msgCtxs[i]);
        }

        return replies;
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
            System.out.println("************************\ntps at stable phase is {} \n***********************"+
                    (60000.0*1000.0/(total60ktime-total40ktime)));
            total160kupdateflag = true;
        }

        if (msgCtx != null && msgCtx.getFirstInBatch() != null) {
            readOnly = msgCtx.readOnly;
            msgCtx.getFirstInBatch().executedTime = System.nanoTime();
            this.totalLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().receptionTime);
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
            System.out.println("--- (Context)  iterations: " + this.iterations + " // regency: " + msgCtx.getRegency() + " // consensus: " + msgCtx.getConsensusId() + " ---");


            System.out.println("--- Measurements after " + this.iterations + " ops (" + this.interval + " samples) ---");
            tp = (float)(this.interval * 1000) / (float)(System.currentTimeMillis() - this.throughputMeasurementStartTime);
            if (tp > this.maxTp) {
                this.maxTp = tp;
            }

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

    private byte[] processtx(byte[] command) {
        ByteBuffer buffer = ByteBuffer.wrap(command);
        int l=buffer.getInt();
        switch (l) {
            case 0:
                return processmint(command);
            case 1:
                return processdestroy(command);
            case 2:
                return processdelegate(command);
            case 3:
                return processuse(command);
        }
        return new byte[0];
    }

    private byte[] processmint(byte[] command) {

        ByteBuffer buffer = ByteBuffer.wrap(command);
        int l=buffer.getInt();
        byte[] cm = new byte[32];
        buffer.get(cm);
        byte[] t = new byte[32];
        buffer.get(t);
        byte[] tm = new byte[32];
        buffer.get(tm);


        l = buffer.getInt();
//        System.out.print("signature length l is "+ l);
        byte[] signature = new byte[l];
        buffer.get(signature);

        byte[] content = new byte[96];
        System.arraycopy(cm, 0, content, 0, 32);
        System.arraycopy(t,0, content, 32, 32);
        System.arraycopy(tm, 0, content, 64, 32);

        try {
            Signature ecdsaVerify = TOMUtil.getSigEngine();
            ecdsaVerify.initVerify(replica.getReplicaContext().getStaticConfiguration().getPublicKey());
            ecdsaVerify.update(content);
            if (!ecdsaVerify.verify(signature)) {
                System.out.println("Client sent invalid signature!");
                System.exit(0);
            } else {
//                System.out.println("finished validating 1 request sig which is valid");
            }
        } catch (Exception e) {
            System.out.println("error in validating tx mint " + e);
        }

        Ltm.add(tm);
        return "tx create committed".getBytes();
    }

    private byte[] processdestroy(byte[] command) {
        ByteBuffer buffer = ByteBuffer.wrap(command);
        int l=buffer.getInt();
        byte[] cm = new byte[32];
        buffer.get(cm);


        l = buffer.getInt();
        byte[] signature = new byte[l];
        buffer.get(signature);



        try {
            Signature ecdsaVerify = TOMUtil.getSigEngine();
            ecdsaVerify.initVerify(replica.getReplicaContext().getStaticConfiguration().getPublicKey());
            ecdsaVerify.update(cm);
            if (!ecdsaVerify.verify(signature)) {
                System.out.println("Client sent invalid signature!");
                System.exit(0);
            } else {
//                System.out.println("finished validating 1 request sig which is valid");
            }
        } catch (Exception e) {
            System.out.println("error in validating tx destroy " + e);
        }

        Lcm.add(cm);
        return "tx destroy committed".getBytes();
    }

    private byte[] processdelegate(byte[] command) {
        ByteBuffer buffer = ByteBuffer.wrap(command);
        int l=buffer.getInt();
        byte[] rt = new byte[32];
        buffer.get(rt);
        byte[] sn = new byte[32];
        buffer.get(sn);
        byte[] tm = new byte[32];
        buffer.get(tm);
        l = buffer.getInt();
        byte[] pidele = new byte[l];
        buffer.get(pidele);


        if (!LtmMerkleRoot.containsValue(rt)) {
            //todo, bad thing
        }
        if (snSets.contains(sn)) {
            //todo, bad thing
        }




        Random rand = new Random();
        int randomElement = delegatezkpcosts.get(rand.nextInt(delegatezkpcosts.size()));
        busyWait(randomElement);

        Ltm.add(tm);
        return "tx delegate committed".getBytes();
    }

    private byte[] processuse(byte[] command) {
        ByteBuffer buffer = ByteBuffer.wrap(command);
        int l=buffer.getInt();
        byte[] rt = new byte[32];
        buffer.get(rt);
        byte[] sn = new byte[32];
        buffer.get(sn);
        byte[] cm = new byte[32];
        buffer.get(cm);
        byte[] wm = new byte[32];
        buffer.get(wm);
        l = buffer.getInt();
        byte[] piuse = new byte[l];
        buffer.get(piuse);


        if (!LtmMerkleRoot.containsValue(rt)) {
            //todo, bad thing
        }
        if (snSets.contains(sn)) {
            //todo, bad thing
        }

        Random rand = new Random();
        int randomElement = usezkpcosts.get(rand.nextInt(usezkpcosts.size()));
        busyWait(randomElement);

        return "tx use committed".getBytes();
    }

    // interval: us
    public void busyWait(int interval) {
        long start = System.nanoTime();
        long end = 0;
        do {
            end = System.nanoTime();
        } while (start+interval*1000>=end);
    }

    public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
//        return new byte[0];
        return "yes it is correct".getBytes();
        // todo
    }

    public void installSnapshot(byte[] state) {

    }

    public byte[] getSnapshot() {
        return new byte[0];
        // todo
    }


}
