package org.example;

import bftsmart.tom.ServiceProxy;
import bftsmart.tom.util.Storage;
import bftsmart.tom.util.TOMUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;
// import java.util.Collection;
// import java.util.List;
// import java.util.LinkedList;
// import java.util.Random;
import java.util.concurrent.*;

import static org.example.baselineParameters.*;

public class baselineClient {

    public static int initId = 0;
    static LinkedBlockingQueue<String> latencies;
    static Thread writerThread;

    public static String privKey = "MD4CAQAwEAYHKoZIzj0CAQYFK4EEAAoEJzAlAgEBBCBnhIob4JXH+WpaNiL72BlbtUMAIBQoM852d+tKFBb7fg==";
    public static String pubKey = "MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEavNEKGRcmB7u49alxowlwCi1s24ANOpOQ9UiFBxgqnO/RfOl3BJm0qE2IJgCnvL7XUetwj5C/8MnMWi9ux2aeQ==";


    @SuppressWarnings("static-access")
    public static void main(String[] args) throws IOException {


        if (args.length < 6) {
            System.out.println("Usage: ... ThroughputLatencyClient <initial client id> <number of clients> <number of operations> " +
                    "<interval (ms)> <verbose?> <signed>");
            System.exit(-1);
        }

        initId = Integer.parseInt(args[0]);
        int numThreads = Integer.parseInt(args[1]);
        int numberOfOps = Integer.parseInt(args[2]);
        int interval = Integer.parseInt(args[3]);
        boolean verbose = Boolean.parseBoolean(args[4]);
        boolean signed = Boolean.parseBoolean(args[5]);

        latencies = new LinkedBlockingQueue<>();
        writerThread = new Thread() {

            public void run() {

                FileWriter f = null;
                try {
                    f = new FileWriter("./latencies_" + initId + ".txt");
                    while (true) {

                        f.write(latencies.take());
                    }

                } catch (IOException | InterruptedException ex) {
                    ex.printStackTrace();
                } finally {
                    try {
                        f.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        };
        writerThread.start();

        

        Client[] clients = new Client[numThreads];
        for(int i=0; i<numThreads; i++) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {

                ex.printStackTrace();
            }

            System.out.println("Launching client " + (initId+i));
            clients[i] = new Client(initId+i,numberOfOps, interval, verbose, signed);
        }

        ExecutorService exec = Executors.newFixedThreadPool(clients.length);
        Collection<Future<?>> tasks = new LinkedList<>();

        for (Client c : clients) {
            tasks.add(exec.submit(c));
        }

        // wait for tasks completion
        for (Future<?> currTask : tasks) {
            try {
                currTask.get();
            } catch (InterruptedException | ExecutionException ex) {

                ex.printStackTrace();
            }

        }


        List<Long> latencyres = new ArrayList<Long>();
        for (int i=0; i<numThreads; i++) {
            for (long x: clients[i].getLatencydata().getValues())
                latencyres.add(x);
        }
        long[] finaldata = new long[latencyres.size()];
        for(int i=0; i<latencyres.size(); i++) {
            finaldata[i] = latencyres.get(i);
        }
        double averagelatency = computeAverage(finaldata, true);


        System.out.println("All clients done. average latency is "+averagelatency/1000000 + " ms");


        exec.shutdown();

        System.out.println("All clients done.");
    }

    private static double computeAverage(long[] values, boolean percent) {
        Arrays.sort(values);
        int limit = 0;
        if (percent) {
            limit = values.length / 10;
        }

        long count = 0L;

        for(int i = limit; i < values.length - limit; ++i) {
            count += values[i];
        }

        return (double)count / (double)(values.length - 2 * limit);
    }

    static class Client extends Thread {

        int id;
        Storage st;
        boolean signed;
        int numberOfOps;
        int interval;
        boolean verbose;
        ServiceProxy proxy;
        int rampup = 1000;

        public Client(int id, int numberOfOps, int interval, boolean verbose, boolean sign) {
            super("Client "+id);

            this.id = id;
            this.signed = sign;
            this.numberOfOps = numberOfOps;

            this.interval = interval;

            this.verbose = verbose;
            this.proxy = new ServiceProxy(id);
        }

        public byte[] getARequest(int req) {

            Random ran = new Random(System.nanoTime() + this.id);
            int userid = ran.nextInt(USERNUM);
            int resourceid = ran.nextInt(RESOURCENUM);
            int amount = ran.nextInt(10);
            int totalamount = ran.nextInt(80);
            String kMarketRequest = createKMarketRequest("user"+userid, "resource"+resourceid,
                    amount, totalamount);

//            System.out.println("client " + this.id + " request " + req + " is " + kMarketRequest.hashCode() +"\nuserid=" + userid + ", resource=" + resourceid + ", amount=" + amount +
//                    ", totalamount=" + totalamount);
            byte[] request = kMarketRequest.getBytes();
            byte[] signature = new byte[0];
            if (this.signed) {
                try {
                    Signature ecdsaSign = TOMUtil.getSigEngine();
                    ecdsaSign.initSign(proxy.getViewManager().getStaticConf().getPrivateKey());
                    ecdsaSign.update(request);
                    signature = ecdsaSign.sign();

                } catch (Exception e) {
                    System.out.println("wrong in signing messages... "+e);
                }
            }

            ByteBuffer buffer = ByteBuffer.allocate(request.length + signature.length + (Integer.BYTES * 2));

            buffer.putInt(request.length);
            buffer.put(request);
            buffer.putInt(signature.length);
            buffer.put(signature);
            request = buffer.array();
            return request;
        }

        public void run() {

            int timeoutvalue = proxy.getInvokeTimeout();

            System.out.println("Warm up...");

            int req = 0;

            for (int i = 0; i < numberOfOps / 2; i++, req++) {
//                if (verbose) System.out.print("Sending req " + req + "...");

                long last_send_instant = System.nanoTime();

                byte[] reply = null;
                byte[] request = getARequest(req);
                reply = proxy.invokeOrdered(request);
//                System.out.println("reply is " + new String(reply));
                long latency = System.nanoTime() - last_send_instant;

                try {
                    if (reply != null) latencies.put(id + "\t" + System.currentTimeMillis() + "\t" + latency + "\n");
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }

//                if (verbose) System.out.println(" sent!");

                if (verbose && (req % 100 == 0)) System.out.println(this.id + " // " + req + " operations sent!");

                try {

                    //sleeps interval ms before sending next request
                    if (interval > 0) {

                        Thread.sleep(interval);
                    }
                    else if (this.rampup > 0) {
                        Thread.sleep(this.rampup);
                    }
                    this.rampup -= 100;

                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            st = new Storage(numberOfOps / 2);

            System.out.println("Executing experiment for " + numberOfOps / 2 + " ops");

            for (int i = 0; i < numberOfOps / 2; i++, req++) {
                long last_send_instant = System.nanoTime();
                if (req%100==0)
                    if (verbose) System.out.print(this.id + " // Sending req " + req + "...");

                byte[] reply = null;
                byte[] request = getARequest(req);
                reply = proxy.invokeOrdered(request);

                long latency = System.nanoTime() - last_send_instant;

                try {
                    latencies.put(id + "\t" + System.currentTimeMillis() + "\t" + latency + "\n");
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                if (req%100==0)
                    if (verbose) System.out.println(this.id + " // sent!");
                st.store(latency);

                try {
                    //sleeps interval ms before sending next request
                    if (interval > 0) {
                        Thread.sleep(interval);
                    }
                    else if (this.rampup > 0) {
                        Thread.sleep(this.rampup);
                    }
                    this.rampup -= 100;

                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }


                if (verbose && (req % 1000 == 0)) System.out.println(this.id + " // " + req + " operations sent!");
            }

            if(id == initId) {
                System.out.println(this.id + " // Average time for " + numberOfOps / 2 + " executions (-10%) = " + st.getAverage(true) / 1000 + " us ");
                System.out.println(this.id + " // Standard desviation for " + numberOfOps / 2 + " executions (-10%) = " + st.getDP(true) / 1000 + " us ");
                System.out.println(this.id + " // Average time for " + numberOfOps / 2 + " executions (all samples) = " + st.getAverage(false) / 1000 + " us ");
                System.out.println(this.id + " // Standard desviation for " + numberOfOps / 2 + " executions (all samples) = " + st.getDP(false) / 1000 + " us ");
                System.out.println(this.id + " // Maximum time for " + numberOfOps / 2 + " executions (all samples) = " + st.getMax(false) / 1000 + " us ");
            }
            System.out.println("client " + this.id + " is done");
            proxy.close();
        }

        public Storage getLatencydata() {
            return st;
        }
    }
}
