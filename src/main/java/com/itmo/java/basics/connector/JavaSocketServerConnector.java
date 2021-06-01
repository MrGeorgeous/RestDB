package com.itmo.java.basics.connector;

import com.itmo.java.basics.DatabaseServer;
import com.itmo.java.basics.config.DatabaseConfig;
import com.itmo.java.basics.config.ServerConfig;
import com.itmo.java.basics.console.ExecutionEnvironment;
import com.itmo.java.basics.console.impl.ExecutionEnvironmentImpl;
import com.itmo.java.basics.initialization.impl.DatabaseInitializer;
import com.itmo.java.basics.initialization.impl.DatabaseServerInitializer;
import com.itmo.java.basics.initialization.impl.SegmentInitializer;
import com.itmo.java.basics.initialization.impl.TableInitializer;
import com.itmo.java.basics.resp.CommandReader;
import com.itmo.java.protocol.RespReader;
import com.itmo.java.protocol.RespWriter;
import com.itmo.java.protocol.model.RespArray;
import com.itmo.java.protocol.model.RespObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Класс, который предоставляет доступ к серверу через сокеты
 */
public class JavaSocketServerConnector implements Closeable {

    /**
     * Экзекьютор для выполнения ClientTask
     */
    private final ExecutorService connectionAcceptorExecutor = Executors.newSingleThreadExecutor();

    private final ServerSocket serverSocket;
    private final DatabaseServer databaseServer;

    /**
     * Стартует сервер. По аналогии с сокетом открывает коннекшн в конструкторе.
     */
    public JavaSocketServerConnector(DatabaseServer databaseServer, ServerConfig config) throws IOException {
        try {
            this.databaseServer = databaseServer;
            this.serverSocket = new ServerSocket(config.getPort());
        } catch (Exception e) {
            throw new IOException("ServerSocket could not be opened.", e);
        }
    }
 
     /**
     * Начинает слушать заданный порт, начинает аксептить клиентские сокеты. На каждый из них начинает клиентскую таску
     */
    public void start() {

        try {
            while (true) {
                connectionAcceptorExecutor.submit(new ClientTask(serverSocket.accept(), databaseServer));
                //connectionAcceptorExecutor.execute(new ClientTask(serverSocket.accept(), databaseServer));
            }
        } catch (IOException e) {
            connectionAcceptorExecutor.shutdown();
        }

//        connectionAcceptorExecutor.submit(() -> {
//            // todo implement
//        });

    }

    /**
     * Закрывает все, что нужно ¯\_(ツ)_/¯
     */
    @Override
    public void close() {
        connectionAcceptorExecutor.shutdownNow();
        try {
            System.out.println("Stopping socket connector");
            serverSocket.close();
        } catch (Exception e) {

        }
    }


    public static void main(String[] args) throws Exception {

        DatabaseConfig dbConfig = new DatabaseConfig();
        ExecutionEnvironment env = new ExecutionEnvironmentImpl(dbConfig);
        DatabaseServerInitializer initializer = new DatabaseServerInitializer(
                                                        new DatabaseInitializer(
                                                                new TableInitializer(
                                                                        new SegmentInitializer())));
        DatabaseServer dbServer = DatabaseServer.initialize(env, initializer);
        JavaSocketServerConnector server = new JavaSocketServerConnector(dbServer, new ServerConfig("localhost", 8080));
        server.start();

    }

    /**
     * Runnable, описывающий исполнение клиентской команды.
     */
    static class ClientTask implements Runnable, Closeable {

        private Socket clientSocket = null;
        private DatabaseServer server = null;

        /**
         * @param client клиентский сокет
         * @param server сервер, на котором исполняется задача
         */
        public ClientTask(Socket client, DatabaseServer server) {
            this.clientSocket = client;
            this.server = server;
        }

        /**
         * Исполняет задачи из одного клиентского сокета, пока клиент не отсоединился или текущий поток не был прерван (interrupted).
         * Для кажной из задач:
         * 1. Читает из сокета команду с помощью {@link CommandReader}
         * 2. Исполняет ее на сервере
         * 3. Записывает результат в сокет с помощью {@link RespWriter}
         */
        @Override
        public void run() {
            try {
                RespReader rr = new RespReader(clientSocket.getInputStream());
                RespWriter rw = new RespWriter(clientSocket.getOutputStream());

                while (clientSocket.isConnected()) {
                    if (rr.hasArray()) {
                        RespArray command = rr.readArray();
                        RespObject result = server.executeNextCommand(command).join().serialize();
                        result.write(System.out);
                        rw.write(result);
                    }
                }
                rr.close();
                rw.close();
                //close();
            } catch (Exception ignored) {
                ignored.printStackTrace();
                System.out.println("Failed to process request.");
            }
        }

        /**
         * Закрывает клиентский сокет
         */
        @Override
        public void close() {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Client socket was not closed.");
            }
        }

    }
}