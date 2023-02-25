package org.example;

import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;


public class sslclient extends Thread{

    private static String CLIENT_KEY_STORE = "./zkpexperiment/keys/client_ks";
    private static String CLIENT_KEY_STORE_PASSWORD = "456456";
    private final org.slf4j.Logger logger = LoggerFactory.getLogger(this.getClass());

    private Socket socket;
    private DataOutputStream socketOutStream;
    private DataInputStream socketInStream;

    boolean dowork;
    String functiontested;
    private ReentrantLock cansendnextlock = new ReentrantLock();
    private Condition cansendnext = cansendnextlock.newCondition();

    private int currentReplyInd = 0;
    private int currentWaitInd = 0;
    private Set<Integer> responded;
    private ReadWriteLock respondlock;

    zkbaclient.Client zkbablockchainclient;
    public sslclient(zkbaclient.Client zc) {
        // Set the key store to use for validating the server cert.
        System.setProperty("javax.net.ssl.trustStore", CLIENT_KEY_STORE);
        System.setProperty("javax.net.info", "ssl,handshake");
        logger.info("sslclient launching...");

        zkbablockchainclient = zc;
        responded = new HashSet<Integer>();
        respondlock = new ReentrantReadWriteLock();
        try {
            this.dowork = true;
            this.socket = clientWithCert();
            this.socketOutStream = new DataOutputStream(socket.getOutputStream());
            this.socketInStream = new DataInputStream(socket.getInputStream());
//            PrintWriter writer = new PrintWriter(s.getOutputStream());
//            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
//            writer.println(new String(input));
//            writer.flush();
//            logger.info(reader.readLine());
        } catch (Exception e) {

        }

        start();
//        testcode();
        dothework();
    }

