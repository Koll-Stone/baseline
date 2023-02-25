package org.example;

import bftsmart.tom.ServiceProxy;
import bftsmart.tom.util.Storage;
import bftsmart.tom.util.TOMUtil;

import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.Signature;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.example.baselineParameters.*;





public class zkbaclient {

    public static int initId = 0;
    static LinkedBlockingQueue<String> latencies;
    static Thread writerThread;



    @SuppressWarnings("static-access")
    public static void main(String[] args) throws IOException {


        if (args.length < 6) {
            System.out.println("Usage: ... zkbaClient <initial client id> <number of clients> <number of operations> " +
                    "<interval (ms)> <verbose?> <commnad=mint/destroy/delegate/use> <signed>");
            System.exit(-1);
        }

        initId = Integer.parseInt(args[0]);
        latencies = new LinkedBlockingQueue<>();

        int numThreads = Integer.parseInt(args[1]);
        int numberOfOps = Integer.parseInt(args[2]);
        int interval = Integer.parseInt(args[3]);
        boolean verbose = Boolean.parseBoolean(args[4]);
        int command = Integer.parseInt(args[5]);

        Client[] clients = new Client[numThreads];
        for(int i=0; i<numThreads; i++) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {

                ex.printStackTrace();
            }

            System.out.println("Launching client " + (initId+i));
            clients[i] = new Client(initId+i,numberOfOps, interval, verbose, command);
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


        System.out.println("All clients done. average latency is "+averagelatency/1000 + " us");

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


        private int delegateproofsize;
        private int useproofsize;

        int command;
        int id;
        int numberOfOps;
        int interval;
        boolean verbose;
        ServiceProxy proxy;
        int rampup = 1000;

        Storage latencydata;

        ReentrantLock cansend = new ReentrantLock();

        public Client(int id, int numberOfOps, int interval, boolean verbose, int command) {
            super("Client "+id);

            this.command = command;
            this.delegateproofsize = 128;
            this.useproofsize = 128;
            this.id = id;
            this.numberOfOps = numberOfOps;

            this.interval = interval;
            this.verbose = verbose;
            this.proxy = new ServiceProxy(id);
        }

        private byte[] getARequest(int reqtype) {
            switch (reqtype) {
                case 0:
                    return createtxmint();
                case 1:
                    return createdestroy();
                case 2:
                    return createdelegate();
                case 3:
                    return createuse();
            }
            return new byte[0];
        }

        private byte[] createtxmint() {
            int reqtype = 0;
            byte[] cm = new byte[32];
            for (int i=0; i<32; i++)
                cm[i] = (byte) i;
            byte[] t = cm;
            byte[] tm = cm;

            byte[] content = new byte[96];
            System.arraycopy(cm, 0, content, 0, 32);
            System.arraycopy(t,0, content, 32, 32);
            System.arraycopy(tm, 0, content, 64, 32);


            byte[] signature = new byte[0];
            try {
                Signature ecdsaSign = TOMUtil.getSigEngine();
                ecdsaSign.initSign(proxy.getViewManager().getStaticConf().getPrivateKey());
                ecdsaSign.update(content);
                signature = ecdsaSign.sign();
//                System.out.println("signature length is "+signature.length);
            } catch (Exception e) {
                System.out.println("wrong in signing messages... "+e);
            }


            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES*2 + 96+signature.length);

            buffer.putInt(reqtype);
            buffer.put(cm);
            buffer.put(t);
            buffer.put(tm);
            buffer.putInt(signature.length);
            buffer.put(signature);

            return buffer.array();

        }

        private byte[] createdestroy() {
            int reqtype = 1;
            byte[] cm = new byte[32];
            for (int i=0; i<32; i++)
                cm[i] = (byte) i;


            byte[] content = new byte[32];
            System.arraycopy(cm, 0, content, 0, 32);



            byte[] signature = new byte[0];
            try {
                Signature ecdsaSign = TOMUtil.getSigEngine();
                ecdsaSign.initSign(proxy.getViewManager().getStaticConf().getPrivateKey());
                ecdsaSign.update(content);
                signature = ecdsaSign.sign();
            } catch (Exception e) {
                System.out.println("wrong in signing messages... "+e);
            }


            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES*2 + 32+signature.length);

            buffer.putInt(reqtype);
            buffer.put(cm);
            buffer.putInt(signature.length);
            buffer.put(signature);

