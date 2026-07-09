
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class Server {

  // Main method

  public static void main(String[] args) throws IOException {

    // new instance of A selector monitoring the channels
    Selector selector = Selector.open();
    ArrayList<Integer> ports = new ArrayList<Integer>();
    ports.add(8080);
    ports.add(6060);
    ports.add(5000);
    /*
     * - chaneels must be configured no blocking in order to register to a selector
     * - also it return a selection key that acts as ref of this register chanell
     * and u can
     * monitor the state of the channel by using intrest set ( OP_Accept , OP_read
     * etc...) also they can be represanted as
     * integer value
     * - interest set is the events that the selector is intersted to listen in the
     * monitored channel
     * - the selection key contains :
     * interest set ( OP_Accept , OP_read etc...) get by : selectionKey.interstOps()
     * ready set (which operation is ready to be executed) get by :
     * selectionKey.readyOps()
     * the channel get by : selectionKey.channel();
     * the selector get by : selectionKey.selector();
     * attaching object : u can attach and retrieve objects from a selection key
     * 
     */
    ports.forEach(x -> {

      try {
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.bind(new InetSocketAddress(x));
        channel.register(selector, SelectionKey.OP_ACCEPT);

      } catch (Exception e) {
        System.err.println("Failed to bind port " + x + ": " + e.getMessage());
      }

    });
    long lastTimeoutCheck = System.currentTimeMillis();
    while (true) {
      selector.select(1000);
      long currentTime = System.currentTimeMillis();
      if (currentTime - lastTimeoutCheck > 1000) {

        for (SelectionKey key : selector.keys()) {
          if (key.isValid() && key.attachment() instanceof SocketConnection) {
            SocketConnection client = (SocketConnection) key.attachment();
            try {
              client.CheckTimeout(key);
            } catch (Exception e) {
              System.err.println("Timeout cleanup error: " + e.getMessage());
              key.cancel();
              try {
                key.channel().close();
              } catch (IOException ignored) {
              }
            }
          }
        }
        lastTimeoutCheck = currentTime; 

      }

      Set<SelectionKey> select_keys = selector.selectedKeys();
      Iterator<SelectionKey> keyIterator = select_keys.iterator();

      while (keyIterator.hasNext()) {
        SelectionKey key = keyIterator.next();
        // removing the key so the next time it wont be processed again
        keyIterator.remove();
        if (!key.isValid()) {
          continue;
        }

        // if the key is acceptable meaning it the server socket channel respo
        // to accept the connection in order to make a new socket channel for the client
        // also we track a connectionContext to track the current prorgess and state
        try {
          if (key.isAcceptable()) {
            ServerSocketChannel server = (ServerSocketChannel) (key.channel());

            SocketChannel client = server.accept();

            if (client == null) {
              continue;
            }
            SocketConnection connectionContext = new SocketConnection(client);
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ, connectionContext);

          }
          if (key.isReadable()) {
            // we retrieve the attachmenent to get Metadata about the channel also to track
            // our progress
            SocketConnection socketConnection = (SocketConnection) key.attachment();
            // socketConnection.ReadingRequest(key);
            socketConnection.HandlePhase(key);

            // key.interestOps(SelectionKey.OP_WRITE);
          }
          if (key.isWritable()) {
            SocketConnection socketconnection = (SocketConnection) key.attachment();
            socketconnection.HandlePhase(key);

          }
        } catch (Exception e) {
          System.err.println("Connection error: " + e.getMessage());
          key.cancel();
          try {
            if (key.channel() != null) {
              key.channel().close();
            }
          } catch (IOException ignored) {
          }
        }

      }

    }

  }

}