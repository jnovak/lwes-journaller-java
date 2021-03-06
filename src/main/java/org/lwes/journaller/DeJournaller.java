package org.lwes.journaller;
/**
 * A Class that will "dejournal" files written to by the GZIPEventHandler.  If you want to
 * do something other than write the events to stdout, subclass this and override handleEvent.
 *
 * User: fmaritato
 * Date: Apr 16, 2009
 */

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.SequenceFile;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.lwes.Event;
import org.lwes.EventSystemException;
import org.lwes.db.EventTemplateDB;
import org.lwes.journaller.util.EventHandlerUtil;
import org.lwes.serializer.DeserializerState;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

public class DeJournaller implements Runnable, JournallerConstants {
    private static transient Log log = LogFactory.getLog(DeJournaller.class);

    @Option(name = "--seq", aliases = "--sequence")
    protected boolean sequence = false;

    @Option(name = "-g", aliases = "--gzipped")
    protected boolean gzipped;

    @Option(name = "-f", aliases = "--file")
    protected String fileName;

    @Option(name = "-e", aliases = "--esf-file")
    protected String esfFile;

    @Option(name = "-v", aliases = "validate")
    protected boolean validate = false;

    public DeJournaller() {
    }

    public void run() {

        if (fileName == null || "".equals(fileName)) {
            log.error("File name was not specified");
            return;
        }

        EventTemplateDB evtTemplate = new EventTemplateDB();
        DeserializerState state = new DeserializerState();

        if (getEsfFile() != null) {
            evtTemplate.setESFFile(new File(getEsfFile()));
        }
        log.debug("esf: " + evtTemplate.getESFFile());
        log.debug("validate: " + validate);
        evtTemplate.initialize();

        DataInputStream in = null;
        try {
            log.debug("Opening file: " + fileName);
            if (sequence) {
                processSequenceFile(fileName);
            }
            else {
                if (gzipped) {
                    in = new DataInputStream(new GZIPInputStream(new FileInputStream(fileName)));
                }
                else {
                    in = new DataInputStream(new FileInputStream(fileName));
                }
                Event evt;
                while ((evt = EventHandlerUtil.readEvent(in, state, evtTemplate, validate)) != null) {
                    state.reset();
                    handleEvent(evt);
                }
            }
        }
        catch (EOFException e) {
            // this is normal. Just catch and ignore.
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
            done();
        }
    }

    protected void processSequenceFile(String file) {
        Configuration conf = new Configuration();
        FileSystem fs = null;
        SequenceFile.Reader reader = null;
        try {
            if (log.isDebugEnabled()) {
                log.debug("Opening file: " + file);
            }
            fs = FileSystem.get(conf);
            Path p = new Path(file);
            reader = new SequenceFile.Reader(fs, p, conf);

            BytesWritable key = (BytesWritable) reader.getKeyClass().newInstance();
            BytesWritable value = (BytesWritable) reader.getKeyClass().newInstance();

            EventTemplateDB templ = new EventTemplateDB();
            DeserializerState state = new DeserializerState();
            while (reader.next(key, value)) {
                byte[] evtBytes = new byte[key.getLength()+value.getLength()];
                System.arraycopy(key.getBytes(), 0,
                                 evtBytes, 0,
                                 key.getLength());
                System.arraycopy(value.getBytes(), 0,
                                 evtBytes,
                                 key.getLength(), value.getLength());
                Event evt = EventHandlerUtil.readEvent
                    (new DataInputStream(new ByteArrayInputStream(evtBytes)),
                     state, templ);
                state.reset();
                if (log.isDebugEnabled()) {
                    log.debug("read a k/v: "+evt.toOneLineString());
                }
                handleEvent(evt);
            }
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        catch (InstantiationException e) {
            log.error(e.getMessage(), e);
        }
        catch (IllegalAccessException e) {
            log.error(e.getMessage(), e);
        }
        catch (EventSystemException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * This method is called after all events in the file have been processed.
     */
    public void done() {
        if (log.isDebugEnabled()) {
            log.debug("done");
        }
    }

    /**
     * Subclass and override this method if you want to do something other than print the
     * events to the logging system. For example, you may want to write these events to a
     * database.
     *
     * @param event
     */
    public void handleEvent(Event event) {
        System.out.println(event.toOneLineString());
    }

    protected void parseArguments(String[] args) throws CmdLineException {
        CmdLineParser parser = new CmdLineParser(this);
        parser.parseArgument(args);
    }

    public static void main(String[] args) {
        DeJournaller dj = new DeJournaller();
        try {
            dj.parseArguments(args);
        }
        catch (CmdLineException e) {
            log.error(e.getMessage(), e);
        }
        dj.run();
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getEsfFile() {
        return esfFile;
    }

    public void setEsfFile(String esfFile) {
        this.esfFile = esfFile;
    }

    public boolean isGzipped() {
        return gzipped;
    }

    public void setGzipped(boolean gzipped) {
        this.gzipped = gzipped;
    }

    public boolean isValidate() {
        return validate;
    }

    public void setValidate(boolean validate) {
        this.validate = validate;
    }
}
