package com.sfdc.http.loadgen;

import com.sfdc.http.queue.ProducerConsumerQueue;
import com.sfdc.http.queue.ProducerConsumerQueueConfig;
import com.sfdc.http.smc.QueueingStreamingClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poc.SessionIdReader;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author psrinivasan
 *         Date: 9/20/12
 *         Time: 7:47 PM
 *         Sets up the producers, consumers, and pushes the requests into the system.
 *         the FSMs take over from that point.
 *         RequestGenerator needs to be hooked into main()
 */
public class RequestGenerator implements Runnable {
    private ProducerConsumerQueue pcQueue;   // todo: we need on dedicated queue for connects.
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestGenerator.class);
    private QueueingStreamingClientImpl httpClient;
    private ProducerConsumerQueueConfig config;
    private SessionIdReader sessionReader;
    private volatile boolean run;


    public RequestGenerator() {
        run = true;
        try {
            //todo: parameterize location of props file.
            config = new ProducerConsumerQueueConfig("src/main/resources/config.properties");
        } catch (IOException e) {
            LOGGER.error("FATAL:  Failed to load config.properties ... exiting.");
            e.printStackTrace();
            LOGGER.error("Bye.");
            System.exit(1);
        }
        try {
            pcQueue = new ProducerConsumerQueue(config);
        } catch (Exception e) {
            LOGGER.error("FATAL:  Failed to initialize producer/consumer subsystem ... exiting.");
            e.printStackTrace();
            LOGGER.error("Bye.");
            System.exit(1);
        }
        try {
            sessionReader = config.getSessionIdReader();
        } catch (FileNotFoundException e) {
            LOGGER.error("FATAL:  Failed to load session id file ... exiting.");
            e.printStackTrace();
            LOGGER.error("Bye.");
            System.exit(1);
        }
    }

    public void generateRequests() {
        //dont forget to start the producer/consumer/queue.
        new Thread(pcQueue).start();

        int numHandshakes = config.getNumHandshakes();
        for (int i = 0; i < numHandshakes; i++) {
            QueueingStreamingClientImpl httpClient = null;
            String sessionId = null;
            try {
                sessionId = sessionReader.getOneSessionId();
            } catch (IOException e) {
                LOGGER.warn("Error reading session id.  Skipping one client.");
                e.printStackTrace();
                continue;
            }
            httpClient = new QueueingStreamingClientImpl(
                    sessionId,
                    config.getInstance(),
                    pcQueue.getProducer(),
                    pcQueue.getProducer(),
                    config.getTopics());
            LOGGER.debug("Going to start client with session id: " + sessionId);
            httpClient.start();
            if (!run) {
                return;
            }
        }
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p/>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        generateRequests();
    }

    public void stop() {
        run = false;
        pcQueue.gracefulShutdown();
    }
}
