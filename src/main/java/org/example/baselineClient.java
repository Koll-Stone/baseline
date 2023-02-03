package org.example;

import bftsmart.tom.ServiceProxy;
import bftsmart.tom.util.Storage;
import bftsmart.tom.util.TOMUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.*;

import static org.example.baselineParameters.*;

public class baselineClient {

    public static int initId = 0;
    static LinkedBlockingQueue<String> latencies;
    static Thread writerThread;

    /*public static String privKey =  "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgXa3mln4anewXtqrM" +
                                    "hMw6mfZhslkRa/j9P790ToKjlsihRANCAARnxLhXvU4EmnIwhVl3Bh0VcByQi2um" +
                                    "9KsJ/QdCDjRZb1dKg447voj5SZ8SSZOUglc/v8DJFFJFTfygjwi+27gz";

    public static String pubKey =   "MIICNjCCAd2gAwIBAgIRAMnf9/dmV9RvCCVw9pZQUfUwCgYIKoZIzj0EAwIwgYEx" +
                                    "CzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1TYW4g" +
                                    "RnJhbmNpc2NvMRkwFwYDVQQKExBvcmcxLmV4YW1wbGUuY29tMQwwCgYDVQQLEwND" +
                                    "T1AxHDAaBgNVBAMTE2NhLm9yZzEuZXhhbXBsZS5jb20wHhcNMTcxMTEyMTM0MTEx" +
                                    "WhcNMjcxMTEwMTM0MTExWjBpMQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZv" +
                                    "cm5pYTEWMBQGA1UEBxMNU2FuIEZyYW5jaXNjbzEMMAoGA1UECxMDQ09QMR8wHQYD" +
                                    "VQQDExZwZWVyMC5vcmcxLmV4YW1wbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0D" +
                                    "AQcDQgAEZ8S4V71OBJpyMIVZdwYdFXAckItrpvSrCf0HQg40WW9XSoOOO76I+Umf" +
                                    "EkmTlIJXP7/AyRRSRU38oI8Ivtu4M6NNMEswDgYDVR0PAQH/BAQDAgeAMAwGA1Ud" +
                                    "EwEB/wQCMAAwKwYDVR0jBCQwIoAginORIhnPEFZUhXm6eWBkm7K7Zc8R4/z7LW4H" +
                                    "ossDlCswCgYIKoZIzj0EAwIDRwAwRAIgVikIUZzgfuFsGLQHWJUVJCU7pDaETkaz" +
                                    "PzFgsCiLxUACICgzJYlW7nvZxP7b6tbeu3t8mrhMXQs956mD4+BoKuNI";*/

    public static String privKey = "MD4CAQAwEAYHKoZIzj0CAQYFK4EEAAoEJzAlAgEBBCBnhIob4JXH+WpaNiL72BlbtUMAIBQoM852d+tKFBb7fg==";
    public static String pubKey = "MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEavNEKGRcmB7u49alxowlwCi1s24ANOpOQ9UiFBxgqnO/RfOl3BJm0qE2IJgCnvL7XUetwj5C/8MnMWi9ux2aeQ==";


    @SuppressWarnings("static-access")
    public static void main(String[] args) throws IOException {
//        if (args.length < 8) {
//            System.out.println("Usage: ... ThroughputLatencyClient <initial client id> <number of clients> <number of operations> <request size> <interval (ms)> <read only?> <verbose?> <nosig | default | ecdsa>");
//            System.exit(-1);
//        }

        if (args.length < 5) {
            System.out.println("Usage: ... baselineClient <initial client id> <number of clients> <number of operations> <interval (ms)> <verbose?>");
            System.exit(-1);
        }

        initId = Integer.parseInt(args[0]);
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

        int numThreads = Integer.parseInt(args[1]);

        int numberOfOps = Integer.parseInt(args[2]);
//        int requestSize = Integer.parseInt(args[3]);
        int interval = Integer.parseInt(args[3]);
//        boolean readOnly = Boolean.parseBoolean(args[5]);
        boolean verbose = Boolean.parseBoolean(args[4]);
//        String sign = args[7];
        String sign = "nosig";
        int s = 0;
        if (!sign.equalsIgnoreCase("nosig")) s++;
        if (sign.equalsIgnoreCase("ecdsa")) s++;

        if (s == 2 && Security.getProvider("SunEC") == null) {

            System.out.println("Option 'ecdsa' requires SunEC provider to be available.");
            System.exit(0);
        }

        Client[] clients = new Client[numThreads];

        for(int i=0; i<numThreads; i++) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {

                ex.printStackTrace();
            }

            System.out.println("Launching client " + (initId+i));
            clients[i] = new Client(initId+i,numberOfOps, interval, verbose, s);
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

        exec.shutdown();

        System.out.println("All clients done.");
    }

    static class Client extends Thread {

        int id;
        int numberOfOps;
        int interval;
        boolean verbose;
        ServiceProxy proxy;
        int rampup = 1000;

        public Client(int id, int numberOfOps, int interval, boolean verbose, int sign) {
            super("Client "+id);

            this.id = id;
            this.numberOfOps = numberOfOps;

            this.interval = interval;

            this.verbose = verbose;
            this.proxy = new ServiceProxy(id);
        }

        public byte[] getARequest(int req) {

            Random ran = new Random(System.nanoTime() + this.id);
//            byte[] signature = new byte[0];
            int userid = ran.nextInt(USERNUM);
            int resourceid = ran.nextInt(RESOURCENUM);
            int amount = ran.nextInt(10);
            int totalamount = ran.nextInt(80);
            String kMarketRequest = createKMarketRequest("user"+userid, "resource"+resourceid,
                    amount, totalamount);

//            System.out.println("client " + this.id + " request " + req + " is " + kMarketRequest.hashCode() +"\nuserid=" + userid + ", resource=" + resourceid + ", amount=" + amount +
//                    ", totalamount=" + totalamount);
            byte[] request = kMarketRequest.getBytes();
            ByteBuffer buffer = ByteBuffer.allocate(request.length + (Integer.BYTES * 1));
            buffer.putInt(request.length);
            buffer.put(request);
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
                System.out.println("reply is " + new String(reply));
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

            Storage st = new Storage(numberOfOps / 2);

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
    }
}
