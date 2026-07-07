
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class Server {

  // Main method
  public static void main(String[] args) throws IOException {

    // new instance of A selector monitoring the channels
    Selector selector = Selector.open();
    ServerSocketChannel channel = ServerSocketChannel.open();
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
    channel.configureBlocking(false);
    channel.bind(new InetSocketAddress(8080));
    channel.register(selector, SelectionKey.OP_ACCEPT);
    // ByteBuffer buffer = ByteBuffer.allocate(8000);
    while (true) {

      selector.select();
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
        if (key.isAcceptable()) {
          ServerSocketChannel server = (ServerSocketChannel) (key.channel());

          SocketChannel client = server.accept();
          SocketConnection connectionContext = new SocketConnection(client);
          connectionContext.socket = client;
          client.configureBlocking(false);
          client.register(selector, SelectionKey.OP_READ, connectionContext);

        }
        if (key.isReadable()) {
          // we retrieve the attachmenent to get Metadata about the channel also to track
          // our progress
          SocketConnection socketConnection = (SocketConnection) key.attachment();
          socketConnection.ReadingRequest(key);
          // key.interestOps(SelectionKey.OP_WRITE);
        }

        if (key.isWritable()) {
          SocketConnection socketconnection = (SocketConnection) key.attachment();
          socketconnection.WritingResponse(key);

        }

      }
    }

  }

}