package org.lwes.journaller.util;
/**
 * @author fmaritato
 */

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.lwes.AttributeNotSetException;
import org.lwes.Event;
import org.lwes.NoSuchAttributeException;
import org.lwes.NoSuchAttributeTypeException;
import org.lwes.NoSuchEventException;
import org.lwes.db.EventTemplateDB;
import org.lwes.journaller.JournallerConstants;
import org.lwes.serializer.Deserializer;
import org.lwes.serializer.DeserializerState;
import org.lwes.serializer.Serializer;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class EventHandlerUtil implements JournallerConstants {

    private static transient Log log = LogFactory.getLog(EventHandlerUtil.class);

    /**
     * The header is [length][time][host][port][site][unused]
     * [length] is uint16  -- 2
     * [time] is int64 -- 8
     * [host] is 4 byte ip address -- 4
     * [port] is uint16 -- 2
     * [site] is uint16 -- 2
     * [unused] is uint32 -- 4
     *
     * @param event Event we are writing the header for.
     * @param buf   preallocated ByteBuffer for writing the header bytes into.
     */
    public static void writeHeader(Event event, ByteBuffer buf) {
        byte[] data = event.serialize();
        // The header contains bytes reserved for expansion...
        byte[] unused = new byte[4];
        int size = data.length;
        if (log.isDebugEnabled()) {
            log.debug("size: " + size);
        }
        long time = System.currentTimeMillis();

        try {
            InetAddress sender = event.getInetAddress("SenderIP");
            int port = event.getUInt16("SenderPort");
            int siteId = 0;
            try {
                siteId = event.getUInt16("SiteID");
            }
            catch (AttributeNotSetException e) {
                // who cares
            }

            byte[] shortBuf = new byte[2];
            Serializer.serializeUINT16(size, shortBuf, 0);
            buf.put(shortBuf)
                    .putLong(time)
                    .put(sender.getAddress());
            Serializer.serializeUINT16(port, shortBuf, 0);
            buf.put(shortBuf);
            Serializer.serializeUINT16(siteId, shortBuf, 0);
            buf.put(shortBuf)
                    .put(unused);

        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public static void writeEvent(Event event, ByteBuffer buf) {
        writeHeader(event, buf);
        buf.put(event.serialize());
    }

    public static Event readEvent(DataInputStream in,
                                  DeserializerState state,
                                  EventTemplateDB evtTemplate)
            throws IOException,
                   NoSuchAttributeException,
                   NoSuchAttributeTypeException,
                   NoSuchEventException {

        byte[] headerData = new byte[MAX_HEADER_SIZE];
        // read header
        in.readFully(headerData, 0, MAX_HEADER_SIZE);

        int size = 0;
        long time = 0l;
        int port = 0;
        int siteId = 0;

        ByteBuffer buf = ByteBuffer.allocate(MAX_HEADER_SIZE);
        buf = buf.put(headerData);
        buf.position(0); // reset to beginning

        byte[] shortBuf = new byte[2];
        buf.get(shortBuf);
        size = Deserializer.deserializeUINT16(state, shortBuf);
        if (size < 0 || size > MAX_MSG_SIZE) {
            // something is wrong
            log.info("error reading header info. Size was " + size);
            return null;
        }

        time = buf.getLong();

        byte[] ipbytes = new byte[4];
        buf.get(ipbytes);
        InetAddress ip = InetAddress.getByAddress(ipbytes);

        state.reset();
        buf.get(shortBuf);
        port = Deserializer.deserializeUINT16(state, shortBuf);

        state.reset();
        buf.get(shortBuf);
        siteId = Deserializer.deserializeUINT16(state, shortBuf);

        byte[] unused = new byte[4];
        buf.get(unused);

        if (log.isDebugEnabled()) {
            log.debug("size: " + size);
            log.debug("time: " + time);
            log.debug("ip: " + ip);
            log.debug("port: " + port);
            log.debug("siteId: " + siteId);
        }

        byte[] eventData = new byte[MAX_BODY_SIZE];
        // Now read in the event
        in.readFully(eventData, 0, size);
        return new Event(eventData, false, evtTemplate);
    }
}