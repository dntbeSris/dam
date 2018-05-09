package org.dam.io.nio;

import org.dam.io.AbstractEndPoint;
import org.dam.io.ConnectedEndPoint;
import org.dam.io.connection.Connection;
import org.dam.io.nio.buffer.Buffer;

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * Created by geeche on 2018/2/3.
 */
public class SelectChannelEndPoint extends AbstractEndPoint implements ConnectedEndPoint {

    private SelectorManager manager;
    private SelectorManager.SelectorWorker worker;
    private SelectionKey key;
    private Connection connection;


    private static final int STATE_NEEDDISPATCH = -1;
    private static final int STATE_UNDISPATCH = 0;
    private static final int STATE_DISPATCHED = 1;

    private int state;

    private boolean open;

    private boolean readBloking;
    private boolean writeBloking;


    private int interestOps;

    private volatile boolean writeable;

    private boolean onIdle;

    private final Runnable handler = ()-> {
        try {
            handle();
        }catch (IOException e){

        }
    };

    public SelectChannelEndPoint(SocketChannel channel, SelectorManager.SelectorWorker worker, SelectionKey key) throws IOException{
        super(channel);
        manager = worker.getManager();
        this.worker = worker;
        this.key = key;
        state = STATE_UNDISPATCH;
        onIdle = false;
        open = true;
    }

    public SelectionKey getSelectionKey() {
        synchronized (this){
            return key;
        }
    }

    public SelectorManager getManager() {
        return manager;
    }


    @Override
    public void setConnection(Connection conn) {
        Connection old = connection;
        connection = conn;
        if(old != null && old != connection){
            manager.endPointUpgraded(this,old);
        }
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public boolean blockReadable(long millisecs) throws IOException{
        synchronized (this){
            if(isInputShutdown()){
                throw new EOFException();
            }
            //long now = worker.getNow();
            //long end =  now + millisecs;
            readBloking = true;;
            while (readBloking && !isInputShutdown()){
                updateKey();
                try {
                    this.wait(100000);
                } catch (InterruptedException e) {

                }finally {
                    readBloking = false;
                }
            }
            readBloking = false;
        }
        return false;
    }

    @Override
    public boolean blockWritable(long millisecs) throws IOException{
        synchronized (this){
            if(isOutputShutdown()){
                throw new EOFException();
            }
            writeBloking = true;
            while (writeBloking && !isOutputShutdown()){
                System.out.println("write Block and updateKey");
                updateKey();
                try {
                    this.wait(100000);
                } catch (InterruptedException e) {

                }finally {
                    writeBloking =false;
                }
            }
            writeBloking = false;
        }
        return false;
    }

    public boolean isWriteable() {
        return writeable;
    }

    @Override
    public int flush(Buffer header, Buffer body){
        int len = super.flush(header,body);
        return len;
    }

    @Override
    public void schedule() throws IOException{
        synchronized (this){
            if(key == null || !key.isValid()){
                readBloking = false;
                writeBloking = false;
                this.notifyAll();
                return;
            }

            if(readBloking || writeBloking){
                if(readBloking && key.isReadable()){
                    readBloking = false;
                }
                if(writeBloking && key.isWritable()){
                    writeBloking = false;
                }
                this.notifyAll();
                key.interestOps(0);
                if(state <STATE_DISPATCHED){
                    doUpdateKey();
                }
            }

            if((key.readyOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE && (key.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE){
                interestOps = key.interestOps() & ~SelectionKey.OP_WRITE;
                key.interestOps(interestOps);
                writeable = true;
            }
            if(state >= STATE_DISPATCHED){
                key.interestOps(0);
            }else{
                dispatch();
                if(state >= STATE_DISPATCHED){
                    key.interestOps(0);
                }
            }
        }
    }

    @Override
    public void dispatch(){
        synchronized (this){
            if(state<=STATE_UNDISPATCH){
                if(onIdle){
                    state = STATE_NEEDDISPATCH;
                }else{
                    state = STATE_DISPATCHED;
                    boolean dispatched = manager.dispatch(handler);
                    if(!dispatched){
                        state = STATE_NEEDDISPATCH;
                        doUpdateKey();
                    }
                }
            }
        }
    }


    @Override
    public void updateKey() {

        final boolean changed;

        synchronized (this){
            int currentOps = -1;
            if(getChannel().isOpen()){
                boolean readInterest = readBloking || state < STATE_DISPATCHED;
                boolean writeInterest = writeBloking || (state < STATE_DISPATCHED && !writeable);

                interestOps = ((!isInputShutdown() && readInterest) ? SelectionKey.OP_READ : 0) |
                        ((!isOutputShutdown() && writeInterest) ? SelectionKey.OP_WRITE : 0);

                try {
                    currentOps = (key != null && key.isValid()) ? key.interestOps() : currentOps;
                }catch (Exception e){
                    key = null;
                }
            }
            changed = interestOps != currentOps;
        }
        if (changed) {
            System.out.println("updateKey,interestOps change and add self to workQueue"+this);
            worker.addWork(this);
            worker.wakeUp();
        }

    }

    public boolean isReadBloking() {
        return readBloking;
    }

    public boolean isWriteBloking() {
        return writeBloking;
    }

    @Override
    public void doUpdateKey(){
        synchronized (this){
            if(getChannel().isOpen()){
                if(interestOps > 0){
                    if(key == null && !key.isValid()){
                        SelectableChannel sc = (SelectableChannel)getChannel();
                        if(sc.isRegistered()){
                            updateKey();
                        }else{
                            try {
                                key = ((SelectableChannel)getChannel()).register(worker.getSelector(),interestOps,this);
                            }catch (IOException e){
                                if(key != null && key.isValid()){
                                    key.cancel();
                                }
                                if(open){
                                    worker.destroyEndPoint(this);
                                }
                                open = false;
                                key = null;
                            }
                        }
                    }else{
                        System.out.println("doUpdatekey->register:"+interestOps);
                        key.interestOps(interestOps);
                    }
                }else{
                    if(key != null && key.isValid()){
                        key.interestOps(0);
                    }else{
                        key = null;
                    }
                }
            }else{
                if(key != null && key.isValid()){
                    key.cancel();
                }
                if(open){
                    open = false;
                    worker.destroyEndPoint(this);
                }
                key = null;
            }
        }

    }


    private void handle() throws IOException{
        connection.handle();
    }

    @Override
    public void close() throws IOException {
        final SelectionKey selectionKey = key;
        if(selectionKey != null){
            selectionKey.cancel();
        }
        try {
            super.close();
        }catch (IOException e){

        }finally {
            updateKey();
        }
    }
}