            return buffer.array();
        }

        private byte[] createdelegate() {
            int reqtype = 2;
            byte[] rt = new byte[32];
            for (int i=0; i<32; i++)
                rt[i] = (byte) i;
            byte[] sn = rt;
            byte[] tm = rt;
            byte[] pidele = new byte[delegateproofsize];
            for (int i=0; i<pidele.length; i++)
                pidele[i] = (byte) i;



            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES*2 + 96+ pidele.length);

            buffer.putInt(reqtype);
            buffer.put(rt);
            buffer.put(sn);
            buffer.put(tm);
            buffer.putInt(pidele.length);
            buffer.put(pidele);

            return buffer.array();
        }

        private byte[] createuse() {
            int reqtype = 3;
            byte[] rt = new byte[32];
            for (int i=0; i<32; i++)
                rt[i] = (byte) i;
            byte[] sn = rt;
            byte[] cm = rt;
            byte[] wm = rt;
            byte[] piuse = new byte[useproofsize];
            for (int i=0; i<piuse.length; i++)
                piuse[i] = (byte) i;



            ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES*2 + 128 + piuse.length);

            buffer.putInt(reqtype);
            buffer.put(rt);
            buffer.put(sn);
            buffer.put(cm);
            buffer.put(wm);
            buffer.putInt(useproofsize);
            buffer.put(piuse);

            return buffer.array();
        }

        public void sendatxdeleasrequired() {
            byte[] request = getARequest(2);
            cansend.lock();
            proxy.invokeOrdered(request);
            cansend.unlock();
        }

        public void sendatxuseasrequired() {
            byte[] request = getARequest(3);
            cansend.lock();
            proxy.invokeOrdered(request);
            cansend.unlock();
        }

        public void sendatxcheckasrequired() {
            // invoke unordered
            byte[] request = getARequest(2);
            cansend.lock();
            proxy.invokeUnordered(request);
            cansend.unlock();
        }

        public void run() {
            int operationundertest = 0;
            int sigratio = 30; // 25%
            int zkpratio = 20; //25% make sure sigratio+zkpratio==50
            List<Integer> commandwillbeused = new ArrayList<Integer>();
            for (int i=0; i<sigratio; i++)
                commandwillbeused.add(0);
            for (int i=sigratio; i<sigratio*2; i++)
                commandwillbeused.add(1);
            for (int i=sigratio*2; i<sigratio*2+zkpratio; i++)
                commandwillbeused.add(2);
            for (int i=sigratio*2+zkpratio; i<sigratio*2+zkpratio*2; i++)
                commandwillbeused.add(3);
//            System.out.println("command will be used length: "+commandwillbeused.size());
//
//            for (int i=0; i<100; i++) {
//                if (i<sigratio) {
//                    commandwillbeused.add(0);
//                } else if (i>=sigratio && i<sigratio*2) {
//                    commandwillbeused.add(1);
//                } else if (i>=sigratio*2 && i<sigratio*2+zkpratio) {
//                    commandwillbeused.add(2);
//                } else {
//                    commandwillbeused.add(3);
//                }
//            }
            Collections.shuffle(commandwillbeused);
            int[] allcommands = new int[numberOfOps];
            for (int i=0; i<numberOfOps; i++) {
                allcommands[i] = commandwillbeused.get((i%100));
            }


            int timeoutvalue = proxy.getInvokeTimeout();
            if (id==1) {
                numberOfOps = 0;
                new Thread(() -> {
                    new sslserver(this);
                }).start();
            }

            if (id==2) {
                numberOfOps = 0;
                new Thread(() -> {
                    new sslclient(this);
                }).start();
            }


            System.out.println("client " + id +" Warm up...");

            int req = 0;

            for (int i = 0; i < numberOfOps / 2; i++, req++) {
                long last_send_instant = System.nanoTime();

                byte[] reply = null;
                byte[] request = getARequest(allcommands[req]);

                cansend.lock();
                reply = proxy.invokeOrdered(request);
                cansend.unlock();

//                System.out.println(new String(reply));
                long latency = System.nanoTime() - last_send_instant;

                try {
                    if (reply != null) latencies.put(id + "\t" + System.currentTimeMillis() + "\t" + latency + "\n");
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }

                if (verbose && (req % 100 == 0)) System.out.println(this.id + " // " + req + " operations sent!");

            }

            latencydata = new Storage(numberOfOps / 2);

            System.out.println("Executing experiment for " + numberOfOps / 2 + " ops");

            for (int i = 0; i < numberOfOps / 2; i++, req++) {
                long last_send_instant = System.nanoTime();
                if (req%100==0)
                    if (verbose) System.out.print(this.id + " // Sending req " + req + "...");

                byte[] reply = null;
                byte[] request = getARequest(allcommands[req]);

                cansend.lock();
                reply = proxy.invokeOrdered(request);
                cansend.unlock();

                long latency = System.nanoTime() - last_send_instant;

                try {
                    latencies.put(id + "\t" + System.currentTimeMillis() + "\t" + latency + "\n");
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                if (req%100==0)
                    if (verbose) System.out.println(this.id + " // sent!");

                if (allcommands[req]==operationundertest)
                    latencydata.store(latency);

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
                System.out.println(this.id + " // Average time for " + numberOfOps / 2 + " executions (-10%) = " + latencydata.getAverage(true) / 1000 + " us ");
                System.out.println(this.id + " // Standard desviation for " + numberOfOps / 2 + " executions (-10%) = " + latencydata.getDP(true) / 1000 + " us ");
                System.out.println(this.id + " // Average time for " + numberOfOps / 2 + " executions (all samples) = " + latencydata.getAverage(false) / 1000 + " us ");
                System.out.println(this.id + " // Standard desviation for " + numberOfOps / 2 + " executions (all samples) = " + latencydata.getDP(false) / 1000 + " us ");
                System.out.println(this.id + " // Maximum time for " + numberOfOps / 2 + " executions (all samples) = " + latencydata.getMax(false) / 1000 + " us ");
            }
            System.out.println("client " + this.id + " is done");
            if (id!=1 && id!=2)
                proxy.close();
        }

        public Storage getLatencydata() {
            return latencydata;
        }
    }
}