    private void dothework() {
        functiontested = "use";

        // simulate the test!
        try {
            Thread.sleep(1000*3);
        } catch (Exception e) {

        }

        List<Long> latencyres = new ArrayList<Long>();
        int ind=1;
        for (; ind<30; ind++) {

            try {
                Thread.sleep(300);
            } catch (Exception e) {
                logger.info("sleep error " + e);
            }
            logger.info("end-to-end {} starts", this.functiontested);
            int cost = 0;
            if (functiontested.equals("transfer"))
                cost = simulateTransfer(ind);
            else if (functiontested.equals("use"))
                cost = simulateUse(ind);
            logger.info("time cost of {}th end-to-end {} is {} us\n", this.functiontested, ind, cost);
            latencyres.add((long) cost);
        }

        long[] finaldata = new long[latencyres.size()];
        for(int i=0; i<latencyres.size(); i++) {
            finaldata[i] = latencyres.get(i);
        }
        double averagelatency = computeAverage(finaldata, true);


        System.out.println("end-to-end test done. average latency is "+averagelatency + " us");

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

//    private void testcode() {
//        start();
//
//        int i=1;
//        currentReplyInd = 0;
//
//
//        while(i<10) {
//            if (i>1 && currentWaitInd>currentReplyInd) {
//                cansendlock.lock();
//                logger.info("waiting for reply to "+(i-1)+"th query!");
//                cansend.awaitUninterruptibly();
//                cansendlock.unlock();
//            }
//            if (i%2==0)
//                sendBytes(getRandomValues());
//            else
//                sendBytes(getRequestFile());
//            currentWaitInd = i;
//            logger.info("sent the "+i+"th request!");
//            i++;
//        }
//
//        closeSocket();
//    }

    private int simulateTransfer(int ind) {
        long start = System.nanoTime();
        // send zerotoken requester and randomness
        sendBytes(getRandomValues(ind));

        // ensure the response is received before exit
        while(true) {
            respondlock.readLock().lock();
            boolean x = responded.contains(ind);
            respondlock.readLock().unlock();

            if (!x) {
                cansendnextlock.lock();
                cansendnext.awaitUninterruptibly();
                cansendnextlock.unlock();
            } else {
                break;
            }
        }


        long duration = System.nanoTime() - start;
        return ((int) duration/1000);
    }

    private int simulateUse(int ind) {
        long start = System.nanoTime();

        // invoke blockchain ordered
        if (zkbablockchainclient!=null) {
            zkbablockchainclient.sendatxuseasrequired();
            logger.info("send a tx_use as required by user who wants to access resoruce");
        } else {
            logger.info("wrong!");
            System.exit(0);
        }

        // send to resource owner
        sendBytes(getRequestFile(ind));

        // ensure the response is received before exit
        while(true) {
            respondlock.readLock().lock();
            boolean x = responded.contains(ind);
            respondlock.readLock().unlock();

            if (!x) {
                cansendnextlock.lock();
                cansendnext.awaitUninterruptibly();
                cansendnextlock.unlock();
            } else {
                break;
            }
        }

        long duration = System.nanoTime() - start;
        return ((int) duration/1000);
    }

    private void closeSocket() {
        try {
            dowork = false;
            socket.close();
        } catch (Exception e) {

        }
        logger.info("socket ended");
    }


    private void cansendnow() {
        cansendnextlock.lock();
        cansendnext.signal();
        cansendnextlock.unlock();
    }

//    public static void main(String[] args) throws Exception {
//        sslclient sclient = new sslclient();
//    }

//    private void sendReq() {
//        int inputleng = 1000*1024;
//        byte[] input = new byte[inputleng];
//        for (int i=0; i<inputleng; i++) {
//            input[i] = (byte) (i%1024);
//        }
//        for (int i=0; i<1; i++) {
//            sendBytes(input);
//            try {
//                Thread.sleep(100);
//            } catch (Exception e) {
//
//            }
//        }
//
//    }


    private Socket clientWithCert() throws Exception {
//        SocketFactory sf = SSLSocketFactory.getDefault();
//        Socket s = sf.createSocket("localhost", 8443);

        SSLContext context = SSLContext.getInstance("TLS");
        KeyStore ks = KeyStore.getInstance("jceks");

        ks.load(new FileInputStream(CLIENT_KEY_STORE), null);
        KeyManagerFactory kf = KeyManagerFactory.getInstance("SunX509");
        kf.init(ks, CLIENT_KEY_STORE_PASSWORD.toCharArray());
        context.init(kf.getKeyManagers(), null, null);

        SocketFactory factory = context.getSocketFactory();
        Socket s = factory.createSocket("localhost", 8443);
        s.setKeepAlive(true);
        s.setTcpNoDelay(true);

        return s;
    }

    private byte[] getRandomValues(int ind) {
        byte[] apk = new byte[256];
        for (int i=0; i<256; i++)
            apk[i] = (byte) i;
        ByteBuffer buffer = ByteBuffer.allocate(256+Integer.BYTES*2);
        buffer.putInt(10018); // 1 stands for query the permission and provide a_pk
        buffer.putInt(ind);
        buffer.put(apk);
        return buffer.array();
    }

    private byte[] getRequestFile(int ind) {
        byte[] cm = new byte[256];
        for (int i=0; i<256; i++)
            cm[i] = (byte) i;
        byte[] word = cm;

        ByteBuffer buffer = ByteBuffer.allocate(256*2+Integer.BYTES*2);
        buffer.putInt(30018); // 1 stands for query the permission and provide a_pk
        buffer.putInt(ind);
        buffer.put(cm);
        buffer.put(word);
        return buffer.array();
    }



    private void sendBytes(byte[] messageData) {
        try {
            byte[] data = new byte[5 + messageData.length];
            int value = messageData.length;
            System.arraycopy(new byte[]{(byte)(value >>> 24), (byte)(value >>> 16), (byte)(value >>> 8), (byte)value}, 0, data, 0, 4);
            System.arraycopy(messageData, 0, data, 4, messageData.length);
            System.arraycopy(new byte[]{0}, 0, data, 4 + messageData.length, 1);
            socketOutStream.write(data);
//            logger.info("sent message, length: "+data.length);
        } catch (IOException var5) {
        }
    }

    private void processRequest(byte[] command) {
        ByteBuffer buffer = ByteBuffer.wrap(command);
        int reqtype = buffer.getInt();
        int order = buffer.getInt();
        logger.info("get a reply with "+ reqtype + " for "+order);
        if (reqtype==20018) {
            logger.info("get the secrets, I can use the zerotoken now!");
        } else if (reqtype==40018) {
            logger.info("get the wanted file");
        }
        respondlock.writeLock().lock();
        responded.add(order);
        respondlock.writeLock().unlock();

        cansendnow();
    }

    public void run() {
        while(dowork) {
            try {
                int dataLength = socketInStream.readInt();
                byte[] data = new byte[dataLength];
                int read = 0;
                do {
                    read += socketInStream.read(data, read, dataLength - read);
                } while(read < dataLength);
                byte hasMAC = socketInStream.readByte();
                processRequest(data);
//                logger.info("received data length: " + data.length);
            } catch (IOException e) {

            }
        }
    }



}