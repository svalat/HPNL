package com.intel.hpnl.core.rdm4;

import com.intel.hpnl.api.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;


public class ServerTest {

  private HpnlService service;
  private String hostname;
  private int msgSize;

  private BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

  public ServerTest(int numThreads, int numBuffer, int bufferSize, int msgSize, String hostname) {
    service = HpnlFactory.getService(numThreads, numBuffer, numBuffer, bufferSize, true);
    this.hostname = hostname;
    this.msgSize = msgSize;
  }

  public void start()throws Exception{
    EventTask task = service.getCqTask(0);
    EventTask task2 = service.getCqTask(1);
    Thread th = new Thread(()->{
      System.out.println("starting");
      while(!Thread.currentThread().isInterrupted()) {
        task.run();
//        task2.run();
      }
      System.out.println("ending");
    });

    th.setUncaughtExceptionHandler((Thread t, Throwable e) -> {
      e.printStackTrace();
    });

    th.start();

    Thread th2 = new Thread(()->{
      System.out.println("starting child connection");
      while(!Thread.currentThread().isInterrupted()) {
//        task.run();
        task2.run();
      }
      System.out.println("ending child connection");
    });

    th2.setUncaughtExceptionHandler((Thread t, Throwable e) -> {
      e.printStackTrace();
    });

    th2.start();
    service.bind(hostname, 12345, 0, new Handler() {
      @Override
      public int handle(Connection connection, int bufferId, int bufferSize) {
        System.out.println("bound");
        return 0;
      }
      @Override
      public int handle(Connection connection, HpnlBuffer hpnlBuffer) {
        System.out.println("bound");
        return 0;
      }
    }, new RecvCallback(true, 5, msgSize, -1));
    System.out.println("waiting");
    th.join();
  }

  public static void main(String... args)throws Exception {
    int numThreads = Integer.valueOf(System.getProperty("numThreads", "1"));
    int numBuffer = Integer.valueOf(System.getProperty("numBuffer", "200"));;
    int bufferSize = Integer.valueOf(System.getProperty("bufferSize", "32768"));
    int msgSize = Integer.valueOf(System.getProperty("msgSize", "4096"));
    String hostname = System.getProperty("hostname", "10.100.0.35");

    ServerTest test = new ServerTest(numThreads, numBuffer, bufferSize, msgSize, hostname);
    test.start();
  }
}