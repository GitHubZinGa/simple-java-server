import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Server implements Runnable {
    private static final Log LOG = LogFactory.getLog(Server.class.getName());

    protected int serverPort;
    protected ServerSocket serverSocket;
    protected boolean isStopped;
    ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 10, SimpleSample.RUN_TIME, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

    public Server(int port) {
        this.serverPort = port;
    }

    public void run() {
        try {
            openServerSocket();
        while (!isStopped()) {
            Socket clientSocket;
            try {
                clientSocket = this.serverSocket.accept();
            } catch (IOException e) {
                if (isStopped()) {
                    LOG.info("Server Stopped after an Error.");
                    break;
                }
                throw new RuntimeException("Error accepting client connection", e);
            }
            this.executor.execute(new ConnectionHandler(clientSocket));
        }
        this.executor.shutdownNow();
        } catch (IOException e) {
            LOG.error(e);
        }
        LOG.info("Server Stopped.");
    }

    public synchronized boolean isStopped() {
        return this.isStopped;
    }

    public synchronized void stop() throws IOException {
        this.isStopped = true;
        this.serverSocket.close();
    }

    public synchronized void openServerSocket() throws IOException {
        this.serverSocket = new ServerSocket(this.serverPort);
    }
}