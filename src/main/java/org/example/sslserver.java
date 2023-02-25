package org.example;

import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;



public class sslserver extends Thread {

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(this.getClass());

    private static String SERVER_KEY_STORE = "./zkpexperiment/keys/server_ks";

    private static String SERVER_KEY_STORE_PASSWORD = "123123";

    zkbaclient.Client zkbablockchainclient;


    public sslserver(zkbaclient.Client zc) {
        logger.info("sslserver launching...");

        zkbablockchainclient = zc;
        System.setProperty("javax.net.ssl.trustStore", SERVER_KEY_STORE);
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            KeyStore ks = KeyStore.getInstance("jceks");
            ks.load(new FileInputStream(SERVER_KEY_STORE), null);
            KeyManagerFactory kf = KeyManagerFactory.getInstance("SunX509");
            kf.init(ks, SERVER_KEY_STORE_PASSWORD.toCharArray());
            context.init(kf.getKeyManagers(), null, null);

            ServerSocketFactory factory = context.getServerSocketFactory();
            ServerSocket _socket = factory.createServerSocket(8443);
            ((SSLServerSocket) _socket).setNeedClientAuth(true);

            while (true) {
                Socket newsocket = _socket.accept();
                new connectionMaintainerThread(newsocket).start();
            }
        } catch (Exception e) {

        }
    }


//    public static void main(String[] args) throws Exception {
//        new sslserver();
//    }

    public void linktoblockchainclient(zkbaclient.Client zkbac) {
        this.zkbablockchainclient = zkbac;
        logger.info("lined to blochain client");
    }

    class connectionMaintainerThread extends Thread {
        private Socket socket;
        DataInputStream socketInStream;
        DataOutputStream socketOutStream;
        boolean dowork;

        zkbaclient.Client zkbaclient;

        public connectionMaintainerThread(Socket socket) {
            this.socket = socket;
            try {
                this.socket.setKeepAlive(true);
                this.socket.setTcpNoDelay(true);
            } catch (Exception e) {

            }


            dowork = true;
        }

        public void run() {
            try {
                this.socketInStream = new DataInputStream(this.socket.getInputStream());
                this.socketOutStream = new DataOutputStream(this.socket.getOutputStream());
            } catch (Exception e) {

            }

            while(dowork && !this.socket.isClosed()) {
                try {

                    int dataLength = socketInStream.readInt();
                    byte[] data = new byte[dataLength];
                    int read = 0;
                    do {
                        read += socketInStream.read(data, read, dataLength - read);
                    } while(read < dataLength);
                    byte hasMAC = socketInStream.readByte();

                    logger.info("received data length: " + data.length);
                    processRequest(data);



                } catch (IOException e) {
                }
            }

            logger.info("connection maintainer thread exits");
        }

        private byte[] getSecrets(int order) {

            byte[] rho = new byte[256];
            for (int i=0; i<256; i++)
                rho[i] = (byte) i;
            byte[] r= rho;
            byte[] cm = rho;


            ByteBuffer buffer = ByteBuffer.allocate(256*3+Integer.BYTES*2);
            buffer.putInt(20018); // 20018 stands for query the permission and provide a_pk
            buffer.putInt(order);
            buffer.put(rho);
            buffer.put(r);
            buffer.put(cm);

            return buffer.array();
        }

        private byte[] gettheChosenFile(int order) {
            int fileleng = 500*1024;
            byte[] file = new byte[fileleng];
            for (int i=0; i<256; i++)
                file[i] = (byte) (i%1024);

            ByteBuffer buffer = ByteBuffer.allocate(fileleng+Integer.BYTES*2);
            buffer.putInt(40018); // 40018 stands for sending the file
            buffer.putInt(order);
            buffer.put(file);
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
                logger.info("sent message, length: "+data.length);
                return;
            } catch (IOException var5) {
            }
        }

        private void processRequest(byte[] command) {
            ByteBuffer buffer = ByteBuffer.wrap(command);
            int reqtype = buffer.getInt();
            int order = buffer.getInt();
            logger.info("req is "+reqtype);
            if (reqtype==10018) {
                // invoke tx_dele
//                logger.info("call my blockchain client to send tx_dele for me");
                if (zkbablockchainclient!=null) {
                    logger.info("send a tx_dele as required by user who wants to transfer token");
                    zkbablockchainclient.sendatxdeleasrequired();
                } else {
                    logger.info("wrong!");
                    System.exit(0);
                }
                byte[] reply = getSecrets(order);
                sendBytes(reply);
            } else if (reqtype==30018) {
                // invoke blockchain query (unordered)
                if (zkbablockchainclient!=null) {
                    logger.info("send a tx_check as required by user who wants to check word commitment");
                    zkbablockchainclient.sendatxcheckasrequired();
                } else {
                    logger.info("wrong!");
                    System.exit(0);
                }
                byte[] reply = gettheChosenFile(order);
                sendBytes(reply);
            }
        }

        private void closeSocket() {
            dowork = false;
            try {
                socket.close();
            } catch (Exception e) {

            }
        }

//        public void setZkbaclient(org.example.zkbaclient.Client zkbaclient) {
//            this.zkbaclient = zkbaclient;
//        }

    }



}